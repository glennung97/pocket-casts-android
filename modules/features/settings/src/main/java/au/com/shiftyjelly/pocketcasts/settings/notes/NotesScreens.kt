package au.com.shiftyjelly.pocketcasts.settings.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClip
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipMode
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStatus
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStore
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteSentence
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteTranscript
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesConfig
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesExport
import au.com.shiftyjelly.pocketcasts.repositories.notes.SaveToNotes
import kotlinx.coroutines.launch

private val HighlightColor = Color(0x66FFC740)
private val ErrorColor = Color(0xFFD9534F)

@Composable
internal fun NotesApp(
    screen: NotesScreen,
    onNavigate: (NotesScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        when (screen) {
            NotesScreen.Notebook -> NotebookScreen(onNavigate = onNavigate, onBack = onBack)
            is NotesScreen.Episode -> EpisodeScreen(screen = screen, onNavigate = onNavigate, onBack = onBack)
            NotesScreen.Settings -> NotesSettingsScreen(onNavigate = onNavigate, onBack = onBack)
            NotesScreen.TickTickSignIn -> TickTickSignInScreen(onBack = onBack)
        }
    }
}

@Composable
private fun NotebookScreen(
    onNavigate: (NotesScreen) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clips by NoteClipStore.get(context).clips.collectAsState()
    val config = NotesConfig.get(context)
    val needsSetup = config.elevenLabsKey.isBlank() || config.effectiveProvider == null
    val episodes = clips.groupBy { it.episodeUuid }.values.toList()

    ThemedTopAppBar(
        title = "聽記筆記本",
        onNavigationClick = onBack,
        actions = { color ->
            TextButton(onClick = { onNavigate(NotesScreen.Settings) }) {
                Text("設定", color = color)
            }
        },
    )
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (needsSetup) {
            item {
                Banner(
                    text = "還沒填 API key。先到設定填入 ElevenLabs 和 Claude 或 OpenAI 的 key，存到筆記才會轉錄。",
                    action = "前往設定",
                    onClick = { onNavigate(NotesScreen.Settings) },
                )
            }
        }
        if (episodes.isEmpty()) {
            item {
                Text(
                    text = "在通知列的播放器按「存到筆記」，片段會出現在這裡。",
                    color = MaterialTheme.theme.colors.primaryText02,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        items(episodes, key = { it.first().episodeUuid }) { episodeClips ->
            EpisodeRow(
                clips = episodeClips,
                onClick = { onNavigate(NotesScreen.Episode(episodeUuid = episodeClips.first().episodeUuid, focusClipId = null)) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(clips: List<NoteClip>, onClick: () -> Unit) {
    val first = clips.first()
    val todo = clips.count { !it.isDone }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .cardStyle()
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        if (first.podcastTitle.isNotBlank()) {
            Text(first.podcastTitle, fontSize = 12.sp, color = MaterialTheme.theme.colors.primaryText02, maxLines = 1)
        }
        Text(
            first.episodeTitle,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.theme.colors.primaryText01,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val summary = buildString {
            append("${clips.size} 段")
            if (todo > 0) append(" · 待整理 $todo")
            if (clips.any { it.status == NoteClipStatus.Processing }) append(" · 轉錄中")
            if (clips.any { it.status == NoteClipStatus.Failed }) append(" · 有失敗")
        }
        Text(summary, fontSize = 12.sp, color = MaterialTheme.theme.colors.primaryText02)
    }
}

@Composable
private fun ColumnScope.EpisodeScreen(
    screen: NotesScreen.Episode,
    onNavigate: (NotesScreen) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allClips by NoteClipStore.get(context).clips.collectAsState()
    val clips = allClips.filter { it.episodeUuid == screen.episodeUuid }.sortedBy { it.bookmarkMs }
    var message by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var askAboutTodo by remember { mutableStateOf(false) }

    LaunchedEffect(clips.isEmpty()) {
        if (clips.isEmpty()) onBack()
    }
    if (clips.isEmpty()) return

    fun send(toSend: List<NoteClip>) {
        if (toSend.isEmpty()) {
            message = "沒有已整理的片段可以送"
            return
        }
        sending = true
        message = null
        scope.launch {
            message = try {
                NotesExport.sendToTickTick(context, toSend)
                "已送到 TickTick（${toSend.size} 段）"
            } catch (e: Exception) {
                NotesExport.userMessage(e)
            }
            sending = false
        }
    }

    val readyClips = clips.filter { it.status == NoteClipStatus.Ready }
    ThemedTopAppBar(
        title = clips.first().episodeTitle,
        onNavigationClick = onBack,
    )
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f),
    ) {
        items(clips, key = { it.id }) { clip ->
            ClipCard(clip = clip, initiallyExpanded = clip.id == screen.focusClipId || clips.size == 1)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.theme.colors.primaryUi01)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.theme.colors.primaryText02) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    copyToClipboard(context, NotesExport.markdown(readyClips))
                },
                enabled = readyClips.isNotEmpty(),
            ) { Text("複製") }
            Spacer(Modifier.weight(1f))
            if (!NotesConfig.get(context).isTickTickConnected) {
                TextButton(onClick = { onNavigate(NotesScreen.Settings) }) { Text("連接 TickTick") }
            } else {
                Button(
                    onClick = {
                        if (readyClips.any { !it.isDone }) askAboutTodo = true else send(readyClips)
                    },
                    enabled = !sending && readyClips.isNotEmpty(),
                ) { Text(if (sending) "傳送中…" else "送到 TickTick") }
            }
        }
    }

    if (askAboutTodo) {
        val todoCount = readyClips.count { !it.isDone }
        AlertDialog(
            onDismissRequest = { askAboutTodo = false },
            title = { Text("還有 $todoCount 段待整理") },
            text = { Text("要一起送出，還是只送已整理的片段？") },
            confirmButton = {
                TextButton(onClick = {
                    askAboutTodo = false
                    send(readyClips)
                }) { Text("一起送") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askAboutTodo = false
                    send(readyClips.filter { it.isDone })
                }) { Text("只送已整理") }
            },
        )
    }
}

