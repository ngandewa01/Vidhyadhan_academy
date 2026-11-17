package `in`.vidyadhan.academy.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.R
import `in`.vidyadhan.academy.students.asDate
import java.io.File

/** A printable A4 admission form with logo, photo, details, an optional table and signature lines. */
fun shareAdmissionForm(
    ctx: Context, title: String, number: String, name: String, photo: String,
    details: List<Pair<String, String>>, tableTitle: String? = null, table: List<List<String>> = emptyList(),
) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val c = page.canvas
    val blue = Color.parseColor("#0b5583"); val saffron = Color.parseColor("#ea580c"); val ink = Color.parseColor("#1e293b"); val grey = Color.parseColor("#526077")
    fun paint(size: Float, bold: Boolean = false, color: Int = ink) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }

    c.drawRect(0f, 0f, 595f, 118f, Paint().apply { color = blue })
    c.drawRect(0f, 118f, 595f, 123f, Paint().apply { color = saffron })
    BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)?.let { logo ->
        c.drawCircle(76f, 59f, 42f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.drawBitmap(logo, null, RectF(40f, 23f, 112f, 95f), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    c.drawText(AppSettings.academyName, 132f, 54f, paint(24f, true, Color.WHITE))
    val sub = listOf(AppSettings.academyAddress, AppSettings.academyPhone).filter { it.isNotBlank() }.joinToString("  |  ")
    if (sub.isNotBlank()) c.drawText(sub.take(80), 132f, 80f, paint(10.5f, false, Color.parseColor("#d6ecf7")))

    c.drawText(title, 36f, 160f, paint(17f, true, blue))
    c.drawText("Form no: $number", 36f, 182f, paint(11f, false, grey))
    c.drawText("Date: ${java.time.LocalDate.now().toEpochDay().asDate()}", 36f, 198f, paint(11f, false, grey))

    // photo box
    val box = RectF(447f, 140f, 559f, 270f)
    c.drawRect(box, Paint().apply { style = Paint.Style.STROKE; color = Color.parseColor("#cbd5e1"); strokeWidth = 1.2f })
    val bmp = Photos.load(ctx, photo, 600)
    if (bmp != null) {
        val src = if (bmp.width.toFloat() / bmp.height > box.width() / box.height()) {
            val w = (bmp.height * box.width() / box.height()).toInt(); Rect((bmp.width - w) / 2, 0, (bmp.width + w) / 2, bmp.height)
        } else { val h = (bmp.width * box.height() / box.width()).toInt(); Rect(0, (bmp.height - h) / 2, bmp.width, (bmp.height + h) / 2) }
        c.drawBitmap(bmp, src, box, Paint(Paint.FILTER_BITMAP_FLAG))
    } else c.drawText("Photo", box.centerX() - 16f, box.centerY(), paint(11f, false, grey))

    var y = 236f
    fun row(k: String, v: String) {
        c.drawText(k, 36f, y, paint(11.5f, false, grey))
        c.drawText(v.take(60), 170f, y, paint(12.5f, true))
        c.drawLine(170f, y + 5f, if (y < 280f) 430f else 559f, y + 5f, paint(1f, false, Color.parseColor("#e2e8f0")))
        y += 27f
    }
    row("Full name", name)
    details.forEach { (k, v) -> row(k, v) }

    if (tableTitle != null && table.isNotEmpty()) {
        y += 12f
        c.drawText(tableTitle, 36f, y, paint(13.5f, true, blue)); y += 18f
        val cols = floatArrayOf(36f, 120f, 300f)
        c.drawRect(36f, y - 13f, 559f, y + 7f, Paint().apply { color = Color.parseColor("#eef7fc") })
        table.first().forEachIndexed { i, h -> c.drawText(h, cols[i] + 6f, y, paint(11f, true)) }
        y += 22f
        table.drop(1).forEach { r -> r.forEachIndexed { i, v -> c.drawText(v, cols[i] + 6f, y, paint(11f)) }; y += 19f }
    }

    y = maxOf(y + 20f, 640f)
    c.drawText("Declaration / प्रतिज्ञापत्र", 36f, y, paint(12.5f, true, blue)); y += 18f
    listOf(
        "I confirm the details above are correct and I will follow the rules of the academy.",
        "Fees once paid are not refundable unless the academy decides otherwise.",
        "वरील माहिती खरी आहे आणि मी अकॅडमीचे नियम पाळेन.",
    ).forEach { c.drawText(it, 36f, y, paint(10.5f, false, grey)); y += 15f }

    val sy = 800f
    listOf(36f to "Student signature", 226f to "Parent / guardian", 416f to "For ${AppSettings.academyName}".take(28)).forEach { (x, label) ->
        c.drawLine(x, sy - 14f, x + 143f, sy - 14f, paint(1f, false, Color.parseColor("#94a3b8")))
        c.drawText(label, x, sy, paint(10f, false, grey))
    }
    doc.finishPage(page)
    val f = File(ctx.cacheDir, "admission-$number.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share or print the form").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
