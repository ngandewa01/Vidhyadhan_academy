package `in`.vidyadhan.academy.telegram

import android.content.Context
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.studentsWithFeesNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The academy's own Telegram bot (made by the owner with @BotFather), used straight from the phone through Telegram's Bot API.
 * Students press Start in the bot once; the app learns their chat from their @username and can then message them.
 */
object TelegramBot {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val connected: Boolean get() = AppSettings.botToken.isNotBlank() && AppSettings.botName.isNotBlank()
    /** Set by the app at start, so the bot can look people up by mobile number. */
    @Volatile var app: Context? = null
    val link: String get() = "https://t.me/${AppSettings.botName}"

    private fun call(method: String, form: Map<String, String> = emptyMap(), file: java.io.File? = null): JsonObject {
        val body = if (file == null) FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        else okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).apply {
            form.forEach { (k, v) -> addFormDataPart(k, v) }
            addFormDataPart("document", file.name, file.asRequestBody("application/pdf".toMediaType()))
        }.build()
        val req = Request.Builder().url("https://api.telegram.org/bot${AppSettings.botToken}/$method").post(body).build()
        client.newCall(req).execute().use { r ->
            val o = Json.parseToJsonElement(r.body.string()).jsonObject
            require(o["ok"]?.jsonPrimitive?.booleanOrNull == true) { o["description"]?.jsonPrimitive?.contentOrNull ?: "Telegram said no (${r.code})" }
            return o
        }
    }

    /** Checks the token and remembers the bot's username. */
    suspend fun connect(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            AppSettings.botToken = token
            val name = call("getMe")["result"]!!.jsonObject["username"]!!.jsonPrimitive.content
            AppSettings.botName = name
            name
        }.onFailure { AppSettings.botName = "" }
    }

    fun disconnect() { AppSettings.botToken = ""; AppSettings.botName = "" }

    /**
     * Reads new messages sent to the bot: who pressed Start (by @username), "/owner" from the owner, "/staff" in the staff group.
     * Returns how many students are linked.
     */
    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        if (!connected) return@withContext 0
        runCatching {
            val res = call("getUpdates", mapOf("offset" to "${AppSettings.botOffset}", "timeout" to "0"))["result"]!!.jsonArray
            res.forEach { u ->
                val o = u.jsonObject
                AppSettings.botOffset = (o["update_id"]!!.jsonPrimitive.long + 1)
                val msg = o["message"]?.jsonObject ?: return@forEach
                val chat = msg["chat"]!!.jsonObject
                val chatId = chat["id"]!!.jsonPrimitive.long
                val type = chat["type"]?.jsonPrimitive?.contentOrNull ?: "private"
                val text = msg["text"]?.jsonPrimitive?.contentOrNull ?: ""
                val user = msg["from"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull
                val fromId = msg["from"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull
                val contact = msg["contact"]?.jsonObject
                when {
                    contact != null && type == "private" -> linkByPhone(chatId, fromId, contact)
                    text.startsWith("/start ") && type == "private" && text.removePrefix("/start ").trim() != "join" -> {
                        AppSettings.setTelegramChat("k_" + text.removePrefix("/start ").trim(), chatId)
                        reply(chatId, "✅ Connected to <b>${esc(AppSettings.academyName)}</b>. Your fee receipts and reminders will come here.")
                    }
                    (text.trim() == "/start" || text.trim() == "/start join") && type == "private" -> askPhone(chatId)
                    text.startsWith("/owner") && type == "private" -> AppSettings.botOwnerChat = chatId
                    text.startsWith("/staff") && type != "private" -> AppSettings.botStaffChat = chatId
                    text.startsWith("/students") && type != "private" -> AppSettings.botStudentsChat = chatId
                    type == "private" && user != null -> AppSettings.setTelegramChat(user, chatId)
                }
            }
        }
        linkedCount()
    }

    private fun reply(chat: Long, text: String, markup: String? = null) {
        runCatching { call("sendMessage", buildMap { put("chat_id", "$chat"); put("text", text); put("parse_mode", "HTML"); if (markup != null) put("reply_markup", markup) }) }
    }

    /** Someone opened the common bot link: ask them to share their phone number (a Telegram button). */
    private fun askPhone(chat: Long) = reply(chat,
        "Namaskar 🙏 Welcome to <b>${esc(AppSettings.academyName)}</b>.\nTo get your fee receipts and reminders here, tap <b>📱 Share my phone number</b> below.",
        """{"keyboard":[[{"text":"📱 Share my phone number","request_contact":true}]],"one_time_keyboard":true,"resize_keyboard":true}""")

    /** A shared phone number: connect every student, Abhyasika member and open enquiry with that mobile. */
    private suspend fun linkByPhone(chat: Long, fromId: Long?, contact: JsonObject) {
        val done = """{"remove_keyboard":true}"""
        val owner = contact["user_id"]?.jsonPrimitive?.longOrNull
        if (owner == null || owner != fromId) { reply(chat, "Please share <b>your own</b> number with the button.", done); return }
        val mobile = contact["phone_number"]?.jsonPrimitive?.contentOrNull.orEmpty().filter(Char::isDigit).takeLast(10)
        val ctx = app ?: return
        val db = AppDb.get(ctx)
        val names = mutableListOf<String>()
        db.students().allStudents().filter { it.mobile.takeLast(10) == mobile && mobile.length == 10 }.forEach { AppSettings.setTelegramChat("k_s${it.id}", chat); names += it.name }
        db.library().allMembers().filter { it.active && it.mobile.takeLast(10) == mobile && mobile.length == 10 }.forEach { AppSettings.setTelegramChat("k_m${it.id}", chat); names += it.name }
        db.enquiries().all().first().filter { it.mobile.takeLast(10) == mobile && mobile.length == 10 && it.status in listOf("NEW", "CONTACTED") }
            .forEach { AppSettings.setTelegramChat("k_e${it.id}", chat); names += it.name }
        if (names.isEmpty()) reply(chat, "We could not find +91 $mobile in ${esc(AppSettings.academyName)} records. Please ask the office to add or correct your mobile number, then tap /start again.", done)
        else reply(chat, "✅ Connected: <b>${esc(names.distinct().joinToString(", "))}</b>.\nYour fee receipts, reminders and academy updates will come here.", done)
    }

    /** The one link for everyone: students press Start and share their phone number to be connected. */
    val joinLink: String get() = "$link?start=join"

    fun linkedCount() = AppSettings.telegramChats().keys.count { it.startsWith("k_") }
    fun isLinked(key: String) = AppSettings.telegramChat("k_$key") != null
    /** The personal link for a student (s12), Abhyasika member (m3) or enquiry (e5): tapping Start in it links them. */
    fun linkFor(key: String) = "$link?start=$key"
    fun invite(key: String) = "${AppSettings.academyName}: get your fee receipts and reminders on Telegram. Tap ${linkFor(key)} and press Start."
    /** Sends to a person by their key; false if they have not tapped their link yet. */
    suspend fun sendToKey(key: String, text: String): Boolean = sendTo("k_$key", text)

    /** [text] may use <b>bold</b> (Telegram HTML); anything typed by people must go through [esc]. */
    suspend fun send(chat: Long, text: String): Boolean = withContext(Dispatchers.IO) {
        connected && chat != 0L && runCatching { call("sendMessage", mapOf("chat_id" to "$chat", "text" to text, "parse_mode" to "HTML")) }.isSuccess
    }

    /** Sends a receipt PDF to a connected person (s12 / m3). */
    suspend fun sendReceipt(key: String, file: java.io.File, caption: String): Boolean {
        val chat = AppSettings.telegramChat("k_$key") ?: return false
        return sendDocument(chat, file, caption)
    }

    suspend fun sendDocument(chat: Long, file: java.io.File, caption: String): Boolean = withContext(Dispatchers.IO) {
        connected && chat != 0L && runCatching { call("sendDocument", mapOf("chat_id" to "$chat", "caption" to caption, "parse_mode" to "HTML"), file) }.isSuccess
    }

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * Every Telegram button in the app: sends through the bot, or says why it can't
     * (bot not connected, no @username saved, or the person hasn't pressed Start yet).
     */
    fun sendOrExplain(ctx: Context, key: String, mobile: String, text: String) {
        fun toast(m: String) = android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_LONG).show()
        if (!connected) { toast("Connect your Telegram bot first: Bot & More → Connect"); return }
        scope.launch {
            val ok = sendToKey(key, text)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (ok) toast("Sent on Telegram ✓")
                else {
                    toast("Not on Telegram yet. WhatsApp opens with their personal link; once they tap Start, Telegram works.")
                    if (mobile.isNotBlank()) `in`.vidyadhan.academy.ui.Contact.whatsapp(ctx, mobile, invite(key))
                }
            }
        }
    }

    /** Sends to a student by @username; false if they have not pressed Start yet. */
    suspend fun sendTo(username: String, text: String): Boolean {
        if (!connected || username.isBlank()) return false
        val chat = AppSettings.telegramChat(username) ?: run { refresh(); AppSettings.telegramChat(username) } ?: return false
        return send(chat, text)
    }

    /** After a student pays: the receipt, if receipts are switched on and the student is linked. Runs in the background. */
    fun studentPaid(ctx: Context, studentId: Long, amount: Long, mode: String, paymentId: Long = 0) {
        if (!connected || !AppSettings.tgReceipt) return
        scope.launch {
            val dao = AppDb.get(ctx).students()
            val st = dao.allStudents().firstOrNull { it.id == studentId } ?: return@launch
            val chat = AppSettings.telegramChat("k_s$studentId") ?: run { refresh(); AppSettings.telegramChat("k_s$studentId") } ?: return@launch
            val pos = studentsWithFeesNow(dao).firstOrNull { it.id == studentId } ?: return@launch
            val no = "${AppSettings.receiptPrefix}-S${paymentId.toString().padStart(5, '0')}"
            val text = receiptText(no, st.name, amount, mode, st.course + if (st.batch.isNotBlank()) " · ${st.batch}" else "",
                pos.totalFee, pos.paid, pos.nextDue, pos.nextDueAmount)
            val pdf = runCatching {
                `in`.vidyadhan.academy.ui.receiptPdf(ctx, `in`.vidyadhan.academy.ui.ReceiptData(no, `in`.vidyadhan.academy.students.today(), "FEE RECEIPT", st.name,
                    listOf("Mobile" to "+91 ${st.mobile}"),
                    amount, listOf("Total fee" to rupees(pos.totalFee), "Paid so far" to rupees(pos.paid), "Balance" to rupees((pos.totalFee - pos.paid).coerceAtLeast(0))),
                    forText = "${st.course} course fee" + if (st.batch.isNotBlank()) " · ${st.batch} batch" else "", forSub = mode,
                    nextAmount = if (pos.nextDue != null) pos.nextDueAmount else 0L, nextDue = pos.nextDue))
            }.getOrNull()
            if (pdf == null || !sendDocument(chat, pdf, text)) send(chat, text)
        }
    }

    /** The receipt as a Telegram message (bold labels, one fact per line). */
    fun receiptText(no: String, name: String, amount: Long, mode: String, course: String, total: Long, paid: Long, nextDue: Long?, nextAmount: Long) = buildString {
        append("🧾 <b>FEE RECEIPT</b>\n<b>${esc(AppSettings.academyName)}</b>\n")
        append("━━━━━━━━━━━━━━\n")
        append("Receipt no: <b>$no</b>\n")
        append("Date: ${`in`.vidyadhan.academy.students.today().asDate()}\n")
        append("Student: <b>${esc(name)}</b>\n")
        if (course.isNotBlank()) append("Course: ${esc(course)}\n")
        append("━━━━━━━━━━━━━━\n")
        append("Amount paid: <b>${rupees(amount)}</b>" + (if (mode.isNotBlank()) " (${esc(mode)})" else "") + "\n")
        if (total > 0) append("Total fee: ${rupees(total)}\nPaid so far: ${rupees(paid)}\nBalance: <b>${rupees((total - paid).coerceAtLeast(0))}</b>\n")
        if (nextDue != null && nextAmount > 0) append("Next due: <b>${rupees(nextAmount)}</b> on ${nextDue.asDate()}\n")
        append("━━━━━━━━━━━━━━\nThank you! 🙏 धन्यवाद!")
    }
}
