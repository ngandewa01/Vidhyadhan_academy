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
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.students.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the year or all-time report shows. [kind] is "year" or "all". */
data class PeriodData(
    val kind: String, val year: Int, val months: List<MonthMoney>, val expByCategory: List<Pair<String, Long>>, val made: Long,
)

/** Reads the records for a year, or for every month since the first record. */
suspend fun loadPeriod(ctx: Context, kind: String, year: Int): PeriodData = withContext(Dispatchers.IO) {
    val db = AppDb.get(ctx)
    val sp = db.students().paymentList()
    val lp = db.library().allPayments().first()
    val ex = db.expenses().all().first()
    val kh = db.khata().payments().first()
    val now = YearMonth.now()
    val months = if (kind == "year") (1..12).map { YearMonth.of(year, it) }.filter { it <= now }
    else {
        val first = listOfNotNull(sp.minOfOrNull { it.paidOn }, lp.minOfOrNull { it.paidOn }, ex.minOfOrNull { it.spentOn }, kh.minOfOrNull { it.date }).minOrNull()
        val start = first?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: now
        generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= now }.toList()
    }.ifEmpty { listOf(now) }
    val from = months.first().atDay(1).toEpochDay()
    val to = months.last().atEndOfMonth().toEpochDay()
    val cats = ex.filter { it.spentOn in from..to }.groupBy { it.category.ifBlank { "Other" } }
        .map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }
    PeriodData(kind, year, months.map { monthMoney(it, sp, lp, ex, kh) }, cats, today())
}

private val P_TEAL = Color.parseColor("#0F4C5C"); private val P_TEAL6 = Color.parseColor("#166478")
private val P_MARI = Color.parseColor("#E07A2B"); private val P_INK = Color.parseColor("#0F172A")
private val P_S5 = Color.parseColor("#64748B"); private val P_S4 = Color.parseColor("#94A3B8")
private val P_LINE = Color.parseColor("#E2E8F0"); private val P_SOFT = Color.parseColor("#F8FAFC")
private val P_GREEN = Color.parseColor("#16A34A"); private val P_RED = Color.parseColor("#DC2626")

private fun pText(size: Float, bold: Boolean = false, color: Int = P_INK, align: Paint.Align = Paint.Align.LEFT) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = align }
private fun pFill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }
private fun pStroke(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = 1f }

private fun pHeader(ctx: Context, c: Canvas, title: String, sub: String, right: String) {
    val h = 92f
    c.drawRect(0f, 0f, 595f, h, Paint().apply { shader = LinearGradient(0f, 0f, 595f, h, Color.parseColor("#093541"), P_TEAL6, Shader.TileMode.CLAMP) })
    c.drawRect(0f, h, 595f, h + 4f, pFill(P_MARI))
    val lg = 50f; val ly = (h - lg) / 2
    c.drawCircle(28f + lg / 2, ly + lg / 2, lg / 2 + 2f, pFill(Color.WHITE))
    runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.logo) }.getOrNull()?.let { c.drawBitmap(it, null, RectF(28f, ly, 28f + lg, ly + lg), null) }
    c.drawText(title, 28f + lg + 14f, 44f, pText(17f, true, Color.WHITE))
    c.drawText(sub.take(80), 28f + lg + 14f, 62f, pText(9.5f, false, Color.parseColor("#CCE7EE")))
    c.drawText(right, 567f, 44f, pText(12f, true, Color.WHITE, Paint.Align.RIGHT))
    c.drawText("${AppSettings.academyName}", 567f, 62f, pText(9.5f, false, Color.parseColor("#CCE7EE"), Paint.Align.RIGHT))
}

private fun pTile(c: Canvas, x: Float, y: Float, w: Float, top: String, big: String, color: Int = P_INK) {
    c.drawRoundRect(RectF(x, y, x + w, y + 46f), 10f, 10f, pFill(P_SOFT))
    c.drawText(top.uppercase(), x + 10f, y + 15f, pText(7.5f, true, P_S5))
    val bp = pText(16f, true, color); while (bp.measureText(big) > w - 16f && bp.textSize > 9f) bp.textSize -= 0.5f
    c.drawText(big, x + 10f, y + 35f, bp)
}

private fun pSection(c: Canvas, title: String, y: Float) {
    c.drawText(title.uppercase(), 28f, y, pText(10f, true, P_TEAL).apply { letterSpacing = 0.08f })
}

/** A table: first column on the left, the others right-aligned at [rights]. Returns the next free height. */
private fun pTable(c: Canvas, y0: Float, heads: List<String>, rights: List<Float>, rows: List<List<String>>, boldLast: Boolean = false): Float {
    var y = y0
    heads.forEachIndexed { i, h ->
        if (i == 0) c.drawText(h.uppercase(), 28f, y, pText(7.5f, true, P_S5))
        else c.drawText(h.uppercase(), rights[i], y, pText(7.5f, true, P_S5, Paint.Align.RIGHT))
    }
    c.drawLine(28f, y + 5f, 567f, y + 5f, pStroke(P_LINE))
    y += 19f
    rows.forEachIndexed { r, cells ->
        val bold = boldLast && r == rows.lastIndex
        val col = if (bold) P_TEAL else P_INK
        cells.forEachIndexed { i, v ->
            if (i == 0) c.drawText(v, 28f, y, pText(9.5f, bold, col))
            else c.drawText(v, rights[i], y, pText(9.5f, true, col, Paint.Align.RIGHT))
        }
        y += 17f
    }
    return y + 8f
}

