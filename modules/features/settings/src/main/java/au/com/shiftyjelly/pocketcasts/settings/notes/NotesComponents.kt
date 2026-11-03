package au.com.shiftyjelly.pocketcasts.settings.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.components.TextH70
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClip
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipMode
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStatus
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStore
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteSentence
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteTranscript
import au.com.shiftyjelly.pocketcasts.repositories.notes.SaveToNotes

/** A small rounded label, tinted with [color]. */
@Composable
internal fun NotePill(text: String, color: Color, modifier: Modifier = Modifier) {
    TextH70(
        text = text,
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun ClipStatusPill(clip: NoteClip) {
    val colors = MaterialTheme.theme.colors
    when {
        clip.status == NoteClipStatus.Processing -> NotePill("轉錄中", colors.primaryText02)
        clip.status == NoteClipStatus.Failed -> NotePill("失敗", colors.support05)
        clip.isDone -> NotePill("已整理", colors.primaryText02)
        else -> NotePill("待整理", colors.primaryInteractive01)
    }
}

internal fun NoteClip.rangeText(): String {
    return if (status == NoteClipStatus.Ready) {
        "${NoteTranscript.formatTime(selectionStartMs)}–${NoteTranscript.formatTime(selectionEndMs)}"
    } else {
        NoteTranscript.formatTime(bookmarkMs)
    }
}

internal fun NoteClip.displayTitle(): String = title.ifBlank { "書籤 ${NoteTranscript.formatTime(bookmarkMs)}" }

internal fun NoteClip.excerpt(): String = selectedSentences.joinToString("") { it.text }

internal fun NoteClip.isInSelection(sentence: NoteSentence): Boolean {
    return sentence.endMs > selectionStartMs && sentence.startMs < selectionEndMs
}

/** Tapping inside the selection toggles a highlight; tapping outside stretches the selection to that sentence. */
internal fun toggleSentence(context: Context, clip: NoteClip, sentence: NoteSentence) {
    val store = NoteClipStore.get(context)
    if (clip.isInSelection(sentence)) {
        store.update(clip.id) { current ->
            val starts = if (sentence.startMs in current.highlightStarts) {
                current.highlightStarts - sentence.startMs
            } else {
                current.highlightStarts + sentence.startMs
            }
            current.copy(highlightStarts = starts)
        }
    } else {
        store.update(clip.id) { current ->
            current.copy(
                selectionStartMs = minOf(current.selectionStartMs, sentence.startMs),
                selectionEndMs = maxOf(current.selectionEndMs, sentence.endMs),
            )
        }
    }
}

internal fun extendClip(context: Context, clip: NoteClip, mode: NoteClipMode) {
    NoteClipStore.get(context).update(clip.id) { it.copy(status = NoteClipStatus.Processing) }
    SaveToNotes.enqueue(context, clip.id, mode)
}

internal fun retryClip(context: Context, clip: NoteClip) {
    NoteClipStore.get(context).update(clip.id) { it.copy(status = NoteClipStatus.Processing, error = null) }
    SaveToNotes.enqueue(context, clip.id, if (clip.words.isEmpty()) NoteClipMode.Initial else NoteClipMode.Reanalyze)
}

internal fun deleteClip(context: Context, clipId: String) {
    NoteClipStore.get(context).delete(clipId)
    SaveToNotes.cancel(context, clipId)
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("聽記", text))
    Toast.makeText(context, "已複製", Toast.LENGTH_SHORT).show()
}
