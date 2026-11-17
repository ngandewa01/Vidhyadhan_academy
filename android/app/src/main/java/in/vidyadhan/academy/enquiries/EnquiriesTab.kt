package `in`.vidyadhan.academy.enquiries

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.*
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EnquiriesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).enquiries()
    val list = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun get(id: Long) = dao.get(id)
    fun save(e: Enquiry) = viewModelScope.launch { if (e.id == 0L) dao.insert(e) else dao.update(e) }
    fun delete(e: Enquiry) = viewModelScope.launch { dao.delete(e) }
}

private sealed interface Screen {
    data object List : Screen
    data class Form(val id: Long) : Screen
    data class Detail(val id: Long) : Screen
}

private fun open(e: Enquiry) = e.status == "NEW" || e.status == "CONTACTED"

@Composable
fun EnquiriesTab(vm: EnquiriesViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    BackHandler(enabled = screen != Screen.List) { screen = Screen.List }
    when (val s = screen) {
        Screen.List -> EnquiryList(vm, { screen = Screen.Form(0) }, { screen = Screen.Detail(it) })
        is Screen.Form -> EnquiryForm(vm, s.id) { screen = if (s.id == 0L) Screen.List else Screen.Detail(s.id) }
        is Screen.Detail -> EnquiryDetail(vm, s.id, { screen = Screen.List }, { screen = Screen.Form(s.id) })
    }
}

@Composable
private fun statusPill(e: Enquiry) = when (e.status) {
    "JOINED" -> GoodPill("Joined")
    "CLOSED" -> InfoPill("Closed")
    "CONTACTED" -> WarnPill("Contacted")
    else -> InfoPill("New")
}

@Composable
private fun EnquiryList(vm: EnquiriesViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val all by vm.list.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("Due") }
    val t = today()
    val shown = when (filter) {
        "Due" -> all.filter { open(it) && it.followUpOn <= t }
        "Open" -> all.filter { open(it) }
        else -> all
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Due" to "Follow up today", "Open" to "All open", "All" to "Everything").forEach { (k, l) ->
                    FilterChip(filter == k, { filter = k }, { Text(l) })
                }
            }
            if (shown.isEmpty()) EmptyState(Icons.Filled.QuestionAnswer,
                if (filter == "Due") "Nothing to follow up today" else "No enquiries yet", "Tap + to note a new enquiry")
            else LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { e ->
                    val late = open(e) && e.followUpOn < t
                    AppCard(onClick = { onOpen(e.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(e.name, color = androidx.compose.ui.graphics.Color(0xFFC2410C)); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(listOf(e.mobile, e.course).filter { it.isNotBlank() }.joinToString(" · "), color = Brand.Slate500)
                                if (e.details.isNotBlank()) Text(e.details, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                                if (open(e)) Text((if (late) "Follow-up overdue: " else "Follow up: ") + e.followUpOn.asDate(),
                                    style = MaterialTheme.typography.bodySmall, color = if (late) MaterialTheme.colorScheme.error else Brand.Slate500)
                            }
                            statusPill(e)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onAdd, Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary) {
            Icon(Icons.Filled.Add, "Add enquiry")
        }
    }
}

@Composable
private fun EnquiryForm(vm: EnquiriesViewModel, id: Long, onDone: () -> Unit) {
    val existing by remember(id) { vm.get(id) }.collectAsStateWithLifecycle(null)
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var follow by remember { mutableLongStateOf(today() + AppSettings.enquiryDays) }
    LaunchedEffect(existing) {
        val e = existing
        if (!loaded && e != null) { name = e.name; mobile = e.mobile; course = e.course; details = e.details; follow = e.followUpOn; loaded = true }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (id == 0L) "New enquiry" else "Edit enquiry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        NumberField("Mobile number (10 digits)", mobile, 10, { mobile = it }, phone = true)
        Text("Interested in", style = MaterialTheme.typography.labelLarge)
        ChipChoice(AppSettings.courseList, course) { course = if (course == it) "" else it }
        OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("Details (what they asked, budget, timing…)") }, minLines = 3)
        DateField("Remind me to follow up on", follow) { follow = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onDone) { Text("Cancel") }
            BigButton("Save", {
                val old = existing
                vm.save(Enquiry(if (id == 0L) 0 else id, name.trim(), mobile, course, details.trim(),
                    old?.createdOn ?: today(), follow, old?.status ?: "NEW")); onDone()
            }, enabled = name.isNotBlank() && mobile.length == 10)
        }
    }
}

@Composable
private fun EnquiryDetail(vm: EnquiriesViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val e by remember(id) { vm.get(id) }.collectAsStateWithLifecycle(null)
    var confirm by remember { mutableStateOf(false) }
    val en = e ?: return
    val days = AppSettings.enquiryDays
    fun launch(i: Intent) = runCatching { ctx.startActivity(i) }
    val msg = "नमस्कार ${en.name}, ${AppSettings.academyName} मध्ये चौकशी केल्याबद्दल धन्यवाद." +
        (if (AppSettings.academyPhone.isNotBlank()) " अधिक माहितीसाठी संपर्क: ${AppSettings.academyPhone}" else "")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubTop(en.name, onBack) {
            IconButton(onEdit) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
        }
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(en.mobile, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); statusPill(en) }
            if (en.course.isNotBlank()) Text("Interested in: ${en.course}")
            if (en.details.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(en.details) }
            Spacer(Modifier.height(6.dp))
            Text("Enquired on ${en.createdOn.asDate()}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            Text("Follow-up: ${en.followUpOn.asDate()}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton({ launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${en.mobile}"))) }, Modifier.weight(1f)) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text("Call") }
            FilledTonalButton({ launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91${en.mobile}?text=" + Uri.encode(msg)))) }, Modifier.weight(1f)) { Icon(Icons.Filled.Chat, null); Spacer(Modifier.width(6.dp)); Text("WhatsApp") }
        }
        if (en.status == "NEW" || en.status == "CONTACTED") {
            BigButton("Called — remind me again in $days days", { vm.save(en.copy(status = "CONTACTED", followUpOn = today() + days)) }, Modifier.fillMaxWidth(), accent = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.save(en.copy(status = "JOINED")) }, Modifier.weight(1f)) { Text("Joined ✓") }
                OutlinedButton({ vm.save(en.copy(status = "CLOSED")) }, Modifier.weight(1f)) { Text("Not interested") }
            }
        } else OutlinedButton({ vm.save(en.copy(status = "CONTACTED", followUpOn = today() + days)) }, Modifier.fillMaxWidth()) { Text("Reopen") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete this enquiry?") },
        confirmButton = { TextButton({ vm.delete(en); confirm = false; onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}
