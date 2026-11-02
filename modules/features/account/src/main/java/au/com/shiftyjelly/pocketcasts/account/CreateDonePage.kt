package au.com.shiftyjelly.pocketcasts.account

import android.widget.Switch
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.use
import au.com.shiftyjelly.pocketcasts.compose.buttons.ViewMaterialButton
import au.com.shiftyjelly.pocketcasts.compose.images.DrawableImage
import au.com.shiftyjelly.pocketcasts.compose.text.TextViewText
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.ui.extensions.getThemeColor
import kotlin.math.roundToInt
import au.com.shiftyjelly.pocketcasts.cartheme.R as CR
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

internal data class CreateDonePageState(
    val isCar: Boolean,
    @DrawableRes val imageRes: Int?,
    val title: String,
    val detail: String,
    val isNewsletterChecked: Boolean,
    val isProgressVisible: Boolean,
)

@Composable
internal fun CreateDonePage(
    state: CreateDonePageState,
    onNewsletterChange: (Boolean) -> Unit,
    onCloseClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = modifier
        .fillMaxSize()
        .background(MaterialTheme.theme.colors.primaryUi01)
    if (state.isCar) {
        CarCreateDone(state, onNewsletterChange, onDoneClick, background)
    } else {
        PhoneCreateDone(state, onNewsletterChange, onCloseClick, onDoneClick, background)
    }
}

@Composable
private fun PhoneCreateDone(
    state: CreateDonePageState,
    onNewsletterChange: (Boolean) -> Unit,
    onCloseClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.theme.colors
    Layout(
        content = {
            AccountCloseButton(onCloseClick, hasRipple = false, Modifier.layoutId(DoneId.CloseButton))
            DoneImage(state, Modifier.layoutId(DoneId.Image))
            DoneTitle(state, 22.sp, FontWeight.Medium, Modifier.layoutId(DoneId.Title))
            DoneDetail(state, 15.sp, FontWeight.Medium, Modifier.layoutId(DoneId.Detail))
            DoneProgress(state, Modifier.layoutId(DoneId.Progress))
            DrawableImage(
                drawableId = R.drawable.ic_newsletter,
                contentDescription = stringResource(LR.string.profile_pocket_casts_newsletter),
                tint = colors.primaryInteractive01,
                modifier = Modifier.layoutId(DoneId.NewsletterIcon).size(24.dp),
            )
            NewsletterRow(state, onNewsletterChange, Modifier.layoutId(DoneId.NewsletterRow))
            TextViewText(
                text = stringResource(LR.string.profile_pocket_casts_newsletter),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primaryText01,
                modifier = Modifier.layoutId(DoneId.NewsletterTitle).padding(top = 8.dp),
            )
            TextViewText(
                text = stringResource(LR.string.profile_create_newsletter_summary),
                fontSize = 13.sp,
                color = colors.primaryText02,
                lineSpacingMultiplier = 1.1f,
                modifier = Modifier.layoutId(DoneId.NewsletterDescription).padding(bottom = 8.dp),
            )
            NewsletterSwitch(state, onNewsletterChange, Modifier.layoutId(DoneId.Switch))
            AccountPrimaryButton(stringResource(LR.string.done), 18.sp, onDoneClick, Modifier.layoutId(DoneId.Button))
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        fun px(dp: Int) = dp.dp.roundToPx()
        fun measure(id: DoneId, c: Constraints = Constraints()) = measurables.first { it.layoutId == id }.measure(c)
        val closeButton = measure(DoneId.CloseButton)
        val button = measure(DoneId.Button, Constraints.fixedWidth(width - px(32)))
        val switch = measure(DoneId.Switch)
        val newsletterIcon = measure(DoneId.NewsletterIcon)
        val textStart = px(24) + newsletterIcon.width + px(16)
        val textWidth = width - px(24) - switch.width - px(16) - textStart
        val description = measure(DoneId.NewsletterDescription, Constraints.fixedWidth(textWidth))
        val title = measure(DoneId.NewsletterTitle, Constraints.fixedWidth(textWidth))
        val buttonTop = height - px(8) - button.height
        val descriptionTop = buttonTop - px(8) - description.height
        val titleTop = descriptionTop - px(6) - title.height
        val row = measure(DoneId.NewsletterRow, Constraints.fixed(width, descriptionTop + description.height - titleTop))
        val image = measure(DoneId.Image)
        val progress = measure(DoneId.Progress)
        val doneTitle = measure(DoneId.Title, Constraints(maxWidth = width - px(32)))
        val doneDetail = measure(DoneId.Detail, Constraints(maxWidth = width - px(16)))
        val imageSpace = titleTop - px(8)
        val imageTop = centre(imageSpace, image.height)
        layout(width, height) {
            closeButton.place(0, 0)
            image.place(centre(width, image.width), imageTop)
            doneTitle.place(centre(width, doneTitle.width), imageTop + image.height + px(24))
            doneDetail.place(centre(width, doneDetail.width), imageTop + image.height + px(24) + doneTitle.height + px(8))
            progress.place(centre(width, progress.width), centre(imageSpace, progress.height))
            newsletterIcon.place(px(24), titleTop + px(8))
            row.place(0, titleTop)
            title.place(textStart, titleTop)
            description.place(textStart, descriptionTop)
            switch.place(width - px(24) - switch.width, titleTop + centre(descriptionTop + description.height - titleTop, switch.height))
            button.place(px(16), buttonTop)
        }
    }
}

@Composable
private fun CarCreateDone(
    state: CreateDonePageState,
    onNewsletterChange: (Boolean) -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Box(contentAlignment = Alignment.Center) {
            DoneImage(state)
            DoneProgress(state)
        }
        DoneTitle(state, dimensionResource(CR.dimen.car_display3_size).value.sp, FontWeight.Normal, Modifier.padding(top = 31.5.dp))
        DoneDetail(state, dimensionResource(CR.dimen.car_body3_size).value.sp, FontWeight.Normal, Modifier.padding(top = 8.dp))
        CarNewsletter(state, onNewsletterChange, Modifier.padding(top = 32.dp))
        AccountPrimaryButton(stringResource(LR.string.done), dimensionResource(CR.dimen.car_body2_size).value.sp, onDoneClick, Modifier.padding(top = 40.dp, bottom = 24.dp).width(460.dp))
    }
}

