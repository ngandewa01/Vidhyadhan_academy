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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
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
    fun add(m: LibraryMember, pays: List<LibraryPayment>) = viewModelScope.launch { dao.addMember(m, pays) }
    fun update(m: LibraryMember) = viewModelScope.launch { dao.updateMember(m) }
    fun delete(m: LibraryMember) = viewModelScope.launch { dao.deleteMember(m) }
    fun pay(m: LibraryMember, kind: String, amount: Long) = viewModelScope.launch {
        dao.pay(m, LibraryPayment(memberId = m.id, kind = kind, amount = amount, paidOn = today()))
    }
}

private sealed interface Screen {
    data object Main : Screen
    data class Form(val seat: Int) : Screen
    data class Detail(val id: Long) : Screen
}

fun kindLabel(k: String) = when (k) {
    "FEE" -> "Monthly fee"; "DEPOSIT" -> "Deposit received"; "REGISTRATION" -> "Registration fee"
    "DEPOSIT_RETURN" -> "Deposit returned"; else -> k
}

@Composable
fun LibraryTab(openNew: Boolean = false, onOpened: () -> Unit = {}, vm: LibraryViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    val members by vm.members.collectAsStateWithLifecycle()
    LaunchedEffect(openNew) {
        if (openNew) { val taken = members.filter { it.active }.map { it.seatNo }.toSet(); screen = Screen.Form((1..AppSettings.seatCount).firstOrNull { it !in taken } ?: 0); onOpened() }
    }
    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main }
    AnimatedContent(screen, transitionSpec = {
        if (targetState == Screen.Main) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "lib") { s ->
        when (s) {
            Screen.Main -> LibraryMain(vm, { screen = Screen.Form(it) }, { screen = Screen.Detail(it) })
            is Screen.Form -> MemberForm(vm, s.seat) { screen = Screen.Main }
            is Screen.Detail -> MemberDetail(vm, s.id) { screen = Screen.Main }
        }
    }
}

@Composable
private fun LibraryMain(vm: LibraryViewModel, onAdd: (Int) -> Unit, onOpen: (Long) -> Unit) {
    val members by vm.members.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf("seats") }
    val active = members.filter { it.active }
    val bySeat = active.associateBy { it.seatNo }
    val total = AppSettings.seatCount
    val t = today()
    val due = active.count { it.validTill < t }
    val held = active.filter { it.depositPaid && it.depositReturnedOn == null }.sumOf { it.deposit }
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
                Segmented(listOf("seats" to "Seat map", "members" to "Members (${active.size})"), view, { view = it },
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp))
                if (view == "seats") {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Color.White, Brand.Green500, "Free"); Legend(Brand.Blue700, Brand.Blue700, "Taken"); Legend(Brand.Red50, Brand.Red500, "Fee due")
                    }
                    LazyVerticalGrid(GridCells.Adaptive(60.dp), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items((1..total).toList()) { n -> SeatTile(n, bySeat[n], t) { m -> if (m == null) onAdd(n) else onOpen(m.id) } }
                    }
                } else if (active.isEmpty()) EmptyState(Icons.Rounded.LocalLibrary, "No members yet", "Tap a free seat on the seat map to add someone.")
                else LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(active.sortedBy { it.seatNo }, key = { it.id }) { m ->
                        ListCard(onClick = { onOpen(m.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SeatBadge(m.seatNo, 50.dp, m.validTill < t)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1)
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (m.seatType.isNotBlank()) { SmallTag(m.seatType, Brand.Teal700); Spacer(Modifier.width(8.dp)) }
                                        Text(m.mobile, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                                    }
                                }
                                when {
                                    m.validTill < t -> StatusChip("Fee due", Tone.Bad)
                                    m.validTill - t <= 3 -> StatusChip("Ends ${shortDate(m.validTill)}", Tone.Warn)
                                    else -> StatusChip("Till ${shortDate(m.validTill)}", Tone.Good)
                                }
                            }
                        }
                    }
                }
            }
            Fab("Add member", Icons.Rounded.PersonAdd, { onAdd((1..total).firstOrNull { it !in bySeat } ?: 0) }, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
}

