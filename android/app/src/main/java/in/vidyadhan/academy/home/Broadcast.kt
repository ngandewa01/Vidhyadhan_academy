package `in`.vidyadhan.academy.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*

private data class Person(val key: String, val name: String, val mobile: String, val photo: String)

private val presets = listOf(
    Triple("Holiday", "नमस्कार {name}, उद्या अकॅडमीला सुट्टी आहे. परवापासून वर्ग नेहमीप्रमाणे सुरू राहतील. — विद्याधन अकॅडमी",
        "Hello {name}, the academy is closed tomorrow. Classes continue as usual the day after. — Vidyadhan Academy"),
    Triple("Test", "नमस्कार {name}, रविवारी सकाळी १० वाजता सराव परीक्षा आहे. वेळेवर उपस्थित राहा. — विद्याधन अकॅडमी",
        "Hello {name}, there is a practice test on Sunday at 10 am. Please be on time. — Vidyadhan Academy"),
    Triple("Fees", "नमस्कार {name}, कृपया तुमची थकित फी लवकरात लवकर भरावी. धन्यवाद. — विद्याधन अकॅडमी",
        "Hello {name}, please pay your pending fee at the earliest. Thank you. — Vidyadhan Academy"),
    Triple("New batch", "नमस्कार {name}, नवीन MPSC बॅच लवकरच सुरू होत आहे. प्रवेशासाठी संपर्क करा. — विद्याधन अकॅडमी",
        "Hello {name}, a new MPSC batch starts soon. Contact us for admission. — Vidyadhan Academy"),
)

private fun forEveryone(text: String, marathi: Boolean) = text.replace("{name}", if (marathi) "सर्व विद्यार्थ्यांनो" else "everyone")

private fun shareTelegram(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    val tg = Intent(send).setPackage("org.telegram.messenger")
    runCatching { ctx.startActivity(tg) }.onFailure { runCatching { ctx.startActivity(Intent.createChooser(send, "Send with")) } }
}

