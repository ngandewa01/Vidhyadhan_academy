package `in`.vidyadhan.academy.library

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.*
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).library()
    val members = dao.members().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val types = dao.seatTypes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rooms = dao.rooms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val plans = dao.plans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun member(id: Long) = dao.member(id)
    fun payments(id: Long) = dao.payments(id)
    fun seatHistory(seat: Int) = dao.seatHistory(seat)
    fun add(m: LibraryMember, pays: List<LibraryPayment>, onSaved: (Long) -> Unit = {}) = viewModelScope.launch { onSaved(dao.addMember(m, pays)) }
    fun update(m: LibraryMember, onSaved: (Long) -> Unit = {}) = viewModelScope.launch { dao.updateMember(m); onSaved(m.id) }

    /** Changes the type of one seat; the member sitting there (if any) moves to the new type name too. */
    fun setSeatType(n: Int, chosen: SeatType) = viewModelScope.launch {
        reassignSeat(n, chosen, dao.seatTypesNow()).forEach { dao.updateType(it) }
        dao.allMembers().filter { it.active && it.seatNo == n && it.seatType != chosen.name }.forEach { dao.updateMember(it.copy(seatType = chosen.name)) }
    }
    fun delete(m: LibraryMember) = viewModelScope.launch { dao.deleteMember(m) }
    fun pay(m: LibraryMember, kind: String, amount: Long, mode: String, onSaved: (LibraryPayment) -> Unit = {}) = viewModelScope.launch {
        val p = LibraryPayment(memberId = m.id, kind = kind, amount = amount, paidOn = today(), mode = mode)
        onSaved(p.copy(id = dao.pay(m, p)))
    }
}

private sealed interface Screen {
    data object Main : Screen
    data class Form(val seat: Int, val id: Long = 0) : Screen
    data class Detail(val id: Long) : Screen
}

fun lockerLabel(m: LibraryMember) = when { !m.hasLocker -> ""; m.lockerNo == "-" -> "Locker"; else -> "Locker ${m.lockerNo}" }

fun kindLabel(k: String) = when (k) {
    "FEE" -> "Monthly fee"; "DEPOSIT" -> "Deposit received"; "REGISTRATION" -> "Registration fee"
    "DEPOSIT_RETURN" -> "Deposit returned"; else -> k
}

/** WhatsApp reminder for a member: the fee when it is due, otherwise the balance. */
fun libraryReminder(m: LibraryMember, t: Long): String {
    val amount = when { m.validTill < t -> m.monthlyFee + m.balance; m.balance > 0 -> m.balance; else -> m.monthlyFee }
    return `in`.vidyadhan.academy.Templates.fill("library", true, mapOf("name" to m.name, "amount" to rupees(amount), "date" to m.validTill.asDate()))
}

