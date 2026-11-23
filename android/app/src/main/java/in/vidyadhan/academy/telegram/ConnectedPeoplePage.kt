package `in`.vidyadhan.academy.telegram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.ui.*

/** One connected chat: a person (key s12 / m3 / e5), a group, or the owner. */
private data class Linked(val id: String, val kind: String, val name: String, val detail: String, val mobile: String, val canRemove: Boolean = true)

/** Everyone connected to the bot, with search, filters and Disconnect. */
@Composable
fun ConnectedPeoplePage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val students by db.students().students().collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    var version by remember { mutableIntStateOf(0) } // bumped after a disconnect
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var confirm by remember { mutableStateOf<Linked?>(null) }
    BackHandler { if (confirm != null) confirm = null else onBack() }
    HideBottomNav()

    val all = remember(students, members, enquiries, version) {
        buildList {
            if (AppSettings.botOwnerChat != 0L) add(Linked("owner", "owner", "You (owner)", "Gets tests and the daily summary · /owner", "", canRemove = false))
            TelegramBot.linkedKeys().sortedBy { it }.forEach { k ->
                val id = k.drop(1).toLongOrNull()
                when (k.firstOrNull()) {
                    's' -> students.firstOrNull { it.id == id }.let { s ->
                        add(Linked(k, "s", s?.name ?: "Student (removed)", s?.let { "Student · ${it.course}" + if (it.batch.isNotBlank()) " ${it.batch}" else "" } ?: "No longer in the app", s?.mobile.orEmpty()))
                    }
                    'm' -> members.firstOrNull { it.id == id }.let { m ->
                        add(Linked(k, "m", m?.name ?: "Member (removed)", m?.let { "Abhyasika · seat ${it.seatNo}" } ?: "No longer in the app", m?.mobile.orEmpty()))
                    }
                    'e' -> enquiries.firstOrNull { it.id == id }.let { e ->
                        add(Linked(k, "e", e?.name ?: "Enquiry (removed)", "Enquiry" + (e?.course?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), e?.mobile.orEmpty()))
                    }
                }
            }
            if (AppSettings.botStudentsChat != 0L) add(Linked("g_students", "g", "Students' group", "Broadcasts and news · /students", ""))
            if (AppSettings.botStaffChat != 0L) add(Linked("g_staff", "g", "Staff group", "Follow-up digest · /staff", ""))
        }
    }
    fun count(k: String) = all.count { it.kind == k }
    val shown = all.filter { (filter == "all" || it.kind == filter) && (q.isBlank() || it.name.contains(q, true) || it.mobile.contains(q)) }

    HeroPage("Connected people", "${all.size} on Telegram · new ones every 15 minutes", onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            SearchBox(q, { q = it }, "Search name or mobile", Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp))
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "All ${all.size}", "s" to "Students ${count("s")}", "m" to "Abhyasika ${count("m")}", "g" to "Groups ${count("g")}").forEach { (k, l) ->
                    val on = filter == k
                    Text(l, Modifier.clip(RoundedCornerShape(50)).background(if (on) Brand.Blue700 else Color.White).border(1.dp, if (on) Brand.Blue700 else Brand.Line, RoundedCornerShape(50))
                        .clickable { filter = k }.padding(horizontal = 11.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Brand.Slate600, maxLines = 1)
                }
            }
            if (shown.isEmpty()) EmptyState(Icons.Rounded.Groups, "No one here", if (all.isEmpty()) "Share the bot link: people connect when they press Start." else "Try a different name or filter.")
            else LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { p -> PersonRow(p) { confirm = p } }
            }
        }
    }
    confirm?.let { p ->
        ConfirmDialog("Disconnect ${p.name}?",
            if (p.kind == "g") "The bot will stop posting in this group. Send the command there again to connect it." else "They will stop getting receipts, reminders and wishes on Telegram. They can connect again with the join link.",
            "Disconnect", danger = true,
            onConfirm = {
                when (p.id) { "g_students" -> AppSettings.botStudentsChat = 0L; "g_staff" -> AppSettings.botStaffChat = 0L; else -> TelegramBot.unlink(p.id) }
                version++; confirm = null
            }, onDismiss = { confirm = null })
    }
}

@Composable
private fun PersonRow(p: Linked, onDisconnect: () -> Unit) {
    val (bg, fg) = when (p.kind) { "owner" -> Color(0xFFF3E8FF) to Color(0xFF6D28D9); "m" -> Brand.Green50 to Brand.Green700; "g" -> Color(0xFFE0F2FE) to Color(0xFF0284C7); else -> Brand.Blue100 to Brand.Blue700 }
    val icon: ImageVector? = when { p.kind == "owner" -> Icons.Rounded.Star; p.kind == "g" -> Icons.Rounded.Groups; p.name.endsWith("(removed)") -> Icons.Rounded.PersonOff; else -> null }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(bg), Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = fg)
            else Text(p.name.split(" ").filter { it.firstOrNull()?.isLetter() == true }.take(2).joinToString("") { it.take(1).uppercase() }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = fg)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.detail, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 2)
        }
        if (p.canRemove) {
            Spacer(Modifier.width(8.dp))
            Text("Disconnect", Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0xFFFECACA), RoundedCornerShape(50)).clickable(onClick = onDisconnect)
                .padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Red600, maxLines = 1)
        }
    }
}
