package `in`.vidyadhan.academy.students

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.khata.KhataStudentSection
import kotlinx.coroutines.launch
import `in`.vidyadhan.academy.data.*
import `in`.vidyadhan.academy.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Details handed over from an enquiry that has joined. */
data class Prefill(val name: String, val mobile: String, val course: String)
object Handoff { var prefill by mutableStateOf<Prefill?>(null) }

private sealed interface Screen {
    data object List : Screen
    data class Form(val id: Long) : Screen
    data class Detail(val id: Long) : Screen
}

@Composable
fun StudentsTab(openNew: Boolean = false, openId: Long = 0, onOpened: () -> Unit = {}, onAdmit: (() -> Unit)? = null, onOpenMember: (Long) -> Unit = {},
                vm: StudentsViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    LaunchedEffect(openNew, openId, Handoff.prefill) {
        if (openNew || Handoff.prefill != null) { screen = Screen.Form(0); onOpened() }
        else if (openId > 0) { screen = Screen.Detail(openId); onOpened() }
    }
    BackHandler(enabled = screen != Screen.List) { screen = (screen as? Screen.Form)?.takeIf { it.id != 0L }?.let { Screen.Detail(it.id) } ?: Screen.List }
    AnimatedContent(screen, transitionSpec = {
        if (targetState == Screen.List) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "students") { s ->
        when (s) {
            is Screen.Form -> StudentForm(vm, s.id, onCancel = { screen = if (s.id == 0L) Screen.List else Screen.Detail(s.id) }, onSaved = { screen = Screen.Detail(it) })
            else -> StudentList(vm, onAdd = { onAdmit?.invoke() ?: run { screen = Screen.Form(0) } }, onOpen = { screen = Screen.Detail(it) }, onOpenMember = onOpenMember)
        }
    }
    (screen as? Screen.Detail)?.let { d -> StudentSheet(vm, d.id, onClose = { screen = Screen.List }, onEdit = { screen = Screen.Form(d.id) }) }
}

// ───────────────────────── list ─────────────────────────

@Composable
private fun StudentList(vm: StudentsViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit, onOpenMember: (Long) -> Unit) {
    val all by vm.list.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val khataEntries by vm.khataEntries.collectAsStateWithLifecycle()
    val khataBal = remember(khataEntries) { khataEntries.groupBy { it.key }.mapValues { (_, v) -> v.sumOf { it.debit - it.credit } } }
    var filter by rememberSaveable { mutableStateOf("All") }
    val t = today()
    val current = all.filter { it.current }
    val former = all.filter { !it.current }
    val seatOf = members.filter { it.active && it.studentId != null }.associateBy { it.studentId!! }
    val courses = (AppSettings.courseList + current.map { it.course }).filter { it.isNotBlank() }.distinct()
    val libraryOnly = members.filter { it.active && it.studentId == null }.sortedBy { it.name.lowercase() }
    val chips = listOf("All") + courses + (if (libraryOnly.isNotEmpty()) listOf("Abhyasika") else emptyList()) + listOf("Fees due", "Paid", "Former")
    val libShown = when (filter) {
        "All", "Abhyasika" -> libraryOnly
        "Fees due" -> libraryOnly.filter { it.validTill < t || it.balance > 0 }
        "Paid" -> libraryOnly.filter { it.validTill >= t && it.balance == 0L }
        else -> emptyList()
    }
    val shown = (if (filter == "Former") former else current)
        .filter {
            when (filter) {
                "All", "Former" -> true
                "Abhyasika" -> false
                "Fees due" -> it.totalFee > it.paid
                "Paid" -> it.totalFee <= it.paid
                else -> it.course == filter
            }
        }
    HeroPage("Students & fee ledger", "Equal / unequal installments, advance & deposits",
        actions = { PillButton("Admit", onAdd, Brand.Saffron500, Icons.Rounded.Add) },
        header = {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { c ->
                    val sel = c == filter
                    Text(if (c == "Former" && former.isNotEmpty()) "Former (${former.size})" else c,
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.White.copy(alpha = 0.12f)).clickable { filter = c }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium, color = if (sel) Brand.Blue700 else Brand.Blue50, maxLines = 1)
                }
            }
        }) {
        Column {
            if (shown.isEmpty() && libShown.isEmpty()) EmptyState(Icons.Rounded.School,
                if (all.isEmpty()) "No students yet" else if (filter == "Former") "No former students" else "No matches",
                if (all.isEmpty()) "Tap “Admit” to add your first student." else if (filter == "Former") "Students who finish or leave are kept here with their records." else "Try a different name or filter.",
                action = null, onAction = onAdd)
            else LazyColumn(contentPadding = PaddingValues(14.dp, 16.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown, key = { it.id }) { s -> StudentCard(s, seatOf[s.id], t, khataBal[s.khataKey]?.takeIf { s.khataKey.isNotBlank() && it != 0L }) { onOpen(s.id) } }
                items(libShown, key = { "m${it.id}" }) { m -> MemberLedgerCard(m, t) { onOpenMember(m.id) } }
            }
        }
    }
}

/** "VA-2026-07": receipt prefix, joining year, student number. */
fun rollNo(id: Long, joinedOn: Long) = "${AppSettings.receiptPrefix}-${if (joinedOn > 0) LocalDate.ofEpochDay(joinedOn).year else LocalDate.now().year}-${id.toString().padStart(2, '0')}"

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(6.dp)).background(bg).border(1.dp, fg.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
}