@Composable
private fun CarNewsletter(state: CreateDonePageState, onNewsletterChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.theme.colors
    val bodySize = dimensionResource(CR.dimen.car_body4_size).value.sp
    Layout(
        content = {
            DrawableImage(
                drawableId = R.drawable.ic_newsletter,
                contentDescription = stringResource(LR.string.profile_pocket_casts_newsletter),
                tint = colors.primaryInteractive01,
                modifier = Modifier.size(36.dp),
            )
            NewsletterRow(state, onNewsletterChange)
            Column(modifier = Modifier.width(460.dp)) {
                TextViewText(
                    text = stringResource(LR.string.profile_pocket_casts_newsletter),
                    fontSize = bodySize,
                    color = colors.primaryText01,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextViewText(
                    text = stringResource(LR.string.profile_create_newsletter_summary),
                    fontSize = bodySize,
                    color = colors.primaryText02,
                    lineSpacingMultiplier = 1.1f,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
            }
            NewsletterSwitch(state, onNewsletterChange)
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val icon = measurables[0].measure(Constraints())
        val texts = measurables[2].measure(Constraints())
        val row = measurables[1].measure(Constraints.fixed(width, texts.height))
        val switch = measurables[3].measure(Constraints())
        val textX = centre(width, texts.width)
        layout(width, texts.height) {
            icon.place(textX - 16.dp.roundToPx() - icon.width, 8.dp.roundToPx())
            row.place(0, 0)
            texts.place(textX, 0)
            switch.place(textX + texts.width + 16.dp.roundToPx(), centre(texts.height, switch.height))
        }
    }
}

@Composable
private fun DoneImage(state: CreateDonePageState, modifier: Modifier = Modifier) {
    val imageRes = state.imageRes
    if (imageRes != null) {
        DrawableImage(drawableId = imageRes, contentDescription = stringResource(LR.string.done), modifier = modifier.size(160.dp))
    } else {
        Box(modifier = modifier.size(160.dp))
    }
}

@Composable
private fun DoneTitle(state: CreateDonePageState, fontSize: TextUnit, fontWeight: FontWeight, modifier: Modifier = Modifier) {
    TextViewText(text = state.title, fontSize = fontSize, fontWeight = fontWeight, color = MaterialTheme.theme.colors.primaryText01, modifier = modifier)
}

@Composable
private fun DoneDetail(state: CreateDonePageState, fontSize: TextUnit, fontWeight: FontWeight, modifier: Modifier = Modifier) {
    TextViewText(text = state.detail, fontSize = fontSize, fontWeight = fontWeight, color = MaterialTheme.theme.colors.primaryText02, modifier = modifier)
}

@Composable
private fun DoneProgress(state: CreateDonePageState, modifier: Modifier = Modifier) {
    if (state.isProgressVisible) {
        AccountProgressBar(MaterialTheme.theme.colors.primaryInteractive01, modifier)
    } else {
        Box(modifier = modifier)
    }
}

@Composable
private fun NewsletterRow(state: CreateDonePageState, onNewsletterChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clickable { onNewsletterChange(!state.isNewsletterChecked) },
    )
}

@Composable
private fun NewsletterSwitch(state: CreateDonePageState, onNewsletterChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val currentOnNewsletterChange by rememberUpdatedState(onNewsletterChange)
    AndroidView(
        factory = { context ->
            Switch(context).apply {
                if (state.isCar) {
                    setThumbResource(CR.drawable.car_switch_thumb)
                    switchMinWidth = context.resources.getDimensionPixelSize(CR.dimen.car_primary_icon_size)
                    background = context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.controlBackground)).use { it.getDrawable(0) }
                }
                setTextColor(context.getThemeColor(UR.attr.primary_interactive_01))
                setOnCheckedChangeListener { _, isChecked -> currentOnNewsletterChange(isChecked) }
            }
        },
        update = { if (it.isChecked != state.isNewsletterChecked) it.isChecked = state.isNewsletterChecked },
        modifier = modifier,
    )
}

