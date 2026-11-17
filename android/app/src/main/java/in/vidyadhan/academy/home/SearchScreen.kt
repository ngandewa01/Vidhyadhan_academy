package `in`.vidyadhan.academy.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.library.lockerLabel
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*

private data class Hit(val section: String, val name: String, val detail: String, val photo: String, val icon: ImageVector, val go: Go, val id: Long, val chip: String?, val tone: Tone)

/** One search box for students, library members (current and past) and enquiries: by name, mobile, seat, locker or batch. */
@Composable
fun SearchScreen(onClose: () -> Unit, onGo: (Go, Long) -> Unit) {
    val db = AppDb.get(LocalContext.current)
    val students by remember { studentsWithFees(db.students()) }.collectAsStateWithLifecycle(emptyList())
    val members by db.library().members().collectAsStateWithLifecycle(emptyList())
    val enquiries by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onClose() }
    HideBottomNav()
    val term = q.trim()
    val digits = term.filter(Char::isDigit)
    fun nameOrPhone(name: String, mobile: String) = name.contains(term, true) || (digits.length >= 3 && mobile.contains(digits))
    val hits = if (term.isEmpty()) emptyList() else buildList {
        students.filter { nameOrPhone(it.name, it.mobile) || it.batch.equals(term, true) }.forEach {
            val left = it.totalFee - it.paid
            add(Hit("Students", it.name, listOf(it.course, it.batch, it.mobile).filter(String::isNotBlank).joinToString(" · "), it.photo, Icons.Rounded.School, Go.Student, it.id,
                if (!it.current) "Former" else if (left > 0) "${rupees(left)} due" else "Paid", if (!it.current) Tone.Neutral else if (left > 0) Tone.Warn else Tone.Good))
        }
        members.filter { nameOrPhone(it.name, it.mobile) || (digits.isNotEmpty() && digits == term && it.seatNo.toString() == digits) || (it.hasLocker && it.lockerNo.equals(term, true)) }.forEach {
            add(Hit("Library", it.name, listOf("Seat ${it.seatNo}", lockerLabel(it), it.mobile).filter(String::isNotBlank).joinToString(" · "), it.photo, Icons.Rounded.EventSeat, Go.Member, it.id,
                if (!it.active) "Left" else if (it.validTill < today()) "Fee due" else null, if (!it.active) Tone.Neutral else Tone.Bad))
        }
        enquiries.filter { nameOrPhone(it.name, it.mobile) }.forEach {
            add(Hit("Enquiries", it.name, listOf(it.course, it.mobile).filter(String::isNotBlank).joinToString(" · "), "", Icons.Rounded.Forum, Go.Enquiry, it.id,
                when (it.status) { "JOINED" -> "Joined"; "CLOSED" -> "Closed"; else -> "Open" }, if (it.status == "JOINED") Tone.Good else Tone.Info))
        }
    }
    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        Row(Modifier.fillMaxWidth().heroBackground().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClose)
            Spacer(Modifier.width(10.dp))
            SearchBox(q, { q = it }, "Name, mobile, seat or locker no.", Modifier.weight(1f).focusRequester(focus))
        }
        if (term.isEmpty()) EmptyState(Icons.Rounded.Search, "Search everything", "Students, library members and enquiries.\nTry a name, the last digits of a mobile, a seat number or a batch.")
        else if (hits.isEmpty()) EmptyState(Icons.Rounded.SearchOff, "Nothing found", "No student, member or enquiry matches “$term”.")
        else LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            hits.groupBy { it.section }.forEach { (section, list) ->
                item(key = "h$section") { Text("$section · ${list.size}", Modifier.padding(start = 4.dp, top = 6.dp), style = MaterialTheme.typography.labelLarge, color = Brand.Slate500) }
                items(list, key = { "${it.go}${it.id}" }) { h ->
                    ListCard(onClick = { onClose(); onGo(h.go, h.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (h.photo.isNotBlank() || h.go != Go.Enquiry) Avatar(h.name, 44.dp, photo = h.photo) else IconTile(h.icon, Brand.Saffron600, 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.name, style = MaterialTheme.typography.titleSmall, color = Brand.Ink, maxLines = 1)
                                Text(h.detail, style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
                            }
                            h.chip?.let { StatusChip(it, h.tone) }
                        }
                    }
                }
            }
        }
    }
}
