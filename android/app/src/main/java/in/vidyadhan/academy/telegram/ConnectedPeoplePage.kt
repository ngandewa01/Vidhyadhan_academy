package `in`.vidyadhan.academy.telegram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** One row: a person (key s12 / m3 / e5), a group, or the owner. [keys] are their linked keys; empty = not connected. */
private data class Linked(val id: String, val kind: String, val name: String, val detail: String, val mobile: String,
                          val keys: List<String>, val canRemove: Boolean = true) {
    val connected get() = keys.isNotEmpty()
}

/** Everyone who can get Telegram messages: connected or not, with search, filters, Connect and Disconnect. */
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
    var connect by remember { mutableStateOf<List<Linked>>(emptyList()) } // queue; the first one is shown
    var connectTotal by remember { mutableIntStateOf(0) }
    BackHandler { when { confirm != null -> confirm = null; connect.isNotEmpty() -> connect = emptyList(); else -> onBack() } }
    HideBottomNav()

    val all = remember(students, members, enquiries, version) {
        val linked = TelegramBot.linkedKeys().toSet()
        val used = mutableSetOf<String>()
        fun Linked.mark() = also { used += it.keys }
        buildList {
            if (AppSettings.botOwnerChat != 0L) add(Linked("owner", "owner", "You (owner)", "Gets tests and the daily summary · /owner", "", listOf("owner"), canRemove = false))
            // Current course students; a combo student's Abhyasika link counts for them too.
            students.filter { it.leftOn == null || "s${it.id}" in linked }.forEach { s ->
                val keys = (listOf("s${s.id}") + members.filter { it.studentId == s.id }.map { "m${it.id}" }).filter { it in linked }
                val what = "${s.course}" + if (s.batch.isNotBlank()) " ${s.batch}" else ""
                add(Linked("s${s.id}", "s", s.name, (if (s.leftOn != null) "Former student · " else "") + what, s.mobile, keys).mark())
            }
            members.filter { it.studentId == null && ((it.active && it.leftOn == null) || "m${it.id}" in linked) }.forEach { m ->
                val k = "m${m.id}"
                add(Linked(k, "m", m.name, "Seat ${m.seatNo}", m.mobile, listOf(k).filter { it in linked }).mark())
            }
            members.filter { it.studentId != null && "m${it.id}" in linked && "m${it.id}" !in used }.forEach { m ->
                add(Linked("m${m.id}", "m", m.name, "Seat ${m.seatNo}", m.mobile, listOf("m${m.id}")).mark())
            }
            (linked - used).sorted().forEach { k ->
                val id = k.drop(1).toLongOrNull()
                if (k.startsWith("e")) enquiries.firstOrNull { it.id == id }.let { e ->
                    add(Linked(k, "e", e?.name ?: "Enquiry (removed)", "Enquiry" + (e?.course?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), e?.mobile.orEmpty(), listOf(k)))
                } else add(Linked(k, if (k.startsWith("m")) "m" else "s", if (k.startsWith("m")) "Member (removed)" else "Student (removed)", "No longer in the app", "", listOf(k)))
            }
            add(Linked("g_students", "g", "Students' group", "Broadcasts and news · /students", "", if (AppSettings.botStudentsChat != 0L) listOf("g") else emptyList()))
            add(Linked("g_staff", "g", "Staff group", "Follow-up digest · /staff", "", if (AppSettings.botStaffChat != 0L) listOf("g") else emptyList()))
        }
    }
    val people = all.filter { it.kind != "owner" }
    fun match(p: Linked, f: String) = when (f) {
        "c" -> p.connected; "u" -> !p.connected; "s" -> p.kind == "s"; "m" -> p.kind == "m"; "g" -> p.kind == "g"; else -> true
    }
    val shown = all.filter { match(it, filter) && (q.isBlank() || it.name.contains(q, true) || it.mobile.contains(q)) }
        .sortedWith(compareBy({ listOf("owner", "s", "m", "e", "g").indexOf(it.kind) }, { it.name.lowercase() }))
    val notConnected = shown.filter { !it.connected && it.kind != "g" }
    val on = all.count { it.connected }

    HeroPage("Telegram people", "$on of ${all.size} connected · new ones every 15 minutes", onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            SearchBox(q, { q = it }, "Search name or mobile", Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "All ${all.size}", "c" to "Connected $on", "u" to "Not connected ${people.count { !it.connected }}",
                    "s" to "Students ${people.count { it.kind == "s" }}", "m" to "Abhyasika ${people.count { it.kind == "m" }}", "g" to "Groups 2").forEach { (k, l) ->
                    val sel = filter == k
                    Text(l, Modifier.clip(RoundedCornerShape(50)).background(if (sel) Brand.Blue700 else Color.White).border(1.dp, if (sel) Brand.Blue700 else Brand.Line, RoundedCornerShape(50))
                        .clickable { filter = k }.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else Brand.Slate600, maxLines = 1)
                }
            }
            if (shown.isEmpty()) EmptyState(Icons.Rounded.Groups, "No one here", "Try a different name or filter.")
            else LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filter == "u" && notConnected.size > 1) item("bulk") {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sky50).border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(18.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${notConnected.size} not connected", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink)
                            Text("Send them the join link on WhatsApp, one by one", style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
                        }
                        Spacer(Modifier.width(8.dp))
                        Pill("Send links", Sky700, Color.White) { connect = notConnected; connectTotal = notConnected.size }
                    }
                }
                items(shown, key = { it.id }) { p ->
                    PersonRow(p, onDisconnect = { confirm = p }, onConnect = { connect = listOf(p); connectTotal = 1 })
                }
            }
        }
    }
    confirm?.let { p ->
        ConfirmDialog("Disconnect ${p.name}?",
            if (p.kind == "g") "The bot will stop posting in this group. Send the command there again to connect it." else "They will stop getting receipts, reminders and wishes on Telegram. You can send them the link again anytime.",
            "Disconnect", danger = true,
            onConfirm = {
                when (p.id) { "g_students" -> AppSettings.botStudentsChat = 0L; "g_staff" -> AppSettings.botStaffChat = 0L; else -> p.keys.forEach { TelegramBot.unlink(it) } }
                version++; confirm = null
            }, onDismiss = { confirm = null })
    }
    connect.firstOrNull()?.let { p ->
        ConnectSheet(p, if (connectTotal > 1) "${connectTotal - connect.size + 1} of $connectTotal" else null,
            onNext = { connect = connect.drop(1) }, onDismiss = { connect = emptyList() })
    }
}

