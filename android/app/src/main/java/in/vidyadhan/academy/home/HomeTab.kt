package `in`.vidyadhan.academy.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.PaymentRow
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Where a Home shortcut should take the owner. */
enum class Go { Students, NewStudent, Student, Enquiries, NewEnquiry, Enquiry, Library, NewMember, Member, Money, NewExpense, Settings, Backup, Search, Broadcast }

private data class Agenda(
    val name: String, val detail: String, val icon: ImageVector, val color: Color, val tone: Tone, val chip: String, val go: Go, val id: Long,
    val photo: String = "", val mobile: String = "", val remind: String = "", val amount: Long = 0,
)
private data class Birthday(val name: String, val mobile: String, val photo: String, val what: String, val go: Go, val id: Long)

fun isBirthday(dob: Long?, day: LocalDate): Boolean = dob != null && LocalDate.ofEpochDay(dob).let { it.month == day.month && it.dayOfMonth == day.dayOfMonth }

fun birthdayWish(name: String) = `in`.vidyadhan.academy.Templates.fill("birthday", true, mapOf("name" to name.substringBefore(' ')))

@Composable
fun HomeTab(onGo: (Go, Long) -> Unit) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val now = LocalDate.now()
    val monthStart = now.withDayOfMonth(1).toEpochDay()
    val monthEnd = now.withDayOfMonth(now.lengthOfMonth()).toEpochDay()
    val allStudents by remember { studentsWithFees(db.students()) }.collectAsStateWithLifecycle(emptyList())
    val students = allStudents.filter { it.current }
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val feesMonth by remember(monthStart) { db.students().paidBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val libMonth by remember(monthStart) { db.library().collectedBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val recentFees by db.students().recentPaid().collectAsStateWithLifecycle(emptyList())
    val recentLib by db.library().recentPayments().collectAsStateWithLifecycle(emptyList())
    val rooms by db.library().rooms().collectAsStateWithLifecycle(emptyList())
    val yearStart = now.withDayOfYear(1).toEpochDay()
    val feesYear by remember(yearStart) { db.students().paidBetween(yearStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val libYear by remember(yearStart) { db.library().collectedBetween(yearStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    var showAll by remember { mutableStateOf(false) }

    val t = today()
    val pending = students.sumOf { (it.totalFee - it.paid).coerceAtLeast(0) }
    val collected = feesMonth + libMonth
    val overdue = students.filter { it.nextDue != null && it.nextDue < t }
    val openEnq = enquiries.filter { it.status == "NEW" || it.status == "CONTACTED" }
    val active = members.filter { it.active }
    val seats = `in`.vidyadhan.academy.library.allSeats(rooms, AppSettings.seatCount).size

    val agenda = buildList {
        openEnq.filter { it.followUpOn <= t }.forEach {
            add(Agenda(it.name, "Enquiry · ${it.course.ifBlank { it.mobile }}", Icons.Rounded.Forum, Brand.Saffron600,
                if (it.followUpOn < t) Tone.Bad else Tone.Accent, if (it.followUpOn < t) "Overdue" else "Follow up", Go.Enquiry, it.id))
        }
        students.filter { it.nextDue != null && it.nextDue <= t }.forEach {
            add(Agenda(it.name, "${rupees(it.nextDueAmount)} due · ${rupees(it.totalFee - it.paid)} pending", Icons.Rounded.CurrencyRupee, Brand.Red600,
                if (it.nextDue!! < t) Tone.Bad else Tone.Warn, if (it.nextDue < t) "Fee overdue" else "Fee due", Go.Student, it.id, it.photo,
                it.mobile, feeMessage(true, it.name, it.nextDueAmount, it.nextDue)))
        }
        active.filter { it.validTill <= t }.forEach {
            add(Agenda(it.name, "Seat ${it.seatNo} · fee ${rupees(it.monthlyFee)}" + if (it.balance > 0) " + ${rupees(it.balance)} balance" else "", Icons.Rounded.EventSeat,
                Brand.Teal700, Tone.Bad, "Renewal", Go.Member, it.id, it.photo, it.mobile, `in`.vidyadhan.academy.library.libraryReminder(it, t)))
        }
        active.filter { it.validTill > t && it.balance > 0 }.forEach {
            add(Agenda(it.name, "Seat ${it.seatNo} · ${rupees(it.balance)} still to pay", Icons.Rounded.EventSeat,
                Brand.Violet700, Tone.Accent, "Part paid", Go.Member, it.id, it.photo, it.mobile, `in`.vidyadhan.academy.library.libraryReminder(it, t)))
        }
    }
    val upcoming = buildList {
        students.filter { it.nextDue != null && it.nextDue in (t + 1)..(t + 7) }.forEach {
            add(Agenda(it.name, "Course fee · ${whenText(it.nextDue!!)}", Icons.Rounded.School, Brand.Blue600, Tone.Info, rupees(it.nextDueAmount), Go.Student, it.id, it.photo,
                it.mobile, feeMessage(true, it.name, it.nextDueAmount, it.nextDue), it.nextDueAmount) to it.nextDue)
        }
        active.filter { it.validTill in (t + 1)..(t + 7) }.forEach {
            add(Agenda(it.name, "Library · seat ${it.seatNo} · ends ${whenText(it.validTill)}", Icons.Rounded.EventSeat, Brand.Teal700, Tone.Info, rupees(it.monthlyFee), Go.Member, it.id, it.photo,
                it.mobile, `in`.vidyadhan.academy.library.libraryReminder(it, t), it.monthlyFee) to it.validTill)
        }
    }.sortedBy { it.second }.map { it.first }
    val birthdays = students.filter { isBirthday(it.dob, now) }.map { Birthday(it.name, it.mobile, it.photo, "Student · ${it.course}", Go.Student, it.id) } +
        active.filter { isBirthday(it.dob, now) }.map { Birthday(it.name, it.mobile, it.photo, "Library · seat ${it.seatNo}", Go.Member, it.id) }
    val recent = (recentFees + recentLib).sortedByDescending { it.paidOn }.take(5)

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().heroBackground().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 74.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoBadge(46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${greeting()} 👋 · ${now.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))}", style = MaterialTheme.typography.bodySmall, color = Brand.Blue200, maxLines = 1)
                    Text(AppSettings.academyName, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                }
                GlassIconButton(Icons.Rounded.Search, "Search", { onGo(Go.Search, 0) })
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Rounded.Settings, "Settings", { onGo(Go.Settings, 0) })
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onGo(Go.Money, 0) }, verticalAlignment = Alignment.CenterVertically) {
                Text("Collected in ${now.format(DateTimeFormatter.ofPattern("MMMM"))}", style = MaterialTheme.typography.labelLarge, color = Brand.Blue200)
                Icon(Icons.Rounded.ChevronRight, null, tint = Brand.Blue200, modifier = Modifier.size(18.dp))
            }
            Text(rupees(collected), style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp), color = Color.White)
            Spacer(Modifier.height(10.dp))
            val total = collected + pending
            ProgressBar(if (total == 0L) 0f else collected.toFloat() / total, color = Brand.Saffron400, track = Color.White.copy(alpha = 0.16f), height = 8.dp)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("Students ${rupees(feesMonth)} · Library ${rupees(libMonth)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Brand.Blue200, maxLines = 1)
                Text("${rupees(pending)} to collect", style = MaterialTheme.typography.labelMedium, color = Brand.Saffron200)
            }
            Spacer(Modifier.height(4.dp))
            Text("This year so far: ${rupees(feesYear + libYear)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        }

        Column(Modifier.offset(y = (-52).dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(26.dp), 14.dp).padding(vertical = 14.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(Icons.Rounded.PersonAdd, "Student", Brand.Blue600, { onGo(Go.NewStudent, 0) }, modifier = Modifier.weight(1f))
                RoundAction(Icons.Rounded.AddComment, "Enquiry", Brand.Saffron600, { onGo(Go.NewEnquiry, 0) }, modifier = Modifier.weight(1f))
                RoundAction(Icons.Rounded.EventSeat, "Library", Brand.Teal700, { onGo(Go.NewMember, 0) }, modifier = Modifier.weight(1f))
                RoundAction(Icons.Rounded.Campaign, "Message", Color(0xFFE11D48), { onGo(Go.Broadcast, 0) }, modifier = Modifier.weight(1f))
                RoundAction(Icons.Rounded.ReceiptLong, "Expense", Brand.Violet700, { onGo(Go.NewExpense, 0) }, modifier = Modifier.weight(1f))
            }
            if (birthdays.isNotEmpty()) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFF1F2), Brand.Saffron50)))
                    .border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Cake, Color(0xFFE11D48), 40.dp, filled = true); Spacer(Modifier.width(12.dp))
                    Text("Birthday today 🎂", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                }
                birthdays.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable { onGo(b.go, b.id) }) { Avatar(b.name, 40.dp, photo = b.photo) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                            Text(b.what, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        }
                        PillButton("Wish", { Contact.whatsapp(ctx, b.mobile, birthdayWish(b.name)) }, Brand.WhatsAppDark, Icons.AutoMirrored.Rounded.Chat)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricCard(Icons.Rounded.School, "Students", "${students.size}", Brand.Blue600, Modifier.weight(1f),
                    sub = if (overdue.isEmpty()) "All on time" else "${overdue.size} overdue", subColor = if (overdue.isEmpty()) Brand.Green700 else Brand.Red600) { onGo(Go.Students, 0) }
                MetricCard(Icons.Rounded.Forum, "Open enquiries", "${openEnq.size}", Brand.Saffron600, Modifier.weight(1f),
                    sub = "${openEnq.count { it.followUpOn <= t }} due today") { onGo(Go.Enquiries, 0) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricCard(Icons.Rounded.EventSeat, "Seats filled", "${active.size}/$seats", Brand.Teal700, Modifier.weight(1f),
                    sub = "${(seats - active.size).coerceAtLeast(0)} free") { onGo(Go.Library, 0) }
                MetricCard(Icons.Rounded.AccountBalanceWallet, "Fees pending", rupees(pending), Brand.Violet700, Modifier.weight(1f),
                    sub = "${students.count { it.totalFee > it.paid }} students") { onGo(Go.Students, 0) }
            }

            SectionHeader("Today's to-do", action = if (agenda.size > 8) (if (showAll) "Show less" else "Show all ${agenda.size}") else null, onAction = { showAll = !showAll })
            if (agenda.isEmpty()) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Brand.Green50, Color.White)))
                    .border(1.dp, Brand.Green100, RoundedCornerShape(24.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.TaskAlt, Brand.Green600, 48.dp, filled = true)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("All caught up!", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                        Text("No follow-ups, dues or renewals today.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                }
            } else AgendaList(if (showAll) agenda else agenda.take(8)) { onGo(it.go, it.id) }

            if (recent.isNotEmpty()) {
                SectionHeader("Recent payments")
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                    recent.forEachIndexed { i, p -> if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = Brand.Line); PaymentLine(p) }
                }
            }
            SectionHeader("Coming up · next 7 days", action = if (upcoming.isNotEmpty()) rupees(upcoming.sumOf { it.amount }) else null)
            if (upcoming.isEmpty()) Text("No payments due in the next week.", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500, modifier = Modifier.padding(start = 4.dp))
            else AgendaList(upcoming, amountChip = true) { onGo(it.go, it.id) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AgendaList(items: List<Agenda>, amountChip: Boolean = false, onClick: (Agenda) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
        items.forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = Brand.Line)
            Row(Modifier.fillMaxWidth().pressable { onClick(a) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (a.photo.isNotBlank()) Avatar(a.name, 42.dp, photo = a.photo) else IconTile(a.icon, a.color, 42.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1)
                    Text(a.detail, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
                }
                if (amountChip) Text(a.chip, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
                else StatusChip(a.chip, a.tone)
                if (a.remind.isNotBlank() && a.mobile.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    SmallIconButton(Icons.AutoMirrored.Rounded.Chat, "Send reminder to ${a.name}", Brand.WhatsAppDark) { Contact.whatsapp(ctx, a.mobile, a.remind) }
                }
            }
        }
    }
}

@Composable
private fun PaymentLine(p: PaymentRow) {
    val out = p.kind == "DEPOSIT_RETURN"
    val label = when (p.kind) { "STUDENT_FEE" -> "Course fee"; "FEE" -> "Library fee"; "DEPOSIT" -> "Library deposit"; "REGISTRATION" -> "Registration"; else -> "Deposit returned" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.name, 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1)
            Text("$label · ${relativeDay(p.paidOn)}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
        Text((if (out) "− " else "+ ") + rupees(p.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = if (out) Brand.Red600 else Brand.Green700)
    }
}
