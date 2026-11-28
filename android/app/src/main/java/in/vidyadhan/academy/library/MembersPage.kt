package `in`.vidyadhan.academy.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.LibraryRoom
import `in`.vidyadhan.academy.data.MemberPlan
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.shortDate
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.*

/** Which members the list shows. */
enum class MemberFilter(val label: String) { All("All members"), Active("Active"), Ending("Ending soon"), Partly("Part paid"), Expired("Expired"), Past("Left") }

private fun matches(f: MemberFilter, m: LibraryMember, t: Long): Boolean = when (f) {
    MemberFilter.All -> m.active
    MemberFilter.Past -> !m.active
    else -> m.active && memberStatus(m, t).name == f.name
}

/** "Members": counts, search, filters and the member cards. */
@Composable
internal fun MembersPage(vm: LibraryViewModel, filter: MemberFilter, setFilter: (MemberFilter) -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit, onRenew: (Long) -> Unit = onOpen, onPlans: () -> Unit,
                         switcher: @Composable () -> Unit = {}) {
    val ctx = LocalContext.current
    val members by vm.members.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    // students who also have a seat (Combo): their course, for the tag
    val studs by remember { `in`.vidyadhan.academy.data.AppDb.get(ctx).students().students() }.collectAsStateWithLifecycle(emptyList())
    val courseOf = remember(studs) { studs.associate { it.id to it.course } }
    var sort by rememberSaveable { mutableStateOf("Seat number") }
    var query by rememberSaveable { mutableStateOf("") }
    val t = today()
    val active = members.filter { it.active }
    fun count(f: MemberFilter) = members.count { matches(f, it, t) }
    val q = query.trim().lowercase()
    val shown = members.filter { matches(filter, it, t) }
        .filter { m -> q.isEmpty() || m.name.lowercase().contains(q) || m.mobile.contains(q) || m.seatNo.toString() == q || m.lockerNo.lowercase() == q }
        .let { l -> when (sort) { "Name" -> l.sortedBy { it.name.lowercase() }; "Valid till" -> l.sortedBy { it.validTill }; else -> l.sortedBy { it.seatNo } } }

    HeroPage("Abhyasika", "${active.size} members · ${count(MemberFilter.Expired)} expired", header = { switcher() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Add member", onAdd, Modifier.weight(1.4f), accent = true, icon = Icons.Rounded.PersonAdd)
                    SoftButton("Plans", onPlans, Modifier.weight(1f), icon = Icons.Rounded.Loyalty)
                }
            }
            // one row of filters with live counts (replaces the big grid and the status dropdown)
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MemberFilter.All to "All", MemberFilter.Active to "Active", MemberFilter.Ending to "Ending soon",
                        MemberFilter.Partly to "Part paid", MemberFilter.Expired to "Expired", MemberFilter.Past to "Left").forEach { (f, l) ->
                        val on = filter == f
                        Text("$l (${count(f)})", Modifier.clip(RoundedCornerShape(50)).background(if (on) Brand.Blue700 else Color.White)
                            .border(1.dp, if (on) Brand.Blue700 else Brand.Line, RoundedCornerShape(50)).clickable { setFilter(f) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Brand.Slate600, maxLines = 1)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 4.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchBox(query, { query = it }, "Search name, mobile, seat")
                    Dropdown("Sort by", sort, listOf("Seat number", "Name", "Valid till"), Modifier.fillMaxWidth()) { sort = it }
                }
            }
            if (shown.isEmpty()) item {
                EmptyState(Icons.Rounded.SearchOff, if (members.isEmpty()) "No members yet" else "Nobody here",
                    if (members.isEmpty()) "Tap “Add member”, or tap a free seat on the Seats page." else "No member matches this search or filter.")
            }
            items(shown, key = { it.id }) { m -> MemberCard(m, rooms, t, { onOpen(m.id) }, combo = m.studentId?.let { "Combo · ${courseOf[it] ?: "course"}" },
                onRenew = if (m.monthlyFee > 0) ({ onRenew(m.id) }) else null) { `in`.vidyadhan.academy.ui.Contact.whatsapp(ctx, m.mobile, libraryReminder(m, t)) } }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, icon: ImageVector, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.softCard(RoundedCornerShape(20.dp), 4.dp).clip(RoundedCornerShape(20.dp))
        .border(if (selected) 2.dp else 0.dp, if (selected) color else Color.Transparent, RoundedCornerShape(20.dp))
        .clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$value", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = color)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun Dropdown(label: String, value: String, options: List<String>, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(14.dp))
            .clickable { open = true }.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Slate400)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.ExpandMore, null, tint = Brand.Slate400)
            }
        }
        DropdownMenu(open, { open = false }, containerColor = Color.White) {
            options.forEach { o -> DropdownMenuItem({ Text(o, fontWeight = if (o == value) FontWeight.Bold else FontWeight.Normal) }, { onPick(o); open = false }) }
        }
    }
}

/** "Seat 12 · Room 1" */
internal fun seatLine(m: LibraryMember, rooms: List<LibraryRoom>) =
    listOf("Seat ${m.seatNo}", roomOf(m.seatNo, rooms)?.name ?: "").filter { it.isNotBlank() }.joinToString(" · ")

