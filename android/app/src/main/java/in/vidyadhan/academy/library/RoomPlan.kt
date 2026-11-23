package `in`.vidyadhan.academy.library

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.data.SeatType
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*

fun isXl(n: Int, types: List<SeatType>) = typeForSeat(n, types)?.icon == "LEGROOM"

/** The four seat colours of the room plan. */
private enum class Look(val label: String, val color: Color) {
    Free("Available", Brand.Green500), Taken("Occupied", Brand.Blue600), Soon("Renewal / due soon", Brand.Saffron500), Due("Fee due", Brand.Red600)
}
private val XlEdge = Color(0xFF9333EA)

private fun lookOf(m: LibraryMember?, t: Long): Look = when (m?.let { memberStatus(it, t) }) {
    null -> Look.Free
    SeatStatus.Expired -> Look.Due
    SeatStatus.Ending, SeatStatus.Partly -> Look.Soon
    else -> Look.Taken
}


/** "Abhyasika room plan": cubicles along the walls (and optional back-to-back rows in the middle), coloured by who sits there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomPlanPage(vm: LibraryViewModel, onAdd: (Int) -> Unit, onOpen: (Long) -> Unit, onMembers: () -> Unit, onRenew: (Long) -> Unit = onOpen) {
    val members by vm.members.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    var roomId by rememberSaveable { mutableLongStateOf(0L) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var names by rememberSaveable { mutableStateOf(false) }
    var seatSheet by remember { mutableStateOf<Int?>(null) }
    var editPlace by remember { mutableStateOf<Pair<Int, Int>?>(null) } // side, place
    var rename by remember { mutableStateOf<LibraryRoom?>(null) }
    var deleteRoom by remember { mutableStateOf<LibraryRoom?>(null) }
    val t = today()
    val active = members.filter { it.active }
    val bySeat = active.associateBy { it.seatNo }
    val seats = allSeats(rooms, AppSettings.seatCount)
    val looks = seats.groupingBy { lookOf(bySeat[it], t) }.eachCount()
    val potential = seats.sumOf { typeForSeat(it, types)?.fee ?: 0L }
    val room = rooms.firstOrNull { it.id == roomId } ?: rooms.firstOrNull()
    LaunchedEffect(rooms.size) { if (rooms.none { it.id == roomId }) roomId = rooms.firstOrNull()?.id ?: 0L }
    val xlType = types.firstOrNull { it.icon == "LEGROOM" }
    val normalType = types.firstOrNull { it.icon != "LEGROOM" }

    fun newRoom() {
        val first = rooms.isEmpty()
        val count = if (first) AppSettings.seatCount.coerceAtLeast(1) else 16
        val start = if (first) 1 else nextSeatNumber(rooms)
        val (walls, islands, per) = defaultWalls(count)
        val (w, c) = wallRoom(walls, islands, per, emptyList(), start)
        vm.addRoom(LibraryRoom(name = if (first) "Hall A" else "Hall ${'A' + rooms.size}", sortOrder = (rooms.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            layout = planText(FloorPlan(walls = w, centre = c)))) { roomId = it; editing = true }
    }

    HeroPage("Abhyasika room plan", "${seats.size} seats · ${looks[Look.Free] ?: 0} free" + if (potential > 0) " · Potential ${rupees(potential)}/mo" else "",
        actions = {
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(2.dp)) {
                ModeChip("Live map", !editing, Color.White, Brand.Blue700) { editing = false }
                ModeChip("Edit room", editing, Brand.Saffron500, Color.White) { editing = true }
            }
        },
        header = {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Look.entries.forEach { l -> HeaderCount("${looks[l] ?: 0}", l.label.substringBefore(" /"), l.color) }
            }
        }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                rooms.forEach { r ->
                    val sel = r.id == room?.id
                    Chip("${r.name} · ${seatsIn(r.layout).size}", sel) { roomId = r.id }
                }
                Chip(if (rooms.isEmpty()) "+ Create room" else "+ New room", false, outline = Brand.Blue700, onClick = ::newRoom)
                Chip("Members", false, onClick = onMembers)
                if (!editing && room != null) Chip(if (names) "Hide names" else "Show names", names) { names = !names }
            }

            if (room == null) Text("No room yet. Tap “+ Create room”: your ${AppSettings.seatCount} seats are set out along the walls and a back-to-back row in the middle. Change it any time in Edit room.",
                style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
            else {
                val (walls, centre) = wallsOf(planOf(room.layout))
                fun save(w: List<List<Int>> = walls, c: List<List<Int>> = centre) = vm.saveRoom(room.copy(layout = planText(FloorPlan(walls = w, centre = c))))
                // a place is (side, index): sides 0-3 are the top, right, bottom and left walls, 4+ the middle double rows
                fun at(side: Int, i: Int) = (if (side < 4) walls.getOrNull(side) else centre.getOrNull(side - 4))?.getOrNull(i)
                fun setPlace(side: Int, i: Int, v: Int) {
                    fun upd(l: List<List<Int>>, k: Int) = l.mapIndexed { x, r -> if (x == k) r.mapIndexed { j, n -> if (j == i) v else n } else r }
                    if (side < 4) save(w = upd(walls, side)) else save(c = upd(centre, side - 4))
                }

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(22.dp)).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    WallRoom(walls, centre, bySeat, types, t, editing, names, editPlace,
                        onPlace = { side, i, n -> if (editing) editPlace = side to i else if (n > 0) seatSheet = n })
                    Spacer(Modifier.height(10.dp))
                    Legend()
                }

                if (editing) Designer(walls, centre, rooms.filter { it.id != room.id }, bySeat, onApply = { w, c -> save(w, c) },
                    onRename = { rename = room }, onDelete = { deleteRoom = room })

                // the selected seat or empty place (edit mode)
                editPlace?.let { (side, i) ->
                    val n = at(side, i)
                    val where = if (side < 4) listOf("top wall", "right wall", "bottom wall", "left wall")[side] else "middle row ${side - 3}"
                    if (n == null) editPlace = null
                    else ModalBottomSheet({ editPlace = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
                        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (n == 0) {
                                SheetTitle("Empty place · $where", "A gap between seats")
                                PrimaryButton("Add a seat here (no. ${nextSeatNumber(rooms)})", { setPlace(side, i, nextSeatNumber(rooms)); editPlace = null }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                            } else {
                                val m = bySeat[n]
                                val xl = isXl(n, types)
                                SheetTitle("Seat $n · $where", (if (xl) "Extra legroom (XL)" else "Normal seat") + (m?.let { " · ${it.name}" } ?: " · free"))
                                SoftButton(if (xl) "Make it a normal seat" else "Make it an XL legroom seat", { (if (xl) normalType else xlType)?.let { vm.setSeatType(n, it) } },
                                    Modifier.fillMaxWidth(), color = XlEdge, icon = Icons.Rounded.Chair)
                                var number by remember(n) { mutableStateOf("$n") }
                                val newN = number.toIntOrNull() ?: 0
                                val clash = newN != n && newN in allSeats(rooms, 0)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DigitsField("Seat number", number, 3, { number = it }, modifier = Modifier.weight(1f), isError = clash,
                                        supporting = if (clash) "Seat $newN already exists" else if (m != null && newN != n) "${m.name} moves with the seat" else null)
                                    PillButton("Save", { if (newN > 0 && !clash) { setPlace(side, i, newN); m?.let { vm.update(it.copy(seatNo = newN)) }; editPlace = null } }, Brand.Blue700)
                                }
                                SoftButton("Remove this seat", { setPlace(side, i, 0); editPlace = null }, Modifier.fillMaxWidth(), color = Brand.Red600, icon = Icons.Rounded.DeleteOutline)
                                if (m != null) Text("${m.name} keeps seat $n and shows under “Not in any room” until it is placed again.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                            }
                        }
                    }
                }

                val placed = seats.toSet()
                val stray = active.filter { it.seatNo !in placed }
                if (stray.isNotEmpty()) Text("Not in any room: " + stray.joinToString(", ") { "${it.name} (seat ${it.seatNo})" },
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Amber50).padding(10.dp), style = MaterialTheme.typography.bodySmall, color = Brand.Amber700)
            }
        }
    }

    // seat sheet (live map)
    seatSheet?.let { n ->
        val m = bySeat[n]
        val xl = isXl(n, types)
        val ty = typeForSeat(n, types)
        ModalBottomSheet({ seatSheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(("${if (xl) "Extra legroom XL seat" else "Normal seat"}" + ((m?.monthlyFee?.takeIf { it > 0 } ?: ty?.fee)?.takeIf { it > 0 }?.let { " · ${rupees(it)}/mo" } ?: "")).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (xl) XlEdge else Brand.Blue700)
                Text("Seat $n", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                if (m == null) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Green50).border(1.dp, Brand.Green100, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        Text("This seat is vacant & ready for allotment.", style = MaterialTheme.typography.titleSmall, color = Brand.Green700)
                        Text("Optional locker ${rupees(AppSettings.lockerFee.toLong())}/mo · Refundable deposit ${rupees(ty?.deposit ?: 0)}", style = MaterialTheme.typography.bodySmall, color = Brand.Green700)
                    }
                    PrimaryButton("Assign student / walk-in to seat $n →", { seatSheet = null; onAdd(n) }, Modifier.fillMaxWidth())
                } else {
                    val look = lookOf(m, t)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SheetLine("Occupant", m.name)
                        SheetLine("Plan", m.planName.ifBlank { "Monthly" } + if (m.balance > 0) " · ${rupees(m.balance)} to pay" else "")
                        SheetLine("Assigned locker", if (m.hasLocker) lockerLabel(m) else "None", XlEdge)
                        SheetLine("Valid until", m.validTill.asDate(), look.color)
                    }
                    var vacate by remember { mutableStateOf(false) }
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // opens the same renewal sheet as the member page: period, amount, Cash or UPI, confirm, receipt
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Brand.Green600).clickable { seatSheet = null; onRenew(m.id) }
                            .padding(vertical = 11.dp, horizontal = 6.dp), Alignment.Center) {
                            Text("Renew…", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Brand.Red50).border(1.dp, Brand.Red100, RoundedCornerShape(12.dp)).clickable { vacate = true }
                            .padding(vertical = 11.dp, horizontal = 6.dp), Alignment.Center) {
                            Text("Vacate seat", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Brand.Red700, textAlign = TextAlign.Center)
                        }
                    }
                    Text("Open member page ›", Modifier.clickable { seatSheet = null; onOpen(m.id) }.padding(4.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Blue700)
                    if (vacate) ConfirmDialog("Vacate seat $n?", "${m.name} moves to past members. Records are kept." +
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

/** Size of one seat along the wall, in "seat units" (XL seats are longer). */
private fun ext(n: Int, types: List<SeatType>) = if (n > 0 && isXl(n, types)) 1.5f else 1f
private const val GAP = 0.12f   // space between seats, in seat units
private const val DEPTH = 1.5f  // desk + chair, in seat units
private const val AISLE = 1.1f  // the narrowest walkway, in seat units

