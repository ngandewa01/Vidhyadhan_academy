package `in`.vidyadhan.academy.khata

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.KhataEntry
import `in`.vidyadhan.academy.data.Student
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch

/** Picks a phone number from the phone's contacts. No permission is needed: the phone gives the app only that one number. */
@Composable
fun rememberContactPicker(onNumber: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        val number = runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        number?.let { onNumber(Khata.mobileDigits(it)) }
    }
    return {
        runCatching {
            launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
    }
}

/** Mobile number field with the contact icon at its end. Saves as soon as 10 digits are there. */
@Composable
fun MobileField(value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val pick = rememberContactPicker { n -> text = n; onSave(n) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val d = Khata.mobileDigits(v)
            text = d
            if (d.length == 10) onSave(d)
        },
        label = { Text("Mobile") },
        prefix = { Text("+91 ", color = Brand.Slate500) },
        singleLine = true,
        supportingText = { Text(when { text.isEmpty() -> "Optional · needed only for WhatsApp reminders"; text.length < 10 -> "Enter all 10 digits"; else -> "Saved" }) },
        trailingIcon = {
            IconButton(onClick = { pick() }) { Icon(Icons.Rounded.ContactPhone, "Pick from contacts", tint = Brand.Blue600) }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    )
}

/** Four choices, always fully visible: MPSC course, Library, Enquiry, Other. */
@Composable
fun TypeChips(value: String, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Khata.TYPES.forEach { t ->
            val on = t == value
            Text(Khata.label(t),
                Modifier.clip(RoundedCornerShape(50)).background(if (on) Brand.Blue700 else Color.White)
                    .border(1.dp, if (on) Brand.Blue700 else Brand.Slate200, RoundedCornerShape(50))
                    .clickable { onPick(t) }.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Brand.Slate800)
        }
    }
}

/** The old Khatabook balances: type buttons, select many, record payments, and each person's history. */
@Composable
fun KhataScreen(onClose: () -> Unit) {
    val db = AppDb.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    val entries by db.khata().all().collectAsStateWithLifecycle(emptyList())
    var person by remember { mutableStateOf<String?>(null) }
    var recordFor by remember { mutableStateOf<String?>(null) }
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var bulkType by rememberSaveable { mutableStateOf(Khata.COURSE) }
    val selected = remember { mutableStateListOf<String>() }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (recordFor != null) recordFor = null else if (person != null) person = null else if (reviewing) reviewing = false else onClose() }
    HideBottomNav()
    // only people still to confirm; confirmed ones are in Students
    val people = remember(entries) { Khata.people(entries).filter { it.pending } }
    val s = remember(entries) { Khata.summary(entries) }
    val pending = people.filter { it.pending }
    if (reviewing && pending.isNotEmpty()) { ReviewPage(pending) { reviewing = false }; return }
    val open = people.firstOrNull { it.key == person }
    if (open != null) {
        PersonPage(open, onRecord = { recordFor = open.key }) { person = null }
        recordFor?.let { k -> people.firstOrNull { it.key == k }?.let { RecordPaymentSheet(it) { recordFor = null } } }
        return
    }

    val shown = people.filter { p ->
        (q.isBlank() || p.name.contains(q, true) || p.mobile.contains(q)) && (filter == "all" || p.type == filter)
    }
    HeroPage("Old khata", "${people.size} to confirm", onBack = onClose, header = {
        Spacer(Modifier.height(14.dp))
        SearchBox(q, { q = it }, "Search name or mobile")
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassStat("To collect", rupees(s.owed), Modifier.weight(1f), valueColor = Color(0xFFFED7AA))
            GlassStat("Advance", rupees(s.advance), Modifier.weight(1f))
            GlassStat("People", "${s.people}", Modifier.weight(1f))
        }
    }) {
        Column {
            Segmented(listOf("all" to "All", Khata.COURSE to "MPSC", Khata.LIBRARY to "Library", Khata.ENQUIRY to "Enquiry"), filter, { filter = it; selected.clear() },
                Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp))
            if (pending.isNotEmpty()) androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Saffron50)
                    .border(1.dp, Brand.Saffron200, RoundedCornerShape(18.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${pending.size} people to confirm", style = MaterialTheme.typography.titleSmall, color = Brand.Saffron800)
                    Text("Pick MPSC, Library, Enquiry or Skip for each", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                }
                PillButton("Review", { reviewing = true }, Brand.Saffron600)
            }
            if (selected.isNotEmpty()) Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Saffron50)
                    .border(1.dp, Brand.Saffron200, RoundedCornerShape(18.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${selected.size} selected · change to", style = MaterialTheme.typography.labelLarge, color = Brand.Saffron800)
                TypeChips(bulkType) { bulkType = it }
                PillButton("Apply to ${selected.size}", {
                    val keys = selected.toList()
                    scope.launch { keys.forEach { db.khata().setType(it, bulkType) } }
                    selected.clear()
                }, Brand.Saffron600)
            }
            if (shown.isEmpty()) EmptyState(Icons.Rounded.MenuBook, "Nothing here",
                if (entries.isEmpty()) "Nothing imported yet." else "No one matches this filter.")
            else LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.key }) { p ->
                    PersonCard(
                        p = p,
                        checked = selected.contains(p.key),
                        onToggle = { if (selected.contains(p.key)) selected.remove(p.key) else selected.add(p.key) },
                        onOpen = { person = p.key },
                        onType = { t -> scope.launch { db.khata().setType(p.key, t) } },
                        onMobile = { m -> scope.launch { db.khata().setMobile(p.key, m) } },
                        onRecord = { recordFor = p.key },
                    )
                }
            }
        }
    }
    recordFor?.let { k -> people.firstOrNull { it.key == k }?.let { RecordPaymentSheet(it) { recordFor = null } } }
}

