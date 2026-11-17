package `in`.vidyadhan.academy

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import `in`.vidyadhan.academy.enquiries.EnquiriesTab
import `in`.vidyadhan.academy.home.HomeTab
import `in`.vidyadhan.academy.library.LibraryTab
import `in`.vidyadhan.academy.lock.LockScreen
import `in`.vidyadhan.academy.lock.LockStore
import `in`.vidyadhan.academy.settings.SettingsTab
import `in`.vidyadhan.academy.students.StudentsTab
import `in`.vidyadhan.academy.ui.ScreenHeader
import `in`.vidyadhan.academy.ui.VidyadhanTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LockStore(this)
        setContent {
            VidyadhanTheme {
                var unlocked by rememberSaveable { mutableStateOf(false) }
                if (unlocked) HomeShell(store) else LockScreen(this, store) { unlocked = true }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Students("Students", Icons.Filled.School),
    Enquiries("Enquiries", Icons.Filled.QuestionAnswer),
    Library("Library", Icons.Filled.LocalLibrary),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
private fun HomeShell(lock: LockStore) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val activity = androidx.compose.ui.platform.LocalContext.current as FragmentActivity
    LaunchedEffect(Unit) {
        // asked directly: the newer result-launcher route crashes on a FragmentActivity
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            runCatching { androidx.core.app.ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100) }
    }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = it == tab, onClick = { tab = it },
                        icon = { Icon(it.icon, it.label) }, label = { Text(it.label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (tab != Tab.Home) ScreenHeader(tab.label)
            when (tab) {
                Tab.Home -> HomeTab { tab = Tab.entries[it] }
                Tab.Students -> StudentsTab()
                Tab.Enquiries -> EnquiriesTab()
                Tab.Library -> LibraryTab()
                Tab.Settings -> SettingsTab(lock)
            }
        }
    }
}