private val Sky50 = Color(0xFFE0F2FE)
private val Sky700 = Color(0xFF0284C7)

@Composable
private fun Pill(text: String, bg: Color, fg: Color, border: Color = bg, onClick: () -> Unit) =
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, border, RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)

/** How to connect one person (their own link on WhatsApp) or a group (add the bot, send the command). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectSheet(p: Linked, step: String?, onNext: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step != null) Text(step, style = MaterialTheme.typography.labelMedium, color = Sky700)
            if (p.kind == "g") {
                val cmd = if (p.id == "g_staff") "/staff" else "/students"
                Text("Connect the ${p.name.lowercase()}", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                Text("1. Add @${AppSettings.botName} to the Telegram group.\n2. In the group, send $cmd.\nIt shows here as Connected within 15 minutes.",
                    style = MaterialTheme.typography.bodyMedium, color = Brand.Slate600)
                PrimaryButton("Open Telegram", { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(TelegramBot.link))) } }, Modifier.fillMaxWidth())
                SoftButton("Close", onDismiss, Modifier.fillMaxWidth(), color = Brand.Slate600)
                return@Column
            }
            val msg = TelegramBot.invite(p.id)
            val mobile = p.mobile.filter { it.isDigit() }.takeLast(10)
            Text("Connect ${p.name}", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
            Text("Telegram only lets a person join by tapping the bot link themselves. Send them their own link:", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
            Text(msg, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Green50).padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = Brand.Ink)
            if (mobile.length == 10) PrimaryButton("Send on WhatsApp", { Contact.whatsapp(ctx, mobile, msg); onNext() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Send)
            else Text("No mobile number saved: copy the link and send it another way.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            SoftButton("Copy link", {
                ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("link", msg))
                android.widget.Toast.makeText(ctx, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth(), color = Sky700, icon = Icons.Rounded.ContentCopy)
            if (step != null) SoftButton("Skip", onNext, Modifier.fillMaxWidth(), color = Brand.Slate600)
            SoftButton(if (step != null) "Stop" else "Cancel", onDismiss, Modifier.fillMaxWidth(), color = Brand.Slate600)
            Text("They show as Connected here within 15 minutes of tapping it.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
    }
}

@Composable
private fun PersonRow(p: Linked, onDisconnect: () -> Unit, onConnect: () -> Unit) {
    val (bg, fg) = when {
        !p.connected && p.kind != "g" -> Color(0xFFF1F5F9) to Brand.Slate500
        p.kind == "owner" -> Color(0xFFF3E8FF) to Color(0xFF6D28D9); p.kind == "m" -> Brand.Green50 to Brand.Green700
        p.kind == "g" -> Sky50 to Sky700; else -> Brand.Blue100 to Brand.Blue700
    }
    val icon: ImageVector? = when { p.kind == "owner" -> Icons.Rounded.Star; p.kind == "g" -> Icons.Rounded.Groups; p.name.endsWith("(removed)") -> Icons.Rounded.PersonOff; else -> null }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(bg), Alignment.Center) {
                if (icon != null) Icon(icon, null, tint = fg)
                else Text(p.name.split(" ").filter { it.firstOrNull()?.isLetter() == true }.take(2).joinToString("") { it.take(1).uppercase() }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = fg)
            }
            if (p.kind != "owner") Box(Modifier.align(Alignment.BottomEnd).size(15.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(2.5.dp)
                .clip(RoundedCornerShape(50)).background(if (p.connected) Sky700 else Color(0xFFCBD5E1)))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = if (p.kind == "owner") "" else if (p.connected) "Connected · " else "Not connected · "
            Text(androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = if (p.connected) Sky700 else Brand.Slate500, fontWeight = if (p.connected) FontWeight.Bold else null)); append(status); pop()
                append(p.detail)
            }, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (p.canRemove) {
            Spacer(Modifier.width(8.dp))
            if (p.connected) Pill("Disconnect", Color.White, Brand.Red600, Color(0xFFFECACA), onDisconnect)
            else Pill("Connect", Sky700, Color.White, onClick = onConnect)
        }
    }
}
