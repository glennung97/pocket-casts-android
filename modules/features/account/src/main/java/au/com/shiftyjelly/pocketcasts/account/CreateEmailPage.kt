package au.com.shiftyjelly.pocketcasts.account

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.LayerDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import au.com.shiftyjelly.pocketcasts.compose.text.TextViewText
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.ui.extensions.getThemeColor
import au.com.shiftyjelly.pocketcasts.ui.extensions.getTintedDrawable
import au.com.shiftyjelly.pocketcasts.utils.extensions.dpToPx
import au.com.shiftyjelly.pocketcasts.views.extensions.addOnTextChanged
import au.com.shiftyjelly.pocketcasts.views.extensions.showKeyboard
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.roundToInt
import au.com.shiftyjelly.pocketcasts.cartheme.R as CR
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

internal data class CreateEmailPageState(
    val isCar: Boolean,
    val isLandscape: Boolean,
    val isFormValid: Boolean,
    val isPasswordInvalid: Boolean,
    val isProgressVisible: Boolean,
    val errorText: String,
)

@Composable
internal fun CreateEmailPage(
    state: CreateEmailPageState,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    fields: @Composable () -> Unit = {},
) {
    val background = Modifier
        .fillMaxSize()
        .background(MaterialTheme.theme.colors.primaryUi01)
    when {
        state.isCar -> CarCreateEmail(state, onNextClick, fields, modifier.then(background))
        state.isLandscape -> LandscapeCreateEmail(state, onNextClick, fields, modifier.then(background))
        else -> PortraitCreateEmail(state, onNextClick, fields, modifier.then(background))
    }
}

@Composable
private fun PortraitCreateEmail(
    state: CreateEmailPageState,
    onNextClick: () -> Unit,
    fields: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .clipToBounds(),
    ) {
        fields()
        CreateEmailTexts(state, 14.sp, requirementsTop = 16.dp)
        NextButtonRow(state, onNextClick, progressStart = 16.dp)
    }
}

@Composable
private fun LandscapeCreateEmail(
    state: CreateEmailPageState,
    onNextClick: () -> Unit,
    fields: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clipToBounds()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Box(modifier = Modifier.padding(top = 32.dp)) {
                fields()
            }
            CreateEmailTexts(state, 14.sp, requirementsTop = 16.dp)
        }
        NextButtonRow(
            state = state,
            onNextClick = onNextClick,
            progressStart = 16.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun CreateEmailTexts(
    state: CreateEmailPageState,
    fontSize: TextUnit,
    requirementsTop: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.theme.colors
    Column(modifier = modifier) {
        TextViewText(
            text = stringResource(LR.string.profile_create_password_requirements),
            fontSize = fontSize,
            color = if (state.isPasswordInvalid) colors.support05 else colors.primaryText02,
            modifier = Modifier.padding(top = requirementsTop).fillMaxWidth(),
        )
        TextViewText(
            text = state.errorText,
            fontSize = fontSize,
            color = colors.support05,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun CarCreateEmail(
    state: CreateEmailPageState,
    onNextClick: () -> Unit,
    fields: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.theme.colors
    val bodySize = dimensionResource(CR.dimen.car_body3_size).value.sp
    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 128.dp),
        ) {
            TextViewText(
                text = stringResource(LR.string.profile_create_account),
                fontSize = dimensionResource(CR.dimen.car_display1_size).value.sp,
                color = colors.primaryText01,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = if (state.isLandscape) 24.dp else 0.dp, top = if (state.isLandscape) 16.dp else 72.dp, end = if (state.isLandscape) 24.dp else 0.dp),
            )
            val fieldsModifier = if (state.isLandscape) {
                Modifier.padding(horizontal = 220.dp).fillMaxWidth()
            } else {
                Modifier.widthIn(max = 500.dp).fillMaxWidth()
            }
            Column(modifier = fieldsModifier.padding(top = 16.dp)) {
                fields()
                if (state.isLandscape) {
                    CarLandscapeTexts(state)
                } else {
                    CreateEmailTexts(state, bodySize, requirementsTop = 8.dp)
                }
            }
            NextButtonRow(
                state = state,
                onNextClick = onNextClick,
                progressStart = 16.dp,
                modifier = Modifier
                    .padding(top = if (state.isLandscape) 30.dp else 16.dp)
                    .width(591.dp),
            )
        }
    }
}

