package `in`.vidyadhan.academy.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.SeatType
import `in`.vidyadhan.academy.library.LibraryViewModel
import `in`.vidyadhan.academy.lock.LockStore
import `in`.vidyadhan.academy.notify.Reminders
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.ui.*
import `in`.vidyadhan.academy.update.UpdateInfo
import `in`.vidyadhan.academy.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Page(val title: String) { Main(""), Academy("Academy details"), Courses("Courses & fees"), Messages("Message templates"), Reminders("Reminders"), Library("Seats & lockers"), Backup("Backup & restore"), Updates("Updates"), Cloud("Cloud sync"), Devices("Devices"), Telegram("Telegram bot"), News("News & current affairs") }

@Composable
fun SettingsTab(lock: LockStore, openBackup: Boolean = false, onOpened: () -> Unit = {}, onClose: () -> Unit = {}, asTab: Boolean = false,
                onGo: (`in`.vidyadhan.academy.home.Go, Long) -> Unit = { _, _ -> }) {
    var page by rememberSaveable { mutableStateOf(Page.Main) }
    LaunchedEffect(openBackup) { if (openBackup) { page = Page.Backup; onOpened() } }
    if (!asTab || page != Page.Main) HideBottomNav()
    BackHandler(enabled = !asTab || page != Page.Main) { if (page != Page.Main) page = Page.Main else onClose() }
    AnimatedContent(page, transitionSpec = {
        if (targetState == Page.Main) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "settings") { p ->
        when (p) {
            Page.Main -> MainPage(lock, if (asTab) null else onClose, onGo) { page = it }
            Page.Telegram -> `in`.vidyadhan.academy.telegram.BotPage(onGo, onBack = { page = Page.Main }, onMessages = { page = Page.Messages })
            Page.News -> `in`.vidyadhan.academy.ai.NewsPage("SET") { page = Page.Main }
            else -> HeroPage(p.title, onBack = { page = Page.Main }) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Spacer(Modifier.height(4.dp))
                    when (p) {
                        Page.Academy -> AcademyPage()
                        Page.Courses -> CoursesPage()
                        Page.Messages -> MessagesPage()
                        Page.Reminders -> RemindersPage()
                        Page.Library -> LibraryPage()
                        Page.Backup -> BackupPage()
                        Page.Updates -> UpdatesPage()
                        Page.Cloud -> CloudPage()
                        Page.Devices -> DevicesPage { page = Page.Main }
                        Page.Main, Page.Telegram, Page.News -> {}
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ───────────────────────── main list ─────────────────────────

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), Modifier.padding(start = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
        Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).clip(RoundedCornerShape(24.dp)), content = content)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, color: Color, title: String, subtitle: String? = null, divider: Boolean = true, trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Column {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, color, 40.dp, filled = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
            }
            if (trailing != null) trailing() else if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Brand.Slate300)
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 70.dp), color = Brand.Line)
    }
}

