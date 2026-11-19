package `in`.vidyadhan.academy.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.data.SeatType
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*

private val SkyBg = Color(0xFFF0F9FF); private val Sky = Color(0xFF38BDF8); private val SkyInk = Color(0xFF082F49)
private val Emerald = Color(0xFF34D399); private val EmeraldInk = Color(0xFF022C22)
private val Purple50 = Color(0xFFFAF5FF); private val Purple100 = Color(0xFFF3E8FF); private val Purple300 = Color(0xFFD8B4FE); private val Purple600 = Color(0xFF9333EA); private val Purple900 = Color(0xFF581C87)

fun isXl(n: Int, types: List<SeatType>) = typeForSeat(n, types)?.icon == "LEGROOM"

/** A new room laid out like the prototype: window and AC on top, seats, then the entrance and water. */
fun defaultGrid(seats: List<Int>): List<GridItem> {
    var id = 1
    return listOf(GridItem(id++, GridItem.FIXTURE, span = 4, label = "Window light", icon = "WINDOW"), GridItem(id++, GridItem.FIXTURE, span = 2, label = "Split AC", icon = "AC")) +
        seats.map { GridItem(id++, GridItem.SEAT, n = it) } +
        listOf(GridItem(id++, GridItem.FIXTURE, span = 3, label = "Main entrance", icon = "DOOR"), GridItem(id, GridItem.FIXTURE, span = 3, label = "RO water & Wi-Fi", icon = "WATER"))
}

