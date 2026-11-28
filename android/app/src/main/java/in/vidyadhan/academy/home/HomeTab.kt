package `in`.vidyadhan.academy.home

import android.content.Context
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import `in`.vidyadhan.academy.library.libraryReminder
import `in`.vidyadhan.academy.library.typeForSeat
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Where a dashboard shortcut should take the owner. */
enum class Go {
    Students, NewStudent, Student, Enquiries, NewEnquiry, Enquiry, Library, NewMember, Member, Money, NewExpense, Settings, Backup, Search, Broadcast,
    Seats, MemberDues, MemberEnding, Khata,
}

fun isBirthday(dob: Long?, day: LocalDate): Boolean = dob != null && LocalDate.ofEpochDay(dob).let { it.month == day.month && it.dayOfMonth == day.dayOfMonth }

fun birthdayWish(name: String) = `in`.vidyadhan.academy.Templates.fill("birthday", true, mapOf("name" to name.substringBefore(' ')))

private data class Bday(val name: String, val mobile: String, val what: String, val photo: String, val key: String = "")

/** One line of "Recent transactions": money in (payment) or out (expense, deposit returned). */
private data class Txn(val title: String, val subtitle: String, val amount: Long, val income: Boolean, val day: Long, val mode: String, val go: Go? = null, val id: Long = 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(onGo: (Go, Long) -> Unit) {
    var newsOpen by rememberSaveable { mutableStateOf<String?>(null) }
    newsOpen?.let { `in`.vidyadhan.academy.ai.NewsPage(it) { newsOpen = null }; return }
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
    val khataEntries by db.khata().all().collectAsStateWithLifecycle(emptyList())
    val khataPending = remember(khataEntries) { `in`.vidyadhan.academy.khata.Khata.people(khataEntries).filter { it.pending } }
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
    var showRecord by remember { mutableStateOf(false) }
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
    val khataMonth = khataEntries.filter { it.kind == "PAYMENT" && it.date in monthStart..monthEnd }.sumOf { it.credit }
    val income = feesMonth + libMonth + khataMonth
    val spent = expenses.filter { it.spentOn in monthStart..monthEnd }.sumOf { it.amount }
    val studentPending = students.sumOf { (it.totalFee - it.paid).coerceAtLeast(0) }
    val libraryPending = active.sumOf { it.balance + if (it.validTill < t) it.monthlyFee else 0L }
    val pending = studentPending + libraryPending
    val khataToday = khataEntries.filter { it.kind == "PAYMENT" && it.date == t }.sumOf { it.credit }
    val collectedToday = feesToday + libToday + khataToday
    val spentToday = expenses.filter { it.spentOn == t }.sumOf { it.amount }
    // course fees due up to today (not later installments), and who owes them
    val studentDueNow = students.sumOf { it.dueNow }
    val dueNowCount = students.count { it.dueNow > 0 }
    val libDueMembers = active.count { it.validTill < t || it.balance > 0 }
    val later = (studentPending - studentDueNow).coerceAtLeast(0)
    val depositsHeld = members.filter { it.depositPaid && it.depositReturnedOn == null }.sumOf { it.deposit }

    // ── reminders (behind the bell) ──
    val feesDue = students.count { it.nextDue != null && it.nextDue <= t }
    val libDue = active.count { it.validTill <= t || it.balance > 0 }
    val ending = active.count { it.validTill in t..(t + ENDING_DAYS) }
    val tgOf = allStudents.associate { it.id to it.telegram }
    val birthdays = students.filter { isBirthday(it.dob, now) }.map { Bday(it.name, it.mobile, "Student · ${it.course}${if (it.batch.isNotBlank()) " ${it.batch}" else ""}", it.photo, "s${it.id}") } +
        active.filter { isBirthday(it.dob, now) && (it.studentId == null || students.none { s -> s.id == it.studentId && isBirthday(s.dob, now) }) }
            .map { Bday(it.name, it.mobile, "Abhyasika · seat ${it.seatNo}", it.photo, it.studentId?.let { id -> "s$id" } ?: "m${it.id}") }
    val alerts = feesDue + libDue + callsToday + birthdays.size

    val txns = (recentFees + recentLib).map {
        val label = when (it.kind) { "STUDENT_FEE" -> "Course fee"; "FEE" -> "Abhyasika fee"; "DEPOSIT" -> "Deposit"; "REGISTRATION" -> "Registration"; else -> "Deposit returned" }
        Txn(it.name, if (it.detail.isBlank()) label else "$label · ${it.detail}", it.amount, it.kind != "DEPOSIT_RETURN", it.paidOn, it.mode,
            if (it.kind == "STUDENT_FEE") Go.Student else Go.Member, it.ownerId)
    } + expenses.take(5).map { Txn(it.category, it.note.ifBlank { "Expense" }, it.amount, false, it.spentOn, "", Go.Money) }
    val recent = txns.sortedByDescending { it.day }.take(4)

    // ── the cards below the quick actions (long-press to move or hide them) ──
    val titles = linkedMapOf("acad" to "Academy batches", "lib" to "Abhyasika", "txn" to "Recent transactions")
    val order = (AppSettings.homeOrder.filter { it in titles } + titles.keys).distinct()
    val hidden = AppSettings.homeHidden

    val acadCard: @Composable () -> Unit = {
        val batches = students.map { it.batch }.filter { it.isNotBlank() }.distinct().size
        SectionCard("Academy batches", "", Icons.Rounded.School, Brand.Blue700, "Students",
            onLink = { onGo(Go.Students, 0) }) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column3("${students.size}", "Enrolled", if (batches > 0) "$batches batch${if (batches == 1) "" else "es"}" else "${allStudents.size - students.size} former",
                    Brand.Ink, Brand.Green50, Brand.Green700, Modifier.weight(1f)) { onGo(Go.Students, 0) }
                VerticalDivider(Modifier.padding(vertical = 6.dp), color = Brand.Line)
                Column3(rupees(studentDueNow), "Fees due", "$dueNowCount student${if (dueNowCount == 1) "" else "s"}", Brand.Saffron500, Brand.Saffron100, Brand.Saffron600, Modifier.weight(1f)) {
                    `in`.vidyadhan.academy.students.Handoff.studentFilter = "Fees due"; onGo(Go.Students, 0)
                }
                VerticalDivider(Modifier.padding(vertical = 6.dp), color = Brand.Line)
                Column3("${openEnq.size}", "Enquiries", "$callsToday due today", Brand.Blue700, Color(0xFFF0F9FF), Color(0xFF0369A1), Modifier.weight(1f)) { onGo(Go.Enquiries, 0) }
            }
        }
    }
    val libCard: @Composable () -> Unit = {
        val legroom = seats.count { typeForSeat(it, types)?.icon == "LEGROOM" }
        SectionCard("Abhyasika (Library)", "", Icons.Rounded.EventSeat, Brand.Green600, "Floor plan",
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
                Row(Modifier.fillMaxWidth().clickable(enabled = x.go != null) { x.go?.let { onGo(it, x.id) } }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (x.income) Brand.Green50 else Brand.Red50), Alignment.Center) {
                        Icon(if (x.income) Icons.Rounded.Payments else Icons.Rounded.ReceiptLong, null,
                            tint = if (x.income) Brand.Green700 else Brand.Red600, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(x.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${x.subtitle} · ${relativeDay(x.day)}", style = MaterialTheme.typography.labelMedium, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text((if (x.income) "+ " else "− ") + rupees(x.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (x.income) Brand.Green600 else Brand.Red600, maxLines = 1, softWrap = false)
                        if (x.mode.isNotBlank()) Text(x.mode, Modifier.clip(RoundedCornerShape(50)).background(Brand.Slate50).padding(horizontal = 7.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = Brand.Slate600)
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
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(2.dp)) {
                    PeriodChip("Today", period == "today") { period = "today" }
                    PeriodChip(month, period == "month") { period = "month" }
                }
                // everything in this card follows the switch: collected, spent and profit for today or for the month
                val isToday = period == "today"
                val per = if (isToday) "TODAY" else "IN ${month.uppercase()}"
                val inAmt = if (isToday) collectedToday else income
                val outAmt = if (isToday) spentToday else spent
                val profit = inAmt - outAmt
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().clickable { onGo(Go.Money, 0) }) {
                    Text("COLLECTED $per", style = MaterialTheme.typography.labelSmall, color = Brand.Blue100.copy(alpha = 0.8f))
                    Text(rupees(inAmt), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroBox("SPENT $per", rupees(outAmt), Color.White, Modifier.weight(1f))
                    HeroBox(if (profit >= 0) "PROFIT $per" else "LOSS $per", rupees(kotlin.math.abs(profit)),
                        if (profit >= 0) Color(0xFF86EFAC) else Color(0xFFFCA5A5), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                // academy and Abhyasika are kept apart: they are different students and are never added into one figure
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFDE68A).copy(alpha = 0.14f))
                    .border(1.dp, Color(0xFFFDE68A).copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                    if (pending == 0L) Text("Nothing to collect ✓", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFDE68A))
                    else {
                        Text("STILL TO COLLECT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.75f))
                        if (studentPending > 0) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("Academy", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Text("${rupees(studentDueNow)} due now", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            }
                            Text(rupees(studentPending), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFFFDE68A), maxLines = 1, softWrap = false)
                        }
                        if (studentPending > 0 && libraryPending > 0) HorizontalDivider(Modifier.padding(top = 10.dp), color = Color.White.copy(alpha = 0.25f))
                        if (libraryPending > 0) Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("Abhyasika", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Text("$libDueMembers due", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            }
                            Text(rupees(libraryPending), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFFFDE68A), maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (depositsHeld > 0) Text("Deposits held ${rupees(depositsHeld)} (to be returned)", Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall, color = Brand.Blue100.copy(alpha = 0.85f))
            }
        }

        Column(Modifier.offset(y = (-14).dp).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (khataPending.isNotEmpty()) ListCard(onClick = { onGo(Go.Khata, 0) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.MenuBook, Brand.Saffron600, 44.dp, filled = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Old khata", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                        Text("${khataPending.size} people to confirm for Students",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Brand.Slate300)
                }
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
                Quick("+ Fee", Icons.Rounded.CurrencyRupee, Brand.Green600, Brand.Green50, Modifier.weight(1f)) { showRecord = true }
                Quick("+ Admission", Icons.Rounded.PersonAdd, Brand.Blue700, Brand.Blue100, Modifier.weight(1f)) { onGo(Go.NewStudent, 0) }
                Quick("+ Enquiry", Icons.Rounded.AddComment, Brand.Saffron500, Brand.Saffron100, Modifier.weight(1f)) { onGo(Go.NewEnquiry, 0) }
                Quick("+ Expense", Icons.Rounded.ReceiptLong, Brand.Red600, Brand.Red50, Modifier.weight(1f)) { onGo(Go.NewExpense, 0) }
            }

            `in`.vidyadhan.academy.ai.NewsCard { newsOpen = it }

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
    if (showRecord) RecordAnySheet(students, active, t, onMember = { onGo(Go.Member, it) }) { showRecord = false }
    if (showCollect) QuickCollectSheet(students.filter { it.nextDue != null && it.totalFee > it.paid }.sortedBy { it.nextDue },
        active.filter { it.validTill <= t + ENDING_DAYS || it.balance > 0 }.sortedBy { it.validTill }, t, onStudent = { onGo(Go.Student, it) }, onMember = { onGo(Go.Member, it) }) { showCollect = false }

    if (showReminders) ModalBottomSheet({ showReminders = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetTitle("Today's alerts & Telegram feed", if (`in`.vidyadhan.academy.telegram.TelegramBot.connected)
                "Due reminders and birthday wishes go out by the bot at ${"%02d:%02d".format(AppSettings.reminderHour, AppSettings.reminderMinute)}" else "Everything that needs you today")
            if (birthdays.isNotEmpty()) OptionRow(Icons.Rounded.Cake, Brand.Saffron500, "${birthdays.size} birthday${if (birthdays.size == 1) "" else "s"} today") { showReminders = false; showBirthdays = true }
            if (alerts == 0) Text("Nothing needs you today ✓", style = MaterialTheme.typography.bodyLarge, color = Brand.Slate500)
            // three tabs: enquiries, academy fees, Abhyasika fees
            val dueStudents = students.filter { it.nextDue != null && it.nextDue <= t }.sortedBy { it.nextDue }
            val dueMembers = active.filter { it.validTill <= t || it.balance > 0 }.sortedBy { it.validTill }
            val enqList = openEnq.sortedBy { it.followUpOn }
            var tab by rememberSaveable { mutableStateOf("enq") }
            var showAllAcad by remember { mutableStateOf(false) }
            var showAllLib by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("enq" to "Enquiries ${enqList.size}", "acad" to "Academy ${dueStudents.size}", "lib" to "Abhyasika ${dueMembers.size}").forEach { (k, l) ->
                    val on = tab == k
                    Text(l, Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) Brand.Blue700 else Color.White)
                        .border(1.dp, if (on) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable { tab = k }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Brand.Slate600, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
            when (tab) {
                "enq" -> {
                    if (enqList.isEmpty()) Text("No enquiries to follow up ✓", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500, modifier = Modifier.padding(top = 6.dp))
                    enqList.take(8).forEach { e ->
                        ReminderPerson(e.name, (if (e.followUpOn <= t) "Follow up today" else "Follow up ${relativeDay(e.followUpOn)}") + if (e.source.isNotBlank()) " · ${e.source}" else "",
                            e.followUpOn <= t, onOpen = { showReminders = false; onGo(Go.Enquiries, 0) },
                            onRemind = { Contact.whatsapp(ctx, e.mobile) })
                    }
                }
                "acad" -> {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Fees due now", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Brand.Slate500)
                        Text("Quick collect ›", Modifier.clickable { showReminders = false; showCollect = true }.padding(4.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Blue700)
                    }
                    if (dueStudents.isEmpty()) Text("No academy fees due ✓", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    (if (showAllAcad) dueStudents else dueStudents.take(5)).forEach { s ->
                        val due = s.nextDue ?: t
                        val tgKey = if (`in`.vidyadhan.academy.telegram.TelegramBot.isLinked("s${s.id}")) "s${s.id}" else null
                        ReminderPerson(s.name, "${if (due < t) "Overdue" else "Due"} ${shortDate(due)} · ${rupees(s.nextDueAmount)}", due < t,
                            onOpen = { showReminders = false; onGo(Go.Student, s.id) },
                            onRemind = { Contact.whatsapp(ctx, s.mobile, feeMessage(true, s.name, s.nextDueAmount, due, AppSettings.academyName)) },
                            telegramKey = tgKey,
                            onTelegram = { tgSend(ctx, "s${s.id}", feeMessage(true, s.name, s.nextDueAmount, due, AppSettings.academyName)) })
                    }
                    if (dueStudents.size > 5) TextButton(onClick = { showAllAcad = !showAllAcad }) { Text(if (showAllAcad) "Show less" else "Show all ${dueStudents.size}") }
                }
                else -> {
                    if (dueMembers.isEmpty()) Text("No Abhyasika fees due ✓", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    (if (showAllLib) dueMembers else dueMembers.take(5)).forEach { m ->
                        val tgKey = if (`in`.vidyadhan.academy.telegram.TelegramBot.isLinked("m${m.id}")) "m${m.id}" else null
                        ReminderPerson("${m.name} · Seat ${m.seatNo}", if (m.validTill < t) "Renewal overdue since ${shortDate(m.validTill)}" else if (m.validTill <= t) "Renewal due today" else "Balance ${rupees(m.balance)}", m.validTill < t,
                            onOpen = { showReminders = false; onGo(Go.Member, m.id) },
                            onRemind = { Contact.whatsapp(ctx, m.mobile, libraryReminder(m, t)) },
                            telegramKey = tgKey,
                            onTelegram = { tgSend(ctx, "m${m.id}", libraryReminder(m, t)) })
                    }
                    if (dueMembers.size > 5) TextButton(onClick = { showAllLib = !showAllLib }) { Text(if (showAllLib) "Show less" else "Show all ${dueMembers.size}") }
                }
            }
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
                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0284C7)).clickable { `in`.vidyadhan.academy.telegram.TelegramBot.sendOrExplain(ctx, b.key, b.mobile, `in`.vidyadhan.academy.telegram.TelegramBot.esc(text)); onClose() }.padding(vertical = 12.dp),
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
private fun QuickCollectSheet(students: List<StudentWithFees>, members: List<LibraryMember>, t: Long, onStudent: (Long) -> Unit = {}, onMember: (Long) -> Unit = {}, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("Cash") }
    var tab by rememberSaveable { mutableStateOf("acad") } // academy and Abhyasika are separate lists and totals
    val done = remember { mutableStateMapOf<String, Long>() }
    // a payment is only recorded after the owner confirms the name, instalment, mode and amount
    var confirm by remember { mutableStateOf<CollectItem?>(null) }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        val waiting = confirm
        if (waiting != null) PaymentConfirm(waiting, mode, onBack = { confirm = null }) { amt ->
            confirm = null
            scope.launch { waiting.record(amt, mode); done[waiting.key] = amt }
        } else Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTitle("Quick collect", "Tap Collect only when the money is in your hand")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("acad" to "Academy · ${students.size}", "lib" to "Abhyasika · ${members.size}").forEach { (k, l) ->
                    Text(l, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (tab == k) Brand.Blue700 else Brand.Slate50)
                        .clickable { tab = k }.padding(vertical = 9.dp), style = MaterialTheme.typography.labelLarge,
                        color = if (tab == k) Color.White else Brand.Slate600, textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Bank").forEach { m ->
                    Text(m, Modifier.clip(RoundedCornerShape(50)).background(if (mode == m) Brand.Blue700 else Brand.Slate50)
                        .border(1.dp, if (mode == m) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable { mode = m }.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = if (mode == m) Color.White else Brand.Slate600)
                }
            }
            if (tab == "acad") {
                if (students.isEmpty()) Text("No academy fees due right now ✓", style = MaterialTheme.typography.bodyLarge, color = Brand.Slate500)
                students.forEach { s ->
                    val key = "s${s.id}"
                    val due = s.nextDue ?: t
                    val late = due < t
                    val remind = feeMessage(true, s.name, s.nextDueAmount, due, AppSettings.academyName)
                    CollectRow(s.name, "Instalment ${if (late) "overdue since" else "due"} ${shortDate(due)}", s.nextDueAmount, late, done[key],
                        onOpen = { onClose(); onStudent(s.id) },
                        onRemind = { Contact.whatsapp(ctx, s.mobile, remind) }) {
                        confirm = CollectItem(key, s.name, "Instalment ${if (late) "overdue since" else "due"} ${shortDate(due)}", s.nextDueAmount, s.mobile, false) { amt, md ->
                            db.students().insertPayment(StudentPayment(studentId = s.id, amount = amt, paidOn = t, mode = md)).also { pid ->
                                `in`.vidyadhan.academy.telegram.TelegramBot.studentPaid(ctx, s.id, amt, md, pid)
                            }
                        }
                    }
                }
            } else {
                if (members.isEmpty()) Text("Nobody owes anything right now ✓", style = MaterialTheme.typography.bodyLarge, color = Brand.Slate500)
                members.forEach { m ->
                    val key = "m${m.id}"
                    val renew = m.validTill <= t + ENDING_DAYS
                    val full = (if (renew) m.monthlyFee else 0L) + m.balance
                    val detail = if (renew) "Renew 1 month" + (if (m.balance > 0) " + ${rupees(m.balance)} balance" else "") + " · ends ${shortDate(m.validTill)}" else "Balance to pay"
                    CollectRow("${m.name} · Seat ${m.seatNo}", detail, full, m.validTill < t, done[key],
                        onOpen = { onClose(); onMember(m.id) },
                        onRemind = { Contact.whatsapp(ctx, m.mobile, libraryReminder(m, t)) }) {
                        confirm = CollectItem(key, "${m.name} · Seat ${m.seatNo}", detail, full, m.mobile, true) { amt, md ->
                            val updated = if (renew) m.copy(validTill = renewedTill(m.validTill, t, 1), planName = "1 month", balance = (full - amt).coerceAtLeast(0))
                                else m.copy(balance = (full - amt).coerceAtLeast(0))
                            db.library().pay(updated, LibraryPayment(memberId = m.id, kind = "FEE", amount = amt, paidOn = t, mode = md))
                            0L
                        }
                    }
                }
            }
        }
    }
}

/** Step 2 of Quick collect: the owner checks the name, instalment, mode and amount, then records it. */
@Composable
private fun PaymentConfirm(item: CollectItem, mode: String, onBack: () -> Unit, onRecord: (Long) -> Unit) {
    var amountText by remember(item.key) { mutableStateOf(item.amount.toString()) }
    val amt = amountText.toLongOrNull() ?: 0L
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetTitle("Confirm payment", item.name)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = Brand.Slate600)
            Text("Paid by $mode", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate600)
        }
        DigitsField("Amount received", amountText, 7, { amountText = it }, prefix = "₹ ",
            supporting = when { amt > item.amount -> "More than ${rupees(item.amount)}"; amt < item.amount -> "${rupees(item.amount - amt)} will stay due"; else -> "Full amount" },
            isError = amt > item.amount)
        PrimaryButton("Yes, record ${rupees(amt)} for ${item.name.substringBefore(" ·")}", { onRecord(amt) }, Modifier.fillMaxWidth(),
            enabled = amt in 1..item.amount, icon = Icons.Rounded.Check,
            custom = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Green600, Brand.Green700)))
        SoftButton("Cancel", onBack, Modifier.fillMaxWidth(), color = Brand.Slate600)
    }
}

