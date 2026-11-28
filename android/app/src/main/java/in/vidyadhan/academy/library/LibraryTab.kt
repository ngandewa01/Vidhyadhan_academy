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
    fun deletePayment(p: `in`.vidyadhan.academy.data.LibraryPayment) = viewModelScope.launch { dao.deletePayment(p) }
    fun seatHistory(seat: Int) = dao.seatHistory(seat)
    fun add(m: LibraryMember, pays: List<LibraryPayment>, onSaved: (Long) -> Unit = {}) = viewModelScope.launch { onSaved(dao.addMember(m, pays)) }
    fun update(m: LibraryMember, onSaved: (Long) -> Unit = {}) = viewModelScope.launch { dao.updateMember(m); onSaved(m.id) }

    /** Changes the type of one seat; the member sitting there (if any) moves to the new type name too. */
    fun setSeatType(n: Int, chosen: SeatType) = viewModelScope.launch {
        reassignSeat(n, chosen, dao.seatTypesNow()).forEach { dao.updateType(it) }
        dao.allMembers().filter { it.active && it.seatNo == n && it.seatType != chosen.name }.forEach { dao.updateMember(it.copy(seatType = chosen.name)) }
    }
    fun delete(m: LibraryMember) = viewModelScope.launch { dao.deleteMember(m) }
    fun addRoom(r: LibraryRoom, onSaved: (Long) -> Unit = {}) = viewModelScope.launch { onSaved(dao.insertRoom(r)) }
    fun saveRoom(r: LibraryRoom) = viewModelScope.launch { if (r.id == 0L) dao.insertRoom(r) else dao.updateRoom(r) }
    fun deleteRoom(r: LibraryRoom) = viewModelScope.launch { dao.deleteRoom(r) }
    fun savePlan(p: MemberPlan) = viewModelScope.launch { if (p.id == 0L) dao.insertPlan(p) else dao.updatePlan(p) }
    fun deletePlan(p: MemberPlan) = viewModelScope.launch { dao.deletePlan(p) }
    fun pay(m: LibraryMember, kind: String, amount: Long, mode: String, onSaved: (LibraryPayment) -> Unit = {}) = viewModelScope.launch {
        val p = LibraryPayment(memberId = m.id, kind = kind, amount = amount, paidOn = today(), mode = mode)
        onSaved(p.copy(id = dao.pay(m, p)))
    }
}

private sealed interface Screen {
    data object Main : Screen
    data class Form(val seat: Int, val id: Long = 0) : Screen
    data class Detail(val id: Long, val renew: Boolean = false) : Screen
    data object Plans : Screen
}

fun lockerLabel(m: LibraryMember) = when { !m.hasLocker -> ""; m.lockerNo == "-" -> "Locker"; else -> "Locker ${m.lockerNo}" }

fun kindLabel(k: String) = when (k) {
    "FEE" -> "Monthly fee"; "DEPOSIT" -> "Deposit received"; "REGISTRATION" -> "Registration fee"
    "DEPOSIT_RETURN" -> "Deposit returned"; else -> k
}

/** WhatsApp reminder for a member: the fee when it is due, otherwise the balance. */
fun libraryReminder(m: LibraryMember, t: Long): String {
    val amount = when { m.validTill < t -> m.monthlyFee + m.balance; m.balance > 0 -> m.balance; else -> m.monthlyFee }
    return `in`.vidyadhan.academy.Templates.fill("library", true, mapOf("name" to m.name, "amount" to rupees(amount), "date" to m.validTill.asDate(), "seat" to "${m.seatNo}"))
}