/** One person's card: tick box, name, type buttons, mobile with contacts, and Record payment. */
@Composable
private fun PersonCard(
    p: Khata.Person, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit,
    onType: (String) -> Unit, onMobile: (String) -> Unit, onRecord: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (checked) Brand.Saffron50 else Color.White)
            .border(1.dp, if (checked) Brand.Saffron600 else Brand.Line, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Brand.Saffron600))
            Avatar(p.name, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1)
                Text("${p.entries.size} entries", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            }
            when {
                p.balance > 0 -> Text(rupees(p.balance), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron700)
                p.balance < 0 -> StatusChip("Advance ${rupees(-p.balance)}", Tone.Info)
                else -> StatusChip("Settled", Tone.Good)
            }
        }
        TypeChips(p.type, onType)
        MobileField(p.mobile, onMobile)
        // payments are recorded from the student page in Students
    }
}

/** Records money received against the old khata. Counts in Home and the monthly report from this date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentSheet(p: Khata.Person, onDone: () -> Unit) {
    val db = AppDb.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(p.balance.toString()) }
    var mode by remember { mutableStateOf("Cash") }
    var date by remember { mutableLongStateOf(today()) }
    val a = amount.toLongOrNull() ?: 0L
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle("Record a payment", "${p.name} · balance ${rupees(p.balance)}")
            DigitsField("Amount received", amount, 8, { amount = it }, prefix = "₹ ",
                supporting = if (a > p.balance) "More than the balance of ${rupees(p.balance)}" else "Any amount; the rest stays as balance",
                isError = a > p.balance)
            Text("Paid by", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
            ModeChoice(mode) { mode = it }
            DateField("Date received", date, { date = it })
            PrimaryButton("Save payment", {
                scope.launch {
                    db.khata().add(KhataEntry(key = p.key, name = p.entries.first().name, date = date, credit = a,
                        details = "Paid · $mode", kind = "PAYMENT"))
                }
                onDone()
            }, Modifier.fillMaxWidth(), enabled = a in 1..p.balance, icon = Icons.Rounded.Check)
        }
    }
}

/** One person: balance, mobile, type, Record payment, and the full history with a running balance. */
@Composable
private fun PersonPage(p: Khata.Person, onRecord: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDb.get(ctx)
    var running = 0L
    val rows = p.entries.map { e -> running += e.debit - e.credit; e to running }
    HideBottomNav()
    HeroPage(p.name, "Old khata", onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(when { p.balance > 0 -> "Still to collect"; p.balance < 0 -> "Paid in advance"; else -> "Settled" },
                    style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                Text(rupees(kotlin.math.abs(p.balance)), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (p.balance > 0) Brand.Saffron700 else Brand.Ink)
            }
            // payments are recorded from the student page in Students
            Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeChips(p.type) { t -> scope.launch { db.khata().setType(p.key, t) } }
                MobileField(p.mobile) { m -> scope.launch { db.khata().setMobile(p.key, m) } }
                if (p.balance > 0) PrimaryButton("Send reminder on WhatsApp", {
                    Contact.whatsapp(ctx, p.mobile, Khata.reminder(p.name, rupees(p.balance), AppSettings.academyName))
                }, Modifier.fillMaxWidth(), enabled = p.mobile.length == 10, icon = Icons.AutoMirrored.Rounded.Chat,
                    custom = Brush.linearGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E))))
            }
            SectionHeader("History", action = "${p.entries.size} entries")
            Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                rows.forEachIndexed { i, (e, bal) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp), color = Brand.Line)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.date.asDate(), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                            Text(if (e.debit > 0) "Charged ${rupees(e.debit)}" else "Paid ${rupees(e.credit)}",
                                style = MaterialTheme.typography.titleSmall, color = if (e.debit > 0) Brand.Red600 else Brand.Green700)
                            val note = listOf(if (e.kind == "PAYMENT") "Recorded in app" else "", e.details).filter { it.isNotBlank() }.joinToString(" · ")
                            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500, maxLines = 1)
                        }
                        Text("Bal ${rupees(bal)}", style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}


