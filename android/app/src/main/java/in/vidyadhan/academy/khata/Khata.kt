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

    /** Reads the CSV made from the Khatabook statement: name, date (yyyy-mm-dd), debit, credit, details. */
    fun parseCsv(text: String): List<KhataEntry> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).mapNotNull { line ->
            val c = splitCsv(line)
            if (c.size < 4) return@mapNotNull null
            val date = runCatching { LocalDate.parse(c[1]).toEpochDay() }.getOrNull() ?: return@mapNotNull null
            KhataEntry(key = keyOf(c[0]), name = c[0].trim(), date = date,
                debit = c[2].trim().toLongOrNull() ?: 0, credit = c[3].trim().toLongOrNull() ?: 0,
                details = c.getOrElse(4) { "" }.trim())
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
    }

    fun people(list: List<KhataEntry>): List<Person> = list.groupBy { it.key }.map { (key, lines) ->
        val sorted = lines.sortedWith(compareBy({ it.date }, { it.id }))
        Person(key, lines.groupingBy { it.name }.eachCount().maxByOrNull { it.value }?.key ?: key,
            lines.sumOf { it.debit - it.credit }, sorted)
    }.sortedByDescending { it.balance }

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