@Composable
private fun StudentCard(s: StudentWithFees, seat: LibraryMember?, t: Long, khata: Long?, onClick: () -> Unit) {
    val left = (s.totalFee - s.paid).coerceAtLeast(0)
    val advance = s.advance
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).pressCard(RoundedCornerShape(18.dp), onClick)
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (khata != null && khata != 0L) StatusChip(if (khata > 0) "Old khata owes ${rupees(khata)}" else "Old khata advance ${rupees(-khata)}", if (khata > 0) Tone.Warn else Tone.Info)
        Row(verticalAlignment = Alignment.Top) {
            if (s.photo.isNotBlank()) Avatar(s.name, 42.dp, photo = s.photo)
            else Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Blue100), Alignment.Center) {
                Text(initials(s.name), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Blue700)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text(rollNo(s.id, s.joinedOn), style = MaterialTheme.typography.labelSmall, color = Brand.Slate400, maxLines = 1)
                }
                Text(s.course + if (s.batch.isNotBlank()) " · ${s.batch}" else "", style = MaterialTheme.typography.labelMedium, color = Brand.Blue700, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (s.telegram.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF0F9FF)).border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color(0xFF0369A1), modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp))
                    Text("@${s.telegram}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0369A1), maxLines = 1)
                }
            } else if (!s.current) { Spacer(Modifier.width(6.dp)); StatusChip(s.leftReason.ifBlank { "Former" }, Tone.Neutral) }
        }
        val pills = seat != null || advance > 0
        if (pills) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (seat != null) {
                val xl = seat.seatType.contains("leg", true) || seat.seatType.contains("xl", true)
                Pill("Seat ${seat.seatNo}" + if (seat.seatType.isNotBlank()) " (${seat.seatType})" else "", if (xl) Brand.Violet700 else Brand.Slate600, if (xl) Color(0xFFF5F3FF) else Brand.Slate50)
                if (seat.hasLocker) Pill(if (seat.lockerNo == "-") "Locker" else "Locker ${seat.lockerNo}", Color(0xFF4338CA), Color(0xFFEEF2FF))
                if (seat.depositPaid && seat.depositReturnedOn == null && seat.deposit > 0) Pill("Deposit ${rupees(seat.deposit)}", Brand.Green700, Brand.Green50)
            }
            if (advance > 0) Pill("Advance +${rupees(advance)}", Brand.Amber700, Brand.Amber50)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(12.dp)).padding(vertical = 7.dp)) {
            listOf(Triple("Total plan", rupees(s.totalFee), Brand.Ink), Triple("Paid", rupees(minOf(s.paid, s.totalFee)), Brand.Green600),
                Triple("Balance", rupees(left), if (left > 0) Brand.Saffron500 else Brand.Green600)).forEach { (l, v, c) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
                    Text(v, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c, maxLines = 1)
                }
            }
        }
        HorizontalDivider(color = Brand.Line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    !s.current && left > 0 -> "Left ${s.leftOn?.let { shortDate(it) } ?: ""} · ${rupees(left)} unpaid"
                    !s.current -> "Left ${s.leftOn?.let { shortDate(it) } ?: ""}"
                    left == 0L -> "All installments cleared ✓"
                    s.nextDue != null && s.nextDue < t -> "Overdue ${rupees(s.nextDueAmount)} · ${shortDate(s.nextDue)}"
                    s.nextDue != null -> "Next due ${rupees(s.nextDueAmount)} · ${shortDate(s.nextDue)}"
                    else -> "${rupees(left)} to pay"
                },
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = when { left == 0L && s.current -> Brand.Green700; s.nextDue != null && s.nextDue < t -> Brand.Red600; else -> Brand.Slate600 }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Manage plan", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Blue700)
            Icon(Icons.Rounded.ChevronRight, null, tint = Brand.Blue700, modifier = Modifier.size(16.dp))
        }
    }
}

// ───────────────────────── form ─────────────────────────

