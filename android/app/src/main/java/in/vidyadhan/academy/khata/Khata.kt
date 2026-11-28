package `in`.vidyadhan.academy.khata

import `in`.vidyadhan.academy.data.KhataEntry
import java.time.LocalDate

/** Old Khatabook ledger: reading the CSV, grouping by person, and the totals. */
object Khata {
    /** Words that differ between the old book and the names in the app ("Stu", "MPSC", batch numbers). */
    private val NOISE = setOf("stu", "std", "student", "enquiry", "mpsc", "academy", "sir", "saralseva", "saralsrva",
        "40", "10th", "11th", "70", "lab", "ex", "service", "man", "pass", "10pass")

    /** The same person under different spellings gives the same key: "Stu Rahul Patil Mpsc" and "Rahul Patil". */
    fun keyOf(name: String): String =
        Regex("[a-z]+").findAll(name.lowercase()).map { it.value }.filter { it !in NOISE && it.length > 1 }.sorted().joinToString(" ")
            .ifEmpty { name.trim().lowercase() }

    const val COURSE = "COURSE"; const val LIBRARY = "LIBRARY"; const val ENQUIRY = "ENQUIRY"; const val OTHER = "OTHER"
    /** Review answer: confirmed. Skip is not saved, so a skipped person stays in the review. */
    const val DONE = "DONE"
    const val SKIP = "SKIP"
    val TYPES = listOf(COURSE, LIBRARY, ENQUIRY, OTHER)
    fun label(t: String) = when (t) { COURSE -> "MPSC course"; LIBRARY -> "Library"; ENQUIRY -> "Enquiry"; SKIP -> "Skip for now"; else -> "Other" }

    /** A first guess from the name, which the owner can change: "Enquiry" lines, "Stu"/"Std"/"MPSC" lines. */
    fun guessType(name: String): String = when {
        Regex("\\benquiry\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) -> ENQUIRY
        Regex("\\b(stu|std|student|mpsc)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) -> COURSE
        else -> OTHER
    }

    /** The name shown on screen: the book's "Stu", "Std", "Enquiry" and "MPSC" words are hidden. The book's spelling stays in the history. */
    fun cleanName(name: String): String {
        val noise = setOf("stu", "std", "student", "enquiry", "mpsc")
        val words = name.trim().split(Regex("\\s+")).filter { it.lowercase().trim(',', '.') !in noise }
        return words.joinToString(" ").ifBlank { name.trim() }
    }

    /** Only the 10 digits of a mobile number, from anything typed or picked from contacts. */
    fun mobileDigits(text: String): String = text.filter { it.isDigit() }.takeLast(10)

    /** Reads the CSV made from the Khatabook statement: name, date (yyyy-mm-dd), debit, credit, details. */
    fun parseCsv(text: String): List<KhataEntry> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).mapNotNull { line ->
            val c = splitCsv(line)
            if (c.size < 4) return@mapNotNull null
            val date = runCatching { LocalDate.parse(c[1]).toEpochDay() }.getOrNull() ?: return@mapNotNull null
            KhataEntry(key = keyOf(c[0]), name = c[0].trim(), date = date, type = guessType(c[0]),
                debit = c[2].trim().toLongOrNull() ?: 0, credit = c[3].trim().toLongOrNull() ?: 0,
                details = c.getOrElse(4) { "" }.trim(), mobile = mobileDigits(c.getOrElse(5) { "" }))
        }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        for (ch in line) when {
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { out += sb.toString(); sb.clear() }
            else -> sb.append(ch)
        }
        out += sb.toString()
        return out
    }

    /** One person's lines and balance: positive = still owes, negative = paid in advance. */
    data class Person(val key: String, val name: String, val balance: Long, val entries: List<KhataEntry>) {
        val mobile: String get() = entries.firstOrNull { it.mobile.isNotBlank() }?.mobile.orEmpty()
        val type: String get() = entries.firstOrNull()?.type ?: OTHER
        /** "" = not confirmed yet, DONE = confirmed, SKIP = left out. */
        val review: String get() = entries.firstOrNull()?.review.orEmpty()
        /** Still to be confirmed: anything not confirmed, including people skipped before. */
        val pending: Boolean get() = review != DONE
        /** Everything paid so far plus what is still owed. */
        val paidPlusDue: Long get() = entries.sumOf { it.credit } + balance.coerceAtLeast(0)
    }

    fun people(list: List<KhataEntry>): List<Person> = list.groupBy { it.key }.map { (key, lines) ->
        val sorted = lines.sortedWith(compareBy({ it.date }, { it.id }))
        Person(key, cleanName(lines.groupingBy { it.name }.eachCount().maxByOrNull { it.value }?.key ?: key),
            lines.sumOf { it.debit - it.credit }, sorted)
    }.sortedByDescending { it.balance }

    /** Over ₹20,000 paid plus due is a likely course student, so it is pre-picked in the review. */
    fun suggested(p: Person): String? = if (p.paidPlusDue > 20_000) COURSE else null

    /** Re-importing the book: each new line keeps the type, confirmation and number already given to the same person. Payments recorded in the app are not touched. */
    fun carryOver(old: List<KhataEntry>, fresh: List<KhataEntry>): List<KhataEntry> {
        val before = old.groupBy { it.key }
        return fresh.map { e ->
            val prev = before[e.key]?.firstOrNull() ?: return@map e
            val mobile = e.mobile.ifBlank { before[e.key].orEmpty().map { it.mobile }.firstOrNull { it.isNotBlank() }.orEmpty() }
            e.copy(type = prev.type, review = prev.review, mobile = mobile)
        }
    }

    /** Totals for the import preview. */
    data class Summary(val people: Int, val owing: Int, val owed: Long, val advance: Long, val debit: Long, val credit: Long)

    fun summary(list: List<KhataEntry>): Summary {
        val people = people(list)
        return Summary(
            people = people.size,
            owing = people.count { it.balance > 0 },
            owed = people.filter { it.balance > 0 }.sumOf { it.balance },
            advance = -people.filter { it.balance < 0 }.sumOf { it.balance },
            debit = list.sumOf { it.debit },
            credit = list.sumOf { it.credit },
        )
    }

    /** The reminder text, in Marathi. */
    fun reminder(name: String, amount: String, academy: String) =
        "नमस्कार $name, $academy कडून आठवण: तुमची $amount बाकी आहे. कृपया लवकर भरावी. धन्यवाद."
}
