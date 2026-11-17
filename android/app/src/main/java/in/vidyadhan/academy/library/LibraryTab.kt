package `in`.vidyadhan.academy.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import `in`.vidyadhan.academy.students.maskAadhaar
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).library()
    val members = dao.members().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val types = dao.seatTypes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun member(id: Long) = dao.member(id)
    fun payments(id: Long) = dao.payments(id)

    fun add(m: LibraryMember, pays: List<LibraryPayment>) = viewModelScope.launch { dao.addMember(m, pays) }
    fun update(m: LibraryMember) = viewModelScope.launch { dao.updateMember(m) }
    fun delete(m: LibraryMember) = viewModelScope.launch { dao.deleteMember(m) }
    fun pay(m: LibraryMember, kind: String, amount: Long) = viewModelScope.launch {
        dao.pay(m, LibraryPayment(memberId = m.id, kind = kind, amount = amount, paidOn = today()))
    }
}

private sealed interface Screen {
    data object Main : Screen
    data class Form(val seat: Int) : Screen
    data class Detail(val id: Long) : Screen
}

fun kindLabel(k: String) = when (k) {
    "FEE" -> "Monthly fee"; "DEPOSIT" -> "Deposit received"; "REGISTRATION" -> "Registration fee"
    "DEPOSIT_RETURN" -> "Deposit returned"; else -> k
}

@Composable
fun LibraryTab(vm: LibraryViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main }
    when (val s = screen) {
        Screen.Main -> LibraryMain(vm, { screen = Screen.Form(it) }, { screen = Screen.Detail(it) })
        is Screen.Form -> MemberForm(vm, s.seat) { screen = Screen.Main }
        is Screen.Detail -> MemberDetail(vm, s.id) { screen = Screen.Main }
    }
}

@Composable
private fun LibraryMain(vm: LibraryViewModel, onAdd: (Int) -> Unit, onOpen: (Long) -> Unit) {
    val members by vm.members.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf("Seats") }
    val active = members.filter { it.active }
    val bySeat = active.associateBy { it.seatNo }
    val total = AppSettings.seatCount
    val t = today()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(view == "Seats", { view = "Seats" }, { Text("Seat map") })
                FilterChip(view == "Members", { view = "Members" }, { Text("Members (${active.size})") })
                Spacer(Modifier.weight(1f))
                InfoPill("${active.size}/$total filled")
            }
            if (view == "Seats") {
                Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Brand.Green100, "Free"); Legend(Brand.Blue700, "Taken"); Legend(Brand.Red100, "Fee due")
                }
                LazyVerticalGrid(GridCells.Adaptive(62.dp), contentPadding = PaddingValues(12.dp, 10.dp, 12.dp, 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((1..total).toList()) { n ->
                        val m = bySeat[n]
                        val due = m != null && m.validTill < t
                        val bg = when { m == null -> Brand.Green100; due -> Brand.Red100; else -> Brand.Blue700 }
                        val fg = when { m == null -> Brand.Green700; due -> Brand.Red700; else -> Color.White }
                        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(bg)
                            .clickable { if (m == null) onAdd(n) else onOpen(m.id) }.padding(vertical = 10.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(m?.name?.substringBefore(' ')?.take(7) ?: "free", color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            } else if (active.isEmpty()) EmptyState(Icons.Filled.LocalLibrary, "No library members yet", "Tap + or a free seat to add one")
            else LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(active, key = { it.id }) { m ->
                    AppCard(onClick = { onOpen(m.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(m.name); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Seat ${m.seatNo} · ${m.mobile}", color = Brand.Slate500)
                            }
                            if (m.validTill < t) BadPill("Fee due") else if (m.validTill - t <= 3) WarnPill("Ends ${m.validTill.asDate()}") else GoodPill("Paid")
                        }
                    }
                }
            }
        }
        FloatingActionButton({ onAdd((1..total).firstOrNull { it !in bySeat } ?: 0) }, Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary) { Icon(Icons.Filled.Add, "Add member") }
    }
}

