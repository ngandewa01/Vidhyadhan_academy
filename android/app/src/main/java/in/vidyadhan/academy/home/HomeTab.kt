package `in`.vidyadhan.academy.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
enum class Go { Students, NewStudent, Enquiries, NewEnquiry, Library, NewMember, Settings, Backup }

private data class Agenda(val name: String, val detail: String, val icon: ImageVector, val color: Color, val tone: Tone, val chip: String, val go: Go)

@Composable
fun HomeTab(onGo: (Go) -> Unit) {
    val db = AppDb.get(LocalContext.current)
    val now = LocalDate.now()
    val monthStart = now.withDayOfMonth(1).toEpochDay()
    val monthEnd = now.withDayOfMonth(now.lengthOfMonth()).toEpochDay()
    val students by db.students().listWithFees().collectAsStateWithLifecycle(emptyList())
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val feesMonth by remember(monthStart) { db.students().paidBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val libMonth by remember(monthStart) { db.library().collectedBetween(monthStart, monthEnd) }.collectAsStateWithLifecycle(0L)
    val recentFees by db.students().recentPaid().collectAsStateWithLifecycle(emptyList())
    val recentLib by db.library().recentPayments().collectAsStateWithLifecycle(emptyList())

    val t = today()
    val pending = students.sumOf { (it.totalFee - it.paid).coerceAtLeast(0) }
    val collected = feesMonth + libMonth
    val overdue = students.filter { it.nextDue != null && it.nextDue < t && it.totalFee > it.paid }
    val openEnq = enquiries.filter { it.status == "NEW" || it.status == "CONTACTED" }
    val active = members.filter { it.active }
    val seats = AppSettings.seatCount

    val agenda = buildList {
        openEnq.filter { it.followUpOn <= t }.forEach {
            add(Agenda(it.name, "Enquiry · ${it.course.ifBlank { it.mobile }}", Icons.Rounded.Forum, Brand.Saffron600,
                if (it.followUpOn < t) Tone.Bad else Tone.Accent, if (it.followUpOn < t) "Overdue" else "Follow up", Go.Enquiries))
        }
        students.filter { it.nextDue != null && it.nextDue <= t && it.totalFee > it.paid }.forEach {
            add(Agenda(it.name, "${rupees(it.totalFee - it.paid)} pending", Icons.Rounded.CurrencyRupee, Brand.Red600,
                if (it.nextDue!! < t) Tone.Bad else Tone.Warn, if (it.nextDue < t) "Fee overdue" else "Fee due", Go.Students))
        }
        active.filter { it.validTill <= t + 3 }.forEach {
            add(Agenda(it.name, "Seat ${it.seatNo} · ${if (it.validTill < t) "ended " else "ends "}${relativeDay(it.validTill).lowercase()}", Icons.Rounded.EventSeat,
                Brand.Teal700, if (it.validTill < t) Tone.Bad else Tone.Warn, "Renewal", Go.Library))
        }
    }
    val recent = (recentFees + recentLib).sortedByDescending { it.paidOn }.take(5)

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState())) {
        // ── hero ──
        Column(Modifier.fillMaxWidth().heroBackground().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 74.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoBadge(46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${greeting()} 👋", style = MaterialTheme.typography.bodyMedium, color = Brand.Blue200)
                    Text(AppSettings.academyName, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                }
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEE, dd MMM")),
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium, color = Color.White,
                )
            }
            Spacer(Modifier.height(26.dp))
            Text("Collected in ${now.format(DateTimeFormatter.ofPattern("MMMM"))}", style = MaterialTheme.typography.labelLarge, color = Brand.Blue200)
            Text(rupees(collected), style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp), color = Color.White)
            Spacer(Modifier.height(10.dp))
            val total = collected + pending
            ProgressBar(if (total == 0L) 0f else collected.toFloat() / total, color = Brand.Saffron400, track = Color.White.copy(alpha = 0.16f), height = 8.dp)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("Student fees ${rupees(feesMonth)} · Library ${rupees(libMonth)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Brand.Blue200)
                Text("${rupees(pending)} to collect", style = MaterialTheme.typography.labelMedium, color = Brand.Saffron200)
            }
        }

        Column(Modifier.offset(y = (-52).dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ── quick actions ──
            Row(
                Modifier.fillMaxWidth().softCard(RoundedCornerShape(26.dp), 14.dp).padding(vertical = 14.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RoundAction(Icons.Rounded.PersonAdd, "Student", Brand.Blue600, { onGo(Go.NewStudent) })
                RoundAction(Icons.Rounded.AddComment, "Enquiry", Brand.Saffron600, { onGo(Go.NewEnquiry) })
                RoundAction(Icons.Rounded.EventSeat, "Library", Brand.Teal700, { onGo(Go.NewMember) })
                RoundAction(Icons.Rounded.CloudUpload, "Backup", Brand.Violet700, { onGo(Go.Backup) })
            }
            // ── metrics ──
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricCard(Icons.Rounded.School, "Students", "${students.size}", Brand.Blue600, Modifier.weight(1f),
                    sub = if (overdue.isEmpty()) "All on time" else "${overdue.size} overdue", subColor = if (overdue.isEmpty()) Brand.Green700 else Brand.Red600) { onGo(Go.Students) }
                MetricCard(Icons.Rounded.Forum, "Open enquiries", "${openEnq.size}", Brand.Saffron600, Modifier.weight(1f),
                    sub = "${openEnq.count { it.followUpOn <= t }} due today") { onGo(Go.Enquiries) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricCard(Icons.Rounded.EventSeat, "Seats filled", "${active.size}/$seats", Brand.Teal700, Modifier.weight(1f),
                    sub = "${(seats - active.size).coerceAtLeast(0)} free") { onGo(Go.Library) }
                MetricCard(Icons.Rounded.AccountBalanceWallet, "Fees pending", rupees(pending), Brand.Violet700, Modifier.weight(1f),
                    sub = "${students.count { it.totalFee > it.paid }} students") { onGo(Go.Students) }
            }

            // ── agenda ──
            SectionHeader("Today's to-do", action = if (agenda.size > 5) "${agenda.size} items" else null)
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
            } else Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                agenda.take(6).forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = Brand.Line)
                    Row(Modifier.fillMaxWidth().pressable { onGo(a.go) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(a.icon, a.color, 42.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1)
                            Text(a.detail, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
                        }
                        StatusChip(a.chip, a.tone)
                    }
                }
            }

            // ── recent payments ──
            if (recent.isNotEmpty()) {
                SectionHeader("Recent payments")
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                    recent.forEachIndexed { i, p -> if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = Brand.Line); PaymentLine(p) }
                }
            }
            Spacer(Modifier.height(4.dp))
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
