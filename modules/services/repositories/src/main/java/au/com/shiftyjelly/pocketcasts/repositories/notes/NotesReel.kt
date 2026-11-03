package au.com.shiftyjelly.pocketcasts.repositories.notes

import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays only the selected part of each clip, one after another. Stops by itself when the listener
 * seeks away, changes episode, or the last clip ends.
 */
object NotesReel {
    data class State(
        val episodeUuid: String,
        val clipIds: List<String>,
        val index: Int,
    ) {
        val currentClipId: String get() = clipIds[index]
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    fun start(playbackManager: PlaybackManager, clips: List<NoteClip>, fromClipId: String? = null) {
        stop()
        val ordered = clips.filter { it.status == NoteClipStatus.Ready && it.selectionEndMs > it.selectionStartMs }
            .sortedBy { it.selectionStartMs }
        if (ordered.isEmpty()) return
        val startIndex = ordered.indexOfFirst { it.id == fromClipId }.coerceAtLeast(0)
        val episodeUuid = ordered.first().episodeUuid
        job = scope.launch {
            for (index in startIndex until ordered.size) {
                val clip = ordered[index]
                _state.value = State(episodeUuid, ordered.map { it.id }, index)
                if (playbackManager.getCurrentEpisode()?.uuid != clip.episodeUuid) {
                    playbackManager.playNowSuspend(episodeUuid = clip.episodeUuid)
                }
                playbackManager.seekToTimeMsSuspend(clip.selectionStartMs.toInt())
                playbackManager.playIfNotPlaying()

                // Wait for the seek to land before watching for the end of the clip.
                withTimeoutOrNull(SEEK_SETTLE_MS) {
                    playbackManager.playbackStateFlow.first { state ->
                        state.episodeUuid == clip.episodeUuid &&
                            state.positionMs >= clip.selectionStartMs - SLACK_MS &&
                            state.positionMs <= clip.selectionEndMs
                    }
                }
                val stopState = playbackManager.playbackStateFlow.first { state ->
                    state.episodeUuid != clip.episodeUuid ||
                        state.positionMs >= clip.selectionEndMs ||
                        state.positionMs < clip.selectionStartMs - LEFT_CLIP_MS ||
                        state.positionMs > clip.selectionEndMs + LEFT_CLIP_MS
                }
                val leftClip = stopState.episodeUuid != clip.episodeUuid ||
                    stopState.positionMs < clip.selectionStartMs - LEFT_CLIP_MS ||
                    stopState.positionMs > clip.selectionEndMs + LEFT_CLIP_MS
                if (leftClip) {
                    _state.value = null
                    return@launch
                }
            }
            playbackManager.pause()
            _state.value = null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = null
    }

    private const val SEEK_SETTLE_MS = 5_000L
    private const val SLACK_MS = 1_500L
    private const val LEFT_CLIP_MS = 8_000L
}
