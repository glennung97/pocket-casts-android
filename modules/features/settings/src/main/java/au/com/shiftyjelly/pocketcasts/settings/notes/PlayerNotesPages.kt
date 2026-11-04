package au.com.shiftyjelly.pocketcasts.settings.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.components.TextH30
import au.com.shiftyjelly.pocketcasts.compose.components.TextH40
import au.com.shiftyjelly.pocketcasts.compose.components.TextH50
import au.com.shiftyjelly.pocketcasts.compose.components.TextH70
import au.com.shiftyjelly.pocketcasts.compose.components.TextP50
import au.com.shiftyjelly.pocketcasts.compose.components.TextP60
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClip
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStatus
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStore
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteTranscript
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesReel
import au.com.shiftyjelly.pocketcasts.repositories.transcript.BookmarkTranscript
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TextSpan

/** Colors the player pages take from the player's podcast-tinted palette. */
data class PlayerNotesColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val accentText: Color,
    val card: Color,
)

/** Marks each saved clip on the player's seek bar: its range as a band and the bookmarked moment as a tick. */
@Composable
fun NoteSeekBarMarkers(
    episodeUuid: String,
    durationMs: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val allClips by NoteClipStore.get(context).clips.collectAsState()
    val clips = remember(allClips, episodeUuid) { allClips.filter { it.episodeUuid == episodeUuid } }
    if (clips.isEmpty() || durationMs <= 0) return
    // Matches the seek bar view: 4dp side padding and the track drawn 9dp from the top.
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp),
    ) {
        val inset = 4.dp.toPx()
        val trackWidth = size.width - inset * 2
        val centerY = 9.dp.toPx()
        fun x(ms: Long) = inset + trackWidth * (ms.toFloat() / durationMs).coerceIn(0f, 1f)
        clips.forEach { clip ->
            if (clip.status == NoteClipStatus.Ready && clip.selectionEndMs > clip.selectionStartMs) {
                val start = x(clip.selectionStartMs)
                val end = x(clip.selectionEndMs).coerceAtLeast(start + 2.dp.toPx())
                drawRect(
                    color = color.copy(alpha = 0.45f),
                    topLeft = Offset(start, centerY - 3.dp.toPx()),
                    size = Size(end - start, 6.dp.toPx()),
                )
            }
            val tickX = x(clip.bookmarkMs)
            drawRect(
                color = color,
                topLeft = Offset(tickX - 1.dp.toPx(), centerY - 7.dp.toPx()),
                size = Size(2.dp.toPx(), 14.dp.toPx()),
            )
        }
    }
}

/**
 * The 筆記 tab in the player: this episode's clips, a button to play all the highlights back to back, and the
 * sentence being played lit up as it plays.
 */
