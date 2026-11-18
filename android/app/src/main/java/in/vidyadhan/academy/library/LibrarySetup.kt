package `in`.vidyadhan.academy.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.data.MemberPlan
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch

// ───────────────────────── rooms ─────────────────────────

/** Full-screen editor for the library's rooms and how the seats in each are laid out. */
@Composable
fun RoomsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).library() }
    val scope = rememberCoroutineScope()
    val rooms by dao.rooms().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<LibraryRoom?>(null) }
    Dialog(onDismissRequest = { if (editing != null) editing = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Brand.Bg) {
            val e = editing
            if (e != null) RoomEditor(e, rooms, onBack = { editing = null },
                onSave = { r -> scope.launch { if (r.id == 0L) dao.insertRoom(r) else dao.updateRoom(r) }; editing = null },
                onDelete = { r -> scope.launch { dao.deleteRoom(r) }; editing = null })
            else Column(Modifier.fillMaxSize()) {
                DialogTop("Rooms & seat layout", onClose)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (rooms.isEmpty()) "Add your rooms (for example Lab 1, Lab 2) and place the seats the way they really are. " +
                            "Until then the seat map shows seats 1 – ${AppSettings.seatCount} in a simple grid."
                        else "Tap a room to change its seats. The seat map and the seat count follow these rooms.",
                        style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500,
                    )
                    val dup = duplicateSeats(rooms)
                    if (dup.isNotEmpty()) Warning("Seat ${dup.sorted().joinToString(", ")} is in more than one room. Remove it from one of them.")
                    rooms.forEach { r ->
                        val seats = seatsIn(r.layout)
                        Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 6.dp).clip(RoundedCornerShape(22.dp)).clickable { editing = r }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.MeetingRoom, Brand.Teal700, 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                                Text(if (seats.isEmpty()) "No seats yet" else "${seats.size} seats · ${formatSeats(seats.toSet())}",
                                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 2)
                            }
                            Icon(Icons.Rounded.Edit, "Edit", tint = Brand.Blue600, modifier = Modifier.size(20.dp))
                        }
                    }
                    PrimaryButton("Add room", {
                        val next = (rooms.flatMap { seatsIn(it.layout) }.maxOrNull() ?: 0) + 1
                        editing = LibraryRoom(name = "Lab ${rooms.size + 1}", sortOrder = (rooms.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                            layout = quickLayout(next, next + 7, 8))
                    }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                }
            }
        }
    }
}

@Composable
private fun DialogTop(title: String, onBack: () -> Unit, back: Boolean = false) {
    Row(Modifier.fillMaxWidth().heroBackground().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(if (back) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close, if (back) "Back" else "Close", tint = Color.White) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

@Composable
private fun Warning(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Red50).border(1.dp, Brand.Red100, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Brand.Red600); Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Brand.Red700)
    }
}

private data class Pick(val row: Int, val col: Int)

