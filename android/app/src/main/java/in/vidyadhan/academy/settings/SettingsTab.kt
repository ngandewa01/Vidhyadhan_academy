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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private enum class Page(val title: String) { Main(""), Academy("Academy details"), Courses("Courses & fees"), Reminders("Reminders"), Library("Library seats"), Backup("Backup & export"), Updates("App updates") }

@Composable
fun SettingsTab(lock: LockStore, openBackup: Boolean = false, onOpened: () -> Unit = {}) {
    var page by remember { mutableStateOf(Page.Main) }
    LaunchedEffect(openBackup) { if (openBackup) { page = Page.Backup; onOpened() } }
    BackHandler(enabled = page != Page.Main) { page = Page.Main }
    AnimatedContent(page, transitionSpec = {
        if (targetState == Page.Main) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "settings") { p ->
        when (p) {
            Page.Main -> MainPage(lock) { page = it }
            else -> HeroPage(p.title, onBack = { page = Page.Main }) {
                HideBottomNav()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Spacer(Modifier.height(4.dp))
                    when (p) {
                        Page.Academy -> AcademyPage()
                        Page.Courses -> CoursesPage()
                        Page.Reminders -> RemindersPage()
                        Page.Library -> LibraryPage()
                        Page.Backup -> BackupPage()
                        Page.Updates -> UpdatesPage()
                        Page.Main -> {}
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
private fun MainPage(lock: LockStore, open: (Page) -> Unit) {
    val ctx = LocalContext.current
    val types by viewModel<LibraryViewModel>().types.collectAsStateWithLifecycle()
    var bio by remember { mutableStateOf(lock.biometricOn) }
    var pinDialog by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val (code, version) = remember { Updater.currentVersion(ctx) }
    HeroPage("Settings", "Make the app work your way", header = {
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
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(2.dp))
            Group("Academy") {
                SettingRow(Icons.Rounded.Apartment, Brand.Blue600, "Academy details", "Name, phone, address, receipt number") { open(Page.Academy) }
                SettingRow(Icons.AutoMirrored.Rounded.MenuBook, Brand.Saffron600, "Courses & fees", "${AppSettings.courseList.size} course${if (AppSettings.courseList.size == 1) "" else "s"} · ${AppSettings.defaultInstallments} installments" +
                    if (AppSettings.lateFeeOn) " · late fee on" else "", divider = false) { open(Page.Courses) }
            }
            Group("Daily work") {
                SettingRow(Icons.Rounded.NotificationsActive, Brand.Red600, "Reminders", "Follow up after ${AppSettings.enquiryDays} days · daily at %02d:%02d".format(AppSettings.reminderHour, AppSettings.reminderMinute)) { open(Page.Reminders) }
                SettingRow(Icons.Rounded.EventSeat, Brand.Teal700, "Library seats", "${AppSettings.seatCount} seats · ${types.size} seat type${if (types.size == 1) "" else "s"}", divider = false) { open(Page.Library) }
            }
            Group("Security") {
                SettingRow(Icons.Rounded.Password, Brand.Violet700, "Change PIN", "4-digit PIN to open the app") { pinDialog = true }
                SettingRow(Icons.Rounded.Fingerprint, Brand.Green600, "Fingerprint unlock", if (bio) "On" else "Off", divider = false,
                    trailing = { Switch(bio, { bio = it; lock.biometricOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Blue600)) }) { bio = !bio; lock.biometricOn = bio }
            }
            Group("Data") {
                SettingRow(Icons.Rounded.CloudUpload, Brand.Blue600, "Backup & export", "Save a copy to Google Drive, restore, Excel export", divider = false) { open(Page.Backup) }
            }
            Group("App") {
                SettingRow(Icons.Rounded.SystemUpdate, Brand.Saffron700, "App updates", "Version $version") { open(Page.Updates) }
                SettingRow(Icons.Rounded.Info, Brand.Slate600, "About", "Vidyadhan Academy · build $code", divider = false)
            }
            Text("Made for ${AppSettings.academyName}\nAll data is stored only on this phone", Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall, color = Brand.Slate400, textAlign = TextAlign.Center)
            Spacer(Modifier.height(90.dp))
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
    Text("Changes save automatically.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate400, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun CoursesPage() {
    FormCard("Courses", Icons.AutoMirrored.Rounded.MenuBook, Brand.Saffron600) {
        Field(AppSettings.courses, { AppSettings.courses = it }, "Course names, separated by commas", singleLine = false, minLines = 2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { AppSettings.courseList.forEach { SmallTag(it, Brand.Saffron700) } }
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
    FormCard("Seats", Icons.Rounded.EventSeat, Brand.Teal700) {
        Stepper("Total seats", AppSettings.seatCount, 1..300) { AppSettings.seatCount = it }
    }
    FormCard("Seat types", Icons.Rounded.Category, Brand.Blue600) {
        types.forEach { t ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(18.dp))
                .clickable { edit = t }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                    Text("${rupees(t.fee)}/month · deposit ${rupees(t.deposit)} · registration ${rupees(t.regFee)}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                }
                Icon(Icons.Rounded.Edit, "Edit", tint = Brand.Blue600, modifier = Modifier.size(20.dp))
            }
        }
        SoftButton("Add seat type", { edit = SeatType(name = "") }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
    }
    edit?.let { t ->
        var n by remember { mutableStateOf(t.name) }; var f by remember { mutableStateOf(t.fee.toString()) }
        var d by remember { mutableStateOf(t.deposit.toString()) }; var r by remember { mutableStateOf(t.regFee.toString()) }
        AlertDialog(onDismissRequest = { edit = null }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            title = { Text(if (t.id == 0L) "New seat type" else "Edit seat type") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(n, { n = it }, "Name, e.g. With locker")
                DigitsField("Monthly fee", f, 6, { f = it }, prefix = "₹ ")
                DigitsField("Deposit", d, 6, { d = it }, prefix = "₹ ")
                DigitsField("Registration fee", r, 6, { r = it }, prefix = "₹ ")
            } },
            confirmButton = { TextButton(enabled = n.isNotBlank(), onClick = {
                val nt = t.copy(name = n.trim(), fee = f.toLongOrNull() ?: 0, deposit = d.toLongOrNull() ?: 0, regFee = r.toLongOrNull() ?: 0)
                scope.launch { val dao = AppDb.get(ctx).library(); if (t.id == 0L) dao.insertType(nt) else dao.updateType(nt) }; edit = null }) { Text("Save", fontWeight = FontWeight.Bold) } },
            dismissButton = { androidx.compose.foundation.layout.Row {
                if (t.id != 0L) TextButton({ scope.launch { AppDb.get(ctx).library().deleteType(t) }; edit = null }) { Text("Delete", color = Brand.Red600) }
                TextButton({ edit = null }) { Text("Cancel") }
            } })
    }
}

@Composable
private fun BackupPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { status = runCatching { backup(ctx, uri) }.fold({ "Backup saved ✓" }, { "Backup failed: ${it.message}" }) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { status = runCatching { exportCsv(ctx, uri) }.fold({ "Students list exported ✓" }, { "Export failed: ${it.message}" }) }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PrimaryBrush).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(Icons.Rounded.CloudUpload, Color.White, 48.dp)
        Text("Keep a safe copy", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text("Save a backup to Google Drive every week, and always before changing phones. Choose Drive in the screen that opens.",
            style = MaterialTheme.typography.bodyMedium, color = Brand.Blue100)
        PrimaryButton("Back up now", { backupLauncher.launch("vidyadhan-backup-${java.time.LocalDate.now()}.db") }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Backup)
    }
    status?.let { StatusChip(it, if (it.contains("fail")) Tone.Bad else Tone.Good) }
    OptionRow(Icons.Rounded.SettingsBackupRestore, Brand.Saffron700, "Restore from a backup", "Replaces everything in the app") { restoreLauncher.launch(arrayOf("*/*")) }
    OptionRow(Icons.Rounded.TableChart, Brand.Green600, "Export students to Excel", "Saves a CSV file Excel can open") { csvLauncher.launch("students-${java.time.LocalDate.now()}.csv") }
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
        Field(AppSettings.updateUrl, { AppSettings.updateUrl = it.trim() }, "Update link (starts with https://)", Icons.Rounded.Link, keyboard = KeyboardType.Uri)
        PrimaryButton("Check for updates", {
            status = "Checking…"; info = null
            scope.launch {
                runCatching { Updater.check(AppSettings.updateUrl) }
                    .onSuccess { if (it.versionCode > code) { info = it; status = null } else status = "You have the latest version ✓" }
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
    ctx.contentResolver.openOutputStream(dest)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
}

private suspend fun restore(ctx: Context, src: Uri) = withContext(Dispatchers.IO) {
    val tmp = File(ctx.cacheDir, "restore.db")
    ctx.contentResolver.openInputStream(src)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
    val head = tmp.inputStream().use { s -> ByteArray(15).also { s.read(it) } }
    require(String(head) == "SQLite format 3") { "That file is not a backup from this app" }
    AppDb.closeForRestore()
    val f = ctx.getDatabasePath(AppDb.dbFileName())
    File(f.path + "-wal").delete(); File(f.path + "-shm").delete()
    tmp.copyTo(f, overwrite = true); tmp.delete()
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