@Composable
fun PlayerNotesPage(
    episodeUuid: String,
    positionMs: Long,
    colors: PlayerNotesColors,
    onStartReel: (List<NoteClip>, String?) -> Unit,
    onStopReel: () -> Unit,
    onPlayFrom: (NoteClip, Long) -> Unit,
    onOpenClip: (String) -> Unit,
    onOpenNotebook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val allClips by NoteClipStore.get(context).clips.collectAsState()
    val reel by NotesReel.state.collectAsState()
    val clips = remember(allClips, episodeUuid) {
        allClips.filter { it.episodeUuid == episodeUuid }.sortedBy { it.bookmarkMs }
    }
    val readyClips = clips.filter { it.status == NoteClipStatus.Ready }
    val reelForEpisode = reel?.takeIf { it.episodeUuid == episodeUuid }
    val totalMs = readyClips.sumOf { (it.selectionEndMs - it.selectionStartMs).coerceAtLeast(0) }
    val listState = rememberLazyListState()
    val playingClipId = reelForEpisode?.currentClipId
        ?: readyClips.firstOrNull { positionMs in it.selectionStartMs..it.selectionEndMs }?.id

    LaunchedEffect(playingClipId) {
        val index = clips.indexOfFirst { it.id == playingClipId }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                if (readyClips.isEmpty()) {
                    TextH40(text = "這一集還沒有筆記", color = colors.primaryText)
                    TextP60(
                        text = "播放時按下方的書籤按鈕，就會把前後兩分鐘轉成文字、抓出重點，存在這裡。",
                        color = colors.secondaryText,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            TextH40(text = "${readyClips.size} 段重點", color = colors.primaryText)
                            TextH70(
                                text = if (reelForEpisode != null) {
                                    "連播中 · 第 ${reelForEpisode.index + 1} / ${reelForEpisode.clipIds.size} 段"
                                } else {
                                    "全部連播約 ${NoteTranscript.formatTime(totalMs)}"
                                },
                                color = colors.secondaryText,
                            )
                        }
                        TextH50(
                            text = "筆記本",
                            color = colors.accent,
                            modifier = Modifier
                                .clickable(onClick = onOpenNotebook)
                                .padding(8.dp),
                        )
                    }
                    if (reelForEpisode != null) {
                        ReelProgress(reel = reelForEpisode, clips = readyClips, positionMs = positionMs, colors = colors)
                    }
                    TextH70(
                        text = "$TRANSCRIPT_GESTURE_HINT · 「編輯」調整範圍和筆記",
                        color = colors.secondaryText,
                    )
                    RowButton(
                        text = if (reelForEpisode != null) "停止連播" else "▶ 連播所有重點",
                        onClick = { if (reelForEpisode != null) onStopReel() else onStartReel(readyClips, null) },
                        includePadding = false,
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = colors.accent),
                        textColor = colors.accentText,
                    )
                }
            }
        }
        items(clips, key = { it.id }) { clip ->
            PlayerClipCard(
                clip = clip,
                positionMs = positionMs.takeIf { episodeUuid == clip.episodeUuid },
                isPlaying = clip.id == playingClipId,
                colors = colors,
                onPlay = { onStartReel(readyClips, clip.id) },
                onPlayFrom = { ms -> onPlayFrom(clip, ms) },
                onOpen = { onOpenClip(clip.id) },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ReelProgress(reel: NotesReel.State, clips: List<NoteClip>, positionMs: Long, colors: PlayerNotesColors) {
    val ordered = reel.clipIds.mapNotNull { id -> clips.firstOrNull { it.id == id } }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        ordered.forEachIndexed { index, clip ->
            val length = (clip.selectionEndMs - clip.selectionStartMs).coerceAtLeast(1)
            val progress = when {
                index < reel.index -> 1f
                index > reel.index -> 0f
                else -> ((positionMs - clip.selectionStartMs).toFloat() / length).coerceIn(0f, 1f)
            }
            Box(
                modifier = Modifier
                    .weight(length.toFloat())
                    .height(4.dp)
                    .background(colors.secondaryText.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(colors.accent, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun PlayerClipCard(
    clip: NoteClip,
    positionMs: Long?,
    isPlaying: Boolean,
    colors: PlayerNotesColors,
    onPlay: () -> Unit,
    onPlayFrom: (Long) -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPlaying) colors.accent.copy(alpha = 0.14f) else colors.card,
                RoundedCornerShape(12.dp),
            )
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            TextH70(text = clip.rangeText(), color = colors.accent, modifier = Modifier.weight(1f))
            if (clip.status == NoteClipStatus.Ready) {
                TextH70(
                    text = if (isPlaying) "播放中" else "▶ 從頭播",
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onPlay)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            TextH70(
                text = "編輯",
                color = colors.accent,
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        TextH50(text = clip.displayTitle(), color = colors.primaryText, modifier = Modifier.padding(horizontal = 14.dp))
        when (clip.status) {
            NoteClipStatus.Processing -> TextP60(
                text = "轉錄中…",
                color = colors.secondaryText,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            NoteClipStatus.Failed -> TextP60(
                text = clip.error ?: "發生錯誤",
                color = colors.secondaryText,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            NoteClipStatus.Ready -> {
                val model = remember(clip) { NoteTranscriptModel(clip) }
                NoteTranscriptView(
                    transcript = model.transcript,
                    passage = model.passage,
                    highlights = model.highlightSpans,
                    referenceOffset = model.bookmarkOffset,
                    currentSpan = positionMs?.let { model.spanAtTime(it) },
                    anchorFraction = 0.4f,
                    primaryTextColor = colors.primaryText,
                    secondaryTextColor = colors.secondaryText.copy(alpha = 0.7f),
                    editableHighlightColor = colors.accent,
                    onTextTap = { offset -> model.sentenceAt(offset)?.let { onPlayFrom(it.startMs) } },
                    onTextLongPress = { offset -> model.sentenceAt(offset)?.let { toggleSentence(context, clip, it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
                if (clip.note.isNotBlank()) {
                    TextP60(
                        text = "筆記：${clip.note}",
                        color = colors.primaryText,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            }
        }
    }
}

/**
 * The 逐字稿 tab: the publisher's transcript in the bookmark style, with the line being played lit and kept in
 * view, saved clips highlighted, and a tap on any line seeking there.
 */
@Composable
fun PlayerTranscriptPage(
    transcript: Transcript.Text?,
    positionMs: Long,
    colors: PlayerNotesColors,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (transcript == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(32.dp),
        ) {
            TextH30(text = "載入逐字稿中…", color = colors.primaryText, textAlign = TextAlign.Center)
        }
        return
    }
    val model = remember(transcript) { BookmarkTranscript.from(transcript) }
    val allClips by NoteClipStore.get(context).clips.collectAsState()
    val savedSpans = remember(allClips, model, transcript.episodeUuid) {
        allClips.filter { it.episodeUuid == transcript.episodeUuid && it.status == NoteClipStatus.Ready }
            .mapNotNull { clip ->
                val start = model.referenceOffsetAt(clip.selectionStartMs) ?: return@mapNotNull null
                val end = model.referenceOffsetAt(clip.selectionEndMs) ?: return@mapNotNull null
                TextSpan(start, maxOf(end, start + 1))
            }
    }
    val currentOffset = model.referenceOffsetAt(positionMs)
    val currentSpan = currentOffset?.let { model.sentenceDisplaySpan(it) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextH70(text = "節目方提供的逐字稿 · 點一下從這句播放", color = colors.secondaryText)
            Spacer(Modifier.width(4.dp))
        }
        NoteTranscriptView(
            transcript = model,
            passage = currentSpan,
            highlights = savedSpans,
            anchorFraction = 0.35f,
            primaryTextColor = colors.primaryText,
            secondaryTextColor = colors.secondaryText.copy(alpha = 0.7f),
            editableHighlightColor = colors.accent,
            onTextTap = { offset -> model.referenceTimeMsAt(offset)?.let(onSeek) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