private fun pFooter(c: Canvas, left: String, page: Int) {
    c.drawLine(28f, 812f, 567f, 812f, pStroke(P_LINE))
    c.drawText(left, 28f, 828f, pText(8.5f, false, P_S4))
    c.drawText("Page $page of 2", 567f, 828f, pText(8.5f, false, P_S4, Paint.Align.RIGHT))
}

private fun periodTitle(d: PeriodData): Pair<String, String> {
    val first = d.months.first().month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    val last = d.months.last().month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    return if (d.kind == "year") "${AppSettings.academyName} · Year report" to "Report for ${d.year} ($first to $last)"
    else "${AppSettings.academyName} · All-time report" to "From $first to $last"
}

private fun sourceTotals(d: PeriodData): List<Pair<String, Long>> = listOf(
    "Course fees" to d.months.sumOf { it.studentFees },
    "Abhyasika (library) fees" to d.months.sumOf { it.libraryFees },
    "Registration" to d.months.sumOf { it.registration },
    "Old book payments recorded in the app" to d.months.sumOf { it.khataPayments },
).filter { it.second > 0 }

/** Page 1 of the year report: totals, month by month, and where the money came from. */
fun drawPeriodPage1(ctx: Context, c: Canvas, d: PeriodData) {
    val (title, sub) = periodTitle(d)
    val collected = d.months.sumOf { it.collected }; val spent = d.months.sumOf { it.expenses }; val profit = collected - spent
    pHeader(ctx, c, title, sub, "Made ${d.made}")
    val w = (539f - 16f) / 3
    pTile(c, 28f, 110f, w, "Money in", rupees(collected))
    pTile(c, 28f + w + 8f, 110f, w, "Money out", rupees(spent))
    pTile(c, 28f + 2 * (w + 8f), 110f, w, if (profit >= 0) "Profit" else "Loss", rupees(kotlin.math.abs(profit)), if (profit >= 0) P_GREEN else P_RED)
    var y = 186f
    if (d.kind == "year") {
        pSection(c, "Month by month", y); y += 14f
        val rows = d.months.map { m ->
            listOf(m.month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)), rupees(m.collected), rupees(m.expenses), rupees(m.collected - m.expenses))
        }
        y = pTable(c, y, listOf("Month", "Money in", "Money out", "Profit"), listOf(0f, 360f, 460f, 567f), rows)
    } else {
        pSection(c, "Year by year", y); y += 14f
        val rows = d.months.groupBy { it.month.year }.toSortedMap().map { (yr, l) ->
            val inn = l.sumOf { it.collected }; val out = l.sumOf { it.expenses }
            listOf("$yr", rupees(inn), rupees(out), rupees(inn - out))
        }
        y = pTable(c, y, listOf("Year", "Money in", "Money out", "Profit"), listOf(0f, 360f, 460f, 567f), rows)
    }
    pSection(c, "Where money came from", y + 6f); y += 20f
    y = pTable(c, y, listOf("Source", "Amount"), listOf(0f, 567f), sourceTotals(d).map { listOf(it.first, rupees(it.second)) })
    c.drawText("* Money in counts payments received in the month they were paid.", 28f, y + 4f, pText(8.5f, false, P_S5))
    pFooter(c, "Made with the ${AppSettings.academyName} app · figures are from the app's records", 1)
}

/** Page 2: where the money went (by category), and expenses by month for a year. */
fun drawPeriodPage2(ctx: Context, c: Canvas, d: PeriodData) {
    pHeader(ctx, c, periodTitle(d).first, if (d.kind == "year") "Expenses for ${d.year}" else "Expenses, all years", "Made ${d.made}")
    var y = 124f
    pSection(c, "Where money went", y); y += 14f
    val total = d.expByCategory.sumOf { it.second }
    y = pTable(c, y, listOf("Category", "Amount", "Share"), listOf(0f, 460f, 567f),
        d.expByCategory.map { listOf(it.first, rupees(it.second), if (total > 0) "${it.second * 100 / total}%" else "0%") } +
            listOf(listOf("Total", rupees(total), "100%")), boldLast = true)
    if (d.kind == "year") {
        pSection(c, "Expenses by month", y + 6f); y += 20f
        y = pTable(c, y, listOf("Month", "Spent"), listOf(0f, 567f),
            d.months.map { listOf(it.month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)), rupees(it.expenses)) })
    }
    pFooter(c, "Made with the ${AppSettings.academyName} app · figures are from the app's records", 2)
}

/** The year and all-time reports as two pages. */
fun periodPdf(ctx: Context, d: PeriodData): File {
    val doc = PdfDocument()
    listOf<(Canvas) -> Unit>({ drawPeriodPage1(ctx, it, d) }, { drawPeriodPage2(ctx, it, d) }).forEachIndexed { i, draw ->
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
        page.canvas.drawColor(Color.WHITE); draw(page.canvas); doc.finishPage(page)
    }
    val f = File(ctx.cacheDir, if (d.kind == "year") "report-year-${d.year}.pdf" else "report-all-time.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    return f
}

/** Builds the year or all-time report and opens the share menu. */
suspend fun sharePeriodReport(ctx: Context, kind: String, year: Int) {
    val f = withContext(Dispatchers.IO) { periodPdf(ctx, loadPeriod(ctx, kind, year)) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
