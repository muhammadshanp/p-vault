package com.dudeapp.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.launch

/**
 * What a new install sees once, before the vault.
 *
 * The reference's shape, not its artwork: a dark gradient head with the app's own mark and
 * name, one sentence about what the app is, then the two doors — Google, or skip. The
 * gradient is the theme's own dark end, so it never clashes with the chosen look.
 */
@Composable
fun WelcomeScreen(
    onSignedIn: (GoogleUser) -> Unit,
    onSkip: () -> Unit,
    onExternal: () -> Unit,
) {
    val accent = LocalThemePreset.current
    val ink = accent.onBackground
    val scope = rememberCoroutineScope()
    var signedInAs by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(accent.primary, accent.background),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Screen)
                .padding(top = 36.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Welcome to",
                color = ink.copy(alpha = 0.75f),
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = "P Vault",
                color = ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your photos, PDFs, notes, reels and chats, kept on this phone and in no one else's hands.",
                color = ink.copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(accent.background)
                .padding(horizontal = Spacing.Screen)
                .padding(top = 26.dp, bottom = 26.dp),
        ) {
            WelcomePoint(Icons.Outlined.Lock, "Stays on this phone", "Nothing is uploaded unless you ask for a backup.")
            Spacer(Modifier.height(14.dp))
            WelcomePoint(Icons.Outlined.Person, "A name, once", "Sign in, then pick the name people see. That is all an account is for.")
            Spacer(Modifier.height(14.dp))
            WelcomePoint(Icons.Outlined.Shield, "Yours to lock", "PIN or fingerprint, and a backup only you can read.")

            Spacer(Modifier.height(26.dp))

            val signed = signedInAs
            if (signed != null) {
                Text(
                    text = "Signed in as $signed",
                    color = ink.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
            }
            // The Google flow lives in the account section, so there is exactly one of it in
            // the app; this button just opens it here.
            GoogleAccountSection(
                onExternal = onExternal,
                onSignedIn = { user ->
                    signedInAs = user.email
                    onSignedIn(user)
                },
            )

            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip for now", color = ink.copy(alpha = 0.75f), fontWeight = FontWeight.Medium)
            }
            Text(
                text = "You can sign in and choose a name later from the menu.",
                color = ink.copy(alpha = 0.45f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WelcomePoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    val accent = LocalThemePreset.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.onBackground.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent.onBackground, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = accent.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                color = accent.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

/**
 * "Choose your name", shown once to a signed-in person who has no name yet.
 *
 * The name is not an account: it is a display name, claimed once through the name service,
 * and the phone that claims it is the phone it belongs to. That is why this asks nothing
 * but the name — there is no password, and signing up is what the welcome page did.
 */
@Composable
fun ChooseNameScreen(
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val accent = LocalThemePreset.current
    val ink = accent.onBackground
    val scope = rememberCoroutineScope()

    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var good by remember { mutableStateOf(false) }

    fun claim() {
        val wanted = normalizeUsername(typed)
        if (wanted.isEmpty()) {
            good = false
            message = "Type a name first."
            return
        }
        busy = true
        message = null
        scope.launch {
            val status = when (val check = Supabase.checkName(context, wanted)) {
                Supabase.NameCheck.Free -> Supabase.claimName(context, wanted)
                Supabase.NameCheck.Taken -> "taken"
                Supabase.NameCheck.Offline -> null
                is Supabase.NameCheck.Rejected -> check.reason
            }
            busy = false
            if (status == "ok" || status == "already_yours") {
                context.getSharedPreferences("pre_re_supabase", android.content.Context.MODE_PRIVATE)
                    .edit().putString("claimed_name", wanted).apply()
                good = true
                message = "@$wanted is yours."
                onDone()
            } else {
                good = false
                message = Supabase.claimProblem(status)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(accent.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.Screen),
    ) {
        Spacer(Modifier.height(30.dp))
        Text(
            text = "Choose your name",
            color = ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This is the name beside your photos and chats. Letters, numbers, dots and " +
                "underscores; 3 to 24 characters. It is taken once and nobody else can have it.",
            color = ink.copy(alpha = 0.6f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(22.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accent.surface)
                .border(1.dp, accent.outline.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("@", color = ink.copy(alpha = 0.4f), fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            BasicTextField(
                value = typed,
                onValueChange = {
                    typed = normalizeUsername(it).take(24)
                    message = null
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = ink, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                keyboardActions = KeyboardActions(onDone = { claim() }),
                modifier = Modifier.weight(1f),
            )
        }

        val note = message
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = note,
                color = if (good) ink else Color(0xFFE05252),
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { claim() },
            enabled = !busy && typed.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.primary,
                contentColor = accent.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent.onPrimary)
            } else {
                Text("Claim this name", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Later", color = ink.copy(alpha = 0.7f))
        }
    }
}
