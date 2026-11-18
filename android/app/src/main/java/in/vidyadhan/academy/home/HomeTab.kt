package `in`.vidyadhan.academy.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** One line in a dashboard card. */
private data class Line(
    val name: String, val detail: String, val detailColor: Color, val photo: String, val go: Go, val id: Long,
    val mobile: String = "", val message: String = "", val amount: Long? = null,
)

@Composable
fun HomeTab(onGo: (Go, Long) -> Unit) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val now = LocalDate.now()
    val monthStart = now.withDayOfMonth(1).toEpochDay()
    val monthEnd = now.withDayOfMonth(now.lengthOfMonth()).toEpochDay()
    val yearStart = now.withDayOfYear(1).toEpochDay()
    val allStudents by remember { studentsWithFees(db.students()) }.collectAsStateWithLifecycle(emptyList())
    val students = allStudents.filter { it.current }
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val rooms by db.library().rooms().collectAsStateWithLifecycle(emptyList())
    val feesMonth by remember(monthStart) { db.students().paidBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val libMonth by remember(monthStart) { db.library().collectedBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val feesYear by remember(yearStart) { db.students().paidBetween(yearStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val libYear by remember(yearStart) { db.library().collectedBetween(yearStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val recentFees by db.students().recentPaid().collectAsStateWithLifecycle(emptyList())
    val recentLib by db.library().recentPayments().collectAsStateWithLifecycle(emptyList())

    val t = today()
    val active = members.filter { it.active }
    val seats = allSeats(rooms, AppSettings.seatCount)
    val free = seats.count { n -> active.none { it.seatNo == n } }
    val openEnq = enquiries.filter { it.status == "NEW" || it.status == "CONTACTED" }

    val dues = active.filter { it.validTill < t || it.balance > 0 }.sortedBy { it.validTill }.map {
        val late = t - it.validTill
        Line(it.name, if (it.validTill < t) "Expired ${if (late == 1L) "yesterday" else "$late days ago"}" + (if (it.balance > 0) " · ${rupees(it.balance)} balance" else "")
            else "${rupees(it.balance)} still to pay", if (it.validTill < t) Brand.Red600 else Brand.Saffron700, it.photo, Go.Member, it.id, it.mobile, libraryReminder(it, t))
    }
    val ending = active.filter { it.validTill in t..(t + ENDING_DAYS) && it.balance == 0L }.sortedBy { it.validTill }.map {
        val left = it.validTill - t
        Line(it.name, when (left) { 0L -> "Ends today"; 1L -> "1 day remaining"; else -> "$left days remaining" }, Brand.Saffron700, it.photo, Go.Member, it.id, it.mobile, libraryReminder(it, t))
    }
    val studentDue = students.filter { it.nextDue != null && it.nextDue <= t + 7 }.sortedBy { it.nextDue }.map {
        val d = it.nextDue!!
        Line(it.name, if (d < t) "Overdue since ${shortDate(d)}" else "Due ${whenText(d)}", if (d < t) Brand.Red600 else Brand.Slate500, it.photo, Go.Student, it.id,
            it.mobile, feeMessage(true, it.name, it.nextDueAmount, d), it.nextDueAmount)
    }
    val followUps = openEnq.filter { it.followUpOn <= t }.sortedBy { it.followUpOn }.map {
        Line(it.name, (if (it.followUpOn < t) "Follow-up was ${shortDate(it.followUpOn)}" else "Follow up today") + if (it.course.isNotBlank()) " · ${it.course}" else "",
            if (it.followUpOn < t) Brand.Red600 else Brand.Saffron700, "", Go.Enquiry, it.id)
    }
    val joinedToday = active.filter { it.joinedOn == t }
    val recent = (recentFees + recentLib).sortedByDescending { it.paidOn }.take(5)
    val birthdays = students.filter { isBirthday(it.dob, now) }.map { Triple(it.name, it.mobile, "Student · ${it.course}") to it.photo } +
        active.filter { isBirthday(it.dob, now) }.map { Triple(it.name, it.mobile, "Library · seat ${it.seatNo}") to it.photo }

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().heroBackground().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 46.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoBadge(44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(AppSettings.academyName, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${greeting()} · ${now.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))}", style = MaterialTheme.typography.bodySmall, color = Brand.Blue200, maxLines = 1)
                }
                GlassIconButton(Icons.Rounded.Search, "Search", { onGo(Go.Search, 0) })
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Rounded.Settings, "Settings", { onGo(Go.Settings, 0) })
            }
            Spacer(Modifier.height(18.dp))
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Here's what is happening today.", style = MaterialTheme.typography.bodyMedium, color = Brand.Blue200)
        }

        Column(Modifier.offset(y = (-30).dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Library members", "${active.size}", Icons.Rounded.Groups, Brand.Blue600, Modifier.weight(1f)) { onGo(Go.Library, 0) }
                Stat("Free / total seats", "$free / ${seats.size}", Icons.Rounded.EventSeat, Brand.Green600, Modifier.weight(1f)) { onGo(Go.Seats, 0) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Income ${now.format(DateTimeFormatter.ofPattern("MMMM"))}", rupees(feesMonth + libMonth), Icons.Rounded.CurrencyRupee, Brand.Saffron600, Modifier.weight(1f)) { onGo(Go.Money, 0) }
                Stat("Income this year", rupees(feesYear + libYear), Icons.Rounded.Savings, Brand.Violet700, Modifier.weight(1f)) { onGo(Go.Money, 0) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Students", "${students.size}", Icons.Rounded.School, Brand.Teal700, Modifier.weight(1f)) { onGo(Go.Students, 0) }
                Stat("Open enquiries", "${openEnq.size}", Icons.Rounded.Forum, Brand.Saffron700, Modifier.weight(1f)) { onGo(Go.Enquiries, 0) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("Add member", Icons.Rounded.PersonAdd, Modifier.weight(1f)) { onGo(Go.NewMember, 0) }
                QuickAction("Add student", Icons.Rounded.School, Modifier.weight(1f)) { onGo(Go.NewStudent, 0) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("New enquiry", Icons.Rounded.AddComment, Modifier.weight(1f)) { onGo(Go.NewEnquiry, 0) }
                QuickAction("Add expense", Icons.Rounded.ReceiptLong, Modifier.weight(1f)) { onGo(Go.NewExpense, 0) }
            }

            if (birthdays.isNotEmpty()) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFF1F2), Brand.Saffron50)))
                    .border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Birthday today 🎂", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                birthdays.forEach { (b, photo) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(b.first, 40.dp, photo = photo)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.first, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                            Text(b.third, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        }
                        PillButton("Wish", { Contact.whatsapp(ctx, b.second, birthdayWish(b.first)) }, Brand.WhatsAppDark, Icons.AutoMirrored.Rounded.Chat)
                    }
                }
            }

            DashCard("Payment dues", "${dues.size} member${if (dues.size == 1) "" else "s"} pending", Brand.Red600, dues, "View all dues", "No library fees pending ✓",
                onAll = { onGo(Go.MemberDues, 0) }, onGo = onGo)
            DashCard("Ending soon", "${ending.size} member${if (ending.size == 1) "" else "s"} in the next $ENDING_DAYS days", Brand.Saffron700, ending, "View all", "No plans ending this week",
                onAll = { onGo(Go.MemberEnding, 0) }, onGo = onGo)
            DashCard("Student fees", "${studentDue.size} due in the next 7 days", Brand.Blue700, studentDue, "View students", "No student fees due this week",
                onAll = { onGo(Go.Students, 0) }, onGo = onGo)
            if (followUps.isNotEmpty()) DashCard("Enquiry follow-ups", "${followUps.size} to call today", Brand.Teal700, followUps, "View enquiries", "",
                onAll = { onGo(Go.Enquiries, 0) }, onGo = onGo)
            DashCard("Recent payments", "${recent.size} latest", Brand.Green700, recent.map { it.toLine() }, "View all payments", "No payments yet",
                onAll = { onGo(Go.Money, 0) }, onGo = onGo)
            DashCard("Today's admissions", "${joinedToday.size} new member${if (joinedToday.size == 1) "" else "s"} today", Brand.Violet700,
                joinedToday.map { Line(it.name, "Seat ${it.seatNo}" + if (it.planName.isNotBlank()) " · ${it.planName}" else "", Brand.Slate500, it.photo, Go.Member, it.id) },
                "View members", "Nobody joined today", onAll = { onGo(Go.Library, 0) }, onGo = onGo)
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun PaymentRow.toLine(): Line {
    val label = when (kind) { "STUDENT_FEE" -> "Course fee"; "FEE" -> "Library fee"; "DEPOSIT" -> "Deposit"; "REGISTRATION" -> "Registration"; else -> "Deposit returned" }
    val out = kind == "DEPOSIT_RETURN"
    return Line(name, "$label · ${relativeDay(paidOn)}", Brand.Slate500, "", Go.Money, 0, amount = if (out) -amount else amount)
}

