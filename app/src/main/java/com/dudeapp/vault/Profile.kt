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
import androidx.compose.material.icons.outlined.AlternateEmail
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
import androidx.compose.runtime.rememberCoroutineScope
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
    name: String,
    avatar: File?,
    drive: DriveBackupState,
    onPickPhoto: () -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onBackupNow: (String) -> Unit,
    onRestoreFile: () -> Unit,
    onExternal: () -> Unit,
    onGoogleSignedIn: (GoogleUser) -> Unit,
    /** True when a Google account is signed in; without one there is no identity, so no name. */
    signedIn: Boolean,
    /** True when the admin has fixed names: a name already taken can no longer be changed. */
    namesFixed: Boolean,
    /** The username this phone has, and whether it is settled with the database. */
    username: String,
    usernameConfirmed: Boolean,
    /**
     * Checks and keeps a name, with the Google pass that lets this phone write it. Reports
     * back in words: null when the name was taken, a sentence when it was not.
     */
    onChangeUsername: suspend (String, String?) -> String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(name) { mutableStateOf(name) }
    // Seeded from what is saved, and never reset by the sheet reopening, so a name that was
    // just typed is not pulled back to the old one by a redraw.
    var userDraft by remember { mutableStateOf(username) }
    var userNote by remember(username) { mutableStateOf<String?>(null) }
    var userBusy by remember { mutableStateOf(false) }
    var userSaved by remember { mutableStateOf(username) }
    var savedConfirmed by remember { mutableStateOf(usernameConfirmed) }
    val userScope = rememberCoroutineScope()
    val sheetContext = LocalContext.current
    // A name waiting for Google's permission to be saved, and the pass it comes back with.
    var pendingName by remember { mutableStateOf<String?>(null) }
    // The Google pass, kept while the session is being made from it.
    var pendingPass by remember { mutableStateOf<String?>(null) }

    // Saves the name once a pass is in hand, and says what happened either way.
    fun keepName(wanted: String, token: String?) {
        userBusy = true
        userScope.launch {
            val problem = onChangeUsername(wanted, token)
            userBusy = false
            if (problem == null) {
                userSaved = wanted
                savedConfirmed = true
                userNote = null
            } else {
                userNote = problem
                // A name that could not be checked is still kept on this phone, so the note
                // explains rather than blocks.
                if (problem.contains("this phone", ignoreCase = true) ||
                    problem.contains("could not be reserved", ignoreCase = true)
                ) {
                    userSaved = wanted
                    savedConfirmed = false
                }
            }
        }
    }

    // The account picker; its answer holds the ID token Firebase accepts as proof of identity.
    val signInScreen = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val wanted = pendingName
        val pass = pendingPass
        pendingName = null
        pendingPass = null
        val activity = sheetContext.findActivity()
        val idToken = activity?.let { FirebaseSession.idTokenFrom(it, result.data) }
        if (idToken == null || wanted == null) {
            userBusy = false
            userNote = "Google sign-in was cancelled."
            return@rememberLauncherForActivityResult
        }
        userScope.launch {
            val session = FirebaseSession.signInWithIdToken(sheetContext, idToken)
            if (session == null) {
                userBusy = false
                userNote = "Firebase refused the session. Google sign-in must be enabled for " +
                    "P Vault in the Firebase console."
            } else {
                keepName(wanted, pass)
            }
        }
    }

    // Google's sign-in screen for a name: the answer carries the pass, and the token that
    // proves who the person is.
    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val wanted = pendingName
        val pass = tokenFromConsent(sheetContext, result.data)
        if (wanted == null || pass == null) {
            pendingName = null
            userBusy = false
            userNote = "Google sign-in was cancelled."
            return@rememberLauncherForActivityResult
        }
        userScope.launch {
            val activity = sheetContext.findActivity()
            val sender = activity?.let { FirebaseSession.signInPendingIntent(it) }
            if (activity == null || sender == null) {
                pendingName = null
                userBusy = false
                userNote = "Google could not confirm the account. Sign out and in, then try again."
            } else {
                pendingPass = pass
                runCatching {
                    signInScreen.launch(IntentSenderRequest.Builder(sender).build())
                }.onFailure {
                    pendingName = null
                    userBusy = false
                    userNote = "Google's sign-in screen could not be opened."
                }
            }
        }
    }

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
                text = "Stored on this phone. Google sign-in and Drive backup are optional.",
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

            // Usernames are unique and checked as they are typed; the name is kept only when
            // the check passes, and the verdict is always said in words.
            Spacer(Modifier.height(14.dp))
            // A username belongs to a person, so it is only offered once a Google account is
            // signed in: that is the only way to keep a name for someone and check it again
            // on their next phone.
            if (!signedIn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.AlternateEmail,
                        contentDescription = null,
                        tint = TextSoft,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Usernames need a Google account. Sign in below to pick yours.",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (signedIn) OutlinedTextField(
                value = userDraft,
                onValueChange = { typed ->
                    userDraft = normalizeUsername(typed).take(20)
                    userNote = null
                },
                label = { Text("Username") },
                // A fixed name is read-only: the field is shown, but there is nothing to change.
                readOnly = namesFixed && userSaved.isNotEmpty(),
                supportingText = {
                    val clean = normalizeUsername(userDraft)
                    val rule = nameProblemText(usernameProblem(clean))
                    when {
                        rule != null -> Text(rule, color = MaterialTheme.colorScheme.error)
                        namesFixed && userSaved.isNotEmpty() ->
                            Text("Fixed — this username cannot be changed.")
                        clean == userSaved -> Text("Yours — tap it to pick a different name.")
                        userNote != null -> Text(userNote.orEmpty(), color = MaterialTheme.colorScheme.error)
                        else -> Text("Tap Keep to take this name.")
                    }
                },
                isError = userNote != null || usernameProblem(normalizeUsername(userDraft)) != null,
                singleLine = true,
                leadingIcon = { Text("@", color = TextSoft) },
                trailingIcon = {
                    if (userBusy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (!(namesFixed && userSaved.isNotEmpty()) &&
                        normalizeUsername(userDraft).isNotEmpty() &&
                        (normalizeUsername(userDraft) != userSaved || !savedConfirmed)
                    ) {
                        TextButton(
                            onClick = {
                                val wanted = normalizeUsername(userDraft)
                                userBusy = true
                                userNote = null
                                userScope.launch {
                                    // Google may need to ask first; that answer arrives through
                                    // the launcher below, and the name is kept from there.
                                    when (val answer = requestToken(sheetContext)) {
                                        is TokenAnswer.Ready -> {
                                            userBusy = false
                                            keepName(wanted, answer.accessToken)
                                        }
                                        is TokenAnswer.Consent -> {
                                            pendingName = wanted
                                            runCatching {
                                                consent.launch(
                                                    IntentSenderRequest.Builder(answer.intentSender).build(),
                                                )
                                            }.onFailure {
                                                userBusy = false
                                                userNote = "Google sign-in could not be opened."
                                            }
                                        }
                                        TokenAnswer.Failed -> {
                                            userBusy = false
                                            userNote = "Sign in with Google below, then tap Keep again."
                                        }
                                    }
                                }
                            },
                        ) { Text("Keep") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            GoogleAccountSection(onExternal = onExternal, onSignedIn = onGoogleSignedIn)
            Spacer(Modifier.height(12.dp))
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