@Composable
private fun ClipCard(clip: NoteClip, initiallyExpanded: Boolean) {
    val context = LocalContext.current
    val store = NoteClipStore.get(context)
    var expanded by remember(clip.id) { mutableStateOf(initiallyExpanded) }
    var editingNote by remember(clip.id) { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .cardStyle()
            .padding(14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val range = if (clip.status == NoteClipStatus.Ready) {
                    "${NoteTranscript.formatTime(clip.selectionStartMs)}–${NoteTranscript.formatTime(clip.selectionEndMs)}"
                } else {
                    NoteTranscript.formatTime(clip.bookmarkMs)
                }
                Text(range, fontSize = 12.sp, color = MaterialTheme.theme.colors.primaryText02)
                Spacer(Modifier.weight(1f))
                StatusChip(clip)
            }
            Text(
                clip.title.ifBlank { "書籤 ${NoteTranscript.formatTime(clip.bookmarkMs)}" },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.theme.colors.primaryText01,
            )
        }

        when (clip.status) {
            NoteClipStatus.Processing -> Text("轉錄中…", color = MaterialTheme.theme.colors.primaryText02)
            NoteClipStatus.Failed -> {
                Text(clip.error ?: "發生錯誤", color = ErrorColor, fontSize = 13.sp)
                Row {
                    TextButton(onClick = {
                        store.update(clip.id) { it.copy(status = NoteClipStatus.Processing, error = null) }
                        SaveToNotes.enqueue(context, clip.id, if (clip.words.isEmpty()) NoteClipMode.Initial else NoteClipMode.Reanalyze)
                    }) { Text("重試") }
                    TextButton(onClick = { deleteClip(context, clip.id) }) { Text("刪除") }
                }
            }
            NoteClipStatus.Ready -> if (expanded) {
                clip.aiReason?.let { reason ->
                    Text("AI 補轉的原因：$reason", fontSize = 12.sp, color = MaterialTheme.theme.colors.primaryText02)
                }
                ExtendButton(text = "↑ +30s") { extend(context, clip, NoteClipMode.ExtendBefore) }
                Transcript(
                    clip = clip,
                    onSentenceClick = { sentence -> onSentenceClick(context, clip, sentence) },
                )
                ExtendButton(text = "↓ +30s") { extend(context, clip, NoteClipMode.ExtendAfter) }
                if (clip.keyPoints.isNotEmpty()) {
                    Text("重點", fontSize = 12.sp, color = MaterialTheme.theme.colors.primaryText02)
                    clip.keyPoints.forEach { point ->
                        Text("• $point", color = MaterialTheme.theme.colors.primaryText01)
                    }
                }
                if (editingNote || clip.note.isNotBlank()) {
                    NoteField(
                        value = clip.note,
                        onValueChange = { text -> store.update(clip.id) { it.copy(note = text) } },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!editingNote && clip.note.isBlank()) {
                        TextButton(onClick = { editingNote = true }) { Text("＋ 筆記") }
                    }
                    TextButton(onClick = { deleteClip(context, clip.id) }) { Text("刪除") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { store.update(clip.id) { it.copy(isDone = !it.isDone) } }) {
                        Text(if (clip.isDone) "改回待整理" else "完成")
                    }
                }
            } else {
                val preview = clip.selectedSentences.joinToString("") { it.text }
                Text(
                    preview,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = MaterialTheme.theme.colors.primaryText02,
                )
            }
        }
    }
}

