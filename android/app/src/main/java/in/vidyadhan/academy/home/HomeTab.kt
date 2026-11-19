package `in`.vidyadhan.academy.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.PaymentRow
import `in`.vidyadhan.academy.library.ENDING_DAYS
import `in`.vidyadhan.academy.library.allSeats
import `in`.vidyadhan.academy.library.libraryReminder
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Where a dashboard shortcut should take the owner. */
enum class Go {
    Students, NewStudent, Student, Enquiries, NewEnquiry, Enquiry, Library, NewMember, Member, Money, NewExpense, Settings, Backup, Search, Broadcast,
    Seats, MemberDues, MemberEnding,
}

fun isBirthday(dob: Long?, day: LocalDate): Boolean = dob != null && LocalDate.ofEpochDay(dob).let { it.month == day.month && it.dayOfMonth == day.dayOfMonth }

fun birthdayWish(name: String) = `in`.vidyadhan.academy.Templates.fill("birthday", true, mapOf("name" to name.substringBefore(' ')))

/** One person in "Today's work". [remindKey] marks "Reminded today ✓"; [call] means the action is a phone call. */
private data class Task(
    val name: String, val detail: String, val detailColor: Color, val photo: String, val go: Go, val id: Long,
    val mobile: String, val message: String = "", val amount: Long? = null, val remindKey: String = "", val call: Boolean = false,
)

