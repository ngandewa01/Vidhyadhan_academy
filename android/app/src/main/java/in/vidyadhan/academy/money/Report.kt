package `in`.vidyadhan.academy.money

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.R
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.data.Enquiry
import `in`.vidyadhan.academy.data.LibraryMember
import `in`.vidyadhan.academy.data.StudentWithFees
import `in`.vidyadhan.academy.library.allSeats
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.studentsWithFeesNow
import `in`.vidyadhan.academy.students.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line in the "Fees due" list. */
data class DueLine(val name: String, val what: String, val amount: Long, val due: Long)

/** Everything the monthly report shows (worked out from the app's records). */
data class ReportData(
    val month: YearMonth, val made: Long,
    val cur: MonthMoney, val prev: MonthMoney?, val last6: List<MonthMoney>,
    val byCourse: List<Pair<String, Long>>, val byMode: List<Pair<String, Long>>,
    val pending: Long, val pendingPeople: Int, val dues: List<DueLine>, val moreDues: Int,
    val depositsHeld: Long,
    val seats: Int, val filled: Int, val renewals7: Int, val lockersUsed: Int, val lockers: Int, val newMembers: Int, val leftMembers: Int,
    val students: Int, val admissions: Int, val leftStudents: Int, val perCourse: List<Pair<String, Int>>,
    val enqNew: Int, val enqFollowed: Int, val enqJoined: Int, val enqClosed: Int, val bySource: List<Triple<String, Int, Int>>,
)

private fun YearMonth.days() = atDay(1).toEpochDay()..atEndOfMonth().toEpochDay()

/** Reads the records and works out the report for [month]. */
suspend fun loadReport(ctx: Context, month: YearMonth): ReportData = withContext(Dispatchers.IO) {
    val db = AppDb.get(ctx)
    val sp = db.students().paymentList()
    val lp = db.library().allPayments().first()
    val ex = db.expenses().all().first()
    val students = studentsWithFeesNow(db.students())
    val members = db.library().allMembers()
    val rooms = db.library().roomsNow()
    val enq = db.enquiries().all().first()
    val r = month.days()
    val t = today()
    val cur = monthMoney(month, sp, lp, ex)
    val last6 = (5 downTo 0).map { monthMoney(month.minusMonths(it.toLong()), sp, lp, ex) }
    val courseOf = students.associate { it.id to it.course.ifBlank { "Course" } }
    val byCourse = sp.filter { it.paidOn in r }.groupBy { courseOf[it.studentId] ?: "Course" }
        .map { (k, v) -> "$k course fees" to v.sumOf { it.amount } }.sortedByDescending { it.second } +
        listOfNotNull(cur.libraryFees.takeIf { it > 0 }?.let { "Abhyasika fees (seat + locker)" to it }, cur.registration.takeIf { it > 0 }?.let { "Registration" to it })
    val modes = (sp.filter { it.paidOn in r }.map { it.mode to it.amount } + lp.filter { it.paidOn in r && it.kind in listOf("FEE", "REGISTRATION") }.map { it.mode to it.amount })
        .groupBy { it.first.ifBlank { "Other" } }.map { (k, v) -> k to v.sumOf { it.second } }.sortedByDescending { it.second }
    val active = students.filter { it.leftOn == null }
    val owing = active.filter { it.totalFee > it.paid }
    val dueStudents = active.filter { it.nextDue != null && it.nextDueAmount > 0 && it.nextDue <= t + 10 }
        .map { DueLine(it.name, it.course + if (it.batch.isNotBlank()) " · ${it.batch}" else "", it.nextDueAmount, it.nextDue!!) }
    val activeMembers = members.filter { it.active }
    val dueMembers = activeMembers.filter { it.validTill <= t + 7 || it.balance > 0 }
        .map { DueLine(it.name, "Abhyasika seat ${it.seatNo}", if (it.balance > 0) it.balance else it.monthlyFee, it.validTill) }
    val dues = (dueStudents + dueMembers).sortedBy { it.due }
    val joined = enq.filter { it.createdOn in r }
    ReportData(
        month, t, cur, last6.getOrNull(4), last6, byCourse, modes,
        owing.sumOf { it.totalFee - it.paid }, owing.size, dues.take(9), (dues.size - 9).coerceAtLeast(0),
        lp.filter { it.kind == "DEPOSIT" }.sumOf { it.amount } - lp.filter { it.kind == "DEPOSIT_RETURN" }.sumOf { it.amount },
        allSeats(rooms, AppSettings.seatCount).size, activeMembers.size, activeMembers.count { it.validTill in t..t + 7 },
        activeMembers.count { it.hasLocker }, AppSettings.lockerCount, members.count { it.joinedOn in r }, members.count { it.leftOn?.let { d -> d in r } == true },
        active.size, students.count { it.joinedOn in r }, students.count { it.leftOn?.let { d -> d in r } == true },
        active.groupBy { it.course.ifBlank { "Other" } }.map { (k, v) -> k to v.size }.sortedByDescending { it.second },
        joined.size, joined.count { it.status != "NEW" }, joined.count { it.status == "JOINED" }, joined.count { it.status == "CLOSED" },
        joined.groupBy { it.source.ifBlank { "Not noted" } }.map { (k, v) -> Triple(k, v.size, v.count { it.status == "JOINED" }) }.sortedByDescending { it.second },
    )
}

