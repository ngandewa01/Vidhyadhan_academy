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

private fun frontLabel(f: String) = when (f) { "WINDOW" -> "Window"; "DESK" -> "Teacher's desk"; "STAGE" -> "Stage"; else -> "Whiteboard" }

/** "Abhyasika room plan": seats in rows facing the front, like a classroom or theatre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomPlanPage(vm: LibraryViewModel, onAdd: (Int) -> Unit, onOpen: (Long) -> Unit, onMembers: () -> Unit) {
    val members by vm.members.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    var roomId by rememberSaveable { mutableLongStateOf(0L) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var names by rememberSaveable { mutableStateOf(false) }
    var seatSheet by remember { mutableStateOf<Int?>(null) }
    var editPlace by remember { mutableStateOf<Pair<Int, Int>?>(null) } // row, place
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
        val numbers = if (first) (1..count).toList() else (nextSeatNumber(rooms) until nextSeatNumber(rooms) + count).toList()
        val rowsN = (count + 7) / 8
        vm.addRoom(LibraryRoom(name = if (first) "Hall A" else "Hall ${'A' + rooms.size}", sortOrder = (rooms.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            layout = planText(FloorPlan(rows = theatreRows(rowsN, 4, 4, numbers, numbers.last() + 1).map { r -> r.map { if (it > numbers.last()) 0 else it } },
                front = "WHITEBOARD", door = "BACK_RIGHT")))) { roomId = it; editing = true }
    }

    HeroPage("Abhyasika room plan", "${seats.size} seats · ${looks[Look.Free] ?: 0} free · Potential ${rupees(potential)}/mo",
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

            if (room == null) Text("No room yet. Tap “+ Create room”: your ${AppSettings.seatCount} seats are set out in rows of 8 with a centre aisle, facing the whiteboard. Then change it in Edit room.",
                style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
            else {
                val plan = planOf(room.layout)
                val rows = rowsOf(plan)
                fun save(newRows: List<List<Int>> = rows, front: String = plan.front, door: String = plan.door, window: String = plan.window) =
                    vm.saveRoom(room.copy(layout = planText(FloorPlan(rows = newRows, front = front, door = door, window = window))))

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(22.dp)).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Theatre(rows, plan, bySeat, types, t, editing, names, editPlace,
                        onSeat = { r, c, n -> if (editing) editPlace = r to c else seatSheet = n },
                        onGap = { r, c -> if (editing) editPlace = r to c })
                    Spacer(Modifier.height(10.dp))
                    Legend()
                }

                if (editing) Designer(rows, plan, rooms.filter { it.id != room.id }, bySeat, onApply = { save(it) },
                    onFront = { save(front = it) }, onDoor = { save(door = it) }, onWindow = { save(window = it) },
                    onRename = { rename = room }, onDelete = { deleteRoom = room })

                // the selected seat or empty place (edit mode)
                editPlace?.let { (ri, ci) ->
                    val n = rows.getOrNull(ri)?.getOrNull(ci)
                    if (n == null) editPlace = null
                    else ModalBottomSheet({ editPlace = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
                        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            fun setPlace(v: Int) = save(rows.mapIndexed { i, r -> if (i == ri) r.mapIndexed { j, x -> if (j == ci) v else x } else r })
                            if (n == 0) {
                                SheetTitle("Empty place · row ${rowLetter(ri)}", "An aisle or a gap between seats")
                                PrimaryButton("Add a seat here (no. ${nextSeatNumber(rooms)})", { setPlace(nextSeatNumber(rooms)); editPlace = null }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                            } else {
                                val m = bySeat[n]
                                val xl = isXl(n, types)
                                SheetTitle("Seat $n · row ${rowLetter(ri)}", (if (xl) "Extra legroom (XL)" else "Normal seat") + (m?.let { " · ${it.name}" } ?: " · free"))
                                SoftButton(if (xl) "Make it a normal seat" else "Make it an XL legroom seat", { (if (xl) normalType else xlType)?.let { vm.setSeatType(n, it) } },
                                    Modifier.fillMaxWidth(), color = XlEdge, icon = Icons.Rounded.Chair)
                                var number by remember(n) { mutableStateOf("$n") }
                                val newN = number.toIntOrNull() ?: 0
                                val clash = newN != n && newN in allSeats(rooms, 0)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DigitsField("Seat number", number, 3, { number = it }, modifier = Modifier.weight(1f), isError = clash,
                                        supporting = if (clash) "Seat $newN already exists" else if (m != null && newN != n) "${m.name} moves with the seat" else null)
                                    PillButton("Save", { if (newN > 0 && !clash) { setPlace(newN); m?.let { vm.update(it.copy(seatNo = newN)) }; editPlace = null } }, Brand.Blue700)
                                }
                                SoftButton("Remove this seat", { setPlace(0); editPlace = null }, Modifier.fillMaxWidth(), color = Brand.Red600, icon = Icons.Rounded.DeleteOutline)
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
                Text("${if (xl) "Extra legroom XL seat" else "Normal seat"} · ${rupees(ty?.fee ?: 0)}/mo".uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Renew +1 month (${rupees(m.monthlyFee)})", Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Brand.Green600).clickable {
                            vm.pay(m.copy(validTill = renewedTill(m.validTill, t, 1), planName = "1 month"), "FEE", m.monthlyFee, "Cash"); seatSheet = null
                        }.padding(vertical = 11.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
                        Text("Vacate seat", Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Brand.Red50).border(1.dp, Brand.Red100, RoundedCornerShape(12.dp)).clickable { vacate = true }
                            .padding(vertical = 11.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Brand.Red700, textAlign = TextAlign.Center)
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

/** The room: the front board on top, rows of chairs with a letter at each end, the aisle, the entrance and the window wall. */
@Composable
private fun Theatre(rows: List<List<Int>>, plan: FloorPlan, bySeat: Map<Int, LibraryMember>, types: List<SeatType>, t: Long, editing: Boolean, names: Boolean,
                    picked: Pair<Int, Int>?, onSeat: (Int, Int, Int) -> Unit, onGap: (Int, Int) -> Unit) {
    if (rows.isEmpty()) { Text("This room has no seats yet. Use “Layout” below.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500); return }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val places = rows.maxOf { it.size }.coerceAtLeast(1)
        val label = 16.dp
        val windowW = if (plan.window.isNotBlank()) 12.dp else 0.dp
        val seatW = ((maxWidth - label * 2 - windowW - 4.dp) / places).coerceIn(26.dp, 46.dp)
        val width = seatW * places + label * 2
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                if (plan.window == "LEFT") WindowWall()
                Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
                    Door(plan.door, front = true)
                    FrontBoard(frontLabel(plan.front), width - label * 2)
                    Spacer(Modifier.height(12.dp))
                    rows.forEachIndexed { ri, row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RowTag(rowLetter(ri), label)
                            row.forEachIndexed { ci, n ->
                                val sel = editing && picked == ri to ci
                                if (n > 0) Chair(n, bySeat[n], isXl(n, types), t, seatW, sel, names && !editing) { onSeat(ri, ci, n) }
                                else Box(Modifier.size(seatW, seatW * 0.95f).padding(3.dp).let { m ->
                                    if (editing) m.clip(RoundedCornerShape(8.dp)).border(1.dp, if (sel) Brand.Saffron500 else Brand.Slate200, RoundedCornerShape(8.dp)).clickable { onGap(ri, ci) } else m
                                })
                            }
                            repeat(places - row.size) { Spacer(Modifier.width(seatW)) }
                            RowTag(rowLetter(ri), label)
                        }
                        Spacer(Modifier.height(if (names && !editing) 2.dp else 6.dp))
                    }
                    Door(plan.door, front = false)
                }
                if (plan.window == "RIGHT") WindowWall()
            }
        }
    }
}

