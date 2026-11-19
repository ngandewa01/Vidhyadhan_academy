package `in`.vidyadhan.academy.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryPayment
import `in`.vidyadhan.academy.data.StudentPayment
import `in`.vidyadhan.academy.data.StudentWithFees
import `in`.vidyadhan.academy.library.ENDING_DAYS
import `in`.vidyadhan.academy.library.allSeats
import `in`.vidyadhan.academy.library.renewedTill
import `in`.vidyadhan.academy.library.typeForSeat
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Where a dashboard shortcut should take the owner. */
enum class Go {
    Students, NewStudent, Student, Enquiries, NewEnquiry, Enquiry, Library, NewMember, Member, Money, NewExpense, Settings, Backup, Search, Broadcast,
    Seats, MemberDues, MemberEnding,
}

fun isBirthday(dob: Long?, day: LocalDate): Boolean = dob != null && LocalDate.ofEpochDay(dob).let { it.month == day.month && it.dayOfMonth == day.dayOfMonth }

fun birthdayWish(name: String) = `in`.vidyadhan.academy.Templates.fill("birthday", true, mapOf("name" to name.substringBefore(' ')))

private data class Bday(val name: String, val mobile: String, val what: String, val photo: String)

/** One line of "Recent transactions": money in (payment) or out (expense, deposit returned). */
private data class Txn(val title: String, val subtitle: String, val amount: Long, val income: Boolean, val day: Long, val mode: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(onGo: (Go, Long) -> Unit) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val now = LocalDate.now()
    val t = today()
    val monthStart = now.withDayOfMonth(1).toEpochDay()
    val monthEnd = now.withDayOfMonth(now.lengthOfMonth()).toEpochDay()
    val month = now.format(DateTimeFormatter.ofPattern("MMMM"))
    val allStudents by remember { studentsWithFees(db.students()) }.collectAsStateWithLifecycle(emptyList())
    val students = allStudents.filter { it.current }
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val rooms by db.library().rooms().collectAsStateWithLifecycle(emptyList())
    val types by db.library().seatTypes().collectAsStateWithLifecycle(emptyList())
    val expenses by db.expenses().all().collectAsStateWithLifecycle(emptyList())
    val feesToday by remember(t) { db.students().paidBetween(t, t) }.collectAsStateWithLifecycle(0L)
    val libToday by remember(t) { db.library().collectedBetween(t, t) }.collectAsStateWithLifecycle(0L)
    val feesMonth by remember(monthStart) { db.students().paidBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val libMonth by remember(monthStart) { db.library().collectedBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val recentFees by db.students().recentPaid().collectAsStateWithLifecycle(emptyList())
    val recentLib by db.library().recentPayments().collectAsStateWithLifecycle(emptyList())
    var period by rememberSaveable { mutableStateOf("today") }
    var showCollect by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var showBirthdays by remember { mutableStateOf(false) }
    var wishFor by remember { mutableStateOf<Bday?>(null) }
    var arranging by remember { mutableStateOf(false) }

    val active = members.filter { it.active }
    val seats = allSeats(rooms, AppSettings.seatCount)
    val occupied = seats.count { n -> active.any { it.seatNo == n } }
    val openEnq = enquiries.filter { it.status == "NEW" || it.status == "CONTACTED" }
    val callsToday = openEnq.count { it.followUpOn <= t }

    // ── money ──
    val income = feesMonth + libMonth
    val spent = expenses.filter { it.spentOn in monthStart..monthEnd }.sumOf { it.amount }
    val studentPending = students.sumOf { (it.totalFee - it.paid).coerceAtLeast(0) }
    val libraryPending = active.sumOf { it.balance + if (it.validTill < t) it.monthlyFee else 0L }
    val pending = studentPending + libraryPending
    val billed = students.sumOf { it.totalFee }
    val paidAll = students.sumOf { it.paid.coerceAtMost(it.totalFee) }
    val billedPct = if (billed == 0L) 0 else (paidAll * 100 / billed).toInt()
    val depositsHeld = members.filter { it.depositPaid && it.depositReturnedOn == null }.sumOf { it.deposit }

    // ── reminders (behind the bell) ──
    val feesDue = students.count { it.nextDue != null && it.nextDue <= t }
    val libDue = active.count { it.validTill <= t || it.balance > 0 }
    val ending = active.count { it.validTill in t..(t + ENDING_DAYS) }
    val birthdays = students.filter { isBirthday(it.dob, now) }.map { Bday(it.name, it.mobile, "Student · ${it.course}${if (it.batch.isNotBlank()) " ${it.batch}" else ""}", it.photo) } +
        active.filter { isBirthday(it.dob, now) }.map { Bday(it.name, it.mobile, "Abhyasika · seat ${it.seatNo}", it.photo) }
    val backupDays = if (AppSettings.lastBackup == 0L) null else t - AppSettings.lastBackup
    val needsBackup = (students.isNotEmpty() || members.isNotEmpty()) && (backupDays == null || backupDays >= 7)
    val alerts = feesDue + libDue + callsToday + birthdays.size + if (needsBackup) 1 else 0

    val txns = (recentFees + recentLib).map {
        val label = when (it.kind) { "STUDENT_FEE" -> "Course fee"; "FEE" -> "Abhyasika fee"; "DEPOSIT" -> "Deposit"; "REGISTRATION" -> "Registration"; else -> "Deposit returned" }
        Txn(it.name, if (it.detail.isBlank()) label else "$label · ${it.detail}", it.amount, it.kind != "DEPOSIT_RETURN", it.paidOn, it.mode)
    } + expenses.take(5).map { Txn(it.category, it.note.ifBlank { "Expense" }, it.amount, false, it.spentOn, "") }
    val recent = txns.sortedByDescending { it.day }.take(4)

    // ── the cards below the quick actions (long-press to move or hide them) ──
    val titles = linkedMapOf("acad" to "Academy batches", "lib" to "Abhyasika", "txn" to "Recent transactions")
    val order = (AppSettings.homeOrder.filter { it in titles } + titles.keys).distinct()
    val hidden = AppSettings.homeHidden

    val acadCard: @Composable () -> Unit = {
        val batches = students.map { it.batch }.filter { it.isNotBlank() }.distinct().size
        SectionCard("Academy batches", AppSettings.courseList.joinToString(" · ").ifBlank { "Courses" }, Icons.Rounded.School, Brand.Blue700, "Students",
            onLink = { onGo(Go.Students, 0) }) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column3("${students.size}", "Enrolled", if (batches > 0) "$batches batch${if (batches == 1) "" else "es"}" else "${allStudents.size - students.size} former",
                    Brand.Ink, Brand.Green50, Brand.Green700, Modifier.weight(1f)) { onGo(Go.Students, 0) }
                VerticalDivider(Modifier.padding(vertical = 6.dp), color = Brand.Line)
                Column3(rupees(studentPending), "Installments", "Tap to collect", Brand.Saffron500, Brand.Saffron100, Brand.Saffron600, Modifier.weight(1f)) { showCollect = true }
                VerticalDivider(Modifier.padding(vertical = 6.dp), color = Brand.Line)
                Column3("${openEnq.size}", "Enquiries", "$callsToday due today", Brand.Blue700, Color(0xFFF0F9FF), Color(0xFF0369A1), Modifier.weight(1f)) { onGo(Go.Enquiries, 0) }
            }
        }
    }
    val libCard: @Composable () -> Unit = {
        val legroom = seats.count { typeForSeat(it, types)?.icon == "LEGROOM" }
        SectionCard("Abhyasika (Library)", "${seats.size - legroom} Normal · $legroom Extra leg room", Icons.Rounded.EventSeat, Brand.Green600, "Floor plan",
            onLink = { onGo(Go.Seats, 0) }) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(14.dp)).padding(vertical = 8.dp)) {
                Mini("${seats.size - occupied}", "Free seats", Brand.Green600, Modifier.weight(1f)) { onGo(Go.Seats, 0) }
                Mini("$occupied/${seats.size}", "Occupied", Brand.Ink, Modifier.weight(1f)) { onGo(Go.Library, 0) }
                Mini("${active.count { it.hasLocker }}/${AppSettings.lockerCount}", "Lockers", Brand.Violet700, Modifier.weight(1f)) { onGo(Go.Library, 0) }
                Mini("$ending", "Expiring ${ENDING_DAYS}d", Brand.Saffron500, Modifier.weight(1f)) { onGo(Go.MemberEnding, 0) }
            }
        }
    }
    val txnCard: @Composable () -> Unit = {
        SectionCard("Recent transactions", "$month ${now.year}", Icons.Rounded.AccountBalanceWallet, Brand.Slate800, "Money", onLink = { onGo(Go.Money, 0) }) {
            if (recent.isEmpty()) Text("No payments or expenses yet", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
            recent.forEachIndexed { i, x ->
                if (i > 0) HorizontalDivider(color = Brand.Line)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (x.income) Brand.Green50 else Brand.Red50), Alignment.Center) {
                        Text(x.title.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = if (x.income) Brand.Green700 else Brand.Red600)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(x.title, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${x.subtitle} · ${relativeDay(x.day)}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text((if (x.income) "+ " else "− ") + rupees(x.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (x.income) Brand.Green600 else Brand.Red600)
                        if (x.mode.isNotBlank()) Text(x.mode, style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState())) {
        // ── header with the financial pulse ──
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).heroBackground().statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoBadge(40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(AppSettings.academyName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Abhyasika Kendra · ${now.format(DateTimeFormatter.ofPattern("EEE, d MMM"))}", style = MaterialTheme.typography.labelMedium, color = Brand.Blue100.copy(alpha = 0.85f), maxLines = 1)
                }
                GlassIconButton(Icons.Rounded.Search, "Search", { onGo(Go.Search, 0) })
                Spacer(Modifier.width(8.dp))
                Box {
                    GlassIconButton(Icons.Rounded.Notifications, "Reminders", { showReminders = true })
                    if (alerts > 0) Text(if (alerts > 99) "99+" else "$alerts", Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).clip(CircleShape)
                        .background(Brand.Saffron500).padding(horizontal = 5.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(2.dp)) {
                        PeriodChip("Today", period == "today") { period = "today" }
                        PeriodChip(month, period == "month") { period = "month" }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Net profit: ${rupees(income - spent)}", Modifier.clip(RoundedCornerShape(50)).background(Brand.Green500.copy(alpha = 0.20f))
                        .border(1.dp, Brand.Green500.copy(alpha = 0.3f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF86EFAC), maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.clickable { onGo(Go.Money, 0) }, verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(if (period == "today") "COLLECTED TODAY" else "$month TOTAL COLLECTED".uppercase(), style = MaterialTheme.typography.labelSmall, color = Brand.Blue100.copy(alpha = 0.8f))
                        Text(rupees(if (period == "today") feesToday + libToday else income), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Pending: ${rupees(pending)}", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFCD34D), maxLines = 1)
                        Text("Deposits held: ${rupees(depositsHeld)}", style = MaterialTheme.typography.labelSmall, color = Brand.Blue100.copy(alpha = 0.85f), maxLines = 1)
                    }
                }
                HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 8.dp), color = Color.White.copy(alpha = 0.10f))
                Row {
                    Text("$billedPct% of ${rupees(billed)} billed", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Brand.Blue50.copy(alpha = 0.9f), maxLines = 1)
                    Text("${rupees(pending)} upcoming/due", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFDE68A), maxLines = 1)
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(1.dp)) {
                    if (billedPct > 0) Box(Modifier.weight(billedPct.toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color(0xFF34D399)))
                    if (billedPct < 100) Box(Modifier.weight((100 - billedPct).toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brand.Saffron500.copy(alpha = 0.6f)))
                }
            }
        }

        Column(Modifier.offset(y = (-14).dp).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (needsBackup) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Amber50).border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
                .clickable { onGo(Go.Backup, 0) }.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Brand.Amber700); Spacer(Modifier.width(10.dp))
                Text(if (backupDays == null) "No backup yet" else "Last backup $backupDays days ago", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Amber700)
                PillButton("Back up", { onGo(Go.Backup, 0) }, Brand.Amber700)
            }

            // birthday strip (× hides it for today)
            if (birthdays.isNotEmpty() && AppSettings.birthdayHiddenOn != t) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Brand.Saffron50, Brand.Amber50))).border(1.dp, Brand.Saffron500.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Saffron500), Alignment.Center) { Icon(Icons.Rounded.Cake, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (birthdays.size == 1) "TODAY'S BIRTHDAY" else "${birthdays.size} BIRTHDAYS TODAY", style = MaterialTheme.typography.labelSmall, color = Brand.Saffron600)
                    Text(if (birthdays.size == 1) "${birthdays[0].name} · ${birthdays[0].what.substringAfter("· ")}"
                        else birthdays.take(2).joinToString(", ") { it.name.substringBefore(' ') } + if (birthdays.size > 2) " +${birthdays.size - 2}" else "",
                        style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Marathi wish ready to send", style = MaterialTheme.typography.labelSmall, color = Brand.Slate500, maxLines = 1)
                }
                PillButton(if (birthdays.size == 1) "Wish" else "See all", {
                    if (birthdays.size == 1) wishFor = birthdays[0] else showBirthdays = true
                }, Brand.Saffron500, Icons.AutoMirrored.Rounded.Send)
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { AppSettings.birthdayHiddenOn = t }, Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Hide birthdays for today", tint = Brand.Slate400, modifier = Modifier.size(18.dp))
                }
            }

            // four quick actions (instead of a floating + button)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quick("+ Fee", Icons.Rounded.CurrencyRupee, Brand.Green600, Brand.Green50, Modifier.weight(1f)) { showCollect = true }
                Quick("+ Admission", Icons.Rounded.PersonAdd, Brand.Blue700, Brand.Blue100, Modifier.weight(1f)) { onGo(Go.NewStudent, 0) }
                Quick("+ Enquiry", Icons.Rounded.AddComment, Brand.Saffron500, Brand.Saffron100, Modifier.weight(1f)) { onGo(Go.NewEnquiry, 0) }
                Quick("+ Expense", Icons.Rounded.ReceiptLong, Brand.Red600, Brand.Red50, Modifier.weight(1f)) { onGo(Go.NewExpense, 0) }
            }

            Column(Modifier.longPress { arranging = true }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (arranging) Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(18.dp), 4.dp).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Arrange your dashboard", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                        Text("Move cards with ↑ ↓. The eye hides a card.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    PillButton("Done", { arranging = false }, Brand.Blue700, Icons.Rounded.Check)
                }
                order.filter { it !in hidden }.forEach { key ->
                    Arrangeable(arranging, titles.getValue(key),
                        onMove = { d ->
                            val o = order.toMutableList(); val i = o.indexOf(key)
                            var j = i + d
                            while (j in o.indices && o[j] in hidden) j += d
                            if (j in o.indices) { o.removeAt(i); o.add(j, key); AppSettings.homeOrder = o }
                        },
                        onHide = { AppSettings.homeHidden = hidden + key }) {
                        when (key) { "acad" -> acadCard(); "lib" -> libCard(); else -> txnCard() }
                    }
                }
                if (arranging && hidden.isNotEmpty()) Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(18.dp), 4.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hidden cards", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                    order.filter { it in hidden }.forEach { key ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(titles.getValue(key), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate600)
                            PillButton("Show", { AppSettings.homeHidden = hidden - key }, Brand.Blue700, Icons.Rounded.Visibility)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    wishFor?.let { WishSheet(it) { wishFor = null } }
    if (showCollect) QuickCollectSheet(students.filter { it.nextDue != null && it.totalFee > it.paid }.sortedBy { it.nextDue },
        active.filter { it.validTill <= t + ENDING_DAYS || it.balance > 0 }.sortedBy { it.validTill }, t) { showCollect = false }

    if (showReminders) ModalBottomSheet({ showReminders = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetTitle("Today's alerts", "Everything that needs you today")
            if (feesDue > 0) OptionRow(Icons.Rounded.CurrencyRupee, Brand.Red600, "$feesDue student fee${if (feesDue == 1) "" else "s"} due", "Tap to collect") { showReminders = false; showCollect = true }
            if (callsToday > 0) OptionRow(Icons.Rounded.Call, Brand.Saffron500, "$callsToday enquir${if (callsToday == 1) "y" else "ies"} to call") { showReminders = false; onGo(Go.Enquiries, 0) }
            if (libDue > 0) OptionRow(Icons.Rounded.EventSeat, Brand.Teal700, "$libDue Abhyasika due${if (libDue == 1) "" else "s"}") { showReminders = false; onGo(Go.MemberDues, 0) }
            if (birthdays.isNotEmpty()) OptionRow(Icons.Rounded.Cake, Brand.Saffron500, "${birthdays.size} birthday${if (birthdays.size == 1) "" else "s"} today") { showReminders = false; showBirthdays = true }
            if (needsBackup) OptionRow(Icons.Rounded.WarningAmber, Brand.Amber700, if (backupDays == null) "No backup yet" else "Backup is $backupDays days old", "Back up to Google Drive") {
                showReminders = false; onGo(Go.Backup, 0)
            }
            if (alerts == 0) Text("Nothing needs you today ✓", style = MaterialTheme.typography.bodyLarge, color = Brand.Slate500)
        }
    }
    if (showBirthdays) ModalBottomSheet({ showBirthdays = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        val wished = remember { mutableStateListOf<String>() }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTitle("${birthdays.size} birthday${if (birthdays.size == 1) "" else "s"} today 🎂", "Tap Wish: WhatsApp opens with the message. Come back and wish the next one.")
            birthdays.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(b.name, 42.dp, photo = b.photo); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                        Text(b.what, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    PillButton(if (b.mobile in wished) "✓ Wished" else "Wish", { wished += b.mobile; showBirthdays = false; wishFor = b },
                        if (b.mobile in wished) Brand.Slate400 else Brand.Saffron500)
                }
            }
        }
    }
}

