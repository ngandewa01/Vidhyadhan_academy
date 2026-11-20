package `in`.vidyadhan.academy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.vidyadhan.academy.students.rupees
import `in`.vidyadhan.academy.telegram.TelegramBot
import kotlinx.coroutines.launch

/**
 * What to do with one payment's receipt: WhatsApp (straight to the person's chat), Telegram (through the bot),
 * Share (other apps), View, Delete. [tgKey] is the person's bot key (s12 / m3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptActions(r: ReceiptData, mobile: String, tgKey: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetTitle("Receipt ${rupees(r.amount)}", "No. ${r.number} · ${r.name}" + if (mobile.isNotBlank()) " · +91 $mobile" else "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("WhatsApp", Icons.AutoMirrored.Rounded.Chat, Color(0xFFDCFCE7), Color(0xFF15803D), Modifier.weight(1f)) { whatsappReceipt(ctx, r, mobile); onDismiss() }
                Action("Telegram", Icons.AutoMirrored.Rounded.Send, Color(0xFFE0F2FE), Color(0xFF0369A1), Modifier.weight(1f)) {
                    when {
                        !TelegramBot.connected -> android.widget.Toast.makeText(ctx, "Connect your Telegram bot first: Bot & More", android.widget.Toast.LENGTH_LONG).show()
                        !TelegramBot.isLinked(tgKey) -> android.widget.Toast.makeText(ctx, "${r.name} is not connected on Telegram yet. Use WhatsApp, or send them the join link.", android.widget.Toast.LENGTH_LONG).show()
                        else -> scope.launch {
                            val ok = TelegramBot.sendReceipt(tgKey, receiptPdf(ctx, r), "🧾 Receipt <b>${TelegramBot.esc(r.number)}</b> · ${rupees(r.amount)}\n— ${TelegramBot.esc(`in`.vidyadhan.academy.AppSettings.academyName)}")
                            android.widget.Toast.makeText(ctx, if (ok) "Sent on Telegram ✓" else "Could not send on Telegram", android.widget.Toast.LENGTH_SHORT).show()
                            if (ok) onDismiss()
                        }
                    }
                }
                Action("Share", Icons.Rounded.Share, Brand.Slate50, Brand.Slate600, Modifier.weight(1f)) { shareReceipt(ctx, r); onDismiss() }
                Action("Delete", Icons.Rounded.DeleteOutline, Brand.Red50, Brand.Red600, Modifier.weight(1f)) { onDismiss(); onDelete() }
            }
            SoftButton("View receipt", { viewReceipt(ctx, r) }, Modifier.fillMaxWidth(), color = Brand.Blue700, icon = Icons.Rounded.Visibility)
        }
    }
}

@Composable
private fun Action(label: String, icon: ImageVector, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = fg, textAlign = TextAlign.Center, maxLines = 1)
    }
}