/**
 * The room seen from above: cubicles along the walls with the desk against the wall and the chair facing in,
 * and back-to-back double rows in the middle. Tapping a place calls [onPlace] (side, index, seat or 0).
 */
@Composable
private fun WallRoom(walls: List<List<Int>>, centre: List<List<Int>>, bySeat: Map<Int, LibraryMember>, types: List<SeatType>, t: Long, editing: Boolean,
                     names: Boolean, picked: Pair<Int, Int>?, onPlace: (Int, Int, Int) -> Unit) {
    if ((walls + centre).all { it.isEmpty() }) {
        Text("This room has no seats yet. Set the seats on each wall below.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500); return
    }
    fun run(l: List<Int>) = if (l.isEmpty()) 0f else l.sumOf { ext(it, types).toDouble() }.toFloat() + GAP * (l.size - 1)
    val top = walls[0]; val right = walls[1]; val bottom = walls[2].reversed(); val left = walls[3].reversed()
    val islands = centre.map { it.take((it.size + 1) / 2) to it.drop((it.size + 1) / 2) }
    val middle = (if (left.isNotEmpty()) DEPTH else 0f) + (if (right.isNotEmpty()) DEPTH else 0f) + islands.size * DEPTH * 2 + (islands.size + 1) * AISLE
    val units = maxOf(run(top), run(bottom), middle, 4f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wall = 5.dp
        val avail = maxWidth - (wall + 3.dp) * 2
        val s = (avail / units).coerceIn(24.dp, 38.dp)
        val gap = s * GAP
        val inner = maxOf(s * units, avail)
        // where each place is: (side, index in the saved list)
        @Composable
        fun place(side: Int, idx: Int, n: Int, deskAt: Int) {
            val sel = editing && picked == side to idx
            Seat(n, bySeat[n], ext(n, types), deskAt, s, isXl(n, types), t, editing, sel, names && !editing) { onPlace(side, idx, n) }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.border(wall, Brand.Blue700, RoundedCornerShape(10.dp)).padding(wall + 3.dp).width(inner)) {
                // top wall: desk on top, chair below
                if (top.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    top.forEachIndexed { i, n -> place(0, i, n, 0) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = gap * 2).heightIn(min = s * 1.4f).height(IntrinsicSize.Min)) {
                    if (left.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        left.forEachIndexed { i, n -> place(3, walls[3].size - 1 - i, n, 3) }
                    }
                    Aisle(Modifier.weight(1f).fillMaxHeight())
                    islands.forEachIndexed { k, (a, b) ->
                        // back to back: chairs face out, desks meet in the middle
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) { a.forEachIndexed { i, n -> place(4 + k, i, n, 1) } }
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) { b.forEachIndexed { i, n -> place(4 + k, a.size + i, n, 3) } }
                        }
                        Aisle(Modifier.weight(1f).fillMaxHeight())
                    }
                    if (right.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        right.forEachIndexed { i, n -> place(1, i, n, 1) }
                    }
                }
                // bottom wall: chair on top, desk against the wall
                if (bottom.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    bottom.forEachIndexed { i, n -> place(2, walls[2].size - 1 - i, n, 2) }
                }
            }
        }
    }
}