@Composable
internal fun AccountPrimaryButton(text: String, fontSize: TextUnit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ViewMaterialButton(
        text = text,
        onClick = onClick,
        backgroundColor = MaterialTheme.theme.colors.primaryInteractive01,
        textColor = MaterialTheme.theme.colors.primaryInteractive02,
        fontSize = fontSize,
        letterSpacing = 0.02.em,
        cornerRadius = 12.dp,
        verticalPadding = 16.dp,
        modifier = modifier,
    )
}

@Composable
internal fun AccountCloseButton(onClick: () -> Unit, hasRipple: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(100.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (hasRipple) ripple(bounded = false) else null,
                onClick = onClick,
            ),
    ) {
        DrawableImage(
            drawableId = IR.drawable.ic_close,
            contentDescription = stringResource(LR.string.close),
            tint = MaterialTheme.theme.colors.primaryInteractive01,
            modifier = Modifier.padding(24.dp).size(24.dp),
        )
    }
}

internal fun centre(space: Int, size: Int) = ((space - size) / 2f).roundToInt()

private enum class DoneId {
    CloseButton,
    Image,
    Title,
    Detail,
    Progress,
    NewsletterIcon,
    NewsletterRow,
    NewsletterTitle,
    NewsletterDescription,
    Switch,
    Button,
}
