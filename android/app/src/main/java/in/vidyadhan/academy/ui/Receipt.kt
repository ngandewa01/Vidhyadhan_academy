package `in`.vidyadhan.academy.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.R
import `in`.vidyadhan.academy.students.asDate
import `in`.vidyadhan.academy.students.rupees
import java.io.File

data class ReceiptData(
    val number: String,
    val date: Long,
    val title: String, // e.g. "FEE RECEIPT"
    val name: String,
    val rows: List<Pair<String, String>>, // label to value
    val amount: Long,
    val summary: List<Pair<String, String>> = emptyList(),
    /** One line under the fee table, when there is no next installment to show. */
    val footnote: String = "",
    /** The main line of the particulars table, e.g. "MPSC course fee · Evening batch". */
    val forText: String = "",
    /** The small line under it: installment, payment mode, reference. */
    val forSub: String = "",
    /** The next installment, printed with a calendar badge. */
    val nextAmount: Long = 0,
    val nextDue: Long? = null,
)

/** 15000 → "Fifteen Thousand"; Indian system (lakh, crore). */
fun amountInWords(n: Long): String {
    if (n == 0L) return "Zero"
    val ones = listOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
        "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    val tens = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
    fun two(x: Int): String = if (x < 20) ones[x] else tens[x / 10] + if (x % 10 > 0) " " + ones[x % 10] else ""
    fun three(x: Int): String = (if (x >= 100) ones[x / 100] + " Hundred" + if (x % 100 > 0) " " else "" else "") + if (x % 100 > 0) two(x % 100) else ""
    var v = n
    val parts = mutableListOf<String>()
    val crore = v / 10_000_000; v %= 10_000_000
    val lakh = v / 100_000; v %= 100_000
    val thousand = v / 1000; v %= 1000
    if (crore > 0) parts += amountInWords(crore) + " Crore"
    if (lakh > 0) parts += two(lakh.toInt()) + " Lakh"
    if (thousand > 0) parts += two(thousand.toInt()) + " Thousand"
    if (v > 0) parts += three(v.toInt())
    return parts.joinToString(" ")
}

/**
 * One-page A5 receipt in the "payment app" style: tick and amount first, then the details and the fee position.
 * System fonts are used so Marathi names print correctly. Returns the PDF file.
 */
fun receiptPdf(ctx: Context, r: ReceiptData): File {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(420, 595, 1).create())
    drawReceipt(ctx, page.canvas, r)
    doc.finishPage(page)
    val f = File(ctx.cacheDir, "receipt-${r.number}.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    return f
}