/** The walkway between seat rows: a faint dashed look. */
@Composable
private fun Aisle(modifier: Modifier) {
    Canvas(modifier.padding(horizontal = 4.dp)) {
        val x = size.width / 2
        drawLine(Brand.Slate200, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
    }
}

/**
 * One cubicle: a desk (with the seat number) against the wall on side [deskAt] (0 top, 1 right, 2 bottom, 3 left) and the chair in front of it.
 * Colour shows who sits there; XL seats are longer with a violet edge; a key badge means the student has a locker. A 0 is an empty place.
 */
@Composable
private fun Seat(n: Int, m: LibraryMember?, along: Float, deskAt: Int, s: Dp, xl: Boolean, t: Long, editing: Boolean, selected: Boolean, showName: Boolean, onClick: () -> Unit) {
    val horizontal = deskAt == 0 || deskAt == 2 // seats on the top/bottom walls sit side by side
    val w = if (horizontal) s * along else s * DEPTH
    val h = if (horizontal) s * DEPTH else s * along
    if (n <= 0) {
        Box(Modifier.size(w, h).padding(2.dp).let { if (editing) it.clip(RoundedCornerShape(6.dp)).border(1.dp, if (selected) Brand.Saffron500 else Brand.Slate200, RoundedCornerShape(6.dp)).clickable(onClick = onClick) else it },
            Alignment.Center) { if (editing) Text("+", color = Brand.Slate300, style = MaterialTheme.typography.labelMedium) }
        return
    }
    val look = lookOf(m, t)
    val free = look == Look.Free
    val fill = if (free) Color.White else look.color
    val edge = look.color
    val ink = if (free) Brand.Green700 else Color.White
    val deskShape = RoundedCornerShape(6.dp)
    val desk: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.clip(deskShape).background(fill).border(2.dp, edge, deskShape)
            .let { if (xl) it.border(2.dp, XlEdge, deskShape).padding(2.dp).border(1.5.dp, edge, deskShape) else it }, Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = cappedSp((s.value * 0.32f).coerceIn(9f, 13f), 1f)),
                    color = ink, maxLines = 1, softWrap = false)
                if (showName && m != null) Text(m.name.substringBefore(' ').take(7), style = MaterialTheme.typography.labelSmall.copy(fontSize = cappedSp(7f, 1f), lineHeight = 8.sp),
                    color = ink, maxLines = 1, softWrap = false)
                else if (xl) Text("XL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = cappedSp(7f, 1f), lineHeight = 8.sp),
                    color = if (free) XlEdge else Color.White.copy(alpha = 0.9f), maxLines = 1)
            }
        }
    }
    val chair: @Composable () -> Unit = {
        val cs = RoundedCornerShape(50)
        Box(Modifier.size(if (horizontal) s * 0.6f else s * 0.36f, if (horizontal) s * 0.36f else s * 0.6f).clip(cs)
            .background(if (free) Color.White else edge).border(1.5.dp, edge, cs))
    }
    Box(Modifier.size(w, h).semantics { contentDescription = "Seat $n" }.clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick)
        .let { if (selected) it.border(2.dp, Brand.Saffron500, RoundedCornerShape(7.dp)) else it }.padding(1.dp)) {
        val deskMod = if (horizontal) Modifier.fillMaxWidth().height(s * 0.98f) else Modifier.fillMaxHeight().width(s * 0.98f)
        when (deskAt) {
            0 -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) { desk(deskMod); chair() }
            2 -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom)) { chair(); desk(deskMod) }
            3 -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { desk(deskMod); chair() }
            else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End)) { chair(); desk(deskMod) }
        }
        if (m?.hasLocker == true) Box(Modifier.align(if (deskAt == 1) Alignment.TopEnd else if (deskAt == 3) Alignment.TopStart else if (deskAt == 2) Alignment.BottomEnd else Alignment.TopEnd)
            .size(14.dp).clip(CircleShape).background(Color.White).border(1.dp, Color(0xFFF59E0B), CircleShape), Alignment.Center) {
            Icon(Icons.Rounded.Key, "Locker", tint = Color(0xFFB45309), modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun Legend() {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Look.entries.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp, 12.dp).clip(RoundedCornerShape(3.dp)).background(if (l == Look.Free) Color.White else l.color).border(2.dp, l.color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(4.dp)); Text(l.label, style = MaterialTheme.typography.labelSmall, color = Brand.Slate600)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp, 12.dp).clip(RoundedCornerShape(3.dp)).border(2.dp, XlEdge, RoundedCornerShape(3.dp))); Spacer(Modifier.width(4.dp))
            Text("XL extra legroom", style = MaterialTheme.typography.labelSmall, color = Brand.Slate600)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White).border(1.dp, Color(0xFFF59E0B), CircleShape), Alignment.Center) {
                Icon(Icons.Rounded.Key, null, tint = Color(0xFFB45309), modifier = Modifier.size(10.dp))
            }
            Spacer(Modifier.width(4.dp)); Text("Has a locker", style = MaterialTheme.typography.labelSmall, color = Brand.Slate600)
        }
    }
}

