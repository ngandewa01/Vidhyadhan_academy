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

/** One-page A5 receipt (system fonts, so Marathi names print correctly). Returns the PDF file. */
fun receiptPdf(ctx: Context, r: ReceiptData): File {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(420, 595, 1).create())
    val c = page.canvas
    val teal = Color.parseColor("#0F4C5C"); val tealLight = Color.parseColor("#E0F2F5"); val marigold = Color.parseColor("#E07A2B")
    val ink = Color.parseColor("#0F172A"); val grey = Color.parseColor("#64748B"); val line = Color.parseColor("#E2E8F0")
    fun paint(size: Float, bold: Boolean = false, color: Int = ink, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = align }
    val fill = { col: Int -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col } }

    // header: logo, academy, receipt number
    runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.logo) }.getOrNull()?.let { c.drawBitmap(it, null, RectF(24f, 22f, 74f, 72f), null) }
    c.drawText(AppSettings.academyName, 84f, 42f, paint(17f, true, teal))
    val contact = listOf(AppSettings.academyAddress, AppSettings.academyPhone.takeIf { it.isNotBlank() }?.let { "Ph: $it" } ?: "").filter { it.isNotBlank() }
    contact.take(2).forEachIndexed { i, s -> c.drawText(s.take(48), 84f, 58f + i * 13f, paint(9.5f, false, grey)) }
    c.drawRect(0f, 86f, 420f, 90f, fill(marigold))
    c.drawRect(0f, 90f, 420f, 122f, fill(teal))
    c.drawText(r.title, 24f, 111f, paint(12.5f, true, Color.WHITE))
    c.drawText("No. ${r.number}   ·   ${r.date.asDate()}", 396f, 111f, paint(10.5f, false, Color.WHITE, Paint.Align.RIGHT))

    // who and what
    c.drawText("Received with thanks from", 24f, 148f, paint(10f, false, grey))
    c.drawText(r.name, 24f, 168f, paint(16f, true))
    var y = 190f
    r.rows.forEachIndexed { i, (k, v) ->
        if (i % 2 == 0) c.drawRect(24f, y - 13f, 396f, y + 7f, fill(Color.parseColor("#F8FAFC")))
        c.drawText(k, 32f, y, paint(10.5f, false, grey))
        c.drawText(v.take(46), 388f, y, paint(11f, true, ink, Paint.Align.RIGHT))
        y += 20f
    }

    // amount
    y += 10f
    c.drawRoundRect(RectF(24f, y, 396f, y + 70f), 12f, 12f, fill(teal))
    c.drawText("AMOUNT RECEIVED", 38f, y + 22f, paint(9.5f, true, Color.parseColor("#B3DCE4")))
    c.drawText(rupees(r.amount), 38f, y + 50f, paint(24f, true, Color.WHITE))
    c.drawText("Rupees ${amountInWords(r.amount)} only", 382f, y + 50f, paint(9.5f, false, Color.parseColor("#E0F2F5"), Paint.Align.RIGHT).apply {
        // keep long words inside the box
        if (measureText("Rupees ${amountInWords(r.amount)} only") > 200f) textSize = 8f
    })
    y += 88f

    // fee position
    if (r.summary.isNotEmpty()) {
        val w = (372f - (r.summary.size - 1) * 8f) / r.summary.size
        r.summary.forEachIndexed { i, (k, v) ->
            val x = 24f + i * (w + 8f)
            c.drawRoundRect(RectF(x, y, x + w, y + 48f), 10f, 10f, fill(tealLight))
            c.drawText(k.uppercase(), x + w / 2, y + 18f, paint(8.5f, true, teal, Paint.Align.CENTER))
            c.drawText(v, x + w / 2, y + 37f, paint(13f, true, if (k.contains("Balance", true)) marigold else ink, Paint.Align.CENTER))
        }
        y += 64f
    }

    // footer
    c.drawLine(24f, 520f, 396f, 520f, paint(1f, false, line))
    c.drawText("Thank you!  धन्यवाद!", 24f, 540f, paint(12f, true, teal))
    c.drawText("Computer-generated receipt.", 24f, 556f, paint(8.5f, false, grey))
    c.drawLine(280f, 552f, 396f, 552f, paint(1f, false, Color.parseColor("#CBD5E1")))
    c.drawText("Authorised signature", 338f, 566f, paint(9f, false, grey, Paint.Align.CENTER))
    doc.finishPage(page)

    val f = File(ctx.cacheDir, "receipt-${r.number}.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    return f
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