@Composable
fun LibraryTab(openNew: Boolean = false, openId: Long = 0, onOpened: () -> Unit = {}, vm: LibraryViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    val members by vm.members.collectAsStateWithLifecycle()
    LaunchedEffect(openNew, openId) {
        if (openNew) {
            val taken = members.filter { it.active }.map { it.seatNo }.toSet()
            val rooms = AppDb.get(vm.getApplication<Application>()).library().roomsNow()
            screen = Screen.Form(allSeats(rooms, AppSettings.seatCount).firstOrNull { it !in taken } ?: 0); onOpened()
        }
        else if (openId > 0) { screen = Screen.Detail(openId); onOpened() }
    }
    BackHandler(enabled = screen != Screen.Main) { screen = (screen as? Screen.Form)?.takeIf { it.id != 0L }?.let { Screen.Detail(it.id) } ?: Screen.Main }
    AnimatedContent(screen, transitionSpec = {
        if (targetState == Screen.Main) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "lib") { s ->
        when (s) {
            Screen.Main -> LibraryMain(vm, { screen = Screen.Form(it) }, { screen = Screen.Detail(it) })
            is Screen.Form -> MemberForm(vm, s.seat, s.id, onCancel = { screen = if (s.id == 0L) Screen.Main else Screen.Detail(s.id) }, onSaved = { screen = Screen.Detail(it) })
            is Screen.Detail -> MemberDetail(vm, s.id, onBack = { screen = Screen.Main }, onOpen = { screen = Screen.Detail(it) }, onEdit = { screen = Screen.Form(0, s.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryMain(vm: LibraryViewModel, onAdd: (Int) -> Unit, onOpen: (Long) -> Unit) {
    val members by vm.members.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf("seats") }
    var seatSheet by remember { mutableStateOf<Int?>(null) }
    var typeMode by rememberSaveable { mutableStateOf(false) }
    var filter by remember { mutableStateOf<SeatStatus?>(null) }
    var roomPick by rememberSaveable { mutableLongStateOf(0L) } // 0 = all rooms
    var showRooms by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val active = members.filter { it.active }
    val past = members.filter { !it.active }
    val bySeat = active.associateBy { it.seatNo }
    val seats = allSeats(rooms, AppSettings.seatCount)
    val total = seats.size
    val t = today()
    val statusOf: (Int) -> SeatStatus = { n -> bySeat[n]?.let { memberStatus(it, t) } ?: SeatStatus.Empty }
    val free = seats.count { it !in bySeat }
    val due = active.count { it.validTill < t }
    val held = members.filter { it.depositPaid && it.depositReturnedOn == null }.sumOf { it.deposit }
    HeroPage("Library", "${active.size} members · $free seats free", header = {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val filled = total - free
            ProgressRing(if (total == 0) 0f else filled.toFloat() / total, 96.dp, 10.dp, Brand.Saffron400, Color.White.copy(alpha = 0.16f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$filled/$total", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                    Text("filled", style = MaterialTheme.typography.labelSmall, color = Brand.Blue200)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassStat("Free seats", "$free", Modifier.weight(1f))
                    GlassStat("Fee due", "$due", Modifier.weight(1f), valueColor = if (due > 0) Brand.Saffron200 else Color.White)
                }
                GlassStat("Deposits held", rupees(held), Modifier.fillMaxWidth())
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Column {
                Segmented(listOf("seats" to "Seat map", "members" to "Members", "past" to "Past (${past.size})"), view, { view = it },
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp))
                when (view) {
                    "seats" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
                        if (typeMode) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Saffron50)
                            .border(1.dp, Brand.Saffron200, RoundedCornerShape(18.dp)).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TouchApp, null, tint = Brand.Saffron700); Spacer(Modifier.width(10.dp))
                            Text("Tap a seat to switch it between ${types.joinToString(" and ") { it.name }}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Brand.Saffron800)
                            PillButton("Done", { typeMode = false }, Brand.Saffron600, Icons.Rounded.Check)
                        }
                        StatusFilters(seats.groupingBy(statusOf).eachCount(), seats.size, filter, { filter = it }, withEmpty = true)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            types.forEach { ty ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SeatIcon(ty.icon, Brand.Slate600, 16.dp, badgeBg = Brand.Bg); Spacer(Modifier.width(4.dp))
                                    Text(ty.name, style = MaterialTheme.typography.labelMedium, color = Brand.Slate600, maxLines = 1)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SeatIcon("CHAIR", Brand.Slate600, 16.dp, badgeBg = Brand.Bg, locker = true); Spacer(Modifier.width(4.dp))
                                Text("Has locker", style = MaterialTheme.typography.labelMedium, color = Brand.Slate600, maxLines = 1)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!typeMode) MiniLink("Set seat types", Icons.Rounded.Category) { typeMode = true }
                            MiniLink(if (rooms.isEmpty()) "Set up rooms" else "Edit rooms", Icons.Rounded.MeetingRoom) { showRooms = true }
                        }
                        if (rooms.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoomChip("All rooms", total, roomPick == 0L) { roomPick = 0L }
                            rooms.forEach { r -> RoomChip(r.name, seatsIn(r.layout).size, roomPick == r.id) { roomPick = r.id } }
                        }
                        val onSeat: (Int, LibraryMember?) -> Unit = { n, m ->
                            if (typeMode) { val cur = typeForSeat(n, types); if (types.isNotEmpty()) vm.setSeatType(n, types[(types.indexOfFirst { it.id == cur?.id } + 1) % types.size]) }
                            else if (m == null) seatSheet = n else onOpen(m.id)
                        }
                        val tile: @Composable (Int) -> Unit = { n ->
                            val cur = typeForSeat(n, types)
                            SeatTile(n, bySeat[n], cur?.icon ?: "CHAIR", t, highlight = typeMode && cur?.icon == "LEGROOM",
                                dim = filter != null && statusOf(n) != filter) { m -> onSeat(n, m) }
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (rooms.isEmpty()) RoomBox("All seats", seats.size, free) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { seats.forEach { tile(it) } }
                            }
                            rooms.filter { roomPick == 0L || it.id == roomPick }.forEach { r ->
                                val rs = seatsIn(r.layout)
                                RoomBox(r.name, rs.size, rs.count { it !in bySeat }) {
                                    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        parseLayout(r.layout).forEach { row ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                row.forEach { c ->
                                                    when (c) {
                                                        is Cell.Seat -> tile(c.n)
                                                        Cell.Gap -> Spacer(Modifier.size(TileW, TileH))
                                                        Cell.Door -> DoorTile()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            val placed = seats.toSet()
                            val stray = active.filter { rooms.isNotEmpty() && it.seatNo !in placed }.sortedBy { it.seatNo }
                            if (stray.isNotEmpty() && roomPick == 0L) RoomBox("Not in a room", stray.size, 0) {
                                Text("These members sit on seats that are not in any room. Add the seats to a room or edit the member.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { stray.forEach { tile(it.seatNo) } }
                            }
                        }
                    }
                    "members" -> {
                        val counts = active.groupingBy { memberStatus(it, t) }.eachCount()
                        val q = query.trim().lowercase()
                        val f = filter.takeIf { it != SeatStatus.Empty }
                        val shown = active.filter { m -> (f == null || memberStatus(m, t) == f) &&
                            (q.isEmpty() || m.name.lowercase().contains(q) || m.mobile.contains(q) || m.seatNo.toString() == q || m.lockerNo.lowercase() == q) }
                        Column {
                            StatusFilters(counts, active.size, f, { filter = it }, withEmpty = false)
                            SearchBox(query, { query = it }, "Search name, mobile, seat", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                            if (active.isEmpty()) EmptyState(Icons.Rounded.LocalLibrary, "No members yet", "Tap a free seat on the seat map to add someone.")
                            else if (shown.isEmpty()) EmptyState(Icons.Rounded.SearchOff, "Nobody here", "No member matches this search or filter.")
                            else LazyColumn(contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(shown.sortedBy { it.seatNo }, key = { it.id }) { m -> MemberCard(m, types, rooms, t) { onOpen(m.id) } }
                            }
                        }
                    }
                    else -> if (past.isEmpty()) EmptyState(Icons.Rounded.History, "No past members", "When someone leaves, they appear here with their dates.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(past.sortedByDescending { it.leftOn ?: it.validTill }, key = { it.id }) { m -> MemberCard(m, types, rooms, t) { onOpen(m.id) } }
                    }
                }
            }
            Fab("Add member", Icons.Rounded.PersonAdd, { onAdd(seats.firstOrNull { it !in bySeat } ?: 0) }, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
    if (showRooms) RoomsDialog { showRooms = false }
    seatSheet?.let { n ->
        val ty = typeForSeat(n, types)
        val room = roomOf(n, rooms)
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
                        Text(listOfNotNull(room?.name, ty?.name, ty?.let { "${rupees(it.fee)}/month" }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    }
                }
                Text("Seat type", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                ChoicePills(types.map { it.name }, ty?.name ?: "", { name -> types.firstOrNull { it.name == name }?.let { vm.setSeatType(n, it) } },
                    captions = types.associate { it.name to "${rupees(it.fee)}/month" })
                PrimaryButton("Add a member to seat $n", { seatSheet = null; onAdd(n) }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.PersonAdd)
                SeatHistory(history, onOpen = { seatSheet = null; onOpen(it) })
            }
        }
    }
}

private val TileW = 60.dp
private val TileH = 66.dp

/** Background and text/icon colour of a seat in each status. */
private fun statusColors(s: SeatStatus): Pair<Brush, Color> = when (s) {
    SeatStatus.Empty -> Brush.linearGradient(listOf(Color.White, Color.White)) to Brand.Green600
    SeatStatus.Active -> PrimaryBrush to Color.White
    SeatStatus.Ending -> Brush.linearGradient(listOf(Brand.Saffron400, Brand.Saffron600)) to Color.White
    SeatStatus.Partly -> Brush.linearGradient(listOf(Brand.Violet500, Brand.Violet700)) to Color.White
    SeatStatus.Expired -> Brush.linearGradient(listOf(Brand.Red50, Brand.Red100)) to Brand.Red600
}

/** Dot colour of a status (filters and lists). */
fun statusDot(s: SeatStatus): Color = when (s) {
    SeatStatus.Empty -> Brand.Green500; SeatStatus.Active -> Brand.Blue600; SeatStatus.Ending -> Brand.Saffron500
    SeatStatus.Partly -> Brand.Violet500; SeatStatus.Expired -> Brand.Red500
}

/** Tap-to-filter chips with counts. Tapping the selected one again shows everything. */
@Composable
private fun StatusFilters(counts: Map<SeatStatus, Int>, all: Int, selected: SeatStatus?, onSelect: (SeatStatus?) -> Unit, withEmpty: Boolean) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("All", all, null, selected == null) { onSelect(null) }
        SeatStatus.entries.filter { withEmpty || it != SeatStatus.Empty }.forEach { s ->
            FilterPill(s.label, counts[s] ?: 0, statusDot(s), selected == s) { onSelect(if (selected == s) null else s) }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (selected) Brand.Blue700 else Color.White)
        .border(1.dp, if (selected) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Brand.Slate800, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = if (selected) Brand.Blue100 else Brand.Slate400)
    }
}

@Composable
private fun RoomChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Brand.Teal700 else Color.White)
        .border(1.dp, if (selected) Brand.Teal700 else Brand.Line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Brand.Ink, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = if (selected) Brand.Green100 else Brand.Slate400)
    }
}

@Composable
private fun MiniLink(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Brand.Blue50).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Brand.Blue700, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Brand.Blue700)
    }
}

@Composable
private fun RoomBox(name: String, seats: Int, free: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MeetingRoom, null, tint = Brand.Teal700, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
            Text("$seats seats · $free free", style = MaterialTheme.typography.labelMedium, color = Brand.Slate500)
        }
        content()
    }
}