// ───────────── drawing (A4, 595 × 842) ─────────────

private val TEAL = Color.parseColor("#0F4C5C"); private val TEAL6 = Color.parseColor("#166478"); private val TEAL1 = Color.parseColor("#E0F2F5")
private val MARI = Color.parseColor("#E07A2B"); private val MARI_SOFT = Color.parseColor("#F4A261"); private val INK = Color.parseColor("#0F172A")
private val S6 = Color.parseColor("#475569"); private val S5 = Color.parseColor("#64748B"); private val S4 = Color.parseColor("#94A3B8")
private val LINE = Color.parseColor("#E2E8F0"); private val SOFT = Color.parseColor("#F8FAFC"); private val GREEN = Color.parseColor("#16A34A")
private val RED = Color.parseColor("#DC2626"); private val VIOLET = Color.parseColor("#7C3AED")

private fun p(size: Float, bold: Boolean = false, color: Int = INK, align: Paint.Align = Paint.Align.LEFT) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = align }
private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }
private fun stroke(c: Int, w: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = w }
private fun short(v: Long) = rupees(v)
private fun pct(a: Long, b: Long): String? = if (b <= 0) null else ((a - b) * 100 / b).let { (if (it >= 0) "▲ " else "▼ ") + "${kotlin.math.abs(it)}%" }

private fun header(ctx: Context, c: Canvas, d: ReportData, big: Boolean) {
    val h = if (big) 92f else 62f
    c.drawRect(0f, 0f, 595f, h, Paint().apply { shader = LinearGradient(0f, 0f, 595f, h, Color.parseColor("#093541"), TEAL6, Shader.TileMode.CLAMP) })
    c.drawRect(0f, h, 595f, h + 4f, fill(MARI))
    val lg = if (big) 50f else 36f; val ly = (h - lg) / 2
    c.drawCircle(28f + lg / 2, ly + lg / 2, lg / 2 + 2f, fill(Color.WHITE))
    runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.logo) }.getOrNull()?.let { c.drawBitmap(it, null, RectF(28f, ly, 28f + lg, ly + lg), null) }
    c.drawText(AppSettings.academyName, 28f + lg + 14f, if (big) 44f else 38f, p(if (big) 19f else 15f, true, Color.WHITE))
    if (big) c.drawText(listOf("Academy & Abhyasika", AppSettings.academyAddress, AppSettings.academyPhone).filter { it.isNotBlank() }.joinToString(" · ").take(70),
        28f + lg + 14f, 62f, p(9.5f, false, Color.parseColor("#CCE7EE")))
    c.drawText(d.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), 567f, if (big) 44f else 38f, p(if (big) 16f else 13f, true, Color.WHITE, Paint.Align.RIGHT))
    if (big) c.drawText("Monthly report · made ${d.made.asDate()}", 567f, 62f, p(9.5f, false, Color.parseColor("#CCE7EE"), Paint.Align.RIGHT))
}