/** One chair: backrest, armrests and cushion, coloured by who sits there; seat number on the backrest. */
@Composable
private fun Chair(n: Int, m: LibraryMember?, xl: Boolean, t: Long, w: Dp, selected: Boolean, showName: Boolean, onClick: () -> Unit) {
    val look = lookOf(m, t)
    val free = look == Look.Free
    val body = look.color
    val shade = lerp(body, Color.Black, 0.22f)
    Column(Modifier.width(w).semantics { contentDescription = "Seat $n" }.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        .let { if (selected) it.border(2.dp, Brand.Saffron500, RoundedCornerShape(8.dp)) else it }.padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(w - 4.dp, (w - 4.dp) * 0.9f), Alignment.TopCenter) {
            Canvas(Modifier.matchParentSize()) {
                val cw = size.width; val ch = size.height
                fun part(x: Float, y: Float, pw: Float, ph: Float, r: Float, fill: Color) {
                    if (free) {
                        drawRoundRect(Color.White, Offset(x, y), Size(pw, ph), CornerRadius(r))
                        drawRoundRect(body, Offset(x, y), Size(pw, ph), CornerRadius(r), style = Stroke(1.6.dp.toPx()))
                    } else drawRoundRect(fill, Offset(x, y), Size(pw, ph), CornerRadius(r))
                }
                part(cw * 0.14f, 0f, cw * 0.72f, ch * 0.64f, cw * 0.2f, body) // backrest
                part(0f, ch * 0.40f, cw * 0.15f, ch * 0.52f, cw * 0.07f, shade) // arms
                part(cw * 0.85f, ch * 0.40f, cw * 0.15f, ch * 0.52f, cw * 0.07f, shade)
                part(cw * 0.12f, ch * 0.62f, cw * 0.76f, ch * 0.3f, cw * 0.1f, shade) // cushion
                if (xl) drawRoundRect(XlEdge, Offset(cw * 0.14f, 0f), Size(cw * 0.72f, ch * 0.64f), CornerRadius(cw * 0.2f), style = Stroke(2.2.dp.toPx()))
                if (m?.hasLocker == true) drawCircle(Color(0xFF7E22CE), cw * 0.08f, Offset(cw * 0.9f, ch * 0.12f))
            }
            Text("$n", Modifier.padding(top = (w - 4.dp) * 0.1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold,
                fontSize = cappedSp((w.value * 0.3f).coerceIn(9f, 14f), 1f)), color = if (free) Brand.Green700 else Color.White, maxLines = 1, softWrap = false)
        }
        if (showName) Text(m?.name?.substringBefore(' ') ?: "", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Brand.Slate600, maxLines = 1)
    }
}