@Composable
private fun MainPage(lock: LockStore, onClose: (() -> Unit)?, onGo: (`in`.vidyadhan.academy.home.Go, Long) -> Unit, open: (Page) -> Unit) {
    val ctx = LocalContext.current
    val types by viewModel<LibraryViewModel>().types.collectAsStateWithLifecycle()
    var bio by remember { mutableStateOf(lock.biometricOn) }
    var pinDialog by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var done by remember { mutableStateOf<String?>(null) }
    val (code, version) = remember { Updater.currentVersion(ctx) }
    HeroPage("Settings", "Make the app work your way", onBack = onClose, header = {
        Spacer(Modifier.height(18.dp))
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.11f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp)).clickable { open(Page.Academy) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoBadge(52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(AppSettings.academyName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(AppSettings.academyPhone.ifBlank { "Add phone and address for receipts" }, style = MaterialTheme.typography.bodySmall, color = Brand.Blue200, maxLines = 1)
            }
            GlassChip("Edit", Icons.Rounded.Edit)
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(14.dp)) // keeps the first group name clear of the header card
            // search: type a word (courses, PIN, reports, telegram...) and jump straight to that setting
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search settings") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            val matches = SETTING_INDEX.filter { (title, words, _) -> query.isNotBlank() && (title.contains(query, true) || words.contains(query, true)) }
            if (query.isNotBlank()) {
                if (matches.isEmpty()) Text("No setting called \"$query\"", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                else Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).clip(RoundedCornerShape(24.dp))) {
                    matches.forEachIndexed { i, (title, _, page) ->
                        SettingRow(Icons.Rounded.Settings, Brand.Slate600, title, null, divider = i < matches.lastIndex) { open(page) }
                    }
                }
                return@Column
            }
            AccountCard(open)
            Group("Academy") {
                SettingRow(Icons.AutoMirrored.Rounded.MenuBook, Brand.Saffron600, "Courses & fees", "${AppSettings.courseList.size} course${if (AppSettings.courseList.size == 1) "" else "s"} · ${AppSettings.batchList.size} batches · ${AppSettings.defaultInstallments} installments" +
                    if (AppSettings.lateFeeOn) " · late fee on" else "") { open(Page.Courses) }
                SettingRow(Icons.Rounded.EventSeat, Brand.Teal700, "Seats & lockers", "Rooms · plans · ${types.size} seat type${if (types.size == 1) "" else "s"} · ${AppSettings.lockerCount} lockers", divider = false) { open(Page.Library) }
            }
            Group("Messages") {
                SettingRow(Icons.AutoMirrored.Rounded.Send, Color(0xFF229ED9), "Telegram bot",
                    if (`in`.vidyadhan.academy.telegram.TelegramBot.connected) "@${AppSettings.botName} · ${`in`.vidyadhan.academy.telegram.TelegramBot.linkedCount()} connected" else "Not connected") { open(Page.Telegram) }
                SettingRow(Icons.Rounded.Sms, Brand.WhatsAppDark, "Message templates", "Fee, Abhyasika, enquiry, birthday") { open(Page.Messages) }
                SettingRow(Icons.Rounded.NotificationsActive, Brand.Red600, "Reminders", "Follow up after ${AppSettings.enquiryDays} days · daily at %02d:%02d".format(AppSettings.reminderHour, AppSettings.reminderMinute)) { open(Page.Reminders) }
                SettingRow(Icons.Rounded.Campaign, Color(0xFFE11D48), "Broadcast", "Telegram, group, WhatsApp or SMS", divider = false) { onGo(`in`.vidyadhan.academy.home.Go.Broadcast, 0) }
            }
            Group("MPSC News") {
                SettingRow(Icons.Rounded.AutoAwesome, Brand.Violet700, "News & current affairs",
                    if (!`in`.vidyadhan.academy.ai.AiRouter.ready()) "Add an API key" else "Checks twice a day", divider = false) { open(Page.News) }
            }
            Group("Reports") {
                SettingRow(Icons.Rounded.AccountBalanceWallet, Brand.Green600, "Reports", "Month, year, all time · PDF", divider = false) { onGo(`in`.vidyadhan.academy.home.Go.Money, 0) }
            }
            Group("Security") {
                SettingRow(Icons.Rounded.Password, Brand.Violet700, "PIN", "4-digit PIN to open the app") { pinDialog = true }
                SettingRow(Icons.Rounded.Fingerprint, Brand.Green600, "Fingerprint", if (bio) "On" else "Off", divider = false,
                    trailing = { Switch(bio, { bio = it; lock.biometricOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Blue600)) }) { bio = !bio; lock.biometricOn = bio }
            }
            Group("Data") {
                val c = `in`.vidyadhan.academy.cloud.Cloud
                SettingRow(Icons.Rounded.CloudSync, Brand.Teal700, "Cloud sync", when (c.state) {
                    `in`.vidyadhan.academy.cloud.Cloud.State.On -> "On · ${c.email}"
                    `in`.vidyadhan.academy.cloud.Cloud.State.Error -> "Problem: ${c.message}"
                    `in`.vidyadhan.academy.cloud.Cloud.State.NotSetUp -> "Not set up in this version"
                    else -> "Off · sign in with Google to use on more phones"
                }) { open(Page.Cloud) }
                SettingRow(Icons.Rounded.CloudUpload, Brand.Blue600, "Backup & restore", "Google Drive copy · Excel export", divider = false) { open(Page.Backup) }
            }
            Group("App") {
                SettingRow(Icons.Rounded.SystemUpdate, Brand.Saffron700, "Updates", "Version $version") { open(Page.Updates) }
                SettingRow(Icons.Rounded.Info, Brand.Slate600, "About", "Vidyadhan Academy · build $code", divider = false)
            }
            Text("Made for ${AppSettings.academyName}\n" + if (`in`.vidyadhan.academy.cloud.Cloud.state == `in`.vidyadhan.academy.cloud.Cloud.State.On) "Records are kept on this phone and in your cloud" else "All data is stored only on this phone", Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall, color = Brand.Slate400, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
        }
    }
    if (pinDialog) ChangePinDialog(lock, onDone = { pinDialog = false; done = it })
    done?.let { msg -> AlertDialog({ done = null }, confirmButton = { TextButton({ done = null }) { Text("OK") } }, text = { Text(msg) }, containerColor = Color.White) }
}

