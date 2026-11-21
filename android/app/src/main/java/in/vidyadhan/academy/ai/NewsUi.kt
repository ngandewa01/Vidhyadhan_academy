package `in`.vidyadhan.academy.ai

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.telegram.TelegramBot
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch

private val TgBlue = Color(0xFF229ED9)
private fun typeColors(t: String): Pair<Color, Color> = when (t) {
    "Advertisement" -> Color(0xFFFDF0E6) to Color(0xFF9A3412)
    "Result" -> Color(0xFFDCFCE7) to Color(0xFF166534)
    "Answer key" -> Color(0xFFEDE9FE) to Color(0xFF5B21B6)
    "Timetable" -> Color(0xFFE0F2F5) to Brand.Blue700
    "Admit card" -> Color(0xFFE0E7FF) to Color(0xFF3730A3)
    else -> Brand.Line to Brand.Slate600
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = fg, maxLines = 1)
}

@Composable
private fun CiteMark(n: Int) {
    Text("$n", Modifier.padding(start = 3.dp).clip(RoundedCornerShape(5.dp)).background(Brand.Blue50).padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold), color = Brand.Blue600)
}

/** Dashboard card: new MPSC news and today's current affairs. */
@Composable
fun NewsCard(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { News.load(ctx) }
    val all by News.items.collectAsStateWithLifecycle()
    val news = all.filter { it.kind == News.NEWS }
    val unread = news.filter { !it.read }
    val todayCa = all.count { it.kind == News.CA && it.day == today() }
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 6.dp).clickable { onOpen(News.NEWS) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Campaign, null, tint = Brand.Saffron600, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
            Text("MPSC News", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
            if (unread.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Tag("${unread.size} new", Brand.Red600, Color.White) }
            Spacer(Modifier.weight(1f))
            Tag("✓ cited sources", Color(0xFFDCFCE7), Color(0xFF166534))
        }
        when {
            AppSettings.claudeKey.isBlank() -> Text("Turn on AI news: add your Claude key in Settings → MPSC News.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            news.isEmpty() -> Text("No MPSC announcements found yet. It checks twice a day.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            else -> (unread.ifEmpty { news }).take(2).forEach { n ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (bg, fg) = typeColors(n.type); Tag(n.type, bg, fg); Spacer(Modifier.width(6.dp))
                        Text(n.day.asDate(), style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
                    }
                    Text(n.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Brand.Ink, maxLines = 2)
                    n.dates.firstOrNull()?.let { Text("${it.label}: ${it.value}", style = MaterialTheme.typography.bodySmall, color = Brand.Red600, maxLines = 1) }
                }
            }
        }
        if (todayCa > 0) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Slate50).clickable { onOpen(News.CA) }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("📰 आजच्या चालू घडामोडी · $todayCa points", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
            Icon(Icons.Rounded.ChevronRight, null, tint = Brand.Slate400)
        }
    }
}

