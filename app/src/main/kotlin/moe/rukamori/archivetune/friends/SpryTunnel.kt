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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket

object SpryTunnel {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db get() = FirebaseDatabase.getInstance()

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

    /** Unchanged room states (only the position moved) go out at most this often; real changes go at once. */
    private const val UNCHANGED_RESEND_MS = 2_000L

    /** The full queue rides along at least this often even when unchanged, so a guest can always rebuild it. */
    private const val FULL_QUEUE_EVERY_MS = 30_000L

    private fun jsonOf(text: String) = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()

    /**
     * Pumps text frames between a local websocket and one tunnel connection until either side ends.
     * [host] side: drops timestamp-only repeats, leaves the (large) queue out of room states whose queue
     * hash is unchanged, and keeps at most one room state in flight so updates never pile up behind
     * each other. Guest side: puts the last queue back into slim room states and, when several arrive
     * at once, applies only the newest.
     */
    private suspend fun bridge(
        ws: WebSocketSession,
        conn: DatabaseReference,
        inbox: String,
        outbox: String,
        host: Boolean = false,
    ) {
        val inRef = conn.child(inbox)
        val frames = Channel<DataSnapshot>(Channel.UNLIMITED)
        log("bridge open ${conn.key} in=$inbox")
        var sent = 0
        var received = 0
        // host side
        var lastKey = ""
        var lastSentAt = 0L
        var lastQueueHash = ""
        var lastFullAt = 0L
        val lock = Any()
        var stateInFlight = false
        var pendingState: String? = null
        // guest side
        var lastQueue: JsonElement? = null

        fun pushState(text: String) {
            conn.child(outbox).push().setValue(text).addOnCompleteListener {
                val next = synchronized(lock) { pendingState.also { pendingState = null; if (it == null) stateInFlight = false } }
                if (next != null) pushState(next)
            }
        }

        val listener = childEvents(inRef, onAdded = { frames.trySend(it) }, onCancelled = { log("bridge ${conn.key} listener cancelled"); frames.close() })
        try {
            coroutineScope {
                launch {
                    for (first in frames) {
                        val batch = mutableListOf(first)
                        while (true) batch += frames.tryReceive().getOrNull() ?: break
                        val texts = batch.map { it.getValue(String::class.java) }
                        val newestState = texts.indexOfLast { it != null && typeOf(it) == "room_state" }
                        texts.forEachIndexed { i, raw ->
                            var text = raw ?: return@forEachIndexed
                            if (typeOf(text) == "room_state") {
                                val obj = jsonOf(text)
                                val state = obj?.get("state")?.jsonObject
                                if (obj != null && state != null) {
                                    if (state.containsKey("queue")) {
                                        lastQueue = state["queue"]
                                    } else if (lastQueue != null) {
                                        text = JsonObject(obj + ("state" to JsonObject(state + ("queue" to lastQueue!!)))).toString()
                                    }
                                }
                                if (i != newestState) return@forEachIndexed // a newer state is right behind it
                            }
                            ws.send(text)
                            log("<- ${typeOf(text)} #${++received}${if (batch.size > 1) " (batch ${batch.size})" else ""}")
                        }
                        batch.forEach { it.ref.removeValue() }
                    }
                    ws.close() // the other side went away
                }
                for (f in ws.incoming) {
                    if (f !is Frame.Text) continue
                    var text = f.readText()
                    if (!host || typeOf(text) != "room_state") {
                        log("-> ${typeOf(text)} #${++sent}")
                        conn.child(outbox).push().setValue(text).addOnFailureListener { log("push failed: ${it.message}") }
                        continue
                    }
                    val now = android.os.SystemClock.elapsedRealtime()
                    val key = text.replace(volatileFields, "")
                    if (key == lastKey && now - lastSentAt < UNCHANGED_RESEND_MS) continue
                    lastKey = key
                    lastSentAt = now
                    val obj = jsonOf(text)
                    val state = obj?.get("state")?.jsonObject
                    val hash = state?.get("queueHash")?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (obj != null && state != null && hash.isNotEmpty() && hash == lastQueueHash && now - lastFullAt < FULL_QUEUE_EVERY_MS) {
                        text = JsonObject(obj + ("state" to JsonObject(state - "queue"))).toString()
                    } else {
                        lastQueueHash = hash
                        lastFullAt = now
                    }
                    log("-> room_state #${++sent} (${text.length / 1024} KB)")
                    val sendNow = synchronized(lock) { if (stateInFlight) { pendingState = text; false } else { stateInFlight = true; true } }
                    if (sendNow) pushState(text)
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
    /**
     * One end of an in-memory websocket. Two of these with swapped channels connect the tunnel straight
     * to the room server, with no loopback socket (some phones block an app connecting to itself).
     */
    private class PipeSession(
        override val coroutineContext: kotlin.coroutines.CoroutineContext,
        override val incoming: kotlinx.coroutines.channels.ReceiveChannel<Frame>,
        override val outgoing: kotlinx.coroutines.channels.SendChannel<Frame>,
    ) : WebSocketSession {
        override var masking = false
        override var maxFrameSize = Long.MAX_VALUE
        override val extensions: List<io.ktor.websocket.WebSocketExtension<*>> = emptyList()

        override suspend fun flush() = Unit

        @Suppress("OVERRIDE_DEPRECATION")
        override fun terminate() {
            outgoing.close()
        }
    }

    /** [onReady] runs once old connections are cleared and new ones are being accepted. */
    fun startHost(
        myUid: String,
        serve: suspend (WebSocketSession) -> Unit,
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
                                    log("host: new guest connection $id")
                                    val toServer = Channel<Frame>(Channel.UNLIMITED)
                                    val fromServer = Channel<Frame>(Channel.UNLIMITED)
                                    launch {
                                        try {
                                            serve(PipeSession(coroutineContext, incoming = toServer, outgoing = fromServer))
                                        } catch (t: Throwable) {
                                            log("host: room server error: $t")
                                        } finally {
                                            fromServer.close()
                                        }
                                    }
                                    try {
                                        bridge(PipeSession(coroutineContext, incoming = fromServer, outgoing = toServer), root.child(id), inbox = "up", outbox = "down", host = true)
                                    } finally {
                                        toServer.close() // guest gone: the room server sees the connection end and cleans up
                                        root.child(id).removeValue()
                                    }
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
