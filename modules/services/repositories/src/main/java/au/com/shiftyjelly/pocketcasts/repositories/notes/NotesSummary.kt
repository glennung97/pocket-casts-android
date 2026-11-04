package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.content.Context
import androidx.core.content.edit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/** An AI-written note for a whole episode, built from its clips' highlights, key points and the listener's notes. */
data class EpisodeSummary(
    val episodeUuid: String,
    val text: String,
    val createdAt: Long,
    val clipCount: Int,
)

object NotesSummary {
    private const val PREFS_NAME = "tingji_notes_summaries"

    private val _summaries = MutableStateFlow<Map<String, EpisodeSummary>>(emptyMap())
    private val _generating = MutableStateFlow<Set<String>>(emptySet())
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loaded = false

    val generating: StateFlow<Set<String>> = _generating.asStateFlow()

    /** The last failure per episode, cleared when a new attempt starts. */
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    /** Starts writing the episode note in the background, so leaving the screen does not stop it. */
    fun start(context: Context, clips: List<NoteClip>) {
        val episodeUuid = clips.firstOrNull()?.episodeUuid ?: return
        if (episodeUuid in _generating.value) return
        _errors.update { it - episodeUuid }
        val appContext = context.applicationContext
        scope.launch {
            try {
                generate(appContext, clips)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Could not write the episode note")
                _errors.update { it + (episodeUuid to NotesExport.userMessage(e)) }
            }
        }
    }

    fun summaries(context: Context): StateFlow<Map<String, EpisodeSummary>> {
        load(context)
        return _summaries.asStateFlow()
    }

    fun get(context: Context, episodeUuid: String): EpisodeSummary? {
        load(context)
        return _summaries.value[episodeUuid]
    }

    /** Writes the episode note from the ready clips. Throws with a message the user can read when it fails. */
    suspend fun generate(context: Context, clips: List<NoteClip>): EpisodeSummary {
        val ready = clips.filter { it.status == NoteClipStatus.Ready }.sortedBy { it.selectionStartMs }
        val first = ready.firstOrNull() ?: throw NotesSetupException("這一集還沒有整理好的片段")
        val episodeUuid = first.episodeUuid
        _generating.update { it + episodeUuid }
        try {
            val config = NotesConfig.get(context)
            val reply = withContext(Dispatchers.IO) { NoteAiClient.complete(config, SYSTEM_PROMPT, prompt(ready)) }
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')
            val fromJson: String? = if (start >= 0 && end > start) {
                runCatching { JSONObject(reply.substring(start, end + 1)).optString("summary") }.getOrNull()
            } else {
                null
            }
            val text = fromJson?.trim().orEmpty().ifEmpty { reply.trim() }
            if (text.isEmpty()) throw NotesSetupException("AI 沒有回傳內容，請再試一次")
            val summary = EpisodeSummary(episodeUuid, text, System.currentTimeMillis(), ready.size)
            save(context, summary)
            return summary
        } finally {
            _generating.update { it - episodeUuid }
        }
    }

    private fun prompt(clips: List<NoteClip>): String = buildString {
        val first = clips.first()
        appendLine("節目：${first.podcastTitle}")
        appendLine("集數：${first.episodeTitle}")
        appendLine("以下是聽眾在這一集存下的 ${clips.size} 個片段，依時間排序。")
        clips.forEachIndexed { index, clip ->
            appendLine()
            appendLine("## 片段 ${index + 1}（${NoteTranscript.formatTime(clip.selectionStartMs)}）${clip.title}")
            val highlighted = clip.selectedSentences.filter { clip.isHighlighted(it) }
            if (highlighted.isNotEmpty()) {
                appendLine("聽眾標的重點句：")
                highlighted.forEach { appendLine("- ${it.text}") }
            }
            if (clip.keyPoints.isNotEmpty()) {
                appendLine("AI 整理的要點：")
                clip.keyPoints.forEach { appendLine("- $it") }
            }
            if (clip.note.isNotBlank()) appendLine("聽眾的筆記：${clip.note}")
            appendLine("原文：${clip.selectedSentences.joinToString("") { it.text }.take(1200)}")
        }
        appendLine()
        appendLine(INSTRUCTIONS)
    }

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _summaries.value = prefs.all.mapNotNull { (key, value) ->
                val json = (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@mapNotNull null
                key to EpisodeSummary(
                    episodeUuid = key,
                    text = json.optString("text"),
                    createdAt = json.optLong("createdAt"),
                    clipCount = json.optInt("clipCount"),
                )
            }.toMap()
            loaded = true
        }
    }

    private fun save(context: Context, summary: EpisodeSummary) {
        load(context)
        val json = JSONObject()
            .put("text", summary.text)
            .put("createdAt", summary.createdAt)
            .put("clipCount", summary.clipCount)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(summary.episodeUuid, json.toString()) }
        _summaries.update { it + (summary.episodeUuid to summary) }
    }

    private const val SYSTEM_PROMPT = "你是幫 podcast 聽眾整理筆記的助理。只輸出一個 JSON 物件，不要有其他文字。"

    private val INSTRUCTIONS = """
        請把這些片段整理成一份這一集的筆記，輸出 JSON：{"summary": "筆記內容（Markdown）"}
        筆記內容依序包含：
        1. 一句話說這一集在講什麼
        2. 「重點」：3 到 6 點，合併重複的內容，以聽眾標的重點句為優先，每點 40 字以內
        3. 「我的想法」：只根據聽眾自己的筆記整理；沒有筆記就省略這一段
        4. 「可以試試看」：1 到 3 個具體可行的做法；內容不適合就省略
        小標用「### 」，條列用「- 」。不要編造片段裡沒有的內容。
        使用逐字稿的語言；如果是中文，一律使用繁體中文。
    """.trimIndent()
}
