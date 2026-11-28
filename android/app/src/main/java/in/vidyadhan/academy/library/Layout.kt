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
data class FloorPlan(
    val w: Float = 10f, val h: Float = 6f, val items: List<PlanItem> = emptyList(), val cols: Int = 0, val grid: List<GridItem> = emptyList(),
    /** Theatre / classroom seating: rows from the front, each a list of seat numbers; 0 = aisle or empty place. */
    val rows: List<List<Int>> = emptyList(),
    val front: String = "", // what is at the front: WHITEBOARD, WINDOW, DESK, STAGE
    val door: String = "", // FRONT_LEFT, FRONT_RIGHT, BACK_LEFT, BACK_RIGHT
    val window: String = "", // LEFT, RIGHT or empty
    /** Seats along the walls, like cubicles facing the wall: [top, right, bottom, left], each in clockwise order; 0 = a gap. */
    val walls: List<List<Int>> = emptyList(),
    /** Back-to-back double rows in the middle: first half of each list is the left column (top to bottom), the rest the right column. */
    val centre: List<List<Int>> = emptyList(),
)

/** The wall seating of a room: saved walls, or older rows split between the right and left walls (like students sitting back to back). */
fun wallsOf(p: FloorPlan): Pair<List<List<Int>>, List<List<Int>>> {
    if (p.walls.isNotEmpty() || p.centre.isNotEmpty()) return List(4) { p.walls.getOrNull(it).orEmpty() } to p.centre
    val seats = rowsOf(p).flatten().filter { it > 0 }
    val half = (seats.size + 1) / 2
    return listOf(emptyList(), seats.take(half), emptyList(), seats.drop(half).reversed()) to emptyList()
}

/**
 * New wall seating: [counts] seats on the top, right, bottom and left walls and [islands] middle double rows of [perIsland] seats.
 * Seats keep the numbers in [numbers] (in order, clockwise from the top left); extra places get numbers from [nextFrom].
 */
fun wallRoom(counts: List<Int>, islands: Int, perIsland: Int, numbers: List<Int>, nextFrom: Int): Pair<List<List<Int>>, List<List<Int>>> {
    var i = 0; var next = nextFrom
    fun take() = if (i < numbers.size) numbers[i++] else next++
    val walls = counts.map { c -> List(c.coerceAtLeast(0)) { take() } }
    val centre = List(islands.coerceAtLeast(0)) { List(perIsland.coerceAtLeast(0)) { take() } }
    return walls to centre
}

/** How [count] seats are set out in a new hall: [top, right, bottom, left] wall counts, middle double rows, seats per middle row. */
fun defaultWalls(count: Int): Triple<List<Int>, Int, Int> {
    val n = count.coerceAtLeast(1)
    if (n <= 12) return Triple(listOf(0, (n + 1) / 2, 0, n / 2), 0, 0)
    var right = minOf(8, (n + 1) / 2); var left = minOf(8, n - right)
    var rest = n - right - left
    val top = minOf(5, (rest + 1) / 2); val bottom = minOf(5, rest - top)
    rest -= top + bottom
    val island = minOf(rest, 16)
    rest -= island
    right += (rest + 1) / 2; left += rest / 2 // very big halls: longer side walls
    return Triple(listOf(top, right, bottom, left), if (island > 0) 1 else 0, island)
}

fun wallSeatList(walls: List<List<Int>>, centre: List<List<Int>>) = (walls.flatten() + centre.flatten()).filter { it > 0 }

/** Seats [numbers] in [rows] rows: [left] seats, an aisle, then [right] seats (no aisle when one side is 0). Extra places get the next numbers after [nextFrom]. */
fun theatreRows(rows: Int, left: Int, right: Int, numbers: List<Int>, nextFrom: Int): List<List<Int>> {
    var i = 0; var next = nextFrom
    fun take() = if (i < numbers.size) numbers[i++] else next++
    return (0 until rows.coerceAtLeast(1)).map {
        (0 until left).map { take() } + (if (left > 0 && right > 0) listOf(0) else emptyList()) + (0 until right).map { take() }
    }
}

/** The theatre rows of a room: saved ones, or made from an older grid/drawing (seats in reading order). */
fun rowsOf(p: FloorPlan): List<List<Int>> {
    if (p.rows.isNotEmpty()) return p.rows
    val seats: List<Int> = when {
        p.grid.isNotEmpty() -> p.grid.filter { it.kind == GridItem.SEAT }.map { it.n }
        else -> p.items.filter { it.type == PlanItem.SEAT }.sortedWith(compareBy({ (it.y * 2).toInt() }, { it.x })).map { it.n }
    }
    if (seats.isEmpty()) return emptyList()
    val per = (p.cols.takeIf { it > 0 } ?: 8).coerceIn(2, 12)
    val left = (per + 1) / 2
    return seats.chunked(per).map { r -> if (r.size > left) r.take(left) + 0 + r.drop(left) else r }
}

