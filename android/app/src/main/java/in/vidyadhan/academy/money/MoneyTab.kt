package `in`.vidyadhan.academy.money

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.*
import `in`.vidyadhan.academy.students.*
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Money for one month. Deposits are not income (they are returned), so they are shown separately. */
data class MonthMoney(
    val month: YearMonth, val studentFees: Long, val libraryFees: Long, val registration: Long,
    val depositsIn: Long, val depositsOut: Long, val expenses: Long, val byCategory: List<Pair<String, Long>>,
    /** Payments recorded in the app against the old khata (not the imported book). */
    val khataPayments: Long = 0,
) {
    val collected get() = studentFees + libraryFees + registration + khataPayments
    val profit get() = collected - expenses
}

private fun YearMonth.range() = atDay(1).toEpochDay()..atEndOfMonth().toEpochDay()

fun monthMoney(m: YearMonth, sp: List<StudentPayment>, lp: List<LibraryPayment>, ex: List<Expense>, kh: List<KhataEntry> = emptyList()): MonthMoney {
    val r = m.range()
    val l = lp.filter { it.paidOn in r }
    val e = ex.filter { it.spentOn in r }
    return MonthMoney(m, sp.filter { it.paidOn in r }.sumOf { it.amount }, l.filter { it.kind == "FEE" }.sumOf { it.amount },
        l.filter { it.kind == "REGISTRATION" }.sumOf { it.amount }, l.filter { it.kind == "DEPOSIT" }.sumOf { it.amount },
        l.filter { it.kind == "DEPOSIT_RETURN" }.sumOf { it.amount }, e.sumOf { it.amount },
        e.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second },
        kh.filter { it.kind == "PAYMENT" && it.date in r }.sumOf { it.credit })
}

