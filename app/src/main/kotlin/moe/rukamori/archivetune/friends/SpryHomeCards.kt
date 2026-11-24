/*
 * Spry (2026) | Home: the developer's pick and the developer's live room.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.theme.spryGlass

@Composable
fun SpryHomeCards(modifier: Modifier = Modifier) {
    if (!SpryFriends.isAvailable) return
    val config by SpryFriends.config.collectAsState()
    val user by SpryFriends.user.collectAsState()
    val connection = LocalPlayerConnection.current
    val featured = config.featured
    val paused = maintenanceActive()
    val roomLive by SpryFriends.roomLive.collectAsState()
    val room = config.room?.takeIf { it.uid != user?.uid && user != null && !paused && roomLive }
    val inRoom = connection?.service?.togetherSessionState?.collectAsState()?.value is TogetherSessionState.Joined
    if (featured == null && room == null) return

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        room?.let { r ->
            GlassRow {
                Column(Modifier.weight(1f)) {
                    Text("LIVE NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Listen along in sync", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    enabled = connection != null && !inRoom,
                    onClick = { connection?.service?.let { SpryFriends.joinRoom(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4F1F8), contentColor = Color(0xFF0B0A10)),
                ) { Text(if (inRoom) "Joined" else "Join") }
            }
        }
        featured?.let { f ->
            GlassRow {
                AsyncImage(
                    model = f.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("DEV'S PICK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(f.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    enabled = connection != null,
                    onClick = { connection?.playQueue(f.toQueue()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4F1F8), contentColor = Color(0xFF0B0A10)),
                ) { Text("Play") }
            }
        }
    }
}

@Composable
private fun GlassRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().spryGlass(RoundedCornerShape(24.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