private data class PlanRow(val amount: String, val due: Long, val label: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePill(epochDay: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(16.dp))
            .clickable { open = true }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CalendarMonth, null, tint = Brand.Blue600, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(shortDate(epochDay) + " ’" + LocalDate.ofEpochDay(epochDay).year % 100, style = MaterialTheme.typography.labelLarge, color = Brand.Slate800)
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }; open = false }) { Text("Done") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

@Composable
private fun StudentForm(vm: StudentsViewModel, id: Long, onCancel: () -> Unit, onSaved: (Long) -> Unit) {
    val existing by remember(id) { vm.student(id) }.collectAsStateWithLifecycle(null)
    val existingPlan by remember(id) { vm.installments(id) }.collectAsStateWithLifecycle(emptyList())
    var loaded by remember { mutableStateOf(id == 0L) }
    val pre = remember { Handoff.prefill.also { Handoff.prefill = null } }
    var name by remember { mutableStateOf(pre?.name ?: "") }
    var mobile by remember { mutableStateOf(pre?.mobile ?: "") }
    var telegram by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var course by remember { mutableStateOf(pre?.course?.takeIf { it in AppSettings.courseList } ?: AppSettings.courseList.first()) }
    var batch by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf<Long?>(null) }
    var photo by remember { mutableStateOf("") }
    var aadhaarPhoto by remember { mutableStateOf("") }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var fee by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(AppSettings.defaultInstallments) }
    var first by remember { mutableLongStateOf(today()) }
    var joined by remember { mutableLongStateOf(today()) }
    val rows = remember { mutableStateListOf<PlanRow>() }
    var customised by remember { mutableStateOf(false) }
    LaunchedEffect(existing, existingPlan) {
        val s = existing
        if (!loaded && s != null && existingPlan.isNotEmpty()) {
            name = s.name; mobile = s.mobile; aadhaar = s.aadhaar; course = s.course; fee = s.totalFee.toString(); joined = s.joinedOn
            batch = s.batch; dob = s.dob; photo = s.photo; aadhaarPhoto = s.aadhaarPhoto; telegram = s.telegram
            count = existingPlan.size; first = existingPlan.first().dueOn
            rows.clear(); rows.addAll(existingPlan.map { PlanRow(it.amount.toString(), it.dueOn, it.label) })
            customised = true; loaded = true
        }
    }
    val feeN = fee.toLongOrNull() ?: 0L
    LaunchedEffect(feeN, count, first, customised, loaded) {
        if (loaded && !customised) { rows.clear(); if (feeN > 0) rows.addAll(buildPlan(feeN, count, LocalDate.ofEpochDay(first)).map { PlanRow(it.amount.toString(), it.dueOn) }) }
    }
    val planTotal = rows.sumOf { it.amount.toLongOrNull() ?: 0L }
    val planOk = rows.isNotEmpty() && rows.all { (it.amount.toLongOrNull() ?: 0) > 0 } && planTotal == feeN
    val valid = name.isNotBlank() && mobile.length == 10 && (aadhaar.isEmpty() || aadhaar.length == 12) && feeN > 0 && planOk
    val photoPicker = rememberPhotoPicker("Student photo", photo.isNotBlank(), { photo = it }, { photo = "" })
    val aadhaarPicker = rememberPhotoPicker("Aadhaar card photo", aadhaarPhoto.isNotBlank(), { aadhaarPhoto = it }, { aadhaarPhoto = "" })
    HideBottomNav()
    HeroPage(if (id == 0L) "New student" else "Edit student", if (pre != null) "From enquiry · check and add fees" else "Fill in the details below", onBack = onCancel) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            PhotoAvatarEdit(name, photo, photoPicker.open)
            FormCard("Student details", Icons.Rounded.Person) {
                Field(name, { name = it }, "Full name", Icons.Rounded.Person)
                PhoneField(mobile, { mobile = it }, onName = { if (name.isBlank()) name = it },
                    supporting = if (mobile.isNotEmpty() && mobile.length < 10) "Enter all 10 digits" else null, isError = mobile.isNotEmpty() && mobile.length < 10)
                DigitsField("Aadhaar number", aadhaar, 12, { aadhaar = it }, Icons.Rounded.CreditCard,
                    supporting = if (aadhaar.isNotEmpty() && aadhaar.length < 12) "Enter all 12 digits" else "Optional · kept only on this phone", isError = aadhaar.isNotEmpty() && aadhaar.length < 12)
                DocPhoto("Aadhaar card photo", aadhaarPhoto, aadhaarPicker.open) { viewPhoto = aadhaarPhoto }
                OptionalDateField("Date of birth (optional)", dob, { dob = it })
                DateField("Joining date", joined, { joined = it })
            }
            FormCard("Course and batch", Icons.Rounded.School, Brand.Saffron600) {
                ChoicePills(AppSettings.courseList, course, { course = it })
                if (AppSettings.batchList.isNotEmpty()) {
                    Text("Batch", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                    ChoicePills(AppSettings.batchList, batch, { batch = if (batch == it) "" else it })
                }
            }
            FormCard("Fees", Icons.Rounded.CurrencyRupee, Brand.Green600) {
                DigitsField("Total course fee", fee, 7, { fee = it }, prefix = "₹ ")
                Stepper("Installments", count, 1..24) { count = it; customised = false }
                DateField("First installment due", first, { first = it; customised = false })
            }
            if (rows.isNotEmpty()) FormCard("Installment plan", Icons.Rounded.Timeline, Brand.Violet700) {
                Text("Equal split, or change any amount, date or name (unequal plan). Amounts must add up to the total fee.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                rows.forEachIndexed { i, r ->
                    Field(r.label, { v -> rows[i] = r.copy(label = v.take(30)); customised = true }, "Name of installment ${i + 1} (optional)",
                        supporting = "e.g. Admission, Prelims, Mains")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DigitsField("Amount ${i + 1}", r.amount, 7, { v -> rows[i] = r.copy(amount = v); customised = true }, prefix = "₹ ", modifier = Modifier.weight(1f))
                        if (rows.size > 1) IconButton({ rows.removeAt(i); customised = true }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "Remove", tint = Brand.Slate400) }
                    }
                    DatePill(r.due) { d -> rows[i] = r.copy(due = d); customised = true }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SoftButton("Add installment", {
                        val last = rows.lastOrNull()?.due ?: first
                        rows.add(PlanRow((feeN - planTotal).coerceAtLeast(0).toString(), LocalDate.ofEpochDay(last).plusMonths(1).toEpochDay())); customised = true
                    }, Modifier.weight(1f), icon = Icons.Rounded.Add)
                    if (customised) { Spacer(Modifier.width(10.dp)); SoftButton("Split equally", { customised = false }, color = Brand.Slate600, icon = Icons.Rounded.Refresh) }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (planOk) Brand.Green50 else Brand.Red50).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (planOk) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = if (planOk) Brand.Green600 else Brand.Red600)
                    Spacer(Modifier.width(8.dp))
                    Text(if (planOk) "Plan adds up to ${rupees(feeN)}" else if (planTotal < feeN) "${rupees(feeN - planTotal)} still to place in the plan" else "Plan is ${rupees(planTotal - feeN)} more than the fee",
                        style = MaterialTheme.typography.labelLarge, color = if (planOk) Brand.Green700 else Brand.Red700)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            SoftButton("Cancel", onCancel, Modifier.weight(1f), color = Brand.Slate600)
            PrimaryButton(if (id == 0L) "Add student" else "Save changes", {
                val s = (existing ?: Student(name = "", mobile = "", totalFee = 0, joinedOn = joined)).copy(
                    id = if (id == 0L) 0 else id, name = name.trim(), mobile = mobile, aadhaar = aadhaar, course = course, totalFee = feeN, joinedOn = joined,
                    batch = batch, dob = dob, photo = photo, aadhaarPhoto = aadhaarPhoto, telegram = telegram)
                val plan = rows.sortedBy { it.due }.mapIndexed { i, r -> Installment(studentId = s.id, number = i + 1, amount = r.amount.toLong(), dueOn = r.due, label = r.label.trim()) }
                vm.save(s, plan, onSaved)
            }, Modifier.weight(1.6f), enabled = valid, icon = Icons.Rounded.Check)
        }
    }
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
}

