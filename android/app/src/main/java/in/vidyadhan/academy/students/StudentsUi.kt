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
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.vidyadhan.academy.AppSettings
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
fun StudentsTab(openNew: Boolean = false, openId: Long = 0, onOpened: () -> Unit = {}, vm: StudentsViewModel = viewModel()) {
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
            Screen.List -> StudentList(vm, onAdd = { screen = Screen.Form(0) }, onOpen = { screen = Screen.Detail(it) })
            is Screen.Form -> StudentForm(vm, s.id, onCancel = { screen = if (s.id == 0L) Screen.List else Screen.Detail(s.id) }, onSaved = { screen = Screen.Detail(it) })
            is Screen.Detail -> StudentDetail(vm, s.id, onBack = { screen = Screen.List }, onEdit = { screen = Screen.Form(s.id) })
        }
    }
}

// ───────────────────────── list ─────────────────────────

@Composable
private fun StudentList(vm: StudentsViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val all by vm.list.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var batch by rememberSaveable { mutableStateOf("") }
    val t = today()
    val current = all.filter { it.current }
    val former = all.filter { !it.current }
    val total = current.sumOf { it.totalFee }
    val paid = current.sumOf { minOf(it.paid, it.totalFee) }
    val batches = (AppSettings.batchList + all.map { it.batch }).filter { it.isNotBlank() }.distinct()
    val shown = (if (filter == "former") former else current).filter { q.isBlank() || it.name.contains(q, true) || it.mobile.contains(q) }
        .filter { batch.isEmpty() || it.batch == batch }
        .filter { when (filter) { "due" -> it.totalFee > it.paid; "paid" -> it.totalFee <= it.paid; else -> true } }
    HeroPage("Students", "${current.size} studying now" + if (former.isNotEmpty()) " · ${former.size} former" else "", header = {
        Spacer(Modifier.height(16.dp))
        SearchBox(q, { q = it }, "Search by name or mobile")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassStat("Total fees", rupees(total), Modifier.weight(1f))
            GlassStat("Collected", rupees(paid), Modifier.weight(1f))
            GlassStat("Pending", rupees(total - paid), Modifier.weight(1f), valueColor = Brand.Saffron200)
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Column {
                Segmented(listOf("all" to "All", "due" to "Fees due", "paid" to "Paid", "former" to "Former"), filter, { filter = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp))
                if (batches.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("") + batches).forEach { b ->
                        val sel = b == batch
                        Text(if (b.isEmpty()) "All batches" else b,
                            Modifier.clip(RoundedCornerShape(50)).background(if (sel) Brand.Saffron600 else Color.White).border(1.dp, if (sel) Brand.Saffron600 else Brand.Line, RoundedCornerShape(50))
                                .clickable { batch = b }.padding(horizontal = 14.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else Brand.Slate600)
                    }
                }
                if (shown.isEmpty()) EmptyState(Icons.Rounded.School,
                    if (all.isEmpty()) "No students yet" else if (filter == "former") "No former students" else "No matches",
                    if (all.isEmpty()) "Add your first MPSC student to start tracking fees." else if (filter == "former") "Students who finish or leave are kept here with their records." else "Try a different name, batch or filter.",
                    action = if (all.isEmpty()) "Add student" else null, onAction = onAdd)
                else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.id }) { s -> StudentCard(s, t) { onOpen(s.id) } }
                }
            }
            if (all.isNotEmpty()) Fab("Add student", Icons.Rounded.PersonAdd, onAdd, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
}