/** One Quick collect payment waiting for the owner's confirmation. */
private class CollectItem(val key: String, val name: String, val detail: String, val amount: Long, val mobile: String, val isMember: Boolean,
                          val record: suspend (Long, String) -> Long)

@Composable
private fun CollectRow(name: String, detail: String, amount: Long, late: Boolean, collected: Long?, onOpen: () -> Unit = {},
                       onRemind: () -> Unit, onCollect: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(12.dp)) {
        // tap the name to open the student or member
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (late) Brand.Red600 else Brand.Slate500, maxLines = 2)
            Text(rupees(amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron600)
        }
        Spacer(Modifier.height(10.dp))
        if (collected != null) StatusChip("Recorded ✓ ${rupees(collected)}", Tone.Good)
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Remind", onRemind, Modifier.weight(1f), color = Brand.Blue700, icon = Icons.AutoMirrored.Rounded.Chat)
            PrimaryButton("Collect", onCollect, Modifier.weight(1f), accent = false, icon = Icons.Rounded.Check)
        }
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
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = cappedSp(12f, 1.05f)), color = Brand.Slate800, maxLines = 1, softWrap = false)
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
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
    }
}

/** A big number with its label and a small coloured chip under it. */
@Composable
private fun Column3(value: String, label: String, chip: String, color: Color, chipBg: Color, chipFg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = (if (value.length > 7) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.ExtraBold),
            color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
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

/** One small box in the top card: "Spent today ₹0", "Profit in November ₹1,750". */
@Composable
private fun HeroBox(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Blue100.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = color, maxLines = 1)
    }
}

