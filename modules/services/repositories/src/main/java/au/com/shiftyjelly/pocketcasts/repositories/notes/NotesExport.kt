package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns an episode's clips into one note and sends it to TickTick. */
object NotesExport {
    private const val SHARE_HOST = "https://pca.st"

    fun taskTitle(clips: List<NoteClip>): String {
        val first = clips.firstOrNull() ?: return ""
        return listOf(first.podcastTitle, first.episodeTitle).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Markdown for TickTick's task content. The title is repeated at the top so copying the content gives a whole note. */
    fun markdown(clips: List<NoteClip>): String {
        val sorted = clips.sortedBy { it.selectionStartMs }
        val first = sorted.firstOrNull() ?: return ""
        val isPodcastEpisode = first.podcastTitle != "我的檔案"
        return buildString {
            appendLine("**${taskTitle(sorted)}**")
            if (isPodcastEpisode) appendLine("集數：$SHARE_HOST/episode/${first.episodeUuid}")
            sorted.forEach { clip ->
                appendLine()
                append("### ").append(NoteTranscript.formatTime(clip.selectionStartMs)).append(' ')
                appendLine(clip.title.ifBlank { "片段" })
                if (clip.keyPoints.isNotEmpty()) {
                    clip.keyPoints.forEach { appendLine("- $it") }
                }
                val quote = clip.selectedSentences.joinToString("") { sentence ->
                    if (clip.isHighlighted(sentence)) "**${sentence.text}**" else sentence.text
                }
                if (quote.isNotBlank()) {
                    appendLine()
                    appendLine("> $quote")
                }
                if (clip.note.isNotBlank()) {
                    appendLine()
                    appendLine("我的筆記：${clip.note}")
                }
                if (isPodcastEpisode) {
                    val start = clip.selectionStartMs / 1000
                    val end = (clip.selectionEndMs + 999) / 1000
                    appendLine("$SHARE_HOST/episode/${clip.episodeUuid}?t=$start,$end")
                }
            }
        }.trim()
    }

    /** Creates or updates the episode's TickTick task. */
    suspend fun sendToTickTick(context: Context, clips: List<NoteClip>) {
        val config = NotesConfig.get(context)
        if (!config.isTickTickConnected) throw NotesSetupException("請先在聽記設定連接 TickTick")
        val first = clips.firstOrNull() ?: return
        val ref = withContext(Dispatchers.IO) {
            TickTickApi.upsertTask(
                token = config.tickTickToken,
                existing = config.tickTickTask(first.episodeUuid),
                title = taskTitle(clips),
                content = markdown(clips),
            )
        }
        config.setTickTickTask(first.episodeUuid, ref)
    }

    fun tickTickAuthorizeUrl(context: Context, state: String): String? {
        val config = NotesConfig.get(context)
        if (config.tickTickClientId.isBlank()) return null
        return TickTickApi.authorizeUrl(config.tickTickClientId, state)
    }

    val tickTickRedirectUri: String
        get() = TickTickApi.REDIRECT_URI

    /** Finishes the TickTick sign-in with the code from the redirect URL. */
    suspend fun completeTickTickSignIn(context: Context, code: String) {
        val config = NotesConfig.get(context)
        if (config.tickTickClientId.isBlank() || config.tickTickClientSecret.isBlank()) {
            throw NotesSetupException("請先填入 TickTick 的 Client ID 和 Client Secret")
        }
        val (token, expiresAt) = withContext(Dispatchers.IO) {
            TickTickApi.exchangeCode(config.tickTickClientId, config.tickTickClientSecret, code)
        }
        config.tickTickToken = token
        config.tickTickTokenExpiresAt = expiresAt
    }

    fun userMessage(e: Throwable): String = when (e) {
        is NotesSetupException -> e.message ?: "發生錯誤"
        is NotesApiException -> e.userMessage
        else -> "失敗：${e.message ?: e.javaClass.simpleName}"
    }
}
