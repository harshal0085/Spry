/*
 * Spry (2026) | 24-hour chat between friends.
 * Messages live under chats/{a_b}/{id} (a_b = the two uids, sorted) with a content-free index
 * chatAt/{a_b}/{id} = sent time. The database rules only serve messages younger than 24h and
 * let either friend delete expired ones; the app deletes them whenever a chat is opened.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object SpryChat {
    const val TTL_MS = 24L * 60 * 60 * 1000

    data class Message(val id: String, val from: String, val text: String, val at: Long)

    private val db get() = FirebaseDatabase.getInstance()

    @Volatile private var clockOffset = 0L
    private var clockWatched = false

    /** The server's clock (the rules compare against it, so "24h" means the same thing on every phone). */
    fun serverNow(): Long {
        if (!clockWatched) {
            clockWatched = true
            db.getReference(".info/serverTimeOffset").addValueEventListener(
                object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        clockOffset = s.getValue(Long::class.java) ?: 0L
                    }

                    override fun onCancelled(error: DatabaseError) = Unit
                },
            )
        }
        return System.currentTimeMillis() + clockOffset
    }

    fun chatId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    private fun me() = SpryFriends.user.value?.uid

    /** Live list of the last 24h of messages with [friendUid], oldest first. */
    fun messages(friendUid: String): Flow<List<Message>> =
        callbackFlow {
            val me = me()
            if (me == null) {
                trySend(emptyList())
                awaitClose { }
                return@callbackFlow
            }
            // a minute inside the window so a slightly-off clock never asks for more than the rules allow
            val since = (serverNow() - TTL_MS + 60_000).toDouble()
            val query = db.getReference("chats/${chatId(me, friendUid)}").orderByChild("at").startAt(since)
            val listener =
                object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        trySend(
                            s.children.mapNotNull { c ->
                                val from = c.child("from").getValue(String::class.java) ?: return@mapNotNull null
                                val text = c.child("text").getValue(String::class.java) ?: return@mapNotNull null
                                val at = c.child("at").getValue(Long::class.java) ?: return@mapNotNull null
                                Message(c.key!!, from, text, at)
                            }.sortedBy { it.at },
                        )
                    }

                    override fun onCancelled(error: DatabaseError) {
                        close()
                    }
                }
            query.addValueEventListener(listener)
            awaitClose { query.removeEventListener(listener) }
        }

    /** Time of the newest message with [friendUid] (for the unread dot), or null. */
    fun lastActivity(friendUid: String): Flow<Long?> =
        callbackFlow {
            val me = me()
            if (me == null) {
                trySend(null)
                awaitClose { }
                return@callbackFlow
            }
            val query = db.getReference("chatAt/${chatId(me, friendUid)}").orderByKey().limitToLast(1)
            val listener =
                object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        trySend(s.children.firstOrNull()?.getValue(Long::class.java)?.takeIf { it > serverNow() - TTL_MS })
                    }

                    override fun onCancelled(error: DatabaseError) {
                        close()
                    }
                }
            query.addValueEventListener(listener)
            awaitClose { query.removeEventListener(listener) }
        }

    /** Returns an error message, or null when sent. */
    suspend fun send(friendUid: String, raw: String): String? {
        val me = me() ?: return "Not signed in"
        val text = raw.trim().take(1000)
        if (text.isEmpty()) return null
        val id = chatId(me, friendUid)
        val key = db.getReference("chats/$id").push().key ?: return "Couldn't send"
        val updates =
            hashMapOf<String, Any>(
                "chats/$id/$key" to mapOf("from" to me, "text" to text, "at" to ServerValue.TIMESTAMP),
                "chatAt/$id/$key" to ServerValue.TIMESTAMP,
            )
        return runCatching { db.reference.updateChildren(updates).await() }
            .exceptionOrNull()?.let { "Couldn't send. Are you still friends?" }
    }

    /** Unsend one of my own messages. */
    suspend fun unsend(friendUid: String, message: Message) {
        val me = me() ?: return
        if (message.from != me) return
        val id = chatId(me, friendUid)
        val updates = hashMapOf<String, Any?>("chats/$id/${message.id}" to null, "chatAt/$id/${message.id}" to null)
        runCatching { db.reference.updateChildren(updates).await() }
    }

    /** Deletes every message in this chat older than 24h (from both paths). Safe to call often. */
    suspend fun cleanup(friendUid: String) {
        val me = me() ?: return
        val id = chatId(me, friendUid)
        val cutoff = (serverNow() - TTL_MS - 60_000).toDouble()
        val old =
            runCatching { db.getReference("chatAt/$id").orderByValue().endAt(cutoff).get().await() }
                .getOrNull()?.children?.mapNotNull { it.key }.orEmpty()
        if (old.isEmpty()) return
        val updates = HashMap<String, Any?>()
        old.forEach { key ->
            updates["chats/$id/$key"] = null
            updates["chatAt/$id/$key"] = null
        }
        runCatching { db.reference.updateChildren(updates).await() }
    }
}
