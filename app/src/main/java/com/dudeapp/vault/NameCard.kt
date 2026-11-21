package com.dudeapp.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * The one screen that talks to Supabase: claiming a display name.
 *
 * This is the only data P Vault keeps in a database. Vault contents stay on the phone, so
 * the card says so, and the functions behind the button are the closed ones on the server
 * side — a name, the install holding it, nothing else.
 */
@Composable
fun PreReNameCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("pre_re_supabase", android.content.Context.MODE_PRIVATE) }
    val colors = preReColors()

    var claimed by remember { mutableStateOf(prefs.getString("claimed_name", null)) }
    var verified by remember { mutableStateOf(prefs.getBoolean("verified", false)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "claimed_name") claimed = prefs.getString("claimed_name", null)
            if (key == "verified") verified = prefs.getBoolean("verified", false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var typing by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var good by remember { mutableStateOf(false) }

    fun claim() {
        val wanted = typing.trim()
        if (wanted.isEmpty()) {
            good = false
            message = "Type a name first."
            return
        }
        busy = true
        message = null
        scope.launch {
            when (val check = Supabase.checkName(context, wanted)) {
                is Supabase.NameCheck.Rejected -> {
                    good = false
                    message = Supabase.claimProblem(check.reason)
                }
                Supabase.NameCheck.Taken -> {
                    good = false
                    message = "That name is already taken. Try another."
                }
                Supabase.NameCheck.Offline -> {
                    good = false
                    message = Supabase.claimProblem(null)
                }
                Supabase.NameCheck.Free -> {
                    val status = Supabase.claimName(context, wanted)
                    if (status == "ok" || status == "already_yours") {
                        val stored = normalizeUsername(wanted)
                        prefs.edit().putString("claimed_name", stored).apply()
                        claimed = stored
                        // The tick travels with the grant, not with the claim.
                        val access = Supabase.myAccess(context, stored)
                        verified = access?.verified == true
                        prefs.edit().putBoolean("verified", verified).apply()
                        typing = ""
                        good = true
                        message = "@$stored is yours."
                    } else {
                        good = false
                        message = Supabase.claimProblem(status)
                    }
                }
            }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.page)
            .border(1.dp, colors.divider, RoundedCornerShape(18.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Your name",
            color = colors.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "One name, claimed once, then fixed. Your photos, notes and chats stay on this phone.",
            color = colors.ink.copy(alpha = 0.6f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(16.dp))

        val held = claimed
        if (held != null) {
            // Claimed once, and that is the end of it: the owner changes names, not the phone.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@$held",
                    color = colors.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (verified) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedTick(15.dp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This is your name. Only the owner can change it.",
                color = colors.ink.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        } else {
            PreReField(
                value = typing,
                onValueChange = {
                    typing = it
                    message = null
                },
                placeholder = "yourname",
                enabled = !busy,
            )
            Spacer(Modifier.height(16.dp))
            PreRePrimaryButton(
                label = "Claim name",
                onClick = { claim() },
                busy = busy,
                busyLabel = "Checking…",
                enabled = !busy,
            )
        }

        val note = message
        if (note != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = note,
                color = if (good) colors.ink else androidx.compose.ui.graphics.Color(0xFFE05252),
                fontSize = 13.sp,
            )
        }
    }
}
