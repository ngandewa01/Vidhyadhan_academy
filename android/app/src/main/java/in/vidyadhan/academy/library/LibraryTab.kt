package `in`.vidyadhan.academy.library

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    fun member(id: Long) = dao.member(id)
    fun payments(id: Long) = dao.payments(id)
    fun seatHistory(seat: Int) = dao.seatHistory(seat)
    fun add(m: LibraryMember, pays: List<LibraryPayment>, onSaved: (Long) -> Unit = {}) = viewModelScope.launch { onSaved(dao.addMember(m, pays)) }
    fun update(m: LibraryMember, onSaved: (Long) -> Unit = {}) = viewModelScope.launch { dao.updateMember(m); onSaved(m.id) }
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

@Composable
fun LibraryTab(openNew: Boolean = false, openId: Long = 0, onOpened: () -> Unit = {}, vm: LibraryViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    val members by vm.members.collectAsStateWithLifecycle()
    LaunchedEffect(openNew, openId) {
        if (openNew) { val taken = members.filter { it.active }.map { it.seatNo }.toSet(); screen = Screen.Form((1..AppSettings.seatCount).firstOrNull { it !in taken } ?: 0); onOpened() }
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
    var view by rememberSaveable { mutableStateOf("seats") }
    var seatSheet by remember { mutableStateOf<Int?>(null) }
    val active = members.filter { it.active }
    val past = members.filter { !it.active }
    val bySeat = active.associateBy { it.seatNo }
    val total = AppSettings.seatCount
    val t = today()
    val due = active.count { it.validTill < t }
    val held = members.filter { it.depositPaid && it.depositReturnedOn == null }.sumOf { it.deposit }
    HeroPage("Library", "${active.size} members · ${(total - active.size).coerceAtLeast(0)} seats free", header = {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(if (total == 0) 0f else active.size.toFloat() / total, 96.dp, 10.dp, Brand.Saffron400, Color.White.copy(alpha = 0.16f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${active.size}/$total", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                    Text("filled", style = MaterialTheme.typography.labelSmall, color = Brand.Blue200)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassStat("Free seats", "${(total - active.size).coerceAtLeast(0)}", Modifier.weight(1f))
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
                    "seats" -> {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Legend(Color.White, Brand.Green500, "Free"); Legend(Brand.Blue700, Brand.Blue700, "Taken"); Legend(Brand.Red50, Brand.Red500, "Fee due")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                types.forEach { ty ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SeatIcon(ty.icon, Brand.Slate600, 16.dp, badgeBg = Brand.Bg); Spacer(Modifier.width(4.dp))
                                        Text(ty.name, style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SeatIcon("CHAIR", Brand.Slate600, 16.dp, badgeBg = Brand.Bg, locker = true); Spacer(Modifier.width(4.dp))
                                    Text("Has locker", style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
                                }
                            }
                        }
                        LazyVerticalGrid(GridCells.Adaptive(60.dp), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 110.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items((1..total).toList()) { n -> SeatTile(n, bySeat[n], typeForSeat(n, types)?.icon ?: "CHAIR", t) { m -> if (m == null) seatSheet = n else onOpen(m.id) } }
                        }
                    }
                    "members" -> if (active.isEmpty()) EmptyState(Icons.Rounded.LocalLibrary, "No members yet", "Tap a free seat on the seat map to add someone.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(active.sortedBy { it.seatNo }, key = { it.id }) { m -> MemberCard(m, types, t) { onOpen(m.id) } }
                    }
                    else -> if (past.isEmpty()) EmptyState(Icons.Rounded.History, "No past members", "When someone leaves, they appear here with their dates.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(past.sortedByDescending { it.leftOn ?: it.validTill }, key = { it.id }) { m -> MemberCard(m, types, t) { onOpen(m.id) } }
                    }
                }
            }
            Fab("Add member", Icons.Rounded.PersonAdd, { onAdd((1..total).firstOrNull { it !in bySeat } ?: 0) }, Modifier.align(Alignment.BottomEnd).padding(20.dp))
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
                        Text(listOfNotNull(ty?.name, ty?.let { "${rupees(it.fee)}/month" }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                    }
                }
                PrimaryButton("Add a member to seat $n", { seatSheet = null; onAdd(n) }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.PersonAdd)
                SeatHistory(history, onOpen = { seatSheet = null; onOpen(it) })
            }
        }
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

@Composable
private fun MemberCard(m: LibraryMember, types: List<SeatType>, t: Long, onClick: () -> Unit) {
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(m.name, 50.dp, photo = m.photo)
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (m.active && m.validTill < t) Brand.Red600 else Brand.Blue700)
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("${m.seatNo}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeatIcon(types.firstOrNull { it.name == m.seatType }?.icon ?: "CHAIR", Brand.Teal700, 14.dp, locker = m.hasLocker)
                    Spacer(Modifier.width(4.dp))
                    Text(listOf("Seat ${m.seatNo}", m.seatType, lockerLabel(m)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
                }
            }
            when {
                !m.active -> StatusChip("Left ${m.leftOn?.let { shortDate(it) } ?: ""}".trim(), Tone.Neutral)
                m.validTill < t -> StatusChip("Fee due", Tone.Bad)
                m.validTill - t <= 3 -> StatusChip("Ends ${shortDate(m.validTill)}", Tone.Warn)
                else -> StatusChip("Till ${shortDate(m.validTill)}", Tone.Good)
            }
        }
    }
}

