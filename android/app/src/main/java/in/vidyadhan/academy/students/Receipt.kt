package `in`.vidyadhan.academy.students

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.Installment
import `in`.vidyadhan.academy.data.Student
import java.io.File

/** Draws a one-page A5 fee receipt (system fonts, so Marathi names show correctly). */
fun shareReceipt(ctx: Context, s: Student, i: Installment, paidSoFar: Long) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(420, 595, 1).create())
    val c = page.canvas
    val blue = Color.parseColor("#0b5583")
    fun paint(size: Float, bold: Boolean = false, color: Int = Color.parseColor("#1e293b")) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }

    c.drawRect(0f, 0f, 420f, 90f, Paint().apply { this.color = blue })
    c.drawText(AppSettings.academyName, 24f, 42f, paint(22f, true, Color.WHITE))
    val sub = listOf(AppSettings.academyAddress, AppSettings.academyPhone).filter { it.isNotBlank() }.joinToString("  |  ")
    if (sub.isNotBlank()) c.drawText(sub, 24f, 66f, paint(11f, false, Color.parseColor("#d6ecf7")))

    c.drawText("FEE RECEIPT  /  फी पावती", 24f, 130f, paint(16f, true, blue))
    val no = "${AppSettings.receiptPrefix}-${i.id.toString().padStart(5, '0')}"
    c.drawText("Receipt No: $no", 24f, 156f, paint(12f))
    c.drawText("Date: ${(i.paidOn ?: today()).asDate()}", 270f, 156f, paint(12f))

    var y = 200f
    fun row(k: String, v: String) {
        c.drawText(k, 24f, y, paint(12f, false, Color.parseColor("#526077")))
        c.drawText(v, 160f, y, paint(13f, true)); y += 28f
    }
    row("Received from", s.name)
    row("Mobile", s.mobile)
    row("Course", s.course)
    row("Installment", "#${i.number}")
    row("Payment mode", i.mode.ifBlank { "—" })
    y += 8f
    c.drawRect(24f, y - 18f, 396f, y + 34f, Paint().apply { color = Color.parseColor("#eef7fc") })
    c.drawText("Amount received", 36f, y + 12f, paint(13f))
    c.drawText(rupees(i.amount), 250f, y + 14f, paint(20f, true, blue))
    y += 70f
    row("Total fee", rupees(s.totalFee))
    row("Paid so far", rupees(paidSoFar))
    row("Balance", rupees((s.totalFee - paidSoFar).coerceAtLeast(0)))
    c.drawText("Thank you.  धन्यवाद.", 24f, 520f, paint(13f, true, blue))
    c.drawText("Authorised signature", 270f, 560f, paint(10f, false, Color.parseColor("#526077")))
    doc.finishPage(page)

    val f = File(ctx.cacheDir, "receipt-$no.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Fee receipt $no — ${AppSettings.academyName}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