@Composable
internal fun MemberCard(m: LibraryMember, rooms: List<LibraryRoom>, t: Long, onClick: () -> Unit, combo: String? = null, onRenew: (() -> Unit)? = null, onRemind: () -> Unit) {
    val st = memberStatus(m, t)
    val edge = when { !m.active -> Color.Transparent; st == SeatStatus.Expired -> Brand.Red500; st == SeatStatus.Ending -> Brand.Saffron500; else -> Color.Transparent }
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 5.dp).clip(RoundedCornerShape(22.dp))
        .pressCard(RoundedCornerShape(22.dp), onClick, normal = if (edge == Color.Transparent) Brand.Line else edge).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.name, 48.dp, photo = m.photo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                combo?.let { SmallTag(it, Color(0xFF4338CA)) }
                Text("+91 ${m.mobile}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            }
            if (!m.active) StatusChip("Left", Tone.Neutral)
            else StatusChip(if (st == SeatStatus.Partly) "Active" else st.label, when (st) { SeatStatus.Expired -> Tone.Bad; SeatStatus.Ending -> Tone.Warn; else -> Tone.Good })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                !m.active -> {}
                st == SeatStatus.Expired -> SmallTag("Fee due", Brand.Red600)
                m.balance > 0 -> SmallTag("${rupees(m.balance)} to pay", Brand.Saffron700)
                else -> SmallTag("✓ Paid", Brand.Green700)
            }
            SmallTag(seatLine(m, rooms), Brand.Blue700)
            if (m.hasLocker) SmallTag(lockerLabel(m), Brand.Violet700)
            Spacer(Modifier.weight(1f))
            // the amount on the button: one tap opens the renewal sheet (with the confirm step)
            if (m.active && st != SeatStatus.Active && onRenew != null) Text("Renew ${rupees(m.monthlyFee)}",
                Modifier.clip(RoundedCornerShape(50)).background(Brand.Green600).clickable(onClick = onRenew).padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, softWrap = false)
            if (m.active && st != SeatStatus.Active) Spacer(Modifier.width(8.dp))
            if (m.active && st != SeatStatus.Active) SmallIconButton(Icons.AutoMirrored.Rounded.Chat, "Send reminder to ${m.name}", Brand.WhatsAppDark, onRemind)
        }
        HorizontalDivider(color = Brand.Line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plan: ${m.planName.ifBlank { "Monthly" }}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Brand.Slate500, maxLines = 1)
            Text(if (m.active) "Valid till ${m.validTill.asDate()}" else "Left ${m.leftOn?.let { shortDate(it) } ?: ""}", style = MaterialTheme.typography.labelMedium,
                color = when { !m.active -> Brand.Slate500; st == SeatStatus.Expired -> Brand.Red600; st == SeatStatus.Ending -> Brand.Saffron700; else -> Brand.Slate600 })
        }
    }
}

// ───────────────────────── plans ─────────────────────────

/** "Subscription plans": the renewal packages, each with an on/off switch. */
@Composable
internal fun PlansPage(vm: LibraryViewModel, onBack: () -> Unit) {
    val plans by vm.plans.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<MemberPlan?>(null) }
    var delete by remember { mutableStateOf<MemberPlan?>(null) }
    HideBottomNav()
    HeroPage("Subscription plans", "Packages members can pay for", onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PrimaryButton("Add plan", { edit = MemberPlan(name = "", months = 3, price = 0) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) }
            item {
                Text("The member's normal monthly fee (seat + locker) is always offered too. Switch a plan off to stop offering it.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (plans.isEmpty()) item { EmptyState(Icons.Rounded.Loyalty, "No plans yet", "Add packages like “3 months” or “6 months with locker”.") }
            items(plans, key = { it.id }) { p ->
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 5.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = if (p.active) Brand.Ink else Brand.Slate400)
                            Text(if (p.withLocker) "Includes a locker" else "Seat only", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        }
                        SmallIconButton(Icons.Rounded.Edit, "Edit ${p.name}", Brand.Blue700) { edit = p }
                        Spacer(Modifier.width(6.dp))
                        SmallIconButton(Icons.Rounded.DeleteOutline, "Delete ${p.name}", Brand.Red600) { delete = p }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rupees(p.price), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Blue700)
                        Spacer(Modifier.width(8.dp))
                        Text(monthsLabel(p.months), Modifier.clip(RoundedCornerShape(50)).border(1.dp, Brand.Slate300, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
                        if (p.months > 1) { Spacer(Modifier.width(8.dp)); Text("${rupees(p.price / p.months)}/month", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400) }
                    }
                    HorizontalDivider(color = Brand.Line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (p.active) "Active" else "Switched off", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = if (p.active) Brand.Green700 else Brand.Slate400)
                        Switch(p.active, { on -> vm.savePlan(p.copy(active = on)) })
                    }
                }
            }
        }
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
                vm.savePlan(p.copy(name = name.trim(), months = months, price = price.toLong(), withLocker = locker)); edit = null
            }) { Text("Save", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ edit = null }) { Text("Cancel") } })
    }
    delete?.let { p ->
        ConfirmDialog("Delete ${p.name}?", "Members who paid for it keep their dates. It is just not offered any more.", "Delete", danger = true,
            onConfirm = { vm.deletePlan(p); delete = null }, onDismiss = { delete = null })
    }
}
