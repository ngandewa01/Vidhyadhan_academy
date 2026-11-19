package `in`.vidyadhan.academy.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*
import kotlin.math.roundToInt

// ───────────────────────── status colours ─────────────────────────

/** Background and text/icon colour of a seat in each status. */
internal fun statusColors(s: SeatStatus): Pair<Brush, Color> = when (s) {
    SeatStatus.Empty -> Brush.linearGradient(listOf(Color.White, Color.White)) to Brand.Green600
    SeatStatus.Active -> Brush.linearGradient(listOf(Brand.Green500, Brand.Green700)) to Color.White
    SeatStatus.Ending -> Brush.linearGradient(listOf(Brand.Blue400, Brand.Blue600)) to Color.White
    SeatStatus.Partly -> Brush.linearGradient(listOf(Brand.Saffron400, Brand.Saffron600)) to Color.White
    SeatStatus.Expired -> Brush.linearGradient(listOf(Brand.Red500, Brand.Red700)) to Color.White
}

/** Dot colour of a status (filters, lists, badges). */
fun statusDot(s: SeatStatus): Color = when (s) {
    SeatStatus.Empty -> Brand.Slate400; SeatStatus.Active -> Brand.Green500; SeatStatus.Ending -> Brand.Blue500
    SeatStatus.Partly -> Brand.Saffron500; SeatStatus.Expired -> Brand.Red500
}

// ───────────────────────── seats page ─────────────────────────