/** Birthday wish: a Marathi + English message the owner can change, sent by Telegram or WhatsApp. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishSheet(b: Bday, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val first = b.name.substringBefore(' ')
    var text by remember(b) {
        mutableStateOf(birthdayWish(b.name) + "\n\n" + `in`.vidyadhan.academy.Templates.fill("birthday", false, mapOf("name" to first)))
    }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Saffron500), Alignment.Center) { Icon(Icons.Rounded.Cake, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Send birthday wishes", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink)
                    Text("${b.name} · ${b.what}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("Marathi + English greeting (you can change it)", style = MaterialTheme.typography.labelLarge, color = Brand.Slate600)
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 5, shape = RoundedCornerShape(16.dp), textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Brand.Saffron50, focusedContainerColor = Color.White, focusedBorderColor = Brand.Saffron500, unfocusedBorderColor = Brand.Line))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0284C7)).clickable { Contact.telegram(ctx, text); onClose() }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text("Send via Telegram", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Brand.Green600).clickable { Contact.whatsapp(ctx, b.mobile, text); onClose() }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text("Send via WhatsApp", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

/**
 * "+ Fee": everyone who owes money, each with a Collect button.
 * Students pay their next instalment; Abhyasika members pay their balance, or renew for one month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCollectSheet(students: List<StudentWithFees>, members: List<LibraryMember>, t: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("Cash") }
    val done = remember { mutableStateMapOf<String, Long>() }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTitle("Quick collect", "Tap Collect when the money is in your hand")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Bank").forEach { m ->
                    Text(m, Modifier.clip(RoundedCornerShape(50)).background(if (mode == m) Brand.Blue700 else Brand.Slate50)
                        .border(1.dp, if (mode == m) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable { mode = m }.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = if (mode == m) Color.White else Brand.Slate600)
                }
            }
            if (students.isEmpty() && members.isEmpty()) Text("Nobody owes anything right now ✓", style = MaterialTheme.typography.bodyLarge, color = Brand.Slate500)
            if (students.isNotEmpty()) Text("STUDENTS", style = MaterialTheme.typography.labelMedium, color = Brand.Slate500)
            students.forEach { s ->
                val key = "s${s.id}"
                val due = s.nextDue ?: t
                CollectRow(s.name, "Instalment ${if (due < t) "overdue since" else "due"} ${shortDate(due)}", s.nextDueAmount, due < t, done[key]) {
                    scope.launch {
                        db.students().insertPayment(StudentPayment(studentId = s.id, amount = s.nextDueAmount, paidOn = t, mode = mode))
                        done[key] = s.nextDueAmount
                    }
                }
            }
            if (members.isNotEmpty()) Text("ABHYASIKA", style = MaterialTheme.typography.labelMedium, color = Brand.Slate500)
            members.forEach { m ->
                val key = "m${m.id}"
                val renew = m.validTill <= t + ENDING_DAYS
                val amount = (if (renew) m.monthlyFee else 0L) + m.balance
                CollectRow("${m.name} · Seat ${m.seatNo}",
                    if (renew) "Renew 1 month" + (if (m.balance > 0) " + ${rupees(m.balance)} balance" else "") + " · ends ${shortDate(m.validTill)}" else "Balance to pay",
                    amount, m.validTill < t, done[key]) {
                    scope.launch {
                        val updated = if (renew) m.copy(validTill = renewedTill(m.validTill, t, 1), planName = "1 month", balance = 0) else m.copy(balance = 0)
                        db.library().pay(updated, LibraryPayment(memberId = m.id, kind = "FEE", amount = amount, paidOn = t, mode = mode))
                        done[key] = amount
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectRow(name: String, detail: String, amount: Long, late: Boolean, collected: Long?, onCollect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (late) Brand.Red600 else Brand.Slate500, maxLines = 2)
            Text(rupees(amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron600)
        }
        Spacer(Modifier.width(8.dp))
        if (collected != null) StatusChip("Collected ✓", Tone.Good)
        else PillButton("Collect", onCollect, Brand.Green600)
    }
}

@Composable
private fun PeriodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Color.White else Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = if (selected) Brand.Blue700 else Brand.Blue100.copy(alpha = 0.8f))
}

@Composable
private fun Quick(label: String, icon: ImageVector, color: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).pressCard(RoundedCornerShape(18.dp), onClick)
        .padding(vertical = 10.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(bg), Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Slate800, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** White card with an icon, title, small subtitle and a "link ›" on the right. */
