package `in`.vidyadhan.academy

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import `in`.vidyadhan.academy.ai.ClaudeKeyCard
import `in`.vidyadhan.academy.ui.VidyadhanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Taps on the locked Claude key row: the counter shows from the 7th tap, and the 10th opens editing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class ClaudeKeyTapTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tenTapsOpenEditing() {
        AppSettings.claudeKey = "sk-ant-api03-test1234567890abcdef"
        rule.setContent { VidyadhanTheme { ClaudeKeyCard {} } }
        rule.waitForIdle()
        rule.onNodeWithText("Claude key").assertExists()
        repeat(6) { rule.onNodeWithText("Claude key").performClick(); rule.waitForIdle() }
        rule.onNodeWithText("3 more").assertDoesNotExist()
        rule.onNodeWithText("Claude key").performClick(); rule.waitForIdle()
        rule.onNodeWithText("3 more").assertExists()
        repeat(3) { rule.onNodeWithText("Claude key").performClick(); rule.waitForIdle() }
        rule.onNodeWithText("Check & save").assertExists()
    }
}
