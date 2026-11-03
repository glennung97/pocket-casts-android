package au.com.shiftyjelly.pocketcasts.repositories.notes

import org.json.JSONArray
import org.json.JSONObject

enum class NoteClipStatus {
    Processing,
    Ready,
    Failed,
}

/** One transcribed word or spacing token. Times are positions in the episode, in milliseconds. */
data class NoteWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val speaker: String?,
    val isSpacing: Boolean,
)

/** A sentence built from [NoteWord]s. */
data class NoteSentence(
    val startMs: Long,
    val endMs: Long,
    val speaker: String?,
    val text: String,
)

/**
 * A moment the listener saved, with the transcript around it and what the AI picked out.
 * [selectionStartMs]..[selectionEndMs] is the part that counts as the note; the rest of [words] is context.
 * [highlightStarts] holds the start times of the highlighted sentences, so it survives extending the transcript.
 */
data class NoteClip(
    val id: String,
    val episodeUuid: String,
    val podcastUuid: String,
    val podcastTitle: String,
    val episodeTitle: String,
    val bookmarkMs: Long,
    val createdAt: Long,
    val status: NoteClipStatus,
    val error: String? = null,
    val words: List<NoteWord> = emptyList(),
    val transcribedStartMs: Long = 0,
    val transcribedEndMs: Long = 0,
    val selectionStartMs: Long = 0,
    val selectionEndMs: Long = 0,
    val title: String = "",
    val keyPoints: List<String> = emptyList(),
    val highlightStarts: List<Long> = emptyList(),
    val note: String = "",
    val isDone: Boolean = false,
    val autoExtendedBefore: Int = 0,
    val autoExtendedAfter: Int = 0,
    val aiReason: String? = null,
    val speakerNames: Map<String, String> = emptyMap(),
) {
    val sentences: List<NoteSentence> by lazy { NoteTranscript.sentences(words) }

    val selectedSentences: List<NoteSentence>
        get() = sentences.filter { it.endMs > selectionStartMs && it.startMs < selectionEndMs }

    fun isHighlighted(sentence: NoteSentence) = sentence.startMs in highlightStarts

    fun speakerName(speaker: String?): String? {
        if (speaker == null) return null
        return speakerNames[speaker] ?: speaker.replace("speaker_", "講者 ")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("episodeUuid", episodeUuid)
        put("podcastUuid", podcastUuid)
        put("podcastTitle", podcastTitle)
        put("episodeTitle", episodeTitle)
        put("bookmarkMs", bookmarkMs)
        put("createdAt", createdAt)
        put("status", status.name)
        put("error", error ?: JSONObject.NULL)
        put(
            "words",
            JSONArray().apply {
                words.forEach { word ->
                    put(
                        JSONObject().apply {
                            put("t", word.text)
                            put("s", word.startMs)
                            put("e", word.endMs)
                            put("sp", word.speaker ?: JSONObject.NULL)
                            put("ws", word.isSpacing)
                        },
                    )
                }
            },
        )
        put("transcribedStartMs", transcribedStartMs)
        put("transcribedEndMs", transcribedEndMs)
        put("selectionStartMs", selectionStartMs)
        put("selectionEndMs", selectionEndMs)
        put("title", title)
        put("keyPoints", JSONArray(keyPoints))
        put("highlightStarts", JSONArray(highlightStarts))
        put("note", note)
        put("isDone", isDone)
        put("autoExtendedBefore", autoExtendedBefore)
        put("autoExtendedAfter", autoExtendedAfter)
        put("aiReason", aiReason ?: JSONObject.NULL)
        put("speakerNames", JSONObject(speakerNames))
    }

    companion object {
        fun fromJson(json: JSONObject): NoteClip {
            val wordsJson = json.optJSONArray("words") ?: JSONArray()
            val words = (0 until wordsJson.length()).map { index ->
                val word = wordsJson.getJSONObject(index)
                NoteWord(
                    text = word.optString("t"),
                    startMs = word.optLong("s"),
                    endMs = word.optLong("e"),
                    speaker = word.nullableString("sp"),
                    isSpacing = word.optBoolean("ws"),
                )
            }
            val speakersJson = json.optJSONObject("speakerNames") ?: JSONObject()
            val speakerNames = speakersJson.keys().asSequence().associateWith { speakersJson.optString(it) }
            return NoteClip(
                id = json.getString("id"),
                episodeUuid = json.optString("episodeUuid"),
                podcastUuid = json.optString("podcastUuid"),
                podcastTitle = json.optString("podcastTitle"),
                episodeTitle = json.optString("episodeTitle"),
                bookmarkMs = json.optLong("bookmarkMs"),
                createdAt = json.optLong("createdAt"),
                status = NoteClipStatus.entries.firstOrNull { it.name == json.optString("status") } ?: NoteClipStatus.Failed,
                error = json.nullableString("error"),
                words = words,
                transcribedStartMs = json.optLong("transcribedStartMs"),
                transcribedEndMs = json.optLong("transcribedEndMs"),
                selectionStartMs = json.optLong("selectionStartMs"),
                selectionEndMs = json.optLong("selectionEndMs"),
                title = json.optString("title"),
                keyPoints = json.optJSONArray("keyPoints").toStringList(),
                highlightStarts = json.optJSONArray("highlightStarts").toLongList(),
                note = json.optString("note"),
                isDone = json.optBoolean("isDone"),
                autoExtendedBefore = json.optInt("autoExtendedBefore"),
                autoExtendedAfter = json.optInt("autoExtendedAfter"),
                aiReason = json.nullableString("aiReason"),
                speakerNames = speakerNames,
            )
        }
    }
}

internal fun JSONObject.nullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).ifEmpty { null }
}

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}

internal fun JSONArray?.toLongList(): List<Long> {
    if (this == null) return emptyList()
    return (0 until length()).map { index -> optLong(index) }
}

object NoteTranscript {
    private const val SENTENCE_GAP_MS = 1_500L
    private val sentenceEndings = charArrayOf('。', '！', '？', '!', '?', '.', '…')

    fun sentences(words: List<NoteWord>): List<NoteSentence> {
        val result = mutableListOf<NoteSentence>()
        val text = StringBuilder()
        var startMs = -1L
        var endMs = 0L
        var speaker: String? = null

        fun flush() {
            val value = text.toString().trim()
            if (value.isNotEmpty() && startMs >= 0) {
                result.add(NoteSentence(startMs = startMs, endMs = endMs, speaker = speaker, text = value))
            }
            text.clear()
            startMs = -1L
        }

        for (word in words) {
            if (word.isSpacing) {
                if (text.isNotEmpty()) text.append(word.text)
                continue
            }
            val speakerChanged = startMs >= 0 && word.speaker != speaker
            val longPause = startMs >= 0 && word.startMs - endMs > SENTENCE_GAP_MS
            if (speakerChanged || longPause) flush()
            if (startMs < 0) {
                startMs = word.startMs
                speaker = word.speaker
            }
            text.append(word.text)
            endMs = word.endMs
            val lastChar = word.text.trimEnd().lastOrNull()
            if (lastChar != null && lastChar in sentenceEndings) flush()
        }
        flush()
        return result
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