/** Send one message to many people: one post on Telegram, WhatsApp one by one (with each name), or one group SMS. */
@Composable
fun BroadcastScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val students by remember { studentsWithFees(db.students()) }.collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    var group by remember { mutableStateOf("students") }
    var marathi by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf(presets.first().second) }
    var preset by remember { mutableStateOf(presets.first().first) }
    val excluded = remember { mutableStateListOf<String>() }
    var queue by remember { mutableIntStateOf(-1) }
    BackHandler { if (queue >= 0) queue = -1 else onClose() }
    HideBottomNav()
    val t = today()
    val cur = students.filter { it.current }
    val batches = cur.map { it.batch }.filter(String::isNotBlank).distinct()
    val groups = listOf("students" to "All students (${cur.size})") + batches.map { "batch:$it" to "$it batch (${cur.count { s -> s.batch == it }})" } +
        listOf("due" to "Fees due (${cur.count { it.nextDue != null && it.nextDue <= t }})", "library" to "Library (${members.count { it.active }})",
            "libdue" to "Library fee due (${members.count { it.active && it.validTill <= t + 3 }})",
            "enquiries" to "Open enquiries (${enquiries.count { it.status == "NEW" || it.status == "CONTACTED" }})")
    val people = when {
        group == "students" -> cur.map { Person("s${it.id}", it.name, it.mobile, it.photo) }
        group.startsWith("batch:") -> cur.filter { it.batch == group.removePrefix("batch:") }.map { Person("s${it.id}", it.name, it.mobile, it.photo) }
        group == "due" -> cur.filter { it.nextDue != null && it.nextDue <= t }.map { Person("s${it.id}", it.name, it.mobile, it.photo) }
        group == "library" -> members.filter { it.active }.map { Person("m${it.id}", it.name, it.mobile, it.photo) }
        group == "libdue" -> members.filter { it.active && it.validTill <= t + 3 }.map { Person("m${it.id}", it.name, it.mobile, it.photo) }
        else -> enquiries.filter { it.status == "NEW" || it.status == "CONTACTED" }.map { Person("e${it.id}", it.name, it.mobile, "") }
    }.distinctBy { it.mobile }
    val chosen = people.filter { it.key !in excluded }

    HeroPage("Send a message", "To many people at once", onBack = onClose) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            if (queue >= 0 && queue < chosen.size) {
                val p = chosen[queue]
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Brand.WhatsApp, Brand.WhatsAppDark))).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("WhatsApp · ${queue + 1} of ${chosen.size}", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name, 48.dp, photo = p.photo); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text("+91 ${p.mobile}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    ProgressBar((queue + 1f) / chosen.size, color = Color.White, track = Color.White.copy(alpha = 0.25f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton("Skip", { queue++ }, Modifier.weight(1f), color = Color.White)
                        PrimaryButton("Open WhatsApp", { Contact.whatsapp(ctx, p.mobile, text.replace("{name}", p.name.substringBefore(' '))); queue++ },
                            Modifier.weight(1.6f), icon = Icons.AutoMirrored.Rounded.Chat, custom = Brush.linearGradient(listOf(Color(0xFF0B7A6F), Color(0xFF075E54))))
                    }
                    Text("Send in WhatsApp, come back here, then tap again for the next person.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            } else if (queue >= chosen.size && chosen.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand.Green50).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Brand.Green600); Spacer(Modifier.width(10.dp))
                    Text("Done: WhatsApp opened for all ${chosen.size}.", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Brand.Green700)
                    PillButton("Close", { queue = -1 }, Brand.Green700)
                }
            }
            FormCard("1. Who should get it?", Icons.Rounded.Groups, Brand.Blue600) {
                ChoicePills(groups.map { it.second }, groups.firstOrNull { it.first == group }?.second ?: "", { lbl -> group = groups.first { it.second == lbl }.first; excluded.clear(); queue = -1 })
                if (people.isEmpty()) Text("Nobody in this group yet.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Brand.Line, RoundedCornerShape(18.dp))) {
                    Row(Modifier.fillMaxWidth().clickable { if (excluded.isEmpty()) excluded.addAll(people.map { it.key }) else excluded.clear() }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(excluded.isEmpty(), { if (it) excluded.clear() else excluded.addAll(people.map { p -> p.key }) })
                        Text("${chosen.size} of ${people.size} selected", style = MaterialTheme.typography.labelLarge, color = Brand.Slate600)
                    }
                    people.take(60).forEach { p ->
                        HorizontalDivider(color = Brand.Line)
                        Row(Modifier.fillMaxWidth().clickable { if (p.key in excluded) excluded.remove(p.key) else excluded.add(p.key) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(p.key !in excluded, { if (it) excluded.remove(p.key) else excluded.add(p.key) })
                            Avatar(p.name, 32.dp, photo = p.photo); Spacer(Modifier.width(10.dp))
                            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Brand.Ink, maxLines = 1)
                            Text(p.mobile.takeLast(4).let { "…$it" }, style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                        }
                    }
                }
            }
            FormCard("2. Message", Icons.Rounded.EditNote, Brand.Saffron600) {
                Segmented(listOf("mr" to "मराठी", "en" to "English"), if (marathi) "mr" else "en", { marathi = it == "mr"; presets.firstOrNull { p -> p.first == preset }?.let { p -> text = if (marathi) p.second else p.third } })
                ChoicePills(presets.map { it.first } + "Custom", preset, { k -> preset = k; presets.firstOrNull { it.first == k }?.let { text = if (marathi) it.second else it.third } ?: run { text = "" } })
                Field(text, { text = it; preset = "Custom" }, "Message", singleLine = false, minLines = 4,
                    supporting = "{name} becomes each person's first name on WhatsApp")
                MessageBubble(text.replace("{name}", chosen.firstOrNull()?.name?.substringBefore(' ') ?: "Name"), marathi)
            }
            FormCard("3. Send", Icons.AutoMirrored.Rounded.Send, Brand.Green600) {
                OptionRow(Icons.AutoMirrored.Rounded.Send, Color(0xFF229ED9), "Post on Telegram", "One message to your group or channel. Pick it in Telegram.") {
                    shareTelegram(ctx, forEveryone(text, marathi))
                }
                if (AppSettings.telegramLink.startsWith("https://")) SoftButton("Open our Telegram group", {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppSettings.telegramLink))) }
                }, Modifier.fillMaxWidth(), color = Color(0xFF229ED9), icon = Icons.Rounded.Groups)
                OptionRow(Icons.AutoMirrored.Rounded.Chat, Brand.WhatsAppDark, "WhatsApp, one by one", "${chosen.size} people · each message has their name") {
                    if (chosen.isNotEmpty()) queue = 0
                }
                OptionRow(Icons.Rounded.Sms, Brand.Blue600, "One SMS to all", "${chosen.size} numbers in your SMS app") {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + chosen.joinToString(";") { it.mobile })).putExtra("sms_body", forEveryone(text, marathi))) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
