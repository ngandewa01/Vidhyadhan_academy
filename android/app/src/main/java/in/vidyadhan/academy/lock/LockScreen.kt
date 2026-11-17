package `in`.vidyadhan.academy.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.ui.*
import kotlinx.coroutines.launch

@Composable
fun LockScreen(activity: FragmentActivity, store: LockStore, onUnlocked: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val creating = !store.hasPin
    val confirming = creating && first.isNotEmpty()
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val canBio = remember {
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun fail(msg: String) {
        error = msg
        scope.launch {
            shake.animateTo(0f, keyframes { durationMillis = 420; -22f at 60; 22f at 120; -16f at 180; 16f at 240; -8f at 300; 8f at 360 })
        }
    }

    fun tryBiometric() {
        if (!canBio) return
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = onUnlocked()
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(AppSettings.academyName).setSubtitle("Touch the sensor to open")
                .setNegativeButtonText("Use PIN").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build()
        )
    }

    fun complete(v: String) {
        when {
            !creating -> if (store.verify(v)) onUnlocked() else { pin = ""; fail("Wrong PIN, try again") }
            !confirming -> { first = v; pin = "" }
            v == first -> { store.setPin(v); onUnlocked() }
            else -> { first = ""; pin = ""; fail("PINs did not match. Start again.") }
        }
    }

    fun press(d: String) {
        if (pin.length >= 4) return
        error = null
        pin += d
        if (pin.length == 4) complete(pin)
    }

    LaunchedEffect(Unit) { if (store.hasPin && store.biometricOn) tryBiometric() }

    Column(
        Modifier.fillMaxSize().lockBackground().systemBarsPadding().padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.9f))
        Box(Modifier.size(118.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)), Alignment.Center) {
            Box(Modifier.size(98.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)), Alignment.Center) { LogoBadge(78.dp) }
        }
        Spacer(Modifier.height(18.dp))
        Text(AppSettings.academyName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("विद्याधन अकॅडमी", fontFamily = Mukta, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Brand.Blue200)
        Spacer(Modifier.height(30.dp))
        Text(
            when { confirming -> "Enter the same PIN again"; creating -> "Create a 4-digit PIN"; else -> "Enter your PIN" },
            style = MaterialTheme.typography.titleMedium, color = Color.White,
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(4) { i ->
                val filled = i < pin.length
                val c by animateColorAsState(if (error != null) Brand.Red500 else if (filled) Brand.Saffron400 else Color.Transparent, label = "dot")
                val s by animateFloatAsState(if (filled) 1.15f else 1f, label = "dots")
                Box(Modifier.size(16.dp).scale(s).clip(CircleShape).background(c).border(2.dp, if (filled || error != null) c else Color.White.copy(alpha = 0.55f), CircleShape))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(error ?: (if (creating) "You'll use this PIN every time you open the app" else " "),
            style = MaterialTheme.typography.bodySmall, color = if (error != null) Brand.Saffron200 else Brand.Blue200)
        Spacer(Modifier.weight(1f))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    r.forEach { k ->
                        when (k) {
                            "bio" -> if (!creating && store.biometricOn && canBio) KeyIcon(Icons.Rounded.Fingerprint, "Fingerprint") { tryBiometric() } else Spacer(Modifier.size(74.dp))
                            "del" -> KeyIcon(Icons.AutoMirrored.Rounded.Backspace, "Delete") { pin = pin.dropLast(1); error = null }
                            else -> Key(k) { press(k) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = Brand.Blue300, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Your data stays on this phone", style = MaterialTheme.typography.labelMedium, color = Brand.Blue300)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(74.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape).clickable(onClick = onClick),
        Alignment.Center,
    ) { Text(label, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 28.sp) }
}

@Composable
private fun KeyIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(74.dp).clip(CircleShape).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}
