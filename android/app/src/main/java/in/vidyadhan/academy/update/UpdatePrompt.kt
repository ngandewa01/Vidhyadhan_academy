package `in`.vidyadhan.academy.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.students.today
import `in`.vidyadhan.academy.ui.Brand
import kotlinx.coroutines.launch

/** Once a day: looks for a newer version and, if there is one, offers to download and install it. */
@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val day = today()
        if (AppSettings.updateCheckedOn == day || !AppSettings.updateUrl.startsWith("https://")) return@LaunchedEffect
        runCatching { Updater.check(AppSettings.updateUrl) }.onSuccess { u ->
            AppSettings.updateCheckedOn = day
            val (code, _) = Updater.currentVersion(ctx)
            if (u.versionCode > code && (u.versionCode != AppSettings.updateSkipped || u.critical)) info = u
        }
    }
    val u = info ?: return
    val busy = progress in 0..99
    AlertDialog(onDismissRequest = { if (!busy) info = null }, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
        title = { Text("New version available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${u.versionName}", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                if (u.notes.isNotBlank()) Text(u.notes, style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
                Text("Your records stay as they are.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
                if (busy) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth(), color = Brand.Blue600, trackColor = Brand.Blue100)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.Red600) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                error = null; progress = 0
                scope.launch {
                    runCatching { Updater.download(ctx, u) { progress = it } }
                        .onSuccess {
                            Updater.install(ctx, it)
                            if (ctx.packageManager.canRequestPackageInstalls()) { progress = 100; info = null }
                            else { progress = -1; error = "Allow “Install unknown apps” for this app in the screen that opened, then tap Update now again." }
                        }
                        .onFailure { progress = -1; error = "Download failed: ${it.message}" }
                }
            }) { Text(if (busy) "Downloading $progress%" else "Update now", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { if (!busy) TextButton({ AppSettings.updateSkipped = u.versionCode; info = null }) { Text("Later") } })
}
