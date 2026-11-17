package `in`.vidyadhan.academy.students

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Undo
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
import java.time.LocalDate

/** Details handed over from an enquiry that has joined. */
data class Prefill(val name: String, val mobile: String, val course: String)
object Handoff { var prefill by mutableStateOf<Prefill?>(null) }

private sealed interface Screen {
    data object List : Screen
    data class Form(val id: Long) : Screen
    data class Detail(val id: Long) : Screen
}

@Composable
fun StudentsTab(openNew: Boolean = false, onOpened: () -> Unit = {}, vm: StudentsViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    LaunchedEffect(openNew, Handoff.prefill) { if (openNew || Handoff.prefill != null) { screen = Screen.Form(0); onOpened() } }
    BackHandler(enabled = screen != Screen.List) { screen = if (screen is Screen.Form && (screen as Screen.Form).id != 0L) Screen.Detail((screen as Screen.Form).id) else Screen.List }
    AnimatedContent(screen, transitionSpec = {
        if (targetState == Screen.List) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "students") { s ->
        when (s) {
            Screen.List -> StudentList(vm, onAdd = { screen = Screen.Form(0) }, onOpen = { screen = Screen.Detail(it) })
            is Screen.Form -> StudentForm(vm, s.id) { screen = if (s.id == 0L) Screen.List else Screen.Detail(s.id) }
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
    val t = today()
    val total = all.sumOf { it.totalFee }
    val paid = all.sumOf { it.paid }
    val shown = all.filter { q.isBlank() || it.name.contains(q, true) || it.mobile.contains(q) }.filter {
        when (filter) { "due" -> it.totalFee > it.paid; "paid" -> it.totalFee <= it.paid; else -> true }
    }
    HeroPage("Students", "${all.size} enrolled", header = {
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
                Segmented(listOf("all" to "All", "due" to "Fees due", "paid" to "Fully paid"), filter, { filter = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp))
                if (shown.isEmpty()) EmptyState(Icons.Rounded.School,
                    if (all.isEmpty()) "No students yet" else "No matches",
                    if (all.isEmpty()) "Add your first MPSC student to start tracking fees." else "Try a different name or filter.",
                    action = if (all.isEmpty()) "Add student" else null, onAction = onAdd)
                else LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.id }) { s -> StudentCard(s, t) { onOpen(s.id) } }
                }
            }
            if (all.isNotEmpty()) Fab("Add student", Icons.Rounded.PersonAdd, onAdd, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
}

@Composable
private fun StudentCard(s: StudentWithFees, t: Long, onClick: () -> Unit) {
    val left = s.totalFee - s.paid
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallTag(s.course)
                    Spacer(Modifier.width(8.dp))
                    Text(s.mobile, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                }
            }
            if (left > 0) Column(horizontalAlignment = Alignment.End) {
                Text(rupees(left), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron700)
                Text("pending", style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
            } else StatusChip("Paid", Tone.Good)
        }
        Spacer(Modifier.height(14.dp))
        ProgressBar(if (s.totalFee == 0L) 1f else s.paid.toFloat() / s.totalFee, color = Brand.Green500, track = Brand.Line)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${rupees(s.paid)} of ${rupees(s.totalFee)} paid", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            if (left > 0 && s.nextDue != null) {
                if (s.nextDue < t) StatusChip("Overdue · ${shortDate(s.nextDue)}", Tone.Bad)
                else if (s.nextDue == t) StatusChip("Due today", Tone.Warn)
                else StatusChip("Next ${shortDate(s.nextDue)}", Tone.Info)
            }
        }
    }
}

// ───────────────────────── form ─────────────────────────

