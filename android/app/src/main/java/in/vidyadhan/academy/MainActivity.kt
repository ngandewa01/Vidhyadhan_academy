package `in`.vidyadhan.academy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import `in`.vidyadhan.academy.lock.LockScreen
import `in`.vidyadhan.academy.lock.LockStore
import `in`.vidyadhan.academy.ui.VidyadhanTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LockStore(this)
        setContent {
            VidyadhanTheme {
                var unlocked by rememberSaveable { mutableStateOf(false) }
                if (unlocked) HomeShell() else LockScreen(this, store) { unlocked = true }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Students("Students", Icons.Filled.School),
    Enquiries("Enquiries", Icons.Filled.QuestionAnswer),
    Library("Library", Icons.Filled.LocalLibrary),
    Settings("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeShell() {
    var tab by rememberSaveable { mutableStateOf(Tab.Students) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = it == tab, onClick = { tab = it },
                        icon = { Icon(it.icon, it.label) }, label = { Text(it.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            if (tab == Tab.Students) `in`.vidyadhan.academy.students.StudentsTab()
            else Text("${tab.label} — coming in the next steps", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
