package `in`.vidyadhan.academy.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.R
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeTab(onGo: (Int) -> Unit) {
    val db = AppDb.get(LocalContext.current)
    val students by db.students().listWithFees().collectAsStateWithLifecycle(emptyList())
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val t = today()
    val pending = students.sumOf { it.totalFee - it.paid }
    val dueStudents = students.filter { it.nextDue != null && it.nextDue <= t && it.totalFee > it.paid }
    val dueEnq = enquiries.filter { (it.status == "NEW" || it.status == "CONTACTED") && it.followUpOn <= t }
    val active = members.filter { it.active }
    val dueLib = active.filter { it.validTill <= t + 3 }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(HeaderBrush).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 56.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(56.dp).clip(CircleShape).background(Color.White).padding(3.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("नमस्कार 🙏", color = Brand.Blue100, style = MaterialTheme.typography.bodyMedium)
                    Text(AppSettings.academyName, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy")), color = Brand.Blue100, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Column(Modifier.offset(y = (-36).dp).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Filled.School, "Students", "${students.size}", Brand.Blue700, Modifier.weight(1f)) { onGo(1) }
                StatTile(Icons.Filled.CurrencyRupee, "Fees pending", rupees(pending), Brand.Saffron700, Modifier.weight(1f)) { onGo(1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Filled.QuestionAnswer, "Open enquiries", "${enquiries.count { it.status == "NEW" || it.status == "CONTACTED" }}", Brand.Blue500, Modifier.weight(1f)) { onGo(2) }
                StatTile(Icons.Filled.EventSeat, "Library seats", "${active.size}/${AppSettings.seatCount}", Brand.Green700, Modifier.weight(1f)) { onGo(3) }
            }
            SectionTitle("Today")
            if (dueStudents.isEmpty() && dueEnq.isEmpty() && dueLib.isEmpty()) {
                AppCard { Text("All clear — nothing needs your attention today. 🎉", color = Brand.Slate500) }
            }
            if (dueEnq.isNotEmpty()) TodayCard("Enquiries to follow up", Icons.Filled.QuestionAnswer, dueEnq.map { it.name to it.mobile }) { onGo(2) }
            if (dueStudents.isNotEmpty()) TodayCard("Fees due", Icons.Filled.CurrencyRupee,
                dueStudents.map { it.name to ("${rupees(it.totalFee - it.paid)} pending · since ${it.nextDue!!.asDate()}") }) { onGo(1) }
            if (dueLib.isNotEmpty()) TodayCard("Library renewals (next 3 days)", Icons.Filled.EventSeat,
                dueLib.map { it.name to "Seat ${it.seatNo} · ${if (it.validTill < t) "overdue" else "ends " + it.validTill.asDate()}" }) { onGo(3) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TodayCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, rows: List<Pair<String, String>>, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Brand.Saffron700); Spacer(Modifier.width(8.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            WarnPill("${rows.size}")
        }
        Spacer(Modifier.height(6.dp))
        rows.take(4).forEach { (a, b) ->
            Row(Modifier.padding(vertical = 3.dp)) { Text(a, Modifier.weight(1f), fontWeight = FontWeight.Medium); Text(b, color = Brand.Slate500, style = MaterialTheme.typography.bodySmall) }
        }
        if (rows.size > 4) Text("+ ${rows.size - 4} more", color = Brand.Blue700, style = MaterialTheme.typography.bodySmall)
    }
}
