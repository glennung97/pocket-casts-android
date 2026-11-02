package au.com.shiftyjelly.pocketcasts.account

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResetPasswordPageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var clicks = 0

    @Test
    fun `valid email confirms and shows the error`() {
        setContent(ResetPasswordPageState(isCar = false, isEmailValid = true, isProgressVisible = false, errorText = "Error sending email."))

        composeTestRule.onNodeWithText("Error sending email.").assertExists()
        composeTestRule.onNodeWithText("Confirm").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `invalid email disables confirm on car`() {
        setContent(ResetPasswordPageState(isCar = true, isEmailValid = false, isProgressVisible = true, errorText = ""))

        composeTestRule.onNodeWithText("Confirm").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    private fun setContent(state: ResetPasswordPageState) {
        composeTestRule.setContent {
            AppTheme(Theme.ThemeType.LIGHT) {
                ResetPasswordPage(state = state, onConfirmClick = { clicks++ })
            }
        }
    }
}