/** The front of the room: a curved board, like a theatre screen. */
@Composable
private fun FrontBoard(label: String, width: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(width).height(16.dp)) {
            val p = Path().apply { moveTo(0f, size.height); quadraticTo(size.width / 2, -size.height * 0.6f, size.width, size.height) }
            drawPath(p, Brand.Slate300, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        }
        Text("${label.uppercase()} · FRONT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp), color = Brand.Slate400)
    }
}

@Composable
private fun RowTag(letter: String, w: Dp) {
    Text(letter, Modifier.width(w), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Slate400, textAlign = TextAlign.Center)
}

@Composable
private fun Door(door: String, front: Boolean) {
    val here = if (front) door.startsWith("FRONT") else door.startsWith("BACK") || door.isBlank()
    if (!here) return
    val left = door.endsWith("LEFT")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (left) Arrangement.Start else Arrangement.End) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Brand.Saffron50).border(1.dp, Brand.Saffron200, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DoorFront, null, tint = Brand.Saffron600, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
            Text("Entrance", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Saffron700)
        }
    }
}

@Composable
private fun WindowWall() {
    Box(Modifier.width(10.dp).fillMaxHeight().padding(vertical = 20.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFBAE6FD)), Alignment.Center) {
        Icon(Icons.Rounded.WbSunny, "Window", tint = Color(0xFF0284C7), modifier = Modifier.size(8.dp))
    }
}