/** + Fee: find any student and record any amount (part payment or advance too). Abhyasika members open their page to renew or collect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordAnySheet(students: List<StudentWithFees>, members: List<LibraryMember>, t: Long, onMember: (Long) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<StudentWithFees?>(null) }
    var amount by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Cash") }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val s = pick
            if (s == null) {
                SheetTitle("Record a payment", "Find the student or Abhyasika member")
                SearchBox(q, { q = it }, "Search name or mobile")
                val sMatch = students.filter { q.isBlank() || it.name.contains(q, true) || it.mobile.contains(q) }.take(40)
                val mMatch = members.filter { q.isBlank() || it.name.contains(q, true) || it.mobile.contains(q) }.take(40)
                if (sMatch.isNotEmpty()) Text("STUDENTS", style = MaterialTheme.typography.labelMedium, color = Brand.Slate500)
                sMatch.forEach { st ->
                    val bal = (st.totalFee - st.paid).coerceAtLeast(0)
                    OptionRow(Icons.Rounded.School, Brand.Blue700, st.name, if (bal > 0) "Balance ${rupees(bal)}" else "Fully paid") {
                        pick = st; amount = (if (st.dueNow > 0) st.dueNow else bal).takeIf { it > 0 }?.toString() ?: ""
                    }
                }
                if (mMatch.isNotEmpty()) Text("ABHYASIKA", style = MaterialTheme.typography.labelMedium, color = Brand.Slate500)
                mMatch.forEach { m -> OptionRow(Icons.Rounded.EventSeat, Brand.Green600, "${m.name} · Seat ${m.seatNo}", "Opens the member page to renew or collect") { onClose(); onMember(m.id) } }
                if (sMatch.isEmpty() && mMatch.isEmpty()) Text("No one matches", style = MaterialTheme.typography.bodyLarge, color = Brand.Slate500)
            } else {
                val bal = (s.totalFee - s.paid).coerceAtLeast(0)
                val a = amount.toLongOrNull() ?: 0L
                SheetTitle("Record a payment", "${s.name} · balance ${rupees(bal)}")
                DigitsField("Amount received", amount, 8, { amount = it }, prefix = "₹ ", supporting = "Any amount: part payment or advance")
                Text("Paid by", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                ModeChoice(mode) { mode = it }
                PrimaryButton("Save payment", {
                    scope.launch {
                        val pid = db.students().insertPayment(StudentPayment(studentId = s.id, amount = a, paidOn = t, mode = mode))
                        `in`.vidyadhan.academy.telegram.TelegramBot.studentPaid(ctx, s.id, a, mode, pid)
                    }
                    onClose()
                }, Modifier.fillMaxWidth(), enabled = a > 0, icon = Icons.Rounded.Check)
                SoftButton("Choose someone else", { pick = null }, Modifier.fillMaxWidth())
            }
        }
    }
}


/** A person in the reminders list: tap the name to open them, Remind sends the WhatsApp reminder. */
@Composable
private fun ReminderPerson(name: String, detail: String, late: Boolean, onOpen: () -> Unit, onRemind: () -> Unit, telegramKey: String? = null, onTelegram: (() -> Unit)? = null) {
    // quiet colours: a muted amber for "late", never a hard red
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(14.dp))
        .clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = if (late) Color(0xFF9A6700) else Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(6.dp))
        if (telegramKey != null && onTelegram != null) ChannelButton("Telegram", Color(0xFF229ED9), onTelegram) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Send on Telegram", tint = Color(0xFF229ED9), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        ChannelButton("WhatsApp", Brand.WhatsAppDark, onRemind) { WhatsAppLogo(Modifier.size(30.dp)) }
    }
}


/** Sends a reminder through the Telegram bot to a connected person (the bot delivers it; nothing opens). */
private fun tgSend(ctx: Context, key: String, text: String) {
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val ok = `in`.vidyadhan.academy.telegram.TelegramBot.sendToKey(key, `in`.vidyadhan.academy.telegram.TelegramBot.esc(text))
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            android.widget.Toast.makeText(ctx, if (ok) "Sent on Telegram ✓" else "Could not send on Telegram", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
