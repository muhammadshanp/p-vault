package com.dudeapp.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Support: the way to reach the app's helper. One message to the admin, with an optional
 * screenshot or short video, and a reminder of what P Vault can already do so most questions
 * answer themselves. Nothing here needs a connection until the message is sent.
 */
@Composable
fun SupportScreen(
    onExternal: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var sent by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("Support", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Tell the helper what is wrong, or ask for something new.",
                        color = TextSoft,
                        fontSize = 11.sp,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                HelpCard()
                Spacer(Modifier.height(12.dp))
                if (sent) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Message sent", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "The admin has it now, together with your address, so a reply can reach you. " +
                                    "It is deleted after two days.",
                                color = TextSoft,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            androidx.compose.material3.TextButton(onClick = { sent = false }) {
                                Text("Send another")
                            }
                        }
                    }
                } else {
                    SuggestionForm(
                        onExternal = onExternal,
                        onSent = {
                            sent = true
                            problem = null
                        },
                        onProblem = { problem = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Signed in as ${GoogleAccountStore(context).email().orEmpty().ifBlank { "no Google account" }}",
                    color = TextSoft,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** What P Vault already answers by itself, so the common questions never need sending. */
@Composable
private fun HelpCard() {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .padding(16.dp),
    ) {
        Text("Before you write", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        listOf(
            "Missing a file? Pull down on the list, or use the search at the top.",
            "Backup: Settings → Back up now, or wait for the automatic one.",
            "A theme or a look is locked: ask the admin for access to it.",
            "The app will not update: menu → Check for updates.",
        ).forEach { line ->
            Text("•  $line", color = TextSoft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