/** Edit room: how many seats on each wall and how many back-to-back double rows in the middle. */
@Composable
private fun Designer(walls: List<List<Int>>, centre: List<List<Int>>, others: List<LibraryRoom>, bySeat: Map<Int, LibraryMember>,
                     onApply: (List<List<Int>>, List<List<Int>>) -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val key = walls to centre
    val counts = remember(key) { mutableStateListOf(*walls.map { it.size }.toTypedArray()) }
    var islands by remember(key) { mutableIntStateOf(centre.size) }
    var perIsland by remember(key) { mutableIntStateOf(centre.firstOrNull()?.size ?: 8) }
    val current = wallSeatList(walls, centre)
    val capacity = counts.sum() + islands * perIsland
    val dropped = current.drop(capacity).filter { it in bySeat }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Amber50).border(1.dp, Brand.Saffron500.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ROOM & SEAT DESIGNER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron600)
        Text("Seats sit along the walls, facing the wall. Tap a seat to make it XL, renumber or remove it. Tap an empty place to add a seat.",
            style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
        Text("Seats on each wall", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
        listOf("Top wall", "Right wall", "Bottom wall", "Left wall").forEachIndexed { i, l -> Counter(l, counts[i], 0..20) { counts[i] = it } }
        Text("Middle of the room", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
        Counter("Back-to-back double rows", islands, 0..3) { islands = it }
        if (islands > 0) Counter("Seats in each double row", perIsland, 2..30) { perIsland = it }
        if (dropped.isNotEmpty()) Text("Seat ${dropped.joinToString(", ")} is taken and would leave the plan.", style = MaterialTheme.typography.bodySmall, color = Brand.Red600)
        PrimaryButton("Apply layout ($capacity seats)", {
            val (w, c) = wallRoom(counts.toList(), islands, perIsland, current, nextSeatNumber(others, current))
            onApply(w, c)
        }, Modifier.fillMaxWidth(), enabled = capacity > 0, icon = Icons.Rounded.Check)
        Text("Seats are numbered clockwise from the top-left corner, then the middle rows.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Rename room", Modifier.clickable(onClick = onRename).padding(4.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Blue700)
            Text("Delete room", Modifier.clickable(onClick = onDelete).padding(4.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Red600)
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate800)
        Step("−", "$label fewer") { if (value > range.first) onChange(value - 1) }
        Text("$value", Modifier.width(36.dp), style = MaterialTheme.typography.titleSmall, color = Brand.Ink, textAlign = TextAlign.Center)
        Step("+", "$label more") { if (value < range.last) onChange(value + 1) }
    }
}

@Composable
private fun Step(text: String, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).semantics { contentDescription = desc }.clip(CircleShape).background(Color.White).border(1.dp, Brand.Slate200, CircleShape).clickable(onClick = onClick), Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Brand.Blue700)
    }
}

@Composable
private fun Choice(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, l) -> Chip(l, k == selected) { onPick(k) } }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, outline: Color = Brand.Line, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(if (selected) Brand.Blue700 else Color.White)
        .border(1.dp, if (selected) Brand.Blue700 else outline.copy(alpha = if (outline == Brand.Line) 1f else 0.5f), RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else if (outline == Brand.Line) Brand.Slate600 else outline, maxLines = 1)
}

@Composable
private fun HeaderCount(value: String, label: String, dot: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Blue100, maxLines = 1)
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) bg else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (selected) fg else Brand.Blue100)
}

@Composable
private fun SheetLine(label: String, value: String, color: Color = Brand.Ink) {
    Row {
        Text("$label:", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = color)
    }
}
