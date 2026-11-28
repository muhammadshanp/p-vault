package com.dudeapp.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import kotlinx.coroutines.launch

/**
 * The owner's view of the names on the server: who is registered, and what each name may use.
 *
 * This is the only screen that reads the usernames table, and it can only do so with the
 * passphrase, whose SHA-256 the database compares. Nothing is granted by default beyond the
 * two free looks: a name starts at "No access" and the owner decides.
 */
@Composable
internal fun AdminAccessCard(
    /** A passphrase already accepted elsewhere in the panel, so it is not typed twice. */
    initialPassphrase: String = "",
    /** Hands the accepted passphrase back, for the pages that need the same key. */
    onUnlocked: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var passphrase by remember { mutableStateOf(initialPassphrase) }
    var unlocked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var names by remember { mutableStateOf<List<Supabase.RemoteName>>(emptyList()) }
    var reserved by remember { mutableStateOf<List<Supabase.ReservedName>>(emptyList()) }
    var saving by remember { mutableStateOf<String?>(null) }
    var reserveDraft by remember { mutableStateOf("") }
    var owners by remember { mutableStateOf<List<Supabase.Owner>>(emptyList()) }
    var renameAllowed by remember { mutableStateOf(true) }
    var ownerDraft by remember { mutableStateOf("") }
    var reserveNote by remember { mutableStateOf("") }
    // The name being changed, and the field it is being changed to.
    var renameTarget by remember { mutableStateOf<Supabase.RemoteName?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    fun load() {
        busy = true
        problem = null
        scope.launch {
            val valid = Supabase.isAdminKey(context, passphrase)
            val list = if (valid) Supabase.adminNames(context, passphrase) else null
            val held = if (valid) Supabase.adminReserved(context, passphrase) else null
            val adminList = if (valid) Supabase.adminOwners(context, passphrase) else null
            busy = false
            if (list == null || held == null) {
                problem = "The passphrase was not accepted, or the server could not be reached."
                unlocked = false
            } else {
                names = list
                reserved = held
                if (adminList != null && adminList.second) owners = adminList.first
                renameAllowed = Supabase.canRename(context)
                unlocked = true
                onUnlocked(passphrase)
                // Holding the owner passphrase is proof this address is the owner. The
                // panel flag is set locally as well, so the entry is there on the next
                // start even before the server answers, and works offline.
                GoogleAccountStore(context).email()?.let { address ->
                    runCatching { AdminStore(context).save(address) }
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Names on the server",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Only the passphrase opens this list. It is not stored in the app and not " +
                "kept on this phone — type it each time you look.",
            color = TextSoft,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = passphrase,
            onValueChange = {
                passphrase = it
                unlocked = false
                problem = null
            },
            label = { Text("Owner passphrase") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            trailingIcon = {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { load() },
                enabled = passphrase.isNotBlank() && !busy,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (unlocked) "Reload" else "Unlock")
            }
            if (unlocked) {
                IconButton(onClick = { load() }, enabled = !busy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Reload the list")
                }
            }
        }

        val note = problem
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(note, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (unlocked) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (names.isEmpty()) {
                    "No names have been claimed yet."
                } else {
                    "${names.size} name${if (names.size == 1) "" else "s"} claimed"
                },
                color = TextSoft,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            names.forEach { name ->
                NameAccessRow(
                    name = name,
                    saving = saving == name.username,
                    onRename = {
                        renameTarget = name
                        renameDraft = name.username
                        problem = null
                    },
                    onTick = { tick ->
                        val wanted = when (tick) {
                            AccessTick.Blue -> "blue"
                            AccessTick.Black -> "black"
                            AccessTick.None -> "none"
                        }
                        scope.launch {
                            val status = Supabase.adminSetTick(context, passphrase, name.username, wanted)
                            if (status == "ok") {
                                names = names.map {
                                    if (it.username == name.username) {
                                        it.copy(
                                            verified = tick == AccessTick.Blue,
                                            partner = tick == AccessTick.Black,
                                        )
                                    } else {
                                        it
                                    }
                                }
                                problem = when (tick) {
                                    AccessTick.Blue -> "@${name.username} wears the blue tick."
                                    AccessTick.Black -> "@${name.username} wears the black tick."
                                    AccessTick.None -> "The tick was taken off @${name.username}."
                                }
                            } else {
                                problem = "Could not change the tick. Check the connection."
                            }
                        }
                    },
                    onSave = { level, features ->
                        saving = name.username
                        problem = null
                        scope.launch {
                            val ok = Supabase.setAccess(
                                context = context,
                                passphrase = passphrase,
                                username = name.username,
                                level = level,
                                features = features,
                            )
                            saving = null
                            if (ok) {
                                names = names.map {
                                    if (it.username == name.username) {
                                        it.copy(level = level, features = features)
                                    } else {
                                        it
                                    }
                                }
                            } else {
                                problem = "Could not save @${name.username}. Check the connection."
                            }
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
            }

            // ---- names set aside ----
            Spacer(Modifier.height(6.dp))
            Text("Reserved names", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "Nobody can claim one of these, even before it is used.",
                color = TextSoft,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = reserveDraft,
                onValueChange = { reserveDraft = normalizeUsername(it).take(24); problem = null },
                label = { Text("Name to set aside") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = reserveNote,
                onValueChange = { reserveNote = it.take(60) },
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val wanted = normalizeUsername(reserveDraft)
                    if (wanted.isEmpty()) return@Button
                    busy = true
                    scope.launch {
                        val status = Supabase.adminReserve(context, passphrase, wanted, reserveNote.trim())
                        busy = false
                        when (status) {
                            "ok" -> {
                                reserved = (reserved.filterNot { it.name == wanted } + Supabase.ReservedName(wanted, reserveNote.trim()))
                                    .sortedBy { it.name }
                                reserveDraft = ""
                                reserveNote = ""
                                problem = "@$wanted is set aside."
                            }
                            null -> problem = "Could not reach the server."
                            else -> problem = Supabase.claimProblem(status)
                        }
                    }
                },
                enabled = reserveDraft.isNotBlank() && !busy,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Set aside")
            }

            Spacer(Modifier.height(10.dp))
            if (reserved.isEmpty()) {
                Text("Nothing is set aside yet.", color = TextSoft, fontSize = 12.sp)
            } else {
                reserved.forEach { held ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("@${held.name}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (held.note.isNotBlank()) {
                                    Text(held.note, color = TextSoft, fontSize = 11.sp)
                                }
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val ok = Supabase.adminUnreserve(context, passphrase, held.name)
                                        if (ok) {
                                            reserved = reserved.filterNot { it.name == held.name }
                                            problem = "@${held.name} is free again."
                                        } else {
                                            problem = "Could not release @${held.name}."
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Release the name")
                            }
                        }
                    }
                }
            }

            // ---- switches that need no update ----
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(14.dp))
            Text("Switches", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "These are read from the server by every phone, so changing one here takes " +
                    "effect without anyone installing an update.",
                color = TextSoft,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("People can change their name", fontSize = 13.sp)
                    Text(
                        "Off: the name a person holds is fixed, and only the owner can move it " +
                            "from this page.",
                        color = TextSoft,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = renameAllowed,
                    enabled = !busy,
                    onCheckedChange = { on ->
                        renameAllowed = on
                        scope.launch {
                            val ok = Supabase.adminSetRenameAllowed(context, passphrase, on)
                            if (!ok) {
                                renameAllowed = !on
                                problem = "Could not change that switch. Check the connection."
                            } else {
                                problem = if (on) {
                                    "People can change their own name again."
                                } else {
                                    "Name changes are off for everyone."
                                }
                            }
                        }
                    },
                )
            }

            // ---- who may open the admin panel ----
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(14.dp))
            Text("Admin accounts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "A Google address on this list sees the admin panel the moment it signs in, and " +
                    "loses it when it signs out. Nothing else is needed.",
                color = TextSoft,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ownerDraft,
                    onValueChange = { ownerDraft = it.trim().take(80); problem = null },
                    label = { Text("Google address") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val wanted = cleanEmail(ownerDraft)
                        if (wanted == null) {
                            problem = "That does not look like an email address."
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val status = Supabase.adminAddOwner(context, passphrase, wanted)
                            busy = false
                            problem = when (status) {
                                "ok" -> {
                                    ownerDraft = ""
                                    owners = owners.filterNot { it.email == wanted } + Supabase.Owner(wanted, "")
                                    "@$wanted can open the admin panel now."
                                }
                                null -> "Could not reach the server."
                                else -> "That address was refused ($status)."
                            }
                        }
                    },
                    enabled = ownerDraft.isNotBlank() && !busy,
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
            if (owners.isEmpty()) {
                Text("No admin addresses yet.", color = TextSoft, fontSize = 12.sp)
            } else {
                owners.forEach { owner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(owner.email, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val ok = Supabase.adminRemoveOwner(context, passphrase, owner.email)
                                    if (ok) {
                                        owners = owners.filterNot { it.email == owner.email }
                                        problem = "${owner.email} can no longer open the admin panel."
                                    } else {
                                        problem = "Could not remove ${owner.email}."
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove this admin")
                        }
                    }
                }
            }
        }
    }

    // ---- changing a name ----
    val target = renameTarget
    if (target != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Change @${target.username}") },
            text = {
                Column {
                    Text(
                        "The person keeps their access; it moves with the new name. Their phone " +
                            "picks the new name up the next time P Vault opens.",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = normalizeUsername(it).take(24); problem = null },
                        label = { Text("New name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val wanted = normalizeUsername(renameDraft)
                        busy = true
                        scope.launch {
                            val result = Supabase.adminRename(
                                context = context,
                                passphrase = passphrase,
                                old = target.username,
                                new = wanted,
                            )
                            busy = false
                            if (result == null) {
                                problem = "Could not reach the server."
                            } else if (result.status == "ok" && result.name != null) {
                                names = names.map {
                                    if (it.username == target.username) {
                                        it.copy(username = result.name.orEmpty())
                                    } else {
                                        it
                                    }
                                }
                                problem = "@${target.username} is now @${result.name}."
                                renameTarget = null
                            } else {
                                problem = Supabase.claimProblem(result.status)
                            }
                        }
                    },
                    enabled = renameDraft.isNotBlank() && !busy,
                ) { Text("Change it") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }
}

/** One claimed name: its level, its switches, and when it was taken. */
@Composable
private fun NameAccessRow(
    name: Supabase.RemoteName,
    saving: Boolean,
    onRename: () -> Unit,
    onTick: (AccessTick) -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var level by remember(name.username, name.level) { mutableStateOf(name.level) }
    var features by remember(name.username, name.features) { mutableStateOf(name.features.toSet()) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@${name.username}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (name.verified || name.partner) {
                            Spacer(Modifier.width(4.dp))
                            TickBadge(
                                if (name.verified) AccessTick.Blue else AccessTick.Black,
                                14.dp,
                            )
                        }
                    }
                    Text(
                        text = if (name.claimedAt.length >= 10) {
                            "claimed " + name.claimedAt.take(10)
                        } else {
                            "claimed"
                        },
                        color = TextSoft,
                        fontSize = 11.sp,
                    )
                }
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                IconButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Change this name")
                }
            }
            Spacer(Modifier.height(10.dp))

            // The levels, in the same order the app understands them.
            val levels = listOf(
                "NONE" to "No access",
                "THEME" to "Theme",
                "ADVANCED" to "Advanced",
                "EVERYTHING" to "Everything",
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                levels.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { (key, label) ->
                            val active = level == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        level = key
                                        onSave(key, features.toList())
                                    },
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tick", fontSize = 13.sp)
                    Text(
                        "Blue for the admin role, black for a partner.",
                        color = TextSoft,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TickChooser(
                selected = when {
                    name.verified -> AccessTick.Blue
                    name.partner -> AccessTick.Black
                    else -> AccessTick.None
                },
                enabled = !saving,
                onSelect = onTick,
            )

            AccessFeature.entries.forEach { feature ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(feature.label, fontSize = 13.sp)
                        Text(feature.detail, color = TextSoft, fontSize = 11.sp)
                    }
                    Switch(
                        checked = feature.key in features,
                        onCheckedChange = { on ->
                            features = if (on) features + feature.key else features - feature.key
                            onSave(level, features.toList())
                        },
                    )
                }
            }
        }
    }
}