// ───────────────────────── detail ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentSheet(vm: StudentsViewModel, id: Long, onClose: () -> Unit, onEdit: () -> Unit) {
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White, dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Box(Modifier.fillMaxHeight(0.9f)) { StudentDetail(vm, id, onClose, onEdit) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDetail(vm: StudentsViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val s by remember(id) { vm.student(id) }.collectAsStateWithLifecycle(null)
    val plan by remember(id) { vm.installments(id) }.collectAsStateWithLifecycle(emptyList())
    val payments by remember(id) { vm.payments(id) }.collectAsStateWithLifecycle(emptyList())
    val khataEntries by vm.khataEntries.collectAsStateWithLifecycle()
    val seat by remember(id) { vm.memberFor(id) }.collectAsStateWithLifecycle(null)
    val members by vm.members.collectAsStateWithLifecycle()
    var payFor by remember { mutableStateOf<Long?>(null) } // suggested amount
    var linking by remember { mutableStateOf(false) }
    var refund by remember { mutableStateOf(false) }
    var resplit by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf(false) }
    var tgMessage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletePay by remember { mutableStateOf<StudentPayment?>(null) }
    var saved by remember { mutableStateOf<StudentPayment?>(null) }
    var showAadhaar by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val st = s ?: return
    val t = today()
    val paid = payments.sumOf { it.amount }
    val balance = (st.totalFee - paid).coerceAtLeast(0)
    val statuses = allocate(plan, paid)
    val next = statuses.firstOrNull { !it.fullyPaid }
    val frac = if (st.totalFee == 0L) 0f else paid.toFloat() / st.totalFee

    var receiptFor by remember { mutableStateOf<StudentPayment?>(null) }
    fun receipt(p: StudentPayment) { receiptFor = p }
    fun receiptOf(p: StudentPayment): ReceiptData {
        val upTo = payments.filter { it.paidOn < p.paidOn || (it.paidOn == p.paidOn && it.id <= p.id) }.sumOf { it.amount }
        return ReceiptData("${AppSettings.receiptPrefix}-S${p.id.toString().padStart(5, '0')}", p.paidOn, "FEE RECEIPT  /  फी पावती", st.name,
            listOf("Mobile" to "+91 ${st.mobile}", "Course" to (st.course + if (st.batch.isNotBlank()) " · ${st.batch}" else ""), "Payment mode" to p.mode.ifBlank { "—" }) +
                (if (p.note.isNotBlank()) listOf("Note" to p.note) else emptyList()),
            p.amount, listOf("Total fee" to rupees(st.totalFee), "Paid so far" to rupees(upTo), "Balance" to rupees((st.totalFee - upTo).coerceAtLeast(0))),
            footnote = next?.let { "Next installment ${rupees(it.remaining)} on ${it.inst.dueOn.asDate()}" }.orEmpty())
    }

    val m = seat
    val planType = when {
        plan.size <= 1 -> "Full"
        plan.maxOf { it.amount } - plan.minOf { it.amount } <= plan.size -> "Equal"
        else -> "Unequal"
    }
    val seatDue = m?.let { it.balance + if (it.validTill < t) it.monthlyFee else 0L } ?: 0L
    Column(Modifier.fillMaxSize().background(Color.White)) {
        // header like the prototype's student sheet
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${rollNo(st.id, st.joinedOn)} · $planType plan".uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Blue700, maxLines = 1)
                Text(st.name, style = MaterialTheme.typography.titleLarge, color = Brand.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            HeaderButton(Icons.Rounded.Call, "Call", Brand.Blue700) { Contact.call(ctx, st.mobile) }
            HeaderButton(Icons.Rounded.NotificationsActive, "Remind", Brand.Saffron500) { if (next != null) remind = true else Contact.whatsapp(ctx, st.mobile) }
            HeaderButton(Icons.Rounded.Edit, "Edit", Brand.Slate600, onEdit)
            HeaderButton(Icons.Rounded.Close, "Back", Brand.Slate600, onBack)
        }
        HorizontalDivider(color = Brand.Line)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (st.leftOn != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Slate50).border(1.dp, Brand.Slate200, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = Brand.Slate600); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Former student", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                    Text("${st.leftReason.ifBlank { "Left" }} · ${st.leftOn.asDate()}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                }
                PillButton("Make current", { vm.update(st.copy(leftOn = null, leftReason = "")) }, Brand.Blue700)
            }

            // fee components (course + Abhyasika seat + locker)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Slate50).border(1.dp, Brand.Slate200.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FeeLine("Course (${st.course}):", rupees(st.totalFee))
                if (m != null) {
                    FeeLine("Library seat (${m.seatNo}" + (if (m.seatType.isNotBlank()) " · ${m.seatType}" else "") + "):", rupees(m.monthlyFee - m.lockerFee))
                    if (m.hasLocker) FeeLine("Separate locker" + (if (m.lockerNo != "-") " (${m.lockerNo})" else "") + ":", rupees(m.lockerFee))
                }
                HorizontalDivider(Modifier.padding(top = 2.dp), color = Brand.Slate200)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total fee / balance:", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Slate800)
                    Text("${rupees(st.totalFee + (m?.monthlyFee ?: 0))} / ${rupees(balance + seatDue)} Due", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Brand.Saffron500)
                }
                if (m != null) Text("Seat and locker are monthly · paid up to ${m.validTill.asDate()}", style = MaterialTheme.typography.labelSmall,
                    color = if (m.validTill < t) Brand.Red600 else Brand.Slate500)
                else if (st.leftOn == null) Text("+ Link an Abhyasika seat (Combo)", Modifier.clip(RoundedCornerShape(8.dp)).clickable { linking = true }.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Blue700)
            }

            // refundable deposit and advance credit
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val held = m != null && m.depositPaid && m.depositReturnedOn == null && m.deposit > 0
                MoneyBox("REFUNDABLE DEPOSIT", rupees(m?.deposit ?: 0) + " (" + when { m == null || m.deposit == 0L -> "None"; held -> "Held"; m.depositReturnedOn != null -> "Refunded"; else -> "Not paid" } + ")",
                    Brand.Green700, Brand.Green50, if (held) "Refund deposit" else null, Modifier.weight(1f).fillMaxHeight()) { refund = true }
                MoneyBox("ADVANCE CREDIT", rupees(advanceOf(paid, plan, t)), Brand.Amber700, Brand.Amber50, if (balance > 0) "+ Add ${rupees(minOf(balance, 1000L))} advance" else null,
                    Modifier.weight(1f).fillMaxHeight()) { payFor = minOf(balance, 1000L) }
            }

            // installment schedule
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("INSTALLMENT SCHEDULE", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Slate600)
                if (balance > 0 && statuses.count { !it.fullyPaid } > 1) Text("Re-split balance equally", Modifier.clickable { resplit = true },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = Brand.Blue700)
            }
            val paidOn = paidDates(statuses, payments)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                statuses.forEach { s2 -> ScheduleRow(s2, paidOn[s2.inst.number], t, onCollect = { payFor = s2.remaining }) }
            }
            if (balance > 0) SoftButton("Record a payment", { payFor = next?.remaining ?: balance }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            else Text("All installments cleared ✓", style = MaterialTheme.typography.labelLarge, color = Brand.Green700)

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                InfoRow(Icons.Rounded.Phone, "Mobile", "+91 ${st.mobile}")
                HorizontalDivider(color = Brand.Line)
                val linked = `in`.vidyadhan.academy.telegram.TelegramBot.isLinked("s${st.id}")
                InfoRow(Icons.AutoMirrored.Rounded.Send, "Telegram", if (linked) "Connected ✓" else "Not connected yet") {
                    if (!linked) PillButton("Send link", {
                        if (`in`.vidyadhan.academy.telegram.TelegramBot.connected) Contact.whatsapp(ctx, st.mobile, `in`.vidyadhan.academy.telegram.TelegramBot.invite("s${st.id}"))
                        else android.widget.Toast.makeText(ctx, "Connect your Telegram bot first: Bot & More", android.widget.Toast.LENGTH_LONG).show()
                    }, Color(0xFF0284C7))
                    else PillButton("Message", { tgMessage = true }, Color(0xFF0284C7), Icons.AutoMirrored.Rounded.Send)
                }
                HorizontalDivider(color = Brand.Line)
                InfoRow(Icons.Rounded.School, "Course", st.course + (if (st.batch.isNotBlank()) " · ${st.batch}" else "") + " · joined ${st.joinedOn.asDate()}")
                if (st.dob != null) { HorizontalDivider(color = Brand.Line); InfoRow(Icons.Rounded.Cake, "Birthday", LocalDate.ofEpochDay(st.dob).format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy"))) }
                HorizontalDivider(color = Brand.Line)
                InfoRow(Icons.Rounded.CreditCard, "Aadhaar", if (st.aadhaar.isBlank()) "Not added" else if (showAadhaar) st.aadhaar.chunked(4).joinToString(" ") else maskAadhaar(st.aadhaar)) {
                    if (st.aadhaar.isNotBlank()) SmallIconButton(if (showAadhaar) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show", Brand.Slate500) { showAadhaar = !showAadhaar }
                }
                if (st.aadhaarPhoto.isNotBlank()) { DocPhoto("Aadhaar card photo", st.aadhaarPhoto, onEdit) { viewPhoto = st.aadhaarPhoto }; Spacer(Modifier.height(8.dp)) }
            }
                SectionHeader("Payments received", action = if (payments.isNotEmpty()) rupees(paid) else null)
                if (payments.isEmpty()) Text("No payments yet. Tap “Record a payment” when the student pays.", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                else Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                    payments.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = Brand.Line)
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(when (p.mode) { "UPI" -> Icons.Rounded.QrCode2; "Bank" -> Icons.Rounded.AccountBalance; else -> Icons.Rounded.Payments },
                                when (p.mode) { "UPI" -> Brand.Violet700; "Bank" -> Brand.Blue600; else -> Brand.Green600 }, 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("+ " + rupees(p.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Green700)
                                Text(p.paidOn.asDate() + (if (p.mode.isNotBlank()) " · ${p.mode}" else "") + (if (p.note.isNotBlank()) " · ${p.note}" else ""),
                                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
                            }
                            SmallIconButton(Icons.AutoMirrored.Rounded.Chat, "Send receipt on WhatsApp", Brand.WhatsAppDark) { whatsappReceipt(ctx, receiptOf(p), st.mobile) }
                            Spacer(Modifier.width(6.dp))
                            SmallIconButton(Icons.Rounded.ReceiptLong, "Receipt", Brand.Blue600) { receiptFor = p }
                            Spacer(Modifier.width(6.dp))
                            SmallIconButton(Icons.Rounded.DeleteOutline, "Delete payment", Brand.Slate400) { deletePay = p }
                        }
                    }
                }
                st.khataKey.takeIf { it.isNotBlank() }?.let { KhataStudentSection(it, khataEntries) }
                NotesSection("STUDENT", st.id)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Admission form", {
                        shareAdmissionForm(ctx, "ADMISSION FORM  /  प्रवेश अर्ज", "${AppSettings.receiptPrefix}-A${st.id.toString().padStart(4, '0')}", st.name, st.photo,
                            listOf("Mobile" to "+91 ${st.mobile}", "Aadhaar" to st.aadhaar.chunked(4).joinToString(" ").ifBlank { "—" },
                                "Date of birth" to (st.dob?.asDate() ?: "—"), "Course" to st.course, "Batch" to st.batch.ifBlank { "—" },
                                "Joining date" to st.joinedOn.asDate(), "Total fee" to rupees(st.totalFee)),
                            "Fee plan", listOf(listOf("No.", "Due date", "Amount")) + plan.map { listOf("${it.number}", it.dueOn.asDate(), rupees(it.amount)) })
                    }, Modifier.weight(1f), icon = Icons.Rounded.Description)
                    if (st.leftOn == null) SoftButton("Former student", { leaving = true }, Modifier.weight(1f), color = Brand.Slate600, icon = Icons.Rounded.History)
                }
                Text("Delete student", Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).clickable { confirmDelete = true }.padding(8.dp),
                    style = MaterialTheme.typography.labelLarge, color = Brand.Red600)
                Spacer(Modifier.height(20.dp))
        }
    }

    payFor?.let { suggested ->
        var amount by remember { mutableStateOf(suggested.toString()) }
        var mode by remember { mutableStateOf("Cash") }
        var date by remember { mutableLongStateOf(t) }
        var note by remember { mutableStateOf("") }
        val a = amount.toLongOrNull() ?: 0
        ModalBottomSheet({ payFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTitle("Record a payment", "Balance ${rupees(balance)} · any amount is fine")
                DigitsField("Amount received", amount, 7, { amount = it }, prefix = "₹ ",
                    supporting = if (a > balance) "More than the balance of ${rupees(balance)}" else null, isError = a > balance)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (next != null && next.remaining != balance) SoftButton("This installment ${rupees(next.remaining)}", { amount = next.remaining.toString() }, color = Brand.Blue700)
                    SoftButton("Full balance ${rupees(balance)}", { amount = balance.toString() }, color = Brand.Green700)
                }
                Text("Paid by", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                ModeChoice(mode) { mode = it }
                DateField("Date received", date, { date = it })
                Field(note, { note = it }, "Note (optional), e.g. cheque no.", Icons.Rounded.EditNote)
                PrimaryButton("Save payment", {
                    vm.pay(StudentPayment(studentId = st.id, amount = a, paidOn = date, mode = mode, note = note.trim())) { saved = it }
                    payFor = null
                }, Modifier.fillMaxWidth(), enabled = a in 1..balance, icon = Icons.Rounded.Check)
            }
        }
    }
    saved?.let { p ->
        AlertDialog({ saved = null }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            icon = { IconTile(Icons.Rounded.CheckCircle, Brand.Green600, 52.dp, filled = true) },
            title = { Text("${rupees(p.amount)} received") },
            text = { Text("Share a receipt with ${st.name}?", color = Brand.Slate500) },
            confirmButton = { TextButton({ receipt(p); saved = null }) { Text("Share receipt", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ saved = null }) { Text("Done") } })
    }
    if (tgMessage) {
        var msg by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        ModalBottomSheet({ tgMessage = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.imePadding().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTitle("Message on Telegram", "To ${st.name} · sent by your bot")
                Field(msg, { msg = it }, "Type your message", singleLine = false, minLines = 4)
                PrimaryButton(if (sending) "Sending…" else "Send", {
                    sending = true
                    scope.launch {
                        val ok = `in`.vidyadhan.academy.telegram.TelegramBot.sendToKey("s${st.id}",
                            `in`.vidyadhan.academy.telegram.TelegramBot.esc(msg.trim()) + "\n\n— " + `in`.vidyadhan.academy.telegram.TelegramBot.esc(AppSettings.academyName))
                        sending = false
                        android.widget.Toast.makeText(ctx, if (ok) "Sent on Telegram ✓" else "Could not send. Check the bot in Bot & More.", android.widget.Toast.LENGTH_LONG).show()
                        if (ok) tgMessage = false
                    }
                }, Modifier.fillMaxWidth(), enabled = msg.isNotBlank() && !sending, icon = Icons.AutoMirrored.Rounded.Send,
                    custom = Brush.linearGradient(listOf(Color(0xFF229ED9), Color(0xFF1C86BB))))
                Text("Replies from the student are not shown in the app; they see your message in their Telegram chat with the bot.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate400)
            }
        }
    }
    if (remind && next != null) {
        var marathi by remember { mutableStateOf(true) }
        val text = feeMessage(marathi, st.name, next.remaining, next.inst.dueOn, if (marathi) "विद्याधन अकॅडमी" else AppSettings.academyName)
        ModalBottomSheet({ remind = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetTitle("Send a fee reminder", "To ${st.name} · +91 ${st.mobile}")
                Segmented(listOf("mr" to "मराठी", "en" to "English"), if (marathi) "mr" else "en", { marathi = it == "mr" })
                MessageBubble(text, marathi)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("WhatsApp", { Contact.whatsapp(ctx, st.mobile, text); remind = false }, Modifier.weight(1.4f), icon = Icons.AutoMirrored.Rounded.Chat,
                        custom = Brush.linearGradient(listOf(Brand.WhatsApp, Brand.WhatsAppDark)))
                    SoftButton("Telegram", { `in`.vidyadhan.academy.telegram.TelegramBot.sendOrExplain(ctx, "s${st.id}", st.mobile, `in`.vidyadhan.academy.telegram.TelegramBot.esc(text)); remind = false }, Modifier.weight(1f), color = Color(0xFF0284C7), icon = Icons.AutoMirrored.Rounded.Send)
                }
                SoftButton("SMS", { Contact.sms(ctx, st.mobile, text); remind = false }, Modifier.fillMaxWidth(), color = Brand.Slate600, icon = Icons.Rounded.Sms)
                Text("The app opens with the message typed. You press send.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate400)
            }
        }
    }
    if (leaving) {
        var reason by remember { mutableStateOf("Completed the course") }
        var date by remember { mutableLongStateOf(t) }
        ModalBottomSheet({ leaving = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTitle("Move to former students", "${st.name}'s records and payments are kept")
                ChoicePills(listOf("Completed the course", "Left midway", "Selected in exam 🎉", "Other"), reason, { reason = it })
                DateField("Date", date, { date = it })
                if (balance > 0) Text("${rupees(balance)} is still unpaid. It stays visible under Former.", style = MaterialTheme.typography.bodySmall, color = Brand.Red600)
                PrimaryButton("Move to former", { vm.update(st.copy(leftOn = date, leftReason = reason)); leaving = false; onBack() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check)
            }
        }
    }
    if (linking) {
        val free = members.filter { it.active && it.studentId == null }.sortedBy { if (it.mobile == st.mobile) 0 else 1 }
        ModalBottomSheet({ linking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetTitle("Link an Abhyasika seat", "Pick ${st.name.substringBefore(' ')}'s seat. Seat fees stay on the Abhyasika page.")
                if (free.isEmpty()) Text("No Abhyasika member without a student. Add ${st.name.substringBefore(' ')} on the Abhyasika page first.", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                free.forEach { m ->
                    OptionRow(Icons.Rounded.EventSeat, Brand.Teal700, "${m.name} · Seat ${m.seatNo}", "+91 ${m.mobile}" + if (m.mobile == st.mobile) " · same mobile" else "") {
                        vm.link(m, st.id); linking = false
                    }
                }
            }
        }
    }
    if (refund) seat?.let { m ->
        ConfirmDialog("Refund ${rupees(m.deposit)} deposit?", "Records the deposit as returned to ${st.name} today.", "Refund", onConfirm = { vm.returnDeposit(m); refund = false }, onDismiss = { refund = false })
    }
    if (resplit) ConfirmDialog("Re-split the balance equally?", "Paid installments stay. The ${rupees(balance)} still due is divided equally over the ${statuses.count { !it.fullyPaid }} unpaid installments, on their dates.",
        "Re-split", onConfirm = { vm.resplit(st, plan, paid); resplit = false }, onDismiss = { resplit = false })
    receiptFor?.let { p -> ReceiptActions(receiptOf(p), st.mobile, "s${st.id}", onDelete = { deletePay = p }, onDismiss = { receiptFor = null }) }
    deletePay?.let { p -> ConfirmDialog("Delete this payment?", "${rupees(p.amount)} received on ${p.paidOn.asDate()} will be removed.", "Delete", danger = true,
        onConfirm = { vm.deletePayment(p); deletePay = null }, onDismiss = { deletePay = null }) }
    if (confirmDelete) ConfirmDialog("Delete ${st.name}?", "This removes the student, their installments and payments. It cannot be undone.", "Delete", danger = true,
        onConfirm = { Photos.delete(ctx, st.photo); Photos.delete(ctx, st.aadhaarPhoto); vm.delete(st); confirmDelete = false; onBack() }, onDismiss = { confirmDelete = false })
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
}

@Composable
private fun FeeLine(label: String, value: String, valueColor: Color = Brand.Slate800) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        Text(value, style = MaterialTheme.typography.labelLarge, color = valueColor)
    }
}

