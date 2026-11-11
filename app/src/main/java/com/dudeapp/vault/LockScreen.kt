package com.dudeapp.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

private const val MAX_PIN = 6

@Composable
private fun PinShell(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** The lock badge, title and one line of help shown at the top of every PIN screen. */
@Composable
private fun PinHeading(title: String, subtitle: String) {
    IconBadge(
        icon = Icons.Outlined.Lock,
        tint = MaterialTheme.colorScheme.primary,
        size = 72.dp,
        iconSize = 34.dp,
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        color = TextSoft,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )
}

/** Six dots that fill as digits are entered; they turn red while an error is showing. */
@Composable
private fun PinDots(length: Int, error: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(MAX_PIN) { index ->
            val filled = index < length
            val color = when {
                error -> MaterialTheme.colorScheme.error
                filled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (filled) color else MaterialTheme.colorScheme.background)
                    .border(1.5.dp, color, CircleShape),
            )
        }
    }
}

@Composable
private fun PinKey(
    enabled: Boolean,
    description: String?,
    onClick: () -> Unit,
    plain: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = Modifier
        .size(68.dp)
        .clip(CircleShape)
    val styled = if (plain) {
        base
    } else {
        base
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    }
    Box(
        styled.clickable(
            enabled = enabled,
            onClickLabel = description,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A phone-style number pad. The bottom-left slot is optional (used for fingerprint). */
@Composable
private fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onFingerprint: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { digit ->
                    PinKey(enabled = enabled, description = null, onClick = { onDigit(digit) }) {
                        Text(
                            text = digit.toString(),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (onFingerprint != null) {
                PinKey(
                    enabled = true,
                    description = "Use fingerprint",
                    onClick = onFingerprint,
                    plain = true,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = "Use fingerprint",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(68.dp))
            }
            PinKey(enabled = enabled, description = null, onClick = { onDigit('0') }) {
                Text(text = "0", fontSize = 26.sp, fontWeight = FontWeight.Medium)
            }
            PinKey(
                enabled = true,
                description = "Delete digit",
                onClick = onBackspace,
                plain = true,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete digit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
fun CreatePinScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val entered = if (step == 0) pin else confirm

    PinShell {
        PinHeading(
            title = if (step == 0) "Create your PIN" else "Confirm your PIN",
            subtitle = if (step == 0) {
                "Choose 4 to 6 digits. P Vault keeps your photos, PDFs and notes behind this PIN."
            } else {
                "Enter the same PIN again."
            },
        )
        Spacer(Modifier.height(26.dp))
        PinDots(length = entered.length, error = error != null)
        Spacer(Modifier.height(10.dp))
        Text(
            text = error ?: " ",
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        PinPad(
            enabled = true,
            onDigit = { digit ->
                if (entered.length < MAX_PIN) {
                    if (step == 0) pin += digit else confirm += digit
                }
                error = null
            },
            onBackspace = {
                if (step == 0) pin = pin.dropLast(1) else confirm = confirm.dropLast(1)
                error = null
            },
        )
        Spacer(Modifier.height(22.dp))
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
        PinHeading(
            title = "Protect P Vault?",
            subtitle = "Add a PIN so only you can open your vault. You can turn it off later in Settings.",
        )
        Spacer(Modifier.height(30.dp))
        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Set a PIN", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
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
        PinHeading(
            title = "Welcome back",
            subtitle = if (canUseBiometrics && activity != null) {
                "Use your fingerprint, or enter your PIN."
            } else {
                "Enter your PIN to open the vault."
            },
        )
        val message = if (lockoutSeconds > 0) {
            "Too many attempts. Try again in ${lockoutSeconds}s."
        } else {
            error
        }
        Spacer(Modifier.height(26.dp))
        PinDots(length = pin.length, error = message != null)
        Spacer(Modifier.height(10.dp))
        Text(
            text = message ?: " ",
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        PinPad(
            enabled = lockoutSeconds == 0,
            onDigit = { digit ->
                if (pin.length < MAX_PIN) pin += digit
                error = null
            },
            onBackspace = {
                pin = pin.dropLast(1)
                error = null
            },
            onFingerprint = if (canUseBiometrics && activity != null) {
                { promptBiometric(force = true) }
            } else {
                null
            },
        )
        Spacer(Modifier.height(22.dp))
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
    }
}
