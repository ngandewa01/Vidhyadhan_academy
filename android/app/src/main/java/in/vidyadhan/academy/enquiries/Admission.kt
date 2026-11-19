package `in`.vidyadhan.academy.enquiries

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.*
import `in`.vidyadhan.academy.library.allSeats
import `in`.vidyadhan.academy.library.typeForSeat
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class InstRow(val label: String, val amount: String, val due: Long)

/**
 * New admission, like the owner's prototype: one form for an academy student, an Abhyasika (library) member, or both ("Combo").
 * Course installments are the student's plan; the seat and locker are monthly and start today.
 * [from] fills in the details when an enquiry is admitted. [onDone] gets "student" or "member" and the new id.
 */
@Composable
fun AdmissionForm(from: Enquiry?, onDone: (String, Long) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    val rooms by db.library().rooms().collectAsStateWithLifecycle(emptyList())
    val types by db.library().seatTypes().collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val t = today()

    var name by remember(from) { mutableStateOf(from?.name ?: "") }
    var mobile by remember(from) { mutableStateOf(from?.mobile ?: "") }
    var telegram by remember(from) { mutableStateOf(from?.telegram ?: "") }
    var type by remember(from) { mutableStateOf("Combo") } // Academy, Abhyasika, Combo
    var course by remember(from) { mutableStateOf(from?.course?.takeIf { it in AppSettings.courseList } ?: AppSettings.courseList.first()) }
    var batch by remember { mutableStateOf("") }
    var courseFee by remember(course) { mutableStateOf(AppSettings.courseFee(course).takeIf { it > 0 }?.toString() ?: "") }
    val active = members.filter { it.active }
    val freeSeats = allSeats(rooms, AppSettings.seatCount).filter { n -> active.none { it.seatNo == n } }
    var seat by remember { mutableIntStateOf(0) }
    LaunchedEffect(freeSeats) { if (seat !in freeSeats) seat = freeSeats.firstOrNull() ?: 0 }
    val seatType = typeForSeat(seat, types)
    val seatFee = seatType?.fee ?: 0L
    var locker by remember { mutableStateOf(false) }
    val usedLockers = active.map { it.lockerNo }.toSet()
    val freeLockers = (1..AppSettings.lockerCount).map { "$it" }.filter { it !in usedLockers }
    var lockerNo by remember { mutableStateOf("") }
    LaunchedEffect(freeLockers, locker) { if (locker && lockerNo !in freeLockers) lockerNo = freeLockers.firstOrNull() ?: "" }
    val lockerFee = if (locker) AppSettings.lockerFee.toLong() else 0L
    var deposit by remember(seatType) { mutableStateOf(((seatType?.deposit ?: 0L) + 0).toString()) }
    var advance by remember { mutableStateOf("0") }
    var planType by remember { mutableStateOf("Unequal") } // Full, Equal, Unequal
    var equalCount by remember { mutableIntStateOf(3) }
    val rows = remember { mutableStateListOf<InstRow>() }
    var paidFirst by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf("UPI") }

    val academy = type != "Abhyasika"
    val library = type != "Academy"
    val fee = courseFee.toLongOrNull() ?: 0L
    // unequal rows start as three parts; they follow the fee until the owner types in them
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(fee, planType) {
        if (planType == "Unequal" && (!edited || rows.isEmpty())) {
            rows.clear()
            val parts = if (fee > 0) buildPlan(fee, 3, LocalDate.ofEpochDay(t)) else emptyList()
            parts.forEachIndexed { i, p -> rows.add(InstRow(if (i == 0) "Inst 1 (Today)" else "Inst ${i + 1}", "${p.amount}", p.dueOn)) }
        }
    }
    val plan: List<Installment> = when (planType) {
        "Full" -> if (fee > 0) listOf(Installment(studentId = 0, number = 1, amount = fee, dueOn = t, label = "Full fee")) else emptyList()
        "Equal" -> if (fee > 0) buildPlan(fee, equalCount, LocalDate.ofEpochDay(t)).mapIndexed { i, p -> p.copy(label = "Inst ${i + 1} (${i + 1}/$equalCount)") } else emptyList()
        else -> rows.mapIndexed { i, r -> Installment(studentId = 0, number = i + 1, amount = r.amount.toLongOrNull() ?: 0, dueOn = r.due, label = r.label.trim()) }
    }
    val planOk = !academy || (fee > 0 && plan.isNotEmpty() && plan.all { it.amount > 0 } && plan.sumOf { it.amount } == fee)
    val total = (if (academy) fee else 0L) + (if (library) seatFee + lockerFee else 0L)
    val dep = deposit.toLongOrNull() ?: 0L
    val adv = if (academy) advance.toLongOrNull() ?: 0L else 0L
    val firstAmount = if (academy && paidFirst) plan.firstOrNull()?.amount ?: 0L else 0L
    val advOk = adv <= (fee - firstAmount).coerceAtLeast(0)
    val valid = name.isNotBlank() && mobile.length == 10 && planOk && (!library || seat > 0) && (!library || !locker || lockerNo.isNotBlank()) && advOk
    val payingNow = firstAmount + adv + (if (library) seatFee + lockerFee + dep else 0L)

    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Step("1. Student & Telegram details") {
            Field(name, { name = it }, "Full name *", Icons.Rounded.Person)
            DigitsField("Mobile number", mobile, 10, { mobile = it }, prefix = "+91 ", phone = true, isError = mobile.isNotEmpty() && mobile.length < 10,
                supporting = "Telegram is linked later through your bot (no username needed)")
        }

        Step("2. Course, seat & locker") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Academy" to "Academy only", "Abhyasika" to "Abhyasika only", "Combo" to "Combo").forEach { (k, l) ->
                    Toggle(l, type == k, Brand.Blue700, Modifier.weight(1f)) { type = k }
                }
            }
            if (academy) {
                Picker("Select course / exam batch", course + (AppSettings.courseFee(course).takeIf { it > 0 }?.let { " — ${rupees(it)}" } ?: ""),
                    AppSettings.courseList.map { c -> c to (c + (AppSettings.courseFee(c).takeIf { it > 0 }?.let { " — ${rupees(it)}" } ?: "")) }) { course = it }
                DigitsField("Course fee", courseFee, 7, { courseFee = it }, prefix = "₹ ", supporting = "Remembered for the next admission to this course")
                if (AppSettings.batchList.isNotEmpty()) ChoicePills(AppSettings.batchList, batch, { batch = if (batch == it) "" else it })
            }
            if (library) {
                HorizontalDivider(color = Brand.Line)
                if (freeSeats.isEmpty()) Text("No free seat. Free one on the Abhyasika page first.", style = MaterialTheme.typography.bodySmall, color = Brand.Red600)
                else Picker("Abhyasika seat (${types.joinToString(" vs ") { "${it.name} ${rupees(it.fee)}" }})",
                    seatLabel(seat, types), freeSeats.map { "$it" to seatLabel(it, types) }) { seat = it.toInt() }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF5F3FF)).border(1.dp, Color(0xFFDDD6FE), RoundedCornerShape(14.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Separate locker (+${rupees(AppSettings.lockerFee.toLong())}/mo)", style = MaterialTheme.typography.labelLarge, color = Color(0xFF3B0764))
                        Text("Dedicated book storage cabinet", style = MaterialTheme.typography.labelSmall, color = Brand.Violet700)
                    }
                    if (locker && freeLockers.isNotEmpty()) Picker("", "L-$lockerNo", freeLockers.map { it to "L-$it" }, Modifier.width(96.dp)) { lockerNo = it }
                    Switch(locker, { locker = it })
                }
            }
            HorizontalDivider(color = Brand.Line)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DigitsField("Deposit", deposit, 6, { deposit = it }, prefix = "₹ ", modifier = Modifier.weight(1f))
                if (academy) DigitsField("Advance", advance, 6, { advance = it }, prefix = "₹ ", modifier = Modifier.weight(1f),
                    isError = !advOk, supporting = if (!advOk) "More than the rest of the fee" else null)
            }
        }

        if (academy) Step("3. Installment schedule", trailing = "Fee: ${rupees(total)}") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Full", "Equal", "Unequal").forEach { p -> Toggle("$p plan", planType == p, Brand.Saffron500, Modifier.weight(1f)) { planType = p } }
            }
            when (planType) {
                "Full" -> Note("Single payment of ${rupees(fee)}" + (if (paidFirst) " marked paid today" else " due today") + if (dep > 0 && library) " + ${rupees(dep)} deposit." else ".")
                "Equal" -> {
                    Stepper("Number of installments", equalCount, 2..12) { equalCount = it }
                    Note("Auto-split into $equalCount equal installments of ${rupees(if (equalCount > 0) fee / equalCount else 0)} each, one a month from today.")
                }
                else -> {
                    Text("Change the names, amounts and dates:", style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
                    rows.forEachIndexed { i, r ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(12.dp)).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SmallInput(r.label, { v -> rows[i] = r.copy(label = v.take(24)); edited = true }, Modifier.weight(1f))
                                if (rows.size > 1) Box(Modifier.size(32.dp).clip(CircleShape).clickable { rows.removeAt(i); edited = true }, Alignment.Center) {
                                    Icon(Icons.Rounded.Close, "Remove", tint = Brand.Slate400, modifier = Modifier.size(18.dp))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("₹", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                                SmallInput(r.amount, { v -> if (v.all(Char::isDigit) && v.length <= 7) { rows[i] = r.copy(amount = v); edited = true } }, Modifier.weight(1f), number = true)
                                DateChip(r.due, Modifier.weight(1f)) { d -> rows[i] = r.copy(due = d); edited = true }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("+ Add installment", Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            val last = rows.lastOrNull()?.due ?: t
                            rows.add(InstRow("Inst ${rows.size + 1}", "${(fee - plan.sumOf { it.amount }).coerceAtLeast(0)}", LocalDate.ofEpochDay(last).plusMonths(1).toEpochDay())); edited = true
                        }.padding(4.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Blue700)
                        Spacer(Modifier.weight(1f))
                        val diff = fee - plan.sumOf { it.amount }
                        Text(if (diff == 0L) "Adds up ✓" else if (diff > 0) "${rupees(diff)} not placed" else "${rupees(-diff)} too much",
                            style = MaterialTheme.typography.labelMedium, color = if (diff == 0L) Brand.Green700 else Brand.Red600)
                    }
                }
            }
            SwitchRow("First installment paid today", plan.firstOrNull()?.let { rupees(it.amount) }, paidFirst) { paidFirst = it }
        }
        if (payingNow > 0) Step("Paid today", trailing = rupees(payingNow)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Cash", "UPI", "Bank").forEach { m -> Toggle(m, mode == m, Brand.Green600, Modifier.weight(1f)) { mode = m } }
            }
            Text(listOfNotNull(
                firstAmount.takeIf { it > 0 }?.let { "1st installment ${rupees(it)}" },
                adv.takeIf { it > 0 }?.let { "advance ${rupees(it)}" },
                (seatFee + lockerFee).takeIf { library && it > 0 }?.let { "seat${if (locker) " + locker" else ""} ${rupees(it)} (1 month)" },
                dep.takeIf { library && it > 0 }?.let { "deposit ${rupees(it)}" },
            ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
        }

        PrimaryButton("Confirm admission & send Telegram receipt", {
            scope.launch {
                var studentId = 0L
                if (academy) {
                    AppSettings.setCourseFee(course, fee)
                    val s = Student(name = name.trim(), mobile = mobile, course = course, totalFee = fee, joinedOn = t, batch = batch, telegram = telegram)
                    val first = if (paidFirst && plan.isNotEmpty()) StudentPayment(studentId = 0, amount = plan[0].amount, paidOn = t, mode = mode, note = "Admission") else null
                    studentId = db.students().create(s, plan, first)
                    if (adv > 0) db.students().insertPayment(StudentPayment(studentId = studentId, amount = adv, paidOn = t, mode = mode, note = "Advance"))
                }
                var memberId = 0L
                if (library && seat > 0) {
                    val monthly = seatFee + lockerFee
                    val m = LibraryMember(name = name.trim(), mobile = mobile, seatNo = seat, seatType = seatType?.name ?: "", monthlyFee = monthly, deposit = dep,
                        depositPaid = dep > 0, joinedOn = t, validTill = LocalDate.ofEpochDay(t).plusMonths(1).toEpochDay(),
                        lockerNo = if (locker) lockerNo.ifBlank { "-" } else "", lockerFee = lockerFee, planName = "1 month", studentId = studentId.takeIf { it > 0 })
                    val pays = buildList {
                        if (monthly > 0) add(LibraryPayment(memberId = 0, kind = "FEE", amount = monthly, paidOn = t, mode = mode))
                        if (dep > 0) add(LibraryPayment(memberId = 0, kind = "DEPOSIT", amount = dep, paidOn = t, mode = mode))
                    }
                    memberId = db.library().addMember(m, pays)
                }
                from?.let { db.enquiries().update(it.copy(status = "JOINED")) }
                val next = if (academy) plan.drop(if (paidFirst) 1 else 0).firstOrNull() else null
                val receipt = buildString {
                    append("✅ Admission confirmed — ${AppSettings.academyName}\n")
                    append("Student: ${name.trim()}\n")
                    if (academy) append("Course: $course" + (if (batch.isNotBlank()) " · $batch" else "") + "\n")
                    if (library) append("Abhyasika seat $seat" + (seatType?.let { " (${it.name})" } ?: "") + (if (locker) " + Locker L-$lockerNo" else "") + "\n")
                    if (payingNow > 0) append("Paid today: ${rupees(payingNow)} ($mode)\n")
                    if (next != null) append("Next due: ${rupees(next.amount)} on ${next.dueOn.asDate()}\n")
                    append("Thank you! 🙏")
                }
                // through the academy bot when the student is linked, otherwise the Telegram app opens with it
                `in`.vidyadhan.academy.telegram.TelegramBot.sendOrExplain(ctx, if (studentId > 0) "s$studentId" else "m$memberId", mobile, `in`.vidyadhan.academy.telegram.TelegramBot.esc(receipt))
                if (studentId > 0) onDone("student", studentId) else onDone("member", memberId)
            }
        }, Modifier.fillMaxWidth(), enabled = valid, icon = Icons.Rounded.Check)
        Spacer(Modifier.height(24.dp))
    }
}

