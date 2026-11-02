package au.com.shiftyjelly.pocketcasts.account

import android.text.InputType
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.ui.extensions.getThemeColor
import au.com.shiftyjelly.pocketcasts.ui.extensions.getTintedDrawable
import au.com.shiftyjelly.pocketcasts.views.extensions.addOnTextChanged
import au.com.shiftyjelly.pocketcasts.views.extensions.showKeyboard
import com.google.android.material.textfield.TextInputEditText
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

internal data class ResetPasswordPageState(
    val isCar: Boolean,
    val isEmailValid: Boolean,
    val isProgressVisible: Boolean,
    val errorText: String,
)

@Composable
internal fun ResetPasswordPage(
    state: ResetPasswordPageState,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit = {},
) {
    if (state.isCar) {
        ResetPasswordForm(
            state = state,
            onConfirmClick = onConfirmClick,
            field = field,
            fieldTop = 0.dp,
            buttonTop = 8.dp,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.theme.colors.primaryUi01)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 128.dp),
        )
    } else {
        Box(modifier = modifier.fillMaxSize().clipToBounds()) {
            ResetPasswordForm(
                state = state,
                onConfirmClick = onConfirmClick,
                field = field,
                fieldTop = 32.dp,
                buttonTop = 32.dp,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true),
            )
        }
    }
}

@Composable
private fun ResetPasswordForm(
    state: ResetPasswordPageState,
    onConfirmClick: () -> Unit,
    field: @Composable () -> Unit,
    fieldTop: Dp,
    buttonTop: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.padding(start = 16.dp, top = fieldTop, end = 16.dp)) {
            field()
        }
        PhoneErrorText(state.errorText, Modifier.padding(start = 16.dp))
        AccountButtonRow(
            text = stringResource(LR.string.profile_confirm),
            isCar = state.isCar,
            isEnabled = state.isEmailValid,
            isValid = state.isEmailValid,
            isProgressVisible = state.isProgressVisible,
            progressColor = MaterialTheme.theme.colors.primaryInteractive01,
            progressStart = 32.dp,
            onClick = onConfirmClick,
            modifier = Modifier.padding(top = buttonTop),
            buttonModifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )
    }
}

@Composable
internal fun ResetPasswordField(
    state: SignInFieldsState,
    isCar: Boolean,
    isEmailInvalid: Boolean,
    onEmailChange: (String) -> Unit,
) {
    val currentOnEmailChange by rememberUpdatedState(onEmailChange)
    AndroidView(
        factory = { context ->
            signInField(
                context = context,
                layoutId = R.id.emailLayout,
                overlay = if (isCar) R.style.ThemeOverlay_PocketCasts_CarTextInput else R.style.ThemeOverlay_PocketCasts_TextInput,
                id = R.id.txtEmail,
                hint = LR.string.profile_email,
                variation = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                isCar = isCar,
                drawablePadding = 8f,
            ).apply {
                val email = findViewById<TextInputEditText>(R.id.txtEmail)
                if (!isCar) {
                    email.showKeyboard()
                }
                email.addOnTextChanged { currentOnEmailChange(it) }
                updateResetPasswordIcon(this, isEmailInvalid)
                state.restoredViewState?.let(::restoreHierarchyState)
                state.view = this
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun updateResetPasswordIcon(field: View, isEmailInvalid: Boolean) {
    val email = field.findViewById<TextInputEditText>(R.id.txtEmail)
    val context = email.context
    fun icon(id: Int, attr: Int) = context.getTintedDrawable(id, context.getThemeColor(attr))?.apply { setBounds(0, 0, 64, 64) }
    val emailDrawable = icon(IR.drawable.ic_mail, UR.attr.primary_interactive_01)
    val tickDrawable = if (!isEmailInvalid) icon(IR.drawable.ic_tick_circle, UR.attr.support_02) else null
    email.setCompoundDrawables(emailDrawable, null, tickDrawable, null)
}