@Composable
private fun StudentCard(s: StudentWithFees, t: Long, onClick: () -> Unit) {
    val left = (s.totalFee - s.paid).coerceAtLeast(0)
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.name, photo = s.photo)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallTag(s.course)
                    if (s.batch.isNotBlank()) { Spacer(Modifier.width(6.dp)); SmallTag(s.batch, Brand.Saffron700) }
                    Spacer(Modifier.width(8.dp))
                    Text(s.mobile, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
                }
            }
            if (!s.current) StatusChip(s.leftReason.ifBlank { "Former" }, Tone.Neutral)
            else if (left > 0) Column(horizontalAlignment = Alignment.End) {
                Text(rupees(left), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron700)
                Text("pending", style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
            } else StatusChip("Paid", Tone.Good)
        }
        Spacer(Modifier.height(14.dp))
        ProgressBar(if (s.totalFee == 0L) 1f else s.paid.toFloat() / s.totalFee, color = Brand.Green500, track = Brand.Line)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${rupees(minOf(s.paid, s.totalFee))} of ${rupees(s.totalFee)} paid", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            if (!s.current) Text("Left ${s.leftOn?.let { shortDate(it) } ?: ""}" + if (left > 0) " · ${rupees(left)} unpaid" else "", style = MaterialTheme.typography.labelMedium, color = if (left > 0) Brand.Red600 else Brand.Slate500)
            else if (left > 0 && s.nextDue != null) {
                if (s.nextDue < t) StatusChip("Overdue · ${shortDate(s.nextDue)}", Tone.Bad)
                else if (s.nextDue == t) StatusChip("${rupees(s.nextDueAmount)} today", Tone.Warn)
                else StatusChip("${rupees(s.nextDueAmount)} · ${shortDate(s.nextDue)}", Tone.Info)
            }
        }
    }
}

// ───────────────────────── form ─────────────────────────

private data class PlanRow(val amount: String, val due: Long)

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
            batch = s.batch; dob = s.dob; photo = s.photo; aadhaarPhoto = s.aadhaarPhoto
            count = existingPlan.size; first = existingPlan.first().dueOn
            rows.clear(); rows.addAll(existingPlan.map { PlanRow(it.amount.toString(), it.dueOn) })
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
                DigitsField("Mobile number", mobile, 10, { mobile = it }, Icons.Rounded.Phone, prefix = "+91 ", phone = true,
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
                Text("Change any amount or date. Amounts must add up to the total fee.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                rows.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Brand.Blue50), Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = Brand.Blue700)
                        }
                        Spacer(Modifier.width(8.dp))
                        DigitsField("Amount", r.amount, 7, { v -> rows[i] = r.copy(amount = v); customised = true }, prefix = "₹ ", modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        DatePill(r.due) { d -> rows[i] = r.copy(due = d); customised = true }
                        if (rows.size > 1) IconButton({ rows.removeAt(i); customised = true }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "Remove", tint = Brand.Slate400) }
                    }
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
                    batch = batch, dob = dob, photo = photo, aadhaarPhoto = aadhaarPhoto)
                val plan = rows.sortedBy { it.due }.mapIndexed { i, r -> Installment(studentId = s.id, number = i + 1, amount = r.amount.toLong(), dueOn = r.due) }
                vm.save(s, plan, onSaved)
            }, Modifier.weight(1.6f), enabled = valid, icon = Icons.Rounded.Check)
        }
    }
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
}

