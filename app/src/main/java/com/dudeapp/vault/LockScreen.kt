package com.dudeapp.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

@Composable
private fun PinShell(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
fun CreatePinScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    PinShell {
        Icon(Icons.Outlined.Lock, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(
            if (step == 0) "Create your PIN" else "Confirm your PIN",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (step == 0) "P Vault keeps your photos, PDFs and notes behind this PIN." else "Enter the same PIN again.",
            color = TextSoft,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(
            value = if (step == 0) pin else confirm,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).take(6)
                if (step == 0) pin = digits else confirm = digits
                error = null
            },
            label = { Text("4-6 digits") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                when {
                    pin.length < 4 -> error = "Use at least 4 digits"
                    step == 0 -> {
                        step = 1
                        confirm = ""
                        error = null
                    }

                    confirm == pin -> {
                        Auth.setPin(context, pin)
                        onDone()
                    }

                    else -> {
                        error = "PINs do not match"
                        confirm = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(if (step == 0) "Continue" else "Save PIN", fontWeight = FontWeight.SemiBold)
        }
        if (step == 1) {
            TextButton(onClick = { step = 0; error = null }) { Text("Back") }
        }
    }
}

@Composable
fun LockChoiceScreen(onEnable: () -> Unit, onSkip: () -> Unit) {
    PinShell {
        Icon(Icons.Outlined.Lock, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(
            "Protect P Vault?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Add a PIN so only you can open your vault. You can turn it off later in Settings.",
            color = TextSoft,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Set a PIN", fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onSkip) {
            Text("Skip for now")
        }
    }
}

@Composable
fun LockScreen(autoPromptKey: Int, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutUntil by remember { mutableLongStateOf(Auth.lockoutUntil(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val canUseBiometrics = remember(autoPromptKey) {
        SettingsStore(context).load().biometricUnlock && Auth.biometricsAvailable(context)
    }
    val lockoutSeconds = ((lockoutUntil - now + 999) / 1000).coerceAtLeast(0).toInt()
    var biometricPromptOpen by remember { mutableStateOf(false) }
    var lastPromptAt by remember { mutableLongStateOf(0L) }

    fun promptBiometric(force: Boolean = false) {
        val host = activity ?: return
        if (biometricPromptOpen) return
        val currentTime = System.currentTimeMillis()
        if (!force && currentTime - lastPromptAt < 1_500L) return
        lastPromptAt = currentTime
        biometricPromptOpen = true
        val started = runCatching {
            Auth.promptBiometric(
                host,
                onSuccess = {
                    biometricPromptOpen = false
                    onUnlocked()
                },
                onFail = { message ->
                    biometricPromptOpen = false
                    if (message.isNotBlank()) error = message
                },
            )
        }.isSuccess
        if (!started) biometricPromptOpen = false
    }

    LaunchedEffect(autoPromptKey) {
        if (autoPromptKey > 0 && canUseBiometrics) promptBiometric()
    }

    LaunchedEffect(lockoutUntil) {
        while (lockoutUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }

    PinShell {
        Icon(Icons.Outlined.Lock, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            if (canUseBiometrics && activity != null) {
                "Use your fingerprint, or enter your PIN."
            } else {
                "Enter your PIN to open the vault."
            },
            color = TextSoft,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter(Char::isDigit).take(6)
                error = null
            },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        val message = if (lockoutSeconds > 0) {
            "Too many attempts. Try again in ${lockoutSeconds}s."
        } else {
            error
        }
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (Auth.checkPin(context, pin)) {
                    onUnlocked()
                } else {
                    lockoutUntil = Auth.registerFailure(context)
                    now = System.currentTimeMillis()
                    error = "Wrong PIN"
                    pin = ""
                }
            },
            enabled = pin.length >= 4 && lockoutSeconds == 0,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Unlock", fontWeight = FontWeight.SemiBold)
        }
        if (canUseBiometrics && activity != null) {
            TextButton(onClick = { promptBiometric(force = true) }) {
                Icon(Icons.Outlined.Fingerprint, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use fingerprint")
            }
        }
    }
}
