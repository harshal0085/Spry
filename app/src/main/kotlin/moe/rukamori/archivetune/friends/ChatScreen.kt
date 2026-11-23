/*
 * Spry (2026) | the 24-hour chat with one friend, in Spry glass.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.theme.spryGlass

private const val ReadPrefs = "spry_chat"

/** When I last looked at the chat with [friendUid] (server time), for the unread dot. */
fun chatLastRead(context: Context, friendUid: String): Long =
    context.getSharedPreferences(ReadPrefs, Context.MODE_PRIVATE).getLong(friendUid, 0L)

private fun markRead(context: Context, friendUid: String, at: Long) {
    val prefs = context.getSharedPreferences(ReadPrefs, Context.MODE_PRIVATE)
    if (at > prefs.getLong(friendUid, 0L)) prefs.edit().putLong(friendUid, at).apply()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    navController: NavController,
    friendUid: String,
    friendName: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = SpryFriends.user.collectAsState().value?.uid
    val messages by remember(friendUid) { SpryChat.messages(friendUid) }.collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var unsend by remember { mutableStateOf<SpryChat.Message?>(null) }
    // re-evaluated every minute so messages that pass 24h while the chat is open disappear too
    var now by remember { mutableStateOf(SpryChat.serverNow()) }

    LaunchedEffect(friendUid) {
        SpryChat.cleanup(friendUid)
        while (true) {
            delay(60_000)
            now = SpryChat.serverNow()
        }
    }
    val visible = messages.filter { it.at > now - SpryChat.TTL_MS }
    LaunchedEffect(visible.lastOrNull()?.id) {
        markRead(context, friendUid, visible.lastOrNull()?.at ?: SpryChat.serverNow())
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .spryGlass(RoundedCornerShape(24.dp))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("@$friendName", fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    "Messages disappear after 24 hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (visible.isEmpty()) {
                item {
                    Text(
                        "Say hi. Nothing here is kept longer than a day.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                }
            }
            items(visible.asReversed(), key = { it.id }) { m ->
                val mine = m.from == me
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    val shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 6.dp else 20.dp, if (mine) 20.dp else 6.dp)
                    Column(
                        Modifier
                            .widthIn(max = 290.dp)
                            .then(
                                if (mine) {
                                    Modifier.background(Color(0xFFF4F1F8), shape)
                                } else {
                                    Modifier.spryGlass(shape, glints = false)
                                },
                            )
                            .combinedClickable(onClick = {}, onLongClick = { if (mine) unsend = m })
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(m.text, color = if (mine) Color(0xFF0B0A10) else Color.White, fontSize = 15.sp)
                        Text(
                            DateFormat.getTimeFormat(context).format(m.at),
                            fontSize = 10.sp,
                            color = if (mine) Color(0x990B0A10) else Color(0x99FFFFFF),
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        }

        Row(
            Modifier
                // above the mini player and nav bar, or above the keyboard when it's open (whichever is taller)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .spryGlass(RoundedCornerShape(50), glints = false)
                .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (draft.isEmpty()) Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(1000) },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    val text = draft
                    draft = ""
                    scope.launch { error = SpryChat.send(friendUid, text).also { if (it != null) draft = text } }
                },
                modifier = Modifier.size(44.dp).background(if (draft.isNotBlank()) Color(0xFFF4F1F8) else Color.Transparent, CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = if (draft.isNotBlank()) Color(0xFF0B0A10) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    unsend?.let { m ->
        AlertDialog(
            onDismissRequest = { unsend = null },
            title = { Text("Unsend this message?") },
            text = { Text("It disappears for both of you.") },
            confirmButton = {
                Button(onClick = {
                    unsend = null
                    scope.launch { SpryChat.unsend(friendUid, m) }
                }) { Text("Unsend") }
            },
            dismissButton = { TextButton(onClick = { unsend = null }) { Text("Cancel") } },
        )
    }
}
