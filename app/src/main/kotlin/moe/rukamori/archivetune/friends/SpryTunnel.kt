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

    /** Pumps text frames between a local websocket and one tunnel connection until either side ends. */
    private suspend fun bridge(
        ws: WebSocketSession,
        conn: DatabaseReference,
        inbox: String,
        outbox: String,
    ) {
        val inRef = conn.child(inbox)
        val frames = Channel<DataSnapshot>(Channel.UNLIMITED)
        val listener = childEvents(inRef, onAdded = { frames.trySend(it) }, onCancelled = { frames.close() })
        try {
            coroutineScope {
                launch {
                    for (s in frames) {
                        s.getValue(String::class.java)?.let { ws.send(it) }
                        s.ref.removeValue()
                    }
                    ws.close() // the other side went away
                }
                for (f in ws.incoming) {
                    if (f is Frame.Text) conn.child(outbox).push().setValue(f.readText())
                }
                coroutineContext.cancelChildren()
            }
        } finally {
            inRef.removeEventListener(listener)
            conn.removeValue()
        }
    }

    fun startHost(
        myUid: String,
        localPort: Int,
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
                try {
                    for ((id, added) in events) {
                        if (!added) {
                            conns.remove(id)?.cancel()
                        } else if (id !in conns) {
                            conns[id] =
                                launch {
                                    runCatching {
                                        client.webSocket("ws://127.0.0.1:$localPort/together") {
                                            bridge(this, root.child(id), inbox = "up", outbox = "down")
                                        }
                                    }
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
                        runCatching { conn.child("guest").setValue(myUid).await() }.onFailure { return@webSocket }
                        conn.onDisconnect().removeValue()
                        bridge(this, conn, inbox = "down", outbox = "up")
                    }
                }
            }.also { it.start(wait = false) }
        return port
    }

    fun stopGuest() {
        guestServer?.stop(200, 500)
        guestServer = null
    }
}