@Composable
private fun SeatTile(n: Int, m: LibraryMember?, icon: String, t: Long, onClick: (LibraryMember?) -> Unit) {
    val due = m != null && m.validTill < t
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.aspectRatio(0.92f).let { if (m != null && !due) it.shadow(6.dp, shape, spotColor = Brand.Blue800.copy(alpha = 0.4f)) else it }
            .clip(shape)
            .background(when { m == null -> Brush.linearGradient(listOf(Color.White, Color.White)); due -> Brush.linearGradient(listOf(Brand.Red50, Brand.Red100)); else -> PrimaryBrush })
            .border(1.5.dp, when { m == null -> Brand.Green500.copy(alpha = 0.30f); due -> Brand.Red500.copy(alpha = 0.5f); else -> Color.Transparent }, shape)
            .pressable { onClick(m) }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val fg = when { m == null -> Brand.Green600; due -> Brand.Red600; else -> Color.White }
        SeatIcon(icon, if (m == null) Brand.Green500.copy(alpha = 0.75f) else fg, 18.dp, badgeBg = when { m == null -> Color.White; due -> Brand.Red50; else -> Brand.Blue700 }, locker = m?.hasLocker == true)
        Text("$n", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = if (m == null) Brand.Slate600 else fg)
        if (m != null) Text(m.name.substringBefore(' '), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f), maxLines = 1,
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
private fun Legend(fill: Color, edge: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(fill).border(1.5.dp, edge, RoundedCornerShape(5.dp)))
    Spacer(Modifier.width(6.dp))
    Text(t, style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
}

@Composable
private fun MemberForm(vm: LibraryViewModel, startSeat: Int, id: Long, onCancel: () -> Unit, onSaved: (Long) -> Unit) {
    val ctx = LocalContext.current
    val types by vm.types.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
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
    val collectingNow = (if (regPaid) r else 0) + (if (depPaid) d else 0) + (if (feePaid) monthly else 0)
    val valid = loaded && name.isNotBlank() && mobile.length == 10 && (aadhaar.isEmpty() || aadhaar.length == 12) && seatN in 1..AppSettings.seatCount && !taken && !lockerTaken
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
                DigitsField("Seat number (1 – ${AppSettings.seatCount})", seat, 3, { seat = it }, Icons.Rounded.Tag,
                    supporting = if (taken) "Seat $seatN is already taken" else "Seat type and fees fill in automatically", isError = taken)
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
                SwitchRow("First month's fee", rupees(monthly), feePaid) { feePaid = it }
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
                        if (feePaid && monthly > 0) add(LibraryPayment(memberId = 0, kind = "FEE", amount = monthly, paidOn = joined, mode = mode))
                    }
                    val till = if (feePaid) LocalDate.ofEpochDay(joined).plusMonths(1).toEpochDay() else joined
                    vm.add(LibraryMember(name = name.trim(), mobile = mobile, aadhaar = aadhaar, seatNo = seatN, seatType = type, monthlyFee = monthly,
                        deposit = d, depositPaid = depPaid && d > 0, joinedOn = joined, validTill = till, dob = dob, photo = photo, aadhaarPhoto = aadhaarPhoto,
                        lockerNo = lockerValue, lockerFee = lf), pays, onSaved)
                }
            }, Modifier.weight(1.6f), enabled = valid, icon = Icons.Rounded.Check)
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
    var confirm by remember { mutableStateOf(false) }
    var vacate by remember { mutableStateOf(false) }
    var collect by remember { mutableStateOf<String?>(null) } // FEE or DEPOSIT
    var saved by remember { mutableStateOf<LibraryPayment?>(null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    val m = mm ?: return
    val history by remember(m.seatNo) { vm.seatHistory(m.seatNo) }.collectAsStateWithLifecycle(emptyList())
    val t = today()
    val due = m.validTill < t
    val icon = types.firstOrNull { it.name == m.seatType }?.icon ?: "CHAIR"
    HideBottomNav()
    val remindText = "नमस्कार ${m.name}, विद्याधन अकॅडमी लायब्ररी: तुमची मासिक फी ${rupees(m.monthlyFee)} ${m.validTill.asDate()} रोजी देय आहे. धन्यवाद."

    fun receipt(p: LibraryPayment) = shareReceipt(ctx, ReceiptData(
        "${AppSettings.receiptPrefix}-L${p.id.toString().padStart(5, '0')}", p.paidOn,
        if (p.kind == "DEPOSIT_RETURN") "DEPOSIT REFUND  /  अनामत परत" else "LIBRARY RECEIPT  /  लायब्ररी पावती", m.name,
        listOf("Mobile" to "+91 ${m.mobile}", "Seat" to "${m.seatNo} · ${m.seatType}", "For" to kindLabel(p.kind), "Payment mode" to p.mode.ifBlank { "—" }),
        p.amount, if (p.kind == "FEE") listOf("Valid up to" to m.validTill.asDate()) else emptyList()))

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
            chips = { if (m.seatType.isNotBlank()) GlassChip(m.seatType, Icons.Rounded.EventSeat); if (m.hasLocker) GlassChip(lockerLabel(m), Icons.Rounded.Lock); GlassChip("Since ${shortDate(m.joinedOn)}", Icons.Rounded.CalendarMonth) },
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
                            m.validTill - t <= 3 -> StatusChip("${m.validTill - t} days left", Tone.Warn)
                            else -> StatusChip("${m.validTill - t} days left", Tone.Good)
                        }
                    }
                    if (m.active) {
                        val start = LocalDate.ofEpochDay(m.validTill).minusMonths(1).toEpochDay()
                        ProgressBar(if (due) 1f else (t - start).toFloat() / (m.validTill - start).coerceAtLeast(1), color = if (due) Brand.Red500 else Brand.Teal500, track = Brand.Line, height = 8.dp)
                        PrimaryButton("Renew 1 month · ${rupees(m.monthlyFee)}", { collect = "FEE" }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Autorenew)
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
                SeatHistory(history, onOpen, excludeId = m.id)
                if (m.active) SoftButton("Member left · free seat ${m.seatNo}", { vacate = true }, Modifier.fillMaxWidth(), color = Brand.Red600, icon = Icons.AutoMirrored.Rounded.Logout)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    collect?.let { kind ->
        var mode by remember { mutableStateOf("Cash") }
        val amount = if (kind == "FEE") m.monthlyFee else m.deposit
        ModalBottomSheet({ collect = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetTitle(when (kind) { "FEE" -> "Renew for 1 month"; "DEPOSIT" -> "Receive deposit"; else -> "Return deposit" },
                    "${rupees(amount)} · " + if (kind == "FEE") "valid till ${LocalDate.ofEpochDay(maxOf(m.validTill, t)).plusMonths(1).toEpochDay().asDate()}" else m.name)
                Text(if (kind == "DEPOSIT_RETURN") "Returned by" else "Paid by", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                ModeChoice(mode) { mode = it }
                PrimaryButton("Confirm ${rupees(amount)}", {
                    val updated = when (kind) {
                        "FEE" -> m.copy(validTill = LocalDate.ofEpochDay(maxOf(m.validTill, t)).plusMonths(1).toEpochDay())
                        "DEPOSIT" -> m.copy(depositPaid = true)
                        else -> m.copy(depositReturnedOn = t)
                    }
                    vm.pay(updated, kind, amount, mode) { saved = it }
                    collect = null
                }, Modifier.fillMaxWidth(), accent = kind == "FEE", icon = Icons.Rounded.Check)
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