private data class Bday(val name: String, val mobile: String, val what: String, val photo: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(onGo: (Go, Long) -> Unit) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val now = LocalDate.now()
    val t = today()
    val monthStart = now.withDayOfMonth(1).toEpochDay()
    val monthEnd = now.withDayOfMonth(now.lengthOfMonth()).toEpochDay()
    val allStudents by remember { studentsWithFees(db.students()) }.collectAsStateWithLifecycle(emptyList())
    val students = allStudents.filter { it.current }
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val rooms by db.library().rooms().collectAsStateWithLifecycle(emptyList())
    val expenses by db.expenses().all().collectAsStateWithLifecycle(emptyList())
    val feesToday by remember(t) { db.students().paidBetween(t, t) }.collectAsStateWithLifecycle(0L)
    val libToday by remember(t) { db.library().collectedBetween(t, t) }.collectAsStateWithLifecycle(0L)
    val feesMonth by remember(monthStart) { db.students().paidBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val libMonth by remember(monthStart) { db.library().collectedBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val recentFees by db.students().recentPaid().collectAsStateWithLifecycle(emptyList())
    val recentLib by db.library().recentPayments().collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf("fees") }
    var menu by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var showBirthdays by remember { mutableStateOf(false) }

    val active = members.filter { it.active }
    val seats = allSeats(rooms, AppSettings.seatCount)
    val free = seats.count { n -> active.none { it.seatNo == n } }
    val openEnq = enquiries.filter { it.status == "NEW" || it.status == "CONTACTED" }

    // ── money ──
    val income = feesMonth + libMonth
    val monthExpenses = expenses.filter { it.spentOn in monthStart..monthEnd }
    val spent = monthExpenses.sumOf { it.amount }
    val biggest = monthExpenses.groupBy { it.category }.maxByOrNull { (_, l) -> l.sumOf { it.amount } }?.key
    // still to come in this month: student instalments due by month end, library renewals and balances
    val stillDue = students.filter { it.nextDue != null && it.nextDue <= monthEnd }.sumOf { it.nextDueAmount } +
        active.sumOf { (if (it.validTill <= monthEnd) it.monthlyFee else 0L) + it.balance }
    val dueThisMonth = income + stillDue
    val pending = students.sumOf { (it.totalFee - it.paid).coerceAtLeast(0) }
    val overdue = students.count { it.nextDue != null && it.nextDue < t }

    // ── today's work ──
    val fees = students.filter { it.nextDue != null && it.nextDue <= t + 1 }.sortedBy { it.nextDue }.map {
        val d = it.nextDue!!
        Task(it.name, when { d < t -> "Overdue ${t - d} day${if (t - d == 1L) "" else "s"}"; d == t -> "Due today"; else -> "Due tomorrow" },
            if (d < t) Brand.Red600 else if (d == t) Brand.Saffron700 else Brand.Slate500, it.photo, Go.Student, it.id,
            it.mobile, feeMessage(true, it.name, it.nextDueAmount, d), it.nextDueAmount, remindKey = "s${it.id}")
    }
    val calls = openEnq.filter { it.followUpOn <= t }.sortedBy { it.followUpOn }.map {
        Task(it.name, (if (it.followUpOn < t) "Was due ${relativeDay(it.followUpOn).lowercase()}" else "Call today") + if (it.course.isNotBlank()) " · ${it.course}" else "",
            if (it.followUpOn < t) Brand.Red600 else Brand.Saffron700, "", Go.Enquiry, it.id, it.mobile, call = true)
    }
    val library = active.filter { it.validTill <= t || it.balance > 0 }.sortedBy { it.validTill }.map {
        val late = t - it.validTill
        Task("${it.name} · Seat ${it.seatNo}",
            when { it.validTill < t -> "Plan ended ${if (late == 1L) "yesterday" else "$late days ago"}"; it.validTill == t -> "Plan ends today"; else -> "${rupees(it.balance)} balance to pay" },
            if (it.validTill < t) Brand.Red600 else Brand.Saffron700, it.photo, Go.Member, it.id, it.mobile, libraryReminder(it, t),
            if (it.validTill <= t) it.monthlyFee + it.balance else it.balance, remindKey = "m${it.id}")
    }
    val todo = fees.size + calls.size + library.size

    val birthdays = students.filter { isBirthday(it.dob, now) }.map { Bday(it.name, it.mobile, "Student · ${it.course}${if (it.batch.isNotBlank()) " ${it.batch}" else ""}", it.photo) } +
        active.filter { isBirthday(it.dob, now) }.map { Bday(it.name, it.mobile, "Library · seat ${it.seatNo}", it.photo) }
    val backupDays = if (AppSettings.lastBackup == 0L) null else t - AppSettings.lastBackup
    val needsBackup = (students.isNotEmpty() || members.isNotEmpty()) && (backupDays == null || backupDays >= 7)
    val recent = (recentFees + recentLib).sortedByDescending { it.paidOn }.take(3)

    var arranging by remember { mutableStateOf(false) }
    val titles = linkedMapOf("work" to "Today's work", "nums" to "Academy numbers", "lib" to "Library", "pay" to "Recent payments")
    val order = (AppSettings.homeOrder.filter { it in titles } + titles.keys).distinct()
    val hidden = AppSettings.homeHidden
    val workCard: @Composable () -> Unit = {
                // ── today's work ──
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).clip(RoundedCornerShape(24.dp))) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                        Text("Today's work", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                        Text(if (todo == 0) "Nothing pending today ✓" else "$todo thing${if (todo == 1) "" else "s"} to do", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WorkTab("Fees due", fees.size, tab == "fees") { tab = "fees" }
                        WorkTab("Calls", calls.size, tab == "calls") { tab = "calls" }
                        WorkTab("Library", library.size, tab == "lib") { tab = "lib" }
                    }
                    val (list, all, allGo) = when (tab) {
                        "calls" -> Triple(calls, "See all enquiries", Go.Enquiries)
                        "lib" -> Triple(library, "See all library dues", Go.MemberDues)
                        else -> Triple(fees, "See all fees due", Go.Students)
                    }
                    if (list.isEmpty()) Text(when (tab) { "calls" -> "No calls due today ✓"; "lib" -> "No library fees pending ✓"; else -> "No fees due today or tomorrow ✓" },
                        Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    list.take(4).forEach { TaskRow(it, t, onGo) }
                    if (list.isNotEmpty()) SeeAll(if (list.size > 4) "$all (${list.size})" else all) { onGo(allGo, 0) }
                }

    }
    val numsCard: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ── academy numbers ──
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Students", "${students.size}", if (overdue == 0) "All fees on time" else "$overdue fee${if (overdue == 1) "" else "s"} overdue",
                        if (overdue == 0) Brand.Green700 else Brand.Red600, Icons.Rounded.School, Brand.Blue700, Modifier.weight(1f)) { onGo(Go.Students, 0) }
                    Stat("Fees to collect", rupees(pending), "from ${students.count { it.totalFee > it.paid }} students", Brand.Slate500,
                        Icons.Rounded.CurrencyRupee, Brand.Red600, Modifier.weight(1f)) { onGo(Go.Students, 0) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Open enquiries", "${openEnq.size}", "${calls.size} to call today", Brand.Saffron700, Icons.Rounded.Forum, Brand.Saffron600, Modifier.weight(1f)) { onGo(Go.Enquiries, 0) }
                    Stat("Expenses · ${now.format(DateTimeFormatter.ofPattern("MMMM"))}", rupees(spent), biggest?.let { "$it is the biggest" } ?: "Nothing spent yet", Brand.Slate500,
                        Icons.Rounded.ReceiptLong, Brand.Violet700, Modifier.weight(1f)) { onGo(Go.Money, 0) }
                }

                }
    }
    val libCard: @Composable () -> Unit = {
                // ── library at a glance ──
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Library (Abhyasika)", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                        Text("${seats.size} seats" + if (rooms.isNotEmpty()) " in ${rooms.size} room${if (rooms.size == 1) "" else "s"}" else "",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mini("$free", "Free seats", Brand.Green700, Brand.Green50, Modifier.weight(1f)) { onGo(Go.Seats, 0) }
                        Mini("${active.size}", "Members", Brand.Blue700, Brand.Blue50, Modifier.weight(1f)) { onGo(Go.Library, 0) }
                        Mini("${active.count { it.validTill in t..(t + ENDING_DAYS) }}", "Ending in $ENDING_DAYS days", Brand.Saffron700, Brand.Saffron50, Modifier.weight(1f)) { onGo(Go.MemberEnding, 0) }
                    }
                }

    }
    val payCard: @Composable () -> Unit = {
                // ── recent payments ──
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).clip(RoundedCornerShape(24.dp))) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                        Text("Recent payments", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                        Text("Students and library", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    if (recent.isEmpty()) Text("No payments yet", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    recent.forEach { PaymentLine(it) }
                    if (recent.isNotEmpty()) SeeAll("See all payments") { onGo(Go.Money, 0) }
                }
    }
    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ── header ──
            Column(Modifier.fillMaxWidth().heroBackground().statusBarsPadding().padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 46.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoBadge(42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(AppSettings.academyName, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${greeting()} · ${now.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))}", style = MaterialTheme.typography.bodySmall, color = Brand.Blue200, maxLines = 1)
                    }
                    GlassIconButton(Icons.Rounded.Search, "Search", { onGo(Go.Search, 0) })
                    Spacer(Modifier.width(8.dp))
                    Box {
                        GlassIconButton(Icons.Rounded.Notifications, "Reminders", { showReminders = true })
                        val n = todo + birthdays.size + if (needsBackup) 1 else 0
                        if (n > 0) Text(if (n > 99) "99+" else "$n", Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).clip(RoundedCornerShape(50))
                            .background(Brand.Saffron500).padding(horizontal = 5.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneyBox("Collected today", rupees(feesToday + libToday), "Students ${rupees(feesToday)} · Library ${rupees(libToday)}", Color.White, Modifier.weight(1f)) { onGo(Go.Money, 0) }
                    MoneyBox("Profit · ${now.format(DateTimeFormatter.ofPattern("MMMM"))}", rupees(income - spent), "${rupees(income)} in − ${rupees(spent)} out",
                        if (income >= spent) Color(0xFF86EFAC) else Color(0xFFFCA5A5), Modifier.weight(1f)) { onGo(Go.Money, 0) }
                }
                Spacer(Modifier.height(12.dp))
                ProgressBar(if (dueThisMonth == 0L) 0f else (income.toFloat() / dueThisMonth).coerceIn(0f, 1f), color = Brand.Saffron400, track = Color.White.copy(alpha = 0.16f), height = 8.dp)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("${rupees(income)} collected of ${rupees(dueThisMonth)} due this month", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Brand.Blue200,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (dueThisMonth == 0L) 0 else income * 100 / dueThisMonth}%", style = MaterialTheme.typography.labelMedium, color = Brand.Blue200)
                }
            }

            Column(Modifier.offset(y = (-32).dp).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (needsBackup) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Amber50).border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
                    .clickable { onGo(Go.Backup, 0) }.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = Brand.Amber700); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (backupDays == null) "No backup yet" else "Last backup $backupDays days ago", style = MaterialTheme.typography.titleSmall, color = Brand.Amber700)
                        Text("If the phone is lost, data since then is lost.", style = MaterialTheme.typography.bodySmall, color = Brand.Amber700)
                    }
                    PillButton("Back up", { onGo(Go.Backup, 0) }, Brand.Amber700)
                }
                if (birthdays.isNotEmpty() && AppSettings.birthdayHiddenOn != t) Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(16.dp), 4.dp).border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(16.dp))
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎂", fontSize = 18.sp); Spacer(Modifier.width(10.dp))
                    if (birthdays.size == 1) {
                        Text("${birthdays[0].name} · birthday today", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        PillButton("Wish", { Contact.whatsapp(ctx, birthdays[0].mobile, birthdayWish(birthdays[0].name)) }, Brand.WhatsAppDark)
                    } else {
                        val names = birthdays.take(2).joinToString(", ") { it.name.substringBefore(' ') } + if (birthdays.size > 2) " +${birthdays.size - 2}" else ""
                        Text("${birthdays.size} birthdays today · $names", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        PillButton("See all", { showBirthdays = true }, Brand.Blue700)
                    }
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable { AppSettings.birthdayHiddenOn = t }, Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Hide birthdays for today", tint = Brand.Slate400, modifier = Modifier.size(18.dp))
                    }
                }

                Column(Modifier.longPress { arranging = true }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (arranging) Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(18.dp), 4.dp).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Arrange your dashboard", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                            Text("Move cards with ↑ ↓. The eye hides a card.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        }
                        PillButton("Done", { arranging = false }, Brand.Blue700, Icons.Rounded.Check)
                    }
                    val shown = order.filter { it !in hidden }
                    shown.forEach { key ->
                        Arrangeable(arranging, titles.getValue(key),
                            onMove = { d ->
                                val o = order.toMutableList(); val i = o.indexOf(key)
                                // jump over hidden cards so each tap visibly moves the card
                                var j = i + d
                                while (j in o.indices && o[j] in hidden) j += d
                                if (j in o.indices) { o.removeAt(i); o.add(j, key); AppSettings.homeOrder = o }
                            },
                            onHide = { AppSettings.homeHidden = hidden + key }) {
                            when (key) { "work" -> workCard(); "nums" -> numsCard(); "lib" -> libCard(); else -> payCard() }
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
                Spacer(Modifier.height(70.dp))
            }
        }

        // ── one "+" for adding anything ──
        AnimatedVisibility(menu, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brand.Blue950.copy(alpha = 0.45f)).clickable(remember { MutableInteractionSource() }, null) { menu = false })
        }
        if (!arranging) Column(Modifier.align(Alignment.BottomEnd).padding(18.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (menu) {
                MenuItem("Collect fee", Icons.Rounded.CurrencyRupee, Brand.Green600) { menu = false; onGo(Go.Search, 0) }
                MenuItem("Add student", Icons.Rounded.PersonAdd, Brand.Blue600) { menu = false; onGo(Go.NewStudent, 0) }
                MenuItem("New enquiry", Icons.Rounded.AddComment, Brand.Saffron600) { menu = false; onGo(Go.NewEnquiry, 0) }
                MenuItem("Library member", Icons.Rounded.EventSeat, Brand.Teal700) { menu = false; onGo(Go.NewMember, 0) }
                MenuItem("Add expense", Icons.Rounded.ReceiptLong, Brand.Violet700) { menu = false; onGo(Go.NewExpense, 0) }
            }
            Box(Modifier.size(58.dp).shadow(14.dp, RoundedCornerShape(20.dp), spotColor = Brand.Saffron700.copy(alpha = 0.5f)).clip(RoundedCornerShape(20.dp))
                .background(AccentBrush).clickable { menu = !menu }, Alignment.Center) {
                Icon(if (menu) Icons.Rounded.Close else Icons.Rounded.Add, if (menu) "Close" else "Add", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }

    if (showReminders) ModalBottomSheet({ showReminders = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetTitle("Reminders", "Everything that needs you today")
            if (fees.isNotEmpty()) OptionRow(Icons.Rounded.CurrencyRupee, Brand.Red600, "${fees.size} student fee${if (fees.size == 1) "" else "s"} due", rupees(fees.sumOf { it.amount ?: 0L })) { showReminders = false; tab = "fees" }
            if (calls.isNotEmpty()) OptionRow(Icons.Rounded.Call, Brand.Saffron600, "${calls.size} enquir${if (calls.size == 1) "y" else "ies"} to call") { showReminders = false; tab = "calls" }
            if (library.isNotEmpty()) OptionRow(Icons.Rounded.EventSeat, Brand.Teal700, "${library.size} library due${if (library.size == 1) "" else "s"}") { showReminders = false; tab = "lib" }
            if (birthdays.isNotEmpty()) OptionRow(Icons.Rounded.Cake, Color(0xFFE11D48), "${birthdays.size} birthday${if (birthdays.size == 1) "" else "s"} today") { showReminders = false; showBirthdays = true }
            if (needsBackup) OptionRow(Icons.Rounded.WarningAmber, Brand.Amber700, if (backupDays == null) "No backup yet" else "Backup is $backupDays days old", "Back up to Google Drive") {
                showReminders = false; onGo(Go.Backup, 0)
            }
            if (todo == 0 && birthdays.isEmpty() && !needsBackup) Text("Nothing needs you today ✓", style = MaterialTheme.typography.bodyLarge, color = Brand.Slate500)
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
                    PillButton(if (b.mobile in wished) "✓ Wished" else "Wish", { wished += b.mobile; Contact.whatsapp(ctx, b.mobile, birthdayWish(b.name)) },
                        if (b.mobile in wished) Brand.Slate400 else Brand.WhatsAppDark)
                }
            }
        }
    }
}