@Composable
private fun Legend(c: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(c)); Spacer(Modifier.width(5.dp)); Text(t, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MemberForm(vm: LibraryViewModel, startSeat: Int, onDone: () -> Unit) {
    val types by vm.types.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var seat by remember { mutableStateOf(if (startSeat > 0) startSeat.toString() else "") }
    var type by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var deposit by remember { mutableStateOf("0") }
    var reg by remember { mutableStateOf("0") }
    var joined by remember { mutableLongStateOf(today()) }
    var regPaid by remember { mutableStateOf(true) }
    var depPaid by remember { mutableStateOf(true) }
    var feePaid by remember { mutableStateOf(true) }
    LaunchedEffect(types) { if (type.isEmpty() && types.isNotEmpty()) types.first().let { type = it.name; fee = it.fee.toString(); deposit = it.deposit.toString(); reg = it.regFee.toString() } }
    val seatN = seat.toIntOrNull() ?: 0
    val taken = members.any { it.active && it.seatNo == seatN }
    val valid = name.isNotBlank() && mobile.length == 10 && (aadhaar.isEmpty() || aadhaar.length == 12) && seatN in 1..AppSettings.seatCount && !taken
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Add library member", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
        NumberField("Mobile number (10 digits)", mobile, 10, { mobile = it }, phone = true)
        NumberField("Aadhaar number (12 digits)", aadhaar, 12, { aadhaar = it })
        NumberField("Seat number (1 to ${AppSettings.seatCount})", seat, 3, { seat = it })
        if (taken) Text("Seat $seatN is already taken", color = MaterialTheme.colorScheme.error)
        Text("Seat type", style = MaterialTheme.typography.labelLarge)
        ChipChoice(types.map { it.name }, type) { n -> type = n; types.firstOrNull { it.name == n }?.let { fee = it.fee.toString(); deposit = it.deposit.toString(); reg = it.regFee.toString() } }
        NumberField("Monthly fee (₹)", fee, 6, { fee = it })
        NumberField("Deposit (₹)", deposit, 6, { deposit = it })
        NumberField("Registration fee (₹)", reg, 6, { reg = it })
        DateField("Joining date", joined) { joined = it }
        CheckRow("Registration fee received", regPaid) { regPaid = it }
        CheckRow("Deposit received", depPaid) { depPaid = it }
        CheckRow("First month fee received", feePaid) { feePaid = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onDone) { Text("Cancel") }
            BigButton("Save", {
                val f = fee.toLongOrNull() ?: 0; val d = deposit.toLongOrNull() ?: 0; val r = reg.toLongOrNull() ?: 0
                val pays = buildList {
                    if (regPaid && r > 0) add(LibraryPayment(memberId = 0, kind = "REGISTRATION", amount = r, paidOn = joined))
                    if (depPaid && d > 0) add(LibraryPayment(memberId = 0, kind = "DEPOSIT", amount = d, paidOn = joined))
                    if (feePaid && f > 0) add(LibraryPayment(memberId = 0, kind = "FEE", amount = f, paidOn = joined))
                }
                val valid1 = if (feePaid) LocalDate.ofEpochDay(joined).plusMonths(1).toEpochDay() else joined
                vm.add(LibraryMember(name = name.trim(), mobile = mobile, aadhaar = aadhaar, seatNo = seatN, seatType = type, monthlyFee = f,
                    deposit = d, depositPaid = depPaid && d > 0, joinedOn = joined, validTill = valid1), pays)
                onDone()
            }, enabled = valid)
        }
    }
}

@Composable
private fun CheckRow(label: String, v: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!v) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(v, onChange); Text(label) }

