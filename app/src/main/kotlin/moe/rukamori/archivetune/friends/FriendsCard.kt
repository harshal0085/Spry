/*
 * Spry (2026) | friends list on the Music Together screen.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import android.app.Activity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
fun FriendsCard(modifier: Modifier = Modifier) {
    if (!SpryFriends.isAvailable) return
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val user by SpryFriends.user.collectAsState()
    val username by SpryFriends.username.collectAsState()
    val requests by SpryFriends.requests.collectAsState()
    val friends by SpryFriends.friends.collectAsState()
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

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "// friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (!username.isNullOrEmpty()) {
                    Text("@$username", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { scope.launch { SpryFriends.signOut(context) } }) { Text("sign out") }
                }
            }

            when {
                user == null -> {
                    Text("sign in to see which friends are online and join their music.")
                    Button(enabled = !busy, onClick = {
                        val activity = context.findActivity() ?: return@Button
                        run { SpryFriends.signIn(activity) }
                    }) { Text("Sign in with Google") }
                }

                username == null -> Text("loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)

                username!!.isEmpty() -> {
                    Text("pick a username. friends find you with it.")
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) },
                        prefix = { Text("@") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(enabled = !busy && input.isNotBlank(), onClick = {
                        run { SpryFriends.claimUsername(input).also { if (it == null) input = "" } }
                    }) { Text("Save") }
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.trim().take(21) },
                            placeholder = { Text("add by @username") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(enabled = !busy && input.isNotBlank(), onClick = {
                            run {
                                SpryFriends.find(input).fold(
                                    onSuccess = { p -> SpryFriends.sendRequest(p) ?: "request sent to @${p.username}".also { input = "" } },
                                    onFailure = { it.message },
                                )
                            }
                        }) { Text("Add") }
                    }

                    requests.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@${p.username} wants to be friends", Modifier.weight(1f))
                            TextButton(onClick = { run { SpryFriends.accept(p) } }) { Text("accept") }
                            TextButton(onClick = { scope.launch { SpryFriends.decline(p) } }) { Text("no") }
                        }
                    }

                    if (friends.isEmpty()) {
                        Text("no friends yet. add someone by their username.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("@${f.username}", fontFamily = FontFamily.Monospace)
                                Text(
                                    text = if (f.online) "online" else "offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (f.online) {
                                OutlinedButton(enabled = playerConnection != null, onClick = {
                                    playerConnection?.service?.let { SpryFriends.requestVibe(f, it) }
                                }) { Text("vibe together") }
                            } else {
                                TextButton(onClick = { scope.launch { SpryFriends.removeFriend(f.uid) } }) { Text("×") }
                            }
                        }
                    }
                }
            }

            val vibeStatus by SpryFriends.vibeStatus.collectAsState()
            vibeStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

/** Shown anywhere in the app when a friend asks to listen along. */
@Composable
fun VibeRequestDialog() {
    if (!SpryFriends.isAvailable) return
    val incoming by SpryFriends.incomingVibes.collectAsState()
    val from = incoming.firstOrNull() ?: return
    val service = LocalPlayerConnection.current?.service
    AlertDialog(
        onDismissRequest = {},
        title = { Text("@${from.username} wants to vibe together", fontFamily = FontFamily.Monospace) },
        text = { Text("they'll hear what you're playing, in sync.") },
        confirmButton = {
            Button(enabled = service != null, onClick = { service?.let { SpryFriends.acceptVibe(from, it) } }) { Text("let them in") }
        },
        dismissButton = { TextButton(onClick = { SpryFriends.declineVibe(from) }) { Text("not now") } },
    )
}
