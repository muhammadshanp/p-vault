package com.dudeapp.vault

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
    access: MyAccess,
    onTheme: (ThemePreset) -> Unit,
    onThemes: () -> Unit,
    onSettings: (AppSettings) -> Unit,
    onUpdate: () -> Unit,
    backupProtected: Boolean,
    lastBackupBytes: Long,
    /** A new password, or null to remove the protection. */
    onBackupPassword: (String?) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearData: () -> Unit,
    /** Opens the account page; the settings sheet points at it rather than repeating it. */
    onProfile: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var changePinOpen by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    var backupPasswordOpen by remember { mutableStateOf(false) }
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
                    .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = close) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.Screen)
                    .padding(bottom = 32.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // App card: version at a glance, and the update button when there is one.
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("P Vault", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (update != null) "Version $version · ${update.version} is available" else "Version $version · up to date",
                                    fontSize = 12.sp,
                                )
                            }
                            if (update != null) {
                                TextButton(onClick = onUpdate) { Text("Update") }
                            }
                        }
                    }
                }

                SheetGap(18.dp)
                SettingsSheetLabel("Account")
                SettingsSheet {
                    SettingsSheetRow(
                        icon = Icons.Outlined.Person,
                        title = "Profile",
                        value = access.username?.let { "@$it" },
                        onClick = onProfile,
                    )
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        value = theme.label,
                        onClick = onThemes,
                    )
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Settings,
                        title = "Settings path",
                        value = if (settings.cardsAsList) "List" else "Cards",
                    )
                }

                SheetGap()
                SettingsSheetLabel("Preferences")
                SettingsSheet {
                    SettingsSheetRow(
                        icon = Icons.Outlined.ViewAgenda,
                        title = "Photos and PDFs",
                        value = if (settings.cardsAsList) "List" else "Cards",
                    ) {
                        OnOffSwitch(
                            checked = settings.cardsAsList,
                            onCheckedChange = { onSettings(settings.copy(cardsAsList = it)) },
                        )
                    }
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Palette,
                        title = "Advanced mode",
                        value = if (settings.advancedMode) "On" else "Off",
                    ) {
                        OnOffSwitch(
                            checked = settings.advancedMode,
                            onCheckedChange = { onSettings(settings.copy(advancedMode = it)) },
                        )
                    }
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Notifications",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                }

                SheetGap()
                SettingsSheetLabel("Privacy and security")
                SettingsSheet {
                    SettingsSheetRow(
                        icon = Icons.Outlined.Lock,
                        title = "App lock",
                        value = if (settings.appLock) "On" else "Off",
                    ) {
                        OnOffSwitch(
                            checked = settings.appLock,
                            onCheckedChange = { onSettings(settings.copy(appLock = it)) },
                        )
                    }
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Key,
                        title = "Change PIN",
                        onClick = { changePinOpen = true },
                    )
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Fingerprint unlock",
                        value = if (!biometricsAvailable) "Unavailable" else if (settings.biometricUnlock) "On" else "Off",
                    ) {
                        OnOffSwitch(
                            checked = settings.biometricUnlock && biometricsAvailable,
                            onCheckedChange = { onSettings(settings.copy(biometricUnlock = it)) },
                            enabled = biometricsAvailable,
                        )
                    }
                }
                Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                    Text("Auto-lock", color = TextSoft, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    ChoiceChips(
                        options = LOCK_DELAY_OPTIONS,
                        selected = settings.lockDelaySeconds,
                        label = ::lockDelayLabel,
                        onSelect = { onSettings(settings.copy(lockDelaySeconds = it)) },
                    )
                }

                SheetGap()
                SettingsSheetLabel("Backup")
                SettingsSheet {
                    SettingsSheetRow(
                        icon = Icons.Outlined.Key,
                        title = "Backup password",
                        value = if (backupProtected) "On" else "Off",
                        onClick = { backupPasswordOpen = true },
                    )
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Backup,
                        title = "Auto-backup",
                        value = if (settings.autoBackup) "On" else "Off",
                    ) {
                        OnOffSwitch(
                            checked = settings.autoBackup,
                            onCheckedChange = { onSettings(settings.copy(autoBackup = it)) },
                        )
                    }
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Schedule,
                        title = "Backup reminder",
                        value = reminderLabel(settings.reminderDays),
                        onClick = null,
                    )
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Inventory2,
                        title = "Backups kept",
                        value = settings.keepBackups.toString(),
                    )
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.Storage,
                        title = "Backup size limit",
                        value = if (settings.backupLimitGb == 0) "No limit" else "${settings.backupLimitGb} GB",
                    )
                }
                Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                    ChoiceChips(
                        options = KEEP_BACKUP_OPTIONS,
                        selected = settings.keepBackups,
                        label = { it.toString() },
                        onSelect = { onSettings(settings.copy(keepBackups = it)) },
                    )
                    Spacer(Modifier.height(6.dp))
                    ChoiceChips(
                        options = BACKUP_LIMIT_OPTIONS,
                        selected = settings.backupLimitGb,
                        label = { if (it == 0) "No limit" else "$it GB" },
                        onSelect = { onSettings(settings.copy(backupLimitGb = it)) },
                    )
                    Spacer(Modifier.height(6.dp))
                    val limit = settings.backupLimitGb
                    val beyondFree = limit == 0 || limit > FREE_DRIVE_GB
                    Text(
                        text = when {
                            beyondFree ->
                                "A free Google account has $FREE_DRIVE_GB GB in total, shared with Gmail and " +
                                    "Google Photos. A limit above that only works with a paid Google One plan."
                            limit == FREE_DRIVE_GB ->
                                "This is all the space a free Google account has ($FREE_DRIVE_GB GB), and Gmail and " +
                                    "Google Photos share it. Backups may fail if Drive is full."
                            else ->
                                "A free Google account has $FREE_DRIVE_GB GB in total, shared with Gmail and Google Photos."
                        },
                        color = if (beyondFree) MaterialTheme.colorScheme.error else TextSoft,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                    if (lastBackupBytes > 0L) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your last backup was ${sizeLabel(lastBackupBytes)}.",
                            color = TextSoft,
                            fontSize = 12.sp,
                        )
                    }
                }

                SheetGap()
                SettingsSheetLabel("Data")
                SettingsSheet {
                    SettingsSheetRow(
                        icon = Icons.Outlined.FileUpload,
                        title = "Export vault",
                        onClick = onExport,
                    )
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = Icons.Outlined.FileDownload,
                        title = "Import backup",
                        onClick = onImport,
                    )
                }

                SheetGap()
                SettingsSheetLabel("Danger zone")
                SettingsSheet {
                    SettingsSheetRow(
                        icon = Icons.Outlined.Delete,
                        title = "Clear vault data",
                        valueColor = MaterialTheme.colorScheme.error,
                        onClick = { clearOpen = true },
                    )
                }

                Text(
                    "Local-first. Your vault stays on this device.\n" +
                        (if (backupProtected) {
                            "Backups are encrypted with your backup password.\n"
                        } else {
                            "Backups are plain zip files, not encrypted.\n"
                        }) +
                        "Updates are checked and downloaded from P Vault's GitHub releases.",
                    color = TextSoft,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
    }

    if (changePinOpen) {
        ChangePinDialog(onDismiss = { changePinOpen = false })
    }

    if (backupPasswordOpen) {
        BackupPasswordDialog(
            protected = backupProtected,
            onDismiss = { backupPasswordOpen = false },
            onSet = {
                backupPasswordOpen = false
                onBackupPassword(it)
            },
        )
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

@Composable
private fun BackupPasswordDialog(
    protected: Boolean,
    onDismiss: () -> Unit,
    onSet: (String?) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val longEnough = password.length >= BackupCrypto.MIN_PASSWORD_LENGTH
    val matches = password == repeat

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (protected) "Change backup password" else "Protect backups") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Backups and exports are encrypted with this password. You will need it to " +
                        "restore on another phone.",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "There is no way to reset it. If you forget the password, those backups " +
                        "cannot be opened.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
                if (!protected) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "After the next backup, the older unprotected zips are removed from the Drive folder.",
                        color = TextSoft,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (protected) "New password" else "Password") },
                    singleLine = true,
                    supportingText = { Text("At least ${BackupCrypto.MIN_PASSWORD_LENGTH} characters") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text("Type it again") },
                    singleLine = true,
                    isError = repeat.isNotEmpty() && !matches,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (protected) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onSet(null) }) {
                        Text("Turn off protection", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(password) }, enabled = longEnough && matches) {
                Text(if (protected) "Change" else "Protect")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A switch that is clearly visible in both positions, with the word "On" or "Off" beside
 * it. The stock colours draw the "off" position in near-white, which vanished on the cards.
 */
@Composable
private fun OnOffSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (checked) "On" else "Off",
            color = when {
                !enabled -> TextSoft.copy(alpha = 0.6f)
                checked -> scheme.primary
                else -> TextSoft
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = scheme.primary,
                checkedBorderColor = scheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedThumbColor = Color.White,
                disabledCheckedTrackColor = scheme.primary.copy(alpha = 0.35f),
                disabledCheckedBorderColor = Color.Transparent,
                disabledUncheckedThumbColor = Color.White,
                disabledUncheckedTrackColor = scheme.onSurfaceVariant.copy(alpha = 0.22f),
                disabledUncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
