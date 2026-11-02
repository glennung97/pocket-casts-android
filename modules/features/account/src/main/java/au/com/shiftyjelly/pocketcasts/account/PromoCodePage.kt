package au.com.shiftyjelly.pocketcasts.account

import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import au.com.shiftyjelly.pocketcasts.compose.buttons.ViewMaterialButton
import au.com.shiftyjelly.pocketcasts.compose.text.TextViewText
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.views.component.GradientIcon
import au.com.shiftyjelly.pocketcasts.localization.R as LR

internal data class PromoCodePageState(
    val isProgressVisible: Boolean,
    val isLoaded: Boolean,
    @DrawableRes val imageRes: Int,
    @DrawableRes val overlayRes: Int?,
    val title: String,
    val detail: String,
    val doneText: String,
    val createText: String?,
    val isSignInVisible: Boolean,
)

@Composable
internal fun PromoCodePage(
    state: PromoCodePageState,
    onCloseClick: () -> Unit,
    onDoneClick: () -> Unit,
    onCreateClick: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.theme.colors
    Layout(
        content = {
            if (state.isLoaded) {
                AccountCloseButton(onCloseClick, hasRipple = true, Modifier.layoutId(PromoId.Close))
                PromoImage(state, Modifier.layoutId(PromoId.Image))
                TextViewText(
                    text = state.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primaryText01,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.layoutId(PromoId.Title),
                )
                TextViewText(
                    text = state.detail,
                    fontSize = 18.sp,
                    color = colors.primaryText02,
                    lineSpacingMultiplier = 1.2f,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.layoutId(PromoId.Detail),
                )
            }
            if (state.isProgressVisible) {
                AccountProgressBar(colors.primaryInteractive01, Modifier.layoutId(PromoId.Progress))
            }
            state.createText?.let { OutlinedButton(it, onCreateClick, Modifier.layoutId(PromoId.Create)) }
            if (state.isLoaded) {
                AccountPrimaryButton(state.doneText, 18.sp, onDoneClick, Modifier.layoutId(PromoId.Done))
            }
            if (state.isSignInVisible) {
                OutlinedButton(stringResource(LR.string.sign_in), onSignInClick, Modifier.layoutId(PromoId.SignIn))
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(colors.primaryUi01)
            .clipToBounds(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        fun px(dp: Int) = dp.dp.roundToPx()
        fun measure(id: PromoId, c: Constraints = Constraints()) = measurables.firstOrNull { it.layoutId == id }?.measure(c)
        val buttonWidth = Constraints.fixedWidth(width - px(32))
        val signIn = measure(PromoId.SignIn, buttonWidth)
        val done = measure(PromoId.Done, buttonWidth)
        val create = measure(PromoId.Create, buttonWidth)
        val signInTop = signIn?.let { height - px(16) - it.height } ?: height
        val doneTop = done?.let { signInTop - px(8) - it.height } ?: signInTop
        val close = measure(PromoId.Close)
        val image = measure(PromoId.Image)
        val textWidth = Constraints.fixedWidth(width - px(60))
        val title = measure(PromoId.Title, textWidth)
        val detail = measure(PromoId.Detail, textWidth)
        val progress = measure(PromoId.Progress)
        val chainHeight = (image?.height ?: 0) + px(32) + (title?.height ?: 0) + px(8) + (detail?.height ?: 0)
        val imageTop = centre(doneTop, chainHeight)
        layout(width, height) {
            close?.place(0, 0)
            image?.place(centre(width, image.width), imageTop)
            val titleTop = imageTop + (image?.height ?: 0) + px(32)
            title?.place(px(30), titleTop)
            detail?.place(px(30), titleTop + (title?.height ?: 0) + px(8))
            progress?.place(centre(width, progress.width), centre(doneTop - px(8), progress.height))
            create?.place(px(16), doneTop - px(8) - create.height)
            done?.place(px(16), doneTop)
            signIn?.place(px(16), signInTop)
        }
    }
}

@Composable
private fun PromoImage(state: PromoCodePageState, modifier: Modifier = Modifier) {
    val contentDescription = stringResource(LR.string.done)
    AndroidView(
        factory = { context -> GradientIcon(context).apply { this.contentDescription = contentDescription } },
        update = { icon ->
            val context = icon.context
            icon.setup(AppCompatResources.getDrawable(context, state.imageRes), overlay = state.overlayRes?.let { AppCompatResources.getDrawable(context, it) })
        },
        modifier = modifier.size(160.dp),
    )
}

@Composable
private fun OutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ViewMaterialButton(
        text = text,
        onClick = onClick,
        backgroundColor = Color.Transparent,
        textColor = MaterialTheme.theme.colors.primaryInteractive01,
        fontSize = 18.sp,
        letterSpacing = 0.02.em,
        cornerRadius = 8.dp,
        elevation = 0.dp,
        verticalPadding = 16.dp,
        horizontalPadding = 40.dp,
        modifier = modifier,
    )
}

private enum class PromoId {
    Close,
    Image,
    Title,
    Detail,
    Progress,
    Create,
    Done,
    SignIn,
}