@Composable
private fun MoneyBox(label: String, value: String, fg: Color, bg: Color, action: String?, modifier: Modifier, onAction: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
        Spacer(Modifier.weight(1f))
        if (action != null) Text(action, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = fg,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

/** For each fully paid installment: the payment that completed it (payments fill installments in order). */
private fun paidDates(statuses: List<InstallmentStatus>, payments: List<StudentPayment>): Map<Int, StudentPayment> {
    val pays = payments.sortedWith(compareBy({ it.paidOn }, { it.id }))
    val out = mutableMapOf<Int, StudentPayment>()
    var need = 0L
    statuses.forEach { st ->
        need += st.inst.amount
        if (st.fullyPaid) {
            var cum = 0L
            pays.firstOrNull { cum += it.amount; cum >= need }?.let { out[st.inst.number] = it }
        }
    }
    return out
}

@Composable
private fun ScheduleRow(s: InstallmentStatus, paidBy: StudentPayment?, t: Long, onCollect: () -> Unit) {
    val i = s.inst
    val late = !s.fullyPaid && i.dueOn < t
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (s.fullyPaid) Brand.Green50.copy(alpha = 0.6f) else Color.White)
        .border(1.dp, if (s.fullyPaid) Brand.Green100 else if (late) Brand.Red100 else Brand.Line, RoundedCornerShape(14.dp)).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${i.label.ifBlank { "Inst ${i.number}" }} · ${rupees(i.amount)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            val lateFee = if (late && AppSettings.lateFeeOn) (t - i.dueOn) * AppSettings.lateFeePerDay else 0L
            Text(
                when {
                    s.fullyPaid -> "Paid on ${(paidBy?.paidOn ?: i.dueOn).asDate()}" + (paidBy?.mode?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                    s.partlyPaid -> "${rupees(s.covered)} paid · ${rupees(s.remaining)} left · due ${i.dueOn.asDate()}"
                    late -> "Overdue since ${i.dueOn.asDate()}" + if (lateFee > 0) " · late fee ${rupees(lateFee)}" else ""
                    else -> "Due date: ${i.dueOn.asDate()}"
                },
                style = MaterialTheme.typography.bodySmall, color = when { s.fullyPaid -> Brand.Green700; late -> Brand.Red600; s.partlyPaid -> Brand.Amber700; else -> Brand.Slate500 })
        }
        if (s.fullyPaid) StatusChip("Paid ✓", Tone.Good, dot = false)
        else PillButton("Collect", onCollect, if (late) Brand.Red600 else Brand.Blue700)
    }
}

@Composable
private fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape).background(Brand.Slate50).border(1.dp, Brand.Line, CircleShape).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, desc, tint = color, modifier = Modifier.size(18.dp))
    }
}

