package com.dudeapp.vault

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

    BackHandler(onBack = onBack)

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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
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
                Text(
                    text = "Make P Vault work the way you want. Everything stays on this phone unless you export it.",
                    color = TextSoft,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))

                SettingsSection("Appearance") {
                    Text("Theme", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
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

                Spacer(Modifier.height(12.dp))

                SettingsSection("Security") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("App lock", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Ask for your PIN when P Vault opens.",
                                color = TextSoft,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = settings.appLock,
                            onCheckedChange = {
                                onSettings(settings.copy(appLock = it))
                            },
                        )
                    }
                    if (!settings.appLock) {
                        Text(
                            "App lock is off. Anyone with this phone can open the vault.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { changePinOpen = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Change PIN", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Use 4-6 digits", color = TextSoft, fontSize = 12.sp)
                        }
                        TextButton(onClick = { changePinOpen = true }) { Text("Change") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fingerprint unlock", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (biometricsAvailable) {
                                    "Use your fingerprint on the lock screen."
                                } else {
                                    "No fingerprint is set up on this device."
                                },
                                color = TextSoft,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = settings.biometricUnlock && biometricsAvailable,
                            onCheckedChange = {
                                onSettings(settings.copy(biometricUnlock = it))
                            },
                            enabled = biometricsAvailable,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Auto-lock after", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Applies when you leave P Vault. Shorter is safer.",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChoiceChips(
                        options = LOCK_DELAY_OPTIONS,
                        selected = settings.lockDelaySeconds,
                        label = ::lockDelayLabel,
                        onSelect = { onSettings(settings.copy(lockDelaySeconds = it)) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                SettingsSection("Backup") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-backup", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Back up about 20 seconds after a change while P Vault is open.",
                                color = TextSoft,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = settings.autoBackup,
                            onCheckedChange = {
                                onSettings(settings.copy(autoBackup = it))
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Backup reminder", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Ask for a backup when the newest one is older than this.",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChoiceChips(
                        options = REMINDER_DAY_OPTIONS,
                        selected = settings.reminderDays,
                        label = ::reminderLabel,
                        onSelect = { onSettings(settings.copy(reminderDays = it)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Backups kept", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Older P_Vault zips in the connected Drive folder are deleted. Other files are left alone.",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChoiceChips(
                        options = KEEP_BACKUP_OPTIONS,
                        selected = settings.keepBackups,
                        label = { it.toString() },
                        onSelect = { onSettings(settings.copy(keepBackups = it)) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                SettingsSection("Data") {
                    TextButton(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Export vault to a zip")
                    }
                    TextButton(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Import a backup")
                    }
                    TextButton(
                        onClick = { clearOpen = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear vault data")
                    }
                    Text(
                        "Export and import work with a normal zip file. Drive backups are not deleted when you clear local data.",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (update != null) {
                    SettingsSection("Notifications") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onUpdate)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "P Vault ${update.version} is available",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "Tap to download and install the new version.",
                                    color = TextSoft,
                                    fontSize = 12.sp,
                                )
                            }
                            TextButton(onClick = onUpdate) { Text("Install") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                SettingsSection("About") {
                    Text("P Vault $version", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Local-first. The only network request is a GitHub update check; Drive uploads are handled by the Google Drive app.",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Backups are plain zip files, not encrypted.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
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
                Text(
                    "This removes all local photos, PDFs, notes, reels, chats and your profile. Google Drive backups are not deleted. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearOpen = false
                        onClearData()
                    },
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
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