fun rowLetter(i: Int): String = if (i < 26) ('A' + i).toString() else "R${i + 1}"

/**
 * The room as a grid (like the owner's prototype): items fill rows of [FloorPlan.cols] columns in order.
 * [kind] SEAT (number [n]; XL seats take 2 columns) or FIXTURE ([icon] WINDOW, AC, FAN, DOOR, WATER, LOCKERS, WIFI; [span] columns).
 */
@Serializable
data class GridItem(val id: Int, val kind: String, val n: Int = 0, val span: Int = 1, val label: String = "", val icon: String = "") {
    companion object { const val SEAT = "SEAT"; const val FIXTURE = "FIXTURE" }
}

const val GRID_COLS = 6

/** The grid of a room: the saved one, or one made from an older drawing (seats in reading order, then the door). */
fun gridOf(p: FloorPlan): List<GridItem> {
    if (p.grid.isNotEmpty()) return p.grid
    var id = 1
    val seats = p.items.filter { it.type == PlanItem.SEAT }.sortedWith(compareBy({ (it.y * 2).toInt() }, { it.x }))
        .map { GridItem(id++, GridItem.SEAT, n = it.n) }
    val doors = p.items.filter { it.type == PlanItem.DOOR }.map { GridItem(id++, GridItem.FIXTURE, span = 2, label = "Main entrance", icon = "DOOR") }
    return seats + doors
}

fun fixtureLabel(icon: String) = when (icon) {
    "WINDOW" -> "Window light"; "AC" -> "Split AC"; "FAN" -> "Wall fan"; "DOOR" -> "Main entrance"; "WATER" -> "RO water & Wi-Fi"; "LOCKERS" -> "Lockers"; "WIFI" -> "Wi-Fi"
    else -> "Fixture"
}

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

fun seatsIn(layout: String): List<Int> = planOf(layout).let { p ->
    when {
        p.walls.isNotEmpty() || p.centre.isNotEmpty() -> wallSeatList(p.walls, p.centre)
        p.rows.isNotEmpty() -> p.rows.flatten().filter { it > 0 }
        p.grid.isNotEmpty() -> p.grid.filter { it.kind == GridItem.SEAT }.map { it.n }
        else -> p.items.filter { it.type == PlanItem.SEAT }.map { it.n }
    }
}

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

/**
 * New "paid up to" day. The member keeps their monthly date: [months] are added to the old end, also when they pay a few
 * days late (joined 21 Oct, due 21 Nov, paid 23 Nov: valid till 21 Dec). Only after a gap of more than a month does the
 * new period start from today.
 */
fun renewedTill(validTill: Long, today: Long, months: Int): Long {
    val end = LocalDate.ofEpochDay(validTill)
    val start = if (validTill >= today || !end.plusMonths(1).isBefore(LocalDate.ofEpochDay(today))) end else LocalDate.ofEpochDay(today)
    return start.plusMonths(months.toLong()).toEpochDay()
}

/**
 * Members renewed late by older versions had their monthly date moved to the payment day. Puts it back on the
 * joining-day cycle when the last fee was paid within a month after the missed due date. Returns the corrected end, or null.
 */
fun cycleFix(joinedOn: Long, validTill: Long, lastFeePaidOn: Long, months: Int): Long? {
    val paid = LocalDate.ofEpochDay(lastFeePaidOn)
    if (LocalDate.ofEpochDay(validTill) != paid.plusMonths(months.toLong())) return null
    val day = LocalDate.ofEpochDay(joinedOn).dayOfMonth
    var due = paid.withDayOfMonth(minOf(day, paid.lengthOfMonth()))
    if (due.isAfter(paid)) { val pm = paid.minusMonths(1); due = pm.withDayOfMonth(minOf(day, pm.lengthOfMonth())) }
    val late = paid.toEpochDay() - due.toEpochDay()
    if (late !in 1..31 || due.toEpochDay() <= joinedOn) return null
    return due.plusMonths(months.toLong()).toEpochDay()
}

/** What is still owed after receiving [received] for a period costing [price], on top of an old [balance]. */
fun balanceAfter(balance: Long, price: Long, received: Long): Long = (balance + price - received).coerceAtLeast(0)

fun monthsLabel(n: Int) = if (n == 1) "1 month" else "$n months"