private fun section(c: Canvas, title: String, y: Float, note: String = "", x: Float = 28f, right: Float = 567f) {
    c.drawText(title.uppercase(), x, y, p(10f, true, TEAL).apply { letterSpacing = 0.08f })
    if (note.isNotBlank()) c.drawText(note, right, y, p(8.5f, false, S4, Paint.Align.RIGHT))
}

private fun footer(c: Canvas, left: String, page: Int) {
    c.drawLine(28f, 812f, 567f, 812f, stroke(LINE))
    c.drawText(left, 28f, 828f, p(8.5f, false, S4))
    c.drawText("Page $page of 2", 567f, 828f, p(8.5f, false, S4, Paint.Align.RIGHT))
}

private fun row(c: Canvas, k: String, v: String, x: Float, right: Float, y: Float, bold: Boolean = false, color: Int = INK) {
    c.drawText(k, x, y, p(if (bold) 10.5f else 9.5f, bold, if (bold) TEAL else INK))
    c.drawText(v, right, y, p(if (bold) 10.5f else 9.5f, true, if (bold) TEAL else color, Paint.Align.RIGHT))
    if (!bold) c.drawLine(x, y + 6f, right, y + 6f, stroke(LINE))
}

private fun hbar(c: Canvas, label: String, value: String, frac: Float, color: Int, x: Float, right: Float, y: Float) {
    c.drawText(label.take(14), x, y, p(9.5f, false, S6))
    val bx = x + 70f; val bw = right - x - 70f - 70f
    c.drawRoundRect(RectF(bx, y - 7f, bx + bw, y + 1f), 4f, 4f, fill(Color.parseColor("#F1F5F9")))
    if (frac > 0) c.drawRoundRect(RectF(bx, y - 7f, bx + bw * frac.coerceIn(0.02f, 1f), y + 1f), 4f, 4f, fill(color))
    c.drawText(value, right, y, p(9.5f, true, INK, Paint.Align.RIGHT))
}

private fun tile(c: Canvas, x: Float, y: Float, w: Float, h: Float, top: String, big: String, sub: String = "", bg: Int = SOFT, bigColor: Int = INK, border: Int? = null) {
    c.drawRoundRect(RectF(x, y, x + w, y + h), 10f, 10f, fill(bg))
    border?.let { c.drawRoundRect(RectF(x, y, x + w, y + h), 10f, 10f, stroke(it)) }
    c.drawText(top.uppercase(), x + 10f, y + 15f, p(7.5f, true, S5))
    val bp = p(16f, true, bigColor); while (bp.measureText(big) > w - 16f && bp.textSize > 9f) bp.textSize -= 0.5f
    c.drawText(big, x + 10f, y + 35f, bp)
    if (sub.isNotBlank()) c.drawText(sub, x + 10f, y + 49f, p(8.5f, true, S5))
}