@Composable
private fun StudentForm(vm: StudentsViewModel, id: Long, onDone: () -> Unit) {
    val existing by remember(id) { vm.student(id) }.collectAsStateWithLifecycle(null)
    val existingPlan by remember(id) { vm.installments(id) }.collectAsStateWithLifecycle(emptyList())
    var loaded by remember { mutableStateOf(id == 0L) }
    val pre = remember { Handoff.prefill.also { Handoff.prefill = null } }
    var name by remember { mutableStateOf(pre?.name ?: "") }
    var mobile by remember { mutableStateOf(pre?.mobile ?: "") }
    var aadhaar by remember { mutableStateOf("") }
    var course by remember { mutableStateOf(pre?.course?.takeIf { it in AppSettings.courseList } ?: AppSettings.courseList.first()) }
    var fee by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(AppSettings.defaultInstallments) }
    var first by remember { mutableLongStateOf(today()) }
    var joined by remember { mutableLongStateOf(today()) }
    var plan by remember { mutableStateOf<List<Installment>>(emptyList()) }
    LaunchedEffect(existing, existingPlan) {
        val s = existing
        if (!loaded && s != null && existingPlan.isNotEmpty()) {
            name = s.name; mobile = s.mobile; aadhaar = s.aadhaar; course = s.course; fee = s.totalFee.toString(); joined = s.joinedOn
            count = existingPlan.size; first = existingPlan.first().dueOn; plan = existingPlan; loaded = true
        }
    }
    val feeN = fee.toLongOrNull() ?: 0L
    val preview = if (feeN > 0) buildPlan(feeN, count, LocalDate.ofEpochDay(first)) else emptyList()
    val valid = name.isNotBlank() && mobile.length == 10 && (aadhaar.isEmpty() || aadhaar.length == 12) && feeN > 0
    HideBottomNav()
    HeroPage(if (id == 0L) "New student" else "Edit student", if (pre != null) "From enquiry · check and add fees" else "Fill in the details below", onBack = onDone) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            FormCard("Student details", Icons.Rounded.Person) {
                Field(name, { name = it }, "Full name", Icons.Rounded.Person)
                DigitsField("Mobile number", mobile, 10, { mobile = it }, Icons.Rounded.Phone, prefix = "+91 ", phone = true,
                    supporting = if (mobile.isNotEmpty() && mobile.length < 10) "Enter all 10 digits" else null, isError = mobile.isNotEmpty() && mobile.length < 10)
                DigitsField("Aadhaar number", aadhaar, 12, { aadhaar = it }, Icons.Rounded.CreditCard,
                    supporting = if (aadhaar.isNotEmpty() && aadhaar.length < 12) "Enter all 12 digits" else "Optional · kept only on this phone", isError = aadhaar.isNotEmpty() && aadhaar.length < 12)
                DateField("Joining date", joined, { joined = it })
            }
            FormCard("Course", Icons.Rounded.School, Brand.Saffron600) {
                ChoicePills(AppSettings.courseList, course, { course = it })
            }
            FormCard("Fees", Icons.Rounded.CurrencyRupee, Brand.Green600) {
                DigitsField("Total course fee", fee, 7, { fee = it }, prefix = "₹ ")
                Stepper("Installments", count, 1..24) { count = it }
                DateField("First installment due", first, { first = it })
            }
            if (preview.isNotEmpty()) FormCard("Installment plan", Icons.Rounded.Timeline, Brand.Violet700) {
                preview.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Brand.Blue50), Alignment.Center) {
                            Text("${p.number}", style = MaterialTheme.typography.labelLarge, color = Brand.Blue700)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(p.dueOn.asDate(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate600)
                        Text(rupees(p.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
                    }
                }
                if (id != 0L && plan.any { it.paidOn != null }) Text("Changing the fee, number or dates resets the paid marks.", color = Brand.Red600, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            SoftButton("Cancel", onDone, Modifier.weight(1f), color = Brand.Slate600)
            PrimaryButton(if (id == 0L) "Add student" else "Save changes", {
                val s = Student(if (id == 0L) 0 else id, name.trim(), mobile, aadhaar, course, feeN, joined)
                val same = id != 0L && plan.size == preview.size && plan.zip(preview).all { (a, b) -> a.amount == b.amount && a.dueOn == b.dueOn }
                vm.save(s, if (same) plan else preview); onDone()
            }, Modifier.weight(1.6f), enabled = valid, icon = Icons.Rounded.Check)
        }
    }
}

