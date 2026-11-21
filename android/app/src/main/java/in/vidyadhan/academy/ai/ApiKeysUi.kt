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

/** The API keys section: the main AI, an optional backup, and a locked key row for each chosen AI. */
@Composable
fun ApiKeysCard(onSaved: () -> Unit) {
    var primary by remember { mutableStateOf(AiRouter.primary()) }
    var backup by remember { mutableStateOf(AiRouter.backup()) }
    Column(Modifier.fillMaxWidth().softCard(RoundedCornerShape(22.dp), 4.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("API keys", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
        Text("The main AI is used first. If it reaches its limit, the backup AI is used.", style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
        ProviderDropdown("Main AI", primary, allowNone = false) { p ->
            if (p != null) { primary = p; AppSettings.aiPrimary = p.name }
        }
        ProviderDropdown("Backup AI", backup, allowNone = true) { p ->
            backup = p; AppSettings.aiBackup = p?.name ?: "NONE"
        }
        ApiKeyRow(primary, onSaved)
        backup?.takeIf { it != primary }?.let { ApiKeyRow(it, onSaved) }
    }
}

/** Dropdown of the AIs. AIs that are not connected yet are shown but cannot be chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(label: String, selected: AiProvider?, allowNone: Boolean, onPick: (AiProvider?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.label ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("None") }, onClick = { onPick(null); open = false })
            AiProvider.entries.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.label, style = MaterialTheme.typography.titleSmall)
                            Text(p.note, style = MaterialTheme.typography.labelSmall, color = Brand.Slate500)
                        }
                    },
                    onClick = { onPick(p); open = false },
                    enabled = p.connected,
                )
            }
        }
    }
}

/**
 * One AI's key. It is locked: tap the row 10 times quickly to edit. Once it works, it is saved on this phone.
 */
@Composable
fun ApiKeyRow(p: AiProvider, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = AiRouter.keyOf(p)
    val tap = remember(p) { TapUnlock() }
    var unlocked by rememberSaveable(p) { mutableStateOf(false) }
    var hint by remember(p) { mutableStateOf<String?>(null) }
    var draft by remember(p, saved) { mutableStateOf(saved) }
    var busy by remember(p) { mutableStateOf(false) }
    var msg by remember(p) { mutableStateOf<String?>(null) }
    var ok by remember(p) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Text("${p.label} key", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                Text(if (saved.isBlank()) "Not set yet" else maskKey(saved), style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            }
            hint?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Brand.Saffron700) }
        }
        if (unlocked) {
            Text("Paste the key from ${p.keyLink.substringAfter("//").substringBefore("/")}. It is checked with a test search before it is saved.",
                style = MaterialTheme.typography.bodySmall, color = Brand.Slate500)
            Field(draft, { draft = it.trim() }, "${p.label} key (starts with ${p.keyPrefix})", Icons.Rounded.Key)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (busy) "Checking…" else "Check & save", {
                    val k = draft.trim()
                    if (!k.startsWith(p.keyPrefix)) { ok = false; msg = "That is not a ${p.label} key. It starts with ${p.keyPrefix}"; return@PrimaryButton }
                    busy = true; msg = null
                    scope.launch {
                        runCatching { AiRouter.test(p, k) }
                            .onSuccess { pages ->
                                AiRouter.saveKey(p, k); ok = true; unlocked = false
                                msg = if (pages > 0) "Key works ✓ · search works ✓ · $pages pages found"
                                else "Key works ✓ · but no web pages came back. Check web search is allowed for this key."
                                onSaved()
                            }
                            .onFailure { ok = false; msg = it.message ?: "That key did not work" }
                        busy = false
                    }
                }, Modifier.weight(1f), enabled = draft.isNotBlank() && !busy, icon = Icons.Rounded.Check)
                SoftButton("Get a key", {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyLink)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
            SoftButton("Lock again", { unlocked = false; msg = null }, Modifier.fillMaxWidth(), color = Brand.Slate600, icon = Icons.Rounded.Lock)
        }
        msg?.let { StatusChip(it, if (ok) Tone.Good else Tone.Bad) }
    }
}
