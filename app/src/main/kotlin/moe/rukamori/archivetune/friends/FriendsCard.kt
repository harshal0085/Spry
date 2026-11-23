/*
 * Spry (2026) | friends list on the Music Together screen.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import android.app.Activity
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import moe.rukamori.archivetune.ui.theme.spryGlass
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun FriendsCard(
    modifier: Modifier = Modifier,
    onChat: ((SpryFriends.Friend) -> Unit)? = null,
) {
    if (!SpryFriends.isAvailable) return
    val context = LocalContext.current
    val service = LocalPlayerConnection.current?.service
    val scope = rememberCoroutineScope()
    val user by SpryFriends.user.collectAsState()
    val username by SpryFriends.username.collectAsState()
    val requests by SpryFriends.requests.collectAsState()
    val friends by SpryFriends.friends.collectAsState()
    val vibeStatus by SpryFriends.vibeStatus.collectAsState()
    val banned by SpryFriends.banned.collectAsState()
    val isAdmin by SpryFriends.isAdmin.collectAsState()
    val maintenance = maintenanceActive()
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(block: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            message = block()
            busy = false
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            user == null -> {
                Text("Sign in to see when friends are online and listen together.")
                Button(enabled = !busy, onClick = {
                    val activity = context.findActivity() ?: return@Button
                    run { SpryFriends.signIn(activity) }
                }) { Text("Sign in with Google") }
            }

            username == null -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)

            username!!.isEmpty() -> {
                Text("Choose a username. Friends use it to find you.")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) },
                    prefix = { Text("@") },
                    placeholder = { Text("yourname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(enabled = !busy && input.length >= 3, onClick = {
                    run { SpryFriends.claimUsername(input).also { if (it == null) input = "" } }
                }) { Text("Save username") }
            }

            banned != null -> {
                Text("Your account has been banned", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "You can still play music, but friends and listening together are switched off. Reason: $banned",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { scope.launch { SpryFriends.signOut(context) } }) { Text("Sign out") }
            }

            maintenance -> {
                Text("Spry is being updated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Friends, chat and listening together are paused for a bit. Your music still plays.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("You're @$username", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isAdmin) DevBadge(Modifier.padding(start = 8.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { SpryFriends.signOut(context) } }) { Text("Sign out") }
                }

                Text("Add a friend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim().take(21) },
                        placeholder = { Text("Their username") },
                        prefix = { Text("@") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(enabled = !busy && input.isNotBlank(), onClick = {
                        run {
                            SpryFriends.find(input).fold(
                                onSuccess = { p -> SpryFriends.sendRequest(p) ?: "Request sent to @${p.username}".also { input = "" } },
                                onFailure = { it.message },
                            )
                        }
                    }) { Text("Add") }
                }

                if (requests.isNotEmpty()) {
                    Text("Friend requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    requests.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@${p.username}", modifier = Modifier.weight(1f))
                            Button(onClick = { run { SpryFriends.accept(p) } }) { Text("Accept") }
                            TextButton(onClick = { scope.launch { SpryFriends.decline(p) } }) { Text("Decline") }
                        }
                    }
                }

                Text("Your friends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (friends.isEmpty()) {
                    Text("No friends yet. Add someone by their username above.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                friends.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val dot = Modifier.size(10.dp)
                        Box(
                            if (f.online) {
                                dot.background(MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                dot.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "@${f.username}",
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                UserBadges(f.uid, Modifier.padding(start = 6.dp))
                            }
                            Text(
                                text =
                                    when {
                                        f.online && f.playing != null -> "Listening to ${f.playing}"
                                        f.online -> "Online"
                                        else -> "Last seen ${agoText(f.lastSeen)}"
                                    },
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (onChat != null) {
                            val last by remember(f.uid) { SpryChat.lastActivity(f.uid) }.collectAsState(initial = null)
                            val unread = last?.let { it > chatLastRead(context, f.uid) } == true
                            Box {
                                androidx.compose.material3.IconButton(onClick = { onChat(f) }) {
                                    androidx.compose.material3.Icon(
                                        androidx.compose.material.icons.Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = "Chat with @${f.username}",
                                    )
                                }
                                if (unread) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 10.dp, end = 10.dp)
                                            .size(9.dp)
                                            .background(androidx.compose.ui.graphics.Color.White, CircleShape),
                                    )
                                }
                            }
                        }
                        if (f.online) {
                            OutlinedButton(enabled = service != null, onClick = { service?.let { SpryFriends.requestVibe(f, it) } }) { Text("Join") }
                            TextButton(onClick = { message = SpryFriends.invite(f) }) { Text("Invite") }
                        } else {
                            TextButton(onClick = { scope.launch { SpryFriends.removeFriend(f.uid) } }) {
                                Text("Remove", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(
                    "Join asks to listen to their music. Invite asks them to listen to yours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        (vibeStatus ?: message)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
/**
 * Shown anywhere in the app. Two kinds of ask:
 * a friend wants to listen to MY music (vibe), or a friend invites ME to theirs (invite).
 * A vibe request from someone I invited is let in without asking again.
 */
