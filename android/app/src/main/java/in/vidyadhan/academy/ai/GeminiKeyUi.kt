package `in`.vidyadhan.academy.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch

/** Masked key, for example AIzaSy•••••••••• 4f2a. */
fun maskKey(k: String) = if (k.length > 12) k.take(7) + "••••••••••" + k.takeLast(4) else "••••••••"

/**
 * The Gemini key. It is locked: tap the row 10 times quickly to edit it. Once it works it is saved on this phone.
 */
@Composable
fun GeminiKeyCard(onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = AppSettings.geminiKey
    val tap = remember { TapUnlock() }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var draft by remember(saved) { mutableStateOf(saved) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 4.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !unlocked) {
                if (tap.tap()) { unlocked = true; hint = null; msg = null; tap.reset() } else hint = tap.hint()
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (unlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null,
                tint = if (unlocked) Brand.Saffron600 else Brand.Green600, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Gemini key", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                Text(if (saved.isBlank()) "Not set yet" else maskKey(saved) + if (AppSettings.geminiFromFirebase) " · from Firebase" else "",
                    style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            }
            hint?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Brand.Saffron700) }
        }
        if (unlocked) {
            Text("Paste the key from aistudio.google.com. It is checked before it is saved.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            Field(draft, { draft = it.trim() }, "Gemini key (starts with AIza)", Icons.Rounded.Key)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (busy) "Checking…" else "Check & save", {
                    val k = draft.trim()
                    if (!k.startsWith("AIza")) { ok = false; msg = "That is not a Gemini key. It starts with AIza"; return@PrimaryButton }
                    busy = true; msg = null
                    scope.launch {
                        runCatching { Gemini.test(k) }
                            .onSuccess { model ->
                                AppSettings.geminiKey = k; AppSettings.geminiModel = model; AppSettings.geminiFromFirebase = false
                                ok = true; unlocked = false; msg = "Key works ✓ · saved and locked"
                                onSaved()
                            }
                            .onFailure { ok = false; msg = it.message ?: "That key did not work" }
                        busy = false
                    }
                }, Modifier.weight(1f), enabled = draft.isNotBlank() && !busy, icon = Icons.Rounded.Check)
                SoftButton("Get a free key", {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
            SoftButton("Lock again", { unlocked = false; msg = null }, Modifier.fillMaxWidth(), color = Brand.Slate600, icon = Icons.Rounded.Lock)
        }
        msg?.let { StatusChip(it, if (ok) Tone.Good else Tone.Bad) }
    }
}
