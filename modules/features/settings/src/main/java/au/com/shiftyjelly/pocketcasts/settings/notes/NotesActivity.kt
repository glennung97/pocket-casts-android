package au.com.shiftyjelly.pocketcasts.settings.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClip
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStore
import au.com.shiftyjelly.pocketcasts.repositories.notes.SaveToNotes
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackState
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** The notebook for clips saved with "save to notes", plus its settings. */
@AndroidEntryPoint
class NotesActivity : AppCompatActivity() {
    @Inject
    lateinit var theme: Theme

    @Inject
    lateinit var playbackManager: PlaybackManager

    private val backStack = mutableStateListOf<NotesScreen>(NotesScreen.Notebook)
    private var sheetClipId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openClipFrom(intent)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goBack()
            },
        )
        setContentView(
            ComposeView(this).apply {
                setContent {
                    val playbackState by playbackManager.playbackStateFlow.collectAsState(initial = PlaybackState())
                    AppThemeWithBackground(theme.activeTheme) {
                        NotesApp(
                            screen = backStack.last(),
                            sheetClipId = sheetClipId,
                            playback = NotePlayback(playbackState.episodeUuid, playbackState.positionMs.toLong()),
                            onNavigate = { backStack.add(it) },
                            onOpenClip = { sheetClipId = it },
                            onCloseClip = { sheetClipId = null },
                            onPlay = { clip -> play(clip) },
                            onPlayFrom = { clip, positionMs ->
                                lifecycleScope.launch { SaveToNotes.playFrom(playbackManager, clip.episodeUuid, positionMs) }
                            },
                            onBack = this@NotesActivity::goBack,
                        )
                    }
                }
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openClipFrom(intent)
    }

    private fun openClipFrom(intent: Intent) {
        val clipId = intent.getStringExtra(SaveToNotes.EXTRA_CLIP_ID) ?: return
        val clip = NoteClipStore.get(this).get(clipId) ?: return
        backStack.clear()
        backStack.add(NotesScreen.Notebook)
        backStack.add(NotesScreen.Episode(episodeUuid = clip.episodeUuid, focusClipId = clip.id))
    }

    private fun play(clip: NoteClip) {
        lifecycleScope.launch { SaveToNotes.playClip(playbackManager, clip) }
    }

    private fun goBack() {
        when {
            sheetClipId != null -> sheetClipId = null
            backStack.size > 1 -> backStack.removeAt(backStack.lastIndex)
            else -> finish()
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, NotesActivity::class.java)
    }
}

/** Shows one clip as a pop-up over the player right after it is saved. */
@AndroidEntryPoint
class NoteClipSheetActivity : ComponentActivity() {
    @Inject
    lateinit var theme: Theme

    @Inject
    lateinit var playbackManager: PlaybackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clipId = intent.getStringExtra(SaveToNotes.EXTRA_CLIP_ID)
        if (clipId == null) {
            finish()
            return
        }
        setContentView(
            ComposeView(this).apply {
                setContent {
                    val playbackState by playbackManager.playbackStateFlow.collectAsState(initial = PlaybackState())
                    AppTheme(theme.activeTheme) {
                        NoteClipSheet(
                            clipId = clipId,
                            playback = NotePlayback(playbackState.episodeUuid, playbackState.positionMs.toLong()),
                            onDismiss = { finish() },
                            onPlay = { clip -> lifecycleScope.launch { SaveToNotes.playClip(playbackManager, clip) } },
                            onPlayFrom = { clip, positionMs ->
                                lifecycleScope.launch { SaveToNotes.playFrom(playbackManager, clip.episodeUuid, positionMs) }
                            },
                            onOpenNotebook = {
                                startActivity(NotesActivity.intent(this@NoteClipSheetActivity).putExtra(SaveToNotes.EXTRA_CLIP_ID, clipId))
                                finish()
                            },
                        )
                    }
                }
            },
        )
    }
}

sealed interface NotesScreen {
    data object Notebook : NotesScreen
    data class Episode(val episodeUuid: String, val focusClipId: String?) : NotesScreen
    data object Settings : NotesScreen
    data object TickTickSignIn : NotesScreen
}