@Composable
private fun DoorTile() {
    Column(Modifier.size(TileW, TileH).clip(RoundedCornerShape(18.dp)).background(Brand.Saffron50).border(1.dp, Brand.Saffron200, RoundedCornerShape(18.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.DoorFront, null, tint = Brand.Saffron700, modifier = Modifier.size(22.dp))
        Text("Door", style = MaterialTheme.typography.labelSmall, color = Brand.Saffron700)
    }
}

@Composable
private fun SeatHistory(history: List<LibraryMember>, onOpen: (Long) -> Unit, excludeId: Long = -1) {
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
                    Text("${shortDate(m.joinedOn)} ’${LocalDate.ofEpochDay(m.joinedOn).year % 100} → " + (m.leftOn?.let { "${shortDate(it)} ’${LocalDate.ofEpochDay(it).year % 100}" } ?: "now"),
                        style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                }
                StatusChip(if (m.active) "Current" else "Left", if (m.active) Tone.Good else Tone.Neutral)
            }
        }
    }
}

/** "Seat 12 · Lab 2 · Extra leg room · Locker 4" */
private fun seatLine(m: LibraryMember, rooms: List<LibraryRoom>) =
    listOf("Seat ${m.seatNo}", roomOf(m.seatNo, rooms)?.name ?: "", m.seatType, lockerLabel(m)).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun MemberCard(m: LibraryMember, types: List<SeatType>, rooms: List<LibraryRoom>, t: Long, onClick: () -> Unit) {
    val st = memberStatus(m, t)
    val edge = when { !m.active -> Color.Transparent; st == SeatStatus.Expired -> Brand.Red500; st == SeatStatus.Ending -> Brand.Saffron400; st == SeatStatus.Partly -> Brand.Violet500; else -> Color.Transparent }
    ListCard(Modifier.border(1.5.dp, edge, RoundedCornerShape(22.dp)), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(m.name, 50.dp, photo = m.photo)
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (m.active) statusDot(st) else Brand.Slate400)
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("${m.seatNo}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeatIcon(types.firstOrNull { it.name == m.seatType }?.icon ?: "CHAIR", Brand.Teal700, 14.dp, locker = m.hasLocker)
                    Spacer(Modifier.width(4.dp))
                    Text(seatLine(m, rooms), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                !m.active -> StatusChip("Left ${m.leftOn?.let { shortDate(it) } ?: ""}".trim(), Tone.Neutral)
                st == SeatStatus.Expired -> StatusChip("Fee due", Tone.Bad)
                st == SeatStatus.Partly -> StatusChip("${rupees(m.balance)} left", Tone.Accent)
                st == SeatStatus.Ending -> StatusChip(if (m.validTill == t) "Ends today" else "${m.validTill - t} days left", Tone.Warn)
                else -> StatusChip("Paid", Tone.Good)
            }
        }
        if (m.active) {
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp), color = Brand.Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Plan: ${m.planName.ifBlank { "Monthly" }}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Brand.Slate500, maxLines = 1)
                Text("Valid till ${m.validTill.asDate()}", style = MaterialTheme.typography.labelMedium,
                    color = when (st) { SeatStatus.Expired -> Brand.Red600; SeatStatus.Ending -> Brand.Saffron700; else -> Brand.Slate600 })
            }
        }
    }
}