@Composable
private fun SectionCard(title: String, subtitle: String, icon: ImageVector, color: Color, link: String, onLink: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(color), Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLink).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(link, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Brand.Blue700)
                Icon(Icons.Rounded.ChevronRight, null, tint = Brand.Blue700, modifier = Modifier.size(18.dp))
            }
        }
        content()
    }
}

/** A big number with its label and a small coloured chip under it. */
@Composable
private fun Column3(value: String, label: String, chip: String, color: Color, chipBg: Color, chipFg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Slate600, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(chip, Modifier.clip(RoundedCornerShape(6.dp)).background(chipBg).padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall,
            color = chipFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Mini(value: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Long press anywhere on the cards → [onLong]. Listens before the cards do, and swallows the rest of that touch,
 * so the card under the finger is not opened as well. A scroll (finger moves) is not a long press.
 */
private fun Modifier.longPress(onLong: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
            true
        }
        if (ended == null) {
            onLong()
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
            } while (ev.changes.any { it.pressed })
        }
    }
}

/** A dashboard card; in arrange mode it gets a bar with ↑ ↓ and hide, and its own buttons are switched off. */
@Composable
private fun Arrangeable(arranging: Boolean, title: String, onMove: (Int) -> Unit, onHide: () -> Unit, content: @Composable () -> Unit) {
    if (!arranging) { content(); return }
    Column(Modifier.fillMaxWidth().border(2.dp, Brand.Blue500, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().background(Brand.Blue700).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DragIndicator, null, tint = Brand.Blue200, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Color.White)
            IconButton({ onMove(-1) }) { Icon(Icons.Rounded.ArrowUpward, "Move $title up", tint = Color.White) }
            IconButton({ onMove(1) }) { Icon(Icons.Rounded.ArrowDownward, "Move $title down", tint = Color.White) }
            IconButton(onHide) { Icon(Icons.Rounded.VisibilityOff, "Hide $title", tint = Color.White) }
        }
        Box(Modifier.padding(6.dp)) {
            content()
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.25f)).clickable(remember { MutableInteractionSource() }, null) {})
        }
    }
}