private fun seatLabel(n: Int, types: List<SeatType>): String {
    val ty = typeForSeat(n, types)
    return if (n <= 0) "Choose a seat" else "Seat $n — ${ty?.name ?: "Normal"} (${rupees(ty?.fee ?: 0)}/mo)"
}

@Composable
private fun Step(title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Blue700)
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Ink)
        }
        content()
    }
}

@Composable
private fun Toggle(text: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(text, modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) color else Brand.Slate50).border(1.dp, if (selected) color else Brand.Line, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = if (selected) Color.White else Brand.Slate600, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(12.dp)).padding(10.dp),
        style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
}

/** A label above a tappable box that opens a list of choices. [options] are (key, text). */
@Composable
private fun Picker(label: String, value: String, options: List<Pair<String, String>>, modifier: Modifier = Modifier.fillMaxWidth(), onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Slate600, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(12.dp)).clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.ExpandMore, null, tint = Brand.Slate400)
            }
            DropdownMenu(open, { open = false }, containerColor = Color.White) {
                options.forEach { (k, l) -> DropdownMenuItem({ Text(l) }, { onPick(k); open = false }) }
            }
        }
    }
}

@Composable
private fun SmallInput(value: String, onChange: (String) -> Unit, modifier: Modifier, number: Boolean = false) {
    androidx.compose.foundation.text.BasicTextField(value, onChange, modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 8.dp), singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(color = Brand.Ink),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChip(day: Long, modifier: Modifier, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(day.asDate(), modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(8.dp)).clickable { open = true }
        .padding(horizontal = 6.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = Brand.Slate600, maxLines = 1)
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(it / 86_400_000L) }; open = false }) { Text("Done") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}
