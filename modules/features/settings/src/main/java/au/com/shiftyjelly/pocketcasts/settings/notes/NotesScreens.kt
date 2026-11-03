package au.com.shiftyjelly.pocketcasts.settings.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowOutlinedButton
import au.com.shiftyjelly.pocketcasts.compose.components.HorizontalDivider
import au.com.shiftyjelly.pocketcasts.compose.components.NoContentBanner
import au.com.shiftyjelly.pocketcasts.compose.components.PodcastImage
import au.com.shiftyjelly.pocketcasts.compose.components.TextC70
import au.com.shiftyjelly.pocketcasts.compose.components.TextH40
import au.com.shiftyjelly.pocketcasts.compose.components.TextH50
import au.com.shiftyjelly.pocketcasts.compose.components.TextH70
import au.com.shiftyjelly.pocketcasts.compose.components.TextP50
import au.com.shiftyjelly.pocketcasts.compose.components.TextP60
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClip
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStatus
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStore
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteTranscript
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesConfig
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesExport
import kotlinx.coroutines.launch
import au.com.shiftyjelly.pocketcasts.images.R as IR

@Composable
internal fun NotesApp(
    screen: NotesScreen,
    sheetClipId: String?,
    onNavigate: (NotesScreen) -> Unit,
    onOpenClip: (String) -> Unit,
    onCloseClip: () -> Unit,
    onPlay: (NoteClip) -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (screen) {
                NotesScreen.Notebook -> NotebookScreen(onNavigate = onNavigate, onBack = onBack)
                is NotesScreen.Episode -> EpisodeScreen(screen = screen, onNavigate = onNavigate, onOpenClip = onOpenClip, onBack = onBack)
                NotesScreen.Settings -> NotesSettingsScreen(onNavigate = onNavigate, onBack = onBack)
                NotesScreen.TickTickSignIn -> TickTickSignInScreen(onBack = onBack)
            }
        }
        if (sheetClipId != null) {
            NoteClipSheet(clipId = sheetClipId, onDismiss = onCloseClip, onPlay = onPlay)
        }
    }
}