/** MPSC News page: news, current affairs and the AI settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsPage(start: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(if (AppSettings.claudeKey.isBlank()) "SET" else start) }
    var filter by remember { mutableStateOf("All") }
    var open by remember { mutableStateOf<NewsItem?>(null) }
    var postText by remember { mutableStateOf<String?>(null) }
    val busy by News.busy.collectAsStateWithLifecycle()
    val status by News.status.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { News.pageOpen = true; onDispose { News.pageOpen = false } }
    val all by News.items.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    fun check(only: String? = null) = News.checkNow(ctx, only)
    HeroPage("MPSC News", "AI summaries · every line cites its source · MPSC only", onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(News.NEWS to "MPSC News", News.CA to "Current affairs", "SET" to "Settings").forEach { (k, l) -> Pill(l, tab == k) { tab = k } }
            }
            status?.let { StatusChip(it, if (it.endsWith("✓")) Tone.Good else if (busy) Tone.Info else Tone.Bad) }
            when (tab) {
                News.NEWS -> {
                    val news = all.filter { it.kind == News.NEWS }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("All") + News.TYPES).forEach { t -> Pill(t, filter == t, small = true) { filter = t } }
                    }
                    val shown = news.filter { filter == "All" || it.type == filter }
                    if (shown.isEmpty()) Text(if (AppSettings.claudeKey.isBlank()) "Add your Claude key in Settings to start." else "No announcements here yet. Checks run twice a day.",
                        style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    else Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 4.dp).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        shown.forEachIndexed { i, n ->
                            if (i > 0) HorizontalDivider(color = Brand.Line)
                            Column(Modifier.fillMaxWidth().clickable { open = n; if (!n.read) News.update(n.copy(read = true)) }.padding(vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (bg, fg) = typeColors(n.type); Tag(n.type, bg, fg)
                                    if (!n.read) { Spacer(Modifier.width(6.dp)); Tag("New", Brand.Red600, Color.White) }
                                    if (n.posted) { Spacer(Modifier.width(6.dp)); Tag("Posted", Brand.Blue50, Brand.Blue700) }
                                    Spacer(Modifier.weight(1f)); Text(n.day.asDate(), style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
                                }
                                Text(n.title, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                                Text(listOfNotNull(n.exam.ifBlank { null }, n.forWho.ifBlank { null }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                            }
                        }
                    }
                    if (AppSettings.claudeKey.isNotBlank()) SoftButton(if (busy) "Checking…" else "Check MPSC now", { if (!busy) check(News.NEWS) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh, enabled = !busy)
                }
                News.CA -> {
                    val byDay = all.filter { it.kind == News.CA }.groupBy { it.day }.toSortedMap(compareByDescending { it })
                    if (byDay.isEmpty()) Text(if (AppSettings.claudeKey.isBlank()) "Add your Claude key in Settings to start." else "Today's points are made automatically in the first check of the day.",
                        style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    if (AppSettings.claudeKey.isNotBlank()) SoftButton(if (busy) "Working…" else "Get current affairs now", { if (!busy) check(News.CA) }, Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Refresh, enabled = !busy)
                    byDay.entries.take(7).forEach { (day, pts) ->
                        Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 4.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (day == today()) "चालू घडामोडी · today" else "चालू घडामोडी · ${day.asDate()}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                                PillButton("Post", { postText = News.digestPost(pts, "mr") }, TgBlue, Icons.AutoMirrored.Rounded.Send)
                            }
                            val allCites = pts.flatMap { it.cites }.distinctBy { it.uri }
                            pts.forEachIndexed { i, p ->
                                Column(Modifier.fillMaxWidth().clickable { open = p }) {
                                    Row {
                                        Text("${i + 1}. ${p.mr.ifBlank { p.en }}", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, color = Brand.Ink)
                                        p.cites.forEach { c -> CiteMark(allCites.indexOfFirst { it.uri == c.uri } + 1) }
                                    }
                                    if (p.title.isNotBlank()) Tag(p.title, Color(0xFFFDF0E6), Color(0xFF9A3412))
                                }
                            }
                            Sources(allCites)
                        }
                    }
                }
                else -> NewsSettings(busy) { check() }
            }
            Text("AI summaries come only from the cited pages; a line without a trusted source is never shown. Still, check dates in the official notice before sharing.",
                style = MaterialTheme.typography.bodySmall, color = Brand.Slate400)
            Spacer(Modifier.height(20.dp))
        }
    }

    open?.let { n ->
        var lang by remember(n.id) { mutableStateOf("mr") }
        ModalBottomSheet({ open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (n.kind == News.NEWS) Row(verticalAlignment = Alignment.CenterVertically) { val (bg, fg) = typeColors(n.type); Tag(n.type, bg, fg); Spacer(Modifier.width(6.dp)); Text(n.day.asDate(), style = MaterialTheme.typography.labelSmall, color = Brand.Slate400) }
                Text(if (n.kind == News.NEWS) n.title else "चालू घडामोडी", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag("✨ AI summary", Color(0xFFEDE9FE), Color(0xFF6D28D9)); Spacer(Modifier.weight(1f))
                    if (n.mr.isNotBlank()) { Pill("मराठी", lang == "mr", small = true) { lang = "mr" }; Spacer(Modifier.width(4.dp)) }
                    Pill("English", lang == "en", small = true) { lang = "en" }
                }
                Row { Text(if (lang == "mr" && n.mr.isNotBlank()) n.mr else n.en, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge, color = Brand.Ink); CiteMark(1) }
                n.dates.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { d ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Brand.Slate50).padding(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text(d.value, style = MaterialTheme.typography.titleSmall, color = Brand.Ink); CiteMark(d.cite) }
                                Text(d.label, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Sources(n.cites)
                if (n.official) Tag("✓ From mpsc.gov.in", Color(0xFFDCFCE7), Color(0xFF166534))
                PrimaryButton("Prepare post for students' group", { postText = News.post(n, lang); open = null }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Send,
                    custom = androidx.compose.ui.graphics.Brush.linearGradient(listOf(TgBlue, Color(0xFF1C86BB))))
            }
        }
    }

    postText?.let { initial ->
        var text by remember(initial) { mutableStateOf(initial) }
        var sending by remember { mutableStateOf(false) }
        ModalBottomSheet({ postText = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetTitle("Post to students' group", "Check it and edit if needed. Nothing is sent until you tap Post.")
                Field(text, { text = it }, "Post", singleLine = false, minLines = 8)
                val chat = AppSettings.botStudentsChat
                when {
                    !TelegramBot.connected -> Text("Connect your Telegram bot first (Bot & More).", style = MaterialTheme.typography.bodySmall, color = Brand.Red600)
                    chat == 0L -> Text("Add your bot to the students' Telegram group and send /students in that group once. Then tap Bot & More → Refresh.",
                        style = MaterialTheme.typography.bodySmall, color = Brand.Amber700)
                    else -> PrimaryButton(if (sending) "Posting…" else "Post to students' group", {
                        sending = true
                        scope.launch {
                            val ok = TelegramBot.send(chat, TelegramBot.esc(text))
                            sending = false
                            android.widget.Toast.makeText(ctx, if (ok) "Posted in the students' group ✓" else "Could not post. Is the bot still in the group?", android.widget.Toast.LENGTH_LONG).show()
                            if (ok) { all.firstOrNull { initial.contains(it.title) && it.kind == News.NEWS }?.let { News.update(it.copy(posted = true)) }; postText = null }
                        }
                    }, Modifier.fillMaxWidth(), enabled = !sending && text.isNotBlank(), icon = Icons.AutoMirrored.Rounded.Send,
                        custom = androidx.compose.ui.graphics.Brush.linearGradient(listOf(TgBlue, Color(0xFF1C86BB))))
                }
                SoftButton("Share on WhatsApp / other apps", {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
            }
        }
    }
}

@Composable
private fun Sources(cites: List<Cite>) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Brand.Line, RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sources", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
        cites.forEachIndexed { i, c ->
            Text("[${i + 1}] ${c.domain.ifBlank { c.title }} ↗", Modifier.fillMaxWidth().clickable(enabled = c.uri.isNotBlank()) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, style = MaterialTheme.typography.bodySmall, color = Brand.Blue600)
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(if (selected) Brand.Blue700 else Color.White)
        .border(1.dp, if (selected) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 5.dp else 7.dp),
        style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Brand.Slate600, maxLines = 1)
}

/** The Claude key (locked: tap the row 10 times to edit) and what runs. */
@Composable
private fun NewsSettings(busy: Boolean, onCheck: () -> Unit) {
    ClaudeKeyCard(onSaved = onCheck)
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 4.dp).padding(horizontal = 14.dp, vertical = 4.dp)) {
        var a by remember { mutableStateOf(AppSettings.newsOn) }
        var b by remember { mutableStateOf(AppSettings.digestOn) }
        var c by remember { mutableStateOf(AppSettings.newsNotify) }
        Toggle("Check MPSC news", "Twice a day, when the phone is online", a) { a = it; AppSettings.newsOn = it }
        HorizontalDivider(color = Brand.Line)
        Toggle("Daily current affairs (Marathi)", "MPSC syllabus topics only, made in the first check of the day", b) { b = it; AppSettings.digestOn = it }
        HorizontalDivider(color = Brand.Line)
        Toggle("Notify me about new news", "Advertisements, results, answer keys, admit cards", c) { c = it; AppSettings.newsNotify = it }
        HorizontalDivider(color = Brand.Line)
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Students' Telegram group", style = MaterialTheme.typography.bodyLarge, color = Brand.Ink)
                Text(if (AppSettings.botStudentsChat != 0L) "Linked ✓ · posts always ask you first" else "Add your bot to the group and send /students there once",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            }
        }
        HorizontalDivider(color = Brand.Line)
        Row(Modifier.padding(vertical = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Grounded and MPSC-only", style = MaterialTheme.typography.bodyLarge, color = Brand.Ink)
                Text("Always on: every line must be backed by mpsc.gov.in, a government site or a trusted newspaper; anything else is dropped",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            }
        }
    }
    if (AppSettings.claudeKey.isNotBlank()) PrimaryButton(if (busy) "Checking…" else "Check both now", { if (!busy) onCheck() }, Modifier.fillMaxWidth(), enabled = !busy, icon = Icons.Rounded.Refresh)
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Brand.Ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Blue600))
    }
}
