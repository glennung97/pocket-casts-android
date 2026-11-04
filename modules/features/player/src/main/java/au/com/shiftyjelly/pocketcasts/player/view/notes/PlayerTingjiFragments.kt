package au.com.shiftyjelly.pocketcasts.player.view.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.lifecycle.lifecycleScope
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.PlayerColors
import au.com.shiftyjelly.pocketcasts.compose.PodcastColors
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesReel
import au.com.shiftyjelly.pocketcasts.repositories.notes.SaveToNotes
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackState
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import au.com.shiftyjelly.pocketcasts.settings.notes.NotesActivity
import au.com.shiftyjelly.pocketcasts.settings.notes.PlayerNotesColors
import au.com.shiftyjelly.pocketcasts.settings.notes.PlayerNotesPage
import au.com.shiftyjelly.pocketcasts.settings.notes.PlayerTranscriptPage
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

/** The same podcast-tinted palette the player itself uses, in light and dark app themes alike. */
@Composable
private fun rememberPlayerNotesColors(themeType: Theme.ThemeType, podcast: Podcast?): PlayerNotesColors {
    val playerColors = remember(themeType, podcast?.uuid, podcast?.backgroundColor) {
        PlayerColors(themeType, podcast?.let { PodcastColors(it) } ?: PodcastColors.ForUserEpisode)
    }
    return remember(playerColors) {
        PlayerNotesColors(
            background = playerColors.background01,
            primaryText = playerColors.contrast01,
            secondaryText = playerColors.contrast02,
            accent = playerColors.highlight01,
            accentText = playerColors.background01,
            card = playerColors.contrast06,
        )
    }
}

/** The 筆記 tab in the full-screen player. */
@AndroidEntryPoint
class PlayerTingjiNotesFragment : BaseFragment() {
    @Inject
    lateinit var playbackManager: PlaybackManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        val state by playbackManager.playbackStateFlow.collectAsState(initial = PlaybackState())
        AppTheme(theme.activeTheme) {
            PlayerNotesPage(
                episodeUuid = state.episodeUuid,
                positionMs = state.positionMs.toLong(),
                colors = rememberPlayerNotesColors(theme.activeTheme, state.podcast),
                onStartReel = { clips, fromClipId -> NotesReel.start(playbackManager, clips, fromClipId) },
                onStopReel = { NotesReel.stop() },
                onPlayFrom = { clip, positionMs ->
                    lifecycleScope.launch { SaveToNotes.playFrom(playbackManager, clip.episodeUuid, positionMs) }
                },
                onOpenClip = { clipId -> startActivity(SaveToNotes.clipSheetIntent(requireContext(), clipId)) },
                onOpenNotebook = { startActivity(NotesActivity.intent(requireContext())) },
                modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
            )
        }
    }
}

/** The 逐字稿 tab: the publisher's transcript following playback. Only added when the episode has one. */
@AndroidEntryPoint
class PlayerTingjiTranscriptFragment : BaseFragment() {
    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var transcriptManager: TranscriptManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        val state by playbackManager.playbackStateFlow.collectAsState(initial = PlaybackState())
        var transcript by remember { mutableStateOf<Transcript.Text?>(null) }
        LaunchedEffect(state.episodeUuid) {
            transcript = null
            if (state.episodeUuid.isBlank()) return@LaunchedEffect
            val loaded = try {
                transcriptManager.loadTranscript(state.episodeUuid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not load the transcript tab")
                null
            }
            // Pocket Casts' generated transcript is a Plus feature, so only the publisher's is shown here.
            transcript = (loaded as? Transcript.Text)?.takeIf { !it.isGenerated }
        }
        AppTheme(theme.activeTheme) {
            PlayerTranscriptPage(
                transcript = transcript,
                positionMs = state.positionMs.toLong(),
                colors = rememberPlayerNotesColors(theme.activeTheme, state.podcast),
                onSeek = { ms -> playbackManager.seekToTimeMs(ms.toInt()) },
                modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
            )
        }
    }
}