fun categoryIcon(c: String): Pair<ImageVector, Color> = when (c) {
    "Rent" -> Icons.Rounded.Apartment to Brand.Blue600
    "Electricity" -> Icons.Rounded.Bolt to Brand.Amber600
    "Salary" -> Icons.Rounded.Badge to Brand.Violet700
    "Internet" -> Icons.Rounded.Wifi to Brand.Teal700
    "Books & material" -> Icons.Rounded.MenuBook to Brand.Saffron600
    "Advertising" -> Icons.Rounded.Campaign to Color(0xFFE11D48)
    "Maintenance" -> Icons.Rounded.Build to Brand.Slate600
    else -> Icons.Rounded.Category to Brand.Slate500
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyTab(openNew: Boolean = false, onOpened: () -> Unit = {}) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    val sp by db.students().allPayments().collectAsStateWithLifecycle(emptyList())
    val lp by db.library().allPayments().collectAsStateWithLifecycle(emptyList())
    val ex by db.expenses().all().collectAsStateWithLifecycle(emptyList())
    val enq by db.enquiries().all().collectAsStateWithLifecycle(emptyList())
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var view by rememberSaveable { mutableStateOf("overview") }
    var edit by remember { mutableStateOf<Expense?>(null) }
    LaunchedEffect(openNew) { if (openNew) { view = "expenses"; edit = Expense(category = "", amount = 0, spentOn = today()); onOpened() } }
    val kh by db.khata().payments().collectAsStateWithLifecycle(emptyList())
    var period by rememberSaveable { mutableStateOf("month") }
    val cur = monthMoney(month, sp, lp, ex, kh)
    // the months in the chosen period: one month, the months of a year, or every month since the first record
    val periodMonths: List<YearMonth> = when (period) {
        "year" -> (1..12).map { YearMonth.of(month.year, it) }.filter { it <= YearMonth.now() }
        "all" -> {
            val first = listOfNotNull(sp.minOfOrNull { it.paidOn }, lp.minOfOrNull { it.paidOn }, ex.minOfOrNull { it.spentOn }, kh.minOfOrNull { it.date }).minOrNull()
            val start = first?.let { YearMonth.from(java.time.LocalDate.ofEpochDay(it)) } ?: YearMonth.now()
            generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= YearMonth.now() }.toList()
        }
        else -> listOf(month)
    }
    val pm = periodMonths.map { monthMoney(it, sp, lp, ex, kh) }
    val pCollected = pm.sumOf { it.collected }
    val pSpent = pm.sumOf { it.expenses }
    val pProfit = pCollected - pSpent
    val last6 = (5 downTo 0).map { monthMoney(month.minusMonths(it.toLong()), sp, lp, ex, kh) }
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    val reportScope = rememberCoroutineScope()
    HeroPage("Reports", "Income, expenses, profit and monthly PDF", header = {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.11f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
            verticalAlignment = Alignment.CenterVertically) {
            if (period == "all") Text("All time", Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
            else {
                IconButton({ monthText = (if (period == "year") month.minusYears(1) else month.minusMonths(1)).toString() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous", tint = Color.White) }
                Text(if (period == "year") "${month.year}" else monthLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
                val canNext = if (period == "year") month.year < YearMonth.now().year else month < YearMonth.now()
                IconButton({ if (canNext) monthText = (if (period == "year") month.plusYears(1) else month.plusMonths(1)).toString() }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next", tint = if (canNext) Color.White else Color.White.copy(alpha = 0.3f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassStat(if (period == "all") "Collected*" else "Collected", rupees(pCollected), Modifier.weight(1f))
            GlassStat("Spent", rupees(pSpent), Modifier.weight(1f), valueColor = Brand.Saffron200)
            GlassStat(if (pProfit >= 0) "Profit" else "Loss", rupees(kotlin.math.abs(pProfit)), Modifier.weight(1f), valueColor = if (pProfit >= 0) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Column {
                Segmented(listOf("month" to "Month", "year" to "Year", "all" to "All time"), period, { period = it; view = "overview" },
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp))
                if (period == "month") Segmented(listOf("overview" to "Report", "expenses" to "Expenses (${ex.count { it.spentOn in month.range() }})"), view, { view = it },
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp))
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (period != "month") {
                        // year: the 12 months; all time: one bar per year. Tap a bar to open it.
                        val now = YearMonth.now()
                        val bars = if (period == "year") (1..12).map { mo ->
                            val ym = YearMonth.of(month.year, mo)
                            val mm = if (ym <= now) monthMoney(ym, sp, lp, ex, kh) else null
                            BarPoint(ym.format(DateTimeFormatter.ofPattern("MMM")).take(1), mm?.collected ?: 0L, mm?.expenses ?: 0L)
                        } else pm.groupBy { it.month.year }.toSortedMap().map { (y, l) -> BarPoint("$y", l.sumOf { it.collected }, l.sumOf { it.expenses }) }
                        Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp)) {
                            Text(if (period == "year") "${month.year} · month by month" else "Year by year", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                            Row(Modifier.padding(top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Dot(Brand.Blue600, "Collected"); Dot(Brand.Saffron500, "Spent")
                            }
                            BarChart(bars, -1) { i ->
                                if (period == "year") {
                                    val ym = YearMonth.of(month.year, i + 1)
                                    if (ym <= now) { monthText = ym.toString(); period = "month" }
                                } else {
                                    val y = bars[i].label.toInt()
                                    monthText = (if (y == now.year) now else YearMonth.of(y, 12)).toString(); period = "year"
                                }
                            }
                            Text(if (period == "year") "Tap a month to open its report." else "Tap a year to open it.", Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        }
                        PeriodCard(period, pm) { reportScope.launch { runCatching { sharePeriodReport(ctx, period, month.year) } } }
                    }
                    else if (view == "overview") {
                        Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp)) {
                            Text("Last 6 months", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                            Row(Modifier.padding(top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Dot(Brand.Blue600, "Collected"); Dot(Brand.Saffron500, "Spent")
                            }
                            BarChart(last6.map { BarPoint(it.month.format(DateTimeFormatter.ofPattern("MMM")), it.collected, it.expenses) },
                                last6.indexOfFirst { it.month == month }) { monthText = last6[it].month.toString() }
                            Text("Tap a month to open its report.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                        }
                        Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(18.dp)) {
                            Text("Money in · $monthLabel", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                            Spacer(Modifier.height(8.dp))
                            LegendLine(Brand.Blue600, "Course fees", rupees(cur.studentFees))
                            LegendLine(Brand.Teal500, "Library fees", rupees(cur.libraryFees))
                            LegendLine(Brand.Violet500, "Registration", rupees(cur.registration))
                            LegendLine(Brand.Saffron500, "Old khata payments", rupees(cur.khataPayments))
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Brand.Line)
                            LegendLine(Brand.Green500, "Total collected", rupees(cur.collected))
                            if (cur.depositsIn + cur.depositsOut > 0) Text("Deposits are kept aside, not counted as income: ${rupees(cur.depositsIn)} received, ${rupees(cur.depositsOut)} returned.",
                                style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, modifier = Modifier.padding(top = 6.dp))
                        }
                        Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Money out · ${rupees(cur.expenses)}", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                            if (cur.byCategory.isEmpty()) Text("No expenses noted this month.", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                            cur.byCategory.forEach { (c, a) ->
                                val (ic, col) = categoryIcon(c)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(ic, col, 36.dp); Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row { Text(c, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Ink); Text(rupees(a), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)) }
                                        Spacer(Modifier.height(4.dp))
                                        ProgressBar(a.toFloat() / cur.expenses, color = col, track = Brand.Line, height = 5.dp)
                                    }
                                }
                            }
                        }
                        val mEnq = enq.filter { it.createdOn in month.range() }
                        Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Enquiries · ${mEnq.size} · ${mEnq.count { it.status == "JOINED" }} joined", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                            if (mEnq.isEmpty()) Text("No enquiries this month.", style = MaterialTheme.typography.bodyMedium, color = Brand.Slate500)
                            mEnq.groupBy { it.source.ifBlank { "Not noted" } }.toList().sortedByDescending { it.second.size }.forEach { (src, list) ->
                                Column {
                                    Row { Text(src, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                                        Text("${list.size} · ${list.count { it.status == "JOINED" }} joined", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500) }
                                    Spacer(Modifier.height(4.dp))
                                    ProgressBar(list.size.toFloat() / mEnq.size, color = Brand.Saffron500, track = Brand.Line, height = 5.dp)
                                }
                            }
                        }
                        PrimaryButton("Share $monthLabel report (PDF)", { reportScope.launch { runCatching { shareMonthlyReport(ctx, month) } } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PictureAsPdf)
                    } else {
                        val list = ex.filter { it.spentOn in month.range() }
                        if (list.isEmpty()) EmptyState(Icons.Rounded.ReceiptLong, "No expenses in $monthLabel", "Note rent, electricity, salaries and more to see your profit.")
                        else Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
                            list.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = Brand.Line)
                                val (ic, col) = categoryIcon(e.category)
                                Row(Modifier.fillMaxWidth().clickable { edit = e }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(ic, col, 42.dp); Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(e.category, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                                        Text(e.spentOn.asDate() + if (e.note.isNotBlank()) " · ${e.note}" else "", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500, maxLines = 1)
                                    }
                                    Text("− " + rupees(e.amount), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Brand.Red600)
                                }
                            }
                        }
                    }
                }
            }
            if (view == "expenses") Fab("Add expense", Icons.Rounded.Add, { edit = Expense(category = "", amount = 0, spentOn = if (month == YearMonth.now()) today() else month.atDay(1).toEpochDay()) },
                Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
    edit?.let { e ->
        var cat by remember { mutableStateOf(e.category) }
        var amount by remember { mutableStateOf(if (e.amount > 0) e.amount.toString() else "") }
        var date by remember { mutableLongStateOf(e.spentOn) }
        var note by remember { mutableStateOf(e.note) }
        ModalBottomSheet({ edit = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTitle(if (e.id == 0L) "Add an expense" else "Edit expense")
                DigitsField("Amount", amount, 7, { amount = it }, prefix = "₹ ")
                Text("What for?", style = MaterialTheme.typography.labelLarge, color = Brand.Slate500)
                ChoicePills(AppSettings.expenseCategories, cat, { cat = it })
                DateField("Date", date, { date = it })
                Field(note, { note = it }, "Note (optional)", Icons.Rounded.EditNote)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (e.id != 0L) SoftButton("Delete", { scope.launch { db.expenses().delete(e) }; edit = null }, Modifier.weight(1f), color = Brand.Red600, icon = Icons.Rounded.DeleteOutline)
                    PrimaryButton("Save", {
                        val n = e.copy(category = cat, amount = amount.toLong(), spentOn = date, note = note.trim())
                        scope.launch { if (e.id == 0L) db.expenses().insert(n) else db.expenses().update(n) }; edit = null
                    }, Modifier.weight(1.6f), enabled = cat.isNotBlank() && (amount.toLongOrNull() ?: 0) > 0, icon = Icons.Rounded.Check)
                }
            }
        }
    }
}

