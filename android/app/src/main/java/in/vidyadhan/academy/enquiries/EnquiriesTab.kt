package `in`.vidyadhan.academy.enquiries

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

class EnquiriesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).enquiries()
    val list = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun get(id: Long) = dao.get(id)
    fun save(e: Enquiry) = viewModelScope.launch { if (e.id == 0L) dao.insert(e) else dao.update(e) }
    fun delete(e: Enquiry) = viewModelScope.launch { dao.delete(e) }
}

private sealed interface Screen {
    data object List : Screen
    data class Form(val id: Long) : Screen
    data class Detail(val id: Long) : Screen
}

private fun isOpen(e: Enquiry) = e.status == "NEW" || e.status == "CONTACTED"

@Composable
private fun EnquiryStatus(e: Enquiry, t: Long) = when {
    e.status == "JOINED" -> StatusChip("Joined", Tone.Good)
    e.status == "CLOSED" -> StatusChip("Closed", Tone.Neutral)
    e.followUpOn < t -> StatusChip("Overdue", Tone.Bad)
    e.followUpOn == t -> StatusChip("Today", Tone.Accent)
    e.status == "CONTACTED" -> StatusChip("Contacted", Tone.Info)
    else -> StatusChip("New", Tone.Info)
}

@Composable
fun EnquiriesTab(
    openNew: Boolean = false, openId: Long = 0, openAdmission: Boolean = false, onOpened: () -> Unit = {},
    onAdmitted: (String, Long) -> Unit = { _, _ -> }, vm: EnquiriesViewModel = viewModel(),
) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    var sub by rememberSaveable { mutableStateOf("pipeline") } // pipeline or admission
    var admitFrom by remember { mutableStateOf<Enquiry?>(null) }
    LaunchedEffect(openNew, openId, openAdmission) {
        if (openNew) { screen = Screen.Form(0); onOpened() }
        else if (openId > 0) { screen = Screen.Detail(openId); onOpened() }
        else if (openAdmission) { admitFrom = null; sub = "admission"; screen = Screen.List; onOpened() }
    }
    BackHandler(enabled = screen != Screen.List || sub == "admission") { if (screen != Screen.List) screen = Screen.List else sub = "pipeline" }
    AnimatedContent(screen, transitionSpec = {
        if (targetState == Screen.List) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "enq") { s ->
        when (s) {
            Screen.List -> EnquiryList(vm, sub, { sub = it }, admitFrom, onAdmit = { admitFrom = it; sub = "admission" },
                onAdd = { screen = Screen.Form(0) }, onOpen = { screen = Screen.Detail(it) },
                onAdmitted = { kind, id -> sub = "pipeline"; admitFrom = null; onAdmitted(kind, id) })
            is Screen.Form -> EnquiryForm(vm, s.id) { screen = if (s.id == 0L) Screen.List else Screen.Detail(s.id) }
            is Screen.Detail -> EnquiryDetail(vm, s.id, { screen = Screen.List }, { screen = Screen.Form(s.id) }) { en -> admitFrom = en; sub = "admission"; screen = Screen.List }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun EnquiryList(vm: EnquiriesViewModel, sub: String, setSub: (String) -> Unit, admitFrom: Enquiry?, onAdmit: (Enquiry?) -> Unit,
                        onAdd: () -> Unit, onOpen: (Long) -> Unit, onAdmitted: (String, Long) -> Unit) {
    val ctx = LocalContext.current
    val all by vm.list.collectAsStateWithLifecycle()
    val notes by remember { AppDb.get(ctx).notes().allOf("ENQUIRY") }.collectAsStateWithLifecycle(emptyList())
    val latestNote = notes.groupBy { it.ownerId }.mapValues { (_, l) -> l.first().text }
    val t = today()
    val open = all.filter(::isOpen)
    var leadFilter by rememberSaveable { mutableStateOf("all") }
    var snooze by remember { mutableStateOf<Enquiry?>(null) }
    var quickNote by remember { mutableStateOf<Enquiry?>(null) }
    snooze?.let { e ->
        ModalBottomSheet({ snooze = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetTitle("Follow-up for ${e.name}", "Pick the day to call again")
                listOf("Today" to 0L, "Tomorrow" to 1L, "In 3 days" to 3L).forEach { (label, days) ->
                    SoftButton(label, { vm.save(e.copy(followUpOn = t + days)); snooze = null }, Modifier.fillMaxWidth(), color = Brand.Blue700)
                }
            }
        }
    }
    quickNote?.let { e ->
        ModalBottomSheet({ quickNote = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetTitle("Quick remark", e.name)
                listOf("Needs morning batch", "Requested fee discount", "Demo attended", "Parent will visit", "Needs Abhyasika seat").forEach { chip ->
                    SoftButton(chip, { vm.save(e.copy(details = listOf(e.details, chip).filter { it.isNotBlank() }.joinToString(" · "))); quickNote = null }, Modifier.fillMaxWidth(), color = Brand.Slate600)
                }
            }
        }
    }
    HeroPage("Enquiries & Admissions", "Convert walk-ins into Academy, Abhyasika or Combo", header = {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(3.dp)) {
            listOf("pipeline" to "Enquiries (${open.size})", "admission" to "+ New Admission").forEach { (k, l) ->
                Text(l, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (sub == k) Color.White else Color.Transparent).clickable { if (k == "admission") onAdmit(null) else setSub(k) }
                    .padding(vertical = 7.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (sub == k) Brand.Blue700 else Brand.Blue100, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }) {
        if (sub == "admission") Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
            if (admitFrom != null) Text("From enquiry: ${admitFrom.name}", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.labelMedium, color = Brand.Saffron600)
            AdmissionForm(admitFrom, onAdmitted)
        } else LazyColumn(contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active leads (${open.size})", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Brand.Slate600)
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Brand.Blue700).clickable(onClick = onAdd).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
                        Text("Add Enquiry", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
            // live filter counts: one tap narrows the list to what needs doing today
            item {
                val dueToday = open.count { it.followUpOn <= t }
                val overdue = open.count { it.followUpOn < t }
                val newOnes = open.count { it.status == "NEW" }
                val called = open.count { it.status == "CONTACTED" }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "All ${open.size}", "today" to "Due today $dueToday", "overdue" to "Overdue $overdue", "new" to "New $newOnes", "called" to "Contacted $called").forEach { (k, l) ->
                        val on = leadFilter == k
                        Text(l, Modifier.clip(RoundedCornerShape(50)).background(if (on) Brand.Blue700 else Color.White).border(1.dp, if (on) Brand.Blue700 else Brand.Line, RoundedCornerShape(50))
                            .clickable { leadFilter = k }.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Brand.Slate600, maxLines = 1)
                    }
                }
            }
            val shownLeads = open.filter {
                when (leadFilter) { "today" -> it.followUpOn <= t; "overdue" -> it.followUpOn < t; "new" -> it.status == "NEW"; "called" -> it.status == "CONTACTED"; else -> true }
            }
            if (open.isEmpty()) item { EmptyState(Icons.Rounded.Forum, "No active leads", "Note every call or walk-in with “Add enquiry”. The app reminds you to follow up.") }
            else if (shownLeads.isEmpty()) item { Text("Nothing in this filter", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500) }
            items(shownLeads.sortedBy { it.followUpOn }, key = { it.id }) { e ->
                val (label, fg, bg) = when {
                    e.followUpOn <= t -> Triple("Follow-up Today", Brand.Amber700, Brand.Amber50)
                    e.status == "CONTACTED" -> Triple("Visited", Brand.Amber700, Brand.Amber50)
                    else -> Triple("Hot", Color(0xFFBE123C), Color(0xFFFFF1F2))
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).pressCard(RoundedCornerShape(18.dp), { onOpen(e.id) }).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name.split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Ink)
                            if (e.course.isNotBlank()) Text(e.course, style = MaterialTheme.typography.labelMedium, color = Brand.Blue700)
                        }
                        // one status line: the state and where the lead came from
                        Text(if (e.source.isNotBlank()) "$label · ${e.source}" else label, Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = fg)
                    }
                    // tap the note to add a quick remark (chips) instead of typing
                    Text("“${latestNote[e.id] ?: e.details.ifBlank { "Add a quick note" }}”", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Slate50).border(1.dp, Brand.Line, RoundedCornerShape(12.dp))
                        .clickable { quickNote = e }.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
                    Row {
                        Spacer(Modifier.weight(1f))
                        Text("Follow-up: ", style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
                        Text(if (e.followUpOn <= t) "Today" else relativeDay(e.followUpOn), Modifier.clip(RoundedCornerShape(6.dp)).clickable { snooze = e }.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Brand.Saffron500)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Blue100).clickable { Contact.call(ctx, e.mobile) }, Alignment.Center) {
                            Icon(Icons.Rounded.Phone, "Call", tint = Brand.Blue700, modifier = Modifier.size(18.dp))
                        }
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Green50).clickable { Contact.whatsapp(ctx, e.mobile) }, Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", tint = Brand.WhatsAppDark, modifier = Modifier.size(18.dp))
                        }
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0F9FF)).border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp))
                            .clickable { `in`.vidyadhan.academy.telegram.TelegramBot.sendOrExplain(ctx, "e${e.id}", e.mobile, `in`.vidyadhan.academy.telegram.TelegramBot.esc(`in`.vidyadhan.academy.Templates.fill("enquiry", false, mapOf("name" to e.name)))) }.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color(0xFF0369A1), modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
                            Text("Telegram info", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0369A1))
                        }
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Brand.Blue700).clickable { onAdmit(e) }.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("Admit student", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White); Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnquiryForm(vm: EnquiriesViewModel, id: Long, onDone: () -> Unit) {
    val existing by remember(id) { vm.get(id) }.collectAsStateWithLifecycle(null)
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var telegram by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var follow by remember { mutableLongStateOf(today() + AppSettings.enquiryDays) }
    LaunchedEffect(existing) {
        val e = existing
        if (!loaded && e != null) { name = e.name; mobile = e.mobile; telegram = e.telegram; course = e.course; details = e.details; source = e.source; follow = e.followUpOn; loaded = true }
    }
    HideBottomNav()
    HeroPage(if (id == 0L) "New enquiry" else "Edit enquiry", "Who called or walked in?", onBack = onDone) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            FormCard("Contact", Icons.Rounded.Person, Brand.Saffron600) {
                Field(name, { name = it }, "Name", Icons.Rounded.Person)
                PhoneField(mobile, { mobile = it }, onName = { if (name.isBlank()) name = it },
                    supporting = if (mobile.isNotEmpty() && mobile.length < 10) "Enter all 10 digits" else null, isError = mobile.isNotEmpty() && mobile.length < 10)
            }
            FormCard("Interested in", Icons.Rounded.School) {
                ChoicePills(AppSettings.courseList, course, { course = if (course == it) "" else it })
            }
            FormCard("How did they hear about us?", Icons.Rounded.Campaign, Brand.Blue600) {
                ChoicePills(AppSettings.enquirySources, source, { source = if (source == it) "" else it })
            }
            FormCard("Notes", Icons.AutoMirrored.Rounded.Notes, Brand.Violet700) {
                Field(details, { details = it }, "What did they ask? Fees, timing, batch…", singleLine = false, minLines = 3)
            }
            FormCard("Remind me", Icons.Rounded.Alarm, Brand.Teal700) {
                val t = today()
                ChoicePills(listOf("Tomorrow", "In 2 days", "In 3 days", "Next week"),
                    when (follow - t) { 1L -> "Tomorrow"; 2L -> "In 2 days"; 3L -> "In 3 days"; 7L -> "Next week"; else -> "" },
                    { follow = t + when (it) { "Tomorrow" -> 1; "In 2 days" -> 2; "In 3 days" -> 3; else -> 7 } })
                DateField("Follow-up date", follow, { follow = it })
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomBar {
            SoftButton("Cancel", onDone, Modifier.weight(1f), color = Brand.Slate600)
            PrimaryButton("Save enquiry", {
                val old = existing
                vm.save(Enquiry(if (id == 0L) 0 else id, name.trim(), mobile, course, details.trim(), old?.createdOn ?: today(), follow, old?.status ?: "NEW", source, telegram)); onDone()
            }, Modifier.weight(1.6f), enabled = name.isNotBlank() && mobile.length == 10, accent = true, icon = Icons.Rounded.Check)
        }
    }
}