/** Draws the receipt on a 420 × 595 canvas (the PDF page, or a picture in tests): letterhead, big amount card, particulars, fee position, next installment. */
fun drawReceipt(ctx: Context, c: android.graphics.Canvas, r: ReceiptData) {
    val W = 420f; val H = 595f
    val teal = Color.parseColor("#0F4C5C"); val marigold = Color.parseColor("#E07A2B"); val mari1 = Color.parseColor("#FDF0E4")
    val ink = Color.parseColor("#0F172A"); val grey = Color.parseColor("#64748B"); val light = Color.parseColor("#94A3B8"); val line = Color.parseColor("#E2E8F0")
    val green = Color.parseColor("#15803D"); val orange = Color.parseColor("#C2410C"); val soft = Color.parseColor("#F8FAFC")
    fun paint(size: Float, bold: Boolean = false, color: Int = ink, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = align }
    val fill = { col: Int -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col } }
    fun fit(p: Paint, text: String, max: Float): Paint { while (p.measureText(text) > max && p.textSize > 6f) p.textSize -= 0.5f; return p }
    fun grad(rect: RectF) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(rect.left, rect.top, rect.right, rect.bottom, Color.parseColor("#093541"), Color.parseColor("#145E70"), android.graphics.Shader.TileMode.CLAMP)
    }
    val refund = r.title.contains("REFUND", true)
    val mobile = r.rows.firstOrNull { it.first == "Mobile" }?.second.orEmpty()
    val pale = Color.parseColor("#CCE7EE")

    // letterhead
    c.drawRect(0f, 0f, W, 84f, grad(RectF(0f, 0f, W, 84f)))
    c.drawRect(0f, 84f, W, 88f, fill(marigold))
    c.drawCircle(44f, 42f, 25f, fill(Color.WHITE))
    runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.logo) }.getOrNull()?.let { c.drawBitmap(it, null, RectF(22f, 20f, 66f, 64f), Paint(Paint.FILTER_BITMAP_FLAG)) }
    c.drawText(AppSettings.academyName, 80f, 36f, fit(paint(16f, true, Color.WHITE), AppSettings.academyName, W - 96f))
    val tag = (AppSettings.courseList + "Abhyasika Kendra").filter { it.isNotBlank() }.joinToString(" · ")
    c.drawText(tag, 80f, 52f, fit(paint(8.5f, false, pale), tag, W - 96f))
    val contact = listOf(AppSettings.academyAddress, AppSettings.academyPhone).filter { it.isNotBlank() }.joinToString(" · ")
    if (contact.isNotBlank()) c.drawText(contact, 80f, 66f, fit(paint(8.5f, false, pale), contact, W - 96f))

    // title, number and date
    val title = r.title.substringBefore(" / ").trim()
    c.drawText(title, 24f, 117f, paint(12.5f, true, teal).apply { letterSpacing = 0.08f })
    c.drawText("No. ${r.number}", W - 24f, 108f, paint(9f, true, ink, Paint.Align.RIGHT))
    c.drawText("Date ${r.date.asDate()}", W - 24f, 122f, paint(9f, false, grey, Paint.Align.RIGHT))

    // the big amount card
    val card = RectF(24f, 134f, W - 24f, 224f)
    c.save()
    c.clipPath(android.graphics.Path().apply { addRoundRect(card, 14f, 14f, android.graphics.Path.Direction.CW) })
    c.drawRect(card, grad(card))
    c.drawCircle(card.right - 18f, card.top + 4f, 64f, fill(Color.argb(18, 255, 255, 255)))
    c.restore()
    val white80 = Color.argb(210, 255, 255, 255)
    c.drawText(if (refund) "AMOUNT RETURNED" else "AMOUNT RECEIVED", 40f, 155f, paint(8f, true, white80).apply { letterSpacing = 0.08f })
    c.drawText(rupees(r.amount), 40f, 185f, paint(27f, true, Color.WHITE))
    val words = "Rupees ${amountInWords(r.amount)} only"
    c.drawText(words, 40f, 199f, fit(paint(8.5f, false, white80), words, card.width() - 32f))
    val pre = if (refund) "Paid to " else "Received from "
    val pf = paint(9.5f, false, white80)
    val tail = if (mobile.isNotBlank()) "  ·  $mobile" else ""
    var x = 40f
    c.drawText(pre, x, 215f, pf); x += pf.measureText(pre)
    val pb = fit(paint(10.5f, true, Color.WHITE), r.name, (card.right - 16f - x - pf.measureText(tail)).coerceAtLeast(60f))
    c.drawText(r.name, x, 215f, pb); x += pb.measureText(r.name)
    c.drawText(tail, x, 215f, pf)

    // particulars
    var y = 240f
    c.drawRect(24f, y, W - 24f, y + 18f, fill(soft))
    c.drawText("PARTICULARS", 32f, y + 12.5f, paint(7.5f, true, grey).apply { letterSpacing = 0.06f })
    c.drawText("AMOUNT", W - 32f, y + 12.5f, paint(7.5f, true, grey, Paint.Align.RIGHT).apply { letterSpacing = 0.06f })
    y += 18f
    val main = r.forText.ifBlank { r.rows.firstOrNull { it.first == "Course" || it.first == "For" }?.second ?: title.lowercase().replaceFirstChar { it.uppercase() } }
    val sub = r.forSub.ifBlank { r.rows.firstOrNull { it.first == "Payment mode" }?.second?.let { "Paid by $it" }.orEmpty() }
    c.drawText(main, 32f, y + 17f, fit(paint(10f, true, ink), main, W - 150f))
    if (sub.isNotBlank()) c.drawText(sub, 32f, y + 30f, fit(paint(8.5f, false, grey), sub, W - 150f))
    c.drawText(rupees(r.amount), W - 32f, y + 17f, paint(10f, true, ink, Paint.Align.RIGHT))
    y += 40f
    c.drawLine(24f, y, W - 24f, y, paint(1f, false, line))
    c.drawText(if (refund) "Total returned" else "Total received", 32f, y + 17f, paint(10.5f, true, ink))
    c.drawText(rupees(r.amount), W - 32f, y + 17f, paint(11f, true, ink, Paint.Align.RIGHT))
    y += 25f
    c.drawRect(24f, y, W - 24f, y + 1.6f, fill(teal))
    y += 14f

    // fee position
    if (r.summary.isNotEmpty()) {
        val n = r.summary.size.coerceAtMost(3)
        val cw = (W - 48f) / n
        c.drawRect(24f, y, W - 24f, y + 18f, fill(soft))
        r.summary.take(3).forEachIndexed { i, (k, v) ->
            val last = i == n - 1 && n > 1
            val xx = if (last) 24f + cw * (i + 1) - 8f else 24f + cw * i + 8f
            val al = if (last) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(k, xx, y + 12.5f, fit(paint(8f, true, grey, al), k, cw - 12f))
            val col = when { k.contains("Balance", true) -> orange; k.contains("Paid", true) -> green; else -> ink }
            c.drawText(v, xx, y + 34f, fit(paint(10.5f, true, col, al), v, cw - 12f))
        }
        y += 42f
        c.drawLine(24f, y, W - 24f, y, paint(1f, false, line))
        y += 12f
    }

    // next installment with a calendar badge
    val nd = r.nextDue
    if (nd != null && r.nextAmount > 0) {
        c.drawRoundRect(RectF(24f, y, W - 24f, y + 44f), 11f, 11f, fill(mari1))
        val cal = RectF(34f, y + 6f, 66f, y + 38f)
        c.drawRoundRect(cal, 8f, 8f, fill(Color.WHITE))
        c.drawRoundRect(cal, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#F6C9A2"); strokeWidth = 1f })
        val d = java.time.LocalDate.ofEpochDay(nd)
        c.drawText(d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase(), cal.centerX(), y + 17f, paint(7f, true, marigold, Paint.Align.CENTER))
        c.drawText("${d.dayOfMonth}", cal.centerX(), y + 32f, paint(12.5f, true, ink, Paint.Align.CENTER))
        c.drawText("Next installment ${rupees(r.nextAmount)}", 76f, y + 20f, paint(10.5f, true, ink))
        c.drawText("due on ${nd.asDate()}", 76f, y + 34f, paint(8.5f, false, grey))
        y += 56f
    } else if (r.footnote.isNotBlank()) {
        c.drawText(r.footnote, 24f, y + 10f, fit(paint(9.5f, false, grey), r.footnote, W - 48f)); y += 24f
    }

    // terms and signature
    val fy = maxOf(y + 30f, H - 60f)
    if (!refund) {
        c.drawText("Fees once paid are not refundable", 24f, fy + 6f, paint(8f, false, grey))
        c.drawText("or transferable.", 24f, fy + 17f, paint(8f, false, grey))
    }
    c.drawLine(W - 150f, fy + 4f, W - 24f, fy + 4f, paint(1f, false, light))
    c.drawText("Authorised signature", W - 87f, fy + 17f, paint(8.5f, false, grey, Paint.Align.CENTER))
}

/** Shares the receipt PDF (WhatsApp, Drive, print…). */
fun shareReceipt(ctx: Context, r: ReceiptData) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", receiptPdf(ctx, r))
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Receipt ${r.number} — ${AppSettings.academyName}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun uriOf(ctx: Context, r: ReceiptData) = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", receiptPdf(ctx, r))

/** Opens WhatsApp straight in [mobile]'s chat with the receipt attached (WhatsApp, then WhatsApp Business; else the share menu). */
fun whatsappReceipt(ctx: Context, r: ReceiptData, mobile: String) {
    val uri = uriOf(ctx, r)
    val jid = "91${mobile.filter(Char::isDigit).takeLast(10)}@s.whatsapp.net"
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf").setPackage(pkg)
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra("jid", jid)
            .putExtra(Intent.EXTRA_TEXT, "Receipt ${r.number} — ${AppSettings.academyName}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isSuccess) return
    }
    shareReceipt(ctx, r)
}

/** Opens the receipt to look at or print. */
fun viewReceipt(ctx: Context, r: ReceiptData) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(ctx, r), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { ctx.startActivity(i) }.isFailure) shareReceipt(ctx, r)
}
