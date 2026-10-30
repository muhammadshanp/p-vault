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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

data class UserProfile(val name: String, val avatar: File?)

class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("dude_profile", Context.MODE_PRIVATE)
    private val avatarFile = File(context.filesDir, "profile_avatar")

    fun load(): UserProfile = UserProfile(
        name = prefs.getString("name", "").orEmpty(),
        avatar = avatarFile.takeIf { it.exists() && it.length() > 0 },
    )

    fun setName(name: String) {
        prefs.edit().putString("name", name).apply()
    }

    fun saveAvatar(uri: Uri): File? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        val temp = File(context.filesDir, "profile_avatar.tmp")
        input.use { source -> temp.outputStream().use { sink -> source.copyTo(sink) } }
        if (temp.length() == 0L) {
            temp.delete()
            return@runCatching null
        }
        avatarFile.delete()
        if (!temp.renameTo(avatarFile)) {
            temp.copyTo(avatarFile, overwrite = true)
            temp.delete()
        }
        avatarFile.takeIf { it.length() > 0 }
    }.getOrNull()

    fun clear() {
        prefs.edit().remove("name").apply()
        avatarFile.delete()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    name: String,
    avatar: File?,
    drive: DriveBackupState,
    onPickPhoto: () -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onBackupNow: (String) -> Unit,
    onRestoreFile: () -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(name) { mutableStateOf(name) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Your profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Stored on this phone. Drive backup is optional.",
                color = TextSoft,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(24.dp))

            Box(
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
            TextButton(onClick = onPickPhoto) {
                Text(if (avatar == null) "Add photo" else "Change photo")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(40) },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            DriveBackupSection(
                drive = drive,
                onConnect = onConnectDrive,
                onDisconnect = onDisconnectDrive,
                onBackupNow = { onBackupNow(draft.trim()) },
                onRestoreFile = onRestoreFile,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(draft.trim()) },
                enabled = draft.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Save profile", fontWeight = FontWeight.SemiBold)
            }
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
