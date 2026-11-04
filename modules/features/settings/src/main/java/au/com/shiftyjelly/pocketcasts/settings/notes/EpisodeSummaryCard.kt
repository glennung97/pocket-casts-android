package au.com.shiftyjelly.pocketcasts.settings.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.components.TextH50
import au.com.shiftyjelly.pocketcasts.compose.components.TextH70
import au.com.shiftyjelly.pocketcasts.compose.components.TextP50
import au.com.shiftyjelly.pocketcasts.compose.components.TextP60
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClip
import au.com.shiftyjelly.pocketcasts.repositories.notes.NoteClipStatus
import au.com.shiftyjelly.pocketcasts.repositories.notes.NotesSummary

/**
 * The episode note: one button that hands every clip's highlights and notes to the AI, then the result with
 * copy and redo. Used in the player's 筆記 tab and on the notebook's episode page.
 */
@Composable
internal fun EpisodeSummaryCard(
    clips: List<NoteClip>,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    accentTextColor: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val episodeUuid = clips.firstOrNull()?.episodeUuid ?: return
    val readyCount = clips.count { it.status == NoteClipStatus.Ready }
    if (readyCount == 0) return
    val summaries by NotesSummary.summaries(context).collectAsState()
    val generating by NotesSummary.generating.collectAsState()
    val errors by NotesSummary.errors.collectAsState()
    val summary = summaries[episodeUuid]
    val isGenerating = episodeUuid in generating

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextH50(text = "✦ 整集筆記", color = textColor, modifier = Modifier.weight(1f))
            if (summary != null && !isGenerating) {
                TextH70(
                    text = "複製",
                    color = accentColor,
                    modifier = Modifier
                        .clickable { copyToClipboard(context, summary.text) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                TextH70(
                    text = "重新整理",
                    color = accentColor,
                    modifier = Modifier
                        .clickable { NotesSummary.start(context, clips) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        when {
            isGenerating -> TextP60(text = "AI 整理中，大約十幾秒…", color = secondaryColor)
            summary == null -> {
                TextP60(
                    text = "把這一集 $readyCount 段的重點句、要點和你的筆記交給 AI，整理成一份整集筆記。送到 TickTick 時會放在最上面。",
                    color = secondaryColor,
                )
                RowButton(
                    text = "整理成整集筆記",
                    onClick = { NotesSummary.start(context, clips) },
                    includePadding = false,
                    colors = ButtonDefaults.buttonColors(backgroundColor = accentColor),
                    textColor = accentTextColor,
                )
            }
            else -> {
                SummaryText(text = summary.text, textColor = textColor, accentColor = accentColor)
                if (summary.clipCount != readyCount) {
                    TextH70(text = "片段有變動，可以按「重新整理」更新", color = secondaryColor)
                }
            }
        }
        errors[episodeUuid]?.let { TextP60(text = it, color = accentColor) }
    }
}

/** Renders the few Markdown pieces the prompt asks for: ### headings, - bullets and plain lines. */
@Composable
private fun SummaryText(text: String, textColor: Color, accentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") -> TextH50(
                    text = trimmed.trimStart('#').trim(),
                    color = textColor,
                    modifier = Modifier.padding(top = 6.dp),
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> Row {
                    TextP50(text = "•", color = accentColor)
                    Spacer(Modifier.width(8.dp))
                    TextP50(text = trimmed.drop(2).replace("**", ""), color = textColor)
                }
                else -> TextP50(
                    text = trimmed.replace("**", ""),
                    color = textColor,
                    fontWeight = FontWeight.W500,
                )
            }
        }
    }
}