/** Page 1: at a glance, 6-month chart, money in and out, deposits. */
fun drawReportPage1(ctx: Context, c: Canvas, d: ReportData) {
    header(ctx, c, d, true)
    var y = 120f
    section(c, "At a glance", y); y += 10f
    val w = (539f - 3 * 8f) / 4
    val m = d.cur
    tile(c, 28f, y, w, 58f, "Collected", short(m.collected), d.prev?.let { pct(m.collected, it.collected) }?.let { "$it vs last month" }.orEmpty(), Color.WHITE, border = LINE)
    tile(c, 28f + (w + 8f), y, w, 58f, "Spent", short(m.expenses), d.prev?.let { pct(m.expenses, it.expenses) }?.let { "$it vs last month" }.orEmpty(), Color.WHITE, border = LINE)
    tile(c, 28f + 2 * (w + 8f), y, w, 58f, if (m.profit >= 0) "Profit" else "Loss", short(kotlin.math.abs(m.profit)),
        if (m.collected > 0) "${(m.profit * 100 / m.collected)}% margin" else "", Color.parseColor("#F0FDF4"), if (m.profit >= 0) Color.parseColor("#15803D") else RED, Color.parseColor("#BBF7D0"))
    tile(c, 28f + 3 * (w + 8f), y, w, 58f, "Dues pending", short(d.pending), "${d.pendingPeople} students", Color.parseColor("#FFF7ED"), Color.parseColor("#C2410C"), Color.parseColor("#FED7AA"))
    y += 84f

    section(c, "Last 6 months", y, "collected · spent · profit"); y += 12f
    val maxV = (d.last6.maxOfOrNull { maxOf(it.collected, it.expenses) } ?: 1L).coerceAtLeast(1L).toFloat()
    val chartH = 92f; val base = y + chartH
    val slot = 539f / 6
    d.last6.forEachIndexed { i, mm ->
        val cx = 28f + slot * i + slot / 2
        listOf(mm.collected to TEAL6, mm.expenses to MARI_SOFT, mm.profit.coerceAtLeast(0) to GREEN).forEachIndexed { j, (v, col) ->
            val bx = cx - 24f + j * 17f
            val bh = chartH * v / maxV
            if (bh > 0) c.drawRoundRect(RectF(bx, base - bh, bx + 14f, base), 3f, 3f, fill(col))
        }
        c.drawText(mm.month.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)), cx, base + 12f, p(8.5f, false, S5, Paint.Align.CENTER))
    }
    c.drawLine(28f, base, 567f, base, stroke(LINE))
    y = base + 34f

    // money in (left) and out (right)
    val lx = 28f; val lr = 288f; val rx = 307f; val rr = 567f
    section(c, "Money in", y, x = lx, right = lr); section(c, "Money out", y, x = rx, right = rr)
    var yl = y + 20f
    d.byCourse.take(6).forEach { (k, v) -> row(c, k, short(v), lx, lr, yl); yl += 20f }
    row(c, "Total collected", short(m.collected), lx, lr, yl + 4f, bold = true); yl += 22f
    if (d.byMode.isNotEmpty()) c.drawText("Paid by: " + d.byMode.joinToString(" · ") { "${it.first} ${short(it.second)}" }, lx, yl, p(8.5f, false, S5))
    var yr = y + 20f
    val maxE = (m.byCategory.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L).toFloat()
    if (m.byCategory.isEmpty()) { c.drawText("No expenses noted this month", rx, yr, p(9.5f, false, S5)); yr += 20f }
    m.byCategory.take(7).forEach { (k, v) -> hbar(c, k, short(v), v / maxE, MARI, rx, rr, yr); yr += 18f }
    row(c, "Total spent", short(m.expenses), rx, rr, yr + 6f, bold = true)
    y = maxOf(yl, yr + 6f) + 34f

    section(c, "Deposits", y, "held aside, not income"); y += 20f
    row(c, "Received this month", short(m.depositsIn), 28f, 567f, y); y += 20f
    row(c, "Returned this month", short(m.depositsOut), 28f, 567f, y); y += 22f
    row(c, "Total deposits now held", short(d.depositsHeld), 28f, 567f, y, bold = true)
    footer(c, "${AppSettings.academyName} · ${d.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))} report", 1)
}