// ───────────────────────── detail ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDetail(vm: StudentsViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val s by remember(id) { vm.student(id) }.collectAsStateWithLifecycle(null)
    val plan by remember(id) { vm.installments(id) }.collectAsStateWithLifecycle(emptyList())
    val payments by remember(id) { vm.payments(id) }.collectAsStateWithLifecycle(emptyList())
    var payFor by remember { mutableStateOf<Long?>(null) } // suggested amount
    var remind by remember { mutableStateOf(false) }
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
    HideBottomNav()

    fun receipt(p: StudentPayment) {
        val upTo = payments.filter { it.paidOn < p.paidOn || (it.paidOn == p.paidOn && it.id <= p.id) }.sumOf { it.amount }
        shareReceipt(ctx, ReceiptData("${AppSettings.receiptPrefix}-S${p.id.toString().padStart(5, '0')}", p.paidOn, "FEE RECEIPT  /  फी पावती", st.name,
            listOf("Mobile" to "+91 ${st.mobile}", "Course" to (st.course + if (st.batch.isNotBlank()) " · ${st.batch}" else ""), "Payment mode" to p.mode.ifBlank { "—" }) +
                (if (p.note.isNotBlank()) listOf("Note" to p.note) else emptyList()),
            p.amount, listOf("Total fee" to rupees(st.totalFee), "Paid so far" to rupees(upTo), "Balance" to rupees((st.totalFee - upTo).coerceAtLeast(0)))))
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        ProfileHero(st.name, onBack,
            badge = { Box(Modifier.clickable(enabled = st.photo.isNotBlank()) { viewPhoto = st.photo }) { Avatar(st.name, 88.dp, ring = true, photo = st.photo) } },
            chips = { GlassChip(st.course, Icons.Rounded.School); if (st.batch.isNotBlank()) GlassChip(st.batch, Icons.Rounded.Schedule); GlassChip("Joined ${shortDate(st.joinedOn)}", Icons.Rounded.CalendarMonth) },
            topActions = { GlassIconButton(Icons.Rounded.Edit, "Edit", onEdit); GlassIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirmDelete = true }) },
            actions = {
                RoundAction(Icons.Rounded.Call, "Call", Brand.Blue600, { Contact.call(ctx, st.mobile) }, glass = true)
                RoundAction(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", Brand.WhatsApp, { Contact.whatsapp(ctx, st.mobile) }, glass = true)
                RoundAction(Icons.Rounded.Sms, "SMS", Brand.Blue600, { Contact.sms(ctx, st.mobile) }, glass = true)
                RoundAction(Icons.Rounded.NotificationsActive, "Remind", Brand.Saffron600, { if (next != null) remind = true }, glass = true)
            })
        Sheet {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(4.dp))
                if (st.leftOn != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand.Slate50).border(1.dp, Brand.Slate200, RoundedCornerShape(20.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.History, Brand.Slate600, 40.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Former student", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                        Text("${st.leftReason.ifBlank { "Left" }} · ${st.leftOn.asDate()}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    PillButton("Make current", { vm.update(st.copy(leftOn = null, leftReason = "")) }, Brand.Blue700)
                }
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(frac, 108.dp, 12.dp, Brand.Green500, Brand.Line) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${(frac * 100).toInt().coerceAtMost(100)}%", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
                                Text("paid", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            LegendLine(Brand.Blue600, "Total fee", rupees(st.totalFee))
                            LegendLine(Brand.Green500, "Paid", rupees(paid))
                            LegendLine(Brand.Saffron500, "Pending", rupees(balance))
                        }
                    }
                    if (balance > 0) PrimaryButton("Record a payment", { payFor = next?.remaining ?: balance }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Add)
                    else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Green50).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Verified, null, tint = Brand.Green600); Spacer(Modifier.width(8.dp))
                        Text("Full fee received. Thank you!", style = MaterialTheme.typography.labelLarge, color = Brand.Green700)
                    }
                }
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow(Icons.Rounded.Phone, "Mobile", "+91 ${st.mobile}")
                    if (st.dob != null) { HorizontalDivider(color = Brand.Line); InfoRow(Icons.Rounded.Cake, "Birthday", LocalDate.ofEpochDay(st.dob).format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy"))) }
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.CreditCard, "Aadhaar", if (st.aadhaar.isBlank()) "Not added" else if (showAadhaar) st.aadhaar.chunked(4).joinToString(" ") else maskAadhaar(st.aadhaar)) {
                        if (st.aadhaar.isNotBlank()) SmallIconButton(if (showAadhaar) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show", Brand.Slate500) { showAadhaar = !showAadhaar }
                    }
                    if (st.aadhaarPhoto.isNotBlank()) { DocPhoto("Aadhaar card photo", st.aadhaarPhoto, onEdit) { viewPhoto = st.aadhaarPhoto }; Spacer(Modifier.height(8.dp)) }
                }
                SectionHeader("Installments", action = "${statuses.count { it.fullyPaid }} of ${statuses.size} paid")
                Column { statuses.forEachIndexed { idx, s2 -> TimelineItem(s2, idx == 0, idx == statuses.lastIndex, t, onPay = { payFor = s2.remaining }, onRemind = { remind = true }) } }
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
                            SmallIconButton(Icons.Rounded.ReceiptLong, "Receipt", Brand.Blue600) { receipt(p) }
                            Spacer(Modifier.width(6.dp))
                            SmallIconButton(Icons.Rounded.DeleteOutline, "Delete payment", Brand.Slate400) { deletePay = p }
                        }
                    }
                }
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
                Spacer(Modifier.height(20.dp))
            }
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
                    SoftButton("SMS", { Contact.sms(ctx, st.mobile, text); remind = false }, Modifier.weight(1f), icon = Icons.Rounded.Sms)
                }
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
    deletePay?.let { p -> ConfirmDialog("Delete this payment?", "${rupees(p.amount)} received on ${p.paidOn.asDate()} will be removed.", "Delete", danger = true,
        onConfirm = { vm.deletePayment(p); deletePay = null }, onDismiss = { deletePay = null }) }
    if (confirmDelete) ConfirmDialog("Delete ${st.name}?", "This removes the student, their installments and payments. It cannot be undone.", "Delete", danger = true,
        onConfirm = { Photos.delete(ctx, st.photo); Photos.delete(ctx, st.aadhaarPhoto); vm.delete(st); confirmDelete = false; onBack() }, onDismiss = { confirmDelete = false })
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
}