@Composable
private fun Legend() {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Look.entries.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp, 12.dp)) {
                    if (l == Look.Free) {
                        drawRoundRect(l.color, Offset(size.width * 0.1f, 0f), Size(size.width * 0.8f, size.height * 0.7f), CornerRadius(4f), style = Stroke(1.5.dp.toPx()))
                    } else drawRoundRect(l.color, Offset(size.width * 0.1f, 0f), Size(size.width * 0.8f, size.height * 0.7f), CornerRadius(4f))
                    drawRoundRect(if (l == Look.Free) l.color else lerp(l.color, Color.Black, 0.22f), Offset(0f, size.height * 0.65f), Size(size.width, size.height * 0.3f), CornerRadius(3f))
                }
                Spacer(Modifier.width(4.dp)); Text(l.label, style = MaterialTheme.typography.labelSmall, color = Brand.Slate600)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).border(2.dp, XlEdge, RoundedCornerShape(3.dp))); Spacer(Modifier.width(4.dp))
            Text("XL legroom", style = MaterialTheme.typography.labelSmall, color = Brand.Slate600)
        }
    }
}

/** Edit room: the layout (rows × seats with a centre aisle), front, entrance and window. */
@Composable
private fun Designer(rows: List<List<Int>>, plan: FloorPlan, others: List<LibraryRoom>, bySeat: Map<Int, LibraryMember>, onApply: (List<List<Int>>) -> Unit,
                     onFront: (String) -> Unit, onDoor: (String) -> Unit, onWindow: (String) -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val first = rows.firstOrNull().orEmpty()
    val gapAt = first.indexOf(0)
    var nRows by remember(rows) { mutableIntStateOf(rows.size.coerceAtLeast(1)) }
    var left by remember(rows) { mutableIntStateOf(if (gapAt >= 0) gapAt else first.size.coerceAtLeast(4)) }
    var right by remember(rows) { mutableIntStateOf(if (gapAt >= 0) first.size - gapAt - 1 else 0) }
    val current = rows.flatten().filter { it > 0 }
    val capacity = nRows * (left + right)
    val dropped = current.drop(capacity).filter { it in bySeat }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Amber50).border(1.dp, Brand.Saffron500.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ROOM & SEAT DESIGNER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Saffron600)
        Text("Tap a seat to make it XL, renumber or remove it. Tap an empty place to add a seat.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
        Text("Layout", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
        Counter("Rows", nRows, 1..26) { nRows = it }
        Counter("Seats left of the aisle", left, 0..12) { left = it }
        Counter("Seats right of the aisle", right, 0..12) { right = it }
        if (dropped.isNotEmpty()) Text("Seat ${dropped.joinToString(", ")} is taken and would leave the plan.", style = MaterialTheme.typography.bodySmall, color = Brand.Red600)
        PrimaryButton("Apply layout ($capacity seats)", {
            onApply(theatreRows(nRows, left, right, current, nextSeatNumber(others, current)))
        }, Modifier.fillMaxWidth(), enabled = capacity > 0, icon = Icons.Rounded.Check)
        Text("Front of the room", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
        Choice(listOf("WHITEBOARD" to "Whiteboard", "WINDOW" to "Window", "DESK" to "Teacher's desk", "STAGE" to "Stage"), plan.front.ifBlank { "WHITEBOARD" }, onFront)
        Text("Entrance", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
        Choice(listOf("FRONT_LEFT" to "Front left", "FRONT_RIGHT" to "Front right", "BACK_LEFT" to "Back left", "BACK_RIGHT" to "Back right"), plan.door.ifBlank { "BACK_RIGHT" }, onDoor)
        Text("Window wall", style = MaterialTheme.typography.labelLarge, color = Brand.Ink)
        Choice(listOf("" to "None", "LEFT" to "Left", "RIGHT" to "Right"), plan.window, onWindow)
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
        Step("−") { if (value > range.first) onChange(value - 1) }
        Text("$value", Modifier.width(36.dp), style = MaterialTheme.typography.titleSmall, color = Brand.Ink, textAlign = TextAlign.Center)
        Step("+") { if (value < range.last) onChange(value + 1) }
    }
}

@Composable
private fun Step(text: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White).border(1.dp, Brand.Slate200, CircleShape).clickable(onClick = onClick), Alignment.Center) {
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
