/*
 * Spry (2026) | things every phone runs: publish what I'm playing, obey the developer's update gate,
 * maintenance notice, personal alerts and remote commands (play a song / leave a session).
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.playback.together.leaveTogether
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection

/**
 * Plays like tapping a song in the app: the song itself is handed to the player so it starts at once,
 * instead of waiting on a YouTube lookup that can fail silently (seen on the iQOO).
 */
fun SpryFriends.FeaturedSong.toQueue() =
    YouTubeQueue.radio(
        MediaMetadata(
            id = videoId,
            title = title,
            artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
            duration = -1,
            thumbnailUrl = thumb.ifEmpty { null },
        ),
    )

/** "just now", "5m ago", "3h ago", "2d ago". */
fun agoText(at: Long?, now: Long = SpryChat.serverNow()): String {
    if (at == null || at <= 0) return "a while ago"
    val m = (now - at).coerceAtLeast(0) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}

/** dev tag for developers, plus any custom badge the developer gave this person. */
@Composable
fun UserBadges(uid: String, modifier: Modifier = Modifier) {
    val admins by SpryFriends.admins.collectAsState()
    val badges by SpryFriends.badges.collectAsState()
    val custom = badges[uid]
    if (uid !in admins && custom == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (uid in admins) DevBadge()
        custom?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(start = if (uid in admins) 4.dp else 0.dp)
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun SpryGlobalHost() {
    if (!SpryFriends.isAvailable) return
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val config by SpryFriends.config.collectAsState()
    val isAdmin by SpryFriends.isAdmin.collectAsState()
    val alerts by SpryFriends.alerts.collectAsState()
    val command by SpryFriends.command.collectAsState()

    // what I'm playing, for friends' "Listening to …" and the developer's stats
    if (connection != null) {
        val meta by connection.mediaMetadata.collectAsState()
        val playing by connection.isPlaying.collectAsState()
        val text = meta?.takeIf { playing }?.let { m -> listOf(m.title, m.artists.joinToString { it.name }).filter { it.isNotBlank() }.joinToString(" · ") }
        LaunchedEffect(text) { SpryFriends.setPlaying(text) }

        val together by connection.service.togetherSessionState.collectAsState()
        var wasJoined by remember { mutableStateOf(false) }
        LaunchedEffect(together) {
            val joined = together is TogetherSessionState.Joined
            if (wasJoined && !joined) SpryFriends.onSessionLeft()
            wasJoined = joined
        }
    }

    // remote commands from the developer
    // keyed on connection too: a command that lands before the player binds waits for it instead of being dropped
    LaunchedEffect(command, connection) {
        val cmd = command ?: return@LaunchedEffect
        android.util.Log.i("Spry", "running command ${cmd.type} connection=${connection != null}")
        if (connection == null) return@LaunchedEffect
        val service = connection.service
        when (cmd.type) {
            "play" -> cmd.song?.let { connection.playQueue(it.toQueue()) }
            "kick" -> service?.leaveTogether()
        }
        SpryFriends.commandHandled(cmd)
    }

    // update gate: blocks the app until this build is new enough (developers are exempt)
    if (!isAdmin && config.minVersion > BuildConfig.VERSION_CODE) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Update Spry") },
            text = { Text("This version is too old. Get the new one to keep using Spry.") },
            confirmButton = {
                Button(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.updateUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Download update") }
            },
        )
        return
    }

    // maintenance: shown once each time it's switched on
    var seenMaintenance by rememberSaveable { mutableStateOf("") }
    val maintenanceKey = if (config.maintenanceOn) "on:${config.maintenanceText}" else ""
    if (!isAdmin && config.maintenanceOn && seenMaintenance != maintenanceKey) {
        AlertDialog(
            onDismissRequest = { seenMaintenance = maintenanceKey },
            title = { Text("Spry is being updated") },
            text = {
                Text(
                    config.maintenanceText.ifBlank { "Friends, chat and listening together are paused for a bit. Your music still plays." },
                )
            },
            confirmButton = { Button(onClick = { seenMaintenance = maintenanceKey }) { Text("OK") } },
        )
        return
    }

    // a personal message from the developer
    val alert = alerts.firstOrNull { !it.naez } ?: return // Naez's own alerts show in her bubble (NaezHost)
    AlertDialog(
        onDismissRequest = { SpryFriends.dismissAlert(alert) },
        title = { Text("From the developer") },
        text = { Text(alert.text) },
        confirmButton = { Button(onClick = { SpryFriends.dismissAlert(alert) }) { Text("Got it") } },
    )
}

/** Short line shown in Friends/Chat while maintenance is on. */
@Composable
fun maintenanceActive(): Boolean {
    val config by SpryFriends.config.collectAsState()
    val isAdmin by SpryFriends.isAdmin.collectAsState()
    return config.maintenanceOn && !isAdmin
}
