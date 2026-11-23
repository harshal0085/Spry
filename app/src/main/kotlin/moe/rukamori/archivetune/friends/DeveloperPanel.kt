/*
 * Spry (2026) | developer tools: everyone on Spry, ban / unban, reset a username,
 * listen in on any host, and a one-line announcement shown to every user.
 * Only shown to accounts listed under admins/ in the database; the rules enforce it.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.theme.spryGlass

/** Small "dev" tag shown next to a developer's username. */
@Composable
fun DevBadge(modifier: Modifier = Modifier) {
    Text(
        "dev",
        color = Color(0xFF0B0A10),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color(0xFFF4F1F8), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** The developer's announcement, shown once to each user (per announcement). */
@Composable
fun AnnouncementDialog() {
    if (!SpryFriends.isAvailable) return
    val announcement by SpryFriends.announcement.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("spry", Context.MODE_PRIVATE) }
    var seen by remember { mutableLongStateOf(prefs.getLong("announcement_seen", 0L)) }
    val (at, text) = announcement ?: return
    if (at <= seen) return
    fun dismiss() {
        prefs.edit().putLong("announcement_seen", at).apply()
        seen = at
    }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("From the developer") },
        text = { Text(text) },
        confirmButton = { Button(onClick = ::dismiss) { Text("Got it") } },
    )
}

@Composable
fun DeveloperPanel(modifier: Modifier = Modifier) {
    val isAdmin by SpryFriends.isAdmin.collectAsState()
    if (!isAdmin) return
    val users by SpryFriends.allUsers.collectAsState()
    val announcement by SpryFriends.announcement.collectAsState()
    val service = LocalPlayerConnection.current?.service
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> String?>?>(null) }

    fun run(block: suspend () -> String?) {
        scope.launch { message = block() ?: "Done" }
    }

    fun ask(question: String, action: suspend () -> String?) {
        confirm = question to action
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .spryGlass(RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Developer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            DevBadge(Modifier.padding(start = 8.dp))
        }

        Text("Announcement to everyone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        announcement?.let { (_, text) ->
            Text("Live now: \"$text\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(500) },
            placeholder = { Text("Pops up once on every phone") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = draft.isNotBlank(), onClick = { run { SpryFriends.announce(draft).also { if (it == null) draft = "" } } }) { Text("Send") }
            if (announcement != null) TextButton(onClick = { run { SpryFriends.clearAnnouncement() } }) { Text("Take down") }
        }

        Text(
            "Everyone on Spry · ${users.size} (${users.count { it.online }} online)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        users.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val dot = Modifier.size(9.dp)
                Box(if (u.online) dot.background(Color.White, CircleShape) else dot.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (u.username.isEmpty()) "(no username yet)" else "@${u.username}")
                        if (u.admin) DevBadge(Modifier.padding(start = 6.dp))
                    }
                    Text(
                        listOfNotNull(
                            if (u.online) "Online" else "Offline",
                            "hosting".takeIf { u.hosting },
                            "BANNED".takeIf { u.banned },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (u.hosting && service != null) {
                    TextButton(onClick = { SpryFriends.adminJoin(u, service) }) { Text("Listen") }
                }
                if (!u.admin) {
                    if (u.banned) {
                        TextButton(onClick = { run { SpryFriends.unban(u) } }) { Text("Unban") }
                    } else {
                        TextButton(onClick = { ask("Ban @${u.username.ifEmpty { "this user" }}?") { SpryFriends.ban(u) } }) {
                            Text("Ban", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (u.username.isNotEmpty()) {
                        TextButton(onClick = { ask("Reset @${u.username}'s username? They'll pick a new one.") { SpryFriends.resetUsername(u) } }) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    confirm?.let { (question, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(question) },
            confirmButton = {
                Button(onClick = {
                    confirm = null
                    run(action)
                }) { Text("Yes") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
