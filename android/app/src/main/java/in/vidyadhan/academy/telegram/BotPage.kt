package `in`.vidyadhan.academy.telegram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.home.Go
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Sky700 = Color(0xFF0369A1); private val Sky50 = Color(0xFFF0F9FF); private val Sky200 = Color(0xFFBAE6FD)

/** "Telegram Bot & Settings" (the prototype's last tab), plus the rest of "More". */
@Composable
fun BotPage(onGo: (Go, Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDb.get(ctx) }
    val types by db.library().seatTypes().collectAsStateWithLifecycle(emptyList())
    val students by db.students().students().collectAsStateWithLifecycle(emptyList())
    var token by remember { mutableStateOf(AppSettings.botToken) }
    var status by remember { mutableStateOf<String?>(null) }
    var linked by remember { mutableIntStateOf(AppSettings.telegramChats().size) }
    var preview by remember { mutableStateOf("receipt") }
    val connected = TelegramBot.connected
    LaunchedEffect(connected) { if (connected) linked = TelegramBot.refresh() }

    HeroPage("Telegram Bot & Settings", if (connected) "@${AppSettings.botName} · Connected ✓" else "Bot not connected yet",
        actions = {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x400EA5E9)).border(1.dp, Color(0x4D7DD3FC), RoundedCornerShape(14.dp)), Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Sky200, modifier = Modifier.size(20.dp))
            }
        }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!connected) Card("Connect your Telegram bot") {
                Text("1. In Telegram, open @BotFather and send /newbot. Give it a name, e.g. Vidyadhan Academy Bot.\n" +
                    "2. BotFather replies with a token (a long code). Copy it and paste it here.\n" +
                    "3. Ask each student to open your bot's link and press Start once. Their @username must be saved on their student record.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
                Field(token, { token = it.trim() }, "Bot token", Icons.Rounded.Key)
                PrimaryButton("Connect", {
                    scope.launch { status = TelegramBot.connect(token).fold({ "Connected to @$it ✓" }, { "Could not connect: ${it.message}" }) }
                }, Modifier.fillMaxWidth(), enabled = token.length > 20, icon = Icons.Rounded.Link)
                status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = if (it.endsWith("✓")) Brand.Green700 else Brand.Red600) }
            } else Card("Your bot", trailing = { Text("Live Bot API", Modifier.clip(RoundedCornerShape(50)).background(Brand.Green50).padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Green700) }) {
                val withTg = students.count { it.leftOn == null && it.telegram.isNotBlank() }
                Text("$linked student${if (linked == 1) "" else "s"} linked · $withTg with a Telegram username", style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
                Text(if (AppSettings.botOwnerChat != 0L) "Test messages come to you ✓" else "Send /owner to your bot to get the test messages yourself.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                Text(if (AppSettings.botStaffChat != 0L) "Staff group connected ✓" else "For the staff digest: add the bot to your staff group and send /staff there.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Share bot link", {
                        val msg = "Please open ${TelegramBot.link} and press Start to get your fee receipts and reminders from ${AppSettings.academyName}."
                        runCatching { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, msg), "Share bot link")) }
                    },
                        Modifier.weight(1f), icon = Icons.Rounded.Share)
                    SoftButton("Refresh", { scope.launch { linked = TelegramBot.refresh() } }, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
                }
                Text("Disconnect bot", Modifier.clickable { TelegramBot.disconnect(); token = "" }.padding(2.dp), style = MaterialTheme.typography.labelMedium, color = Brand.Red600)
            }

            Card("Automated Telegram workflows") {
                Toggle("Instant fee & installment receipt", "Auto-send receipt on every UPI/Cash entry", AppSettings.tgReceipt) { AppSettings.tgReceipt = it }
                Toggle("Installment & seat expiry alerts", "Remind 3 days prior & on due date at ${"%02d:%02d".format(AppSettings.reminderHour, AppSettings.reminderMinute)}", AppSettings.tgDue) { AppSettings.tgDue = it }
                Toggle("Daily birthday wishes (Marathi + Eng)", "Auto-send wishes to the student", AppSettings.tgBirthday) { AppSettings.tgBirthday = it }
                Toggle("Counselor enquiry follow-up digest", "Notify staff Telegram group of today's follow-ups", AppSettings.tgEnquiry) { AppSettings.tgEnquiry = it }
            }

            Card("Test Telegram bot preview", trailing = { Text("Live Bot API", Modifier.clip(RoundedCornerShape(50)).background(Brand.Green50).padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Green700) }) {
                val s = students.firstOrNull { it.leftOn == null }
                val name = s?.name ?: "Rohan Deshmukh"
                val first = name.substringBefore(' ')
                val texts = mapOf(
                    "receipt" to TelegramBot.receiptText(name, 15000, "UPI", s?.course ?: "MPSC", java.time.LocalDate.now().plusMonths(1).toEpochDay(), 10000),
                    "reminder" to "Installment reminder ⏰\nNamaskar $name, your ${s?.course ?: "MPSC"} installment of ${rupees(9000)} is due on 22 Nov. Please pay on time. — ${AppSettings.academyName}",
                    "birthday" to `in`.vidyadhan.academy.home.birthdayWish(name) + "\n\n" + `in`.vidyadhan.academy.Templates.fill("birthday", false, mapOf("name" to first)),
                    "expiry" to "Abhyasika renewal alert 📚\nHi $name, seat 6 + locker 7 expires on 21 Nov (${rupees(1000)}/mo). Reply YES to renew.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewButton("1. Fee receipt", preview == "receipt", Sky700, Sky50, Modifier.weight(1f)) { preview = "receipt" }
                    PreviewButton("2. Installment due", preview == "reminder", Brand.Amber700, Brand.Amber50, Modifier.weight(1f)) { preview = "reminder" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewButton("3. Birthday wish", preview == "birthday", Brand.Saffron600, Brand.Saffron50, Modifier.weight(1f)) { preview = "birthday" }
                    PreviewButton("4. Seat + locker due", preview == "expiry", Brand.Violet700, Color(0xFFF5F3FF), Modifier.weight(1f)) { preview = "expiry" }
                }
                // a Telegram-like message bubble
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFE6EEF5)).border(1.dp, Brand.Slate300.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(10.dp)) {
                    Column(Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)).background(Color.White).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${AppSettings.academyName} Bot 🤖", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Blue700)
                        Text(texts.getValue(preview), style = MaterialTheme.typography.bodySmall, color = Brand.Slate800)
                        Text("${LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))} ✓✓", Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
                    }
                }
                if (connected && AppSettings.botOwnerChat != 0L) SoftButton("Send this test to me", {
                    scope.launch { status = if (TelegramBot.send(AppSettings.botOwnerChat, texts.getValue(preview))) "Test sent ✓" else "Could not send" }
                }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Send)
            }

            Card("Course, seat & locker tariffs") {
                AppSettings.courseList.forEach { c -> Tariff(c, AppSettings.courseFee(c).takeIf { it > 0 }?.let { rupees(it) } ?: "Set on first admission", Brand.Slate800) }
                types.forEach { ty -> Tariff("Abhyasika ${ty.name} seat", "${rupees(ty.fee)} / mo", if (ty.icon == "LEGROOM") Brand.Violet700 else Brand.Slate800) }
                Tariff("Separate personal locker", "${rupees(AppSettings.lockerFee.toLong())} / mo", Color(0xFF4338CA))
            }

            Card("More") {
                OptionRow(Icons.Rounded.AccountBalanceWallet, Brand.Green600, "Money", "Income, expenses and reports") { onGo(Go.Money, 0) }
                OptionRow(Icons.Rounded.Campaign, Color(0xFFE11D48), "Send a message", "WhatsApp or Telegram to many people") { onGo(Go.Broadcast, 0) }
                OptionRow(Icons.Rounded.Search, Brand.Blue600, "Search", "Students, members and enquiries") { onGo(Go.Search, 0) }
                OptionRow(Icons.Rounded.Settings, Brand.Slate600, "Settings", "Academy details, fees, seat types, backup, updates") { onGo(Go.Settings, 0) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Card(title: String, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Blue700)
            trailing()
        }
        content()
    }
}

@Composable
private fun Toggle(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Brand.Ink)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
        }
        Switch(on, { on = it; onChange(it) })
    }
}

@Composable
private fun PreviewButton(text: String, selected: Boolean, fg: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(text, modifier.clip(RoundedCornerShape(12.dp)).background(bg).border(if (selected) 2.dp else 1.dp, fg.copy(alpha = if (selected) 0.8f else 0.3f), RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = 9.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = fg, textAlign = TextAlign.Center, maxLines = 1)
}

@Composable
private fun Tariff(name: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = color)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = color)
    }
    HorizontalDivider(color = Brand.Line)
}