@Composable
private fun CarLandscapeTexts(state: CreateEmailPageState) {
    val colors = MaterialTheme.theme.colors
    Layout(
        content = {
            TextViewText(
                text = stringResource(LR.string.profile_create_password_requirements),
                fontSize = dimensionResource(CR.dimen.car_body3_size).value.sp,
                color = if (state.isPasswordInvalid) colors.support05 else colors.primaryText02,
            )
            TextViewText(
                text = state.errorText,
                fontSize = 14.sp,
                color = colors.support05,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val top = 8.dp.roundToPx()
        val end = 4.dp.roundToPx()
        val requirements = measurables[0].measure(Constraints(maxWidth = (constraints.maxWidth - end).coerceAtLeast(0)))
        val error = measurables[1].measure(Constraints.fixedWidth(requirements.width))
        val x = ((constraints.maxWidth - end - requirements.width) / 2f).roundToInt()
        layout(constraints.maxWidth, top + requirements.height) {
            requirements.place(x, top)
            error.place(x, top + requirements.height + top)
        }
    }
}

@Composable
private fun NextButtonRow(
    state: CreateEmailPageState,
    onNextClick: () -> Unit,
    progressStart: Dp,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalConfiguration.current.fontScale
    val progressSize = with(LocalDensity.current) { (24.dp.toPx().toInt() * fontScale).toInt().toDp() }
    AccountButtonRow(
        text = stringResource(if (state.isCar) LR.string.profile_create_account else LR.string.profile_next),
        isCar = state.isCar,
        isEnabled = state.isFormValid,
        isValid = state.isFormValid,
        isProgressVisible = state.isProgressVisible,
        progressColor = MaterialTheme.theme.colors.primaryInteractive02,
        progressStart = progressStart,
        progressSize = progressSize,
        onClick = onNextClick,
        modifier = modifier,
    )
}

@Composable
internal fun CreateEmailFields(
    state: SignInFieldsState,
    isCar: Boolean,
    isLandscape: Boolean,
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onEmailFocusChange: () -> Unit,
    onPasswordFocusChange: () -> Unit,
    onCreate: () -> Unit,
) {
    val currentOnEmailChange by rememberUpdatedState(onEmailChange)
    val currentOnPasswordChange by rememberUpdatedState(onPasswordChange)
    val currentOnEmailFocusChange by rememberUpdatedState(onEmailFocusChange)
    val currentOnPasswordFocusChange by rememberUpdatedState(onPasswordFocusChange)
    AndroidView(
        factory = { context ->
            val emailLayout = createEmailField(context, isCar, isPassword = false)
            val passwordLayout = createEmailField(context, isCar, isPassword = true)
            val emailText = emailLayout.findViewById<TextInputEditText>(R.id.txtEmail)
            val passwordText = passwordLayout.findViewById<TextInputEditText>(R.id.txtPwd)
            emailText.setText(email)
            passwordText.setText(password)
            if (!isCar) {
                emailText.showKeyboard()
            }
            scaleEndIcon(passwordLayout)
            emailText.setOnFocusChangeListener { _, _ -> currentOnEmailFocusChange() }
            passwordText.setOnFocusChangeListener { _, _ -> currentOnPasswordFocusChange() }
            emailText.addOnTextChanged { currentOnEmailChange(it) }
            passwordText.addOnTextChanged { currentOnPasswordChange(it) }
            val spacing = if (isCar && !isLandscape) 24f else 16f
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(emailLayout)
                addView(
                    passwordLayout,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (spacing * context.resources.displayMetrics.density + 0.5f).toInt()
                    },
                )
                state.restoredViewState?.let(::restoreHierarchyState)
                state.view = this
                onCreate()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun createEmailField(context: Context, isCar: Boolean, isPassword: Boolean): TextInputLayout {
    val overlay = when {
        isCar && isPassword -> R.style.ThemeOverlay_PocketCasts_CarPasswordTextInput
        isCar -> R.style.ThemeOverlay_PocketCasts_CarTextInput
        isPassword -> R.style.ThemeOverlay_PocketCasts_PasswordTextInput
        else -> R.style.ThemeOverlay_PocketCasts_TextInput
    }
    val hint = when {
        isPassword -> LR.string.profile_password
        isCar -> LR.string.profile_email
        else -> LR.string.profile_email_address
    }
    return signInField(
        context = context,
        layoutId = if (isPassword) R.id.passwordLayout else R.id.emailLayout,
        overlay = overlay,
        id = if (isPassword) R.id.txtPwd else R.id.txtEmail,
        hint = hint,
        variation = if (isPassword) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        isCar = isCar,
    ).apply {
        val editText = findViewById<TextInputEditText>(if (isPassword) R.id.txtPwd else R.id.txtEmail)
        if (isCar) {
            editText.setTextColor(context.getThemeColor(UR.attr.primary_text_01))
        } else {
            editText.imeOptions = EditorInfo.IME_ACTION_DONE
            if (isPassword) {
                setEndIconTintList(ColorStateList.valueOf(context.getThemeColor(UR.attr.primary_field_03)))
            }
        }
    }
}

private fun scaleEndIcon(layout: TextInputLayout) {
    val eyeDrawable = layout.endIconDrawable ?: return
    val context = layout.context
    val size = (32.dpToPx(context) * context.resources.configuration.fontScale).toInt()
    layout.endIconDrawable = LayerDrawable(arrayOf(eyeDrawable)).apply {
        setLayerSize(0, size, size)
        setLayerGravity(0, Gravity.CENTER)
    }
}

internal fun updateCreateEmailIcons(fields: View, isEmailInvalid: Boolean, focusedId: Int) {
    val email = fields.findViewById<TextInputEditText>(R.id.txtEmail)
    val password = fields.findViewById<TextInputEditText>(R.id.txtPwd)
    val context = email.context
    fun color(id: Int) = context.getThemeColor(if (focusedId == id) UR.attr.primary_icon_03_active else UR.attr.primary_icon_03)
    val fontScale = context.resources.configuration.fontScale
    val iconSize = (32.dpToPx(context) * fontScale).toInt()
    val tickSize = (24.dpToPx(context) * fontScale).toInt()
    val emailDrawable = context.getTintedDrawable(IR.drawable.ic_mail, color(R.id.txtEmail))?.apply { setBounds(0, 0, iconSize, iconSize) }
    val tickDrawable = if (!isEmailInvalid) context.getTintedDrawable(IR.drawable.ic_tick_circle, context.getThemeColor(UR.attr.support_02))?.apply { setBounds(0, 0, tickSize, tickSize) } else null
    val passwordDrawable = context.getTintedDrawable(IR.drawable.ic_password, color(R.id.txtPwd))?.apply { setBounds(0, 0, iconSize, iconSize) }
    email.setCompoundDrawables(emailDrawable, null, tickDrawable, null)
    password.setCompoundDrawablesRelative(passwordDrawable, null, null, null)
}