@Composable
private fun TimelineItem(s: InstallmentStatus, first: Boolean, last: Boolean, t: Long, onPay: () -> Unit, onRemind: () -> Unit) {
    val i = s.inst
    val late = !s.fullyPaid && i.dueOn < t
    val color = when { s.fullyPaid -> Brand.Green500; late -> Brand.Red500; s.partlyPaid -> Brand.Amber600; else -> Brand.Blue500 }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(30.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(22.dp).background(if (first) Color.Transparent else Brand.Slate200))
            Box(Modifier.size(26.dp).clip(CircleShape).background(if (s.fullyPaid || late) color else Color.White).border(2.dp, color, CircleShape), Alignment.Center) {
                when {
                    s.fullyPaid -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    late -> Icon(Icons.Rounded.PriorityHigh, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    else -> Text("${i.number}", style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
            Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else Brand.Slate200))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Installment ${i.number} · due ${shortDate(i.dueOn)}", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                        Text(rupees(i.amount), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
                        val lateFee = if (late && AppSettings.lateFeeOn) (t - i.dueOn) * AppSettings.lateFeePerDay else 0L
                        Text(
                            when {
                                s.fullyPaid -> "Paid in full"
                                s.partlyPaid -> "${rupees(s.covered)} paid · ${rupees(s.remaining)} left" + if (late) " · overdue" else ""
                                late -> "Overdue since ${shortDate(i.dueOn)}" + if (lateFee > 0) " · late fee ${rupees(lateFee)}" else ""
                                else -> "Due ${whenText(i.dueOn)}"
                            },
                            style = MaterialTheme.typography.bodySmall, color = when { s.fullyPaid -> Brand.Green700; late -> Brand.Red600; s.partlyPaid -> Brand.Amber700; else -> Brand.Slate500 },
                        )
                        if (s.partlyPaid) { Spacer(Modifier.height(6.dp)); ProgressBar(s.covered.toFloat() / i.amount, color = Brand.Amber600, track = Brand.Line, height = 5.dp) }
                    }
                    if (!s.fullyPaid) {
                        SmallIconButton(Icons.AutoMirrored.Rounded.Chat, "Remind", Brand.WhatsAppDark, onRemind)
                        Spacer(Modifier.width(8.dp))
                        PillButton("Pay", onPay, if (late) Brand.Red600 else Brand.Blue700, Icons.Rounded.Add)
                    } else StatusChip("Paid", Tone.Good)
                }
            }
        }
    }
}
