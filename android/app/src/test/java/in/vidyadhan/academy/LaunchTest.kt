package `in`.vidyadhan.academy

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class LaunchTest {
    @Test fun appLaunches() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup()
        a.get()
    }
}
