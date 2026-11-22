/*
 * Spry (2026) | carries Music Together websocket frames between friends through Firebase,
 * so the existing LAN host/guest code works across the internet without a relay server.
 * GPL-3.0 License
 *
 * tunnel/{hostUid}/{connId}/guest = guest uid
 *                          /up/{push}   guest -> host text frames
 *                          /down/{push} host -> guest text frames
 */

package moe.rukamori.archivetune.friends

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.ServerSocket
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

object SpryTunnel {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db get() = FirebaseDatabase.getInstance()
    private val client by lazy { HttpClient(OkHttp) { install(ClientWebSockets) } }

    private var hostJob: Job? = null
    private var guestServer: EmbeddedServer<*, *>? = null

    private fun childEvents(
        ref: DatabaseReference,
        onAdded: (DataSnapshot) -> Unit,
        onRemoved: (DataSnapshot) -> Unit = {},
        onCancelled: () -> Unit,
    ): ChildEventListener =
        ref.addChildEventListener(
            object : ChildEventListener {
                override fun onChildAdded(s: DataSnapshot, previous: String?) = onAdded(s)

                override fun onChildRemoved(s: DataSnapshot) = onRemoved(s)

                override fun onChildChanged(s: DataSnapshot, previous: String?) = Unit

                override fun onChildMoved(s: DataSnapshot, previous: String?) = Unit

                override fun onCancelled(error: DatabaseError) = onCancelled()
            },
        )

    /** Last lines of the link log, shown on screen in the friends card so both phones can be checked. */
    val debugLines = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    private val clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    private val typeField = Regex("\"type\":\"([a-z_]+)\"")

    fun log(msg: String) {
        android.util.Log.i("SpryTunnel", msg)
        val line = "${clock.format(java.util.Date())} $msg"
        debugLines.value = (debugLines.value + line).takeLast(40)
    }

    private fun typeOf(text: String) = typeField.find(text)?.groupValues?.get(1) ?: "?"

    // Fields that change on every host broadcast even when nothing happened.
    private val volatileFields = Regex("\"(positionMs|sentAtElapsedRealtimeMs)\":\\d+")

    /** Seconds-level heartbeat for unchanged room states; real changes (song, pause, queue) go out at once. */
    private const val UNCHANGED_RESEND_MS = 2_000L

    /**
     * Pumps text frames between a local websocket and one tunnel connection until either side ends.
     * With [throttle], identical-but-for-timestamps messages are sent at most every 2 s (saves Firebase data).
     */
    private suspend fun bridge(
        ws: WebSocketSession,
        conn: DatabaseReference,
        inbox: String,
        outbox: String,
        throttle: Boolean = false,
    ) {
        val inRef = conn.child(inbox)
        val frames = Channel<DataSnapshot>(Channel.UNLIMITED)
        log("bridge open ${conn.key} in=$inbox")
        var sent = 0
        var received = 0
        var lastKey = ""
        var lastSentAt = 0L
        val listener = childEvents(inRef, onAdded = { frames.trySend(it) }, onCancelled = { log("bridge ${conn.key} listener cancelled"); frames.close() })
        try {
            coroutineScope {
                launch {
                    for (s in frames) {
                        s.getValue(String::class.java)?.let {
                            ws.send(it)
                            log("<- ${typeOf(it)} #${++received}")
                        }
                        s.ref.removeValue()
                    }
                    ws.close() // the other side went away
                }
                for (f in ws.incoming) {
                    if (f is Frame.Text) {
                        val text = f.readText()
                        if (throttle) {
                            val key = text.replace(volatileFields, "")
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (key == lastKey && now - lastSentAt < UNCHANGED_RESEND_MS) continue
                            lastKey = key
                            lastSentAt = now
                        }
                        log("-> ${typeOf(text)} #${++sent}")
                        conn.child(outbox).push().setValue(text).addOnFailureListener { log("push failed: ${it.message}") }
                    }
                }
                coroutineContext.cancelChildren()
            }
        } catch (t: Throwable) {
            log("bridge ${conn.key} error: $t")
            throw t
        } finally {
            log("bridge ${conn.key} closed (sent=$sent received=$received)")
            inRef.removeEventListener(listener)
            conn.removeValue()
        }
    }

    /** [onReady] runs once old connections are cleared and new ones are being accepted. */
    fun startHost(
        myUid: String,
        localPort: Int,
        onReady: () -> Unit,
    ) {
        stopHost()
        val root = db.getReference("tunnel/$myUid")
        hostJob =
            io.launch {
                runCatching { root.removeValue().await() }
                root.onDisconnect().removeValue()
                val conns = HashMap<String, Job>()
                val events = Channel<Pair<String, Boolean>>(Channel.UNLIMITED)
                val listener =
                    childEvents(
                        root,
                        onAdded = { events.trySend(it.key!! to true) },
                        onRemoved = { events.trySend(it.key!! to false) },
                        onCancelled = { events.close() },
                    )
                onReady()
                try {
                    for ((id, added) in events) {
                        if (!added) {
                            conns.remove(id)?.cancel()
                        } else if (id !in conns) {
                            conns[id] =
                                launch {
                                    log("host: new guest connection $id -> local port $localPort")
                                    runCatching {
                                        client.webSocket("ws://127.0.0.1:$localPort/together") {
                                            bridge(this, root.child(id), inbox = "up", outbox = "down", throttle = true)
                                        }
                                    }.onFailure { log("host: local connect failed: $it") }
                                    root.child(id).removeValue()
                                }
                        }
                    }
                } finally {
                    root.removeEventListener(listener)
                    conns.values.forEach { it.cancel() }
                    root.removeValue()
                }
            }
    }

    fun stopHost() {
        hostJob?.cancel()
        hostJob = null
    }

    /** Starts a localhost websocket that the normal Music Together client connects to; returns its port. */
    fun openGuest(
        hostUid: String,
        myUid: String,
    ): Int {
        stopGuest()
        val port = ServerSocket(0).use { it.localPort }
        guestServer =
            embeddedServer(CIO, port = port, host = "127.0.0.1") {
                install(WebSockets)
                routing {
                    webSocket("/together") {
                        val conn = db.getReference("tunnel/$hostUid").push()
                        log("guest: local client connected, opening ${conn.key} to host $hostUid")
                        runCatching { conn.child("guest").setValue(myUid).await() }.onFailure {
                            log("guest: tunnel write denied: $it")
                            return@webSocket
                        }
                        conn.onDisconnect().removeValue()
                        bridge(this, conn, inbox = "down", outbox = "up")
                    }
                }
            }.also { it.start(wait = false) }
        log("guest: local bridge on port $port")
        return port
    }

    fun stopGuest() {
        guestServer?.stop(200, 500)
        guestServer = null
    }
}
