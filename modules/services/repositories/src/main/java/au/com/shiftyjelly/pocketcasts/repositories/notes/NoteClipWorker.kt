package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptEntry
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@HiltWorker
class NoteClipWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val episodeManager: EpisodeManager,
    private val podcastManager: PodcastManager,
    private val transcriptManager: TranscriptManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val clipId = inputData.getString(KEY_CLIP_ID) ?: return Result.failure()
        val mode = NoteClipMode.entries.firstOrNull { it.name == inputData.getString(KEY_MODE) } ?: NoteClipMode.Initial
        val store = NoteClipStore.get(applicationContext)
        // The clip may have been deleted from the notification while this waited.
        val clip = store.get(clipId) ?: return Result.success()

        return try {
            val episode = episodeManager.findEpisodeByUuid(clip.episodeUuid)
                ?: throw NotesSetupException("找不到這一集")
            val podcastTitle = clip.podcastTitle.ifBlank {
                if (episode is PodcastEpisode) podcastManager.findPodcastByUuid(episode.podcastUuid)?.title.orEmpty() else ""
            }
            val publisherWords = if (mode == NoteClipMode.RetranscribeWithElevenLabs) null else loadPublisherWords(clip.episodeUuid)
            val processed = withContext(Dispatchers.IO) {
                NoteClipProcessor(applicationContext, publisherWords).process(clip.copy(podcastTitle = podcastTitle), episode, mode)
            }
            // Keep anything the user changed while this ran, such as a note typed into the notification.
            val latest = store.get(clipId) ?: return Result.success()
            val saved = processed.copy(note = latest.note, isDone = latest.isDone)
            store.save(saved)
            SaveToNotes.showReady(applicationContext, saved)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Saving clip $clipId to notes failed")
            val message = when (e) {
                is NotesSetupException -> e.message
                is NotesApiException -> e.userMessage
                else -> "處理失敗：${e.message ?: e.javaClass.simpleName}"
            }
            val failed = store.update(clipId) { it.copy(status = NoteClipStatus.Failed, error = message) }
            if (failed != null) SaveToNotes.showFailed(applicationContext, failed)
            Result.success()
        }
    }

    /**
     * The publisher's own transcript, when it has timings. Pocket Casts' generated transcript is a Plus feature,
     * so it is never used here.
     */
    private suspend fun loadPublisherWords(episodeUuid: String): List<NoteWord>? {
        val transcript = try {
            withTimeoutOrNull(PUBLISHER_TRANSCRIPT_TIMEOUT) { transcriptManager.loadTranscript(episodeUuid) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not load the publisher transcript")
            null
        }
        val text = transcript as? Transcript.Text ?: return null
        if (text.isGenerated) return null
        val words = mutableListOf<NoteWord>()
        var speaker: String? = null
        for (entry in text.entries) {
            when (entry) {
                is TranscriptEntry.Speaker -> speaker = entry.name
                is TranscriptEntry.Text -> {
                    if (entry.startTimeMs < 0 || entry.endTimeMs < 0) continue
                    val value = entry.value.trim()
                    if (value.isEmpty()) continue
                    words.add(NoteWord(text = value, startMs = entry.startTimeMs, endMs = entry.endTimeMs, speaker = speaker, isSpacing = false))
                    // Latin-script cues need a space between them; CJK text does not.
                    if (value.last().code < 0x2E80) {
                        words.add(NoteWord(text = " ", startMs = entry.endTimeMs, endMs = entry.endTimeMs, speaker = speaker, isSpacing = true))
                    }
                }
            }
        }
        return words.takeIf { list -> list.any { !it.isSpacing } }
    }

    companion object {
        private val PUBLISHER_TRANSCRIPT_TIMEOUT = 8.seconds

        const val KEY_CLIP_ID = "clip_id"
        const val KEY_MODE = "mode"
        const val TAG = "tingji_note_clip"
    }
}
