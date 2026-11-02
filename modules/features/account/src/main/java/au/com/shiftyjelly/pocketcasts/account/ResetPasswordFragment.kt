package au.com.shiftyjelly.pocketcasts.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import au.com.shiftyjelly.pocketcasts.account.viewmodel.ResetPasswordError
import au.com.shiftyjelly.pocketcasts.account.viewmodel.ResetPasswordState
import au.com.shiftyjelly.pocketcasts.account.viewmodel.ResetPasswordViewModel
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.utils.Util
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import au.com.shiftyjelly.pocketcasts.views.helper.UiUtil
import dagger.hilt.android.AndroidEntryPoint
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@AndroidEntryPoint
class ResetPasswordFragment : BaseFragment() {

    private val viewModel: ResetPasswordViewModel by viewModels()
    private var form by mutableStateOf(ResetPasswordForm())
    private var fieldState: SignInFieldsState? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = contentWithoutConsumedInsets {
        AppTheme(theme.activeTheme) {
            val isCar = Util.isCarUiMode(requireContext())
            val form = form
            val fieldState = rememberSignInFieldsState().also { this@ResetPasswordFragment.fieldState = it }
            ResetPasswordPage(
                state = ResetPasswordPageState(
                    isCar = isCar,
                    isEmailValid = !form.isEmailInvalid,
                    isProgressVisible = form.isProgressVisible,
                    errorText = form.errorText,
                ),
                onConfirmClick = ::resetPassword,
                field = {
                    ResetPasswordField(
                        state = fieldState,
                        isCar = isCar,
                        isEmailInvalid = form.isEmailInvalid,
                        onEmailChange = viewModel::updateEmail,
                    )
                },
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fieldState = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        form = ResetPasswordForm()

        viewModel.clearValues()

        viewModel.resetPasswordState.observe(viewLifecycleOwner) {
            when (it) {
                is ResetPasswordState.Empty -> {
                    updateForm(false)
                }

                is ResetPasswordState.Failure -> {
                    form = form.copy(isProgressVisible = false)

                    val invalidEmail = it.errors.contains(ResetPasswordError.INVALID_EMAIL)
                    val serverFail = it.errors.contains(ResetPasswordError.SERVER)

                    updateForm(invalidEmail)

                    if (serverFail) {
                        val error = it.message ?: "Check your email address"
                        form = form.copy(errorText = error)
                        viewModel.clearServerError()
                    }
                }

                is ResetPasswordState.Loading -> {
                    form = form.copy(errorText = "", isProgressVisible = true)
                }

                is ResetPasswordState.Success -> {
                    form = form.copy(isProgressVisible = false)
                    UiUtil.displayAlert(
                        activity,
                        getString(LR.string.profile_reset_password_sent),
                        getString(LR.string.profile_reset_password_check_email),
                        onComplete = {
                            if (isAdded) {
                                view.findNavController().popBackStack()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun resetPassword() {
        form = form.copy(isProgressVisible = true)
        fieldState?.view?.let(UiUtil::hideKeyboard)
        viewModel.resetPassword()
    }

    private fun updateForm(invalidEmail: Boolean) {
        form = form.copy(isEmailInvalid = invalidEmail)
        fieldState?.view?.let { updateResetPasswordIcon(it, invalidEmail) }
    }
}

private data class ResetPasswordForm(
    val isEmailInvalid: Boolean = false,
    val isProgressVisible: Boolean = false,
    val errorText: String = "",
)