@Composable
private fun RoomEditor(room: LibraryRoom, rooms: List<LibraryRoom>, onBack: () -> Unit, onSave: (LibraryRoom) -> Unit, onDelete: (LibraryRoom) -> Unit) {
    var name by remember { mutableStateOf(room.name) }
    var rows by remember { mutableStateOf(parseLayout(room.layout)) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var perRow by remember { mutableStateOf("8") }
    var confirmDelete by remember { mutableStateOf(false) }
    val others = rooms.filter { it.id != room.id }
    val otherSeats = others.flatMap { seatsIn(it.layout) }.toSet()
    val mine = rows.flatten().filterIsInstance<Cell.Seat>().map { it.n }
    val clash = (mine.filter { it in otherSeats } + mine.groupingBy { it }.eachCount().filterValues { it > 1 }.keys).toSortedSet()
    fun nextNumber() = ((otherSeats + mine).maxOrNull() ?: 0) + 1
    fun edit(r: Int, f: (MutableList<Cell>) -> Unit) { rows = rows.mapIndexed { i, row -> if (i == r) row.toMutableList().also(f) else row } }

    Column(Modifier.fillMaxSize()) {
        DialogTop(if (room.id == 0L) "New room" else "Edit room", onBack, back = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field(name, { name = it }, "Room name, e.g. Lab 1", Icons.Rounded.MeetingRoom)
            FormCard("Quick fill", Icons.Rounded.AutoAwesome, Brand.Saffron600) {
                Text("Fills the room with seats in order. You can change single seats afterwards.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DigitsField("From seat", from, 3, { from = it }, modifier = Modifier.weight(1f))
                    DigitsField("To seat", to, 3, { to = it }, modifier = Modifier.weight(1f))
                    DigitsField("Per row", perRow, 2, { perRow = it }, modifier = Modifier.weight(1f))
                }
                val a = from.toIntOrNull(); val b = to.toIntOrNull(); val p = perRow.toIntOrNull()
                SoftButton("Fill seats", { rows = parseLayout(quickLayout(a!!, b!!, p!!)); pick = null }, Modifier.fillMaxWidth(),
                    enabled = a != null && b != null && p != null && a > 0 && b > 0 && p > 0 && kotlin.math.abs(b - a) < 300, icon = Icons.Rounded.GridOn)
            }
            FormCard("Layout", Icons.Rounded.EventSeat, Brand.Teal700) {
                Text("Tap a seat, space or door to change it. Use spaces for aisles.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                if (clash.isNotEmpty()) Warning("Seat ${clash.joinToString(", ")} is used twice" + if (clash.any { it in otherSeats }) " (also in another room)" else "")
                rows.forEachIndexed { r, row ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Row ${r + 1}", style = MaterialTheme.typography.labelMedium, color = Brand.Slate500)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { c, cell ->
                                val sel = pick == Pick(r, c)
                                val bad = cell is Cell.Seat && cell.n in clash
                                Box(Modifier.size(width = 46.dp, height = 40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(when { sel -> Brand.Blue700; cell is Cell.Seat -> Color.White; cell is Cell.Door -> Brand.Saffron50; else -> Color.Transparent })
                                    .border(if (sel || bad) 2.dp else 1.dp, when { bad -> Brand.Red500; sel -> Brand.Blue700; cell is Cell.Gap -> Brand.Slate200; else -> Brand.Line }, RoundedCornerShape(10.dp))
                                    .clickable { pick = if (sel) null else Pick(r, c) }, Alignment.Center) {
                                    when (cell) {
                                        is Cell.Seat -> Text("${cell.n}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = if (sel) Color.White else Brand.Ink)
                                        Cell.Door -> Icon(Icons.Rounded.DoorFront, "Door", tint = if (sel) Color.White else Brand.Saffron600, modifier = Modifier.size(20.dp))
                                        Cell.Gap -> Text("·", color = if (sel) Color.White else Brand.Slate400)
                                    }
                                }
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MiniButton("+ Seat") { edit(r) { it.add(Cell.Seat(nextNumber())) } }
                            MiniButton("+ Space") { edit(r) { it.add(Cell.Gap) } }
                            MiniButton("+ Door") { edit(r) { it.add(Cell.Door) } }
                            MiniButton("Delete row", Brand.Red600) { rows = rows.filterIndexed { i, _ -> i != r }; pick = null }
                        }
                    }
                }
                SoftButton("Add row", { rows = rows + listOf(listOf(Cell.Seat(nextNumber()))) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            }
            Spacer(Modifier.height(8.dp))
        }
        val p = pick
        val cell = p?.let { rows.getOrNull(it.row)?.getOrNull(it.col) }
        if (p != null && cell != null) {
            var number by remember(p) { mutableStateOf((cell as? Cell.Seat)?.n?.toString() ?: "") }
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cell is Cell.Seat) DigitsField("Seat number", number, 3, { v -> number = v; v.toIntOrNull()?.takeIf { it > 0 }?.let { n -> edit(p.row) { it[p.col] = Cell.Seat(n) } } },
                        modifier = Modifier.weight(1f))
                    else Text(if (cell is Cell.Door) "Door" else "Empty space", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                    SmallIconButton(Icons.Rounded.ChevronLeft, "Move left", Brand.Blue700) {
                        if (p.col > 0) { edit(p.row) { java.util.Collections.swap(it, p.col, p.col - 1) }; pick = p.copy(col = p.col - 1) }
                    }
                    SmallIconButton(Icons.Rounded.ChevronRight, "Move right", Brand.Blue700) {
                        if (p.col < rows[p.row].size - 1) { edit(p.row) { java.util.Collections.swap(it, p.col, p.col + 1) }; pick = p.copy(col = p.col + 1) }
                    }
                    SmallIconButton(Icons.Rounded.DeleteOutline, "Remove", Brand.Red600) { edit(p.row) { it.removeAt(p.col) }; pick = null }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cell !is Cell.Seat) MiniButton("Make seat") { edit(p.row) { it[p.col] = Cell.Seat(nextNumber()) }; pick = null }
                    if (cell !is Cell.Gap) MiniButton("Make space") { edit(p.row) { it[p.col] = Cell.Gap } }
                    if (cell !is Cell.Door) MiniButton("Make door") { edit(p.row) { it[p.col] = Cell.Door } }
                }
            }
        }
        BottomBar {
            if (room.id != 0L) SoftButton("Delete", { confirmDelete = true }, Modifier.weight(1f), color = Brand.Red600)
            PrimaryButton("Save room", { onSave(room.copy(name = name.trim(), layout = formatLayout(rows))) }, Modifier.weight(1.6f),
                enabled = name.isNotBlank() && clash.isEmpty(), icon = Icons.Rounded.Check)
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${room.name}?", "Members keep their seat numbers; their seats show under “Not in a room” until you place them again.", "Delete", danger = true,
        onConfirm = { confirmDelete = false; onDelete(room) }, onDismiss = { confirmDelete = false })
}

@Composable
private fun MiniButton(text: String, color: Color = Brand.Blue700, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.09f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
}

// ───────────────────────── plans ─────────────────────────

fun monthsLabel(n: Int) = if (n == 1) "1 month" else "$n months"

/** Settings card listing the renewal plans, with an on/off switch each. */
@Composable
fun PlansCard() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).library() }
    val scope = rememberCoroutineScope()
    val plans by dao.plans().collectAsStateWithLifecycle(emptyList())
    var edit by remember { mutableStateOf<MemberPlan?>(null) }
    FormCard("Plans", Icons.Rounded.Loyalty, Brand.Saffron600) {
        Text("Packages members can pay for, e.g. 3 or 6 months at a lower price. The member's normal monthly fee is always offered too. Switch a plan off to hide it.",
            style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        plans.forEach { p ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(18.dp))
                .clickable { edit = p }.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, color = if (p.active) Brand.Ink else Brand.Slate400)
                    Text("${rupees(p.price)} · ${monthsLabel(p.months)}" + if (p.withLocker) " · with locker" else "", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                }
                Switch(p.active, { on -> scope.launch { dao.updatePlan(p.copy(active = on)) } })
            }
        }
        SoftButton("Add plan", { edit = MemberPlan(name = "", months = 3, price = 0) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
    }
    edit?.let { p ->
        var name by remember { mutableStateOf(p.name) }
        var months by remember { mutableIntStateOf(p.months) }
        var price by remember { mutableStateOf(if (p.price == 0L) "" else p.price.toString()) }
        var locker by remember { mutableStateOf(p.withLocker) }
        AlertDialog(onDismissRequest = { edit = null }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            title = { Text(if (p.id == 0L) "New plan" else "Edit plan") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "Name, e.g. 6 months without locker")
                Text("Length", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                ChoicePills(listOf(1, 2, 3, 6, 12).map(::monthsLabel), monthsLabel(months), { s -> months = s.substringBefore(' ').toInt() })
                DigitsField("Price for the whole period", price, 6, { price = it }, prefix = "₹ ",
                    supporting = price.toLongOrNull()?.takeIf { months > 1 && it > 0 }?.let { "${rupees(it / months)} a month" })
                SwitchRow("Includes a locker", null, locker) { locker = it }
            } },
            confirmButton = { TextButton(enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0, onClick = {
                val np = p.copy(name = name.trim(), months = months, price = price.toLong(), withLocker = locker)
                scope.launch { if (p.id == 0L) dao.insertPlan(np) else dao.updatePlan(np) }; edit = null
            }) { Text("Save", fontWeight = FontWeight.Bold) } },
            dismissButton = { Row {
                if (p.id != 0L) TextButton({ scope.launch { dao.deletePlan(p) }; edit = null }) { Text("Delete", color = Brand.Red600) }
                TextButton({ edit = null }) { Text("Cancel") }
            } })
    }
}
