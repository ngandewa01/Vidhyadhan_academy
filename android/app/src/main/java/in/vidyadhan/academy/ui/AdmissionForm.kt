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

/** What the admission form shows. Empty details print as lines to fill in by hand. */
data class AdmissionDoc(
    val title: String, val number: String, val date: Long,
    val name: String, val mobile: String, val photo: String,
    /** Pills under the name: course, batch, joining date (or seat, locker). */
    val tags: List<String>,
    val dob: Long?, val aadhaar: String, val guardian: String, val guardianMobile: String, val address: String,
    /** e.g. "FEES · TOTAL ₹25,000". */
    val feeTitle: String,
    /** Each fee line: what, amount, status ("PAID", "₹4,000 DUE", "HELD" or empty). */
    val feeRows: List<Triple<String, String, String>>,
)

/** Makes the A4 admission form and opens the share menu (print, WhatsApp, Drive…). */
fun shareAdmissionForm(ctx: Context, d: AdmissionDoc) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    drawAdmissionForm(ctx, page.canvas, d)
    doc.finishPage(page)
    val f = File(ctx.cacheDir, "admission-${d.number}.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share or print the form").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Draws the form on a 595 × 842 canvas (the PDF page, or a picture in tests). */
fun drawAdmissionForm(ctx: Context, c: Canvas, d: AdmissionDoc) {
    val W = 595f
    val teal = Color.parseColor("#0F4C5C"); val teal1 = Color.parseColor("#E0F2F5"); val marigold = Color.parseColor("#E07A2B"); val mari1 = Color.parseColor("#FDF0E4")
    val ink = Color.parseColor("#0F172A"); val grey = Color.parseColor("#64748B"); val light = Color.parseColor("#94A3B8"); val line = Color.parseColor("#E2E8F0")
    val green = Color.parseColor("#15803D"); val green1 = Color.parseColor("#ECFDF3"); val amber = Color.parseColor("#B45309"); val soft = Color.parseColor("#F8FAFC")
    fun paint(size: Float, bold: Boolean = false, color: Int = ink, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = align }
    fun fill(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
    fun stroke(col: Int, w: Float = 1f, dash: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = col; strokeWidth = w; if (dash) pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
    }
    fun fit(p: Paint, text: String, max: Float): Paint { while (p.measureText(text) > max && p.textSize > 6f) p.textSize -= 0.5f; return p }
    val pale = Color.parseColor("#CCE7EE")

    // letterhead band
    c.drawRect(0f, 0f, W, 140f, Paint().apply { shader = LinearGradient(0f, 0f, W, 140f, Color.parseColor("#093541"), Color.parseColor("#145E70"), Shader.TileMode.CLAMP) })
    c.drawCircle(64f, 56f, 30f, fill(Color.WHITE))
    runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.logo) }.getOrNull()?.let { c.drawBitmap(it, null, RectF(38f, 30f, 90f, 82f), Paint(Paint.FILTER_BITMAP_FLAG)) }
    c.drawText(AppSettings.academyName, 108f, 50f, fit(paint(21f, true, Color.WHITE), AppSettings.academyName, W - 290f))
    val tag = (AppSettings.courseList + "Abhyasika Kendra").filter { it.isNotBlank() }.joinToString(" · ")
    c.drawText(tag, 108f, 68f, fit(paint(9.5f, false, pale), tag, W - 290f))
    val contact = listOf(AppSettings.academyAddress, AppSettings.academyPhone).filter { it.isNotBlank() }.joinToString(" · ")
    if (contact.isNotBlank()) c.drawText(contact, 108f, 83f, fit(paint(9.5f, false, pale), contact, W - 290f))
    c.drawText(d.title, W - 36f, 44f, paint(9.5f, true, pale, Paint.Align.RIGHT).apply { letterSpacing = 0.08f })
    c.drawText(d.number, W - 36f, 64f, paint(15f, true, Color.WHITE, Paint.Align.RIGHT))
    c.drawText(d.date.asDate(), W - 36f, 80f, paint(10f, false, pale, Paint.Align.RIGHT))

    // the student card
    val card = RectF(36f, 104f, W - 36f, 230f)
    c.drawRoundRect(RectF(card.left, card.top + 3f, card.right, card.bottom + 3f), 18f, 18f, fill(Color.argb(22, 15, 23, 42)))
    c.drawRoundRect(card, 18f, 18f, fill(Color.WHITE))
    val ph = RectF(52f, 118f, 136f, 216f)
    val bmp = Photos.load(ctx, d.photo, 600)
    if (bmp != null) {
        c.save(); c.clipPath(Path().apply { addRoundRect(ph, 12f, 12f, Path.Direction.CW) })
        val src = if (bmp.width.toFloat() / bmp.height > ph.width() / ph.height()) {
            val w = (bmp.height * ph.width() / ph.height()).toInt(); Rect((bmp.width - w) / 2, 0, (bmp.width + w) / 2, bmp.height)
        } else { val h = (bmp.width * ph.height() / ph.width()).toInt(); Rect(0, (bmp.height - h) / 2, bmp.width, (bmp.height + h) / 2) }
        c.drawBitmap(bmp, src, ph, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
    } else {
        c.drawRoundRect(ph, 12f, 12f, fill(teal1))
        c.drawRoundRect(ph, 12f, 12f, stroke(Color.parseColor("#9CC5CF"), 1f, dash = true))
        val ini = d.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
        c.drawText(ini, ph.centerX(), ph.centerY() + 6f, paint(26f, true, teal, Paint.Align.CENTER))
        c.drawText("Photo", ph.centerX(), ph.centerY() + 24f, paint(8.5f, false, grey, Paint.Align.CENTER))
    }
    c.drawText(d.name, 154f, 150f, fit(paint(21f, true, ink), d.name, card.right - 170f))
    if (d.mobile.isNotBlank()) c.drawText("+91 ${d.mobile}", 154f, 170f, paint(11.5f, false, grey))
    var px = 154f
    d.tags.forEachIndexed { i, t ->
        val p = paint(8.5f, true, when (i) { 0 -> teal; 1 -> amber; else -> grey }).apply { letterSpacing = 0.05f }
        val text = t.uppercase(); val w = p.measureText(text) + 18f
        if (px + w > card.right - 12f) return@forEachIndexed
        c.drawRoundRect(RectF(px, 184f, px + w, 202f), 9f, 9f, fill(when (i) { 0 -> teal1; 1 -> mari1; else -> soft }))
        c.drawText(text, px + 9f, 196.5f, p); px += w + 6f
    }

    fun section(title: String, y: Float) {
        val p = paint(9.5f, true, teal).apply { letterSpacing = 0.08f }
        c.drawText(title, 36f, y, p)
        c.drawLine(36f + p.measureText(title) + 10f, y - 3f, W - 36f, y - 3f, stroke(line))
    }
    fun box(x: Float, top: Float, w: Float, h: Float, label: String, value: String) {
        c.drawRoundRect(RectF(x, top, x + w, top + h), 10f, 10f, stroke(line))
        c.drawText(label, x + 10f, top + 14f, paint(7.5f, true, grey).apply { letterSpacing = 0.04f })
        if (value.isBlank()) c.drawLine(x + 10f, top + h - 10f, x + w - 10f, top + h - 10f, stroke(light, 0.8f, dash = true))
        else c.drawText(value, x + 10f, top + 31f, fit(paint(11.5f, true, ink), value, w - 20f))
    }

    // personal
    var y = 262f
    section("PERSONAL", y); y += 12f
    val half = (W - 72f - 12f) / 2
    box(36f, y, half, 42f, "DATE OF BIRTH", d.dob?.asDate() ?: "")
    box(36f + half + 12f, y, half, 42f, "AADHAAR", d.aadhaar.chunked(4).joinToString(" "))
    y += 52f
    box(36f, y, half, 42f, "PARENT / GUARDIAN", d.guardian)
    box(36f + half + 12f, y, half, 42f, "GUARDIAN MOBILE", if (d.guardianMobile.isBlank()) "" else "+91 ${d.guardianMobile}")
    y += 52f
    val addr = RectF(36f, y, W - 36f, y + 56f)
    c.drawRoundRect(addr, 10f, 10f, stroke(line))
    c.drawText("ADDRESS", 46f, y + 14f, paint(7.5f, true, grey).apply { letterSpacing = 0.04f })
    if (d.address.isBlank()) {
        c.drawLine(46f, y + 32f, W - 46f, y + 32f, stroke(light, 0.8f, dash = true))
        c.drawLine(46f, y + 48f, W - 46f, y + 48f, stroke(light, 0.8f, dash = true))
    } else {
        val words = d.address.replace("\n", ", ").split(" ")
        val p = paint(11f, true, ink); var l = ""; var ly = y + 31f; var lines = 0
        for (w in words) {
            val tryL = if (l.isEmpty()) w else "$l $w"
            if (p.measureText(tryL) > W - 92f && l.isNotEmpty()) { c.drawText(l, 46f, ly, p); ly += 15f; lines++; l = w; if (lines == 2) break } else l = tryL
        }
        if (lines < 2 && l.isNotEmpty()) c.drawText(l, 46f, ly, p)
    }
    y += 66f

    // fees, as a timeline
    y += 18f
    section(d.feeTitle, y); y += 14f
    val shown = d.feeRows.take(7)
    if (shown.size > 1) c.drawLine(44f, y + 8f, 44f, y + 8f + (shown.size - 1) * 24f, stroke(line, 2f))
    shown.forEach { (what, amount, status) ->
        val col = when { status == "PAID" -> green; status.contains("DUE") -> marigold; status == "HELD" -> teal; else -> light }
        c.drawCircle(44f, y + 8f, 5f, fill(col))
        c.drawCircle(44f, y + 8f, 5f, stroke(Color.WHITE, 1.5f))
        c.drawText(what, 58f, y + 12f, fit(paint(11f, false, ink), what, W - 330f))
        c.drawText(amount, W - 150f, y + 12f, paint(11f, true, ink, Paint.Align.RIGHT))
        if (status.isNotBlank()) {
            val p = paint(8f, true, when (col) { green -> green; marigold -> amber; teal -> teal; else -> grey }).apply { letterSpacing = 0.05f }
            val w = p.measureText(status) + 14f
            c.drawRoundRect(RectF(W - 36f - w, y + 1f, W - 36f, y + 16f), 8f, 8f, fill(when (col) { green -> green1; marigold -> mari1; teal -> teal1; else -> soft }))
            c.drawText(status, W - 36f - w + 7f, y + 11.5f, p)
        }
        y += 24f
    }
    if (d.feeRows.size > shown.size) { c.drawText("+ ${d.feeRows.size - shown.size} more", 58f, y + 8f, paint(9.5f, false, grey)); y += 16f }

    // declaration
    y += 16f
    section("DECLARATION", y); y += 16f
    c.drawText("I confirm the details above are correct and I will follow the rules of the academy.", 36f, y, paint(10f, false, grey)); y += 14f
    c.drawText("Fees once paid are not refundable or transferable.", 36f, y, paint(10f, false, grey))

    // signatures and office box
    val sy = maxOf(y + 44f, 742f)
    val sw = (W - 72f) / 3
    listOf("Student", "Parent / guardian", "For the academy").forEachIndexed { i, label ->
        val x0 = 36f + i * sw + 8f; val x1 = 36f + (i + 1) * sw - 8f
        c.drawLine(x0, sy, x1, sy, stroke(light))
        c.drawText(label, (x0 + x1) / 2, sy + 13f, paint(9.5f, false, grey, Paint.Align.CENTER))
    }
    val ob = RectF(36f, sy + 22f, W - 36f, sy + 92f)
    c.drawRoundRect(ob, 12f, 12f, stroke(line, 1.5f, dash = true))
    c.drawText("FOR OFFICE USE", 48f, ob.top + 15f, paint(8f, true, grey).apply { letterSpacing = 0.06f })
    val ow = (ob.width() - 36f) / 2
    box(48f, ob.top + 22f, ow, 40f, "DOCUMENTS CHECKED", "")
    box(48f + ow + 12f, ob.top + 22f, ow, 40f, "RECEIVED BY", "")
}