private data class PeriodLine(val label: String, val collected: Long, val spent: Long)

/** Year: each month of the year. All time: each year. Shows money in, money out and profit. */
@Composable
private fun PeriodCard(period: String, pm: List<MonthMoney>, onShare: () -> Unit) {
    val lines = if (period == "year") pm.map { PeriodLine(it.month.format(DateTimeFormatter.ofPattern("MMMM")), it.collected, it.expenses) }
        else pm.groupBy { it.month.year }.toSortedMap().map { (y, l) -> PeriodLine("$y", l.sumOf { it.collected }, l.sumOf { it.expenses }) }
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(24.dp), 8.dp).padding(18.dp)) {
        Text(if (period == "year") "Month by month" else "Year by year", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
        lines.reversed().forEachIndexed { i, ln ->
            if (i > 0) HorizontalDivider(color = Brand.Line)
            val p = ln.collected - ln.spent
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ln.label, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                    Text("In ${rupees(ln.collected)} · Out ${rupees(ln.spent)}", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                }
                Text(rupees(p), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (p >= 0) Brand.Green700 else Brand.Red600)
            }
        }
        if (period == "all") Text("* Includes every payment received, in the month it was paid.", Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        Spacer(Modifier.height(8.dp))
        PrimaryButton(if (period == "year") "Share ${pm.firstOrNull()?.month?.year ?: ""} report (PDF)" else "Share all-time report (PDF)", onShare,
            Modifier.fillMaxWidth(), icon = Icons.Rounded.PictureAsPdf)
    }
}

