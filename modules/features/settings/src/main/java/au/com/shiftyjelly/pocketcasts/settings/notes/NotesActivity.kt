package au.com.shiftyjelly.pocketcasts.settings.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStore
import au.com.shiftyjelly.pocketcasts.repositories.notes.SaveToNotes
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The notebook for clips saved with "save to notes", plus its settings. */
@AndroidEntryPoint
class NotesActivity : AppCompatActivity() {
    @Inject
    lateinit var theme: Theme

    private val backStack = mutableStateListOf<NotesScreen>(NotesScreen.Notebook)

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
                    AppThemeWithBackground(theme.activeTheme) {
                        NotesApp(
                            screen = backStack.last(),
                            onNavigate = { backStack.add(it) },
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

    private fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            finish()
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, NotesActivity::class.java)
    }
}

sealed interface NotesScreen {
    data object Notebook : NotesScreen
    data class Episode(val episodeUuid: String, val focusClipId: String?) : NotesScreen
    data object Settings : NotesScreen
    data object TickTickSignIn : NotesScreen
}