@Composable
private fun SeatTile(n: Int, m: LibraryMember?, icon: String, t: Long, highlight: Boolean = false, dim: Boolean = false, onClick: (LibraryMember?) -> Unit) {
    val st = m?.let { memberStatus(it, t) } ?: SeatStatus.Empty
    val (bg, fg) = statusColors(st)
    val solid = st == SeatStatus.Active || st == SeatStatus.Ending || st == SeatStatus.Partly
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.size(TileW, TileH).alpha(if (dim) 0.22f else 1f)
            .let { if (solid) it.shadow(5.dp, shape, spotColor = Brand.Blue800.copy(alpha = 0.35f)) else it }
            .clip(shape).background(bg)
            .border(if (highlight) 3.dp else 1.5.dp, when { highlight -> Brand.Saffron500; st == SeatStatus.Empty -> Brand.Green500.copy(alpha = 0.30f); st == SeatStatus.Expired -> Brand.Red500.copy(alpha = 0.5f); else -> Color.Transparent }, shape)
            .pressable { onClick(m) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val badge = when (st) { SeatStatus.Empty -> Color.White; SeatStatus.Expired -> Brand.Red50; SeatStatus.Ending -> Brand.Saffron500; SeatStatus.Partly -> Brand.Violet700; SeatStatus.Active -> Brand.Blue700 }
        SeatIcon(icon, if (m == null) Brand.Green500.copy(alpha = 0.75f) else fg, 18.dp, badgeBg = badge, locker = m?.hasLocker == true)
        Text("$n", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = if (m == null) Brand.Slate600 else fg)
        if (m != null) Text(m.name.substringBefore(' '), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.9f), maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun SeatBadge(n: Int, size: androidx.compose.ui.unit.Dp, due: Boolean, icon: String, locker: Boolean = false) {
    Column(
        Modifier.size(size).clip(RoundedCornerShape(22.dp)).background(if (due) Brush.linearGradient(listOf(Brand.Red500, Brand.Red700)) else PrimaryBrush),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        SeatIcon(icon, Color.White.copy(alpha = 0.9f), 20.dp, badgeBg = if (due) Brand.Red600 else Brand.Blue700, locker = locker)
        Text("SEAT $n", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
    }
}

@Composable
private fun MemberForm(vm: LibraryViewModel, startSeat: Int, id: Long, onCancel: () -> Unit, onSaved: (Long) -> Unit) {
    val ctx = LocalContext.current
    val types by vm.types.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val editing = id != 0L
    var existing by remember { mutableStateOf<LibraryMember?>(null) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf<Long?>(null) }
    var photo by remember { mutableStateOf("") }
    var aadhaarPhoto by remember { mutableStateOf("") }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var seat by remember { mutableStateOf(if (startSeat > 0) startSeat.toString() else "") }
    var type by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var deposit by remember { mutableStateOf("0") }
    var reg by remember { mutableStateOf("0") }
    var locker by remember { mutableStateOf(false) }
    var lockerNo by remember { mutableStateOf("") }
    var lockerFee by remember { mutableStateOf(AppSettings.lockerFee.toString()) }
    var joined by remember { mutableLongStateOf(today()) }
    var regPaid by remember { mutableStateOf(true) }
    var depPaid by remember { mutableStateOf(true) }
    var feePaid by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf("Cash") }
    var optName by remember { mutableStateOf("1 month") }
    var received by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(!editing) }
    LaunchedEffect(id) {
        if (editing) AppDb.get(ctx).library().memberNow(id)?.let { m ->
            existing = m; name = m.name; mobile = m.mobile; aadhaar = m.aadhaar; dob = m.dob; photo = m.photo; aadhaarPhoto = m.aadhaarPhoto
            seat = m.seatNo.toString(); type = m.seatType; fee = (m.monthlyFee - m.lockerFee).toString(); deposit = m.deposit.toString(); joined = m.joinedOn
            locker = m.hasLocker; lockerNo = if (m.lockerNo == "-") "" else m.lockerNo; lockerFee = m.lockerFee.toString(); loaded = true
        }
    }
    val seatN = seat.toIntOrNull() ?: 0
    fun applyType(ty: SeatType) { type = ty.name; fee = ty.fee.toString(); deposit = ty.deposit.toString(); reg = ty.regFee.toString() }
    // new members: seat number decides seat type and charges; when editing, only a changed seat does
    LaunchedEffect(types, seatN, loaded) {
        if (loaded && types.isNotEmpty() && (!editing || seatN != existing?.seatNo)) (typeForSeat(seatN, types) ?: types.first()).let(::applyType)
    }
    val taken = members.any { it.active && it.seatNo == seatN && it.id != id }
    val lockerTaken = locker && lockerNo.isNotBlank() && members.any { it.active && it.id != id && it.lockerNo == lockerNo }
    val base = fee.toLongOrNull() ?: 0; val lf = if (locker) lockerFee.toLongOrNull() ?: 0 else 0
    val d = (deposit.toLongOrNull() ?: 0) + if (locker && !editing) AppSettings.lockerDeposit.toLong() else 0
    val r = reg.toLongOrNull() ?: 0
    val monthly = base + lf
    val options = renewOptions(monthly, plans, locker)
    val chosen = options.firstOrNull { it.name == optName } ?: options.first()
    LaunchedEffect(chosen.price) { received = chosen.price.toString() }
    val receivedAmt = received.toLongOrNull() ?: 0
    val collectingNow = (if (regPaid) r else 0) + (if (depPaid) d else 0) + (if (feePaid) receivedAmt else 0)
    val seatSet = allSeats(rooms, AppSettings.seatCount).toSet()
    val seatOk = seatN in seatSet || (editing && seatN > 0 && seatN == existing?.seatNo) // an old seat outside the rooms can be kept
    val noSuchSeat = seatN > 0 && !seatOk
    val valid = loaded && name.isNotBlank() && mobile.length == 10 && (aadhaar.isEmpty() || aadhaar.length == 12) && seatOk && !taken && !lockerTaken
    val photoPicker = rememberPhotoPicker("Member photo", photo.isNotBlank(), { photo = it }, { photo = "" })
    val aadhaarPicker = rememberPhotoPicker("Aadhaar card photo", aadhaarPhoto.isNotBlank(), { aadhaarPhoto = it }, { aadhaarPhoto = "" })
    HideBottomNav()
    HeroPage(if (editing) "Edit member" else "New member", if (seatN > 0) "Seat $seatN · $type" + if (locker) " · locker" else "" else "Choose a seat", onBack = onCancel) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            PhotoAvatarEdit(name, photo, photoPicker.open)
            FormCard("Member details", Icons.Rounded.Person, Brand.Teal700) {
                Field(name, { name = it }, "Full name", Icons.Rounded.Person)
                DigitsField("Mobile number", mobile, 10, { mobile = it }, Icons.Rounded.Phone, prefix = "+91 ", phone = true,
                    supporting = if (mobile.isNotEmpty() && mobile.length < 10) "Enter all 10 digits" else null, isError = mobile.isNotEmpty() && mobile.length < 10)
                DigitsField("Aadhaar number", aadhaar, 12, { aadhaar = it }, Icons.Rounded.CreditCard,
                    supporting = if (aadhaar.isNotEmpty() && aadhaar.length < 12) "Enter all 12 digits" else "Kept only on this phone", isError = aadhaar.isNotEmpty() && aadhaar.length < 12)
                DocPhoto("Aadhaar card photo", aadhaarPhoto, aadhaarPicker.open) { viewPhoto = aadhaarPhoto }
                OptionalDateField("Date of birth (optional)", dob, { dob = it })
            }
            FormCard("Seat and locker", Icons.Rounded.EventSeat) {
                DigitsField(if (rooms.isEmpty()) "Seat number (1 – ${AppSettings.seatCount})" else "Seat number", seat, 3, { seat = it }, Icons.Rounded.Tag,
                    supporting = when {
                        taken -> "Seat $seatN is already taken"
                        noSuchSeat -> "There is no seat $seatN" + if (rooms.isNotEmpty()) " in your rooms" else ""
                        else -> listOfNotNull(roomOf(seatN, rooms)?.name, "seat type and fees fill in automatically").joinToString(" · ").replaceFirstChar { it.uppercase() }
                    }, isError = taken || noSuchSeat)
                ChoicePills(types.map { it.name }, type, { n -> types.firstOrNull { it.name == n }?.let(::applyType) },
                    captions = types.associate { it.name to "${rupees(it.fee)}/month" })
                HorizontalDivider(color = Brand.Line)
                SwitchRow("Locker", "+${rupees(AppSettings.lockerFee.toLong())}/month" + if (AppSettings.lockerDeposit > 0) " · ${rupees(AppSettings.lockerDeposit.toLong())} key deposit" else "", locker) { locker = it }
                if (locker) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(lockerNo, { lockerNo = it.take(6) }, "Locker no.", Icons.Rounded.Lock, Modifier.weight(1f),
                        supporting = if (lockerTaken) "Already given to someone" else null, isError = lockerTaken)
                    DigitsField("Locker fee", lockerFee, 5, { lockerFee = it }, prefix = "₹ ", modifier = Modifier.weight(1f))
                }
            }
            FormCard("Charges", Icons.Rounded.CurrencyRupee, Brand.Green600) {
                DigitsField("Seat fee per month", fee, 6, { fee = it }, prefix = "₹ ")
                if (locker) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly total (seat + locker)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate600)
                    Text(rupees(monthly), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DigitsField("Deposit", deposit, 6, { deposit = it }, prefix = "₹ ", modifier = Modifier.weight(1f),
                        supporting = if (locker && !editing && AppSettings.lockerDeposit > 0) "+ ${rupees(AppSettings.lockerDeposit.toLong())} locker" else null)
                    if (!editing) DigitsField("Registration", reg, 6, { reg = it }, prefix = "₹ ", modifier = Modifier.weight(1f))
                }
                DateField("Joining date", joined, { joined = it })
            }
            if (!editing) FormCard("Collected today", Icons.Rounded.Payments, Brand.Saffron600) {
                SwitchRow("Registration fee", rupees(r), regPaid) { regPaid = it }
                SwitchRow("Deposit", rupees(d), depPaid) { depPaid = it }
                SwitchRow("Fee for the first period", "${chosen.name} · ${rupees(chosen.price)}", feePaid) { feePaid = it }
                if (feePaid) {
                    ChoicePills(options.map { it.name }, chosen.name, { optName = it }, captions = options.associate { it.name to "${rupees(it.price)} · ${monthsLabel(it.months)}" })
                    DigitsField("Amount received now", received, 6, { received = it }, prefix = "₹ ",
                        supporting = if (receivedAmt < chosen.price) "${rupees(chosen.price - receivedAmt)} will show as balance (part paid)" else "Full fee",
                        isError = receivedAmt > chosen.price)
                }
                if (collectingNow > 0) { Text("Paid by", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500); ModeChoice(mode) { mode = it } }
                HorizontalDivider(color = Brand.Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total collecting now", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Slate600)
                    Text(rupees(collectingNow), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Green700)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            SoftButton("Cancel", onCancel, Modifier.weight(1f), color = Brand.Slate600)
            PrimaryButton(if (editing) "Save changes" else "Add member", {
                val lockerValue = if (!locker) "" else lockerNo.trim().ifBlank { "-" }
                val old = existing
                if (old != null) vm.update(old.copy(name = name.trim(), mobile = mobile, aadhaar = aadhaar, seatNo = seatN, seatType = type, monthlyFee = monthly,
                    deposit = d, joinedOn = joined, dob = dob, photo = photo, aadhaarPhoto = aadhaarPhoto, lockerNo = lockerValue, lockerFee = lf), onSaved)
                else {
                    val pays = buildList {
                        if (regPaid && r > 0) add(LibraryPayment(memberId = 0, kind = "REGISTRATION", amount = r, paidOn = joined, mode = mode))
                        if (depPaid && d > 0) add(LibraryPayment(memberId = 0, kind = "DEPOSIT", amount = d, paidOn = joined, mode = mode))
                        if (feePaid && receivedAmt > 0) add(LibraryPayment(memberId = 0, kind = "FEE", amount = receivedAmt, paidOn = joined, mode = mode))
                    }
                    val till = if (feePaid) LocalDate.ofEpochDay(joined).plusMonths(chosen.months.toLong()).toEpochDay() else joined
                    vm.add(LibraryMember(name = name.trim(), mobile = mobile, aadhaar = aadhaar, seatNo = seatN, seatType = type, monthlyFee = monthly,
                        deposit = d, depositPaid = depPaid && d > 0, joinedOn = joined, validTill = till, dob = dob, photo = photo, aadhaarPhoto = aadhaarPhoto,
                        lockerNo = lockerValue, lockerFee = lf, balance = if (feePaid) balanceAfter(0, chosen.price, receivedAmt) else 0,
                        planName = if (feePaid) chosen.name else ""), pays, onSaved)
                }
            }, Modifier.weight(1.6f), enabled = valid && !(feePaid && !editing && receivedAmt > chosen.price), icon = Icons.Rounded.Check)
        }
    }
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberDetail(vm: LibraryViewModel, id: Long, onBack: () -> Unit, onOpen: (Long) -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val mm by remember(id) { vm.member(id) }.collectAsStateWithLifecycle(null)
    val pays by remember(id) { vm.payments(id) }.collectAsStateWithLifecycle(emptyList())
    val types by vm.types.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    var vacate by remember { mutableStateOf(false) }
    var collect by remember { mutableStateOf<String?>(null) } // FEE, BALANCE, DEPOSIT or DEPOSIT_RETURN
    var saved by remember { mutableStateOf<LibraryPayment?>(null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    val m = mm ?: return
    val history by remember(m.seatNo) { vm.seatHistory(m.seatNo) }.collectAsStateWithLifecycle(emptyList())
    val t = today()
    val due = m.validTill < t
    val icon = types.firstOrNull { it.name == m.seatType }?.icon ?: "CHAIR"
    HideBottomNav()
    val remindText = libraryReminder(m, t)

    fun receipt(p: LibraryPayment) = shareReceipt(ctx, ReceiptData(
        "${AppSettings.receiptPrefix}-L${p.id.toString().padStart(5, '0')}", p.paidOn,
        if (p.kind == "DEPOSIT_RETURN") "DEPOSIT REFUND  /  अनामत परत" else "LIBRARY RECEIPT  /  लायब्ररी पावती", m.name,
        listOf("Mobile" to "+91 ${m.mobile}", "Seat" to listOfNotNull("${m.seatNo}", roomOf(m.seatNo, rooms)?.name, m.seatType.ifBlank { null }).joinToString(" · "), "For" to kindLabel(p.kind), "Payment mode" to p.mode.ifBlank { "—" }),
        p.amount, if (p.kind == "FEE") listOfNotNull("Valid up to" to m.validTill.asDate(), m.planName.takeIf { it.isNotBlank() }?.let { "Plan" to it },
            m.balance.takeIf { it > 0 }?.let { "Balance left" to rupees(it) }) else emptyList()))

    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        ProfileHero(m.name, onBack,
            badge = {
                Box {
                    Box(Modifier.clickable(enabled = m.photo.isNotBlank()) { viewPhoto = m.photo }) { Avatar(m.name, 88.dp, ring = true, photo = m.photo) }
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 10.dp).clip(RoundedCornerShape(12.dp)).background(if (due && m.active) Brand.Red600 else Brand.Saffron600)
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("Seat ${m.seatNo}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            },
            chips = { roomOf(m.seatNo, rooms)?.let { GlassChip(it.name, Icons.Rounded.MeetingRoom) }; if (m.seatType.isNotBlank()) GlassChip(m.seatType, Icons.Rounded.EventSeat); if (m.hasLocker) GlassChip(lockerLabel(m), Icons.Rounded.Lock); GlassChip("Since ${shortDate(m.joinedOn)}", Icons.Rounded.CalendarMonth) },
            topActions = { GlassIconButton(Icons.Rounded.Edit, "Edit", onEdit); GlassIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirm = true }) },
            actions = {
                RoundAction(Icons.Rounded.Call, "Call", Brand.Blue600, { Contact.call(ctx, m.mobile) }, glass = true)
                RoundAction(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", Brand.WhatsApp, { Contact.whatsapp(ctx, m.mobile) }, glass = true)
                RoundAction(Icons.Rounded.NotificationsActive, "Remind", Brand.Saffron600, { Contact.whatsapp(ctx, m.mobile, remindText) }, glass = true)
            })
        Sheet {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(4.dp))
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SeatBadge(m.seatNo, 64.dp, due && m.active, icon, m.hasLocker)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (!m.active) "Left on" else if (due) "Monthly fee was due" else if (m.validTill == t) "Monthly fee due today" else "Fee paid up to", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                            Text((if (!m.active) m.leftOn ?: m.validTill else m.validTill).asDate(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (due && m.active) Brand.Red600 else Brand.Ink)
                        }
                        when {
                            !m.active -> StatusChip("Left", Tone.Neutral)
                            due -> StatusChip("${t - m.validTill}d late", Tone.Bad)
                            m.validTill == t -> StatusChip("Due today", Tone.Warn)
                            m.validTill - t <= ENDING_DAYS -> StatusChip("${m.validTill - t} days left", Tone.Warn)
                            else -> StatusChip("${m.validTill - t} days left", Tone.Good)
                        }
                    }
                    if (m.active) {
                        val months = plans.firstOrNull { it.name == m.planName }?.months ?: 1
                        val start = LocalDate.ofEpochDay(m.validTill).minusMonths(months.toLong()).toEpochDay()
                        ProgressBar(if (due) 1f else ((t - start).toFloat() / (m.validTill - start).coerceAtLeast(1)).coerceIn(0f, 1f), color = if (due) Brand.Red500 else Brand.Teal500, track = Brand.Line, height = 8.dp)
                        if (m.balance > 0) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Violet500.copy(alpha = 0.08f)).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Part paid", style = MaterialTheme.typography.labelMedium, color = Brand.Violet700)
                                Text("${rupees(m.balance)} still to pay", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                            }
                            PillButton("Collect", { collect = "BALANCE" }, Brand.Violet700, Icons.Rounded.Payments)
                        }
                        PrimaryButton("Renew · from ${rupees(m.monthlyFee)}", { collect = "FEE" }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Autorenew)
                    }
                }
                Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Savings, Brand.Violet700, 46.dp, filled = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Deposit · ${rupees(m.deposit)}", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                        Text(when { m.depositReturnedOn != null -> "Returned on ${m.depositReturnedOn.asDate()}"; m.depositPaid -> "Held by the library"; else -> "Not received yet" },
                            style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    if (m.active && !m.depositPaid && m.deposit > 0) PillButton("Receive", { collect = "DEPOSIT" }, Brand.Violet700)
                    else if (m.depositPaid && m.depositReturnedOn == null) PillButton("Return", { collect = "DEPOSIT_RETURN" }, Brand.Slate600)
                    else if (m.depositReturnedOn != null) StatusChip("Returned", Tone.Good)
                }
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow(Icons.Rounded.Phone, "Mobile", "+91 ${m.mobile}")
                    if (m.dob != null) { HorizontalDivider(color = Brand.Line); InfoRow(Icons.Rounded.Cake, "Birthday", LocalDate.ofEpochDay(m.dob).format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy"))) }
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.CreditCard, "Aadhaar", if (m.aadhaar.isBlank()) "Not added" else maskAadhaar(m.aadhaar))
                    if (m.aadhaarPhoto.isNotBlank()) { DocPhoto("Aadhaar card photo", m.aadhaarPhoto, {}) { viewPhoto = m.aadhaarPhoto }; Spacer(Modifier.height(8.dp)) }
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.Loyalty, "Plan", m.planName.ifBlank { "Monthly" } + if (m.balance > 0) " · ${rupees(m.balance)} balance" else "")
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.CurrencyRupee, "Monthly fee", rupees(m.monthlyFee) + if (m.hasLocker && m.lockerFee > 0) " (seat ${rupees(m.monthlyFee - m.lockerFee)} + locker ${rupees(m.lockerFee)})" else "")
                    if (m.hasLocker) { HorizontalDivider(color = Brand.Line); InfoRow(Icons.Rounded.Lock, "Locker", if (m.lockerNo == "-") "Yes (number not noted)" else "Locker no. ${m.lockerNo}") }
                }
                SectionHeader("Payment history")
                if (pays.isEmpty()) Text("No payments recorded yet.", color = Brand.Slate500)
                else Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                    pays.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = Brand.Line)
                        val out = p.kind == "DEPOSIT_RETURN"
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(when (p.kind) { "FEE" -> Icons.Rounded.CalendarMonth; "DEPOSIT", "DEPOSIT_RETURN" -> Icons.Rounded.Savings; else -> Icons.Rounded.HowToReg },
                                if (out) Brand.Red600 else Brand.Green600, 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(kindLabel(p.kind), style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                                Text(p.paidOn.asDate() + if (p.mode.isNotBlank()) " · ${p.mode}" else "", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                            }
                            Text((if (out) "− " else "+ ") + rupees(p.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (out) Brand.Red600 else Brand.Green700)
                            Spacer(Modifier.width(6.dp))
                            SmallIconButton(Icons.Rounded.ReceiptLong, "Receipt", Brand.Blue600) { receipt(p) }
                        }
                    }
                }
                NotesSection("MEMBER", m.id)
                SoftButton("Admission form (PDF)", {
                    shareAdmissionForm(ctx, "LIBRARY ADMISSION  /  लायब्ररी प्रवेश", "${AppSettings.receiptPrefix}-LA${m.id.toString().padStart(4, '0')}", m.name, m.photo,
                        listOf("Mobile" to "+91 ${m.mobile}", "Aadhaar" to m.aadhaar.chunked(4).joinToString(" ").ifBlank { "—" }, "Date of birth" to (m.dob?.asDate() ?: "—"),
                            "Seat" to "${m.seatNo} · ${m.seatType}", "Locker" to (if (m.hasLocker) lockerLabel(m) else "No"), "Joining date" to m.joinedOn.asDate(),
                            "Monthly fee" to rupees(m.monthlyFee), "Deposit" to rupees(m.deposit)))
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Description)
                SeatHistory(history, onOpen, excludeId = m.id)
                if (m.active) SoftButton("Member left · free seat ${m.seatNo}", { vacate = true }, Modifier.fillMaxWidth(), color = Brand.Red600, icon = Icons.AutoMirrored.Rounded.Logout)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    collect?.let { kind ->
        var mode by remember { mutableStateOf("Cash") }
        val options = renewOptions(m.monthlyFee, plans, m.hasLocker)
        var optName by remember { mutableStateOf(options.firstOrNull { it.name == m.planName }?.name ?: options.first().name) }
        val chosen = options.firstOrNull { it.name == optName } ?: options.first()
        val full = when (kind) { "FEE" -> chosen.price; "BALANCE" -> m.balance; else -> m.deposit }
        var received by remember(kind, full) { mutableStateOf(full.toString()) }
        val typed = kind == "FEE" || kind == "BALANCE"
        val amount = if (typed) received.toLongOrNull() ?: 0 else full
        val till = renewedTill(m.validTill, t, chosen.months)
        ModalBottomSheet({ collect = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetTitle(when (kind) { "FEE" -> "Renew membership"; "BALANCE" -> "Collect balance"; "DEPOSIT" -> "Receive deposit"; else -> "Return deposit" },
                    when (kind) { "FEE" -> "${chosen.name} · valid till ${till.asDate()}"; "BALANCE" -> "${rupees(m.balance)} still to pay"; else -> "${rupees(full)} · ${m.name}" })
                if (kind == "FEE") {
                    Text("Period", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                    ChoicePills(options.map { it.name }, chosen.name, { optName = it }, captions = options.associate { it.name to "${rupees(it.price)} · ${monthsLabel(it.months)}" })
                    if (m.balance > 0) Text("The old balance of ${rupees(m.balance)} stays separate. Collect it with “Collect” on the member page.", style = MaterialTheme.typography.bodySmall, color = Brand.Violet700)
                }
                if (typed) DigitsField("Amount received", received, 6, { received = it }, prefix = "₹ ",
                    supporting = when { amount > full -> "More than ${rupees(full)}"; amount < full -> "${rupees(full - amount)} will stay as balance"; else -> "Full amount" },
                    isError = amount > full)
                Text(if (kind == "DEPOSIT_RETURN") "Returned by" else "Paid by", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                ModeChoice(mode) { mode = it }
                PrimaryButton("Confirm ${rupees(amount)}", {
                    val updated = when (kind) {
                        "FEE" -> m.copy(validTill = till, planName = chosen.name, balance = m.balance + balanceAfter(0, chosen.price, amount))
                        "BALANCE" -> m.copy(balance = (m.balance - amount).coerceAtLeast(0))
                        "DEPOSIT" -> m.copy(depositPaid = true)
                        else -> m.copy(depositReturnedOn = t)
                    }
                    vm.pay(updated, if (kind == "BALANCE") "FEE" else kind, amount, mode) { saved = it }
                    collect = null
                }, Modifier.fillMaxWidth(), enabled = !typed || amount in 1..full, accent = kind == "FEE", icon = Icons.Rounded.Check)
            }
        }
    }
    saved?.let { p ->
        AlertDialog({ saved = null }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
            icon = { IconTile(Icons.Rounded.CheckCircle, Brand.Green600, 52.dp, filled = true) },
            title = { Text("${kindLabel(p.kind)} · ${rupees(p.amount)}") },
            text = { Text("Share a receipt with ${m.name}?", color = Brand.Slate500) },
            confirmButton = { TextButton({ receipt(p); saved = null }) { Text("Share receipt", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ saved = null }) { Text("Done") } })
    }
    if (vacate) ConfirmDialog("Free seat ${m.seatNo}?",
        if (m.depositPaid && m.depositReturnedOn == null) "The deposit of ${rupees(m.deposit)} has not been returned yet. Return it first if needed." else "${m.name} moves to Past members. Their records are kept.",
        "Free seat", onConfirm = { vm.update(m.copy(active = false, leftOn = t)); vacate = false; onBack() }, onDismiss = { vacate = false })
    if (confirm) ConfirmDialog("Delete ${m.name}?", "All their payment records are deleted too. To keep records, use “Member left” instead.", "Delete", danger = true,
        onConfirm = { Photos.delete(ctx, m.photo); Photos.delete(ctx, m.aadhaarPhoto); vm.delete(m); confirm = false; onBack() }, onDismiss = { confirm = false })
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
}
