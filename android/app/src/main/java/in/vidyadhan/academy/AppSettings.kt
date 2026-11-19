package `in`.vidyadhan.academy

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/** App settings. Reads are Compose-observable; every write is saved immediately. */
object AppSettings {
    private var prefs: SharedPreferences? = null
    private val s = mutableStateMapOf<String, String>()

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (k, v) -> s[k] = v.toString() }
    }
    private fun get(k: String, def: String) = s[k] ?: def
    private fun put(k: String, v: String) { val old = s[k]; s[k] = v; prefs?.edit()?.putString(k, v)?.apply(); if (old != v) onChange?.invoke(k) }

    // ── shared with the academy's other phones through the cloud ──
    /** Called after a setting changes (cloud sync listens). */
    var onChange: ((String) -> Unit)? = null
    private val sharedKeys = setOf("name", "phone", "address", "rprefix", "courses", "batches", "inst", "lateFee", "lateOn", "enqDays", "seats", "lockerCount", "lockerDep", "lockerFee", "tgLink", "gemKey")
    /** Settings every phone of the academy uses (not phone-only things like the bot, reminders already sent or the dashboard order). */
    fun isShared(k: String) = k in sharedKeys || k.startsWith("cf_") || k.startsWith("tpl_")
    fun sharedValues(): Map<String, String> = s.filterKeys(::isShared).toMap()
    fun applyShared(m: Map<String, String>) {
        val e = prefs?.edit()
        m.filterKeys(::isShared).forEach { (k, v) -> if (s[k] != v) { s[k] = v; e?.putString(k, v) } }
        e?.apply()
    }

    // ── cloud sync state (this phone only) ──
    var welcomed: Boolean get() = get("welcomed", "") == "1"; set(v) = put("welcomed", if (v) "1" else "")
    var cloudAcademy: String get() = get("cloudAcademy", ""); set(v) = put("cloudAcademy", v)
    var cloudDevice: Long get() = get("cloudDevice", "0").toLongOrNull() ?: 0; set(v) = put("cloudDevice", v.toString())
    var cloudLast: Long get() = get("cloudLast", "0").toLongOrNull() ?: 0; set(v) = put("cloudLast", v.toString())

    var academyName: String get() = get("name", "Vidyadhan Academy"); set(v) = put("name", v)
    var academyPhone: String get() = get("phone", ""); set(v) = put("phone", v)
    var academyAddress: String get() = get("address", ""); set(v) = put("address", v)
    var receiptPrefix: String get() = get("rprefix", "VA"); set(v) = put("rprefix", v)
    var courses: String get() = get("courses", "MPSC"); set(v) = put("courses", v)
    val courseList: List<String> get() = courses.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("MPSC") }

    /** Usual fee of a course (filled in on the admission form); 0 = not set. */
    fun courseFee(course: String): Long = get("cf_$course", "0").toLongOrNull() ?: 0
    fun setCourseFee(course: String, fee: Long) = put("cf_$course", fee.toString())

    var batches: String get() = get("batches", "Morning, Evening, Weekend"); set(v) = put("batches", v)
    val batchList: List<String> get() = batches.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val enquirySources = listOf("Walk-in", "Phone call", "WhatsApp", "Instagram", "Facebook", "Poster / banner", "Friend / referral", "Other")
    val expenseCategories = listOf("Rent", "Electricity", "Salary", "Internet", "Books & material", "Advertising", "Maintenance", "Other")

    var enquiryDays: Int get() = get("enqDays", "2").toInt(); set(v) = put("enqDays", v.toString())
    var reminderHour: Int get() = get("remH", "9").toInt(); set(v) = put("remH", v.toString())
    var reminderMinute: Int get() = get("remM", "0").toInt(); set(v) = put("remM", v.toString())
    var seatCount: Int get() = get("seats", "40").toInt(); set(v) = put("seats", v.toString())
    var defaultInstallments: Int get() = get("inst", "3").toInt(); set(v) = put("inst", v.toString())
    var lateFeeOn: Boolean get() = get("lateOn", "false") == "true"; set(v) = put("lateOn", v.toString())
    var lateFeePerDay: Int get() = get("lateFee", "10").toInt(); set(v) = put("lateFee", v.toString())
    var lockerFee: Int get() = get("lockerFee", "0").toInt(); set(v) = put("lockerFee", v.toString())
    var lockerDeposit: Int get() = get("lockerDep", "0").toInt(); set(v) = put("lockerDep", v.toString())
    var lockerCount: Int get() = get("lockerCount", "20").toInt(); set(v) = put("lockerCount", v.toString())
    fun template(key: String, marathi: Boolean): String? = s["tpl_${key}_${if (marathi) "mr" else "en"}"]?.takeIf { it.isNotBlank() }
    fun setTemplate(key: String, marathi: Boolean, text: String) = put("tpl_${key}_${if (marathi) "mr" else "en"}", text)
    var telegramLink: String get() = get("tgLink", ""); set(v) = put("tgLink", v.trim())
    var updateUrl: String get() = get("updUrl", "").ifBlank { DEFAULT_UPDATE_URL }; set(v) = put("updUrl", v)
    const val DEFAULT_UPDATE_URL = "https://github.com/ngandewa01/Vidhyadhan_academy/releases/download/app-latest/update.json"
    /** Day the app last looked for an update, and the version the owner chose "Later" for. */
    var updateCheckedOn: Long get() = get("updDay", "0").toLongOrNull() ?: 0; set(v) = put("updDay", v.toString())
    var updateSkipped: Int get() = get("updSkip", "0").toIntOrNull() ?: 0; set(v) = put("updSkip", v.toString())

    // ── Telegram bot ──
    var botToken: String get() = get("botToken", ""); set(v) = put("botToken", v.trim())
    var botName: String get() = get("botName", ""); set(v) = put("botName", v)
    var botOffset: Long get() = get("botOffset", "0").toLong(); set(v) = put("botOffset", v.toString())
    var botOwnerChat: Long get() = get("botOwner", "0").toLong(); set(v) = put("botOwner", v.toString())
    /** The students' Telegram group (registered by sending /students there). */
    var botStudentsChat: Long get() = get("botStudents", "0").toLongOrNull() ?: 0; set(v) = put("botStudents", v.toString())
    // MPSC news (AI). The key is shared with the academy's other phones through the cloud; once saved it is not edited.
    var geminiKey: String get() = get("gemKey", ""); set(v) = put("gemKey", v)
    var geminiModel: String get() = get("gemModel", ""); set(v) = put("gemModel", v)
    var newsOn: Boolean get() = get("newsOn", "1") == "1"; set(v) = put("newsOn", if (v) "1" else "0")
    var digestOn: Boolean get() = get("digestOn", "1") == "1"; set(v) = put("digestOn", if (v) "1" else "0")
    var newsNotify: Boolean get() = get("newsNotify", "1") == "1"; set(v) = put("newsNotify", if (v) "1" else "0")
    var newsLast: Long get() = get("newsLast", "0").toLongOrNull() ?: 0; set(v) = put("newsLast", v.toString())
    var digestDay: Long get() = get("digestDay", "0").toLongOrNull() ?: 0; set(v) = put("digestDay", v.toString())
    var botStaffChat: Long get() = get("botStaff", "0").toLong(); set(v) = put("botStaff", v.toString())
    var tgReceipt: Boolean get() = get("tgReceipt", "true") == "true"; set(v) = put("tgReceipt", v.toString())
    var tgDue: Boolean get() = get("tgDue", "true") == "true"; set(v) = put("tgDue", v.toString())
    var tgBirthday: Boolean get() = get("tgBday", "true") == "true"; set(v) = put("tgBday", v.toString())
    var tgEnquiry: Boolean get() = get("tgEnq", "true") == "true"; set(v) = put("tgEnq", v.toString())
    /** Telegram chat of a student, learnt when they press Start in the bot; keyed by @username (lower case). */
    fun telegramChat(username: String): Long? = s["tg_${username.lowercase().removePrefix("@")}"]?.toLongOrNull()
    fun setTelegramChat(username: String, chat: Long) = put("tg_${username.lowercase().removePrefix("@")}", chat.toString())
    fun telegramChats(): Map<String, Long> = s.filterKeys { it.startsWith("tg_") }.mapNotNull { (k, v) -> v.toLongOrNull()?.let { k.removePrefix("tg_") to it } }.toMap()

    /** Day (epoch day) of the last successful backup; 0 = never. */
    var lastBackup: Long get() = get("lastBackup", "0").toLong(); set(v) = put("lastBackup", v.toString())

    /** "Reminded today ✓": [key] is e.g. "s12" (student) or "m7" (library member). */
    /** Forgets who is linked on Telegram and which reminders were sent (used when starting fresh: record numbers start again). */
    fun forgetPeople() {
        val gone = s.keys.filter { it.startsWith("tg_k_") || it.startsWith("rem_") }
        val e = prefs?.edit()
        gone.forEach { s.remove(it); e?.remove(it) }
        e?.apply()
    }

    fun remindedOn(key: String, day: Long): Boolean = s["rem_$key"] == day.toString()
    fun markReminded(key: String, day: Long) = put("rem_$key", day.toString())

    /** Day the birthday strip was closed with ×; it stays hidden for the rest of that day. */
    var birthdayHiddenOn: Long get() = get("bdayHidden", "0").toLong(); set(v) = put("bdayHidden", v.toString())

    /** Dashboard cards in the owner's order, and the ones they hid (long-press → arrange). */
    var homeOrder: List<String> get() = get("homeOrder", "").split(",").filter { it.isNotBlank() }; set(v) = put("homeOrder", v.joinToString(","))
    var homeHidden: Set<String> get() = get("homeHidden", "").split(",").filter { it.isNotBlank() }.toSet(); set(v) = put("homeHidden", v.joinToString(","))
}
