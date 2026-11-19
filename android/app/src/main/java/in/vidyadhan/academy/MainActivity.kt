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
    Home("Dashboard", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    Students("Students", Icons.Rounded.School, Icons.Outlined.School),
    Enquiries("Enquiries", Icons.Rounded.Forum, Icons.Outlined.Forum),
    Library("Library", Icons.Rounded.LocalLibrary, Icons.Outlined.LocalLibrary),
    More("More", Icons.Rounded.GridView, Icons.Outlined.GridView),
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
            Go.Students, Go.NewStudent, Go.Student -> tab = Tab.Students
            Go.Enquiries, Go.NewEnquiry, Go.Enquiry -> tab = Tab.Enquiries
            Go.Library, Go.NewMember, Go.Member, Go.MemberDues, Go.MemberEnding, Go.Seats -> tab = Tab.Library
            Go.Money, Go.NewExpense -> tab = Tab.Money
        }
        pending = g; pendingId = id
    }
    val done = { pending = null; pendingId = 0L }
    BackHandler(enabled = tab == Tab.Money) { tab = Tab.More }
    CompositionLocalProvider(LocalNavHider provides hider) {
        Box(Modifier.fillMaxSize().background(Brand.Bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    Crossfade(tab, animationSpec = tween(220), label = "tabs") { t ->
                        when (t) {
                            Tab.Home -> HomeTab(::go)
                            Tab.Students -> StudentsTab(openNew = pending == Go.NewStudent, openId = if (pending == Go.Student) pendingId else 0, onOpened = done)
                            Tab.Enquiries -> EnquiriesTab(openNew = pending == Go.NewEnquiry, openId = if (pending == Go.Enquiry) pendingId else 0, onOpened = done,
                                onConvert = { Handoff.prefill = it; tab = Tab.Students })
                            Tab.Library -> LibraryTab(openSeats = pending == Go.Seats, openNew = pending == Go.NewMember, openId = if (pending == Go.Member) pendingId else 0,
                                startFilter = when (pending) { Go.MemberDues -> MemberFilter.Expired; Go.MemberEnding -> MemberFilter.Ending; else -> MemberFilter.All }, onOpened = done)
                            Tab.More -> MoreTab(::go)
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
    Row(
        Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), spotColor = Brand.Blue900.copy(alpha = 0.25f))
            .background(Color.White, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Tab.entries.filter { it.inNav }.forEach { t ->
            val sel = t == selected || (t == Tab.More && !selected.inNav)
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

/** Everything that is not on the bottom bar. */
@Composable
private fun MoreTab(onGo: (Go, Long) -> Unit) {
    `in`.vidyadhan.academy.ui.HeroPage("More", "Money, messages and settings") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.height(6.dp))
            `in`.vidyadhan.academy.ui.OptionRow(Icons.Rounded.AccountBalanceWallet, Brand.Green600, "Money", "Income, expenses and reports") { onGo(Go.Money, 0) }
            `in`.vidyadhan.academy.ui.OptionRow(Icons.Rounded.Campaign, Color(0xFFE11D48), "Send a message", "WhatsApp or Telegram to many people") { onGo(Go.Broadcast, 0) }
            `in`.vidyadhan.academy.ui.OptionRow(Icons.Rounded.Search, Brand.Blue600, "Search", "Students, members and enquiries") { onGo(Go.Search, 0) }
            `in`.vidyadhan.academy.ui.OptionRow(Icons.Rounded.Settings, Brand.Slate600, "Settings", "Academy details, fees, seat types, backup, updates") { onGo(Go.Settings, 0) }
        }
    }
}