@Composable
private fun ChangePinDialog(lock: LockStore, onDone: (String?) -> Unit) {
    var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { onDone(null) }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
        icon = { IconTile(Icons.Rounded.Password, Brand.Violet700, 52.dp) },
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(old, { if (it.length <= 4 && it.all(Char::isDigit)) old = it }, label = { Text("Current PIN") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(new, { if (it.length <= 4 && it.all(Char::isDigit)) new = it }, label = { Text("New 4-digit PIN") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (err.isNotEmpty()) Text(err, color = Brand.Red600, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = new.length == 4, onClick = { if (lock.verify(old)) { lock.setPin(new); onDone("PIN changed.") } else err = "Current PIN is wrong" }) { Text("Change", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ onDone(null) }) { Text("Cancel") } })
}

// ───────────────────────── sub pages ─────────────────────────

@Composable
private fun AcademyPage() {
    FormCard("Shown on receipts", Icons.Rounded.ReceiptLong) {
        Field(AppSettings.academyName, { AppSettings.academyName = it }, "Academy name", Icons.Rounded.Apartment)
        Field(AppSettings.academyPhone, { AppSettings.academyPhone = it }, "Phone", Icons.Rounded.Phone, keyboard = KeyboardType.Phone)
        Field(AppSettings.academyAddress, { AppSettings.academyAddress = it }, "Address", Icons.Rounded.Place, singleLine = false, minLines = 2)
        Field(AppSettings.receiptPrefix, { AppSettings.receiptPrefix = it.take(6).uppercase() }, "Receipt number starts with", Icons.Rounded.Tag,
            supporting = "Receipts look like ${AppSettings.receiptPrefix}-00012")
    }
    if (false) FormCard("Telegram", Icons.Rounded.Groups, Color(0xFF229ED9)) {
        Field(AppSettings.telegramLink, { AppSettings.telegramLink = it }, "Group or channel link, e.g. https://t.me/…", Icons.Rounded.Link, keyboard = KeyboardType.Uri,
            supporting = "Used by “Send a message” to open your group")
    }
    Text("Changes save automatically.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate400, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun MessagesPage() {
    val scope = rememberCoroutineScope()
    val bot = `in`.vidyadhan.academy.telegram.TelegramBot
    val canTest = bot.connected && AppSettings.botOwnerChat != 0L
    var tested by remember { mutableStateOf<String?>(null) }
    fun test(text: String) = scope.launch { tested = if (bot.send(AppSettings.botOwnerChat, text)) "Test sent to your Telegram ✓" else "Could not send the test" }
    Text("Words in {curly brackets} are filled in by the app, e.g. {name} becomes the student's name. Keep them in your text. " +
        "Telegram messages are sent in Marathi and English together.",
        style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, modifier = Modifier.padding(horizontal = 6.dp))
    if (!canTest) Text("To test messages on your own Telegram: connect the bot in Bot & More and send /owner to it.",
        style = MaterialTheme.typography.bodySmall, color = Brand.Amber700, modifier = Modifier.padding(horizontal = 6.dp))
    else SoftButton("Send a test fee receipt to my Telegram", {
        scope.launch {
            val ctx2 = `in`.vidyadhan.academy.telegram.TelegramBot.app ?: return@launch
            val f = `in`.vidyadhan.academy.ui.receiptPdf(ctx2, `in`.vidyadhan.academy.ui.ReceiptData("${AppSettings.receiptPrefix}-TEST", `in`.vidyadhan.academy.students.today(),
                "FEE RECEIPT  /  फी पावती", "Rohan Deshmukh", listOf("Mobile" to "+91 98220 01122", "Course" to "MPSC · Evening", "Payment mode" to "UPI"), 9000,
                listOf("Total fee" to "₹25,000", "Paid so far" to "₹21,000", "Balance" to "₹4,000"), footnote = "Next installment ₹4,000 on 10 Dec"))
            tested = if (bot.sendDocument(AppSettings.botOwnerChat, f, "🧾 Test receipt")) "Test receipt sent to your Telegram ✓" else "Could not send the test"
        }
    }, Modifier.fillMaxWidth(), icon = Icons.Rounded.ReceiptLong)
    tested?.let { StatusChip(it, if (it.endsWith("✓")) Tone.Good else Tone.Bad) }
    `in`.vidyadhan.academy.Templates.all.forEach { t ->
        var mr by remember { mutableStateOf(`in`.vidyadhan.academy.Templates.text(t.key, true)) }
        var en by remember { mutableStateOf(`in`.vidyadhan.academy.Templates.text(t.key, false)) }
        FormCard(t.title, Icons.Rounded.Sms, Brand.WhatsAppDark) {
            Text("Can use: ${t.fields}", style = MaterialTheme.typography.labelMedium, color = Brand.Teal700)
            Field(mr, { mr = it; AppSettings.setTemplate(t.key, true, it) }, "मराठी", singleLine = false, minLines = 3)
            Field(en, { en = it; AppSettings.setTemplate(t.key, false, it) }, "English", singleLine = false, minLines = 3)
            SoftButton("Reset to original", { mr = t.mr; en = t.en; AppSettings.setTemplate(t.key, true, ""); AppSettings.setTemplate(t.key, false, "") },
                color = Brand.Slate600, icon = Icons.Rounded.Refresh)
            if (canTest) SoftButton("Send a test to my Telegram", { test(`in`.vidyadhan.academy.Templates.bot(t.key, `in`.vidyadhan.academy.Templates.sample)) },
                color = Color(0xFF0284C7), icon = Icons.AutoMirrored.Rounded.Send)
        }
    }
}

@Composable
private fun CoursesPage() {
    FormCard("Courses", Icons.AutoMirrored.Rounded.MenuBook, Brand.Saffron600) {
        Field(AppSettings.courses, { AppSettings.courses = it }, "Course names, separated by commas", singleLine = false, minLines = 2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { AppSettings.courseList.forEach { SmallTag(it, Brand.Saffron700) } }
    }
    FormCard("Batches", Icons.Rounded.Schedule, Brand.Teal700) {
        Field(AppSettings.batches, { AppSettings.batches = it }, "Batch names, separated by commas", singleLine = false, minLines = 2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { AppSettings.batchList.forEach { SmallTag(it, Brand.Teal700) } }
    }
    FormCard("Installments", Icons.Rounded.Timeline, Brand.Violet700) {
        Stepper("Usual number of installments", AppSettings.defaultInstallments, 1..12) { AppSettings.defaultInstallments = it }
    }
    FormCard("Late fee", Icons.Rounded.Gavel, Brand.Red600) {
        SwitchRow("Charge a late fee", "Shown on overdue installments", AppSettings.lateFeeOn) { AppSettings.lateFeeOn = it }
        if (AppSettings.lateFeeOn) Stepper("Late fee per day", AppSettings.lateFeePerDay, 0..500, " ₹") { AppSettings.lateFeePerDay = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindersPage() {
    val ctx = LocalContext.current
    var timeDialog by remember { mutableStateOf(false) }
    FormCard("Enquiry follow-up", Icons.Rounded.Forum, Brand.Saffron600) {
        Stepper("Remind me after", AppSettings.enquiryDays, 1..14, if (AppSettings.enquiryDays == 1) " day" else " days") { AppSettings.enquiryDays = it }
    }
    // what the phone will show: a sample of the morning notification (the counts are for the day it fires)
    FormCard("Lock-screen preview", Icons.Rounded.Notifications, Brand.Violet700) {
        Text("Morning Action Digest", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
        Text("• 3 fee instalments due · 2 Abhyasika dues\n• 2 enquiries to follow up today", style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
        Text("One notification lists enquiries to follow up, fees due and library renewals. Android may delay it by a few minutes to save battery.",
            style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
    }
    FormCard("Daily notification", Icons.Rounded.Alarm, Brand.Red600) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(18.dp))
            .clickable { timeDialog = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Every day at", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                Text("%02d:%02d".format(AppSettings.reminderHour, AppSettings.reminderMinute), style = MaterialTheme.typography.headlineMedium, color = Brand.Ink)
            }
            PillButton("Change", { timeDialog = true }, Brand.Blue700, Icons.Rounded.Schedule)
        }
        Text("One notification lists enquiries to follow up, fees due and library renewals. Android may delay it by a few minutes to save battery.",
            style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
    }
    if (timeDialog) {
        val st = rememberTimePickerState(AppSettings.reminderHour, AppSettings.reminderMinute, true)
        AlertDialog(onDismissRequest = { timeDialog = false }, containerColor = Color.White, title = { Text("Daily reminder time") }, text = { TimePicker(st) },
            confirmButton = { TextButton({ AppSettings.reminderHour = st.hour; AppSettings.reminderMinute = st.minute; Reminders.schedule(ctx); timeDialog = false }) { Text("Save") } },
            dismissButton = { TextButton({ timeDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun LibraryPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val types by viewModel<LibraryViewModel>().types.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<SeatType?>(null) }
    val rooms by viewModel<LibraryViewModel>().rooms.collectAsStateWithLifecycle()
    FormCard("Seats", Icons.Rounded.EventSeat, Brand.Teal700) {
        if (rooms.isEmpty()) Stepper("Total seats", AppSettings.seatCount, 1..300) { AppSettings.seatCount = it }
        else Text("${`in`.vidyadhan.academy.library.allSeats(rooms, AppSettings.seatCount).size} seats in ${rooms.size} room${if (rooms.size == 1) "" else "s"}: " +
            rooms.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium, color = Brand.Slate600)
        Text("Rooms and the floor plan are drawn on the Seats page. Plans are on the Members page.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
    }
    FormCard("Lockers", Icons.Rounded.Lock, Brand.Violet700) {
        Text("Any seat can take a locker. These amounts fill in when you tick “Locker” for a member.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        Stepper("Number of lockers", AppSettings.lockerCount, 0..300) { AppSettings.lockerCount = it }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DigitsField("Locker fee / month", AppSettings.lockerFee.toString(), 5, { AppSettings.lockerFee = it.toIntOrNull() ?: 0 }, prefix = "₹ ", modifier = Modifier.weight(1f))
            DigitsField("Key deposit", AppSettings.lockerDeposit.toString(), 5, { AppSettings.lockerDeposit = it.toIntOrNull() ?: 0 }, prefix = "₹ ", modifier = Modifier.weight(1f))
        }
    }
    FormCard("Seat types", Icons.Rounded.Category, Brand.Blue600) {
        Text("Fees for each type. To change one seat, tap it on the Seats page while it is free.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        types.forEach { t ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(18.dp))
                .clickable { edit = t }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                `in`.vidyadhan.academy.library.SeatIcon(t.icon, Brand.Teal700, 24.dp, badgeBg = Brand.Slate50)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                    Text("${rupees(t.fee)}/month · deposit ${rupees(t.deposit)} · registration ${rupees(t.regFee)}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    Text(if (t.seats.isBlank()) "All other seats" else "Seats ${t.seats}", style = MaterialTheme.typography.labelMedium, color = Brand.Teal700)
                }
                Icon(Icons.Rounded.Edit, "Edit", tint = Brand.Blue600, modifier = Modifier.size(20.dp))
            }
        }
        SoftButton("Add seat type", { edit = SeatType(name = "") }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
    }
    edit?.let { t ->
        var n by remember { mutableStateOf(t.name) }; var f by remember { mutableStateOf(t.fee.toString()) }
        var d by remember { mutableStateOf(t.deposit.toString()) }; var r by remember { mutableStateOf(t.regFee.toString()) }
        var seats by remember { mutableStateOf(t.seats) }; var icon by remember { mutableStateOf(t.icon) }
        AlertDialog(onDismissRequest = { edit = null }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            title = { Text(if (t.id == 0L) "New seat type" else "Edit seat type") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(n, { n = it }, "Name, e.g. Extra leg room")
                Field(seats, { seats = it }, "Seat numbers (optional)", Icons.Rounded.Tag, supporting = "e.g. 9-16, 20. Or tap a free seat on the Seats page")
                Text("Icon", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CHAIR" to "Normal", "LEGROOM" to "Extra leg room").forEach { (k, lbl) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (icon == k) Brand.Blue50 else Color.White)
                            .border(if (icon == k) 2.dp else 1.dp, if (icon == k) Brand.Blue600 else Brand.Line, RoundedCornerShape(14.dp)).clickable { icon = k }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            `in`.vidyadhan.academy.library.SeatIcon(k, if (icon == k) Brand.Blue700 else Brand.Slate500, 22.dp, badgeBg = if (icon == k) Brand.Blue50 else Color.White)
                            Text(lbl, style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
                        }
                    }
                }
                DigitsField("Monthly fee", f, 6, { f = it }, prefix = "₹ ")
                DigitsField("Deposit", d, 6, { d = it }, prefix = "₹ ")
                DigitsField("Registration fee", r, 6, { r = it }, prefix = "₹ ")
            } },
            confirmButton = { TextButton(enabled = n.isNotBlank(), onClick = {
                val nt = t.copy(name = n.trim(), fee = f.toLongOrNull() ?: 0, deposit = d.toLongOrNull() ?: 0, regFee = r.toLongOrNull() ?: 0, seats = seats.trim(), icon = icon)
                scope.launch { val dao = AppDb.get(ctx).library(); if (t.id == 0L) dao.insertType(nt) else dao.updateType(nt) }; edit = null }) { Text("Save", fontWeight = FontWeight.Bold) } },
            dismissButton = { androidx.compose.foundation.layout.Row {
                if (t.id != 0L) TextButton({ scope.launch { AppDb.get(ctx).library().deleteType(t) }; edit = null }) { Text("Delete", color = Brand.Red600) }
                TextButton({ edit = null }) { Text("Cancel") }
            } })
    }
}

@Composable
private fun CloudPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = `in`.vidyadhan.academy.cloud.Cloud
    var error by remember { mutableStateOf<String?>(null) }
    var signOut by remember { mutableStateOf(false) }
    val st = c.state
    val on = st == `in`.vidyadhan.academy.cloud.Cloud.State.On || st == `in`.vidyadhan.academy.cloud.Cloud.State.Error
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PrimaryBrush).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(Icons.Rounded.CloudSync, Color.White, 48.dp)
        Text(if (st == `in`.vidyadhan.academy.cloud.Cloud.State.On) "Cloud sync is on" else "Use the app on more phones", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(when {
            on -> "Signed in as ${c.email}. Students, fees, seats, enquiries and expenses are the same on every phone signed in to this account, and safe if a phone is lost." +
                if (c.lastSync > 0) "\nLast sync: " + java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(c.lastSync)) else ""
            st == `in`.vidyadhan.academy.cloud.Cloud.State.NotSetUp -> "This version of the app was made without the academy's Firebase settings. Install the latest version from the update link."
            st == `in`.vidyadhan.academy.cloud.Cloud.State.Working -> c.message.ifBlank { "Working…" }
            else -> "Sign in with the academy's Google account. Your records stay on this phone too, so the app keeps working without internet and syncs when it is back."
        }, style = MaterialTheme.typography.bodyMedium, color = Brand.Blue100)
        when {
            st == `in`.vidyadhan.academy.cloud.Cloud.State.SignedOut -> PrimaryButton("Sign in with Google", { scope.launch { error = c.signIn(ctx) } }, Modifier.fillMaxWidth(), accent = true, icon = Icons.AutoMirrored.Rounded.Login)
            st == `in`.vidyadhan.academy.cloud.Cloud.State.Working -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.25f))
            on -> PrimaryButton("Sync now", { `in`.vidyadhan.academy.cloud.Sync.syncNow() }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Sync)
            else -> {}
        }
    }
    if (st == `in`.vidyadhan.academy.cloud.Cloud.State.Error) ErrorBox("Sync problem: ${c.message}")
    error?.let { ErrorBox(it) }
    if (st == `in`.vidyadhan.academy.cloud.Cloud.State.Choosing) {
        Text("This phone and your cloud both have records. Which should be kept?", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
        OptionRow(Icons.Rounded.CloudDownload, Brand.Blue600, "Use the cloud's records", "This phone's records are replaced by the ones in the cloud") { scope.launch { c.finish(ctx, keepCloud = true) } }
        OptionRow(Icons.Rounded.CloudUpload, Brand.Saffron700, "Keep this phone's records", "The cloud is replaced by this phone's records. Other phones then take them.") { scope.launch { c.finish(ctx, keepCloud = false) } }
        Text("Tip: make a backup first (Settings → Backup & restore).", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
    }
    if (on) {
        OptionRow(Icons.AutoMirrored.Rounded.Logout, Brand.Red600, "Sign out", "Stops syncing on this phone. Records stay here.") { signOut = true }
        Text("Photos stay on the phone that took them. The Telegram bot and reminders run on the phone where the bot is connected.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
    }
    if (signOut) ConfirmDialog("Sign out of cloud sync?", "This phone stops syncing. Its records stay here.", "Sign out", danger = true,
        onConfirm = { scope.launch { c.signOut(ctx) }; signOut = false }, onDismiss = { signOut = false })
}

@Composable
private fun BackupPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch { status = runCatching { backup(ctx, uri) }.fold({ AppSettings.lastBackup = `in`.vidyadhan.academy.students.today(); "Backup saved ✓" }, { "Backup failed: ${it.message}" }) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { status = runCatching { exportCsv(ctx, uri) }.fold({ "Students list exported ✓" }, { "Export failed: ${it.message}" }) }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PrimaryBrush).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(Icons.Rounded.CloudUpload, Color.White, 48.dp)
        Text("Keep a safe copy", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text("Saves all records and photos in one file. Save it to Google Drive every week, and always before changing phones. Choose Drive in the screen that opens.",
            style = MaterialTheme.typography.bodyMedium, color = Brand.Blue100)
        PrimaryButton("Back up now", { backupLauncher.launch("vidyadhan-backup-${java.time.LocalDate.now()}.zip") }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Backup)
    }
    status?.let { StatusChip(it, if (it.contains("fail")) Tone.Bad else Tone.Good) }
    OptionRow(Icons.Rounded.SettingsBackupRestore, Brand.Saffron700, "Restore from a backup", "Replaces everything in the app") { restoreLauncher.launch(arrayOf("*/*")) }
    OptionRow(Icons.Rounded.TableChart, Brand.Green600, "Export students to Excel", "Saves a CSV file Excel can open") { csvLauncher.launch("students-${java.time.LocalDate.now()}.csv") }
    var fresh by remember { mutableStateOf(false) }
    OptionRow(Icons.Rounded.DeleteForever, Brand.Red600, "Start fresh", "Deletes all students, enquiries, Abhyasika members, rooms, payments and expenses") { fresh = true }
    if (fresh) {
        var typed by remember { mutableStateOf("") }
        val cloudOn = `in`.vidyadhan.academy.cloud.Cloud.state == `in`.vidyadhan.academy.cloud.Cloud.State.On
        AlertDialog(onDismissRequest = { fresh = false }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            title = { Text("Delete all records?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("All students, fees, enquiries, Abhyasika members, rooms, payments, expenses and photos are deleted from this phone" +
                        (if (cloudOn) " and from your cloud (and so from your other phones)." else ".") +
                        " Your settings, courses, fees, seat types, PIN and Telegram bot stay. Make a backup first if you may need them.")
                    Field(typed, { typed = it }, "Type RESET to confirm")
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim().equals("RESET", true), onClick = {
                    fresh = false
                    scope.launch { status = runCatching { eraseAll(ctx) }.fold({ "All records deleted. You can start fresh ✓" }, { "Could not delete: ${it.message}" }) }
                }) { Text("Delete everything", color = Brand.Red600, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ fresh = false }) { Text("Cancel") } })
    }
    restoreUri?.let { uri ->
        ConfirmDialog("Restore this backup?", "Everything in the app now will be replaced by the backup, and the app will restart.", "Restore", danger = true,
            onConfirm = { scope.launch { runCatching { restore(ctx, uri) }.onFailure { status = "Restore failed: ${it.message}" } }; restoreUri = null },
            onDismiss = { restoreUri = null })
    }
}

@Composable
private fun UpdatesPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (code, name) = remember { Updater.currentVersion(ctx) }
    var status by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableIntStateOf(-1) }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoBadge(56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Version $name", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
            Text("Build $code", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
        StatusChip("Installed", Tone.Good)
    }
    info?.let { u ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AccentBrush).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("New version ${u.versionName}", style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (u.notes.isNotBlank()) Text(u.notes, style = MaterialTheme.typography.bodyMedium, color = Brand.Saffron50)
            if (progress in 0..99) ProgressBar(progress / 100f, color = Color.White, track = Color.White.copy(alpha = 0.25f), height = 8.dp)
            PrimaryButton(if (progress in 0..99) "Downloading $progress%" else "Download and install", {
                scope.launch {
                    progress = 0
                    runCatching { Updater.download(ctx, u) { progress = it } }
                        .onSuccess { progress = 100; Updater.install(ctx, it) }
                        .onFailure { progress = -1; status = "Download failed: ${it.message}" }
                }
            }, Modifier.fillMaxWidth(), enabled = progress !in 0..99, icon = Icons.Rounded.Download)
        }
    }
    FormCard("Where updates come from", Icons.Rounded.Link) {
        Text("Install new versions from the GitHub download link. Add an update link here only if you host one yourself.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        Field(AppSettings.updateUrl, { AppSettings.updateUrl = it.trim() }, "Update link (starts with https://)", Icons.Rounded.Link, keyboard = KeyboardType.Uri)
        PrimaryButton("Check for updates", {
            status = "Checking…"; info = null
            scope.launch {
                runCatching { Updater.check(AppSettings.updateUrl) }
                    .onSuccess { AppSettings.updateSkipped = 0; if (it.versionCode > code) { info = it; status = null } else status = "You have the latest version ✓" }
                    .onFailure { status = "Could not check: ${it.message}" }
            }
        }, Modifier.fillMaxWidth(), enabled = AppSettings.updateUrl.startsWith("https://"), icon = Icons.Rounded.Refresh)
    }
    status?.let { StatusChip(it, if (it.contains("not") || it.contains("fail")) Tone.Bad else Tone.Info) }
}

// ───────────────────────── data helpers ─────────────────────────

private suspend fun backup(ctx: Context, dest: Uri) = withContext(Dispatchers.IO) {
    val db = AppDb.get(ctx)
    db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
    val f = ctx.getDatabasePath(AppDb.dbFileName())
    ctx.contentResolver.openOutputStream(dest)!!.use { raw ->
        java.util.zip.ZipOutputStream(raw).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("academy.db")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            `in`.vidyadhan.academy.ui.Photos.dir(ctx).listFiles()?.forEach { p ->
                zip.putNextEntry(java.util.zip.ZipEntry("photos/${p.name}")); p.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
}

/** Deletes every record (children first) and the photos; settings stay. With cloud sync on, the deletions go to the cloud too. */
private suspend fun eraseAll(ctx: Context) = withContext(Dispatchers.IO) {
    val db = AppDb.get(ctx)
    db.runInTransaction(Runnable {
        listOf("Installment", "StudentPayment", "LibraryPayment", "Note", "LibraryMember", "Student", "Enquiry", "Expense", "LibraryRoom", "MemberPlan")
            .forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM `$it`") }
    })
    `in`.vidyadhan.academy.ui.Photos.dir(ctx).listFiles()?.forEach { it.delete() }
    `in`.vidyadhan.academy.data.Ids.forget()
    withContext(Dispatchers.Main) { AppSettings.forgetPeople() }
}

/** Accepts the new .zip backups (records + photos) and older .db backups. */
private suspend fun restore(ctx: Context, src: Uri) = withContext(Dispatchers.IO) {
    val tmp = File(ctx.cacheDir, "restore.bin")
    ctx.contentResolver.openInputStream(src)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
    val head = tmp.inputStream().use { s -> ByteArray(15).also { s.read(it) } }
    val dbTmp = File(ctx.cacheDir, "restore.db")
    val photosTmp = File(ctx.cacheDir, "restore-photos").apply { deleteRecursively(); mkdirs() }
    if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
        java.util.zip.ZipInputStream(tmp.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                if (e.name == "academy.db") dbTmp.outputStream().use { zip.copyTo(it) }
                else if (e.name.startsWith("photos/") && name.isNotBlank() && !name.contains("..")) File(photosTmp, name).outputStream().use { zip.copyTo(it) }
            }
        }
    } else tmp.copyTo(dbTmp, overwrite = true)
    val dbHead = dbTmp.takeIf { it.exists() }?.inputStream()?.use { s -> ByteArray(15).also { s.read(it) } }
    require(dbHead != null && String(dbHead) == "SQLite format 3") { "That file is not a backup from this app" }
    AppDb.closeForRestore()
    val f = ctx.getDatabasePath(AppDb.dbFileName())
    File(f.path + "-wal").delete(); File(f.path + "-shm").delete()
    dbTmp.copyTo(f, overwrite = true)
    photosTmp.listFiles()?.forEach { it.copyTo(File(`in`.vidyadhan.academy.ui.Photos.dir(ctx), it.name), overwrite = true) }
    tmp.delete(); dbTmp.delete(); photosTmp.deleteRecursively()
    val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    ctx.startActivity(i)
    Runtime.getRuntime().exit(0)
}

private suspend fun exportCsv(ctx: Context, dest: Uri) = withContext(Dispatchers.IO) {
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val db = AppDb.get(ctx)
    val sb = StringBuilder("﻿Name,Mobile,Aadhaar,Course,Total fee,Joined\n")
    db.students().allStudents().forEach { sb.append("${q(it.name)},${it.mobile},${it.aadhaar},${q(it.course)},${it.totalFee},${it.joinedOn.asDate()}\n") }
    ctx.contentResolver.openOutputStream(dest)!!.use { it.write(sb.toString().toByteArray()) }
}


// ───────────────────────── account and devices ─────────────────────────

private val Violet50 = Color(0xFFF3E8FF)

@Composable
private fun Tag(text: String, bg: Color, fg: Color, dot: Color? = null) {
    androidx.compose.foundation.layout.Row(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(dot)); Spacer(Modifier.width(5.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** Top of Settings: who is signed in, with Devices and Sign out one tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(open: (Page) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = `in`.vidyadhan.academy.cloud.Cloud
    val d = `in`.vidyadhan.academy.cloud.Devices
    if (c.state == `in`.vidyadhan.academy.cloud.Cloud.State.NotSetUp) return
    var signOut by remember { mutableStateOf(false) }
    val signedIn = c.state != `in`.vidyadhan.academy.cloud.Cloud.State.SignedOut
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(if (signedIn) Brand.Saffron600 else Brand.Slate300), Alignment.Center) {
                if (signedIn) Text(c.email.take(1).uppercase().ifBlank { "G" }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                else Icon(Icons.Rounded.CloudOff, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (signedIn) "Signed in" else "Not signed in", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                Text(if (signedIn) c.email else if (c.kickedOut) "The main device disconnected this phone. Records are still here." else "Records are only on this phone",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 2)
                if (signedIn) FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (c.state) {
                        `in`.vidyadhan.academy.cloud.Cloud.State.On -> Tag("Sync on", Brand.Green50, Brand.Green700, Brand.Green600)
                        `in`.vidyadhan.academy.cloud.Cloud.State.Error -> Tag("Sync problem", Color(0xFFFEF2F2), Brand.Red600, Brand.Red600)
                        else -> Tag("Setting up…", Brand.Blue100, Brand.Blue700)
                    }
                    if (d.isMain) Tag("★ Main device", Violet50, Brand.Violet700)
                }
            }
        }
        if (signedIn) androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Devices" + if (d.list.isNotEmpty()) " · ${d.list.size}" else "", { open(Page.Devices) }, Modifier.weight(1f), icon = Icons.Rounded.Smartphone)
            SoftButton("Sign out", { signOut = true }, Modifier.weight(1f), color = Brand.Red600, icon = Icons.AutoMirrored.Rounded.Logout)
        } else PrimaryButton("Sign in with Google", { open(Page.Cloud) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Login)
    }
    if (signOut) ConfirmDialog("Sign out of this phone?", "This phone stops syncing. Its records stay here and the app keeps working. Sign in again anytime from Settings.", "Sign out", danger = true,
        onConfirm = { scope.launch { c.signOut(ctx) }; signOut = false }, onDismiss = { signOut = false })
}

private fun ago(t: Long): String {
    if (t == 0L) return "just now"
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 2 -> "active now"
        m < 60 -> "active $m min ago"
        m < 60 * 24 -> "active ${m / 60} h ago"
        m < 60 * 48 -> "last seen yesterday"
        else -> "last seen ${m / (60 * 24)} days ago"
    }
}

/** Every phone signed in to the academy's account; the main device can disconnect the others or hand over "main". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevicesPage(onSignedOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = `in`.vidyadhan.academy.cloud.Cloud
    val d = `in`.vidyadhan.academy.cloud.Devices
    var kick by remember { mutableStateOf<`in`.vidyadhan.academy.cloud.Devices.Device?>(null) }
    var main by remember { mutableStateOf<`in`.vidyadhan.academy.cloud.Devices.Device?>(null) }
    var signOut by remember { mutableStateOf(false) }
    val list = d.list
    val mainName = list.firstOrNull { it.id == d.mainId }?.model
    Text("${list.size} phone${if (list.size == 1) "" else "s"} signed in to ${c.email}", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        if (list.isEmpty()) Text("Loading…", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
        list.forEachIndexed { i, dev ->
            if (i > 0) HorizontalDivider(color = Brand.Line)
            val isMain = dev.id == d.mainId
            val me = dev.id == d.me
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(if (isMain) Violet50 else Brand.Blue100), Alignment.Center) {
                        Icon(if (isMain) Icons.Rounded.Star else Icons.Rounded.Smartphone, null, tint = if (isMain) Brand.Violet700 else Brand.Blue700)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dev.model + if (me) " · this phone" else "", style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 2)
                        if (isMain) Tag("Main device", Violet50, Brand.Violet700)
                        Text(when {
                            dev.kicked -> "Disconnecting: signs out when it next goes online"
                            else -> (if (me) "active now" else ago(dev.lastSeen)).replaceFirstChar { it.uppercase() } + if (dev.version.isNotBlank()) " · ${dev.version}" else ""
                        }, style = MaterialTheme.typography.bodySmall, color = if (dev.kicked) Brand.Red600 else Brand.Slate500)
                    }
                }
                if (d.isMain && !me) FlowRow(Modifier.padding(start = 54.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dev.kicked) PillButton("Remove from list", Brand.Slate600) { d.remove(dev.id) }
                    else {
                        PillButton("★ Make main", Brand.Violet700) { main = dev }
                        PillButton("Disconnect", Brand.Red600) { kick = dev }
                    }
                }
            }
        }
    }
    Text(if (d.isMain) "Only the main device can disconnect other phones. A disconnected phone is signed out the next time it opens or goes online. Its records stay on it, but it stops syncing until someone signs in there again."
        else "Only the main device${mainName?.let { " ($it)" } ?: ""} can disconnect phones or change the main device.",
        style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
    SoftButton("Sign out of this phone", { signOut = true }, Modifier.fillMaxWidth(), color = Brand.Red600, icon = Icons.AutoMirrored.Rounded.Logout)

    kick?.let { k -> ConfirmDialog("Disconnect ${k.model}?", "It is signed out the next time it opens or goes online, and stops getting the academy's records.", "Disconnect", danger = true,
        onConfirm = { d.disconnect(k.id); kick = null }, onDismiss = { kick = null }) }
    main?.let { m -> ConfirmDialog("Make ${m.model} the main device?", "Then only that phone can disconnect phones. This phone becomes a normal device.", "Make main",
        onConfirm = { d.makeMain(m.id); main = null }, onDismiss = { main = null }) }
    if (signOut) ConfirmDialog("Sign out of this phone?", "This phone stops syncing. Its records stay here and the app keeps working. Sign in again anytime from Settings.", "Sign out", danger = true,
        onConfirm = { scope.launch { c.signOut(ctx); onSignedOut() }; signOut = false }, onDismiss = { signOut = false })
}

@Composable
private fun PillButton(text: String, color: Color, onClick: () -> Unit) =
    Text(text, Modifier.clip(RoundedCornerShape(50)).border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, softWrap = false)


/** Settings you can find by name in the search box: title, extra words people might type, and the page it opens. */
private val SETTING_INDEX = listOf(
    Triple("Courses & fees", "course fee installment late batch", Page.Courses),
    Triple("Seats & lockers", "seat room locker hall plan abhyasika", Page.Library),
    Triple("Telegram bot", "telegram bot connect group link syllabus", Page.Telegram),
    Triple("Message templates", "template sms whatsapp message", Page.Messages),
    Triple("Reminders", "reminder notification time daily", Page.Reminders),
    Triple("News & current affairs", "mpsc news ai gemini groq key", Page.News),
    Triple("Academy details", "name phone address logo", Page.Academy),
    Triple("Cloud sync", "cloud google sign in devices", Page.Cloud),
    Triple("Devices", "phones signed in disconnect main device", Page.Devices),
    Triple("Backup & restore", "backup restore drive excel export start fresh", Page.Backup),
    Triple("Updates", "version update app", Page.Updates),
)