@Composable
private fun MoneyBox(label: String, value: String, sub: String, valueColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Blue200, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = Brand.Blue200, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WorkTab(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (selected) Brand.Blue700 else Color.White)
        .border(1.dp, if (selected) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Brand.Slate600)
        Spacer(Modifier.width(5.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = if (selected) Brand.Blue100 else Brand.Slate400)
    }
}

@Composable
private fun TaskRow(task: Task, t: Long, onGo: (Go, Long) -> Unit) {
    val ctx = LocalContext.current
    val reminded = task.remindKey.isNotBlank() && AppSettings.remindedOn(task.remindKey, t)
    HorizontalDivider(color = Brand.Line)
    Row(Modifier.fillMaxWidth().clickable { onGo(task.go, task.id) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(task.name, 40.dp, photo = task.photo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(task.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (reminded) "${task.detail} · Reminded today ✓" else task.detail, style = MaterialTheme.typography.bodySmall,
                color = if (reminded) Brand.Green700 else task.detailColor, maxLines = 2)
        }
        task.amount?.takeIf { it > 0 }?.let {
            Spacer(Modifier.width(8.dp))
            Text(rupees(it), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
        }
        Spacer(Modifier.width(8.dp))
        when {
            task.call -> SmallIconButton(Icons.Rounded.Call, "Call ${task.name}", Brand.Blue600) { Contact.call(ctx, task.mobile) }
            reminded -> SmallIconButton(Icons.Rounded.Check, "Reminded. Send again to ${task.name}", Brand.Green700) { Contact.whatsapp(ctx, task.mobile, task.message) }
            task.message.isNotBlank() -> SmallIconButton(Icons.AutoMirrored.Rounded.Chat, "Send reminder to ${task.name}", Brand.WhatsAppDark) {
                AppSettings.markReminded(task.remindKey, t); Contact.whatsapp(ctx, task.mobile, task.message)
            }
        }
    }
}

@Composable
private fun SeeAll(text: String, onClick: () -> Unit) {
    HorizontalDivider(color = Brand.Line)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Brand.Blue700)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Brand.Blue700, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, subColor: Color, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.softCard(RoundedCornerShape(20.dp), 6.dp).clip(RoundedCornerShape(20.dp)).pressable(onClick = onClick).padding(13.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)), Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Mini(value: String, label: String, color: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable(onClick = onClick)
        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color), Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
    }
}

