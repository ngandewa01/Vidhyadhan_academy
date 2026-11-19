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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.FormBody
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
    val link: String get() = "https://t.me/${AppSettings.botName}"

    private fun call(method: String, form: Map<String, String> = emptyMap()): JsonObject {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
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
                when {
                    text.startsWith("/owner") && type == "private" -> AppSettings.botOwnerChat = chatId
                    text.startsWith("/staff") && type != "private" -> AppSettings.botStaffChat = chatId
                    type == "private" && user != null -> AppSettings.setTelegramChat(user, chatId)
                }
            }
        }
        AppSettings.telegramChats().size
    }

    suspend fun send(chat: Long, text: String): Boolean = withContext(Dispatchers.IO) {
        connected && chat != 0L && runCatching { call("sendMessage", mapOf("chat_id" to "$chat", "text" to text)) }.isSuccess
    }

    /** Sends to a student by @username; false if they have not pressed Start yet. */
    suspend fun sendTo(username: String, text: String): Boolean {
        if (!connected || username.isBlank()) return false
        val chat = AppSettings.telegramChat(username) ?: run { refresh(); AppSettings.telegramChat(username) } ?: return false
        return send(chat, text)
    }

    /** After a student pays: the receipt, if receipts are switched on and the student is linked. Runs in the background. */
    fun studentPaid(ctx: Context, studentId: Long, amount: Long, mode: String) {
        if (!connected || !AppSettings.tgReceipt) return
        scope.launch {
            val dao = AppDb.get(ctx).students()
            val st = dao.allStudents().firstOrNull { it.id == studentId } ?: return@launch
            if (st.telegram.isBlank()) return@launch
            val pos = studentsWithFeesNow(dao).firstOrNull { it.id == studentId }
            sendTo(st.telegram, receiptText(st.name, amount, mode, st.course, pos?.nextDue, pos?.nextDueAmount ?: 0))
        }
    }

    fun receiptText(name: String, amount: Long, mode: String, what: String, nextDue: Long?, nextAmount: Long) = buildString {
        append("Fee receipt — ${AppSettings.academyName}\n")
        append("Student: $name\n")
        append("Paid: ${rupees(amount)}" + (if (mode.isNotBlank()) " ($mode)" else "") + (if (what.isNotBlank()) " · $what" else "") + "\n")
        if (nextDue != null && nextAmount > 0) append("Next due: ${rupees(nextAmount)} on ${nextDue.asDate()}. ")
        append("Thank you! 🙏")
    }
}