@Composable
private fun Dot(c: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp))
    Text(t, style = MaterialTheme.typography.labelMedium, color = Brand.Slate600)
}

@Composable
private fun BarChart(data: List<BarPoint>, selected: Int, onTap: (Int) -> Unit) {
    val max = (data.maxOfOrNull { maxOf(it.collected, it.spent) } ?: 0L).coerceAtLeast(1L)
    Column {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val slot = size.width / data.size
                val bw = slot * 0.26f
                for (k in 0..3) drawLine(Brand.Line, Offset(0f, size.height * k / 3f), Offset(size.width, size.height * k / 3f), 1f)
                data.forEachIndexed { i, b ->
                    val x = slot * i + slot / 2
                    val alpha = if (selected < 0 || i == selected) 1f else 0.45f
                    val h1 = size.height * b.collected / max
                    val h2 = size.height * b.spent / max
                    drawRoundRect(Brand.Blue600.copy(alpha = alpha), Offset(x - bw - 2f, size.height - h1), Size(bw, h1), CornerRadius(bw / 2, bw / 2))
                    drawRoundRect(Brand.Saffron500.copy(alpha = alpha), Offset(x + 2f, size.height - h2), Size(bw, h2), CornerRadius(bw / 2, bw / 2))
                }
            }
            // one tap area per bar
            Row(Modifier.matchParentSize()) { data.indices.forEach { i -> Box(Modifier.weight(1f).fillMaxHeight().clickable { onTap(i) }) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            data.forEachIndexed { i, b ->
                Text(b.label, Modifier.weight(1f).clickable { onTap(i) }, textAlign = TextAlign.Center, maxLines = 1,
                    style = MaterialTheme.typography.labelMedium, color = if (i == selected) Brand.Ink else Brand.Slate400)
            }
        }
    }
}

/** One bar: money in and out, with its label (month or year). */
private data class BarPoint(val label: String, val collected: Long, val spent: Long)

/** A one-page monthly report to share or print. */
fun shareReport(ctx: Context, m: MonthMoney, enquiries: List<Enquiry>) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val c = page.canvas
    val blue = android.graphics.Color.parseColor("#0b5583")
    fun paint(size: Float, bold: Boolean = false, color: Int = android.graphics.Color.parseColor("#1e293b")) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    c.drawRect(0f, 0f, 595f, 110f, Paint().apply { color = blue })
    c.drawText(AppSettings.academyName, 36f, 52f, paint(24f, true, android.graphics.Color.WHITE))
    val label = m.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    c.drawText("Monthly report · $label", 36f, 84f, paint(14f, false, android.graphics.Color.parseColor("#d6ecf7")))
    var y = 160f
    fun row(k: String, v: String, bold: Boolean = false) {
        c.drawText(k, 48f, y, paint(13f, bold)); val p = paint(13f, bold); c.drawText(v, 547f - p.measureText(v), y, p); y += 26f
    }
    fun head(t: String) { y += 10f; c.drawText(t, 36f, y, paint(15f, true, blue)); y += 28f }
    head("Money in")
    row("Course fees", rupees(m.studentFees)); row("Library fees", rupees(m.libraryFees)); row("Registration", rupees(m.registration))
    row("Total collected", rupees(m.collected), true)
    head("Money out")
    if (m.byCategory.isEmpty()) row("No expenses noted", "—")
    m.byCategory.forEach { (k, v) -> row(k, rupees(v)) }
    row("Total spent", rupees(m.expenses), true)
    head(if (m.profit >= 0) "Profit" else "Loss")
    row(if (m.profit >= 0) "Collected minus spent" else "Spent more than collected", rupees(kotlin.math.abs(m.profit)), true)
    head("Library deposits (not income)")
    row("Received", rupees(m.depositsIn)); row("Returned", rupees(m.depositsOut))
    head("Enquiries")
    row("New enquiries", "${enquiries.size}"); row("Joined", "${enquiries.count { it.status == "JOINED" }}")
    enquiries.groupBy { it.source.ifBlank { "Not noted" } }.forEach { (k, v) -> row("  $k", "${v.size}") }
    c.drawText("Made with the ${AppSettings.academyName} app on ${LocalDate.now().toEpochDay().asDate()}", 36f, 812f, paint(10f, false, android.graphics.Color.parseColor("#8a97ab")))
    doc.finishPage(page)
    val f = File(ctx.cacheDir, "report-${m.month}.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