@Composable
private fun PaymentLine(p: PaymentRow) {
    val out = p.kind == "DEPOSIT_RETURN"
    val label = when (p.kind) { "STUDENT_FEE" -> "Course fee"; "FEE" -> "Library fee"; "DEPOSIT" -> "Library deposit"; "REGISTRATION" -> "Registration"; else -> "Deposit returned" }
    HorizontalDivider(color = Brand.Line)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.name, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1)
            Text("$label · ${relativeDay(p.paidOn)}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
        Text((if (out) "− " else "+ ") + rupees(p.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = if (out) Brand.Red600 else Brand.Green700)
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
    Column(Modifier.fillMaxWidth().border(2.dp, Brand.Blue500, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))) {
        Row(Modifier.fillMaxWidth().background(Brand.Blue700).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DragIndicator, null, tint = Brand.Blue200, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Color.White)
            IconButton({ onMove(-1) }) { Icon(Icons.Rounded.ArrowUpward, "Move $title up", tint = Color.White) }
            IconButton({ onMove(1) }) { Icon(Icons.Rounded.ArrowDownward, "Move $title down", tint = Color.White) }
            IconButton(onHide) { Icon(Icons.Rounded.VisibilityOff, "Hide $title", tint = Color.White) }
        }
        Box(Modifier.padding(6.dp)) {
            content()
            // a cover so the card's own buttons do nothing while arranging
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.25f)).clickable(remember { MutableInteractionSource() }, null) {})
        }
    }
}
