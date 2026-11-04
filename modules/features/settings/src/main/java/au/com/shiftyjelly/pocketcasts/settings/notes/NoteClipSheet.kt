package au.com.shiftyjelly.pocketcasts.settings.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowOutlinedButton
import au.com.shiftyjelly.pocketcasts.compose.buttons.TimePlayButton
import au.com.shiftyjelly.pocketcasts.compose.components.FormField
import au.com.shiftyjelly.pocketcasts.compose.components.PodcastImage
import au.com.shiftyjelly.pocketcasts.compose.components.TextC70
import au.com.shiftyjelly.pocketcasts.compose.components.TextH30
import au.com.shiftyjelly.pocketcasts.compose.components.TextH40
import au.com.shiftyjelly.pocketcasts.compose.components.TextH50
import au.com.shiftyjelly.pocketcasts.compose.components.TextH70
import au.com.shiftyjelly.pocketcasts.compose.components.TextP50
import au.com.shiftyjelly.pocketcasts.compose.components.TextP60
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClip
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipMode
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStatus
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStore
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteTranscript
import au.com.shiftyjelly.pocketcasts.repositories.notes.TranscriptSource
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TextSpan
import au.com.shiftyjelly.pocketcasts.localization.R as LR

/**
 * One saved clip as a bottom sheet over a dimmed background. Used over the player right after saving, and
 * over the notebook when a clip is opened there.
 */
@Composable
internal fun NoteClipSheet(
    clipId: String,
    playback: NotePlayback?,
    onDismiss: () -> Unit,
    onPlay: (NoteClip) -> Unit,
    onPlayFrom: (NoteClip, Long) -> Unit,
    onOpenNotebook: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clips by NoteClipStore.get(context).clips.collectAsState()
    val clip = clips.firstOrNull { it.id == clipId }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(clip == null) {
        if (clip == null) onDismiss()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.92f
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible && clip != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (clip != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight)
                        .background(MaterialTheme.theme.colors.primaryUi01, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        // Swallow taps so they do not reach the dimmed background.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    SheetHandle()
                    ClipHeader(clip = clip, onPlay = { onPlay(clip) })
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        when (clip.status) {
                            NoteClipStatus.Processing -> ProcessingContent()
                            NoteClipStatus.Failed -> FailedContent(clip)
                            NoteClipStatus.Ready -> ReadyContent(clip, playback, onPlayFrom)
                        }
                    }
                    SheetActions(clip = clip, onDismiss = onDismiss, onOpenNotebook = onOpenNotebook)
                }
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(MaterialTheme.theme.colors.primaryText02.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun ClipHeader(clip: NoteClip, onPlay: () -> Unit) {
    val playFromMs = if (clip.status == NoteClipStatus.Ready) clip.selectionStartMs else clip.bookmarkMs
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        PodcastImage(
            uuid = clip.podcastUuid,
            imageSize = 48.dp,
            cornerSize = 8.dp,
            elevation = null,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (clip.podcastTitle.isNotBlank()) {
                TextH70(text = clip.podcastTitle, color = MaterialTheme.theme.colors.primaryText02, maxLines = 1)
            }
            TextH50(text = clip.episodeTitle, maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        TimePlayButton(
            timeSecs = (playFromMs / 1000).toInt(),
            contentDescriptionId = LR.string.bookmark_play,
            onClick = onPlay,
        )
    }
}

@Composable
private fun ProcessingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.theme.colors.primaryInteractive01,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp),
        )
        TextH40(text = "正在轉錄前後兩分鐘", textAlign = TextAlign.Center)
        TextP60(
            text = "通常十幾秒到一分鐘。可以先關掉，完成後會出現在通知和筆記本。",
            color = MaterialTheme.theme.colors.primaryText02,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FailedContent(clip: NoteClip) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 16.dp)) {
        TextH40(text = "沒有存成功")
        TextP50(text = clip.error ?: "發生錯誤", color = MaterialTheme.theme.colors.support05)
    }
}

