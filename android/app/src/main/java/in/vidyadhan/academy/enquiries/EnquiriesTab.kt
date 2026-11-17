package `in`.vidyadhan.academy.enquiries

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
fun EnquiriesTab(openNew: Boolean = false, onOpened: () -> Unit = {}, onConvert: (Prefill) -> Unit = {}, vm: EnquiriesViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    LaunchedEffect(openNew) { if (openNew) { screen = Screen.Form(0); onOpened() } }
    BackHandler(enabled = screen != Screen.List) { screen = Screen.List }
    AnimatedContent(screen, transitionSpec = {
        if (targetState == Screen.List) (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
        else (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
    }, label = "enq") { s ->
        when (s) {
            Screen.List -> EnquiryList(vm, { screen = Screen.Form(0) }, { screen = Screen.Detail(it) })
            is Screen.Form -> EnquiryForm(vm, s.id) { screen = if (s.id == 0L) Screen.List else Screen.Detail(s.id) }
            is Screen.Detail -> EnquiryDetail(vm, s.id, { screen = Screen.List }, { screen = Screen.Form(s.id) }, onConvert)
        }
    }
}

@Composable
private fun EnquiryList(vm: EnquiriesViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val all by vm.list.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("today") }
    var q by rememberSaveable { mutableStateOf("") }
    val t = today()
    val open = all.filter(::isOpen)
    val due = open.filter { it.followUpOn <= t }
    val joinedCount = all.count { it.status == "JOINED" }
    val shown = when (filter) { "today" -> due; "open" -> open; else -> all }
        .filter { q.isBlank() || it.name.contains(q, true) || it.mobile.contains(q) }
    HeroPage("Enquiries", "${open.size} open · ${due.size} to follow up today", header = {
        Spacer(Modifier.height(16.dp))
        SearchBox(q, { q = it }, "Search enquiries")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassStat("Follow up", "${due.size}", Modifier.weight(1f), valueColor = Brand.Saffron200)
            GlassStat("Open", "${open.size}", Modifier.weight(1f))
            GlassStat("Joined", "$joinedCount", Modifier.weight(1f))
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Column {
                Segmented(listOf("today" to "Today (${due.size})", "open" to "Open", "all" to "All"), filter, { filter = it },
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp))
                if (shown.isEmpty()) EmptyState(Icons.Rounded.Forum,
                    if (all.isEmpty()) "No enquiries yet" else if (filter == "today") "Nothing to follow up today" else "No matches",
                    if (all.isEmpty()) "Note every call or walk-in. The app reminds you to follow up." else "You're all caught up. 🎉",
                    action = if (all.isEmpty()) "New enquiry" else null, onAction = onAdd)
                else LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.id }) { e ->
                        ListCard(onClick = { onOpen(e.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(e.name)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Brand.Ink, maxLines = 1)
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (e.course.isNotBlank()) { SmallTag(e.course, Brand.Saffron700); Spacer(Modifier.width(8.dp)) }
                                        Text(e.mobile, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                                    }
                                }
                                EnquiryStatus(e, t)
                            }
                            if (e.details.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text("“${e.details}”", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate600, maxLines = 2)
                            }
                            if (isOpen(e)) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Alarm, null, tint = if (e.followUpOn < t) Brand.Red600 else Brand.Slate400, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Follow up ${whenText(e.followUpOn)}",
                                        style = MaterialTheme.typography.labelMedium, color = if (e.followUpOn < t) Brand.Red600 else Brand.Slate500)
                                }
                            }
                        }
                    }
                }
            }
            if (all.isNotEmpty()) Fab("New enquiry", Icons.Rounded.AddComment, onAdd, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
}

@Composable
private fun EnquiryForm(vm: EnquiriesViewModel, id: Long, onDone: () -> Unit) {
    val existing by remember(id) { vm.get(id) }.collectAsStateWithLifecycle(null)
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var follow by remember { mutableLongStateOf(today() + AppSettings.enquiryDays) }
    LaunchedEffect(existing) {
        val e = existing
        if (!loaded && e != null) { name = e.name; mobile = e.mobile; course = e.course; details = e.details; follow = e.followUpOn; loaded = true }
    }
    HideBottomNav()
    HeroPage(if (id == 0L) "New enquiry" else "Edit enquiry", "Who called or walked in?", onBack = onDone) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            FormCard("Contact", Icons.Rounded.Person, Brand.Saffron600) {
                Field(name, { name = it }, "Name", Icons.Rounded.Person)
                DigitsField("Mobile number", mobile, 10, { mobile = it }, Icons.Rounded.Phone, prefix = "+91 ", phone = true,
                    supporting = if (mobile.isNotEmpty() && mobile.length < 10) "Enter all 10 digits" else null, isError = mobile.isNotEmpty() && mobile.length < 10)
            }
            FormCard("Interested in", Icons.Rounded.School) {
                ChoicePills(AppSettings.courseList, course, { course = if (course == it) "" else it })
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
                vm.save(Enquiry(if (id == 0L) 0 else id, name.trim(), mobile, course, details.trim(), old?.createdOn ?: today(), follow, old?.status ?: "NEW")); onDone()
            }, Modifier.weight(1.6f), enabled = name.isNotBlank() && mobile.length == 10, accent = true, icon = Icons.Rounded.Check)
        }
    }
}

@Composable
private fun EnquiryDetail(vm: EnquiriesViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit, onConvert: (Prefill) -> Unit) {
    val ctx = LocalContext.current
    val e by remember(id) { vm.get(id) }.collectAsStateWithLifecycle(null)
    var confirm by remember { mutableStateOf(false) }
    val en = e ?: return
    val t = today()
    val days = AppSettings.enquiryDays
    HideBottomNav()
    val msg = "नमस्कार ${en.name}, विद्याधन अकॅडमी मध्ये चौकशी केल्याबद्दल धन्यवाद." +
        (if (AppSettings.academyPhone.isNotBlank()) " अधिक माहितीसाठी संपर्क: ${AppSettings.academyPhone}" else "")
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
                            SoftButton("Joined", { vm.save(en.copy(status = "JOINED")) }, Modifier.weight(1f), color = Brand.Green700, icon = Icons.Rounded.HowToReg)
                            SoftButton("Not interested", { vm.save(en.copy(status = "CLOSED")) }, Modifier.weight(1f), color = Brand.Slate600, icon = Icons.Rounded.DoNotDisturbOn)
                        }
                    } else {
                        if (en.status == "JOINED") PrimaryButton("Add as student", { onConvert(Prefill(en.name, en.mobile, en.course)) }, Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.PersonAdd, custom = Brush.linearGradient(listOf(Brand.Green500, Brand.Green700)))
                        SoftButton("Reopen enquiry", { vm.save(en.copy(status = "CONTACTED", followUpOn = t + days)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Replay)
                    }
                }
                Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow(Icons.Rounded.Phone, "Mobile", "+91 ${en.mobile}")
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.School, "Interested in", en.course.ifBlank { "Not noted" })
                    HorizontalDivider(color = Brand.Line)
                    InfoRow(Icons.Rounded.EventNote, "Enquired on", en.createdOn.asDate())
                }
                if (en.details.isNotBlank()) {
                    SectionHeader("Notes")
                    Text(en.details, Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(18.dp), style = MaterialTheme.typography.bodyLarge, color = Brand.Slate800)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if (confirm) ConfirmDialog("Delete this enquiry?", "${en.name}'s enquiry will be removed.", "Delete", danger = true,
        onConfirm = { vm.delete(en); confirm = false; onBack() }, onDismiss = { confirm = false })
}
