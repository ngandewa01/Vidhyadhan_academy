package `in`.vidyadhan.academy.khata

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.KhataEntry
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch

/** The old Khatabook balances: who still owes, who paid in advance, and each person's history. */
@Composable
fun KhataScreen(onClose: () -> Unit) {
    val db = AppDb.get(LocalContext.current)
    val entries by db.khata().all().collectAsStateWithLifecycle(emptyList())
    var person by remember { mutableStateOf<String?>(null) }
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("owing") }
    BackHandler { if (person != null) person = null else onClose() }
    HideBottomNav()
    val people = remember(entries) { Khata.people(entries) }
    val s = remember(entries) { Khata.summary(entries) }
    val open = people.firstOrNull { it.key == person }
    if (open != null) { PersonPage(open) { person = null }; return }

    val shown = people.filter { p ->
        (q.isBlank() || p.name.contains(q, true) || p.mobile.contains(q)) &&
            when (filter) { "owing" -> p.balance > 0; "advance" -> p.balance < 0; else -> true }
    }
    HeroPage("Old khata", "${s.owing} people owe ${rupees(s.owed)}", onBack = onClose, header = {
        Spacer(Modifier.height(14.dp))
        SearchBox(q, { q = it }, "Search name or mobile")
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassStat("To collect", rupees(s.owed), Modifier.weight(1f), valueColor = androidx.compose.ui.graphics.Color(0xFFFED7AA))
            GlassStat("Advance", rupees(s.advance), Modifier.weight(1f))
            GlassStat("People", "${s.people}", Modifier.weight(1f))
        }
    }) {
        Column {
            Segmented(listOf("owing" to "Owing (${s.owing})", "advance" to "Advance", "all" to "All"), filter, { filter = it },
                Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp))
            if (shown.isEmpty()) EmptyState(Icons.Rounded.MenuBook, "Nothing here",
                if (entries.isEmpty()) "Import the old Khatabook file in Settings → Data." else "No one matches this filter.")
            else LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.key }) { p ->
                    ListCard(onClick = { person = p.key }) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1)
                                Text("${p.entries.size} entries" + if (p.mobile.isBlank()) " · no mobile yet" else " · ${p.mobile}",
                                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
                            }
                            when {
                                p.balance > 0 -> Text(rupees(p.balance), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron700)
                                p.balance < 0 -> StatusChip("Advance ${rupees(-p.balance)}", Tone.Info)
                                else -> StatusChip("Settled", Tone.Good)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One person: balance, mobile for reminders, and the full history with a running balance. */
@Composable
private fun PersonPage(p: Khata.Person, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDb.get(ctx)
    var mobile by remember(p.key) { mutableStateOf(p.mobile) }
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
            FormCard("Mobile number", Icons.Rounded.Phone, Brand.Green600) {
                DigitsField("Mobile (for reminders)", mobile, 10, { mobile = it; if (it.length == 10) scope.launch { db.khata().setMobile(p.key, it) } },
                    Icons.Rounded.Phone, prefix = "+91 ", phone = true,
                    supporting = if (mobile.length in 1..9) "Enter all 10 digits" else "Saved as soon as 10 digits are typed")
                if (p.balance > 0) PrimaryButton("Send reminder on WhatsApp", {
                    Contact.whatsapp(ctx, mobile, Khata.reminder(p.name, rupees(p.balance), AppSettings.academyName))
                }, Modifier.fillMaxWidth(), enabled = mobile.length == 10, icon = Icons.AutoMirrored.Rounded.Chat,
                    custom = androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF25D366), androidx.compose.ui.graphics.Color(0xFF128C7E))))
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
                            if (e.details.isNotBlank()) Text(e.details, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500, maxLines = 1)
                        }
                        Text("Bal ${rupees(bal)}", style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Shows what the file contains before anything is saved. Replaces the earlier old-khata import if the owner confirms. */
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
                        if (existing > 0) Text("The old khata is already imported (${existing} entries). Importing again replaces it.",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Red600)
                    }
                }
            }
        },
        confirmButton = {
            if (entries.isNotEmpty() && !done) TextButton(onClick = {
                scope.launch { db.khata().clear(); db.khata().insertAll(entries); done = true }
            }) { Text("Import", color = Brand.Saffron700, fontWeight = FontWeight.Bold) }
            else TextButton(onClick = onClose) { Text("Close") }
        },
        dismissButton = { if (entries.isNotEmpty() && !done) TextButton(onClick = onClose) { Text("Cancel", color = Brand.Slate500) } },
    )
}
