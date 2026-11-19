package `in`.vidyadhan.academy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.enquiries.EnquiriesTab
import `in`.vidyadhan.academy.home.Go
import `in`.vidyadhan.academy.home.HomeTab
import `in`.vidyadhan.academy.library.LibraryTab
import `in`.vidyadhan.academy.library.MemberFilter
import `in`.vidyadhan.academy.lock.LockScreen
import `in`.vidyadhan.academy.lock.LockStore
import `in`.vidyadhan.academy.money.MoneyTab
import `in`.vidyadhan.academy.settings.SettingsTab
import `in`.vidyadhan.academy.students.Handoff
import `in`.vidyadhan.academy.students.StudentsTab
import `in`.vidyadhan.academy.ui.Brand
import `in`.vidyadhan.academy.ui.LocalNavHider
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

private enum class Tab(val label: String, val on: ImageVector, val off: ImageVector, val inNav: Boolean = true) {
    Home("Dashboard", Icons.Outlined.GridView, Icons.Outlined.GridView),
    Students("Students", Icons.Outlined.School, Icons.Outlined.School),
    Enquiries("Enquiries", Icons.Outlined.PersonAddAlt, Icons.Outlined.PersonAddAlt),
    Library("Abhyasika", Icons.Outlined.Chair, Icons.Outlined.Chair),
    More("Bot & More", Icons.AutoMirrored.Outlined.Send, Icons.AutoMirrored.Outlined.Send),
    // opened from "More" (the More tab stays highlighted)
    Money("Money", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, inNav = false),
}

@Composable
private fun AppShell(lock: LockStore) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(false) }
    var broadcast by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Go?>(null) }
    var pendingId by remember { mutableLongStateOf(0L) }
    val hider = remember { mutableIntStateOf(0) }
    val activity = LocalContext.current as FragmentActivity
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            runCatching { ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100) }
    }
    fun go(g: Go, id: Long) {
        when (g) {
            Go.Settings, Go.Backup -> settings = true
            Go.Search -> search = true
            Go.Broadcast -> broadcast = true
            Go.Students, Go.Student -> tab = Tab.Students
            Go.NewStudent -> tab = Tab.Enquiries
            Go.Enquiries, Go.NewEnquiry, Go.Enquiry -> tab = Tab.Enquiries
            Go.Library, Go.NewMember, Go.Member, Go.MemberDues, Go.MemberEnding, Go.Seats -> tab = Tab.Library
            Go.Money, Go.NewExpense -> tab = Tab.Money
        }
        pending = g; pendingId = id
    }
    val done = { pending = null; pendingId = 0L }
    BackHandler(enabled = tab != Tab.Home) { tab = if (tab == Tab.Money) Tab.More else Tab.Home; done() }
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = tab == Tab.Home) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) activity.finish()
        else { lastBack = now; android.widget.Toast.makeText(activity, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show() }
    }
    CompositionLocalProvider(LocalNavHider provides hider) {
        Box(Modifier.fillMaxSize().background(Brand.Bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    Crossfade(tab, animationSpec = tween(220), label = "tabs") { t ->
                        when (t) {
                            Tab.Home -> HomeTab(::go)
                            Tab.Students -> StudentsTab(openId = if (pending == Go.Student) pendingId else 0, onOpened = done, onAdmit = { go(Go.NewStudent, 0) },
                                onOpenMember = { go(Go.Member, it) })
                            Tab.Enquiries -> EnquiriesTab(openNew = pending == Go.NewEnquiry, openId = if (pending == Go.Enquiry) pendingId else 0,
                                openAdmission = pending == Go.NewStudent, onOpened = done,
                                onAdmitted = { kind, id -> if (kind == "student") go(Go.Student, id) else go(Go.Member, id) })
                            Tab.Library -> LibraryTab(openSeats = pending == Go.Seats, openNew = pending == Go.NewMember, openId = if (pending == Go.Member) pendingId else 0,
                                startFilter = when (pending) { Go.MemberDues -> MemberFilter.Expired; Go.MemberEnding -> MemberFilter.Ending; else -> MemberFilter.All }, onOpened = done)
                            Tab.More -> `in`.vidyadhan.academy.telegram.BotPage(::go)
                            Tab.Money -> MoneyTab(openNew = pending == Go.NewExpense, onOpened = done)
                        }
                    }
                }
                AnimatedVisibility(hider.intValue == 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    BottomNav(tab) { tab = it; done() }
                }
            }
            AnimatedVisibility(settings, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
                SettingsTab(lock, openBackup = pending == Go.Backup, onOpened = done, onClose = { settings = false })
            }
            AnimatedVisibility(broadcast, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
                `in`.vidyadhan.academy.home.BroadcastScreen(onClose = { broadcast = false })
            }
            AnimatedVisibility(search, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                `in`.vidyadhan.academy.home.SearchScreen(onClose = { search = false }, onGo = ::go)
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    // badge on Enquiries: how many are still open
    val ctx = LocalContext.current
    val enquiries by remember { `in`.vidyadhan.academy.data.AppDb.get(ctx).enquiries().all() }.collectAsStateWithLifecycle(emptyList())
    val openEnquiries = enquiries.count { it.status == "NEW" || it.status == "CONTACTED" }
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.97f))) {
    HorizontalDivider(color = Brand.Slate200)
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Tab.entries.filter { it.inNav }.forEach { t ->
            val sel = t == selected || (t == Tab.More && !selected.inNav)
            val pill by animateColorAsState(if (sel) Brand.Blue100 else Color.Transparent, label = "nav")
            val fg by animateColorAsState(if (sel) Brand.Blue700 else Brand.Slate500, label = "navfg")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(t) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(pill).padding(horizontal = 14.dp, vertical = 4.dp), Alignment.Center) {
                        Icon(t.off, t.label, tint = fg, modifier = Modifier.size(20.dp))
                    }
                    if (t == Tab.Enquiries && openEnquiries > 0) Text(if (openEnquiries > 99) "99+" else "$openEnquiries",
                        Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).clip(RoundedCornerShape(50)).background(Brand.Saffron500)
                            .padding(horizontal = 5.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                Spacer(Modifier.height(3.dp))
                Text(t.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium), color = fg, maxLines = 1)
            }
        }
    }
    }
}
