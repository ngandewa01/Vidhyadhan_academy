package `in`.vidyadhan.academy

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.vidyadhan.academy.enquiries.EnquiriesTab
import `in`.vidyadhan.academy.home.HomeTab
import `in`.vidyadhan.academy.library.LibraryTab
import `in`.vidyadhan.academy.lock.LockStore
import `in`.vidyadhan.academy.settings.SettingsTab
import `in`.vidyadhan.academy.students.StudentsTab
import `in`.vidyadhan.academy.ui.VidyadhanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class TabsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(c: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { VidyadhanTheme { c() } }
        rule.waitForIdle()
    }
    @Test fun home() = show { HomeTab { } }
    @Test fun students() = show { StudentsTab() }
    @Test fun enquiries() = show { EnquiriesTab() }
    @Test fun library() = show { LibraryTab() }
    @Test fun settings() = show { SettingsTab(LockStore(ApplicationProvider.getApplicationContext())) }
}