/** Page 2: fees due, Abhyasika, academy, enquiries. */
fun drawReportPage2(ctx: Context, c: Canvas, d: ReportData) {
    header(ctx, c, d, false)
    var y = 92f
    section(c, "Fees due", y, "overdue first · ${d.pendingPeople} students · ${short(d.pending)}"); y += 20f
    if (d.dues.isEmpty()) { c.drawText("Nobody owes fees right now 🎉", 28f, y, p(9.5f, false, S5)); y += 20f }
    d.dues.forEach { l ->
        val late = d.made - l.due
        c.drawText(l.name.take(26), 28f, y, p(9.5f, true))
        val tag = if (late > 0) "Overdue $late d" else "Due ${LocalDate.ofEpochDay(l.due).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}"
        val tp = p(7.5f, true, if (late > 0) Color.parseColor("#B91C1C") else Color.parseColor("#9A3412"))
        val tx = 28f + p(9.5f, true).measureText(l.name.take(26)) + 8f; val tw = tp.measureText(tag) + 10f
        c.drawRoundRect(RectF(tx, y - 9f, tx + tw, y + 2f), 5f, 5f, fill(if (late > 0) Color.parseColor("#FEE2E2") else Color.parseColor("#FDF0E6")))
        c.drawText(tag, tx + 5f, y - 1f, tp)
        c.drawText(l.what.take(30), 380f, y, p(9f, false, S5))
        c.drawText(short(l.amount), 567f, y, p(9.5f, true, INK, Paint.Align.RIGHT))
        c.drawLine(28f, y + 6f, 567f, y + 6f, stroke(LINE)); y += 20f
    }
    if (d.moreDues > 0) { c.drawText("… ${d.moreDues} more (full list in the app)", 28f, y, p(8.5f, false, S4)); y += 16f }
    y += 18f

    val w = (539f - 3 * 8f) / 4
    fun four(vararg t: Triple<String, String, Int>) { t.forEachIndexed { i, (big, small, col) -> tile(c, 28f + i * (w + 8f), y, w, 46f, small, big, bigColor = col) } }
    section(c, "Abhyasika", y); y += 10f
    four(Triple("${d.filled}/${d.seats}", "Seats filled" + if (d.seats > 0) " · ${d.filled * 100 / d.seats}%" else "", INK),
        Triple("${(d.seats - d.filled).coerceAtLeast(0)}", "Free seats", GREEN), Triple("${d.renewals7}", "Renewals next 7 days", MARI),
        Triple("${d.lockersUsed}/${d.lockers}", "Lockers in use", INK))
    y += 60f
    c.drawText("New this month ${d.newMembers} · Left ${d.leftMembers} · Abhyasika income ${short(d.cur.libraryFees)}", 28f, y, p(8.5f, false, S5)); y += 26f

    section(c, "Academy", y); y += 10f
    four(Triple("${d.students}", "Students", INK), Triple("${d.admissions}", "New admissions", GREEN), Triple("${d.leftStudents}", "Left / finished", INK),
        Triple(d.perCourse.firstOrNull()?.let { "${it.first} ${it.second}" } ?: "—", d.perCourse.drop(1).take(3).joinToString(" · ") { "${it.first} ${it.second}" }.ifBlank { "Per course" }, INK))
    y += 72f

    section(c, "Enquiries", y, d.month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))); y += 10f
    val conv = if (d.enqNew > 0) " · ${d.enqJoined * 100 / d.enqNew}%" else ""
    listOf(Triple("${d.enqNew}", "New", TEAL1), Triple("${d.enqFollowed}", "Followed up", Color.parseColor("#EDE9FE")),
        Triple("${d.enqJoined}", "Joined$conv", Color.parseColor("#DCFCE7")), Triple("${d.enqClosed}", "Not interested", Color.parseColor("#F1F5F9"))).forEachIndexed { i, (big, small, bg) ->
        val x = 28f + i * (w + 8f)
        c.drawRoundRect(RectF(x, y, x + w, y + 44f), 10f, 10f, fill(bg))
        c.drawText(big, x + w / 2, y + 22f, p(16f, true, INK, Paint.Align.CENTER))
        c.drawText(small, x + w / 2, y + 36f, p(8.5f, false, S6, Paint.Align.CENTER))
    }
    y += 64f
    val maxS = (d.bySource.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1).toFloat()
    d.bySource.take(5).forEach { (src, n, j) -> hbar(c, src, "$n · $j joined", n / maxS, VIOLET, 28f, 567f, y); y += 18f }
    footer(c, "Made with the ${AppSettings.academyName} app · figures are from the app's records", 2)
}

/** Makes the two-page PDF. */
fun reportPdf(ctx: Context, d: ReportData): File {
    val doc = PdfDocument()
    listOf<(Canvas) -> Unit>({ drawReportPage1(ctx, it, d) }, { drawReportPage2(ctx, it, d) }).forEachIndexed { i, draw ->
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
        page.canvas.drawColor(Color.WHITE); draw(page.canvas); doc.finishPage(page)
    }
    val f = File(ctx.cacheDir, "report-${d.month}.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    return f
}

/** Builds the report for [month] and opens the share menu. */
suspend fun shareMonthlyReport(ctx: Context, month: YearMonth) {
    val f = withContext(Dispatchers.IO) { reportPdf(ctx, loadReport(ctx, month)) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
