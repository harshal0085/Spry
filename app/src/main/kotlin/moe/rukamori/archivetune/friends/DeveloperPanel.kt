/*
 * Spry (2026) | developer tools. Only shown to accounts listed under admins/ in the database;
 * the database rules enforce every power, this screen only shows the controls.
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
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
        maxLines = 1,
        softWrap = false,
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

private sealed interface Ask {
    data class Confirm(val question: String, val action: suspend () -> String?) : Ask

    data class Input(val title: String, val hint: String, val initial: String, val action: suspend (String) -> String?) : Ask
}

@Composable
fun DeveloperPanel(modifier: Modifier = Modifier) {
    val isAdmin by SpryFriends.isAdmin.collectAsState()
    if (!isAdmin) return
    val users by SpryFriends.allUsers.collectAsState()
    val stats by SpryFriends.stats.collectAsState()
    val config by SpryFriends.config.collectAsState()
    val announcement by SpryFriends.announcement.collectAsState()
    val hidden by SpryFriends.hidden.collectAsState()
    val me by SpryFriends.user.collectAsState()
    val connection = LocalPlayerConnection.current
    val service = connection?.service
    val nowPlaying = connection?.mediaMetadata?.collectAsState()?.value
    val currentSong =
        nowPlaying?.let { m ->
            SpryFriends.FeaturedSong(m.id, m.title, m.artists.joinToString { it.name }, m.thumbnailUrl.orEmpty())
        }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var maintenanceText by remember(config.maintenanceText) { mutableStateOf(config.maintenanceText) }
    var updateUrl by remember(config.updateUrl) { mutableStateOf(config.updateUrl) }
    var message by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<Ask?>(null) }

    fun run(block: suspend () -> String?) {
        scope.launch { message = block() ?: "Done" }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .spryGlass(RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ---- stats
        Section("Spry right now")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Users", stats.users, Modifier.weight(1f))
            Stat("Online", stats.online, Modifier.weight(1f))
            Stat("Hosting", stats.hosting, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Active today", stats.activeDay, Modifier.weight(1f))
            Stat("This week", stats.activeWeek, Modifier.weight(1f))
            Stat("Messages today", stats.messagesToday, Modifier.weight(1f))
        }
        if (stats.topPlaying.isNotEmpty()) {
            Text(
                "Playing now: " + stats.topPlaying.joinToString("  ·  ") { (song, n) -> if (n > 1) "$song ($n)" else song },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- announcement
        Section("Announcement to everyone")
        announcement?.let { (_, text) -> Hint("Live now: \"$text\"") }
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

        // ---- music for everyone
        Section("Music")
        Hint(config.featured?.let { "Dev's pick on everyone's Home: ${it.title}" } ?: "No Dev's pick on Home")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = currentSong != null, onClick = { run { SpryFriends.setFeatured(currentSong) } }) { Text("Feature what I'm playing") }
            if (config.featured != null) TextButton(onClick = { run { SpryFriends.setFeatured(null) } }) { Text("Clear", maxLines = 1, softWrap = false) }
        }
        Button(
            enabled = currentSong != null && users.any { it.online },
            onClick = {
                val song = currentSong ?: return@Button
                ask = Ask.Confirm("Start \"${song.title}\" on every online phone (${users.count { it.online }})?") {
                    SpryFriends.forcePlay(users.filter { it.online && !it.admin }, song)
                }
            },
        ) { Text("Play my song for everyone online") }
        val myRoomOpen = config.room?.uid == me?.uid && me != null
        Hint(if (myRoomOpen) "Your room is live on everyone's Home" else "Open a room anyone can join from Home")
        Button(enabled = service != null, onClick = { service?.let { if (myRoomOpen) SpryFriends.closeRoom(it) else SpryFriends.openRoom(it) } }) {
            Text(if (myRoomOpen) "Close my room" else "Open my room")
        }

        // ---- app switches
        Section("App")
        SwitchRow("Maintenance mode", "Pauses friends, chat and rooms for everyone", config.maintenanceOn) { on ->
            run { SpryFriends.setMaintenance(on, maintenanceText) }
        }
        OutlinedTextField(
            value = maintenanceText,
            onValueChange = { maintenanceText = it.take(300) },
            placeholder = { Text("Message during maintenance (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(
            if (config.minVersion > 0) {
                "Phones below version ${config.minVersion} must update (this build is ${BuildConfig.VERSION_CODE})"
            } else {
                "No minimum version (this build is ${BuildConfig.VERSION_CODE})"
            },
        )
        OutlinedTextField(
            value = updateUrl,
            onValueChange = { updateUrl = it },
            label = { Text("Download link for updates") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                ask = Ask.Confirm("Make everyone below version ${BuildConfig.VERSION_CODE} update before using Spry?") {
                    SpryFriends.setMinVersion(BuildConfig.VERSION_CODE, updateUrl)
                }
            }) { Text("Require this version") }
            if (config.minVersion > 0) TextButton(onClick = { run { SpryFriends.setMinVersion(0, updateUrl) } }) { Text("Clear") }
        }
        SwitchRow("Hidden mode", "You look offline to everyone, and your music is hidden", hidden) { SpryFriends.setHidden(it) }

        // ---- everyone
        Section("Everyone on Spry · ${users.size}")
        users.forEach { u -> UserRowItem(u, service != null, onAsk = { ask = it }, run = ::run, onListen = { service?.let { s -> SpryFriends.adminJoin(u, s) } }, currentSong = currentSong) }
        message?.let { Hint(it) }
    }

    when (val a = ask) {
        is Ask.Confirm ->
            AlertDialog(
                onDismissRequest = { ask = null },
                title = { Text(a.question) },
                confirmButton = {
                    Button(onClick = {
                        ask = null
                        run(a.action)
                    }) { Text("Yes") }
                },
                dismissButton = { TextButton(onClick = { ask = null }) { Text("Cancel") } },
            )

        is Ask.Input -> {
            var text by remember(a) { mutableStateOf(a.initial) }
            AlertDialog(
                onDismissRequest = { ask = null },
                title = { Text(a.title) },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text(a.hint) }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    Button(onClick = {
                        ask = null
                        run { a.action(text) }
                    }) { Text(if (a.title.startsWith("Alert")) "Send" else "Save") }
                },
                dismissButton = { TextButton(onClick = { ask = null }) { Text("Cancel") } },
            )
        }

        null -> Unit
    }
}

@Composable
private fun UserRowItem(
    u: SpryFriends.UserRow,
    canListen: Boolean,
    onAsk: (Ask) -> Unit,
    run: (suspend () -> String?) -> Unit,
    onListen: () -> Unit,
    currentSong: SpryFriends.FeaturedSong?,
) {
    var menu by remember { mutableStateOf(false) }
    val name = if (u.username.isEmpty()) "this user" else "@${u.username}"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val dot = Modifier.size(9.dp)
        Box(if (u.online) dot.background(Color.White, CircleShape) else dot.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (u.username.isEmpty()) "(no username yet)" else "@${u.username}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                UserBadges(u.uid, Modifier.padding(start = 6.dp))
            }
            Text(
                listOfNotNull(
                    when {
                        u.online && u.playing != null -> "Listening to ${u.playing}"
                        u.online -> "Online"
                        else -> "Last seen ${agoText(u.lastSeen)}"
                    },
                    "hosting".takeIf { u.hosting },
                    "BANNED".takeIf { u.banned },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Actions for $name") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (u.hosting && canListen) DropdownMenuItem(text = { Text("Listen in") }, onClick = { menu = false; onListen() })
                if (u.online) DropdownMenuItem(text = { Text("Kick from session") }, onClick = { menu = false; run { SpryFriends.kick(u) } })
                if (u.online && currentSong != null) {
                    DropdownMenuItem(text = { Text("Play my song for them") }, onClick = { menu = false; run { SpryFriends.forcePlay(listOf(u), currentSong) } })
                }
                DropdownMenuItem(text = { Text("Send an alert") }, onClick = {
                    menu = false
                    onAsk(Ask.Input("Alert $name", "Pops up on their phone", "") { SpryFriends.sendAlert(u, it) })
                })
                DropdownMenuItem(text = { Text(if (u.badge == null) "Give a badge" else "Change badge") }, onClick = {
                    menu = false
                    onAsk(Ask.Input("Badge for $name", "e.g. OG, tester, VIP (empty removes it)", u.badge.orEmpty()) { SpryFriends.setBadge(u, it) })
                })
                if (!u.admin) {
                    if (u.username.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Reset username") }, onClick = {
                            menu = false
                            onAsk(Ask.Confirm("Reset $name's username? They'll pick a new one.") { SpryFriends.resetUsername(u) })
                        })
                    }
                    DropdownMenuItem(text = { Text(if (u.banned) "Unban" else "Ban") }, onClick = {
                        menu = false
                        if (u.banned) run { SpryFriends.unban(u) } else onAsk(Ask.Confirm("Ban $name?") { SpryFriends.ban(u) })
                    })
                    DropdownMenuItem(text = { Text("Delete all their data", color = MaterialTheme.colorScheme.error) }, onClick = {
                        menu = false
                        onAsk(Ask.Confirm("Delete everything Spry stores about $name? Username, friends, chats and requests are wiped. This can't be undone.") { SpryFriends.deleteAllData(u) })
                    })
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier.spryGlass(RoundedCornerShape(16.dp), glints = false).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Hint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