@Composable
private fun SeatTile(n: Int, m: LibraryMember?, t: Long, onClick: (LibraryMember?) -> Unit) {
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
        Icon(Icons.Rounded.EventSeat, null, tint = if (m == null) Brand.Green500.copy(alpha = 0.55f) else fg.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
        Text("$n", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = if (m == null) Brand.Slate600 else fg)
        if (m != null) Text(m.name.substringBefore(' ').take(7), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f), maxLines = 1)
    }
}

@Composable
private fun SeatBadge(n: Int, size: androidx.compose.ui.unit.Dp, due: Boolean) {
    Column(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(if (due) Brush.linearGradient(listOf(Brand.Red500, Brand.Red700)) else PrimaryBrush),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("SEAT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
        Text("$n", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
    }
}

@Composable
private fun Legend(fill: Color, edge: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(fill).border(1.5.dp, edge, RoundedCornerShape(5.dp)))
    Spacer(Modifier.width(6.dp))
    Text(t, style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
}

@Composable
private fun MemberForm(vm: LibraryViewModel, startSeat: Int, onDone: () -> Unit) {
    val types by vm.types.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var seat by remember { mutableStateOf(if (startSeat > 0) startSeat.toString() else "") }
    var type by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var deposit by remember { mutableStateOf("0") }
    var reg by remember { mutableStateOf("0") }
    var joined by remember { mutableLongStateOf(today()) }
    var regPaid by remember { mutableStateOf(true) }
    var depPaid by remember { mutableStateOf(true) }
    var feePaid by remember { mutableStateOf(true) }
    LaunchedEffect(types) {
        if (type.isEmpty() && types.isNotEmpty()) types.first().let { type = it.name; fee = it.fee.toString(); deposit = it.deposit.toString(); reg = it.regFee.toString() }
    }
    val seatN = seat.toIntOrNull() ?: 0
    val taken = members.any { it.active && it.seatNo == seatN }
    val f = fee.toLongOrNull() ?: 0; val d = deposit.toLongOrNull() ?: 0; val r = reg.toLongOrNull() ?: 0
    val collectingNow = (if (regPaid) r else 0) + (if (depPaid) d else 0) + (if (feePaid) f else 0)
    val valid = name.isNotBlank() && mobile.length == 10 && (aadhaar.isEmpty() || aadhaar.length == 12) && seatN in 1..AppSettings.seatCount && !taken
    HideBottomNav()
    HeroPage("New member", if (seatN > 0) "Seat $seatN" else "Choose a seat", onBack = onDone) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            FormCard("Member details", Icons.Rounded.Person, Brand.Teal700) {
                Field(name, { name = it }, "Full name", Icons.Rounded.Person)
                DigitsField("Mobile number", mobile, 10, { mobile = it }, Icons.Rounded.Phone, prefix = "+91 ", phone = true,
                    supporting = if (mobile.isNotEmpty() && mobile.length < 10) "Enter all 10 digits" else null, isError = mobile.isNotEmpty() && mobile.length < 10)
                DigitsField("Aadhaar number", aadhaar, 12, { aadhaar = it }, Icons.Rounded.CreditCard,
                    supporting = if (aadhaar.isNotEmpty() && aadhaar.length < 12) "Enter all 12 digits" else "Optional · kept only on this phone", isError = aadhaar.isNotEmpty() && aadhaar.length < 12)
            }
            FormCard("Seat", Icons.Rounded.EventSeat) {
                DigitsField("Seat number (1 – ${AppSettings.seatCount})", seat, 3, { seat = it }, Icons.Rounded.Tag,
                    supporting = if (taken) "Seat $seatN is already taken" else null, isError = taken)
                ChoicePills(types.map { it.name }, type, { n -> type = n; types.firstOrNull { it.name == n }?.let { fee = it.fee.toString(); deposit = it.deposit.toString(); reg = it.regFee.toString() } },
                    captions = types.associate { it.name to "${rupees(it.fee)}/month" })
            }
            FormCard("Charges", Icons.Rounded.CurrencyRupee, Brand.Green600) {
                DigitsField("Monthly fee", fee, 6, { fee = it }, prefix = "₹ ")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DigitsField("Deposit", deposit, 6, { deposit = it }, prefix = "₹ ", modifier = Modifier.weight(1f))
                    DigitsField("Registration", reg, 6, { reg = it }, prefix = "₹ ", modifier = Modifier.weight(1f))
                }
                DateField("Joining date", joined, { joined = it })
            }
            FormCard("Collected today", Icons.Rounded.Payments, Brand.Saffron600) {
                SwitchRow("Registration fee", rupees(r), regPaid) { regPaid = it }
                SwitchRow("Deposit", rupees(d), depPaid) { depPaid = it }
                SwitchRow("First month's fee", rupees(f), feePaid) { feePaid = it }
                HorizontalDivider(color = Brand.Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total collecting now", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Slate600)
                    Text(rupees(collectingNow), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Green700)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            SoftButton("Cancel", onDone, Modifier.weight(1f), color = Brand.Slate600)
            PrimaryButton("Add member", {
                val pays = buildList {
                    if (regPaid && r > 0) add(LibraryPayment(memberId = 0, kind = "REGISTRATION", amount = r, paidOn = joined))
                    if (depPaid && d > 0) add(LibraryPayment(memberId = 0, kind = "DEPOSIT", amount = d, paidOn = joined))
                    if (feePaid && f > 0) add(LibraryPayment(memberId = 0, kind = "FEE", amount = f, paidOn = joined))
                }
                val till = if (feePaid) LocalDate.ofEpochDay(joined).plusMonths(1).toEpochDay() else joined
                vm.add(LibraryMember(name = name.trim(), mobile = mobile, aadhaar = aadhaar, seatNo = seatN, seatType = type, monthlyFee = f,
                    deposit = d, depositPaid = depPaid && d > 0, joinedOn = joined, validTill = till), pays)
                onDone()
            }, Modifier.weight(1.6f), enabled = valid, icon = Icons.Rounded.Check)
        }
    }
}

@Composable
private fun MemberDetail(vm: LibraryViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mm by remember(id) { vm.member(id) }.collectAsStateWithLifecycle(null)
    val pays by remember(id) { vm.payments(id) }.collectAsStateWithLifecycle(emptyList())
    var confirm by remember { mutableStateOf(false) }
    var vacate by remember { mutableStateOf(false) }
    val m = mm ?: return
    val t = today()
    val due = m.validTill < t
    HideBottomNav()
    val remindText = "नमस्कार ${m.name}, विद्याधन अकॅडमी लायब्ररी: तुमची मासिक फी ${rupees(m.monthlyFee)} ${m.validTill.asDate()} रोजी देय आहे. धन्यवाद."
    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        ProfileHero(m.name, onBack,
            badge = { Box(Modifier.size(92.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.18f)).padding(5.dp)) { SeatBadge(m.seatNo, 82.dp, due) } },
            chips = { if (m.seatType.isNotBlank()) GlassChip(m.seatType, Icons.Rounded.EventSeat); GlassChip("Since ${shortDate(m.joinedOn)}", Icons.Rounded.CalendarMonth) },
            topActions = { GlassIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirm = true }) },
            actions = {
                RoundAction(Icons.Rounded.Call, "Call", Brand.Blue600, { Contact.call(ctx, m.mobile) }, glass = true)
                RoundAction(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", Brand.WhatsApp, { Contact.whatsapp(ctx, m.mobile) }, glass = true)
                RoundAction(Icons.Rounded.NotificationsActive, "Remind", Brand.Saffron600, { Contact.whatsapp(ctx, m.mobile, remindText) }, glass = true)
            })
        Sheet {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(4.dp))
                // validity
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (!m.active) "Membership ended" else if (due) "Monthly fee is due" else "Fee paid up to", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                            Text(m.validTill.asDate(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (due && m.active) Brand.Red600 else Brand.Ink)
                        }
                        when { !m.active -> StatusChip("Left", Tone.Neutral); due -> StatusChip("Overdue ${t - m.validTill}d", Tone.Bad); else -> StatusChip("${m.validTill - t} days left", Tone.Good) }
                    }
                    if (m.active) {
                        val start = LocalDate.ofEpochDay(m.validTill).minusMonths(1).toEpochDay()
                        ProgressBar(if (due) 1f else (t - start).toFloat() / (m.validTill - start).coerceAtLeast(1), color = if (due) Brand.Red500 else Brand.Teal500, track = Brand.Line, height = 8.dp)
                        PrimaryButton("Fee received · renew 1 month · ${rupees(m.monthlyFee)}", {
                            vm.pay(m.copy(validTill = LocalDate.ofEpochDay(maxOf(m.validTill, t)).plusMonths(1).toEpochDay()), "FEE", m.monthlyFee)
                        }, Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.Autorenew)
                    }
                }
                // deposit
                Row(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Savings, Brand.Violet700, 46.dp, filled = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Deposit · ${rupees(m.deposit)}", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                        Text(when {
                            m.depositReturnedOn != null -> "Returned on ${m.depositReturnedOn.asDate()}"
                            m.depositPaid -> "Held by the library"
                            else -> "Not received yet"
                        }, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                    }
                    if (m.active && !m.depositPaid && m.deposit > 0) PillButton("Receive", { vm.pay(m.copy(depositPaid = true), "DEPOSIT", m.deposit) }, Brand.Violet700)
                    else if (m.depositPaid && m.depositReturnedOn == null) PillButton("Return", { vm.pay(m.copy(depositReturnedOn = t), "DEPOSIT_RETURN", m.deposit) }, Brand.Slate600)
                    else if (m.depositReturnedOn != null) StatusChip("Returned", Tone.Good)
                }
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow(Icons.Rounded.Phone, "Mobile", "+91 ${m.mobile}")
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.CreditCard, "Aadhaar", if (m.aadhaar.isBlank()) "Not added" else maskAadhaar(m.aadhaar))
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.CurrencyRupee, "Monthly fee", rupees(m.monthlyFee))
                }
                SectionHeader("Payment history")
                if (pays.isEmpty()) Text("No payments recorded yet.", color = Brand.Slate500)
                else Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                    pays.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = Brand.Line)
                        val out = p.kind == "DEPOSIT_RETURN"
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(when (p.kind) { "FEE" -> Icons.Rounded.CalendarMonth; "DEPOSIT", "DEPOSIT_RETURN" -> Icons.Rounded.Savings; else -> Icons.Rounded.HowToReg },
                                if (out) Brand.Red600 else Brand.Green600, 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(kindLabel(p.kind), style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                                Text(p.paidOn.asDate(), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                            }
                            Text((if (out) "− " else "+ ") + rupees(p.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (out) Brand.Red600 else Brand.Green700)
                        }
                    }
                }
                if (m.active) SoftButton("Member left · free seat ${m.seatNo}", { vacate = true }, Modifier.fillMaxWidth(), color = Brand.Red600, icon = Icons.Rounded.Logout)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if (vacate) ConfirmDialog("Free seat ${m.seatNo}?",
        if (m.depositPaid && m.depositReturnedOn == null) "The deposit of ${rupees(m.deposit)} has not been returned yet. Return it first if needed." else "${m.name}'s records are kept.",
        "Free seat", onConfirm = { vm.update(m.copy(active = false)); vacate = false; onBack() }, onDismiss = { vacate = false })
    if (confirm) ConfirmDialog("Delete ${m.name}?", "All their payment records are deleted too.", "Delete", danger = true,
        onConfirm = { vm.delete(m); confirm = false; onBack() }, onDismiss = { confirm = false })
}
