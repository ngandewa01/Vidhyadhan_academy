package `in`.vidyadhan.academy.students

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.vidyadhan.academy.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private sealed interface Screen {
    data object List : Screen
    data class Form(val id: Long) : Screen
    data class Detail(val id: Long) : Screen
}

@Composable
fun StudentsTab(vm: StudentsViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    when (val s = screen) {
        Screen.List -> StudentList(vm, onAdd = { screen = Screen.Form(0) }, onOpen = { screen = Screen.Detail(it) })
        is Screen.Form -> StudentForm(vm, s.id) { screen = if (s.id == 0L) Screen.List else Screen.Detail(s.id) }
        is Screen.Detail -> StudentDetail(vm, s.id, onBack = { screen = Screen.List }, onEdit = { screen = Screen.Form(s.id) })
    }
}

@Composable
private fun StudentList(vm: StudentsViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val all by vm.list.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val shown = all.filter { q.isBlank() || it.name.contains(q, true) || it.mobile.contains(q) }
    val pending = all.sumOf { it.totalFee - it.paid }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Students"); Text("${all.size}", style = MaterialTheme.typography.headlineSmall) }
                    Column(horizontalAlignment = Alignment.End) { Text("Fees pending"); Text(rupees(pending), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary) }
                }
            }
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), placeholder = { Text("Search name or mobile") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true)
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No students yet. Tap + to add.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { s ->
                    val left = s.totalFee - s.paid
                    val overdue = s.nextDue != null && s.nextDue < today() && left > 0
                    Card(Modifier.fillMaxWidth().clickable { onOpen(s.id) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text("${s.mobile} · ${s.course}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (s.nextDue != null && left > 0) Text(
                                    (if (overdue) "Overdue since " else "Next due ") + s.nextDue.asDate(),
                                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (left > 0) { Text(rupees(left), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium); Text("pending", style = MaterialTheme.typography.bodySmall) }
                                else Text("Paid ✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onAdd, Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary) {
            Icon(Icons.Filled.Add, "Add student")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, epochDay: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(epochDay.asDate(), {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) },
        trailingIcon = { IconButton({ open = true }) { Icon(Icons.Filled.CalendarMonth, "Pick date") } })
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentForm(vm: StudentsViewModel, id: Long, onDone: () -> Unit) {
    val existing by remember(id) { vm.student(id) }.collectAsStateWithLifecycle(null)
    val existingPlan by remember(id) { vm.installments(id) }.collectAsStateWithLifecycle(emptyList())
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("MPSC") }
    var fee by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("3") }
    var first by remember { mutableLongStateOf(today()) }
    var joined by remember { mutableLongStateOf(today()) }
    var plan by remember { mutableStateOf<List<Installment>>(emptyList()) } // custom plan when editing
    LaunchedEffect(existing, existingPlan) {
        val s = existing
        if (!loaded && s != null && existingPlan.isNotEmpty()) {
            name = s.name; mobile = s.mobile; aadhaar = s.aadhaar; course = s.course; fee = s.totalFee.toString(); joined = s.joinedOn
            count = existingPlan.size.toString(); first = existingPlan.first().dueOn; plan = existingPlan; loaded = true
        }
    }
    val feeN = fee.toLongOrNull() ?: 0L
    val countN = count.toIntOrNull()?.coerceIn(1, 24) ?: 1
    val preview = if (feeN > 0) buildPlan(feeN, countN, LocalDate.ofEpochDay(first)) else emptyList()
    val valid = name.isNotBlank() && mobile.length == 10 && (aadhaar.isEmpty() || aadhaar.length == 12) && feeN > 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (id == 0L) "Add student" else "Edit student", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
        OutlinedTextField(mobile, { if (it.length <= 10 && it.all(Char::isDigit)) mobile = it }, Modifier.fillMaxWidth(), label = { Text("Mobile number (10 digits)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(aadhaar, { if (it.length <= 12 && it.all(Char::isDigit)) aadhaar = it }, Modifier.fillMaxWidth(), label = { Text("Aadhaar number (12 digits)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(course, { course = it }, Modifier.fillMaxWidth(), label = { Text("Course") }, singleLine = true)
        DateField("Joining date", joined) { joined = it }
        OutlinedTextField(fee, { if (it.length <= 7 && it.all(Char::isDigit)) fee = it }, Modifier.fillMaxWidth(), label = { Text("Total fee (₹)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(count, { if (it.length <= 2 && it.all(Char::isDigit)) count = it }, Modifier.fillMaxWidth(), label = { Text("Number of installments") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        DateField("First installment due on", first) { first = it }
        if (preview.isNotEmpty()) {
            Text("Installment plan", style = MaterialTheme.typography.titleSmall)
            preview.forEach { Text("${it.number}.  ${rupees(it.amount)}  —  ${it.dueOn.asDate()}") }
            if (id != 0L && plan.any { it.paidOn != null }) Text("Note: changing the plan resets paid marks. Change only if needed.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onDone) { Text("Cancel") }
            Button(enabled = valid, onClick = {
                val s = Student(if (id == 0L) 0 else id, name.trim(), mobile, aadhaar, course.trim().ifBlank { "MPSC" }, feeN, joined)
                // keep paid marks when plan is unchanged
                val same = id != 0L && plan.size == preview.size && plan.zip(preview).all { (a, b) -> a.amount == b.amount && a.dueOn == b.dueOn }
                vm.save(s, if (same) plan else preview); onDone()
            }) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDetail(vm: StudentsViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val s by remember(id) { vm.student(id) }.collectAsStateWithLifecycle(null)
    val plan by remember(id) { vm.installments(id) }.collectAsStateWithLifecycle(emptyList())
    var payFor by remember { mutableStateOf<Installment?>(null) }
    var msgFor by remember { mutableStateOf<Installment?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val st = s ?: return
    val paid = plan.filter { it.paidOn != null }.sumOf { it.amount }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(st.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onEdit) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mobile: ${st.mobile}"); Text("Aadhaar: ${if (st.aadhaar.isBlank()) "—" else maskAadhaar(st.aadhaar)}")
                Text("Course: ${st.course} · Joined ${st.joinedOn.asDate()}")
                HorizontalDivider()
                Text("Total ${rupees(st.totalFee)}   Paid ${rupees(paid)}   Pending ${rupees(st.totalFee - paid)}", style = MaterialTheme.typography.titleSmall)
            }
        }
        Text("Installments", style = MaterialTheme.typography.titleMedium)
        plan.forEach { i ->
            val late = i.paidOn == null && i.dueOn < today()
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${i.number}  ${rupees(i.amount)}", style = MaterialTheme.typography.titleSmall)
                        Text(if (i.paidOn != null) "Paid ${i.paidOn.asDate()} (${i.mode})" else (if (late) "Overdue — " else "Due ") + i.dueOn.asDate(),
                            color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (i.paidOn == null) {
                        IconButton({ msgFor = i }) { Icon(Icons.Filled.Send, "Send message", tint = MaterialTheme.colorScheme.primary) }
                        Button({ payFor = i }) { Text("Paid") }
                    } else TextButton({ vm.undoPaid(i) }) { Text("Undo") }
                }
            }
        }
    }
    payFor?.let { i ->
        AlertDialog(onDismissRequest = { payFor = null }, title = { Text("Payment received: ${rupees(i.amount)}") },
            text = { Column { listOf("Cash", "UPI", "Bank").forEach { m -> TextButton({ vm.markPaid(i, m); payFor = null }) { Text(m) } } } },
            confirmButton = {}, dismissButton = { TextButton({ payFor = null }) { Text("Cancel") } })
    }
    msgFor?.let { i ->
        fun send(marathi: Boolean, whatsapp: Boolean) {
            val text = feeMessage(marathi, st.name, i.amount, i.dueOn)
            val uri = if (whatsapp) Uri.parse("https://wa.me/91${st.mobile}?text=" + Uri.encode(text))
            else Uri.parse("smsto:${st.mobile}")
            val intent = Intent(if (whatsapp) Intent.ACTION_VIEW else Intent.ACTION_SENDTO, uri).apply { if (!whatsapp) putExtra("sms_body", text) }
            runCatching { ctx.startActivity(intent) }
            msgFor = null
        }
        AlertDialog(onDismissRequest = { msgFor = null }, title = { Text("Send reminder") },
            text = { Column {
                Text(feeMessage(true, st.name, i.amount, i.dueOn), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Send in:")
                Row { TextButton({ send(true, true) }) { Text("WhatsApp मराठी") }; TextButton({ send(false, true) }) { Text("WhatsApp English") } }
                Row { TextButton({ send(true, false) }) { Text("SMS मराठी") }; TextButton({ send(false, false) }) { Text("SMS English") } }
            } },
            confirmButton = {}, dismissButton = { TextButton({ msgFor = null }) { Text("Cancel") } })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${st.name}?") },
        text = { Text("This removes the student and all installments. It cannot be undone.") },
        confirmButton = { TextButton({ vm.delete(st); confirmDelete = false; onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}