@Composable
fun LibraryTab(
    openSeats: Boolean = false, openNew: Boolean = false, openId: Long = 0, startFilter: MemberFilter = MemberFilter.All,
    onOpened: () -> Unit = {}, vm: LibraryViewModel = viewModel(),
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var memberFilter by rememberSaveable { mutableStateOf(MemberFilter.All) }
    var seats by rememberSaveable { mutableStateOf(false) } // the list of names opens first; the room plan is one tap away
    LaunchedEffect(openSeats) { if (openSeats) { seats = true; screen = Screen.Main; onOpened() } }
    LaunchedEffect(openNew, openId, startFilter) { if (openNew || openId > 0 || startFilter != MemberFilter.All) seats = false }
    val switcher: @Composable () -> Unit = {
        Spacer(Modifier.height(14.dp))
        Text("Room plan →", Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f)).clickable { seats = true }
            .padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
    LaunchedEffect(startFilter) { if (startFilter != MemberFilter.All) memberFilter = startFilter }
    val members by vm.members.collectAsStateWithLifecycle()
    LaunchedEffect(openNew, openId, startFilter) {
        if (openNew) {
            val taken = members.filter { it.active }.map { it.seatNo }.toSet()
            val rooms = AppDb.get(vm.getApplication<Application>()).library().roomsNow()
            screen = Screen.Form(allSeats(rooms, AppSettings.seatCount).firstOrNull { it !in taken } ?: 0); onOpened()
        }
        else if (openId > 0) { screen = Screen.Detail(openId); onOpened() }
        else if (startFilter != MemberFilter.All) { screen = Screen.Main; onOpened() }
    }
    BackHandler(enabled = screen != Screen.Main) { screen = (screen as? Screen.Form)?.takeIf { it.id != 0L }?.let { Screen.Detail(it.id) } ?: Screen.Main }
    AnimatedContent(screen, transitionSpec = {
        if (targetState == Screen.Main) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "lib") { s ->
        when (s) {
            Screen.Main -> if (seats) RoomPlanPage(vm, { screen = Screen.Form(it) }, { screen = Screen.Detail(it) }, onMembers = { seats = false }, onRenew = { screen = Screen.Detail(it, renew = true) })
            else MembersPage(vm, memberFilter, { memberFilter = it }, onAdd = {
                val taken = members.filter { it.active }.map { it.seatNo }.toSet()
                screen = Screen.Form(allSeats(vm.rooms.value, AppSettings.seatCount).firstOrNull { it !in taken } ?: 0)
            }, onOpen = { screen = Screen.Detail(it) }, onRenew = { screen = Screen.Detail(it, renew = true) }, onPlans = { screen = Screen.Plans }, switcher = switcher)
            Screen.Plans -> PlansPage(vm) { screen = Screen.Main }
            is Screen.Form -> MemberForm(vm, s.seat, s.id, onCancel = { screen = if (s.id == 0L) Screen.Main else Screen.Detail(s.id) }, onSaved = { screen = Screen.Detail(it) })
            is Screen.Detail -> MemberDetail(vm, s.id, startRenew = s.renew, onBack = { screen = Screen.Main }, onOpen = { screen = Screen.Detail(it) }, onEdit = { screen = Screen.Form(0, s.id) })
        }
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
                PhoneField(mobile, { mobile = it }, onName = { if (name.isBlank()) name = it },
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MemberDetail(vm: LibraryViewModel, id: Long, startRenew: Boolean = false, onBack: () -> Unit, onOpen: (Long) -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val mm by remember(id) { vm.member(id) }.collectAsStateWithLifecycle(null)
    val pays by remember(id) { vm.payments(id) }.collectAsStateWithLifecycle(emptyList())
    var deletePay by remember { mutableStateOf<`in`.vidyadhan.academy.data.LibraryPayment?>(null) }
    val types by vm.types.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    var vacate by remember { mutableStateOf(false) }
    var collect by remember { mutableStateOf<String?>(if (startRenew) "FEE" else null) } // FEE, BALANCE, DEPOSIT or DEPOSIT_RETURN
    var saved by remember { mutableStateOf<LibraryPayment?>(null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    val m = mm ?: return
    val history by remember(m.seatNo) { vm.seatHistory(m.seatNo) }.collectAsStateWithLifecycle(emptyList())
    val t = today()
    val due = m.validTill < t
    val icon = types.firstOrNull { it.name == m.seatType }?.icon ?: "CHAIR"
    // a member saved without a monthly fee renews at the seat type's fee (plus the locker), which is then kept on the member
    val seatTypeFee = (types.firstOrNull { it.name == m.seatType } ?: typeForSeat(m.seatNo, types))?.fee ?: 0L
    val lockerPart = if (m.hasLocker) m.lockerFee.takeIf { it > 0 } ?: AppSettings.lockerFee.toLong() else 0L
    val fee = m.monthlyFee.takeIf { it > 0 } ?: (seatTypeFee + lockerPart)
    val feeMissing = m.monthlyFee == 0L && fee > 0
    // deleting the latest monthly fee also undoes that renewal: the paid-till date goes back by its plan's months
    deletePay?.let { p ->
        val latestFee = p.kind == "FEE" && pays.filter { it.kind == "FEE" }.maxWithOrNull(compareBy({ it.paidOn }, { it.id }))?.id == p.id
        val opt = renewOptions(fee, plans, m.hasLocker).firstOrNull { it.name == m.planName }
        val months = opt?.months ?: m.planName.substringBefore(" ").toIntOrNull() ?: 1
        val back = LocalDate.ofEpochDay(m.validTill).minusMonths(months.toLong()).toEpochDay()
        val undo = latestFee && pays.count { it.kind == "FEE" } > 1 && back >= m.joinedOn
        ConfirmDialog(if (undo) "Undo this renewal?" else "Delete this payment?",
            if (undo) "${rupees(p.amount)} on ${p.paidOn.asDate()} is removed, and fee paid up to goes back from ${m.validTill.asDate()} to ${back.asDate()}."
            else "${rupees(p.amount)} on ${p.paidOn.asDate()} will be removed from the records. The paid-till date does not change.",
            if (undo) "Undo renewal" else "Delete", danger = true,
            onConfirm = {
                vm.deletePayment(p)
                if (undo) vm.update(m.copy(validTill = back, balance = (m.balance - ((opt?.price ?: p.amount) - p.amount).coerceAtLeast(0)).coerceAtLeast(0)))
                deletePay = null
            }, onDismiss = { deletePay = null })
    }
    HideBottomNav()
    val remindText = libraryReminder(m, t)

    var receiptFor by remember { mutableStateOf<LibraryPayment?>(null) }
    fun receipt(p: LibraryPayment) { receiptFor = p }
    fun receiptOf(p: LibraryPayment) = (ReceiptData(
        "${AppSettings.receiptPrefix}-L${p.id.toString().padStart(5, '0')}", p.paidOn,
        if (p.kind == "DEPOSIT_RETURN") "DEPOSIT REFUND" else "ABHYASIKA RECEIPT", m.name,
        listOf("Mobile" to "+91 ${m.mobile}", "Seat" to listOfNotNull("${m.seatNo}", roomOf(m.seatNo, rooms)?.name, m.seatType.ifBlank { null }).joinToString(" · "), "For" to kindLabel(p.kind), "Payment mode" to p.mode.ifBlank { "—" }),
        p.amount, if (p.kind == "FEE") listOfNotNull("Valid up to" to m.validTill.asDate(), m.planName.takeIf { it.isNotBlank() }?.let { "Plan" to it },
            m.balance.takeIf { it > 0 }?.let { "Balance left" to rupees(it) }) else emptyList(),
        forText = "${kindLabel(p.kind)} · seat ${m.seatNo}" + (roomOf(m.seatNo, rooms)?.name?.let { " · $it" } ?: ""), forSub = p.mode))
    receiptFor?.let { p -> ReceiptActions(receiptOf(p), m.mobile, m.studentId?.takeIf { !`in`.vidyadhan.academy.telegram.TelegramBot.isLinked("m${m.id}") }?.let { "s$it" } ?: "m${m.id}",
        onDelete = { deletePay = p }, onDismiss = { receiptFor = null }) }

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
                RoundAction(Icons.AutoMirrored.Rounded.Chat, if (m.active && memberStatus(m, t) != SeatStatus.Active) "Send reminder" else "WhatsApp", Brand.WhatsApp,
                    { if (m.active && memberStatus(m, t) != SeatStatus.Active) Contact.whatsapp(ctx, m.mobile, remindText) else Contact.whatsapp(ctx, m.mobile) }, glass = true)
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
                        PrimaryButton(if (fee > 0) "Renew · from ${rupees(fee)}" else "Renew", { collect = "FEE" }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Autorenew)
                    }
                }
                if (m.deposit > 0 || m.depositPaid) Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    InfoRow(Icons.Rounded.CurrencyRupee, "Monthly fee", if (feeMissing) "${rupees(fee)} (seat type fee: none was saved)" else rupees(m.monthlyFee) + if (m.hasLocker && m.lockerFee > 0) " (seat ${rupees(m.monthlyFee - m.lockerFee)} + locker ${rupees(m.lockerFee)})" else "")
                    if (m.hasLocker) { HorizontalDivider(color = Brand.Line); InfoRow(Icons.Rounded.Lock, "Locker", if (m.lockerNo == "-") "Yes (number not noted)" else "Locker no. ${m.lockerNo}") }
                }
                SectionHeader("Payment history")
                if (pays.isEmpty()) Text("No payments recorded yet.", color = Brand.Slate500)
                else Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                    pays.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = Brand.Line)
                        val out = p.kind == "DEPOSIT_RETURN"
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(when (p.kind) { "FEE" -> Icons.Rounded.CalendarMonth; "DEPOSIT", "DEPOSIT_RETURN" -> Icons.Rounded.Savings; else -> Icons.Rounded.HowToReg },
                                    if (out) Brand.Red600 else Brand.Green600, 40.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(kindLabel(p.kind), style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                                    Text(p.paidOn.asDate() + if (p.mode.isNotBlank()) " · ${p.mode}" else "", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text((if (out) "− " else "+ ") + rupees(p.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (out) Brand.Red600 else Brand.Green700, maxLines = 1, softWrap = false)
                            }
                            Row(Modifier.padding(start = 54.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PayAction("WhatsApp", Icons.AutoMirrored.Rounded.Chat, Brand.WhatsAppDark, Color(0xFFE7F6F3)) { whatsappReceipt(ctx, receiptOf(p), m.mobile) }
                                    PayAction("Receipt", Icons.Rounded.ReceiptLong, Brand.Blue600, Color(0xFFEFF6FF)) { receiptFor = p }
                                }
                                Spacer(Modifier.width(6.dp))
                                SmallIconButton(Icons.Rounded.DeleteOutline, "Delete payment", Brand.Slate400) { deletePay = p }
                            }
                        }
                    }
                }
                NotesSection("MEMBER", m.id)
                SoftButton("Admission form (PDF)", {
                    shareAdmissionForm(ctx, AdmissionDoc("ABHYASIKA ADMISSION", "${AppSettings.receiptPrefix}-LA${m.id.toString().padStart(4, '0')}", m.joinedOn,
                        m.name, m.mobile, m.photo, listOfNotNull("Seat ${m.seatNo}", m.seatType.ifBlank { null }, if (m.hasLocker) lockerLabel(m) else null, "Joined ${m.joinedOn.asDate()}"),
                        m.dob, m.aadhaar, "", "", "", "FEES",
                        listOf(Triple("Monthly fee", rupees(fee), ""),
                            Triple("Refundable deposit", rupees(m.deposit), if (m.depositPaid && m.depositReturnedOn == null && m.deposit > 0) "HELD" else ""))))
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Description)
                SeatHistory(history, onOpen, excludeId = m.id)
                if (m.active) SoftButton("Member left · free seat ${m.seatNo}", { vacate = true }, Modifier.fillMaxWidth(), color = Brand.Red600, icon = Icons.AutoMirrored.Rounded.Logout)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    collect?.let { kind ->
        var mode by remember { mutableStateOf("Cash") }
        val options = renewOptions(fee, plans, m.hasLocker)
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
                        "FEE" -> m.copy(validTill = till, planName = chosen.name, balance = m.balance + balanceAfter(0, chosen.price, amount),
                            monthlyFee = if (feeMissing) fee else m.monthlyFee, lockerFee = if (feeMissing) lockerPart else m.lockerFee)
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
    if (confirm) DeletePersonDialog("Delete ${m.name}?", "All their payment records are deleted too. To keep records, use “Member left” instead.",
        `in`.vidyadhan.academy.telegram.TelegramBot.isLinked("m${m.id}"),
        onConfirm = { tg -> if (tg) `in`.vidyadhan.academy.telegram.TelegramBot.unlink("m${m.id}")
            Photos.delete(ctx, m.photo); Photos.delete(ctx, m.aadhaarPhoto); vm.delete(m); confirm = false; onBack() }, onDismiss = { confirm = false })
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
}


@Composable
private fun PayAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, fg: Color, bg: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, softWrap = false)
    }
}