/** An Abhyasika-only member in the students list (the prototype lists everyone together). */
@Composable
private fun MemberLedgerCard(m: LibraryMember, t: Long, onClick: () -> Unit) {
    val due = m.balance + if (m.validTill < t) m.monthlyFee else 0L
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).pressCard(RoundedCornerShape(18.dp), onClick)
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Blue100), Alignment.Center) {
                Text(initials(m.name), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Blue700)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text("${AppSettings.receiptPrefix}-L${m.id.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall, color = Brand.Slate400, maxLines = 1)
                }
                Text("Abhyasika (Library) only", style = MaterialTheme.typography.labelMedium, color = Brand.Blue700, maxLines = 1)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val xl = m.seatType.contains("leg", true) || m.seatType.contains("xl", true)
            Pill("Seat ${m.seatNo}" + if (m.seatType.isNotBlank()) " (${m.seatType})" else "", if (xl) Brand.Violet700 else Brand.Slate600, if (xl) Color(0xFFF5F3FF) else Brand.Slate50)
            if (m.hasLocker) Pill(if (m.lockerNo == "-") "Locker" else "Locker ${m.lockerNo}", Color(0xFF4338CA), Color(0xFFEEF2FF))
            if (m.depositPaid && m.depositReturnedOn == null && m.deposit > 0) Pill("Deposit ${rupees(m.deposit)}", Brand.Green700, Brand.Green50)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(12.dp)).padding(vertical = 7.dp)) {
            listOf(Triple("Monthly", rupees(m.monthlyFee), Brand.Ink), Triple("Paid till", shortDate(m.validTill), if (m.validTill < t) Brand.Red600 else Brand.Green600),
                Triple("Balance", rupees(due), if (due > 0) Brand.Saffron500 else Brand.Green600)).forEach { (l, v, c) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
                    Text(v, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c, maxLines = 1)
                }
            }
        }
        HorizontalDivider(color = Brand.Line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (m.validTill < t) "Renewal overdue since ${m.validTill.asDate()}" else "Next due: ${rupees(m.monthlyFee)} on ${m.validTill.asDate()}",
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = if (m.validTill < t) Brand.Red600 else Brand.Slate600, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Manage plan", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Blue700)
            Icon(Icons.Rounded.ChevronRight, null, tint = Brand.Blue700, modifier = Modifier.size(16.dp))
        }
    }
}
