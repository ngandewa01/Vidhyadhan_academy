package `in`.vidyadhan.academy.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import `in`.vidyadhan.academy.R

@Composable
fun LockScreen(activity: FragmentActivity, store: LockStore, onUnlocked: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val creating = !store.hasPin
    val confirming = creating && first.isNotEmpty()

    fun tryBiometric() {
        val can = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) return
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = onUnlocked()
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Vidyadhan Academy")
                .setSubtitle("Use fingerprint to open")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }

    LaunchedEffect(Unit) { if (store.hasPin && store.biometricOn) tryBiometric() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Vidyadhan Academy", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                confirming -> "Enter the PIN again to confirm"
                creating -> "Create a 4-digit PIN"
                else -> "Enter your PIN"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { v ->
                if (v.length <= 4 && v.all { it.isDigit() }) {
                    pin = v; error = null
                    if (v.length == 4) when {
                        !creating -> if (store.verify(v)) onUnlocked() else { error = "Wrong PIN"; pin = "" }
                        !confirming -> { first = v; pin = "" }
                        v == first -> { store.setPin(v); onUnlocked() }
                        else -> { error = "PINs did not match. Start again."; first = ""; pin = "" }
                    }
                }
            },
            singleLine = true,
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        if (!creating && store.biometricOn) {
            TextButton(onClick = { tryBiometric() }) { Text("Use fingerprint") }
        }
    }
}
