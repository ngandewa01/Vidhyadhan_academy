package `in`.vidyadhan.academy.library

import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.data.MemberPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

// ───────────────────────── floor plan ─────────────────────────

/**
 * A room's floor plan, stored as JSON in [LibraryRoom.layout]. Sizes and positions are in grid cells
 * (one cell holds one seat). [w] × [h] is the size of the room inside its walls.
 */
@Serializable
data class FloorPlan(val w: Float = 10f, val h: Float = 6f, val items: List<PlanItem> = emptyList())

/**
 * One thing in the room. [type] is SEAT (number [n]), DESK (a table line [len] cells long) or DOOR.
 * [x], [y] is the top-left corner; [rot] is 0 (horizontal) or 90 (vertical) for desks and doors.
 */
@Serializable
data class PlanItem(val id: Int, val type: String, val x: Float, val y: Float, val n: Int = 0, val len: Float = 1f, val rot: Int = 0) {
    /** Width and height on the plan, in cells. */
    val width: Float get() = when (type) { SEAT -> 1f; DESK -> if (rot == 90) DESK_T else len; else -> if (rot == 90) DOOR_T else DOOR_L }
    val height: Float get() = when (type) { SEAT -> 1f; DESK -> if (rot == 90) len else DESK_T; else -> if (rot == 90) DOOR_L else DOOR_T }

    companion object {
        const val SEAT = "SEAT"; const val DESK = "DESK"; const val DOOR = "DOOR"
        const val DESK_T = 0.22f; const val DOOR_L = 1.2f; const val DOOR_T = 0.32f
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/** Reads a room layout: the JSON plan, or the older "rows of numbers" text (converted on the fly). */
fun planOf(layout: String): FloorPlan {
    val t = layout.trim()
    if (t.startsWith("{")) return runCatching { json.decodeFromString<FloorPlan>(t) }.getOrElse { FloorPlan() }
    if (t.isEmpty()) return FloorPlan()
    // Old format: one line per row; numbers are seats, "_" a space, "D" a door.
    val items = mutableListOf<PlanItem>()
    var id = 1
    val rows = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
    rows.forEachIndexed { r, line ->
        val y = 0.6f + r * 1.8f
        val cells = line.split(Regex("\\s+"))
        items += PlanItem(id++, PlanItem.DESK, 0.5f, y - 0.35f, len = cells.size.toFloat())
        cells.forEachIndexed { c, tok ->
            val x = 0.5f + c
            when {
                tok.equals("D", true) -> items += PlanItem(id++, PlanItem.DOOR, x, y + 0.3f)
                (tok.toIntOrNull() ?: 0) > 0 -> items += PlanItem(id++, PlanItem.SEAT, x, y, n = tok.toInt())
            }
        }
    }
    val w = (rows.maxOfOrNull { it.split(Regex("\\s+")).size } ?: 4) + 1f
    return FloorPlan(w = w, h = rows.size * 1.8f + 0.8f, items = items)
}

fun planText(p: FloorPlan): String = json.encodeToString(FloorPlan.serializer(), p)

/** Rows of seats [from]..[to], [perRow] to a row, each row with a desk in front of it. */
fun quickPlan(from: Int, to: Int, perRow: Int): FloorPlan {
    val nums = (minOf(from, to)..maxOf(from, to)).toList()
    val per = perRow.coerceIn(1, 30)
    val items = mutableListOf<PlanItem>()
    var id = 1
    nums.chunked(per).forEachIndexed { r, row ->
        val y = 0.9f + r * 2f
        items += PlanItem(id++, PlanItem.DESK, 0.6f, y - 0.4f, len = row.size.toFloat())
        row.forEachIndexed { c, n -> items += PlanItem(id++, PlanItem.SEAT, 0.6f + c, y, n = n) }
    }
    val rows = (nums.size + per - 1) / per
    // a door on the bottom wall
    val w = minOf(per, nums.size) + 1.2f
    val h = rows * 2f + 1.2f
    items += PlanItem(id, PlanItem.DOOR, (w - PlanItem.DOOR_L) / 2, h - PlanItem.DOOR_T)
    return FloorPlan(w = w, h = h, items = items)
}

fun seatsIn(layout: String): List<Int> = planOf(layout).items.filter { it.type == PlanItem.SEAT }.map { it.n }

/** Every seat number in the library: the seats placed in rooms, or 1..[count] while no rooms are set up. */
fun allSeats(rooms: List<LibraryRoom>, count: Int): List<Int> =
    if (rooms.isEmpty()) (1..count).toList() else rooms.flatMap { seatsIn(it.layout) }.distinct().sorted()

fun roomOf(n: Int, rooms: List<LibraryRoom>): LibraryRoom? = rooms.firstOrNull { n in seatsIn(it.layout) }

/** Seat numbers used more than once (in one room or across rooms). */
fun duplicateSeats(rooms: List<LibraryRoom>): Set<Int> =
    rooms.flatMap { seatsIn(it.layout) }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

/** Next free seat number across all rooms. */
fun nextSeatNumber(rooms: List<LibraryRoom>, extra: List<Int> = emptyList()): Int =
    ((rooms.flatMap { seatsIn(it.layout) } + extra).maxOrNull() ?: 0) + 1

/** "1-3, 7" */
fun seatRanges(seats: Collection<Int>): String = formatSeats(seats.toSet())

// ───────────────────────── member status ─────────────────────────

/** "Ending soon" = the paid period ends within this many days. */
const val ENDING_DAYS = 5

enum class SeatStatus(val label: String) {
    Empty("Empty"), Active("Active"), Ending("Ending soon"), Partly("Part paid"), Expired("Expired")
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

/** What is still owed after receiving [received] for a period costing [price], on top of an old [balance]. */
fun balanceAfter(balance: Long, price: Long, received: Long): Long = (balance + price - received).coerceAtLeast(0)

fun monthsLabel(n: Int) = if (n == 1) "1 month" else "$n months"
