package `in`.vidyadhan.academy.students

import `in`.vidyadhan.academy.data.Installment
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Splits [total] into [count] monthly installments; the remainder goes into the first one. */
fun buildPlan(total: Long, count: Int, firstDue: LocalDate): List<Installment> {
    require(count >= 1)
    val base = total / count
    val extra = total - base * count
    return (1..count).map { n ->
        Installment(
            studentId = 0, number = n,
            amount = base + if (n == 1) extra else 0,
            dueOn = firstDue.plusMonths((n - 1).toLong()).toEpochDay(),
        )
    }
}

private val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
fun Long.asDate(): String = LocalDate.ofEpochDay(this).format(fmt)
fun today(): Long = LocalDate.now().toEpochDay()
fun rupees(v: Long): String = (if (v < 0) "-₹" else "₹") + indianGroups(kotlin.math.abs(v))

/** 101000 → "1,01,000": last three digits, then groups of two. */
fun indianGroups(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val head = s.dropLast(3)
    return head.reversed().chunked(2).joinToString(",").reversed() + "," + s.takeLast(3)
}
fun maskAadhaar(a: String) = if (a.length >= 4) "XXXX XXXX " + a.takeLast(4) else a

fun feeMessage(marathi: Boolean, name: String, amount: Long, due: Long, @Suppress("UNUSED_PARAMETER") academy: String = ""): String =
    `in`.vidyadhan.academy.Templates.fill("fee", marathi, mapOf("name" to name, "amount" to rupees(amount), "date" to due.asDate()))

/** "Today", "Tomorrow", "in 3 days", "2 days ago"… */
fun relativeDay(day: Long, from: Long = today()): String = when (val d = day - from) {
    0L -> "Today"
    1L -> "Tomorrow"
    -1L -> "Yesterday"
    in 2..6 -> "in $d days"
    in -6..-2 -> "${-d} days ago"
    else -> day.asDate()
}

fun shortDate(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd MMM"))

fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** "today", "tomorrow", "in 3 days", "2 days ago" or "on 12 Dec". */
fun whenText(day: Long): String = relativeDay(day).let { if (it.first().isDigit()) "on ${shortDate(day)}" else it.lowercase() }
