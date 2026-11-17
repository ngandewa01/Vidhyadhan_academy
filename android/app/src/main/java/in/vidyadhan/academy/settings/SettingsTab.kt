package `in`.vidyadhan.academy.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton({ if (value > range.first) onChange(value - 1) }) { Icon(Icons.Filled.Remove, "Less") }
        Text("$value$suffix", fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 44.dp))
        IconButton({ if (value < range.last) onChange(value + 1) }) { Icon(Icons.Filled.Add, "More") }
    }
}

@Composable
private fun Group(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Brand.Blue700); Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Brand.Blue800)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(lock: LockStore, libVm: LibraryViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val types by libVm.types.collectAsStateWithLifecycle()
    var typeEdit by remember { mutableStateOf<SeatType?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableIntStateOf(-1) }
    var bio by remember { mutableStateOf(lock.biometricOn) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { status = runCatching { backup(ctx, uri) }.fold({ "Backup saved." }, { "Backup failed: ${it.message}" }) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { status = runCatching { exportCsv(ctx, uri) }.fold({ "Students list exported." }, { "Export failed: ${it.message}" }) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Group("Academy details", Icons.Filled.School) {
            OutlinedTextField(AppSettings.academyName, { AppSettings.academyName = it }, Modifier.fillMaxWidth(), label = { Text("Academy name") }, singleLine = true)
            OutlinedTextField(AppSettings.academyPhone, { AppSettings.academyPhone = it }, Modifier.fillMaxWidth(), label = { Text("Phone (shown on receipts)") }, singleLine = true)
            OutlinedTextField(AppSettings.academyAddress, { AppSettings.academyAddress = it }, Modifier.fillMaxWidth(), label = { Text("Address (shown on receipts)") }, minLines = 2)
            OutlinedTextField(AppSettings.receiptPrefix, { AppSettings.receiptPrefix = it.take(6) }, Modifier.fillMaxWidth(), label = { Text("Receipt number prefix") }, singleLine = true)
        }
        Group("Courses and fees", Icons.Filled.MenuBook) {
            OutlinedTextField(AppSettings.courses, { AppSettings.courses = it }, Modifier.fillMaxWidth(), label = { Text("Courses (separate with commas)") })
            Stepper("Usual number of installments", AppSettings.defaultInstallments, 1..12) { AppSettings.defaultInstallments = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Late fee on overdue installments", Modifier.weight(1f))
                Switch(AppSettings.lateFeeOn, { AppSettings.lateFeeOn = it })
            }
            if (AppSettings.lateFeeOn) Stepper("Late fee per day", AppSettings.lateFeePerDay, 0..500, " ₹") { AppSettings.lateFeePerDay = it }
        }
        Group("Enquiry reminders", Icons.Filled.Notifications) {
            Stepper("Remind after", AppSettings.enquiryDays, 1..14, " days") { AppSettings.enquiryDays = it }
            val t = "%02d:%02d".format(AppSettings.reminderHour, AppSettings.reminderMinute)
            OutlinedButton({ timeDialog = true }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(8.dp)); Text("Daily reminder time: $t") }
            Text("A notification lists enquiries to follow up, fees due and library renewals. Android may delay it a few minutes to save battery.",
                style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
        Group("Library", Icons.Filled.LocalLibrary) {
            Stepper("Total seats", AppSettings.seatCount, 1..300) { AppSettings.seatCount = it }
            Text("Seat types", style = MaterialTheme.typography.labelLarge)
            types.forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontWeight = FontWeight.SemiBold)
                        Text("Fee ${rupees(t.fee)} · Deposit ${rupees(t.deposit)} · Registration ${rupees(t.regFee)}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    IconButton({ typeEdit = t }) { Icon(Icons.Filled.Edit, "Edit") }
                }
            }
            OutlinedButton({ typeEdit = SeatType(name = "") }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Text(" Add seat type") }
        }
        Group("Security", Icons.Filled.Lock) {
            OutlinedButton({ pinDialog = true }, Modifier.fillMaxWidth()) { Text("Change PIN") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Open with fingerprint", Modifier.weight(1f)); Switch(bio, { bio = it; lock.biometricOn = it })
            }
        }
        Group("Backup and export", Icons.Filled.CloudUpload) {
            Text("Save a copy of all your data to Google Drive or phone storage. Do this regularly, and before changing phones.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            BigButton("Back up now", { backupLauncher.launch("vidyadhan-backup-${java.time.LocalDate.now()}.db") }, Modifier.fillMaxWidth())
            OutlinedButton({ restoreLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Restore from a backup") }
            OutlinedButton({ csvLauncher.launch("students-${java.time.LocalDate.now()}.csv") }, Modifier.fillMaxWidth()) { Text("Export students list (Excel / CSV)") }
        }
        Group("App updates", Icons.Filled.SystemUpdate) {
            val (code, name) = Updater.currentVersion(ctx)
            Text("Installed version: $name (build $code)")
            OutlinedTextField(AppSettings.updateUrl, { AppSettings.updateUrl = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Update info link") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            BigButton("Check for updates", {
                status = "Checking…"; info = null
                scope.launch {
                    runCatching { Updater.check(AppSettings.updateUrl) }.onSuccess {
                        if (it.versionCode > code) { info = it; status = "" } else status = "You have the latest version."
                    }.onFailure { status = "Could not check: ${it.message}" }
                }
            }, Modifier.fillMaxWidth(), enabled = AppSettings.updateUrl.startsWith("https://"))
            info?.let { u ->
                Text("New version ${u.versionName} is available", fontWeight = FontWeight.Bold, color = Brand.Saffron700)
                if (u.notes.isNotBlank()) Text(u.notes)
                if (progress in 0..99) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth())
                BigButton(if (progress in 0..99) "Downloading $progress%" else "Download and install", {
                    scope.launch {
                        progress = 0
                        runCatching { Updater.download(ctx, u) { progress = it } }
                            .onSuccess { progress = 100; Updater.install(ctx, it) }
                            .onFailure { progress = -1; status = "Download failed: ${it.message}" }
                    }
                }, Modifier.fillMaxWidth(), enabled = progress !in 0..99, accent = true)
            }
        }
        if (status.isNotBlank()) Text(status, color = Brand.Blue800, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp))
        Text("Vidyadhan Academy app · for the owner's use only", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        Spacer(Modifier.height(8.dp))
    }

    if (timeDialog) {
        val st = rememberTimePickerState(AppSettings.reminderHour, AppSettings.reminderMinute, true)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text("Daily reminder time") }, text = { TimePicker(st) },
            confirmButton = { TextButton({ AppSettings.reminderHour = st.hour; AppSettings.reminderMinute = st.minute; Reminders.schedule(ctx); timeDialog = false }) { Text("Save") } },
            dismissButton = { TextButton({ timeDialog = false }) { Text("Cancel") } })
    }
    typeEdit?.let { t ->
        var n by remember { mutableStateOf(t.name) }; var f by remember { mutableStateOf(t.fee.toString()) }
        var d by remember { mutableStateOf(t.deposit.toString()) }; var r by remember { mutableStateOf(t.regFee.toString()) }
        AlertDialog(onDismissRequest = { typeEdit = null }, title = { Text(if (t.id == 0L) "New seat type" else "Edit seat type") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true)
                NumberField("Monthly fee (₹)", f, 6, { f = it }); NumberField("Deposit (₹)", d, 6, { d = it }); NumberField("Registration fee (₹)", r, 6, { r = it })
            } },
            confirmButton = { TextButton(enabled = n.isNotBlank(), onClick = {
                val nt = t.copy(name = n.trim(), fee = f.toLongOrNull() ?: 0, deposit = d.toLongOrNull() ?: 0, regFee = r.toLongOrNull() ?: 0)
                scope.launch { val dao = AppDb.get(ctx).library(); if (t.id == 0L) dao.insertType(nt) else dao.updateType(nt) }; typeEdit = null }) { Text("Save") } },
            dismissButton = { Row {
                if (t.id != 0L) TextButton({ scope.launch { AppDb.get(ctx).library().deleteType(t) }; typeEdit = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton({ typeEdit = null }) { Text("Cancel") }
            } })
    }
    if (pinDialog) {
        var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { pinDialog = false }, title = { Text("Change PIN") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(old, { if (it.length <= 4 && it.all(Char::isDigit)) old = it }, label = { Text("Current PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(new, { if (it.length <= 4 && it.all(Char::isDigit)) new = it }, label = { Text("New 4-digit PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = new.length == 4, onClick = {
                if (lock.verify(old)) { lock.setPin(new); pinDialog = false; status = "PIN changed." } else err = "Current PIN is wrong"
            }) { Text("Change") } },
            dismissButton = { TextButton({ pinDialog = false }) { Text("Cancel") } })
    }
    restoreUri?.let { uri ->
        AlertDialog(onDismissRequest = { restoreUri = null }, title = { Text("Restore this backup?") },
            text = { Text("All data currently in the app will be replaced by the backup. The app will restart.") },
            confirmButton = { TextButton({
                scope.launch { runCatching { restore(ctx, uri) }.onFailure { status = "Restore failed: ${it.message}" } }; restoreUri = null
            }) { Text("Restore", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ restoreUri = null }) { Text("Cancel") } })
    }
}

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