@Composable
private fun MemberDetail(vm: LibraryViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mm by remember(id) { vm.member(id) }.collectAsStateWithLifecycle(null)
    val pays by remember(id) { vm.payments(id) }.collectAsStateWithLifecycle(emptyList())
    var confirm by remember { mutableStateOf(false) }
    var vacate by remember { mutableStateOf(false) }
    val m = mm ?: return
    val t = today()
    val due = m.validTill < t
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubTop(m.name, onBack) { IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "Delete") } }
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Seat ${m.seatNo}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Brand.Blue700, modifier = Modifier.weight(1f))
                if (!m.active) InfoPill("Left") else if (due) BadPill("Fee due") else GoodPill("Fee paid")
            }
            Text("${m.seatType} · ${m.mobile}")
            Text("Aadhaar: ${if (m.aadhaar.isBlank()) "—" else maskAadhaar(m.aadhaar)}")
            Text("Joined ${m.joinedOn.asDate()}")
            Text(if (due) "Fee was due on ${m.validTill.asDate()}" else "Fee paid up to ${m.validTill.asDate()}", color = if (due) MaterialTheme.colorScheme.error else Brand.Slate500)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Monthly fee ${rupees(m.monthlyFee)}   ·   Deposit ${rupees(m.deposit)}")
            Text(when {
                m.depositReturnedOn != null -> "Deposit returned on ${m.depositReturnedOn.asDate()}"
                m.depositPaid -> "Deposit held: ${rupees(m.deposit)}"
                else -> "Deposit not received yet"
            }, fontWeight = FontWeight.SemiBold)
        }
        if (m.active) {
            BigButton("Fee received — renew 1 month (${rupees(m.monthlyFee)})", {
                val base = maxOf(m.validTill, t)
                vm.pay(m.copy(validTill = LocalDate.ofEpochDay(base).plusMonths(1).toEpochDay()), "FEE", m.monthlyFee)
            }, Modifier.fillMaxWidth(), accent = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!m.depositPaid && m.deposit > 0) OutlinedButton({ vm.pay(m.copy(depositPaid = true), "DEPOSIT", m.deposit) }, Modifier.weight(1f)) { Text("Receive deposit") }
                if (m.depositPaid && m.depositReturnedOn == null) OutlinedButton({ vm.pay(m.copy(depositReturnedOn = t), "DEPOSIT_RETURN", m.deposit) }, Modifier.weight(1f)) { Text("Return deposit") }
                OutlinedButton({
                    val text = "नमस्कार ${m.name}, ${AppSettings.academyName} लायब्ररी: तुमची मासिक फी ${rupees(m.monthlyFee)} ${m.validTill.asDate()} रोजी देय आहे. धन्यवाद."
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91${m.mobile}?text=" + Uri.encode(text)))) }
                }, Modifier.weight(1f)) { Text("Remind (WhatsApp)") }
            }
            OutlinedButton({ vacate = true }, Modifier.fillMaxWidth()) { Text("Member left — free the seat") }
        }
        SectionTitle("Payments")
        if (pays.isEmpty()) Text("No payments yet", color = Brand.Slate500)
        pays.forEach { p ->
            AppCard { Row { Text(kindLabel(p.kind), Modifier.weight(1f)); Text((if (p.kind == "DEPOSIT_RETURN") "-" else "") + rupees(p.amount) + "  ·  " + p.paidOn.asDate()) } }
        }
    }
    if (vacate) AlertDialog(onDismissRequest = { vacate = false }, title = { Text("Free seat ${m.seatNo}?") },
        text = { Text(if (m.depositPaid && m.depositReturnedOn == null) "Deposit of ${rupees(m.deposit)} has not been returned yet. Return it first if needed." else "The member's records are kept.") },
        confirmButton = { TextButton({ vm.update(m.copy(active = false)); vacate = false; onBack() }) { Text("Free seat") } },
        dismissButton = { TextButton({ vacate = false }) { Text("Cancel") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete ${m.name}?") }, text = { Text("All their payment records are deleted too.") },
        confirmButton = { TextButton({ vm.delete(m); confirm = false; onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}