@Composable
private fun EnquiryDetail(vm: EnquiriesViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit, onAdmit: (Enquiry) -> Unit) {
    val ctx = LocalContext.current
    val e by remember(id) { vm.get(id) }.collectAsStateWithLifecycle(null)
    var confirm by remember { mutableStateOf(false) }
    val en = e ?: return
    val t = today()
    val days = AppSettings.enquiryDays
    HideBottomNav()
    val msg = `in`.vidyadhan.academy.Templates.fill("enquiry", true, mapOf("name" to en.name))
    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        ProfileHero(en.name, onBack,
            chips = { if (en.course.isNotBlank()) GlassChip(en.course, Icons.Rounded.School); GlassChip("Asked ${whenText(en.createdOn)}", Icons.Rounded.Schedule) },
            topActions = { GlassIconButton(Icons.Rounded.Edit, "Edit", onEdit); GlassIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirm = true }) },
            actions = {
                RoundAction(Icons.Rounded.Call, "Call", Brand.Blue600, { Contact.call(ctx, en.mobile) }, glass = true)
                RoundAction(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", Brand.WhatsApp, { Contact.whatsapp(ctx, en.mobile, msg) }, glass = true)
                RoundAction(Icons.Rounded.Sms, "SMS", Brand.Blue600, { Contact.sms(ctx, en.mobile, msg) }, glass = true)
            })
        Sheet {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(4.dp))
                // status / next step
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Alarm, if (isOpen(en) && en.followUpOn < t) Brand.Red600 else Brand.Saffron600, 44.dp, filled = true)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isOpen(en)) "Next follow-up" else "Status", style = MaterialTheme.typography.labelMedium, color = Brand.Slate400)
                            Text(if (isOpen(en)) "${relativeDay(en.followUpOn)} · ${en.followUpOn.asDate()}" else if (en.status == "JOINED") "Joined the academy 🎉" else "Not interested",
                                style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                        }
                        EnquiryStatus(en, t)
                    }
                    if (isOpen(en)) {
                        PrimaryButton("Called · remind me in $days days", { vm.save(en.copy(status = "CONTACTED", followUpOn = t + days)) },
                            Modifier.fillMaxWidth(), accent = true, icon = Icons.Rounded.PhoneCallback)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SoftButton("Admit", { onAdmit(en) }, Modifier.weight(1f), color = Brand.Green700, icon = Icons.Rounded.HowToReg)
                            SoftButton("Not interested", { vm.save(en.copy(status = "CLOSED")) }, Modifier.weight(1f), color = Brand.Slate600, icon = Icons.Rounded.DoNotDisturbOn)
                        }
                    } else {
                        if (en.status == "JOINED") PrimaryButton("Admit again", { onAdmit(en) }, Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.PersonAdd, custom = Brush.linearGradient(listOf(Brand.Green500, Brand.Green700)))
                        SoftButton("Reopen enquiry", { vm.save(en.copy(status = "CONTACTED", followUpOn = t + days)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Replay)
                    }
                }
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow(Icons.Rounded.Phone, "Mobile", "+91 ${en.mobile}")
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.School, "Interested in", en.course.ifBlank { "Not noted" })
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.Campaign, "Came from", en.source.ifBlank { "Not noted" })
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.EventNote, "Enquired on", en.createdOn.asDate())
                }
                if (en.details.isNotBlank()) {
                    SectionHeader("What they asked")
                    Text(en.details, Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(18.dp), style = MaterialTheme.typography.bodyLarge, color = Brand.Slate800)
                }
                NotesSection("ENQUIRY", en.id)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if (confirm) ConfirmDialog("Delete this enquiry?", "${en.name}'s enquiry will be removed.", "Delete", danger = true,
        onConfirm = { vm.delete(en); confirm = false; onBack() }, onDismiss = { confirm = false })
}
