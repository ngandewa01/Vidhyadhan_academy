package `in`.vidyadhan.academy.library

import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.data.MemberPlan
import java.time.LocalDate

/**
 * Room layouts are stored as text: one line per row, cells separated by spaces.
 * A number is a seat, "_" is an empty space (aisle) and "D" is a door. Example:
 *
 *     16 17 18 19 _ 20 21 22 23
 *     30 29 28 D 27 26 25 24 43
 */
sealed interface Cell {
    data class Seat(val n: Int) : Cell
    data object Gap : Cell
    data object Door : Cell
}

fun parseLayout(text: String): List<List<Cell>> = text.lines().map { line ->
    line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.mapNotNull { tok ->
        when {
            tok == "_" -> Cell.Gap
            tok.equals("D", ignoreCase = true) -> Cell.Door
            (tok.toIntOrNull() ?: 0) > 0 -> Cell.Seat(tok.toInt())
            else -> null
        }
    }
}.filter { it.isNotEmpty() }

fun formatLayout(rows: List<List<Cell>>): String = rows.filter { it.isNotEmpty() }.joinToString("\n") { row ->
    row.joinToString(" ") { when (it) { is Cell.Seat -> "${it.n}"; Cell.Gap -> "_"; Cell.Door -> "D" } }
}

fun seatsIn(layout: String): List<Int> = parseLayout(layout).flatten().filterIsInstance<Cell.Seat>().map { it.n }

/** Seats [from]..[to], [perRow] to a row. */
fun quickLayout(from: Int, to: Int, perRow: Int): String =
    (minOf(from, to)..maxOf(from, to)).chunked(perRow.coerceAtLeast(1)).joinToString("\n") { it.joinToString(" ") }

/** Every seat number in the library: the seats placed in rooms, or 1..[count] while no rooms are set up. */
fun allSeats(rooms: List<LibraryRoom>, count: Int): List<Int> =
    if (rooms.isEmpty()) (1..count).toList() else rooms.flatMap { seatsIn(it.layout) }.distinct().sorted()

fun roomOf(n: Int, rooms: List<LibraryRoom>): LibraryRoom? = rooms.firstOrNull { n in seatsIn(it.layout) }

/** Seat numbers used in more than one room (a mistake the editor warns about). */
fun duplicateSeats(rooms: List<LibraryRoom>): Set<Int> =
    rooms.flatMap { seatsIn(it.layout).distinct() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

// ───────────────────────── member status ─────────────────────────

/** "Ending soon" = the paid period ends within this many days. */
const val ENDING_DAYS = 5

enum class SeatStatus(val label: String) {
    Empty("Empty"), Active("Active"), Ending("Ending soon"), Partly("Part paid"), Expired("Fee due")
}

fun memberStatus(m: LibraryMember, t: Long): SeatStatus = when {
    m.validTill < t -> SeatStatus.Expired
    m.balance > 0 -> SeatStatus.Partly
    m.validTill - t <= ENDING_DAYS -> SeatStatus.Ending
    else -> SeatStatus.Active
}

// ───────────────────────── renewals ─────────────────────────

/** One way to pay for a period: the member's own monthly fee, or a plan. */
data class RenewOption(val name: String, val months: Int, val price: Long, val plan: MemberPlan? = null)

/** The 1-month option first, then the active plans (those matching the member's locker choice first). */
fun renewOptions(monthlyFee: Long, plans: List<MemberPlan>, hasLocker: Boolean): List<RenewOption> =
    listOf(RenewOption("1 month", 1, monthlyFee)) +
        plans.filter { it.active }.sortedWith(compareBy({ it.withLocker != hasLocker }, { it.months }, { it.price }))
            .map { RenewOption(it.name, it.months, it.price, it) }

/** New "paid up to" day: [months] added to the old end, or to today if it had already ended. */
fun renewedTill(validTill: Long, today: Long, months: Int): Long =
    LocalDate.ofEpochDay(maxOf(validTill, today)).plusMonths(months.toLong()).toEpochDay()

/** What is still owed after receiving [received] for an [option] on top of an old [balance]. */
fun balanceAfter(balance: Long, price: Long, received: Long): Long = (balance + price - received).coerceAtLeast(0)