@Composable
private fun Transcript(clip: NoteClip, onSentenceClick: (NoteSentence) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "點句子：範圍內切換重點，範圍外把範圍延伸到那句",
            fontSize = 11.sp,
            color = MaterialTheme.theme.colors.primaryText02,
        )
        var lastSpeaker: String? = null
        clip.sentences.forEach { sentence ->
            val inRange = sentence.endMs > clip.selectionStartMs && sentence.startMs < clip.selectionEndMs
            val highlighted = clip.isHighlighted(sentence)
            val speakerLabel = if (sentence.speaker != null && sentence.speaker != lastSpeaker) clip.speakerName(sentence.speaker) else null
            lastSpeaker = sentence.speaker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSentenceClick(sentence) }
                    .background(if (highlighted) HighlightColor else Color.Transparent, RoundedCornerShape(4.dp))
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
                Text(
                    NoteTranscript.formatTime(sentence.startMs),
                    fontSize = 10.sp,
                    color = MaterialTheme.theme.colors.primaryText02,
                    modifier = Modifier
                        .width(40.dp)
                        .padding(top = 2.dp),
                )
                Column {
                    if (speakerLabel != null) {
                        Text(speakerLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.theme.colors.primaryText02)
                    }
                    Text(
                        sentence.text,
                        color = if (inRange) MaterialTheme.theme.colors.primaryText01 else MaterialTheme.theme.colors.primaryText02.copy(alpha = 0.6f),
                        fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtendButton(text: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            fontSize = 11.sp,
            color = MaterialTheme.theme.colors.primaryInteractive01,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun StatusChip(clip: NoteClip) {
    val (label, color) = when {
        clip.status == NoteClipStatus.Processing -> "轉錄中" to MaterialTheme.theme.colors.primaryText02
        clip.status == NoteClipStatus.Failed -> "失敗" to ErrorColor
        clip.isDone -> "已整理" to MaterialTheme.theme.colors.primaryInteractive01
        else -> "待整理" to Color(0xFFC98A00)
    }
    Text(
        label,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
internal fun Banner(text: String, action: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardStyle()
            .padding(14.dp),
    ) {
        Text(text, color = MaterialTheme.theme.colors.primaryText01, fontSize = 14.sp)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
internal fun Modifier.cardStyle(): Modifier = this
    .background(MaterialTheme.theme.colors.primaryUi01, RoundedCornerShape(12.dp))
    .border(1.dp, MaterialTheme.theme.colors.primaryUi05, RoundedCornerShape(12.dp))

private fun extend(context: Context, clip: NoteClip, mode: NoteClipMode) {
    NoteClipStore.get(context).update(clip.id) { it.copy(status = NoteClipStatus.Processing) }
    SaveToNotes.enqueue(context, clip.id, mode)
}

private fun onSentenceClick(context: Context, clip: NoteClip, sentence: NoteSentence) {
    val store = NoteClipStore.get(context)
    val inRange = sentence.endMs > clip.selectionStartMs && sentence.startMs < clip.selectionEndMs
    if (inRange) {
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

private fun deleteClip(context: Context, clipId: String) {
    NoteClipStore.get(context).delete(clipId)
    SaveToNotes.cancel(context, clipId)
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("聽記", text))
    Toast.makeText(context, "已複製", Toast.LENGTH_SHORT).show()
}