@Composable
private fun ColumnScope.NotebookScreen(
    onNavigate: (NotesScreen) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clips by NoteClipStore.get(context).clips.collectAsState()
    val config = NotesConfig.get(context)
    val needsSetup = config.elevenLabsKey.isBlank() || config.effectiveProvider == null
    val episodes = clips.groupBy { it.episodeUuid }.values.toList()
    val (todoEpisodes, doneEpisodes) = episodes.partition { group -> group.any { !it.isDone } }

    ThemedTopAppBar(
        title = "聽記筆記本",
        onNavigationClick = onBack,
        actions = { color ->
            IconButton(onClick = { onNavigate(NotesScreen.Settings) }) {
                Icon(
                    painter = painterResource(IR.drawable.ic_profile_settings),
                    contentDescription = "聽記設定",
                    tint = color,
                )
            }
        },
    )
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        if (needsSetup) {
            item {
                SetupCard(onClick = { onNavigate(NotesScreen.Settings) })
            }
        }
        if (episodes.isEmpty()) {
            item {
                NoContentBanner(
                    title = "還沒有存任何片段",
                    body = "聽 podcast 時，按播放頁下方的書籤或通知列的「存到筆記」，前後兩分鐘會自動轉成文字、抓出重點。",
                    iconResourceId = IR.drawable.ic_bookmark,
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        if (todoEpisodes.isNotEmpty()) {
            item { SectionHeader("待整理") }
            items(todoEpisodes, key = { "todo_" + it.first().episodeUuid }) { group ->
                EpisodeRow(clips = group) {
                    onNavigate(NotesScreen.Episode(episodeUuid = group.first().episodeUuid, focusClipId = null))
                }
            }
        }
        if (doneEpisodes.isNotEmpty()) {
            item { SectionHeader("已整理") }
            items(doneEpisodes, key = { "done_" + it.first().episodeUuid }) { group ->
                EpisodeRow(clips = group) {
                    onNavigate(NotesScreen.Episode(episodeUuid = group.first().episodeUuid, focusClipId = null))
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun SetupCard(onClick: () -> Unit) {
    val colors = MaterialTheme.theme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .background(colors.primaryInteractive01.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        TextH50(text = "先填 API key")
        TextP60(
            text = "存到筆記需要 ElevenLabs，以及 Claude 或 OpenAI 的 key。點這裡設定。",
            color = colors.primaryText02,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    TextC70(
        text = text,
        isUpperCase = false,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun EpisodeRow(clips: List<NoteClip>, onClick: () -> Unit) {
    val first = clips.first()
    val todo = clips.count { !it.isDone }
    val colors = MaterialTheme.theme.colors
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PodcastImage(uuid = first.podcastUuid, imageSize = 56.dp, cornerSize = 8.dp, elevation = null)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                if (first.podcastTitle.isNotBlank()) {
                    TextH70(text = first.podcastTitle, color = colors.primaryText02, maxLines = 1)
                }
                TextH40(text = first.episodeTitle, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    TextH70(text = "${clips.size} 段", color = colors.primaryText02)
                    if (todo > 0) {
                        Spacer(Modifier.width(8.dp))
                        NotePill("待整理 $todo", colors.primaryInteractive01)
                    }
                    if (clips.any { it.status == NoteClipStatus.Processing }) {
                        Spacer(Modifier.width(8.dp))
                        NotePill("轉錄中", colors.primaryText02)
                    }
                    if (clips.any { it.status == NoteClipStatus.Failed }) {
                        Spacer(Modifier.width(8.dp))
                        NotePill("有失敗", colors.support05)
                    }
                }
            }
        }
        HorizontalDivider(startIndent = 84.dp)
    }
}

@Composable
private fun ColumnScope.EpisodeScreen(
    screen: NotesScreen.Episode,
    onNavigate: (NotesScreen) -> Unit,
    onOpenClip: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allClips by NoteClipStore.get(context).clips.collectAsState()
    val clips = allClips.filter { it.episodeUuid == screen.episodeUuid }.sortedBy { it.bookmarkMs }
    var message by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var askAboutTodo by remember { mutableStateOf(false) }
    val colors = MaterialTheme.theme.colors

    LaunchedEffect(clips.isEmpty()) {
        if (clips.isEmpty()) onBack()
    }
    LaunchedEffect(screen.focusClipId) {
        screen.focusClipId?.let(onOpenClip)
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

    val first = clips.first()
    val readyClips = clips.filter { it.status == NoteClipStatus.Ready }
    val todoCount = readyClips.count { !it.isDone }

    ThemedTopAppBar(title = first.podcastTitle.ifBlank { "單集筆記" }, onNavigationClick = onBack)
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                PodcastImage(uuid = first.podcastUuid, imageSize = 64.dp, cornerSize = 8.dp, elevation = null)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    TextH40(text = first.episodeTitle, maxLines = 3)
                    TextH70(
                        text = buildString {
                            append("${clips.size} 段")
                            if (todoCount > 0) append(" · 待整理 $todoCount")
                        },
                        color = colors.primaryText02,
                    )
                }
            }
            HorizontalDivider()
        }
        items(clips, key = { it.id }) { clip ->
            ClipRow(clip = clip, onClick = { onOpenClip(clip.id) })
        }
    }
    HorizontalDivider()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.primaryUi01)
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        message?.let { TextP60(text = it, color = colors.primaryText02) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RowOutlinedButton(
                text = "複製",
                onClick = {
                    if (readyClips.isNotEmpty()) copyToClipboard(context, NotesExport.markdown(readyClips))
                },
                includePadding = false,
                modifier = Modifier.weight(1f),
            )
            if (NotesConfig.get(context).isTickTickConnected) {
                RowButton(
                    text = if (sending) "傳送中…" else "送到 TickTick",
                    onClick = { if (todoCount > 0) askAboutTodo = true else send(readyClips) },
                    enabled = !sending && readyClips.isNotEmpty(),
                    includePadding = false,
                    modifier = Modifier.weight(2f),
                )
            } else {
                RowButton(
                    text = "連接 TickTick",
                    onClick = { onNavigate(NotesScreen.Settings) },
                    includePadding = false,
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }

    if (askAboutTodo) {
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
private fun ClipRow(clip: NoteClip, onClick: () -> Unit) {
    val colors = MaterialTheme.theme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            TextH70(
                text = NoteTranscript.formatTime(if (clip.status == NoteClipStatus.Ready) clip.selectionStartMs else clip.bookmarkMs),
                color = colors.primaryInteractive01,
                modifier = Modifier
                    .width(56.dp)
                    .padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextH50(text = clip.displayTitle(), maxLines = 2, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ClipStatusPill(clip)
                }
                when (clip.status) {
                    NoteClipStatus.Ready -> TextP50(text = clip.excerpt(), maxLines = 2, color = colors.primaryText02)
                    NoteClipStatus.Failed -> TextP50(text = clip.error ?: "發生錯誤", maxLines = 2, color = colors.support05)
                    NoteClipStatus.Processing -> TextP50(text = "轉錄中…", color = colors.primaryText02)
                }
                if (clip.note.isNotBlank()) {
                    TextP60(text = "筆記：${clip.note}", maxLines = 2, color = colors.primaryText01)
                }
            }
        }
        HorizontalDivider(startIndent = 72.dp)
    }
}
