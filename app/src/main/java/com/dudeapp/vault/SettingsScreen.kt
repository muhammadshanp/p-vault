package com.dudeapp.vault

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    theme: ThemePreset,
    settings: AppSettings,
    update: AppUpdate?,
    onTheme: (ThemePreset) -> Unit,
    onSettings: (AppSettings) -> Unit,
    onUpdate: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearData: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var changePinOpen by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    val biometricsAvailable = remember { Auth.biometricsAvailable(context) }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.6" }
    }

    // Settings fades and slides in when opened, and plays the reverse before closing.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) onBack()
    }

    BackHandler(onBack = close)

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 5 },
        exit = fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 5 },
    ) {
    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = close) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                SettingsGroup("Appearance") {
                    SettingsItem(
                        title = "Theme",
                        subtitle = "Choose how the app looks.",
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePreset.entries.forEach { preset ->
                                item(key = preset.name) {
                                    FilterChip(
                                        selected = preset == theme,
                                        onClick = { onTheme(preset) },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(preset.primary),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(preset.label)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsGroup("Security") {
                    SettingsItem(
                        title = "App lock",
                        subtitle = "Ask for your PIN when P Vault opens.",
                        trailing = {
                            Switch(
                                checked = settings.appLock,
                                onCheckedChange = { onSettings(settings.copy(appLock = it)) },
                            )
                        },
                    ) {
                        if (!settings.appLock) {
                            Text(
                                "App lock is off. Anyone with this phone can open the vault.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsItem(
                        title = "Change PIN",
                        subtitle = "Use 4 to 6 digits.",
                        trailing = {
                            TextButton(onClick = { changePinOpen = true }) { Text("Change") }
                        },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = "Fingerprint unlock",
                        subtitle = if (biometricsAvailable) {
                            "Unlock with your fingerprint."
                        } else {
                            "No fingerprint is set up on this device."
                        },
                        trailing = {
                            Switch(
                                checked = settings.biometricUnlock && biometricsAvailable,
                                onCheckedChange = { onSettings(settings.copy(biometricUnlock = it)) },
                                enabled = biometricsAvailable,
                            )
                        },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = "Auto-lock after",
                        subtitle = "Lock the app after you leave it. Shorter is safer.",
                    ) {
                        ChoiceChips(
                            options = LOCK_DELAY_OPTIONS,
                            selected = settings.lockDelaySeconds,
                            label = ::lockDelayLabel,
                            onSelect = { onSettings(settings.copy(lockDelaySeconds = it)) },
                        )
                    }
                }

                SettingsGroup("Backup") {
                    SettingsItem(
                        title = "Auto-backup",
                        subtitle = "Back up shortly after a change while P Vault is open.",
                        trailing = {
                            Switch(
                                checked = settings.autoBackup,
                                onCheckedChange = { onSettings(settings.copy(autoBackup = it)) },
                            )
                        },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = "Backup reminder",
                        subtitle = "Ask me when my newest backup is older than this.",
                    ) {
                        ChoiceChips(
                            options = REMINDER_DAY_OPTIONS,
                            selected = settings.reminderDays,
                            label = ::reminderLabel,
                            onSelect = { onSettings(settings.copy(reminderDays = it)) },
                        )
                    }
                    SettingsDivider()
                    SettingsItem(
                        title = "Backups kept",
                        subtitle = "Delete older P Vault zips from the connected Drive folder.",
                    ) {
                        ChoiceChips(
                            options = KEEP_BACKUP_OPTIONS,
                            selected = settings.keepBackups,
                            label = { it.toString() },
                            onSelect = { onSettings(settings.copy(keepBackups = it)) },
                        )
                    }
                }

                SettingsGroup("Data") {
                    SettingsItem(
                        title = "Export vault",
                        subtitle = "Save a copy of your vault as a zip file.",
                        trailing = { TextButton(onClick = onExport) { Text("Export") } },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = "Import backup",
                        subtitle = "Restore from a P Vault zip file.",
                        trailing = { TextButton(onClick = onImport) { Text("Import") } },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = "Clear vault data",
                        subtitle = "Delete local photos, PDFs, notes, reels, chats and your profile. Drive backups stay.",
                        trailing = {
                            TextButton(
                                onClick = { clearOpen = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) { Text("Clear") }
                        },
                    )
                }

                if (update != null) {
                    SettingsGroup("Update") {
                        SettingsItem(
                            title = "P Vault ${update.version} is available",
                            subtitle = "Download and install the newest version.",
                            trailing = { TextButton(onClick = onUpdate) { Text("Install") } },
                        )
                    }
                }

                SettingsGroup("About") {
                    SettingsItem(
                        title = "P Vault $version",
                        subtitle = "Local-first. Your vault stays on this device.",
                    ) {
                        Text(
                            "Backups are plain zip files, not encrypted.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Update checks use the official P Vault website.",
                            color = TextSoft,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
    }

    if (changePinOpen) {
        ChangePinDialog(onDismiss = { changePinOpen = false })
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text("Clear vault data?") },
            text = {
                Text("This removes all local photos, PDFs, notes, reels, chats and your profile. Google Drive backups are not deleted. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = { clearOpen = false; onClearData() }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = TextSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, color = TextSoft, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (content != null) {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    )
}

@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            item {
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

private fun reminderLabel(days: Int): String = when (days) {
    1 -> "Daily"
    7 -> "Weekly"
    14 -> "Every 2 weeks"
    30 -> "Monthly"
    else -> "Every $days days"
}

private fun lockDelayLabel(seconds: Int): String = when (seconds) {
    0 -> "Immediately"
    30 -> "30 seconds"
    60 -> "1 minute"
    300 -> "5 minutes"
    else -> "$seconds seconds"
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = current,
                    onValueChange = {
                        current = it.filter(Char::isDigit).take(6)
                        error = null
                    },
                    label = { Text("Current PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        newPin = it.filter(Char::isDigit).take(6)
                        error = null
                    },
                    label = { Text("New PIN (4-6 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it.filter(Char::isDigit).take(6)
                        error = null
                    },
                    label = { Text("Confirm new PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                val message = error
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !Auth.checkPin(context, current) -> error = "Current PIN is wrong"
                        newPin.length < 4 -> error = "Use at least 4 digits"
                        newPin != confirm -> error = "New PINs do not match"
                        newPin == current -> error = "Choose a different PIN"
                        else -> {
                            Auth.setPin(context, newPin)
                            Toast.makeText(context, "PIN changed", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