@Composable
private fun Stat(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.softCard(RoundedCornerShape(22.dp), 8.dp).clip(RoundedCornerShape(22.dp)).pressable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Slate500, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(21.dp)) }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(50)).pressable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Brand.Blue700, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Brand.Ink, maxLines = 1)
    }
}

/** A dashboard card: coloured title, up to four lines, and one "view all" link. */
@Composable
private fun DashCard(title: String, subtitle: String, color: Color, lines: List<Line>, allLabel: String, emptyText: String, onAll: () -> Unit, onGo: (Go, Long) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).clip(RoundedCornerShape(24.dp))) {
        Column(Modifier.fillMaxWidth().background(color.copy(alpha = 0.07f)).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = color)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
        }
        if (lines.isEmpty()) Text(emptyText, Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
        lines.take(4).forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = Brand.Line)
            Row(Modifier.fillMaxWidth().clickable(enabled = l.go != Go.Money) { onGo(l.go, l.id) }.padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Avatar(l.name, 42.dp, photo = l.photo)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.detail, style = MaterialTheme.typography.bodySmall, color = l.detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                l.amount?.let { a ->
                    Text((if (a < 0) "− " else "") + rupees(kotlin.math.abs(a)), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (a < 0) Brand.Red600 else Brand.Green700)
                }
                if (l.message.isNotBlank() && l.mobile.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    SmallIconButton(Icons.AutoMirrored.Rounded.Chat, "Send reminder to ${l.name}", Brand.WhatsAppDark) { Contact.whatsapp(ctx, l.mobile, l.message) }
                }
            }
        }
        if (lines.isNotEmpty() || emptyText.isEmpty()) Row(Modifier.fillMaxWidth().clickable(onClick = onAll).padding(14.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (lines.size > 4) "$allLabel (${lines.size})" else allLabel, style = MaterialTheme.typography.labelLarge, color = Brand.Blue700)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Brand.Blue700, modifier = Modifier.size(16.dp))
        }
    }
}