/** "Seat management": rooms, their floor plans, and tapping seats to allocate them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeatsPage(vm: LibraryViewModel, onAdd: (Int) -> Unit, onOpen: (Long) -> Unit, switcher: @Composable () -> Unit = {}) {
    val members by vm.members.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    var roomId by rememberSaveable { mutableLongStateOf(0L) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var filter by remember { mutableStateOf<SeatStatus?>(null) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var seatSheet by remember { mutableStateOf<Int?>(null) }
    var deleteRoom by remember { mutableStateOf<LibraryRoom?>(null) }
    var rename by remember { mutableStateOf<LibraryRoom?>(null) }
    val active = members.filter { it.active }
    val bySeat = active.associateBy { it.seatNo }
    val t = today()
    val seats = allSeats(rooms, AppSettings.seatCount)
    val room = rooms.firstOrNull { it.id == roomId } ?: rooms.firstOrNull()
    LaunchedEffect(rooms.size) { if (rooms.none { it.id == roomId }) roomId = rooms.firstOrNull()?.id ?: 0L }
    fun newRoom() {
        val next = nextSeatNumber(rooms)
        val first = rooms.isEmpty()
        // The first room takes the seats the library already has; later rooms start with one row of 8.
        val plan = if (first) quickPlan(1, AppSettings.seatCount.coerceAtLeast(1), 8) else quickPlan(next, next + 7, 8)
        vm.addRoom(LibraryRoom(name = "Room ${rooms.size + 1}", sortOrder = (rooms.maxOfOrNull { it.sortOrder } ?: 0) + 1, layout = planText(plan))) { id ->
            roomId = id; editing = true; zoom = 1f
        }
    }

    HeroPage("Library", "${seats.size} seats · ${seats.count { it !in bySeat }} free", header = { switcher() }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            if (rooms.isEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Rounded.MeetingRoom, Brand.Blue600, 56.dp, filled = true)
                    Text("Draw your study room", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                    Text("Create a room and place the seats, desks and door the way they really are. Your ${AppSettings.seatCount} seats are put in rows to start with — drag them where they belong.",
                        style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    PrimaryButton("Create first room", ::newRoom, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                }
                Spacer(Modifier.height(14.dp))
                // Until a room exists, seats are shown in a simple grid so nothing is lost.
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusFilters(seats.groupingBy { n -> bySeat[n]?.let { memberStatus(it, t) } ?: SeatStatus.Empty }.eachCount(), seats.size, filter) { filter = it }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        seats.forEach { n ->
                            val m = bySeat[n]
                            SeatTile(n, m, typeForSeat(n, types)?.icon ?: "CHAIR", t, 58.dp, dim = filter != null && (m?.let { memberStatus(it, t) } ?: SeatStatus.Empty) != filter) {
                                if (m == null) seatSheet = n else onOpen(m.id)
                            }
                        }
                    }
                }
            } else {
                // Rooms, like tabs. The selected one is shown below.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ROOMS", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp), color = Brand.Slate500)
                    PillButton("New room", ::newRoom, Brand.Blue700, Icons.Rounded.Add)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rooms.forEach { r ->
                        val sel = r.id == room?.id
                        Row(Modifier.clip(RoundedCornerShape(16.dp)).background(if (sel) Brand.Blue700 else Color.White)
                            .border(1.dp, if (sel) Brand.Blue700 else Brand.Line, RoundedCornerShape(16.dp))
                            .clickable { roomId = r.id }.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall, color = if (sel) Color.White else Brand.Ink, maxLines = 1)
                            Spacer(Modifier.width(6.dp))
                            Text("${seatsIn(r.layout).size} seats", style = MaterialTheme.typography.labelMedium, color = if (sel) Brand.Blue100 else Brand.Slate400)
                            Box(Modifier.size(30.dp).clip(CircleShape).clickable { deleteRoom = r }, Alignment.Center) {
                                Icon(Icons.Rounded.DeleteOutline, "Delete ${r.name}", tint = if (sel) Brand.Blue100 else Brand.Red500, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (room != null) RoomCard(vm, room, rooms, bySeat, types, t, editing, { editing = it }, zoom, { zoom = it }, filter, { filter = it },
                    onRename = { rename = room }, onSeat = { n, m -> if (m == null) seatSheet = n else onOpen(m.id) })
                // Members whose seat is not drawn in any room
                val placed = seats.toSet()
                val stray = active.filter { it.seatNo !in placed }.sortedBy { it.seatNo }
                if (stray.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Not in any room", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                        Text("These members sit on seats that are not drawn yet. Add the seat to a room (Edit → Seat), or change the member's seat.",
                            style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stray.forEach { m -> SeatTile(m.seatNo, m, typeForSeat(m.seatNo, types)?.icon ?: "CHAIR", t, 58.dp) { onOpen(m.id) } }
                        }
                    }
                }
            }
        }
    }

    seatSheet?.let { n ->
        val ty = typeForSeat(n, types)
        val history by remember(n) { vm.seatHistory(n) }.collectAsStateWithLifecycle(emptyList())
        ModalBottomSheet({ seatSheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Green50).border(1.5.dp, Brand.Green500.copy(alpha = 0.4f), RoundedCornerShape(18.dp)), Alignment.Center) {
                        SeatIcon(ty?.icon ?: "CHAIR", Brand.Green600, 26.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Seat $n is free", style = MaterialTheme.typography.headlineSmall, color = Brand.Ink)
                        Text(listOfNotNull(roomOf(n, rooms)?.name, ty?.name, ty?.let { "${rupees(it.fee)}/month" }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    }
                }
                Text("Seat type", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                ChoicePills(types.map { it.name }, ty?.name ?: "", { name -> types.firstOrNull { it.name == name }?.let { vm.setSeatType(n, it) } },
                    captions = types.associate { it.name to "${rupees(it.fee)}/month" })
                PrimaryButton("Give seat $n to a member", { seatSheet = null; onAdd(n) }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.PersonAdd)
                SeatHistory(history, onOpen = { seatSheet = null; onOpen(it) })
            }
        }
    }
    deleteRoom?.let { r ->
        ConfirmDialog("Delete ${r.name}?", "The room drawing is removed. Members keep their seat numbers and show under “Not in any room”.", "Delete", danger = true,
            onConfirm = { vm.deleteRoom(r); deleteRoom = null }, onDismiss = { deleteRoom = null })
    }
    rename?.let { r ->
        var name by remember { mutableStateOf(r.name) }
        AlertDialog(onDismissRequest = { rename = null }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            title = { Text("Room name") },
            text = { Field(name, { name = it.take(30) }, "e.g. Ground floor, Hall A", Icons.Rounded.MeetingRoom) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveRoom(r.copy(name = name.trim())); rename = null }) { Text("Save", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ rename = null }) { Text("Cancel") } })
    }
}

/** One room: its header, the tools, and the floor plan. */
@Composable
private fun RoomCard(
    vm: LibraryViewModel, room: LibraryRoom, rooms: List<LibraryRoom>, bySeat: Map<Int, LibraryMember>, types: List<`in`.vidyadhan.academy.data.SeatType>, t: Long,
    editing: Boolean, setEditing: (Boolean) -> Unit, zoom: Float, setZoom: (Float) -> Unit, filter: SeatStatus?, setFilter: (SeatStatus?) -> Unit,
    onRename: () -> Unit, onSeat: (Int, LibraryMember?) -> Unit,
) {
    // The plan being edited; saved to the database after every change.
    var plan by remember(room.id) { mutableStateOf(planOf(room.layout)) }
    LaunchedEffect(room.layout) { if (!editing) plan = planOf(room.layout) }
    var selected by remember(room.id) { mutableStateOf<Int?>(null) }
    var addRow by remember { mutableStateOf(false) }
    fun save(p: FloorPlan = plan) { plan = p; vm.saveRoom(room.copy(layout = planText(p))) }
    val others = rooms.filter { it.id != room.id }
    val roomSeats = plan.items.filter { it.type == PlanItem.SEAT }
    val dup = (roomSeats.groupingBy { it.n }.eachCount().filterValues { it > 1 }.keys + roomSeats.map { it.n }.filter { n -> others.any { n in seatsIn(it.layout) } }).toSet()
    fun nextNumber() = nextSeatNumber(others, roomSeats.map { it.n })
    fun nextId() = (plan.items.maxOfOrNull { it.id } ?: 0) + 1
    /** Somewhere free near the top-left for a new item. */
    fun spot(): Pair<Float, Float> {
        for (y in 0 until (plan.h.toInt()).coerceAtLeast(1)) for (x in 0 until (plan.w.toInt()).coerceAtLeast(1)) {
            val fx = x + 0.3f; val fy = y + 0.3f
            if (plan.items.none { fx < it.x + it.width && fx + 1 > it.x && fy < it.y + it.height && fy + 1 > it.y }) return fx to fy
        }
        return 0.3f to (plan.h - 1.1f).coerceAtLeast(0f)
    }

    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onRename).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MeetingRoom, null, tint = Brand.Blue600, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Text(room.name, style = MaterialTheme.typography.titleLarge, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Edit, "Rename room", tint = Brand.Slate400, modifier = Modifier.size(16.dp))
            }
            Text("${roomSeats.size} seats", Modifier.clip(RoundedCornerShape(50)).background(Brand.Blue50).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = Brand.Blue700)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (editing) Brand.Saffron600 else Color.White)
                .border(1.5.dp, if (editing) Brand.Saffron600 else Brand.Blue700, RoundedCornerShape(50)).clickable { selected = null; setEditing(!editing) }
                .padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, null, tint = if (editing) Color.White else Brand.Blue700, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (editing) "Done" else "Edit layout", style = MaterialTheme.typography.labelLarge, color = if (editing) Color.White else Brand.Blue700)
            }
            ZoomButton(Icons.Rounded.ZoomOut, "Zoom out") { setZoom((zoom - 0.2f).coerceAtLeast(0.6f)) }
            Text("${(zoom * 100).roundToInt()}%", Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Slate600,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            ZoomButton(Icons.Rounded.ZoomIn, "Zoom in") { setZoom((zoom + 0.2f).coerceAtMost(2.4f)) }
        }
        if (!editing) {
            StatusFilters(roomSeats.map { it.n }.groupingBy { n -> bySeat[n]?.let { memberStatus(it, t) } ?: SeatStatus.Empty }.eachCount(), roomSeats.size, filter, onSelect = setFilter)
            Hint("Tap a seat to give it to a member or open the member. Use “Edit layout” to move seats, desks and doors.", Brand.Green50, Brand.Green700)
        } else {
            Hint("Drag anything to move it. Tap it to change or delete it. Changes are saved straight away.", Brand.Saffron50, Brand.Saffron800)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip(Icons.Rounded.EventSeat, "Seat") { val (x, y) = spot(); val id = nextId(); save(plan.copy(items = plan.items + PlanItem(id, PlanItem.SEAT, x, y, n = nextNumber()))); selected = id }
                ToolChip(Icons.Rounded.ViewWeek, "Row of seats") { addRow = true }
                ToolChip(Icons.Rounded.HorizontalRule, "Desk") { val (x, y) = spot(); val id = nextId(); save(plan.copy(items = plan.items + PlanItem(id, PlanItem.DESK, x, y, len = 3f))); selected = id }
                ToolChip(Icons.Rounded.DoorFront, "Door") { val id = nextId(); save(plan.copy(items = plan.items + PlanItem(id, PlanItem.DOOR, 0.4f, plan.h - PlanItem.DOOR_T))); selected = id }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SizeStepper("Width", plan.w, Modifier.weight(1f)) { save(plan.copy(w = it)) }
                SizeStepper("Length", plan.h, Modifier.weight(1f)) { save(plan.copy(h = it)) }
            }
            if (dup.isNotEmpty()) Hint("Seat ${dup.sorted().joinToString(", ")} is used twice. Tap the seat and change its number.", Brand.Red50, Brand.Red700)
        }
        FloorPlanView(plan, zoom, editing, selected, { selected = it },
            onMove = { id, dx, dy ->
                plan = plan.copy(items = plan.items.map { if (it.id == id) it.copy(x = (it.x + dx).coerceIn(-0.4f, plan.w - it.width + 0.4f), y = (it.y + dy).coerceIn(-0.4f, plan.h - it.height + 0.4f)) else it })
            },
            onMoveEnd = { save(plan.copy(items = plan.items.map { it.copy(x = snap(it.x), y = snap(it.y)) })) },
            seat = { item, size ->
                val m = bySeat[item.n]
                val st = m?.let { memberStatus(it, t) } ?: SeatStatus.Empty
                if (editing) EditSeat(item.n, size, item.id == selected, item.n in dup)
                else SeatTile(item.n, m, typeForSeat(item.n, types)?.icon ?: "CHAIR", t, size, dim = filter != null && st != filter) { onSeat(item.n, it) }
            })
        if (editing) selected?.let { id -> plan.items.firstOrNull { it.id == id } }?.let { item ->
            ItemPanel(item, onChange = { ch -> save(plan.copy(items = plan.items.map { if (it.id == item.id) ch else it })) },
                onDelete = { save(plan.copy(items = plan.items.filter { it.id != item.id })); selected = null },
                onCopy = {
                    val id = nextId()
                    val copy = item.copy(id = id, x = (item.x + item.width + 0.1f).coerceAtMost(plan.w - item.width), n = if (item.type == PlanItem.SEAT) nextNumber() else item.n)
                    save(plan.copy(items = plan.items + copy)); selected = id
                })
        }
    }
    if (addRow) {
        var count by remember { mutableStateOf("6") }
        var from by remember { mutableStateOf(nextNumber().toString()) }
        AlertDialog(onDismissRequest = { addRow = false }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            title = { Text("Add a row of seats") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Adds the seats in a line with a desk in front. Drag the row where it belongs afterwards.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                DigitsField("How many seats", count, 2, { count = it })
                DigitsField("First seat number", from, 3, { from = it })
            } },
            confirmButton = { TextButton(enabled = (count.toIntOrNull() ?: 0) in 1..30 && (from.toIntOrNull() ?: 0) > 0, onClick = {
                val c = count.toInt(); val f = from.toInt()
                val y = (plan.items.maxOfOrNull { it.y + it.height } ?: 0f) + 0.8f
                var id = nextId()
                val add = listOf(PlanItem(id++, PlanItem.DESK, 0.4f, y - 0.4f, len = c.toFloat())) + (0 until c).map { PlanItem(id++, PlanItem.SEAT, 0.4f + it, y, n = f + it) }
                save(plan.copy(w = maxOf(plan.w, c + 0.8f), h = maxOf(plan.h, y + 1.4f), items = plan.items + add)); addRow = false
            }) { Text("Add", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ addRow = false }) { Text("Cancel") } })
    }
}

