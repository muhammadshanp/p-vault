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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.material3.AlertDialog
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // App card: version at a glance, and the update button when there is one.
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
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
                                Text("P Vault", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (update != null) "Version $version · ${update.version} is available" else "Version $version · up to date",
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        if (update != null) {
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                                Text("Update to ${update.version}")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))

                SettingsGroup("Appearance") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ThemePreset.entries.forEach { preset ->
                            ThemeCard(
                                preset = preset,
                                selected = preset == theme,
                                onClick = { onTheme(preset) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                SettingsGroup("Security") {
                    SettingsItem(
                        icon = Icons.Outlined.Lock,
                        tint = Accent.Photo,
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
                        icon = Icons.Outlined.Key,
                        tint = Accent.Photo,
                        title = "Change PIN",
                        subtitle = "Use 4 to 6 digits.",
                        onClick = { changePinOpen = true },
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Outlined.Fingerprint,
                        tint = Accent.Photo,
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
                        icon = Icons.Outlined.Timer,
                        tint = Accent.Photo,
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

                SettingsGroup("Notifications") {
                    SettingsItem(
                        icon = Icons.Outlined.Notifications,
                        tint = Accent.Reel,
                        title = "Push notifications",
                        subtitle = "News from the P Vault team and update alerts. Tap to turn them on or off in Android's settings.",
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

                SettingsGroup("Backup") {
                    SettingsItem(
                        icon = Icons.Outlined.Backup,
                        tint = Accent.Chat,
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
                        icon = Icons.Outlined.Schedule,
                        tint = Accent.Chat,
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
                        icon = Icons.Outlined.Inventory2,
                        tint = Accent.Chat,
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
                        icon = Icons.Outlined.FileUpload,
                        tint = Accent.Note,
                        title = "Export vault",
                        subtitle = "Save a copy of your vault as a zip file.",
                        onClick = onExport,
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Outlined.FileDownload,
                        tint = Accent.Note,
                        title = "Import backup",
                        subtitle = "Add a P Vault zip to this vault. Files already here are kept.",
                        onClick = onImport,
                    )
                }

                SettingsGroup("Danger zone", danger = true) {
                    SettingsItem(
                        icon = Icons.Outlined.Delete,
                        tint = MaterialTheme.colorScheme.error,
                        title = "Clear vault data",
                        titleColor = MaterialTheme.colorScheme.error,
                        subtitle = "Delete local photos, PDFs, notes, reels, chats and your profile. Drive backups stay.",
                        onClick = { clearOpen = true },
                    )
                }

                Text(
                    "Local-first. Your vault stays on this device.\n" +
                        "Backups are plain zip files, not encrypted.\n" +
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

/** A small picture of the theme: page colour, a card, an accent dot. */
@Composable
private fun ThemeCard(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(preset.background)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(preset.primary),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .height(6.dp)
                        .width(36.dp)
                        .clip(CircleShape)
                        .background(preset.onBackground.copy(alpha = 0.6f)),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(preset.surface)
                    .border(1.dp, preset.outline, RoundedCornerShape(8.dp)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                preset.label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else TextSoft
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (danger) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            ),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * One row: tinted icon tile, title and subtitle, then either a control ([trailing]) or,
 * for rows that open something ([onClick]), an arrow. The whole row is the tap target.
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, color = TextSoft, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            } else if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSoft,
                )
            }
        }
        if (content != null) {
            Spacer(Modifier.height(10.dp))
            // Lines up with the text, past the icon tile.
            Column(Modifier.padding(start = 52.dp), content = content)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 14.dp),
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
