package `in`.vidyadhan.academy.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** What the update-info file (a small JSON file the owner hosts) looks like. */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
    val critical: Boolean = false,
)

object Updater {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun currentVersion(ctx: Context): Pair<Long, String> {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return p.longVersionCode to (p.versionName ?: "")
    }

    suspend fun check(url: String): UpdateInfo = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            require(r.isSuccessful) { "Server said ${r.code}" }
            json.decodeFromString<UpdateInfo>(r.body.string())
        }
    }

    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, "update.apk")
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            require(r.isSuccessful) { "Server said ${r.code}" }
            val total = r.body.contentLength()
            r.body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        }
        out
    }

    /** Opens Android's installer. The owner taps Install. */
    fun install(ctx: Context, apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
