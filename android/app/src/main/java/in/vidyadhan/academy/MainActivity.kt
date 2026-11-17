package `in`.vidyadhan.academy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import `in`.vidyadhan.academy.enquiries.EnquiriesTab
import `in`.vidyadhan.academy.home.Go
import `in`.vidyadhan.academy.home.HomeTab
import `in`.vidyadhan.academy.library.LibraryTab
import `in`.vidyadhan.academy.lock.LockScreen
import `in`.vidyadhan.academy.lock.LockStore
import `in`.vidyadhan.academy.settings.SettingsTab
import `in`.vidyadhan.academy.students.Handoff
import `in`.vidyadhan.academy.students.StudentsTab
import `in`.vidyadhan.academy.ui.Brand
import `in`.vidyadhan.academy.ui.VidyadhanTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.White.toArgb(), Color.White.toArgb()),
        )
        super.onCreate(savedInstanceState)
        val store = LockStore(this)
        setContent {
            VidyadhanTheme {
                var unlocked by rememberSaveable { mutableStateOf(false) }
                Crossfade(unlocked, animationSpec = tween(350), label = "lock") { u ->
                    if (u) AppShell(store) else LockScreen(this, store) { unlocked = true }
                }
            }
        }
    }
}

private enum class Tab(val label: String, val on: ImageVector, val off: ImageVector) {
    Home("Home", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    Students("Students", Icons.Rounded.School, Icons.Outlined.School),
    Enquiries("Enquiries", Icons.Rounded.Forum, Icons.Outlined.Forum),
    Library("Library", Icons.Rounded.LocalLibrary, Icons.Outlined.LocalLibrary),
    Settings("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

@Composable
private fun AppShell(lock: LockStore) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var pending by remember { mutableStateOf<Go?>(null) }
    val hider = remember { mutableIntStateOf(0) }
    val activity = LocalContext.current as FragmentActivity
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            runCatching { ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100) }
    }
    fun go(g: Go) {
        tab = when (g) {
            Go.Students, Go.NewStudent -> Tab.Students
            Go.Enquiries, Go.NewEnquiry -> Tab.Enquiries
            Go.Library, Go.NewMember -> Tab.Library
            Go.Settings, Go.Backup -> Tab.Settings
        }
        pending = g
    }
    CompositionLocalProvider(`in`.vidyadhan.academy.ui.LocalNavHider provides hider) {
    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        Box(Modifier.weight(1f)) {
            Crossfade(tab, animationSpec = tween(220), label = "tabs") { t ->
                when (t) {
                    Tab.Home -> HomeTab(::go)
                    Tab.Students -> StudentsTab(openNew = pending == Go.NewStudent, onOpened = { pending = null })
                    Tab.Enquiries -> EnquiriesTab(openNew = pending == Go.NewEnquiry, onOpened = { pending = null },
                        onConvert = { Handoff.prefill = it; tab = Tab.Students })
                    Tab.Library -> LibraryTab(openNew = pending == Go.NewMember, onOpened = { pending = null })
                    Tab.Settings -> SettingsTab(lock, openBackup = pending == Go.Backup, onOpened = { pending = null })
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(hider.intValue == 0,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
            BottomNav(tab) { tab = it; pending = null }
        }
    }
    }
}

@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), spotColor = Brand.Blue900.copy(alpha = 0.25f))
            .background(Color.White, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Tab.entries.forEach { t ->
            val sel = t == selected
            val pill by animateColorAsState(if (sel) Brand.Blue100 else Color.Transparent, label = "nav")
            val fg by animateColorAsState(if (sel) Brand.Blue800 else Brand.Slate400, label = "navfg")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(t) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(pill).padding(horizontal = 18.dp, vertical = 5.dp), Alignment.Center) {
                    Icon(if (sel) t.on else t.off, t.label, tint = fg, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(t.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold), color = fg, maxLines = 1)
            }
        }
    }
}
