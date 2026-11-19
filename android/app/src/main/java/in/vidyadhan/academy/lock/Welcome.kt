package `in`.vidyadhan.academy.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.cloud.Cloud
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch

/** Shown the first time the app opens: sign in with Google (records kept in the cloud) or use this phone only. */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val st = Cloud.state
    LaunchedEffect(st) { if (st == Cloud.State.On) { AppSettings.welcomed = true; onDone() } }
    Column(Modifier.fillMaxSize().heroBackground().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(36.dp))
        LogoBadge(96.dp)
        Text(AppSettings.academyName, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
        Text("Academy & Abhyasika · students, fees, seats and enquiries in one place", style = MaterialTheme.typography.bodyMedium, color = Brand.Blue100, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (st) {
                Cloud.State.Choosing -> {
                    Text("This phone and your cloud both have records. Which should be kept?", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                    OptionRow(Icons.Rounded.CloudDownload, Brand.Blue600, "Use the cloud's records", "This phone's records are replaced by the ones in the cloud") { scope.launch { Cloud.finish(ctx, keepCloud = true) } }
                    OptionRow(Icons.Rounded.CloudUpload, Brand.Saffron700, "Keep this phone's records", "The cloud is replaced by this phone's records") { scope.launch { Cloud.finish(ctx, keepCloud = false) } }
                }
                Cloud.State.Working -> {
                    Text(Cloud.message.ifBlank { "Signing in…" }, style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand.Blue600, trackColor = Brand.Blue100)
                }
                else -> {
                    Text("Sign in", style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                    Text("Use the academy's Gmail. Your records are kept safely in your cloud and are the same on every phone you sign in on. The app still works without internet.",
                        style = MaterialTheme.typography.bodySmall, color = Brand.Slate600)
                    if (st != Cloud.State.NotSetUp)
                        PrimaryButton("Sign in with Google", { error = null; scope.launch { error = Cloud.signIn(ctx) } }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Login)
                    if (st == Cloud.State.Error) StatusChip(Cloud.message, Tone.Bad)
                    error?.let { StatusChip(it, Tone.Bad) }
                }
            }
        }
        if (st != Cloud.State.Working) Row(
            Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                .clickable { AppSettings.welcomed = true; onDone() }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Use on this phone only", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Text("You can sign in later from Settings → Cloud sync.", style = MaterialTheme.typography.bodySmall, color = Brand.Blue200, textAlign = TextAlign.Center)
    }
}
