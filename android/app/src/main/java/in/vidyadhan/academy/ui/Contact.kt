package `in`.vidyadhan.academy.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the phone dialler, WhatsApp or SMS with the message already typed. The owner presses send. */
object Contact {
    fun call(ctx: Context, mobile: String) = open(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobile")))
    fun whatsapp(ctx: Context, mobile: String, text: String = "") =
        open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$mobile" + if (text.isNotEmpty()) "?text=" + Uri.encode(text) else "")))
    fun sms(ctx: Context, mobile: String, text: String = "") =
        open(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$mobile")).apply { if (text.isNotEmpty()) putExtra("sms_body", text) })
    fun telegram(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val ok = runCatching { ctx.startActivity(Intent(send).setPackage("org.telegram.messenger")) }.isSuccess
        if (!ok) open(ctx, Intent.createChooser(send, "Send with"))
    }
    private fun open(ctx: Context, i: Intent) { runCatching { ctx.startActivity(i) } }
}