@Composable
fun VibeRequestDialog() {
    if (!SpryFriends.isAvailable) return
    val vibes by SpryFriends.incomingVibes.collectAsState()
    val invites by SpryFriends.incomingInvites.collectAsState()
    val banned by SpryFriends.banned.collectAsState()
    val service = LocalPlayerConnection.current?.service ?: return
    if (banned != null) return

    val vibe = vibes.firstOrNull()
    if (vibe != null && SpryFriends.wasInvited(vibe.uid)) {
        LaunchedEffect(vibe.uid) { SpryFriends.acceptVibe(vibe, service) }
        return
    }
    if (vibe != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("@${vibe.username} wants to listen with you") },
            text = { Text("They'll hear what you're playing, in sync. They can pause their own phone, nothing else.") },
            confirmButton = { Button(onClick = { SpryFriends.acceptVibe(vibe, service) }) { Text("Let them in") } },
            dismissButton = { TextButton(onClick = { SpryFriends.declineVibe(vibe) }) { Text("Not now") } },
        )
        return
    }
    val invite = invites.firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("@${invite.username} invited you") },
        text = { Text("Join to hear what they're playing, in sync. Your own music stops while you listen.") },
        confirmButton = { Button(onClick = { SpryFriends.acceptInvite(invite, service) }) { Text("Join") } },
        dismissButton = { TextButton(onClick = { SpryFriends.declineInvite(invite) }) { Text("Not now") } },
    )
}

/**
 * Home: the online panel. Friend requests on top, then everyone online with both directions:
 * "join" asks to listen to their music, "invite" asks them to listen to yours.
 * Hidden when signed out and nothing is pending.
 */
@Composable
fun OnlineFriendsStrip(
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!SpryFriends.isAvailable) return
    val user by SpryFriends.user.collectAsState()
    val friends by SpryFriends.friends.collectAsState()
    val requests by SpryFriends.requests.collectAsState()
    val status by SpryFriends.vibeStatus.collectAsState()
    val banned by SpryFriends.banned.collectAsState()
    if (banned != null) return
    val service = LocalPlayerConnection.current?.service
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    if (user == null) return
    val online = friends.filter { it.online }
    // Spry: nothing to act on → take no space on Home.
    if (online.isEmpty() && requests.isEmpty() && status == null && note == null) return

    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .spryGlass(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (online.isEmpty()) "Online" else "Online · ${online.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenFriends) { Text("See all", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        requests.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${p.username} wants to be friends", modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { SpryFriends.accept(p) } }) { Text("Accept") }
                TextButton(onClick = { scope.launch { SpryFriends.decline(p) } }) { Text("No") }
            }
        }
        online.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text("@${f.username}", modifier = Modifier.weight(1f))
                OutlinedButton(enabled = service != null, onClick = { service?.let { SpryFriends.requestVibe(f, it) } }) { Text("Join") }
                TextButton(onClick = { note = SpryFriends.invite(f) }) { Text("Invite") }
            }
        }
        (status ?: note)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}