// ───────────────────────── detail ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDetail(vm: StudentsViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val s by remember(id) { vm.student(id) }.collectAsStateWithLifecycle(null)
    val plan by remember(id) { vm.installments(id) }.collectAsStateWithLifecycle(emptyList())
    var payFor by remember { mutableStateOf<Installment?>(null) }
    var msgFor by remember { mutableStateOf<Installment?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAadhaar by remember { mutableStateOf(false) }
    val st = s ?: return
    val t = today()
    val paid = plan.filter { it.paidOn != null }.sumOf { it.amount }
    val nextUnpaid = plan.firstOrNull { it.paidOn == null }
    val frac = if (st.totalFee == 0L) 0f else paid.toFloat() / st.totalFee
    HideBottomNav()

    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        ProfileHero(st.name, onBack,
            chips = { GlassChip(st.course, Icons.Rounded.School); GlassChip("Joined ${shortDate(st.joinedOn)}", Icons.Rounded.CalendarMonth) },
            topActions = { GlassIconButton(Icons.Rounded.Edit, "Edit", onEdit); GlassIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirmDelete = true }) },
            actions = {
                RoundAction(Icons.Rounded.Call, "Call", Brand.Blue600, { Contact.call(ctx, st.mobile) }, glass = true)
                RoundAction(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", Brand.WhatsApp, { Contact.whatsapp(ctx, st.mobile) }, glass = true)
                RoundAction(Icons.Rounded.Sms, "SMS", Brand.Blue600, { Contact.sms(ctx, st.mobile) }, glass = true)
                RoundAction(Icons.Rounded.NotificationsActive, "Remind", Brand.Saffron600, { nextUnpaid?.let { msgFor = it } }, glass = true)
            })
        Sheet {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(4.dp))
                // fee summary
                Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(frac, 112.dp, 12.dp, Brand.Green500, Brand.Line) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
                            Text("paid", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        LegendLine(Brand.Blue600, "Total fee", rupees(st.totalFee))
                        LegendLine(Brand.Green500, "Paid", rupees(paid))
                        LegendLine(Brand.Saffron500, "Pending", rupees((st.totalFee - paid).coerceAtLeast(0)))
                    }
                }
                // info
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow(Icons.Rounded.Phone, "Mobile", "+91 ${st.mobile}")
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.CreditCard, "Aadhaar", if (st.aadhaar.isBlank()) "Not added" else if (showAadhaar) st.aadhaar.chunked(4).joinToString(" ") else maskAadhaar(st.aadhaar)) {
                        if (st.aadhaar.isNotBlank()) SmallIconButton(if (showAadhaar) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show", Brand.Slate500) { showAadhaar = !showAadhaar }
                    }
                }
                SectionHeader("Installments", action = "${plan.count { it.paidOn != null }} of ${plan.size} paid")
                Column {
                    plan.forEachIndexed { idx, i -> TimelineItem(i, idx == 0, idx == plan.lastIndex, t,
                        onPay = { payFor = i }, onRemind = { msgFor = i },
                        onReceipt = { shareReceipt(ctx, st, i, paid) }, onUndo = { vm.undoPaid(i) }) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    payFor?.let { i ->
        ModalBottomSheet({ payFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTitle("How was it paid?", "Installment ${i.number} · ${rupees(i.amount)}")
                OptionRow(Icons.Rounded.Payments, Brand.Green600, "Cash", "Received in hand") { vm.markPaid(i, "Cash"); payFor = null }
                OptionRow(Icons.Rounded.QrCode2, Brand.Violet700, "UPI", "Google Pay, PhonePe, Paytm…") { vm.markPaid(i, "UPI"); payFor = null }
                OptionRow(Icons.Rounded.AccountBalance, Brand.Blue600, "Bank transfer", "NEFT, IMPS or cheque") { vm.markPaid(i, "Bank"); payFor = null }
            }
        }
    }
    msgFor?.let { i ->
        var marathi by remember { mutableStateOf(true) }
        val text = feeMessage(marathi, st.name, i.amount, i.dueOn, if (marathi) "विद्याधन अकॅडमी" else AppSettings.academyName)
        ModalBottomSheet({ msgFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetTitle("Send a fee reminder", "To ${st.name} · +91 ${st.mobile}")
                Segmented(listOf("mr" to "मराठी", "en" to "English"), if (marathi) "mr" else "en", { marathi = it == "mr" })
                MessageBubble(text, marathi)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("WhatsApp", { Contact.whatsapp(ctx, st.mobile, text); msgFor = null }, Modifier.weight(1.4f), icon = Icons.AutoMirrored.Rounded.Chat,
                        custom = Brush.linearGradient(listOf(Brand.WhatsApp, Brand.WhatsAppDark)))
                    SoftButton("SMS", { Contact.sms(ctx, st.mobile, text); msgFor = null }, Modifier.weight(1f), icon = Icons.Rounded.Sms)
                }
                Text("The app opens with the message typed. You press send.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate400)
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${st.name}?", "This removes the student and every installment record. It cannot be undone.", "Delete", danger = true,
        onConfirm = { vm.delete(st); confirmDelete = false; onBack() }, onDismiss = { confirmDelete = false })
}

@Composable
private fun TimelineItem(i: Installment, first: Boolean, last: Boolean, t: Long, onPay: () -> Unit, onRemind: () -> Unit, onReceipt: () -> Unit, onUndo: () -> Unit) {
    val paid = i.paidOn != null
    val late = !paid && i.dueOn < t
    val color = when { paid -> Brand.Green500; late -> Brand.Red500; else -> Brand.Blue500 }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(30.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(22.dp).background(if (first) Color.Transparent else Brand.Slate200))
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (paid || late) color else Color.White).border(2.dp, color, CircleShape),
                Alignment.Center,
            ) {
                when {
                    paid -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                        Text("Installment ${i.number}", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                        Text(rupees(i.amount), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
                        val lateFee = if (late && AppSettings.lateFeeOn) (t - i.dueOn) * AppSettings.lateFeePerDay else 0L
                        Text(
                            when {
                                paid -> "Paid ${i.paidOn!!.asDate()} · ${i.mode}"
                                late -> "Overdue since ${shortDate(i.dueOn)}" + if (lateFee > 0) " · late fee ${rupees(lateFee)}" else ""
                                else -> "Due ${whenText(i.dueOn)}"
                            },
                            style = MaterialTheme.typography.bodySmall, color = when { paid -> Brand.Green700; late -> Brand.Red600; else -> Brand.Slate500 },
                        )
                    }
                    if (paid) {
                        SmallIconButton(Icons.Rounded.ReceiptLong, "Receipt", Brand.Blue600, onReceipt)
                        Spacer(Modifier.width(8.dp))
                        SmallIconButton(Icons.AutoMirrored.Rounded.Undo, "Undo", Brand.Slate500, onUndo)
                    } else {
                        SmallIconButton(Icons.AutoMirrored.Rounded.Chat, "Remind", Brand.WhatsAppDark, onRemind)
                        Spacer(Modifier.width(8.dp))
                        PillButton("Paid", onPay, if (late) Brand.Red600 else Brand.Blue700, Icons.Rounded.Check)
                    }
                }
            }
        }
    }
}