@Composable
private fun ReadyContent(clip: NoteClip, playback: NotePlayback?, onPlayFrom: (NoteClip, Long) -> Unit) {
    val context = LocalContext.current
    var editingNote by remember(clip.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextH30(text = clip.displayTitle())
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextH70(text = clip.rangeText(), color = MaterialTheme.theme.colors.primaryText02)
            Spacer(Modifier.width(8.dp))
            ClipStatusPill(clip)
        }
        clip.aiReason?.let { reason ->
            TextP60(text = "AI 已自動補轉：$reason", color = MaterialTheme.theme.colors.primaryText02)
        }
        if (clip.transcriptSource == TranscriptSource.Publisher) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextP60(text = "逐字稿來自節目方，", color = MaterialTheme.theme.colors.primaryText02)
                TextP60(
                    text = "時間對不上？改用 ElevenLabs",
                    color = MaterialTheme.theme.colors.primaryInteractive01,
                    modifier = Modifier.clickable { extendClip(context, clip, NoteClipMode.RetranscribeWithElevenLabs) },
                )
            }
        }
    }

    TranscriptCard(clip, playback, onPlayFrom)

    if (clip.keyPoints.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextC70(text = "重點", isUpperCase = false)
            clip.keyPoints.forEach { point ->
                Row {
                    TextP50(text = "•", color = MaterialTheme.theme.colors.primaryInteractive01)
                    Spacer(Modifier.width(8.dp))
                    TextP50(text = point)
                }
            }
        }
    }

    if (editingNote || clip.note.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextC70(text = "我的筆記", isUpperCase = false)
            var text by remember(clip.id) { mutableStateOf(clip.note) }
            FormField(
                value = text,
                placeholder = "想到什麼就寫下來",
                onValueChange = { value ->
                    text = value
                    NoteClipStore.get(context).update(clip.id) { it.copy(note = value) }
                },
                singleLine = false,
            )
        }
    } else {
        TextH50(
            text = "＋ 加筆記",
            color = MaterialTheme.theme.colors.primaryInteractive01,
            modifier = Modifier
                .clickable { editingNote = true }
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun TranscriptCard(clip: NoteClip, playback: NotePlayback?, onPlayFrom: (NoteClip, Long) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.theme.colors
    val model = remember(clip) { NoteTranscriptModel(clip) }
    var editingRange by remember(clip.id) { mutableStateOf(false) }
    var editedPassage by remember(clip.id) { mutableStateOf<TextSpan?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.primaryUi02, RoundedCornerShape(12.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
        ) {
            TextC70(text = if (editingRange) "調整範圍" else "逐字稿", isUpperCase = false, modifier = Modifier.weight(1f))
            if (!editingRange) {
                ExtendLink(text = "↑ +30s") { extendClip(context, clip, NoteClipMode.ExtendBefore) }
            }
            ExtendLink(text = if (editingRange) "完成" else "調整範圍") {
                if (editingRange) {
                    editedPassage?.let(model::selectionFor)?.let { (startMs, endMs) ->
                        NoteClipStore.get(context).update(clip.id) { it.copy(selectionStartMs = startMs, selectionEndMs = endMs) }
                    }
                    editedPassage = null
                }
                editingRange = !editingRange
            }
        }
        NoteTranscriptView(
            transcript = model.transcript,
            passage = if (editingRange) editedPassage ?: model.passage else model.passage,
            editable = editingRange,
            referenceOffset = model.bookmarkOffset,
            highlights = model.highlightSpans,
            currentSpan = playback?.takeIf { it.episodeUuid == clip.episodeUuid }?.let { model.spanAtTime(it.positionMs) },
            onTextTap = { offset -> model.sentenceAt(offset)?.let { onPlayFrom(clip, it.startMs) } },
            onTextLongPress = { offset -> model.sentenceAt(offset)?.let { toggleSentence(context, clip, it) } },
            onPassageChange = { span -> editedPassage = span },
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        ) {
            TextH70(
                text = if (editingRange) "拖兩端的把手，或長按後拖曳" else TRANSCRIPT_GESTURE_HINT,
                color = colors.primaryText02,
                modifier = Modifier.weight(1f),
            )
            if (!editingRange) {
                ExtendLink(text = "↓ +30s") { extendClip(context, clip, NoteClipMode.ExtendAfter) }
            }
        }
    }
}

@Composable
private fun ExtendLink(text: String, onClick: () -> Unit) {
    TextH70(
        text = text,
        color = MaterialTheme.theme.colors.primaryInteractive01,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun SheetActions(clip: NoteClip, onDismiss: () -> Unit, onOpenNotebook: (() -> Unit)?) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (clip.status) {
                NoteClipStatus.Failed -> {
                    RowOutlinedButton(
                        text = "刪除",
                        onClick = {
                            deleteClip(context, clip.id)
                            onDismiss()
                        },
                        includePadding = false,
                        modifier = Modifier.weight(1f),
                    )
                    RowButton(
                        text = "重試",
                        onClick = { retryClip(context, clip) },
                        includePadding = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                NoteClipStatus.Processing -> {
                    RowOutlinedButton(
                        text = "取消",
                        onClick = {
                            deleteClip(context, clip.id)
                            onDismiss()
                        },
                        includePadding = false,
                        modifier = Modifier.weight(1f),
                    )
                    RowButton(
                        text = "先關掉",
                        onClick = onDismiss,
                        includePadding = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                NoteClipStatus.Ready -> {
                    RowOutlinedButton(
                        text = "刪除",
                        onClick = {
                            deleteClip(context, clip.id)
                            onDismiss()
                        },
                        includePadding = false,
                        modifier = Modifier.weight(1f),
                    )
                    RowButton(
                        text = if (clip.isDone) "改回待整理" else "完成",
                        onClick = {
                            NoteClipStore.get(context).update(clip.id) { it.copy(isDone = !it.isDone) }
                            if (!clip.isDone) onDismiss()
                        },
                        includePadding = false,
                        modifier = Modifier.weight(2f),
                    )
                }
            }
        }
        if (onOpenNotebook != null) {
            TextH50(
                text = "打開聽記筆記本",
                color = MaterialTheme.theme.colors.primaryInteractive01,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNotebook)
                    .padding(vertical = 10.dp),
            )
        }
    }
}
