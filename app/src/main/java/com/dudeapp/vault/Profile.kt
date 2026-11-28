package com.dudeapp.vault

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

data class UserProfile(val name: String, val avatar: File?)

class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("dude_profile", Context.MODE_PRIVATE)

    fun load(): UserProfile = UserProfile(
        name = prefs.getString("name", "").orEmpty(),
        avatar = prefs.getString("avatar", null)
            ?.let { File(it) }
            ?.takeIf { it.isFile && it.length() > 0 },
    )

    fun setName(name: String) {
        prefs.edit().putString("name", name).apply()
    }

    /**
     * Copies the picked picture in and returns it. Each picture gets its own file name, so the
     * screen (and the image loader's cache) can tell the new photo from the old one: writing
     * over one fixed file name left the old picture on screen until the app was restarted.
     */
    fun saveAvatar(uri: Uri): File? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        val target = File(context.filesDir, "profile_avatar_${System.currentTimeMillis()}.img")
        val temp = File(context.filesDir, "profile_avatar.tmp")
        input.use { source -> temp.outputStream().use { sink -> source.copyTo(sink) } }
        if (temp.length() == 0L) {
            temp.delete()
            return@runCatching null
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        if (target.length() == 0L) {
            target.delete()
            return@runCatching null
        }
        // Whatever the last picture was, it is no longer the profile's.
        prefs.getString("avatar", null)?.let { old -> File(old).takeIf { it != target }?.delete() }
        prefs.edit().putString("avatar", target.absolutePath).apply()
        target
    }.getOrNull()

    fun clear() {
        prefs.getString("avatar", null)?.let { runCatching { File(it).delete() } }
        prefs.edit().remove("name").remove("avatar").apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    avatar: File?,
    drive: DriveBackupState,
    /** False when this name has not been given Drive backup. */
    backupAllowed: Boolean = true,
    /** The signed-in Google address, empty when nobody is signed in. */
    email: String = "",
    /** The tick this account holds, if any. */
    tick: AccessTick = AccessTick.None,
    /** The name this phone holds on the server, if any. */
    username: String? = null,
    onPickPhoto: () -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onBackupNow: (String) -> Unit,
    onRestoreFile: () -> Unit,
    onExternal: () -> Unit,
    onGoogleSignedIn: (GoogleUser) -> Unit,
    onSignOut: () -> Unit,
    /** Called after the name this phone holds was replaced. */
    onNameChanged: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    // Seeded from what is saved, and never reset by the sheet reopening, so a name that was
    // just typed is not pulled back to the old one by a redraw.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = LocalThemePreset.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        // The header sits on the theme's darkest colour, like the reference's account page,
        // so the sheet below it reads as a raised panel rather than a continuation.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(accent.background)
                    .padding(horizontal = Spacing.Screen)
                    .padding(top = 10.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SettingsHeader(title = "Account", onBack = onDismiss)
                Spacer(Modifier.height(6.dp))

                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(accent.surfaceVariant)
                            .border(2.dp, accent.onBackground.copy(alpha = 0.25f), CircleShape)
                            .clickable(onClick = onPickPhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatar != null) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = accent.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    // The camera dot: the one affordance for changing the picture.
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accent.primary)
                            .clickable(onClick = onPickPhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = "Change photo",
                            tint = accent.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // No name yet is its own state, not an invitation to type here: the
                        // name is set from the name card below, and only when asked.
                        text = username?.let { "@$it" } ?: "No username yet",
                        color = accent.onBackground,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (tick != AccessTick.None) {
                        Spacer(Modifier.width(5.dp))
                        TickBadge(tick, 17.dp)
                    }
                }
                Text(
                    text = if (email.isBlank()) "Not signed in" else email,
                    color = accent.onBackground.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp,
                        ),
                    )
                    .background(accent.background)
                    .padding(horizontal = Spacing.Screen)
                    .padding(top = 6.dp),
            ) {
                SettingsSheet {
                    SettingsSheetRow(
                        icon = Icons.Outlined.Email,
                        title = "Email",
                        value = email.ifBlank { "None" },
                    )
                }
                SheetGap()
                PreReNameCard(onChanged = onNameChanged, accountName = username)

                SheetGap()
                SettingsSheetLabel("Account")
                SettingsSheet {
                    SettingsSheetRow(
                        icon = Icons.Outlined.PhotoCamera,
                        title = if (avatar == null) "Add photo" else "Change photo",
                        onClick = onPickPhoto,
                    )
                    SettingsSheetDivider()
                    if (backupAllowed) {
                        SettingsSheetRow(
                            icon = Icons.Outlined.CloudUpload,
                            title = "Google Drive backup",
                            value = if (drive.connected) "On" else "Off",
                            onClick = null,
                        )
                    } else {
                        SettingsSheetRow(
                            icon = Icons.Outlined.Lock,
                            title = "Drive backup",
                            value = "Locked",
                            onClick = null,
                        )
                    }
                    SettingsSheetDivider()
                    SettingsSheetRow(
                        icon = if (email.isBlank()) Icons.Outlined.Login else Icons.Outlined.Logout,
                        title = if (email.isBlank()) "Sign in with Google" else "Sign out",
                        value = if (email.isBlank()) null else "Signed in",
                        onClick = { if (email.isBlank()) onExternal() else onSignOut() },
                    )
                }

                SheetGap()
                if (email.isBlank()) {
                    GoogleAccountSection(onExternal = onExternal, onSignedIn = onGoogleSignedIn)
                } else {
                    SettingsSheetLabel("Signed in as $email")
                    if (backupAllowed) {
                        DriveBackupSection(
                            drive = drive,
                            onConnect = onConnectDrive,
                            onDisconnect = onDisconnectDrive,
                            // The backup is named after the account name, which is the only
                            // name this app has (the local "profile name" is gone).
                            onBackupNow = { onBackupNow(username.orEmpty()) },
                            onRestoreFile = onRestoreFile,
                        )
                    } else {
                        SettingsSheetLabel("Drive backup has not been given to this name.")
                    }
                }

            }
        }
    }
}

/** A feature that exists but has not been granted to this name. */
@Composable
private fun LockedFeature(title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = TextSoft,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextSoft, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DriveBackupSection(
    drive: DriveBackupState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBackupNow: () -> Unit,
    onRestoreFile: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp),
    ) {
        Text(
            text = "Google Drive backup",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Connect a Drive folder through Android's file picker. The Google Drive app is required and handles the upload. P Vault never sees your password.",
            color = TextSoft,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (drive.connected) "Connected" else "Not connected",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = drive.account.ifBlank { "Google Drive" },
                    color = TextSoft,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (drive.connected) {
                TextButton(onClick = onDisconnect, enabled = !drive.busy) {
                    Text("Disconnect", fontSize = 12.sp)
                }
            } else {
                TextButton(onClick = onConnect, enabled = !drive.busy) {
                    Text("Connect", fontSize = 12.sp)
                }
            }
        }

        val message = drive.message
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = TextSoft,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onBackupNow,
            enabled = !drive.busy && drive.connected,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
        ) {
            if (drive.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Working...")
            } else {
                Text("Back up now", fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(
            onClick = onRestoreFile,
            enabled = !drive.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore from a zip file (local or Drive)")
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Changes back up about 20 seconds after you edit while P Vault is open. Pending changes retry on next open.",
            color = TextSoft,
            fontSize = 11.sp,
        )
        Text(
            text = "Backups are not encrypted. Anyone with access to the Google account can read them.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 11.sp,
        )
        Text(
            text = "Keep P Vault open while a backup or restore runs.",
            color = TextSoft,
            fontSize = 11.sp,
        )
    }
}
