package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.content.Context
import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import java.io.File
import org.json.JSONObject

enum class NoteClipMode {
    Initial,
    ExtendBefore,
    ExtendAfter,
    Reanalyze,

    /** Start over with ElevenLabs, for when the publisher's transcript does not line up with the audio. */
    RetranscribeWithElevenLabs,
}

/** Transcribes a clip, asks the AI what matters, and pulls in more audio when the point is cut off. */
internal class NoteClipProcessor(
    private val context: Context,
    /** The publisher's transcript as words, when the episode has one with timings. */
    private val publisherWords: List<NoteWord>?,
) {
    private var usedElevenLabs = false

    fun process(clip: NoteClip, episode: BaseEpisode, mode: NoteClipMode): NoteClip {
        val config = NotesConfig.get(context)
        if (publisherWords == null && config.elevenLabsKey.isBlank()) {
            throw NotesSetupException("請先在聽記設定填入 ElevenLabs API key")
        }
        val source by lazy { audioSource(episode) }
        val durationMs = episode.durationMs.toLong().takeIf { it > 0 }

        var current = when (mode) {
            NoteClipMode.Initial, NoteClipMode.RetranscribeWithElevenLabs -> {
                val start = (clip.bookmarkMs - BEFORE_MS).coerceAtLeast(0)
                val end = (clip.bookmarkMs + AFTER_MS).let { if (durationMs != null) it.coerceAtMost(durationMs) else it }
                val words = transcribe(config, { source }, start, end, allowPublisher = mode == NoteClipMode.Initial)
                clip.copy(
                    words = words,
                    transcribedStartMs = start,
                    transcribedEndMs = end,
                    autoExtendedBefore = 0,
                    autoExtendedAfter = 0,
                    aiReason = null,
                    transcriptSource = if (usedElevenLabs) TranscriptSource.ElevenLabs else TranscriptSource.Publisher,
                )
            }
            NoteClipMode.ExtendBefore -> extendBefore(config, { source }, clip)
            NoteClipMode.ExtendAfter -> extendAfter(config, { source }, clip, durationMs)
            NoteClipMode.Reanalyze -> clip
        }
        if (current.words.none { !it.isSpacing }) {
            throw NotesSetupException("這段沒有轉錄到任何內容")
        }

        var analysis = analyze(config, current)
        // Only the first pass fills gaps on its own; manual extends already say where the user wants more.
        if (mode == NoteClipMode.Initial || mode == NoteClipMode.RetranscribeWithElevenLabs) {
            while (true) {
                val canGoBack = current.transcribedStartMs > 0 && current.autoExtendedBefore < MAX_AUTO_EXTENDS
                val canGoForward = (durationMs == null || current.transcribedEndMs < durationMs) &&
                    current.autoExtendedAfter < MAX_AUTO_EXTENDS
                val goBack = analysis.needMoreBefore && canGoBack
                val goForward = analysis.needMoreAfter && canGoForward
                if (!goBack && !goForward) break
                if (goBack) {
                    current = extendBefore(config, { source }, current).copy(autoExtendedBefore = current.autoExtendedBefore + 1)
                }
                if (goForward) {
                    current = extendAfter(config, { source }, current, durationMs).copy(autoExtendedAfter = current.autoExtendedAfter + 1)
                }
                analysis = analyze(config, current)
            }
        }
        return apply(current, analysis)
    }

    private fun extendBefore(config: NotesConfig, source: () -> String, clip: NoteClip): NoteClip {
        if (clip.transcribedStartMs <= 0) return clip
        val start = (clip.transcribedStartMs - EXTEND_MS).coerceAtLeast(0)
        val allowPublisher = clip.transcriptSource == TranscriptSource.Publisher
        val words = transcribe(config, source, start, clip.transcribedStartMs, allowPublisher)
            .filter { it.startMs < clip.transcribedStartMs }
        return clip.copy(words = words + clip.words, transcribedStartMs = start)
    }

    private fun extendAfter(config: NotesConfig, source: () -> String, clip: NoteClip, durationMs: Long?): NoteClip {
        val end = (clip.transcribedEndMs + EXTEND_MS).let { if (durationMs != null) it.coerceAtMost(durationMs) else it }
        if (end <= clip.transcribedEndMs) return clip
        val allowPublisher = clip.transcriptSource == TranscriptSource.Publisher
        val words = transcribe(config, source, clip.transcribedEndMs, end, allowPublisher)
            .filter { it.startMs >= clip.transcribedEndMs }
        return clip.copy(words = clip.words + words, transcribedEndMs = end)
    }

    private fun transcribe(
        config: NotesConfig,
        source: () -> String,
        startMs: Long,
        endMs: Long,
        allowPublisher: Boolean,
    ): List<NoteWord> {
        if (allowPublisher && publisherWords != null) {
            val slice = publisherWords.filter { it.endMs > startMs && it.startMs < endMs }
            if (slice.any { !it.isSpacing }) return slice
        }
        if (config.elevenLabsKey.isBlank()) {
            throw NotesSetupException("這段沒有節目逐字稿，請先在聽記設定填入 ElevenLabs API key")
        }
        usedElevenLabs = true
        val wav = File.createTempFile("tingji_clip", ".wav", context.cacheDir)
        try {
            AudioSegmentExtractor.extractWav(source(), startMs, endMs, wav)
            return ElevenLabsTranscriber.transcribe(config.elevenLabsKey, wav, offsetMs = startMs).words
        } finally {
            wav.delete()
        }
    }

    private fun audioSource(episode: BaseEpisode): String {
        val path = episode.downloadedFilePath
        if (path != null && File(path).exists()) return path
        if (episode.isStreamUrlHls) throw NotesSetupException("這一集是串流格式（HLS），請先下載再存到筆記")
        return episode.streamUrl ?: throw NotesSetupException("找不到這一集的音檔，請先下載")
    }

    private fun analyze(config: NotesConfig, clip: NoteClip): ClipAnalysis {
        val sentences = clip.sentences
        val bookmarkIndex = sentences.indexOfLast { it.startMs <= clip.bookmarkMs }.coerceAtLeast(0)
        val lines = sentences.mapIndexed { index, sentence ->
            val marker = if (index == bookmarkIndex) "★" else ""
            val speaker = sentence.speaker?.let { "$it：" }.orEmpty()
            "[$index]$marker ${NoteTranscript.formatTime(sentence.startMs)} $speaker${sentence.text}"
        }
        val user = buildString {
            appendLine("節目：${clip.podcastTitle}")
            appendLine("集數：${clip.episodeTitle}")
            appendLine("聽眾在 ${NoteTranscript.formatTime(clip.bookmarkMs)} 按下書籤（標 ★ 的句子）。")
            appendLine("以下是書籤前後的逐字稿，每行一句，格式為 [編號] 時間 講者：內容。")
            appendLine()
            lines.forEach { appendLine(it) }
            appendLine()
            appendLine(PROMPT_INSTRUCTIONS)
        }
        val reply = NoteAiClient.complete(config, SYSTEM_PROMPT, user)
        return ClipAnalysis.parse(reply, sentences.size, bookmarkIndex)
    }

    private fun apply(clip: NoteClip, analysis: ClipAnalysis): NoteClip {
        val sentences = clip.sentences
        val start = sentences.getOrNull(analysis.startIndex)
        val end = sentences.getOrNull(analysis.endIndex)
        val reason = analysis.reason.takeIf { it.isNotBlank() && (clip.autoExtendedBefore > 0 || clip.autoExtendedAfter > 0) }
        return clip.copy(
            status = NoteClipStatus.Ready,
            error = null,
            title = analysis.title.ifBlank { clip.title },
            selectionStartMs = start?.startMs ?: clip.transcribedStartMs,
            selectionEndMs = end?.endMs ?: clip.transcribedEndMs,
            highlightStarts = analysis.highlightIndices.mapNotNull { sentences.getOrNull(it)?.startMs },
            keyPoints = analysis.keyPoints,
            aiReason = reason ?: clip.aiReason,
            speakerNames = clip.speakerNames + analysis.speakerNames,
        )
    }

    companion object {
        const val BEFORE_MS = 90_000L
        const val AFTER_MS = 30_000L
        const val EXTEND_MS = 30_000L
        const val MAX_AUTO_EXTENDS = 2

        private const val SYSTEM_PROMPT = "你是幫 podcast 聽眾整理筆記的助理。只輸出一個 JSON 物件，不要有其他文字。"

        private val PROMPT_INSTRUCTIONS = """
            請找出聽眾最可能想保存的一段完整內容：通常 30 到 60 秒，必須包含或緊鄰 ★ 那一句，開頭和結尾都要是完整的句子。
            輸出以下 JSON：
            {
              "title": "15 字以內的標題",
              "start": 起始句編號,
              "end": 結束句編號,
              "highlights": [這段裡最重要的 1 到 3 句的編號],
              "key_points": ["1 到 3 條重點，每條 30 字以內"],
              "need_more_before": 這段內容在逐字稿開頭就被切斷、缺少前文才看得懂時為 true，否則 false,
              "need_more_after": 這段內容在逐字稿結尾被切斷時為 true，否則 false,
              "reason": "需要補前文或後文時，用一句話說明原因，否則空字串",
              "speakers": {"speaker_0": "依內容推測的稱呼，例如主持人、來賓或人名"}
            }
            標題、重點、原因使用逐字稿的語言；如果是中文，一律使用繁體中文。
        """.trimIndent()
    }
}

