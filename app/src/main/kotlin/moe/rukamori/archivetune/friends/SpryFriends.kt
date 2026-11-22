/*
 * Spry (2026) | friends, presence and joining a friend's session, backed by the owner's Firebase project.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.together.joinTogether
import moe.rukamori.archivetune.playback.together.startTogetherHost
import moe.rukamori.archivetune.together.TogetherRoomSettings
import java.net.ServerSocket

object SpryFriends {
    data class Person(
        val uid: String,
        val username: String,
    )

    data class HostedSession(
        val sessionId: String,
        val sessionKey: String,
    )

    data class Friend(
        val uid: String,
        val username: String,
        val online: Boolean,
        val session: HostedSession?,
    )

    val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

    private var available = false
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseDatabase.getInstance()

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user = _user.asStateFlow()

    /** null until loaded; "" when the signed-in user has not picked one yet. */
    private val _username = MutableStateFlow<String?>(null)
    val username = _username.asStateFlow()

    private val _requests = MutableStateFlow<List<Person>>(emptyList())
    val requests = _requests.asStateFlow()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends = _friends.asStateFlow()

    /** Friends asking to listen along with this phone. */
    private val _incomingVibes = MutableStateFlow<List<Person>>(emptyList())
    val incomingVibes = _incomingVibes.asStateFlow()

    /** Progress of our own "vibe together" request, shown under the friends list. */
    private val _vibeStatus = MutableStateFlow<String?>(null)
    val vibeStatus = _vibeStatus.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var vibeJob: Job? = null

    @Volatile
    private var hosting = false

    private val subs = mutableListOf<Pair<Query, ValueEventListener>>()
    private val friendSubs = mutableListOf<Pair<Query, ValueEventListener>>()
    private var friendNames = emptyMap<String, String>()
    private val online = HashMap<String, Boolean>()
    private val sessions = HashMap<String, HostedSession>()

    val isAvailable get() = available

    fun init(context: Context) {
        val app = context.applicationContext
        // Builds without google-services.json have no Firebase config: friends stay hidden.
        available = runCatching { FirebaseApp.getApps(app).isNotEmpty() || FirebaseApp.initializeApp(app) != null }.getOrDefault(false)
        if (!available) return
        auth.addAuthStateListener { onUser(it.currentUser) }
    }

    private fun listen(
        query: Query,
        into: MutableList<Pair<Query, ValueEventListener>> = subs,
        onData: (DataSnapshot) -> Unit,
    ) {
        val l =
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)

                override fun onCancelled(error: DatabaseError) = Unit
            }
        query.addValueEventListener(l)
        into += query to l
    }

    private fun detach(list: MutableList<Pair<Query, ValueEventListener>>) {
        list.forEach { (q, l) -> q.removeEventListener(l) }
        list.clear()
    }

    private fun onUser(u: FirebaseUser?) {
        if (u?.uid == _user.value?.uid && u != null) return
        detach(subs)
        detach(friendSubs)
        _user.value = u
        _username.value = null
        _requests.value = emptyList()
        _friends.value = emptyList()
        _incomingVibes.value = emptyList()
        if (u == null) return
        val me = u.uid

        listen(db.getReference("users/$me/username")) { _username.value = it.getValue(String::class.java).orEmpty() }

        val presence = db.getReference("presence/$me")
        listen(db.getReference(".info/connected")) {
            if (it.getValue(Boolean::class.java) == true) {
                presence.onDisconnect().setValue(mapOf("online" to false, "at" to ServerValue.TIMESTAMP))
                presence.setValue(mapOf("online" to true, "at" to ServerValue.TIMESTAMP))
            }
        }

        listen(db.getReference("vibe/$me")) { snap ->
            _incomingVibes.value =
                snap.children
                    .filter { it.child("ok").getValue(Boolean::class.java) != true }
                    .mapNotNull { c -> c.child("name").getValue(String::class.java)?.let { Person(c.key!!, it) } }
        }

        listen(db.getReference("requests/$me")) { snap ->
            _requests.value = snap.children.mapNotNull { c -> c.getValue(String::class.java)?.let { Person(c.key!!, it) } }
        }

        listen(db.getReference("friends/$me")) { snap ->
            friendNames = snap.children.associate { it.key!! to (it.getValue(String::class.java) ?: "?") }
            detach(friendSubs)
            online.clear()
            sessions.clear()
            friendNames.keys.forEach { uid ->
                listen(db.getReference("presence/$uid/online"), friendSubs) {
                    online[uid] = it.getValue(Boolean::class.java) == true
                    publishFriends()
                }
                listen(db.getReference("sessions/$uid"), friendSubs) {
                    val sid = it.child("sid").getValue(String::class.java)
                    val key = it.child("key").getValue(String::class.java)
                    if (sid != null && key != null) sessions[uid] = HostedSession(sid, key) else sessions.remove(uid)
                    publishFriends()
                }
            }
            publishFriends()
        }
    }

    private fun publishFriends() {
        _friends.value =
            friendNames
                .map { (uid, name) -> Friend(uid, name, online[uid] == true, sessions[uid].takeIf { online[uid] == true }) }
                .sortedWith(compareByDescending<Friend> { it.online }.thenBy { it.username })
    }

    /** Returns an error message, or null on success. */
    suspend fun signIn(activity: Activity): String? =
        runCatching {
            val webClientId =
                activity.getString(
                    activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName),
                )
            val request =
                GetCredentialRequest
                    .Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                    .build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val google = GoogleIdTokenCredential.createFrom(result.credential.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
            null
        }.getOrElse { it.message ?: "Sign-in failed" }

    suspend fun signOut(context: Context) {
        val me = _user.value?.uid ?: return
        runCatching { db.getReference("presence/$me").setValue(mapOf("online" to false, "at" to ServerValue.TIMESTAMP)).await() }
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    suspend fun claimUsername(raw: String): String? {
        val me = _user.value?.uid ?: return "Not signed in"
        val name = raw.trim().lowercase()
        if (!USERNAME_REGEX.matches(name)) return "3–20 letters, numbers or _"
        val taken = db.getReference("usernames/$name").get().await().getValue(String::class.java)
        if (taken != null && taken != me) return "@$name is taken"
        val updates = hashMapOf<String, Any?>("usernames/$name" to me, "users/$me/username" to name)
        _username.value?.takeIf { it.isNotEmpty() && it != name }?.let { updates["usernames/$it"] = null }
        return runCatching { db.reference.updateChildren(updates).await(); null }.getOrElse { it.message ?: "Could not save" }
    }

    suspend fun find(raw: String): Result<Person> {
        val name = raw.trim().removePrefix("@").lowercase()
        val uid =
            runCatching { db.getReference("usernames/$name").get().await().getValue(String::class.java) }.getOrNull()
                ?: return Result.failure(Exception("No one called @$name"))
        if (uid == _user.value?.uid) return Result.failure(Exception("That's you"))
        return Result.success(Person(uid, name))
    }

    suspend fun sendRequest(to: Person): String? {
        val me = _user.value?.uid ?: return "Not signed in"
        val myName = _username.value?.takeIf { it.isNotEmpty() } ?: return "Pick a username first"
        if (friendNames.containsKey(to.uid)) return "Already friends"
        // They already asked us: just accept.
        if (_requests.value.any { it.uid == to.uid }) return accept(to)
        return runCatching { db.getReference("requests/${to.uid}/$me").setValue(myName).await(); null }
            .getOrElse { it.message ?: "Could not send" }
    }

    suspend fun accept(from: Person): String? {
        val me = _user.value?.uid ?: return "Not signed in"
        val myName = _username.value ?: return "Pick a username first"
        val updates =
            hashMapOf<String, Any?>(
                "friends/$me/${from.uid}" to from.username,
                "friends/${from.uid}/$me" to myName,
                "requests/$me/${from.uid}" to null,
            )
        return runCatching { db.reference.updateChildren(updates).await(); null }.getOrElse { it.message ?: "Could not accept" }
    }

    suspend fun decline(from: Person) {
        val me = _user.value?.uid ?: return
        runCatching { db.getReference("requests/$me/${from.uid}").removeValue().await() }
    }

    suspend fun removeFriend(uid: String) {
        val me = _user.value?.uid ?: return
        runCatching { db.reference.updateChildren(mapOf("friends/$me/$uid" to null, "friends/$uid/$me" to null)).await() }
    }

    /** Called when this phone starts hosting a Music Together room. */
    fun publishHosting(
        sessionId: String,
        sessionKey: String,
        port: Int,
    ) {
        val me = _user.value?.uid ?: return
        hosting = true
        val ref = db.getReference("sessions/$me")
        ref.onDisconnect().removeValue()
        ref.setValue(mapOf("sid" to sessionId, "key" to sessionKey))
        SpryTunnel.startHost(me, port)
    }

    fun unpublishHosting() {
        hosting = false
        SpryTunnel.stopHost()
        val me = _user.value?.uid ?: return
        db.getReference("sessions/$me").removeValue()
    }

    /**
     * Asks [friend] to let us listen along. When they accept, their phone shares its playback
     * and we join automatically. Several friends can listen to the same phone at once.
     */
    fun requestVibe(
        friend: Friend,
        service: MusicService,
    ) {
        val me = _user.value?.uid ?: return
        val myName = _username.value?.takeIf { it.isNotEmpty() } ?: return
        val ref = db.getReference("vibe/${friend.uid}/$me")
        vibeJob?.cancel()
        vibeJob =
            scope.launch {
                _vibeStatus.value = "asked @${friend.username}, waiting…"
                if (runCatching { ref.setValue(mapOf("name" to myName, "ok" to false)).await() }.isFailure) {
                    _vibeStatus.value = "couldn't reach @${friend.username}"
                    return@launch
                }
                ref.onDisconnect().removeValue()
                // true = accepted, false = said no (request removed), null = no answer in time
                val accepted =
                    withTimeoutOrNull(120_000) {
                        callbackFlow {
                            val l =
                                object : ValueEventListener {
                                    override fun onDataChange(s: DataSnapshot) {
                                        trySend(if (!s.exists()) false else if (s.child("ok").getValue(Boolean::class.java) == true) true else null)
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        close()
                                    }
                                }
                            ref.addValueEventListener(l)
                            awaitClose { ref.removeEventListener(l) }
                        }.first { it != null }
                    }
                when (accepted) {
                    null -> {
                        ref.removeValue()
                        _vibeStatus.value = "@${friend.username} didn't answer"
                    }

                    false -> _vibeStatus.value = "@${friend.username} said not now"

                    true -> {
                        // Their room starts a moment after they accept.
                        val live = withTimeoutOrNull(20_000) { friends.first { list -> list.any { it.uid == friend.uid && it.session != null } } }
                        val link = live?.firstOrNull { it.uid == friend.uid }?.let { joinLink(it) }
                        ref.removeValue()
                        if (link == null) {
                            _vibeStatus.value = "couldn't connect to @${friend.username}"
                        } else {
                            service.joinTogether(link, myName)
                            _vibeStatus.value = "vibing with @${friend.username}"
                        }
                    }
                }
            }
    }

    /** Host side: share what this phone is playing with [from] (starts a room if none is open). */
    fun acceptVibe(
        from: Person,
        service: MusicService,
    ) {
        val me = _user.value?.uid ?: return
        if (!hosting) {
            val port = ServerSocket(0).use { it.localPort }
            service.startTogetherHost(port, _username.value.orEmpty(), TogetherRoomSettings())
        }
        db.getReference("vibe/$me/${from.uid}/ok").setValue(true)
    }

    fun declineVibe(from: Person) {
        val me = _user.value?.uid ?: return
        db.getReference("vibe/$me/${from.uid}").removeValue()
    }

    /** Opens a local bridge to the friend's room and returns a join link the existing Music Together code understands. */
    private fun joinLink(friend: Friend): String? {
        val me = _user.value?.uid ?: return null
        val session = friend.session ?: return null
        val port = SpryTunnel.openGuest(hostUid = friend.uid, myUid = me)
        return moe.rukamori.archivetune.together.TogetherLink.encode(
            moe.rukamori.archivetune.together.TogetherJoinInfo(
                host = "127.0.0.1",
                port = port,
                sessionId = session.sessionId,
                sessionKey = session.sessionKey,
            ),
        )
    }
}
