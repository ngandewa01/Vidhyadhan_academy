package `in`.vidyadhan.academy.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import `in`.vidyadhan.academy.AppSettings
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

/** One-page A5 receipt using system fonts (so Marathi names print correctly), shared as a PDF. */
fun shareReceipt(ctx: Context, r: ReceiptData) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(420, 595, 1).create())
    val c = page.canvas
    val blue = Color.parseColor("#0b5583")
    val saffron = Color.parseColor("#ea580c")
    fun paint(size: Float, bold: Boolean = false, color: Int = Color.parseColor("#1e293b")) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }

    c.drawRect(0f, 0f, 420f, 96f, Paint().apply { color = blue })
    c.drawRect(0f, 96f, 420f, 100f, Paint().apply { color = saffron })
    c.drawText(AppSettings.academyName, 24f, 44f, paint(22f, true, Color.WHITE))
    val sub = listOf(AppSettings.academyAddress, AppSettings.academyPhone).filter { it.isNotBlank() }.joinToString("  |  ")
    if (sub.isNotBlank()) c.drawText(sub.take(70), 24f, 70f, paint(10.5f, false, Color.parseColor("#d6ecf7")))

    c.drawText(r.title, 24f, 136f, paint(15f, true, blue))
    c.drawText("No: ${r.number}", 24f, 160f, paint(11.5f))
    c.drawText("Date: ${r.date.asDate()}", 280f, 160f, paint(11.5f))

    var y = 200f
    fun row(k: String, v: String) {
        c.drawText(k, 24f, y, paint(11.5f, false, Color.parseColor("#526077")))
        c.drawText(v, 150f, y, paint(12.5f, true)); y += 26f
    }
    row("Received from", r.name)
    r.rows.forEach { (k, v) -> row(k, v) }
    y += 6f
    c.drawRect(24f, y - 18f, 396f, y + 36f, Paint().apply { color = Color.parseColor("#eef7fc") })
    c.drawText("Amount received", 36f, y + 13f, paint(12.5f))
    c.drawText(rupees(r.amount), 240f, y + 16f, paint(21f, true, blue))
    y += 70f
    r.summary.forEach { (k, v) -> row(k, v) }
    c.drawText("Thank you.  धन्यवाद.", 24f, 520f, paint(13f, true, blue))
    c.drawLine(270f, 548f, 396f, 548f, paint(1f, false, Color.parseColor("#cbd5e1")))
    c.drawText("Authorised signature", 284f, 564f, paint(10f, false, Color.parseColor("#526077")))
    doc.finishPage(page)

    val f = File(ctx.cacheDir, "receipt-${r.number}.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Receipt ${r.number} — ${AppSettings.academyName}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