internal data class ClipAnalysis(
    val title: String,
    val startIndex: Int,
    val endIndex: Int,
    val highlightIndices: List<Int>,
    val keyPoints: List<String>,
    val needMoreBefore: Boolean,
    val needMoreAfter: Boolean,
    val reason: String,
    val speakerNames: Map<String, String>,
) {
    companion object {
        fun parse(reply: String, sentenceCount: Int, bookmarkIndex: Int): ClipAnalysis {
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')
            if (start < 0 || end <= start) throw NotesSetupException("AI 回覆的格式不對，請重試")
            val json = JSONObject(reply.substring(start, end + 1))
            val last = (sentenceCount - 1).coerceAtLeast(0)
            val rawStart = json.optInt("start", (bookmarkIndex - 2).coerceAtLeast(0)).coerceIn(0, last)
            val rawEnd = json.optInt("end", (bookmarkIndex + 1).coerceAtMost(last)).coerceIn(0, last)
            val startIdx = minOf(rawStart, rawEnd)
            val endIdx = maxOf(rawStart, rawEnd)
            val highlights = json.optJSONArray("highlights").toLongList()
                .map { it.toInt() }
                .filter { it in startIdx..endIdx }
                .distinct()
            val speakersJson = json.optJSONObject("speakers") ?: JSONObject()
            val speakers = speakersJson.keys().asSequence()
                .associateWith { speakersJson.optString(it) }
                .filterValues { it.isNotBlank() }
            return ClipAnalysis(
                title = json.optString("title").trim(),
                startIndex = startIdx,
                endIndex = endIdx,
                highlightIndices = highlights,
                keyPoints = json.optJSONArray("key_points").toStringList(),
                needMoreBefore = json.optBoolean("need_more_before"),
                needMoreAfter = json.optBoolean("need_more_after"),
                reason = json.optString("reason").trim(),
                speakerNames = speakers,
            )
        }
    }
}
