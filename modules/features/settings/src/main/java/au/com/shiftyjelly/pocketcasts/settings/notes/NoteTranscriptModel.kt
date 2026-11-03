package au.com.shiftyjelly.pocketcasts.settings.notes

import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptEntry
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptType
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClip
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteSentence
import au.com.shiftyjelly.pocketcasts.repositories.transcript.BookmarkTranscript
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TextSpan

/**
 * A clip's sentences laid out the way [BookmarkTranscript] lays out transcript entries (one entry per line,
 * speaker names on their own line), so offsets in the rendered text map back to sentences and times.
 */
internal class NoteTranscriptModel(private val clip: NoteClip) {
    val sentences: List<NoteSentence> = clip.sentences
    val transcript: BookmarkTranscript
    private val sentenceSpans: List<TextSpan>

    init {
        val entries = mutableListOf<TranscriptEntry>()
        val spans = mutableListOf<TextSpan>()
        var length = 0
        var lastSpeaker: String? = null
        sentences.forEach { sentence ->
            val speaker = sentence.speaker
            if (speaker != null && speaker != lastSpeaker) {
                val name = clip.speakerName(speaker).orEmpty()
                if (length > 0) length += 1
                entries.add(TranscriptEntry.Speaker(name))
                length += name.length
            }
            lastSpeaker = speaker
            if (length > 0) length += 1
            val start = length
            entries.add(TranscriptEntry.Text(value = sentence.text, startTimeMs = sentence.startMs, endTimeMs = sentence.endMs))
            length += sentence.text.length
            spans.add(TextSpan(start, length))
        }
        transcript = BookmarkTranscript.from(
            Transcript.Text(
                entries = entries,
                type = TranscriptType.Vtt,
                url = "",
                isGenerated = false,
                episodeUuid = clip.episodeUuid,
                podcastUuid = clip.podcastUuid,
            ),
        )
        sentenceSpans = spans
    }

    /** The selected part of the clip, as a span of the rendered text. */
    val passage: TextSpan?
        get() {
            val selected = sentences.indices.filter { clip.isInSelection(sentences[it]) }
            if (selected.isEmpty()) return null
            return TextSpan(sentenceSpans[selected.first()].start, sentenceSpans[selected.last()].end)
        }

    val highlightSpans: List<TextSpan>
        get() = sentences.indices.filter { clip.isHighlighted(sentences[it]) }.map { sentenceSpans[it] }

    val bookmarkOffset: Int?
        get() = transcript.referenceOffsetAt(clip.bookmarkMs)

    fun sentenceAt(offset: Int): NoteSentence? {
        val index = sentenceSpans.indexOfFirst { offset >= it.start && offset < it.end }
            .takeIf { it >= 0 }
            ?: sentenceSpans.indexOfFirst { it.start >= offset }.takeIf { it >= 0 }
            ?: return null
        return sentences[index]
    }

    /** The selection times for a passage drawn with the handles, snapped out to whole sentences. */
    fun selectionFor(span: TextSpan): Pair<Long, Long>? {
        if (span.isEmpty) return null
        val first = sentenceAt(span.start) ?: return null
        val last = sentenceAt((span.end - 1).coerceAtLeast(span.start)) ?: return null
        return first.startMs to maxOf(first.endMs, last.endMs)
    }
}