/** Confirms the khata people one at a time. Each confirmed MPSC or Library person becomes a Students record linked by key. */
@Composable
private fun ReviewPage(list: List<Khata.Person>, onBack: () -> Unit) {
    val db = AppDb.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    // skipped people go to the end of the queue, in the order they were skipped
    val skipped = remember { mutableStateListOf<String>() }
    val queue = list.filter { it.key !in skipped } + skipped.mapNotNull { k -> list.firstOrNull { it.key == k } }
    val p = queue.firstOrNull()
    if (p == null) { LaunchedEffect(Unit) { onBack() }; return }
    var choice by remember(p.key) { mutableStateOf(Khata.suggested(p)) }
    HideBottomNav()
    HeroPage("Confirm people", "${queue.size} to go", onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Brand.Ink)
                Text("Paid ${rupees(p.entries.sumOf { it.credit })} · Charged ${rupees(p.entries.sumOf { it.debit })}",
                    style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                Text(when { p.balance > 0 -> "Still owes ${rupees(p.balance)}"; p.balance < 0 -> "Advance ${rupees(-p.balance)}"; else -> "Settled" },
                    style = MaterialTheme.typography.titleMedium, color = if (p.balance > 0) Brand.Saffron700 else Brand.Ink)
            }
            Text("Is this a student?", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
            ReviewChoices(choice) { choice = it }
            Text(when (choice) { null -> "Pick one to continue."; Khata.SKIP -> "Skipped people stay in Old khata for review."; else -> "Over ₹20,000 paid plus due is pre-picked as MPSC course. Change it if needed." },
                style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                p.entries.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp), color = Brand.Line)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.date.asDate(), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, modifier = Modifier.weight(1f))
                        Text(if (e.debit > 0) "Charged ${rupees(e.debit)}" else "Paid ${rupees(e.credit)}",
                            style = MaterialTheme.typography.titleSmall, color = if (e.debit > 0) Brand.Red600 else Brand.Green700)
                    }
                }
            }
            val c = choice
            PrimaryButton(if (c == Khata.SKIP) "Skip for now" else "Confirm & next", {
                if (c == Khata.SKIP) { skipped.remove(p.key); skipped.add(p.key) } else if (c != null) scope.launch { confirmKhata(db, p, c) }
            }, Modifier.fillMaxWidth(), enabled = c != null, icon = Icons.Rounded.Check)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ReviewChoices(value: String?, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Khata.COURSE, Khata.LIBRARY, Khata.ENQUIRY, Khata.SKIP).forEach { t ->
            val on = t == value
            Text(Khata.label(t),
                Modifier.clip(RoundedCornerShape(50)).background(if (on) Brand.Blue700 else Color.White)
                    .border(1.dp, if (on) Brand.Blue700 else Brand.Slate200, RoundedCornerShape(50))
                    .clickable { onPick(t) }.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Brand.Slate800)
        }
    }
}