/** "Abhyasika Room Plan": live map of the seats, and Edit Room to lay the room out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomPlanPage(vm: LibraryViewModel, onAdd: (Int) -> Unit, onOpen: (Long) -> Unit, onMembers: () -> Unit) {
    val members by vm.members.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    var roomId by rememberSaveable { mutableLongStateOf(0L) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var seatSheet by remember { mutableStateOf<Int?>(null) }
    var rename by remember { mutableStateOf<LibraryRoom?>(null) }
    var deleteRoom by remember { mutableStateOf<LibraryRoom?>(null) }
    val t = today()
    val active = members.filter { it.active }
    val bySeat = active.associateBy { it.seatNo }
    val seats = allSeats(rooms, AppSettings.seatCount)
    val free = seats.count { it !in bySeat }
    val potential = seats.sumOf { typeForSeat(it, types)?.fee ?: 0L } + active.sumOf { it.lockerFee }
    val room = rooms.firstOrNull { it.id == roomId } ?: rooms.firstOrNull()
    LaunchedEffect(rooms.size) { if (rooms.none { it.id == roomId }) roomId = rooms.firstOrNull()?.id ?: 0L }
    val xlType = types.firstOrNull { it.icon == "LEGROOM" }
    val normalType = types.firstOrNull { it.icon != "LEGROOM" }

    fun newRoom() {
        val first = rooms.isEmpty()
        val next = nextSeatNumber(rooms)
        val nums = if (first) (1..AppSettings.seatCount.coerceAtLeast(1)).toList() else (next until next + 12).toList()
        vm.addRoom(LibraryRoom(name = if (first) "Hall A" else "Hall ${'A' + rooms.size}", sortOrder = (rooms.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            layout = planText(FloorPlan(cols = GRID_COLS, grid = defaultGrid(nums))))) { roomId = it; editing = true }
    }

    HeroPage("Abhyasika room plan", "${seats.size} seats · $free free · Potential ${rupees(potential)}/mo",
        actions = {
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(2.dp)) {
                ModeChip("Live map", !editing, Color.White, Brand.Blue700) { editing = false; selected = null }
                ModeChip("Edit room", editing, Brand.Saffron500, Color.White) { editing = true }
            }
        },
        header = {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Legend(Emerald, "Free"); Legend(Sky, "Occupied"); Legend(Brand.Saffron500, "Expiring")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("XL", Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFA855F7)).padding(horizontal = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Spacer(Modifier.width(4.dp)); Text("Legroom", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // rooms
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                rooms.forEach { r ->
                    val sel = r.id == room?.id
                    Text("${r.name} · ${seatsIn(r.layout).size}", Modifier.clip(RoundedCornerShape(50)).background(if (sel) Brand.Blue700 else Color.White)
                        .border(1.dp, if (sel) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable { roomId = r.id; selected = null }.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else Brand.Slate600, maxLines = 1)
                }
                Text(if (rooms.isEmpty()) "+ Create room" else "+ New room", Modifier.clip(RoundedCornerShape(50)).border(1.dp, Brand.Blue700.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable(onClick = ::newRoom).padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = Brand.Blue700)
                Text("Members", Modifier.clip(RoundedCornerShape(50)).border(1.dp, Brand.Line, RoundedCornerShape(50)).clickable(onClick = onMembers)
                    .padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
            }

            if (room == null) {
                Text("No room drawn yet. Tap “+ Create room”: your ${AppSettings.seatCount} seats are placed in a hall with window, AC, entrance and water, ready to rearrange.",
                    style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
            } else {
                val plan = planOf(room.layout)
                val grid = gridOf(plan)
                val cols = plan.cols.takeIf { it > 0 } ?: GRID_COLS
                fun save(g: List<GridItem>, c: Int = cols) = vm.saveRoom(room.copy(layout = planText(FloorPlan(cols = c, grid = g))))
                fun nextId() = (grid.maxOfOrNull { it.id } ?: 0) + 1

                if (editing) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Amber50).border(1.dp, Brand.Saffron500.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ROOM & SEAT DESIGNER MODE", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron600)
                        Text("Tap an item to change it", style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DesignButton("+ Normal seat", Color.White, Brand.Slate800, Modifier.weight(1f)) {
                            val n = nextSeatNumber(rooms); val id = nextId()
                            normalType?.let { vm.setSeatType(n, it) }; save(grid + GridItem(id, GridItem.SEAT, n = n)); selected = id
                        }
                        DesignButton("+ XL legroom", Purple600, Color.White, Modifier.weight(1f)) {
                            val n = nextSeatNumber(rooms); val id = nextId()
                            xlType?.let { vm.setSeatType(n, it) }; save(grid + GridItem(id, GridItem.SEAT, n = n)); selected = id
                        }
                        var fixMenu by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            DesignButton("+ AC / Window", Color.White, Brand.Slate800, Modifier.fillMaxWidth()) { fixMenu = true }
                            DropdownMenu(fixMenu, { fixMenu = false }, containerColor = Color.White) {
                                listOf("WINDOW", "AC", "FAN", "DOOR", "WATER", "LOCKERS", "WIFI").forEach { ic ->
                                    DropdownMenuItem({ Text(fixtureLabel(ic)) }, {
                                        val id = nextId(); save(grid + GridItem(id, GridItem.FIXTURE, span = if (ic == "LOCKERS" || ic == "WINDOW") 4 else 2, label = fixtureLabel(ic), icon = ic))
                                        selected = id; fixMenu = false
                                    }, leadingIcon = { Icon(fixtureIcon(ic), null) })
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Columns", style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
                        SmallStep("−") { if (cols > 3) save(grid, cols - 1) }
                        Text("$cols", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
                        SmallStep("+") { if (cols < 10) save(grid, cols + 1) }
                        Spacer(Modifier.weight(1f))
                        Text("Rename", Modifier.clickable { rename = room }.padding(4.dp), style = MaterialTheme.typography.labelMedium, color = Brand.Blue700)
                        Text("Delete room", Modifier.clickable { deleteRoom = room }.padding(4.dp), style = MaterialTheme.typography.labelMedium, color = Brand.Red600)
                    }
                }

                // the blueprint canvas
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.Slate50).drawBehind {
                    val step = 16.dp.toPx(); var x = 0f
                    while (x < size.width) { var y = 0f; while (y < size.height) { drawCircle(Brand.Slate300, 1.dp.toPx() / 2, Offset(x, y)); y += step }; x += step }
                }.border(2.dp, Brand.Slate300, RoundedCornerShape(24.dp)).padding(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("${room.name} — study room".uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Slate400, maxLines = 1)
                        Text("${seatsIn(room.layout).count { it !in bySeat }} FREE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Slate400)
                    }
                    Spacer(Modifier.height(6.dp))
                    // pack items into rows of [cols] columns, in order (like a CSS grid)
                    val rowsList = mutableListOf<MutableList<Pair<GridItem, Int>>>()
                    var cur = mutableListOf<Pair<GridItem, Int>>(); var used = 0
                    grid.forEach { g ->
                        val sp = (if (g.kind == GridItem.SEAT) (if (isXl(g.n, types)) 2 else 1) else g.span).coerceIn(1, cols)
                        if (used + sp > cols) { rowsList += cur; cur = mutableListOf(); used = 0 }
                        cur += g to sp; used += sp
                    }
                    if (cur.isNotEmpty()) rowsList += cur
                    rowsList.forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            row.forEach { (g, sp) ->
                                Box(Modifier.weight(sp.toFloat()).fillMaxHeight().padding(3.dp)) {
                                    val sel = editing && selected == g.id
                                    if (g.kind == GridItem.SEAT) SeatCell(g.n, bySeat[g.n], isXl(g.n, types), typeForSeat(g.n, types)?.fee ?: 0, t, sel) {
                                        if (editing) selected = g.id else seatSheet = g.n
                                    } else FixtureCell(g, active, sel) { if (editing) selected = g.id }
                                }
                            }
                            val left = cols - row.sumOf { it.second }
                            if (left > 0) Spacer(Modifier.weight(left.toFloat()))
                        }
                    }
                }

                // the selected item (edit mode)
                val item = grid.firstOrNull { it.id == selected }
                if (editing && item != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val seatItem = item.kind == GridItem.SEAT
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SELECTED ELEMENT", style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
                            Text(if (seatItem) "${seatCode(item.n, isXl(item.n, types))} (${if (isXl(item.n, types)) "XL" else "NORMAL"})" else "${item.label} (FIXTURE)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink)
                        }
                        Text("Remove", Modifier.clip(RoundedCornerShape(10.dp)).background(Brand.Red50).clickable { save(grid.filter { it.id != item.id }); selected = null }
                            .padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Red600)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        if (seatItem) {
                            var number by remember(item.id) { mutableStateOf("${item.n}") }
                            DigitsField("Seat number", number, 3, { v -> number = v; v.toIntOrNull()?.takeIf { it > 0 }?.let { n -> save(grid.map { if (it.id == item.id) it.copy(n = n) else it }) } },
                                modifier = Modifier.weight(1f))
                            val xl = isXl(item.n, types)
                            Text(if (xl) "XL pod (2-col) ⇄" else "Normal (1-col) ⇄", Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Purple50).border(1.dp, Purple300, RoundedCornerShape(12.dp))
                                .clickable { (if (xl) normalType else xlType)?.let { vm.setSeatType(item.n, it) } }.padding(vertical = 14.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Purple900, textAlign = TextAlign.Center)
                        } else {
                            var label by remember(item.id) { mutableStateOf(item.label) }
                            Field(label, { v -> label = v.take(30); save(grid.map { if (it.id == item.id) it.copy(label = v.take(30)) else it }) }, "Label", modifier = Modifier.weight(1.4f))
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmallStep("−") { save(grid.map { if (it.id == item.id) it.copy(span = (it.span - 1).coerceAtLeast(1)) else it }) }
                                Text("${item.span} col", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
                                SmallStep("+") { save(grid.map { if (it.id == item.id) it.copy(span = (it.span + 1).coerceAtMost(cols)) else it }) }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Reorder position:", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
                        DesignButton("← Earlier", Brand.Slate50, Brand.Slate800) {
                            val i = grid.indexOfFirst { it.id == item.id }
                            if (i > 0) save(grid.toMutableList().also { java.util.Collections.swap(it, i, i - 1) })
                        }
                        DesignButton("Later →", Brand.Slate50, Brand.Slate800) {
                            val i = grid.indexOfFirst { it.id == item.id }
                            if (i in 0 until grid.lastIndex) save(grid.toMutableList().also { java.util.Collections.swap(it, i, i + 1) })
                        }
                    }
                }

                // members whose seat is not drawn in any room
                val placed = seats.toSet()
                val stray = active.filter { rooms.isNotEmpty() && it.seatNo !in placed }
                if (stray.isNotEmpty()) Text("Not in any room: " + stray.joinToString(", ") { "${it.name} (seat ${it.seatNo})" },
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Amber50).padding(10.dp), style = MaterialTheme.typography.bodySmall, color = Brand.Amber700)
            }
        }
    }

    // seat sheet (live map)
    seatSheet?.let { n ->
        val m = bySeat[n]
        val xl = isXl(n, types)
        val fee = typeForSeat(n, types)?.fee ?: 0L
        ModalBottomSheet({ seatSheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${if (xl) "Extra legroom XL pod" else "Normal cubicle seat"} · ${rupees(fee)}/mo".uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (xl) Purple600 else Brand.Blue700)
                Text("Seat ${seatCode(n, xl)}", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                if (m == null) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Green50).border(1.dp, Brand.Green100, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        Text("This seat is vacant & ready for allotment.", style = MaterialTheme.typography.titleSmall, color = Brand.Green700)
                        Text("Optional locker ${rupees(AppSettings.lockerFee.toLong())}/mo · Refundable deposit ${rupees(typeForSeat(n, types)?.deposit ?: 0)}",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Green700)
                    }
                    PrimaryButton("Assign student / walk-in to ${seatCode(n, xl)} →", { seatSheet = null; onAdd(n) }, Modifier.fillMaxWidth())
                } else {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SheetLine("Occupant", m.name)
                        SheetLine("Plan", m.planName.ifBlank { "Monthly" } + if (m.balance > 0) " · ${rupees(m.balance)} to pay" else "")
                        SheetLine("Assigned locker", if (m.hasLocker) lockerLabel(m) else "None", Purple600)
                        SheetLine("Valid until", m.validTill.asDate(), when { m.validTill < t -> Brand.Red600; m.validTill - t <= ENDING_DAYS -> Brand.Saffron500; else -> Brand.Green700 })
                    }
                    var vacate by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Renew +1 month (${rupees(m.monthlyFee)})", Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Brand.Green600).clickable {
                            vm.pay(m.copy(validTill = renewedTill(m.validTill, t, 1), planName = "1 month"), "FEE", m.monthlyFee, "Cash"); seatSheet = null
                        }.padding(vertical = 11.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
                        Text("Vacate seat", Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Brand.Red50).border(1.dp, Brand.Red100, RoundedCornerShape(12.dp)).clickable { vacate = true }
                            .padding(vertical = 11.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Brand.Red700, textAlign = TextAlign.Center)
                    }
                    Text("Open member page ›", Modifier.clickable { seatSheet = null; onOpen(m.id) }.padding(4.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Blue700)
                    if (vacate) ConfirmDialog("Vacate seat ${seatCode(n, xl)}?", "${m.name} moves to past members. Records are kept." +
                        if (m.depositPaid && m.depositReturnedOn == null && m.deposit > 0) " Return the ${rupees(m.deposit)} deposit on the member page." else "",
                        "Vacate", danger = true, onConfirm = { vm.update(m.copy(active = false, leftOn = t)); vacate = false; seatSheet = null }, onDismiss = { vacate = false })
                }
            }
        }
    }
    rename?.let { r ->
        var name by remember { mutableStateOf(r.name) }
        AlertDialog(onDismissRequest = { rename = null }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            title = { Text("Room name") },
            text = { Field(name, { name = it.take(30) }, "e.g. Hall A, Ground floor", Icons.Rounded.MeetingRoom) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveRoom(r.copy(name = name.trim())); rename = null }) { Text("Save", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ rename = null }) { Text("Cancel") } })
    }
    deleteRoom?.let { r ->
        ConfirmDialog("Delete ${r.name}?", "The room drawing is removed. Members keep their seat numbers.", "Delete", danger = true,
            onConfirm = { vm.deleteRoom(r); deleteRoom = null }, onDismiss = { deleteRoom = null })
    }
}

fun seatCode(n: Int, xl: Boolean) = (if (xl) "XL-" else "N-") + n.toString().padStart(2, '0')

@Composable
private fun SeatCell(n: Int, m: LibraryMember?, xl: Boolean, fee: Long, t: Long, selected: Boolean, onClick: () -> Unit) {
    val st = m?.let { memberStatus(it, t) }
    val (bg, edge, ink) = when (st) {
        null -> Triple(Brand.Green50, Emerald, EmeraldInk)
        SeatStatus.Expired -> Triple(Brand.Red50, Brand.Red500, Brand.Red700)
        SeatStatus.Ending, SeatStatus.Partly -> Triple(Brand.Amber50, Brand.Saffron500, Color(0xFF451A03))
        else -> Triple(SkyBg, Sky, SkyInk)
    }
    Column(Modifier.fillMaxSize().heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp)).background(bg)
        .border(if (selected) 3.dp else 2.dp, if (selected) Brand.Saffron500 else edge, RoundedCornerShape(12.dp)).pressable(onClick = onClick).padding(5.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(seatCode(n, xl), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = ink, maxLines = 1)
            if (xl) Text("XL", Modifier.clip(RoundedCornerShape(3.dp)).background(Purple600).padding(horizontal = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Text(m?.name?.substringBefore(' ') ?: "Free", style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rupees(fee), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f), maxLines = 1)
            if (m?.hasLocker == true) Text(if (m.lockerNo == "-") "L" else "L-${m.lockerNo}", Modifier.clip(RoundedCornerShape(3.dp)).background(Purple100).padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF7E22CE), maxLines = 1)
        }
    }
}

fun fixtureIcon(icon: String): ImageVector = when (icon) {
    "WINDOW" -> Icons.Rounded.WbSunny; "AC" -> Icons.Rounded.AcUnit; "FAN" -> Icons.Rounded.Air; "DOOR" -> Icons.Rounded.DoorFront
    "WATER" -> Icons.Rounded.WaterDrop; "LOCKERS" -> Icons.Rounded.Lock; "WIFI" -> Icons.Rounded.Wifi; else -> Icons.Rounded.CropSquare
}

@Composable
private fun FixtureCell(g: GridItem, active: List<LibraryMember>, selected: Boolean, onClick: () -> Unit) {
    val lockers = g.icon == "LOCKERS"
    val label = if (lockers) "Lockers L1–L${AppSettings.lockerCount} (${active.count { it.hasLocker }} used)" else g.label.ifBlank { fixtureLabel(g.icon) }
    Row(Modifier.fillMaxSize().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(if (lockers) Purple100 else Brand.Slate200.copy(alpha = 0.8f))
        .border(if (selected) 3.dp else 1.dp, if (selected) Brand.Saffron500 else if (lockers) Purple300 else Brand.Slate300, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(fixtureIcon(g.icon), null, tint = if (lockers) Purple900 else Brand.Slate600, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (lockers) Purple900 else Brand.Slate600, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Legend(color: Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color)); Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
}

@Composable
private fun ModeChip(text: String, selected: Boolean, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) bg else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (selected) fg else Brand.Blue100)
}

@Composable
private fun DesignButton(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(text, modifier.clip(RoundedCornerShape(12.dp)).background(bg).border(1.dp, if (bg == Color.White || bg == Brand.Slate50) Brand.Slate200 else bg, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = fg,
        textAlign = TextAlign.Center, maxLines = 1)
}

@Composable
private fun SmallStep(text: String, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White).border(1.dp, Brand.Slate200, CircleShape).clickable(onClick = onClick), Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Brand.Blue700)
    }
}

@Composable
private fun SheetLine(label: String, value: String, color: Color = Brand.Ink) {
    Row {
        Text(label + ":", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = color)
    }
}
