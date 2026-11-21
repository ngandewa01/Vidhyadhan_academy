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
    /** One line under the fee boxes, e.g. "Next installment ₹4,000 on 10 Dec 2026". */
    val footnote: String = "",
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

/** Draws the receipt on a 420 × 595 canvas (the PDF page, or a picture in tests). */
fun drawReceipt(ctx: Context, c: android.graphics.Canvas, r: ReceiptData) {
    val W = 420f; val H = 595f
    val teal = Color.parseColor("#0F4C5C"); val tealLight = Color.parseColor("#E0F2F5"); val marigold = Color.parseColor("#E07A2B")
    val ink = Color.parseColor("#0F172A"); val grey = Color.parseColor("#64748B"); val light = Color.parseColor("#94A3B8"); val line = Color.parseColor("#E2E8F0")
    val green = Color.parseColor("#16A34A"); val greenLight = Color.parseColor("#DCFCE7"); val soft = Color.parseColor("#F1F5F9")
    fun paint(size: Float, bold: Boolean = false, color: Int = ink, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = align }
    val fill = { col: Int -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col } }
    fun fit(p: Paint, text: String, max: Float): Paint { while (p.measureText(text) > max && p.textSize > 7f) p.textSize -= 0.5f; return p }
    val cx = W / 2
    val mobile = r.rows.firstOrNull { it.first == "Mobile" }?.second.orEmpty()
    val details = listOf("Date" to r.date.asDate()) + r.rows.filter { it.first != "Mobile" }

    // academy: logo + name
    val nameP = fit(paint(15f, true, teal), AppSettings.academyName, 300f)
    val nameW = nameP.measureText(AppSettings.academyName)
    val left = cx - (nameW + 44f) / 2
    runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.logo) }.getOrNull()?.let { c.drawBitmap(it, null, RectF(left, 22f, left + 34f, 56f), null) }
    c.drawText(AppSettings.academyName, left + 44f, 44f, nameP)

    // tick and amount
    c.drawCircle(cx, 104f, 30f, fill(greenLight))
    c.drawPath(android.graphics.Path().apply { moveTo(cx - 13f, 104f); lineTo(cx - 4f, 114f); lineTo(cx + 14f, 93f) },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
    c.drawText(rupees(r.amount), cx, 172f, paint(34f, true, ink, Paint.Align.CENTER))
    val words = "Rupees ${amountInWords(r.amount)} only"
    c.drawText(words, cx, 190f, fit(paint(10f, false, grey, Paint.Align.CENTER), words, 360f))
    val from = "Received from " ; val who = r.name; val tail = if (mobile.isNotBlank()) "  ·  $mobile" else ""
    val pf = paint(11.5f, false, Color.parseColor("#475569")); val pb = paint(11.5f, true, teal)
    val total = pf.measureText(from) + pb.measureText(who) + pf.measureText(tail)
    var x = cx - total / 2
    c.drawText(from, x, 212f, pf); x += pf.measureText(from)
    c.drawText(who, x, 212f, pb); x += pb.measureText(who)
    c.drawText(tail, x, 212f, pf)
    val chip = "${r.title.replace("  /  ", " · ")}  ·  No. ${r.number}"
    val chipP = fit(paint(9.5f, true, teal, Paint.Align.CENTER), chip, 340f)
    val cw = chipP.measureText(chip) + 22f
    c.drawRoundRect(RectF(cx - cw / 2, 222f, cx + cw / 2, 240f), 9f, 9f, fill(tealLight))
    c.drawText(chip, cx, 234.5f, chipP)

    // a torn-ticket edge
    fun perforation(y: Float) { var px = 6f; while (px < W) { c.drawCircle(px, y, 4.5f, fill(Color.parseColor("#E5EAF0"))); px += 13f } }
    perforation(258f)

    // details
    var y = 284f
    details.take(7).forEach { (k, v) ->
        c.drawText(k, 30f, y, paint(10.5f, false, grey))
        c.drawText(v, W - 30f, y, fit(paint(11f, true, ink, Paint.Align.RIGHT), v, 250f))
        c.drawLine(30f, y + 8f, W - 30f, y + 8f, paint(1f, false, line))
        y += 25f
    }

    // fee position: up to three boxes
    if (r.summary.isNotEmpty()) {
        y += 4f
        val n = r.summary.size.coerceAtMost(3)
        val bw = (W - 60f - (n - 1) * 8f) / n
        r.summary.take(3).forEachIndexed { i, (k, v) ->
            val bx = 30f + i * (bw + 8f)
            c.drawRoundRect(RectF(bx, y, bx + bw, y + 44f), 10f, 10f, fill(soft))
            c.drawText(k.uppercase(), bx + bw / 2, y + 16f, fit(paint(8f, true, grey, Paint.Align.CENTER), k.uppercase(), bw - 8f))
            val col = when { k.contains("Balance", true) -> marigold; k.contains("Paid", true) -> green; else -> ink }
            c.drawText(v, bx + bw / 2, y + 34f, fit(paint(12.5f, true, col, Paint.Align.CENTER), v, bw - 8f))
        }
        y += 58f
    }
    if (r.footnote.isNotBlank()) { c.drawText(r.footnote, cx, y, fit(paint(10.5f, false, Color.parseColor("#475569"), Paint.Align.CENTER), r.footnote, 360f)); y += 14f }

    // footer
    perforation(H - 62f)
    c.drawText("Thank you!  धन्यवाद!  ·  Computer-generated, no signature needed", cx, H - 38f, paint(9.5f, false, grey, Paint.Align.CENTER))
    val contact = listOf(AppSettings.academyAddress, AppSettings.academyPhone).filter { it.isNotBlank() }.joinToString("  ·  ")
    if (contact.isNotBlank()) c.drawText(contact, cx, H - 24f, fit(paint(8.5f, false, light, Paint.Align.CENTER), contact, 380f))
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
