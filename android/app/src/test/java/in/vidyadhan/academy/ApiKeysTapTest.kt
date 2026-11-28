package `in`.vidyadhan.academy

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import `in`.vidyadhan.academy.ai.AiProvider
import `in`.vidyadhan.academy.ai.ApiKeyRow
import `in`.vidyadhan.academy.ui.VidyadhanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Taps on a locked key row: the counter shows from the 7th tap, and the 10th opens editing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class ApiKeysTapTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tenTapsOpenEditing() {
        AppSettings.geminiKey = "AIzaSyTestKey1234567890abcdefghij"
        rule.setContent { VidyadhanTheme { ApiKeyRow(AiProvider.GEMINI) {} } }
        rule.waitForIdle()
        repeat(6) { rule.onNodeWithText("Gemini key").performClick(); rule.waitForIdle() }
        rule.onNodeWithText("3 more").assertDoesNotExist()
        rule.onNodeWithText("Gemini key").performClick(); rule.waitForIdle()
        rule.onNodeWithText("3 more").assertExists()
        repeat(3) { rule.onNodeWithText("Gemini key").performClick(); rule.waitForIdle() }
        rule.onNodeWithText("Check & save").assertExists()
    }
}