private fun snap(v: Float) = (v * 4).roundToInt() / 4f

/** Base size of one grid cell at 100% zoom. */
private val CELL = 62.dp

/**
 * Draws the room: walls around [FloorPlan.w] × [FloorPlan.h] cells, desks, doors and seats.
 * In [editing] mode every item can be dragged ([onMove] gets the movement in cells) and tapped to select it.
 */
@Composable
fun FloorPlanView(
    plan: FloorPlan, zoom: Float, editing: Boolean, selected: Int?, onSelect: (Int?) -> Unit,
    onMove: (Int, Float, Float) -> Unit, onMoveEnd: () -> Unit, seat: @Composable (PlanItem, Dp) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wall = 6.dp
        // 100% = the whole room fits the card's width
        val fit = ((maxWidth - wall * 2) / plan.w.coerceAtLeast(1f)).coerceIn(28.dp, CELL)
        val cell = fit * zoom
        val cellPx = with(LocalDensity.current) { cell.toPx() }
        val move by rememberUpdatedState(onMove)
        val end by rememberUpdatedState(onMoveEnd)
        val pick by rememberUpdatedState(onSelect)
        Box(Modifier.fillMaxWidth().heightIn(max = 560.dp).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
            Box(Modifier.size(cell * plan.w + wall * 2, cell * plan.h + wall * 2)
                .shadow(2.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Brand.Slate800).padding(wall)
                .background(if (editing) Color(0xFFFFFDF8) else Brand.Slate50)
                .let { if (editing) it.clickable { pick(null) } else it }) {
                if (editing) Canvas(Modifier.matchParentSize()) {
                    val step = cellPx / 2
                    var x = 0f
                    while (x <= size.width) { var y = 0f; while (y <= size.height) { drawCircle(Brand.Slate300, 1.4f, Offset(x, y)); y += step }; x += step }
                }
                plan.items.sortedBy { if (it.type == PlanItem.SEAT) 1 else 0 }.forEach { item ->
                    key(item.id) {
                        val sel = editing && item.id == selected
                        Box(Modifier.offset { IntOffset((item.x * cellPx).roundToInt(), (item.y * cellPx).roundToInt()) }
                            .size(cell * item.width, cell * item.height)
                            .let { m ->
                                if (!editing) m else m.pointerInput(item.id, cellPx) {
                                    detectDragGestures(onDragStart = { pick(item.id) }, onDragEnd = { end() }, onDragCancel = { end() }) { change, d ->
                                        change.consume(); move(item.id, d.x / cellPx, d.y / cellPx)
                                    }
                                }.clickable { pick(item.id) }
                            }, Alignment.Center) {
                            when (item.type) {
                                PlanItem.SEAT -> seat(item, cell * 0.92f)
                                PlanItem.DESK -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(if (sel) Brand.Blue600 else Brand.Slate300)
                                    .border(if (sel) 2.dp else 0.dp, Brand.Blue800, RoundedCornerShape(50)))
                                else -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Brand.Saffron500)
                                    .border(if (sel) 2.dp else 0.dp, Brand.Blue800, RoundedCornerShape(4.dp)), Alignment.Center) {
                                    if (cell > 40.dp) Text("DOOR", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditSeat(n: Int, size: Dp, selected: Boolean, clash: Boolean) {
    Column(Modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(if (selected) Brand.Blue700 else Color.White)
        .border(if (selected || clash) 2.dp else 1.dp, when { clash -> Brand.Red500; selected -> Brand.Blue800; else -> Brand.Slate300 }, RoundedCornerShape(size * 0.25f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.EventSeat, null, tint = if (selected) Color.White else Brand.Slate400, modifier = Modifier.size(size * 0.36f))
        Text("$n", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.22f).coerceIn(9f, 15f).sp),
            color = if (selected) Color.White else Brand.Ink, maxLines = 1)
    }
}

/** Options for the selected item: seat number, desk length, turning, copying and deleting. */
@Composable
private fun ItemPanel(item: PlanItem, onChange: (PlanItem) -> Unit, onDelete: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Blue50).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(when (item.type) { PlanItem.SEAT -> "Seat ${item.n}"; PlanItem.DESK -> "Desk"; else -> "Door" }, style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
        if (item.type == PlanItem.SEAT) {
            var number by remember(item.id) { mutableStateOf(item.n.toString()) }
            DigitsField("Seat number", number, 3, { v -> number = v; v.toIntOrNull()?.takeIf { it > 0 }?.let { onChange(item.copy(n = it)) } })
        }
        if (item.type == PlanItem.DESK) SizeStepper("Length", item.len, Modifier.fillMaxWidth(), min = 1f) { onChange(item.copy(len = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.type != PlanItem.SEAT) ToolChip(Icons.Rounded.RotateRight, "Turn") { onChange(item.copy(rot = if (item.rot == 90) 0 else 90)) }
            ToolChip(Icons.Rounded.ContentCopy, "Copy") { onCopy() }
            ToolChip(Icons.Rounded.DeleteOutline, "Delete", Brand.Red600) { onDelete() }
        }
    }
}

@Composable
private fun SizeStepper(label: String, value: Float, modifier: Modifier, min: Float = 2f, onChange: (Float) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(50)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Brand.Slate600, maxLines = 1)
        Box(Modifier.size(36.dp).clip(CircleShape).clickable { onChange((value - 0.5f).coerceAtLeast(min)) }, Alignment.Center) { Icon(Icons.Rounded.Remove, "Less $label", tint = Brand.Blue700) }
        Text(if (value % 1f == 0f) "${value.toInt()}" else "%.1f".format(value), style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
        Box(Modifier.size(36.dp).clip(CircleShape).clickable { onChange((value + 0.5f).coerceAtMost(40f)) }, Alignment.Center) { Icon(Icons.Rounded.Add, "More $label", tint = Brand.Blue700) }
    }
}

@Composable
private fun ToolChip(icon: ImageVector, text: String, color: Color = Brand.Blue700, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Brand.Slate50).border(1.dp, Brand.Line, CircleShape).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, desc, tint = Brand.Blue700, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Hint(text: String, bg: Color, fg: Color) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp, vertical = 9.dp),
        style = MaterialTheme.typography.bodySmall, color = fg)
}

/** Tap-to-filter chips with counts. Tapping the selected one again shows everything. */
@Composable
internal fun StatusFilters(counts: Map<SeatStatus, Int>, all: Int, selected: SeatStatus?, withEmpty: Boolean = true, onSelect: (SeatStatus?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("All", all, null, selected == null) { onSelect(null) }
        SeatStatus.entries.filter { withEmpty || it != SeatStatus.Empty }.forEach { s ->
            FilterPill(s.label, counts[s] ?: 0, statusDot(s), selected == s) { onSelect(if (selected == s) null else s) }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (selected) Brand.Blue700 else Color.White)
        .border(1.dp, if (selected) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Brand.Slate800, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = if (selected) Brand.Blue100 else Brand.Slate400)
    }
}

/** A seat coloured by its member's status, with the seat number and the member's first name. */
@Composable
internal fun SeatTile(n: Int, m: LibraryMember?, icon: String, t: Long, size: Dp, dim: Boolean = false, onClick: (LibraryMember?) -> Unit) {
    val st = m?.let { memberStatus(it, t) } ?: SeatStatus.Empty
    val (bg, fg) = statusColors(st)
    val shape = RoundedCornerShape(size * 0.24f)
    val big = size >= 50.dp
    Column(
        Modifier.size(size).alpha(if (dim) 0.22f else 1f)
            .let { if (m != null) it.shadow(4.dp, shape, spotColor = Brand.Blue800.copy(alpha = 0.3f)) else it }
            .clip(shape).background(bg)
            .border(1.5.dp, if (m == null) Brand.Green500.copy(alpha = 0.45f) else Color.Transparent, shape)
            .pressable { onClick(m) }.padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        SeatIcon(icon, if (m == null) Brand.Green500 else fg, size * 0.3f, badgeBg = if (m == null) Color.White else statusDot(st), locker = m?.hasLocker == true)
        Text("$n", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.24f).coerceIn(9f, 16f).sp),
            color = if (m == null) Brand.Slate600 else fg, maxLines = 1)
        if (m != null && big) Text(m.name.substringBefore(' '), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = fg.copy(alpha = 0.92f), maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** Earlier members of a seat, newest first. */
@Composable
internal fun SeatHistory(history: List<LibraryMember>, onOpen: (Long) -> Unit, excludeId: Long = -1) {
    val list = history.filter { it.id != excludeId }
    SectionHeader("Seat history")
    if (list.isEmpty()) Text("Nobody else has used this seat yet.", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
    else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Brand.Line, RoundedCornerShape(20.dp))) {
        list.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 66.dp), color = Brand.Line)
            Row(Modifier.fillMaxWidth().clickable { onOpen(m.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.name, 40.dp, photo = m.photo)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                    Text("${`in`.vidyadhan.academy.students.shortDate(m.joinedOn)} → " + (m.leftOn?.let { `in`.vidyadhan.academy.students.shortDate(it) } ?: "now"),
                        style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                }
                StatusChip(if (m.active) "Current" else "Left", if (m.active) Tone.Good else Tone.Neutral)
            }
        }
    }
}