/** Saves one answer. MPSC and Library create a Students record linked by the khata key. Skip is handled by the review, not saved. */
private suspend fun confirmKhata(db: AppDb, p: Khata.Person, choice: String) {
    db.khata().setType(p.key, choice)
    if (choice == Khata.COURSE || choice == Khata.LIBRARY) {
        val st = Student(name = p.name, mobile = p.mobile, course = if (choice == Khata.COURSE) "MPSC" else "Library",
            totalFee = 0, joinedOn = p.entries.first().date, khataKey = p.key)
        val id = db.students().insert(st)
        OldBook.convert(db, st.copy(id = id)) // fee, installments and payments from the book
    }
    db.khata().setReview(p.key, "DONE")
}

/** The student's old khata on the student's page: balance, recent lines, and Record payment. */
@Composable
fun KhataStudentSection(khataKey: String, entries: List<KhataEntry>) {
    val p = remember(entries, khataKey) { Khata.people(entries.filter { it.key == khataKey }).firstOrNull() } ?: return
    var paying by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Old khata")
        Text(when { p.balance > 0 -> "Still owes ${rupees(p.balance)}"; p.balance < 0 -> "Advance ${rupees(-p.balance)}"; else -> "Settled" },
            style = MaterialTheme.typography.titleMedium, color = if (p.balance > 0) Brand.Saffron700 else Brand.Ink)
        p.entries.forEach { e ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(e.date.asDate(), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, modifier = Modifier.weight(1f))
                Text(if (e.debit > 0) "Charged ${rupees(e.debit)}" else "Paid ${rupees(e.credit)}",
                    style = MaterialTheme.typography.bodyMedium, color = if (e.debit > 0) Brand.Red600 else Brand.Green700)
            }
        }
        if (p.balance > 0) PrimaryButton("Record payment", { paying = true }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Payments)
    }
    if (paying) RecordPaymentSheet(p) { paying = false }
}

/** Replaces the old book lines with the file, keeping what the owner has done: payments in the app, confirmations, types and numbers. */
suspend fun importLedger(db: AppDb, fresh: List<KhataEntry>) {
    val merged = Khata.carryOver(db.khata().ledgerList(), fresh)
    db.khata().clearLedger()
    db.khata().insertAll(merged)
    merged.filter { it.mobile.isNotBlank() }.map { it.key to it.mobile }.distinct().forEach { (k, m) -> db.students().fillKhataMobile(k, m) }
}

/** Shows what the file contains before anything is saved. */
@Composable
fun KhataImportDialog(text: String, onClose: () -> Unit) {
    val db = AppDb.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    val entries = remember(text) { Khata.parseCsv(text) }
    val s = remember(entries) { Khata.summary(entries) }
    var existing by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) { existing = db.khata().countNow() }
    var done by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
        icon = { IconTile(Icons.Rounded.UploadFile, Brand.Saffron600, 52.dp) },
        title = { Text("Import old Khatabook", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    entries.isEmpty() -> Text("No Khatabook entries found in this file. Use the CSV file made for the app.", color = Brand.Red600)
                    done -> Text("Imported ${entries.size} entries ✓", color = Brand.Green700)
                    else -> {
                        Text("${s.people} people · ${entries.size} entries", style = MaterialTheme.typography.titleSmall)
                        Text("${s.owing} people still owe ${rupees(s.owed)}")
                        Text("${rupees(s.advance)} paid in advance")
                        Text("Total charged ${rupees(s.debit)} · total paid ${rupees(s.credit)}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        Text("Payments recorded in the app, confirmations, types and numbers already entered are kept.",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        if (existing > 0) Text("The old book is already here (${existing} entries). Importing again replaces the book lines.",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Red600)
                    }
                }
            }
        },
        confirmButton = {
            if (entries.isNotEmpty() && !done) TextButton(onClick = {
                scope.launch { importLedger(db, entries); done = true }
            }) { Text("Import", color = Brand.Saffron700, fontWeight = FontWeight.Bold) }
            else TextButton(onClick = onClose) { Text("Close") }
        },
        dismissButton = { if (entries.isNotEmpty() && !done) TextButton(onClick = onClose) { Text("Cancel", color = Brand.Slate500) } },
    )
}
