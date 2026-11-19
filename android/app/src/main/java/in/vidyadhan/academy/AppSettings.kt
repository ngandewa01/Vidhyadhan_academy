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
    private fun put(k: String, v: String) { s[k] = v; prefs?.edit()?.putString(k, v)?.apply() }

    var academyName: String get() = get("name", "Vidyadhan Academy"); set(v) = put("name", v)
    var academyPhone: String get() = get("phone", ""); set(v) = put("phone", v)
    var academyAddress: String get() = get("address", ""); set(v) = put("address", v)
    var receiptPrefix: String get() = get("rprefix", "VA"); set(v) = put("rprefix", v)
    var courses: String get() = get("courses", "MPSC"); set(v) = put("courses", v)
    val courseList: List<String> get() = courses.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("MPSC") }

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
    var updateUrl: String get() = get("updUrl", ""); set(v) = put("updUrl", v)

    /** Day (epoch day) of the last successful backup; 0 = never. */
    var lastBackup: Long get() = get("lastBackup", "0").toLong(); set(v) = put("lastBackup", v.toString())

    /** "Reminded today ✓": [key] is e.g. "s12" (student) or "m7" (library member). */
    fun remindedOn(key: String, day: Long): Boolean = s["rem_$key"] == day.toString()
    fun markReminded(key: String, day: Long) = put("rem_$key", day.toString())
}
