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
import moe.rukamori.archivetune.playback.together.leaveTogether
import moe.rukamori.archivetune.playback.together.startTogetherHost
import moe.rukamori.archivetune.playback.together.updateTogetherSettings
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
        val playing: String? = null,
        val lastSeen: Long? = null,
    )

    val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

    /** No approval step (accepting the request is it); guests listen and may pause their own phone only. */
    private val VibeRoomSettings =
        TogetherRoomSettings(
            allowGuestsToAddTracks = false,
            allowGuestsToControlPlayback = false,
            requireHostApprovalToJoin = false,
        )

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

    @Volatile
    private var roomReady = false
    private val pendingAccepts = mutableSetOf<String>()

    private val subs = mutableListOf<Pair<Query, ValueEventListener>>()
    private val friendSubs = mutableListOf<Pair<Query, ValueEventListener>>()
    private var friendNames = emptyMap<String, String>()
    private val online = HashMap<String, Boolean>()
    private val friendPlaying = HashMap<String, String?>()
    private val friendSeen = HashMap<String, Long?>()
    private val sessions = HashMap<String, HostedSession>()
    private var prefs: android.content.SharedPreferences? = null

    val isAvailable get() = available

    fun init(context: Context) {
        val app = context.applicationContext
        // Builds without google-services.json have no Firebase config: friends stay hidden.
        available = runCatching { FirebaseApp.getApps(app).isNotEmpty() || FirebaseApp.initializeApp(app) != null }.getOrDefault(false)
        if (!available) return
        prefs = app.getSharedPreferences("spry", Context.MODE_PRIVATE)
        _hidden.value = prefs?.getBoolean("dev_hidden", false) == true
        watchConfig()
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

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.w("Spry", "read refused at ${query.ref.path}: ${error.message}")
                }
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
        _incomingInvites.value = emptyList()
        resetDevState()
        if (u == null) return
        val me = u.uid
        startDevWatch(me)
        // the room-host read needs auth; one made before sign-in was refused, so start it again
        roomWatch?.let { (q, l) -> q.removeEventListener(l) }
        roomWatch = null
        watchRoomHost(_config.value.room?.uid)

        listen(db.getReference("users/$me/username")) { _username.value = it.getValue(String::class.java).orEmpty() }

        listen(db.getReference(".info/connected")) {
            if (it.getValue(Boolean::class.java) == true) writePresence()
        }

        listen(db.getReference("invites/$me")) { snap ->
            _incomingInvites.value = snap.children.mapNotNull { c -> c.getValue(String::class.java)?.let { Person(c.key!!, it) } }
        }

        listen(db.getReference("vibe/$me")) { snap ->
            _incomingVibes.value =
                snap.children
                    .filter { it.child("ok").getValue(Boolean::class.java) != true }
                    .filter { synchronized(pendingAccepts) { it.key !in pendingAccepts } }
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
                listen(db.getReference("presence/$uid"), friendSubs) {
                    online[uid] = it.child("online").getValue(Boolean::class.java) == true
                    friendPlaying[uid] = it.child("playing").getValue(String::class.java)
                    friendSeen[uid] = it.child("at").getValue(Long::class.java)
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
                .map { (uid, name) ->
                    val on = online[uid] == true
                    Friend(uid, name, on, sessions[uid].takeIf { on }, friendPlaying[uid].takeIf { on }, friendSeen[uid])
                }
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
        server: moe.rukamori.archivetune.together.TogetherServer,
    ) {
        val me = _user.value?.uid ?: return
        hosting = true
        val ref = db.getReference("sessions/$me")
        // Publish the room only once the tunnel accepts connections, then let waiting friends in.
        SpryTunnel.startHost(me, server::acceptTunnel) {
            ref.onDisconnect().removeValue()
            ref.setValue(mapOf("sid" to sessionId, "key" to sessionKey)).addOnSuccessListener {
                val waiting = synchronized(pendingAccepts) { pendingAccepts.toList().also { pendingAccepts.clear() } }
                roomReady = true
                SpryTunnel.log("host: room published, letting in ${waiting.size}")
                waiting.forEach { db.getReference("vibe/$me/$it/ok").setValue(true) }
            }
        }
    }

    fun unpublishHosting() {
        hosting = false
        roomReady = false
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
                        // They only accept once their room is published, so read it fresh (never a stale cached one).
                        val snap = runCatching { db.getReference("sessions/${friend.uid}").get().await() }.getOrNull()
                        val sid = snap?.child("sid")?.getValue(String::class.java)
                        val key = snap?.child("key")?.getValue(String::class.java)
                        val link = if (sid != null && key != null) joinLink(friend.copy(session = HostedSession(sid, key))) else null
                        SpryTunnel.log("vibe accepted by ${friend.username}: session=${sid != null} link=${link != null}")
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
        SpryTunnel.log("host: letting in @${from.username} (room ready=$roomReady)")
        if (roomReady) {
            // Accepting already is the approval; friends listen along but can't change the music.
            service.updateTogetherSettings(VibeRoomSettings)
            db.getReference("vibe/$me/${from.uid}/ok").setValue(true)
            return
        }
        synchronized(pendingAccepts) { pendingAccepts += from.uid }
        // Hide the dialog right away; "ok" is written once the room is published.
        _incomingVibes.value = _incomingVibes.value.filter { it.uid != from.uid }
        if (!hosting) {
            val port = ServerSocket(0).use { it.localPort }
            service.startTogetherHost(port, _username.value.orEmpty(), VibeRoomSettings)
        }
    }

    fun declineVibe(from: Person) {
        val me = _user.value?.uid ?: return
        db.getReference("vibe/$me/${from.uid}").removeValue()
    }

    // ---- Invites: the other direction. I ask a friend to listen to MY music. ----

    private val _incomingInvites = MutableStateFlow<List<Person>>(emptyList())
    val incomingInvites = _incomingInvites.asStateFlow()

    /** Friends I invited; their resulting vibe request is let in without asking me again. */
    private val invited = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun wasInvited(uid: String) = uid in invited

    fun invite(friend: Friend): String? {
        val me = _user.value?.uid ?: return "Not signed in"
        val myName = _username.value?.takeIf { it.isNotEmpty() } ?: return "Pick a username first"
        invited += friend.uid
        db.getReference("invites/${friend.uid}/$me").setValue(myName)
        return "invited @${friend.username} to your music"
    }

    /** Guest side: accepting an invite is just a vibe request the host already agreed to. */
    fun acceptInvite(
        from: Person,
        service: MusicService,
    ) {
        val me = _user.value?.uid ?: return
        db.getReference("invites/$me/${from.uid}").removeValue()
        requestVibe(Friend(from.uid, from.username, online = true, session = null), service)
    }

    fun declineInvite(from: Person) {
        val me = _user.value?.uid ?: return
        db.getReference("invites/$me/${from.uid}").removeValue()
    }

    // ---------------------------------------------------------------- developer powers
    // Who is a developer is decided only in the Firebase console (admins/{uid} = true); the
    // database rules enforce every power below, the app just shows the controls.

    data class UserRow(
        val uid: String,
        val username: String,
        val online: Boolean,
        val hosting: Boolean,
        val banned: Boolean,
        val admin: Boolean,
        val playing: String? = null,
        val lastSeen: Long? = null,
        val badge: String? = null,
    )

    data class FeaturedSong(val videoId: String, val title: String, val artist: String, val thumb: String)

    data class RoomInfo(val uid: String, val title: String)

    /** Remote switches the developer controls for everyone (config/ in the database, readable by all). */
    data class SpryConfig(
        val maintenanceOn: Boolean = false,
        val maintenanceText: String = "",
        val minVersion: Int = 0,
        val updateUrl: String = DEFAULT_UPDATE_URL,
        val featured: FeaturedSong? = null,
        val room: RoomInfo? = null,
    )

    /** [naez] alerts are said by Naez in her bubble instead of a dialog; [mood] picks her sticker. */
    data class Alert(val id: String, val text: String, val naez: Boolean = false, val mood: String? = null)

    /** Something the developer made Naez say to everyone (config/naez). */
    data class NaezLine(val text: String, val mood: String?, val at: Long)

    data class Command(val type: String, val at: Long, val song: FeaturedSong?)

    data class Stats(
        val users: Int = 0,
        val online: Int = 0,
        val hosting: Int = 0,
        val activeDay: Int = 0,
        val activeWeek: Int = 0,
        val banned: Int = 0,
        val messagesToday: Int = 0,
        val topPlaying: List<Pair<String, Int>> = emptyList(),
    )

    const val DEFAULT_UPDATE_URL = "https://github.com/harshal0085/Spry/releases"

    private val _config = MutableStateFlow(SpryConfig())
    val config = _config.asStateFlow()

    private val _badges = MutableStateFlow<Map<String, String>>(emptyMap())
    val badges = _badges.asStateFlow()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts = _alerts.asStateFlow()

    private val _naezLine = MutableStateFlow<NaezLine?>(null)
    val naezLine = _naezLine.asStateFlow()

    private val _command = MutableStateFlow<Command?>(null)
    val command = _command.asStateFlow()

    private val _hidden = MutableStateFlow(false)
    val hidden = _hidden.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats = _stats.asStateFlow()

    private var allPresence = emptyMap<String, DataSnapshot>()
    private var messageTimes = emptyList<Long>()
    private var myPlaying: String? = null

    private fun watchConfig() {
        db.getReference("config").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val f = s.child("featured")
                    val r = s.child("room")
                    _config.value =
                        SpryConfig(
                            maintenanceOn = s.child("maintenance/on").getValue(Boolean::class.java) == true,
                            maintenanceText = s.child("maintenance/text").getValue(String::class.java).orEmpty(),
                            minVersion = s.child("minVersion").getValue(Int::class.java) ?: 0,
                            updateUrl = s.child("updateUrl").getValue(String::class.java)?.takeIf { it.isNotBlank() } ?: DEFAULT_UPDATE_URL,
                            featured =
                                f.child("videoId").getValue(String::class.java)?.let {
                                    FeaturedSong(
                                        it,
                                        f.child("title").getValue(String::class.java).orEmpty(),
                                        f.child("artist").getValue(String::class.java).orEmpty(),
                                        f.child("thumb").getValue(String::class.java).orEmpty(),
                                    )
                                },
                            room =
                                r.child("uid").getValue(String::class.java)?.let {
                                    RoomInfo(it, r.child("title").getValue(String::class.java).orEmpty())
                                },
                        )
                    watchRoomHost(_config.value.room?.uid)
                    val n = s.child("naez")
                    _naezLine.value = n.child("text").getValue(String::class.java)?.takeIf { it.isNotBlank() }?.let {
                        NaezLine(it, n.child("mood").getValue(String::class.java), n.child("at").getValue(Long::class.java) ?: 0L)
                    }
                }

                override fun onCancelled(error: DatabaseError) = Unit
            },
        )
    }

    private val _roomLive = MutableStateFlow(false)

    /** True while the room's host really has a session up (config/room outlives an app restart). */
    val roomLive = _roomLive.asStateFlow()
    private var roomWatch: Pair<Query, ValueEventListener>? = null

    private fun watchRoomHost(uid: String?) {
        if (roomWatch?.first?.ref?.key == uid && uid != null) return
        roomWatch?.let { (q, l) -> q.removeEventListener(l) }
        roomWatch = null
        _roomLive.value = false
        if (uid == null) return
        val q = db.getReference("sessions/$uid")
        val l =
            object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    _roomLive.value = s.hasChild("sid")
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.w("Spry", "room host read refused: ${error.message}")
                }
            }
        q.addValueEventListener(l)
        roomWatch = q to l
    }

    /** Writes my presence: online + what I'm playing, or "offline" when the developer is hidden. */
    private fun writePresence() {
        val me = _user.value?.uid ?: return
        val ref = db.getReference("presence/$me")
        ref.onDisconnect().setValue(mapOf("online" to false, "at" to ServerValue.TIMESTAMP))
        val value = HashMap<String, Any>()
        value["online"] = !_hidden.value
        value["at"] = ServerValue.TIMESTAMP
        if (!_hidden.value) myPlaying?.let { value["playing"] = it }
        ref.setValue(value)
    }

    /** "Title · Artist" while music plays, null when paused. Friends see it as "Listening to …". */
    fun setPlaying(text: String?) {
        if (text == myPlaying) return
        myPlaying = text
        val me = _user.value?.uid ?: return
        if (_hidden.value) return
        val ref = db.getReference("presence/$me/playing")
        if (text == null) ref.removeValue() else ref.setValue(text.take(120))
    }

    /** Developer only: appear offline (and hide what you're playing) while still seeing everyone. */
    fun setHidden(on: Boolean) {
        _hidden.value = on
        prefs?.edit()?.putBoolean("dev_hidden", on)?.apply()
        writePresence()
    }

    fun dismissAlert(alert: Alert) {
        val me = _user.value?.uid ?: return
        _alerts.value = _alerts.value.filter { it.id != alert.id }
        db.getReference("alerts/$me/${alert.id}").removeValue()
    }

    /** The command was carried out on this phone; remove it so it never runs twice. */
    fun commandHandled(cmd: Command) {
        val me = _user.value?.uid ?: return
        prefs?.edit()?.putLong("command_done", cmd.at)?.apply()
        if (_command.value == cmd) _command.value = null
        db.getReference("commands/$me").removeValue()
    }

    fun lastHandledCommandAt(): Long = prefs?.getLong("command_done", 0L) ?: 0L

    private fun recomputeStats() {
        val now = SpryChat.serverNow()
        val day = now - 86_400_000L
        val week = now - 7 * 86_400_000L
        val rows = _allUsers.value
        val seen = allPresence.mapValues { (_, p) -> p.child("at").getValue(Long::class.java) ?: 0L }
        val on = allPresence.filterValues { it.child("online").getValue(Boolean::class.java) == true }
        _stats.value =
            Stats(
                users = rows.size + 1,
                online = rows.count { it.online },
                hosting = rows.count { it.hosting },
                activeDay = allPresence.keys.count { on.containsKey(it) || (seen[it] ?: 0) > day },
                activeWeek = allPresence.keys.count { on.containsKey(it) || (seen[it] ?: 0) > week },
                banned = rows.count { it.banned },
                messagesToday = messageTimes.count { it > day },
                topPlaying =
                    on.values.mapNotNull { it.child("playing").getValue(String::class.java) }
                        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key to it.value },
            )
    }

    private suspend fun adminWrite(updates: Map<String, Any?>, what: String): String? =
        runCatching { db.reference.updateChildren(updates).await() }
            .exceptionOrNull()?.let { "Couldn't $what: ${it.message}" }

    suspend fun setMaintenance(on: Boolean, text: String): String? =
        adminWrite(mapOf("config/maintenance" to mapOf("on" to on, "text" to text.trim().take(300))), "change maintenance")

    /** 0 clears the requirement. */
    suspend fun setMinVersion(code: Int, url: String): String? =
        adminWrite(
            mapOf("config/minVersion" to code.takeIf { it > 0 }, "config/updateUrl" to url.trim().ifEmpty { DEFAULT_UPDATE_URL }),
            "set the version",
        )

    suspend fun setFeatured(song: FeaturedSong?): String? =
        adminWrite(
            mapOf(
                "config/featured" to
                    song?.let { mapOf("videoId" to it.videoId, "title" to it.title, "artist" to it.artist, "thumb" to it.thumb, "at" to ServerValue.TIMESTAMP) },
            ),
            "feature it",
        )

    suspend fun setBadge(row: UserRow, text: String?): String? =
        adminWrite(mapOf("badges/${row.uid}" to text?.trim()?.take(16)?.ifEmpty { null }), "set the badge")

    suspend fun sendAlert(row: UserRow, text: String, asNaez: Boolean = false, mood: String? = null): String? {
        val t = text.trim().take(500).ifEmpty { return "Write something first" }
        val key = db.getReference("alerts/${row.uid}").push().key ?: return "Couldn't send"
        val value = mutableMapOf<String, Any>("text" to t, "at" to ServerValue.TIMESTAMP)
        if (asNaez) { value["naez"] = true; mood?.let { value["mood"] = it } }
        return adminWrite(mapOf("alerts/${row.uid}/$key" to value), "send the alert")
    }

    /** Naez says [text] on everyone's phone (each phone shows a given line once). */
    suspend fun naezSayToAll(text: String, mood: String?): String? {
        val t = text.trim().take(300).ifEmpty { return "Write something first" }
        return adminWrite(mapOf("config/naez" to mapOf("text" to t, "mood" to mood, "at" to ServerValue.TIMESTAMP)), "make Naez speak")
    }

    /** Makes [row] leave whatever listening session they're in. */
    suspend fun kick(row: UserRow): String? =
        adminWrite(mapOf("commands/${row.uid}" to mapOf("type" to "kick", "at" to ServerValue.TIMESTAMP)), "kick")

    /** Starts [song] on each of [targets]' phones. */
    suspend fun forcePlay(targets: List<UserRow>, song: FeaturedSong): String? {
        if (targets.isEmpty()) return "Nobody is online"
        val cmd = mapOf("type" to "play", "videoId" to song.videoId, "title" to song.title, "artist" to song.artist, "thumb" to song.thumb, "at" to ServerValue.TIMESTAMP)
        return adminWrite(targets.associate { "commands/${it.uid}" to cmd }, "start the song")
    }

    /** Wipes everything Spry stores about [row]: name, friends, requests, chats, presence, badges. */
    suspend fun deleteAllData(row: UserRow): String? {
        if (row.admin) return "Developers can't be wiped"
        val uid = row.uid
        val friends = runCatching { db.getReference("friends/$uid").get().await() }.getOrNull()?.children?.mapNotNull { it.key }.orEmpty()
        val sentTo =
            runCatching { db.getReference("requests").get().await() }.getOrNull()?.children
                ?.filter { it.hasChild(uid) }?.mapNotNull { it.key }.orEmpty()
        val updates = HashMap<String, Any?>()
        listOf("users", "friends", "requests", "invites", "sessions", "presence", "badges", "alerts", "commands", "vibe")
            .forEach { updates["$it/$uid"] = null }
        if (row.username.isNotEmpty()) updates["usernames/${row.username}"] = null
        friends.forEach { f ->
            updates["friends/$f/$uid"] = null
            val chat = SpryChat.chatId(uid, f)
            updates["chats/$chat"] = null
            updates["chatAt/$chat"] = null
        }
        sentTo.forEach { to -> updates["requests/$to/$uid"] = null }
        return adminWrite(updates, "delete their data")
    }

    /** Opens the developer's public room: anyone can join it from Home with one tap. */
    fun openRoom(service: MusicService) {
        val me = _user.value?.uid ?: return
        val name = _username.value.orEmpty()
        if (!hosting) {
            val port = ServerSocket(0).use { it.localPort }
            service.startTogetherHost(port, name, VibeRoomSettings)
        } else {
            service.updateTogetherSettings(VibeRoomSettings)
        }
        db.getReference("config/room").setValue(mapOf("uid" to me, "title" to "@$name's room"))
    }

    fun closeRoom(service: MusicService) {
        db.getReference("config/room").removeValue()
        service.leaveTogether()
    }

    /** The listening session ended (left, kicked, host closed): drop the stale "listening in…" line. */
    fun onSessionLeft() {
        val s = _vibeStatus.value ?: return
        if (s.startsWith("listening") || s.startsWith("vibing")) _vibeStatus.value = null
    }

    /** Anyone: join the developer's public room. */
    fun joinRoom(service: MusicService) {
        val room = _config.value.room ?: return
        val myName = _username.value?.takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            val snap = runCatching { db.getReference("sessions/${room.uid}").get().await() }.getOrNull()
            val sid = snap?.child("sid")?.getValue(String::class.java)
            val key = snap?.child("key")?.getValue(String::class.java)
            val link = if (sid != null && key != null) joinLink(Friend(room.uid, room.title, true, HostedSession(sid, key))) else null
            _vibeStatus.value = if (link == null) "The room isn't open yet" else "listening in ${room.title}"
            if (link != null) service.joinTogether(link, myName)
        }
    }

    private val _admins = MutableStateFlow<Set<String>>(emptySet())
    val admins = _admins.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin = _isAdmin.asStateFlow()

    /** Non-null (the reason) when the developer has banned this account. */
    private val _banned = MutableStateFlow<String?>(null)
    val banned = _banned.asStateFlow()

    /** The developer's current announcement: (sent-at millis, text). */
    private val _announcement = MutableStateFlow<Pair<Long, String>?>(null)
    val announcement = _announcement.asStateFlow()

    private val _allUsers = MutableStateFlow<List<UserRow>>(emptyList())
    val allUsers = _allUsers.asStateFlow()

    private val adminSubs = mutableListOf<Pair<Query, ValueEventListener>>()
    private var allNames = emptyMap<String, String>()
    private var allOnline = emptyMap<String, Boolean>()
    private var allSessions = emptyMap<String, HostedSession>()
    private var allBans = emptySet<String>()

    private fun startDevWatch(me: String) {
        listen(db.getReference("admins")) { snap ->
            _admins.value = snap.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }.toSet()
            val nowAdmin = me in _admins.value
            if (nowAdmin != _isAdmin.value) {
                _isAdmin.value = nowAdmin
                if (nowAdmin) startAdminListeners() else detach(adminSubs).also { _allUsers.value = emptyList() }
            }
        }
        listen(db.getReference("bans/$me")) { _banned.value = it.getValue(String::class.java) }
        listen(db.getReference("badges")) { s ->
            _badges.value = s.children.mapNotNull { c -> c.getValue(String::class.java)?.let { c.key!! to it } }.toMap()
            if (_isAdmin.value) publishAllUsers()
        }
        listen(db.getReference("alerts/$me")) { s ->
            _alerts.value = s.children.mapNotNull { c ->
                c.child("text").getValue(String::class.java)?.let {
                    Alert(c.key!!, it, c.child("naez").getValue(Boolean::class.java) == true, c.child("mood").getValue(String::class.java))
                }
            }
        }
        listen(db.getReference("commands/$me")) { s ->
            val type = s.child("type").getValue(String::class.java)
            val at = s.child("at").getValue(Long::class.java)
            android.util.Log.i("Spry", "command $type at=$at lastDone=${lastHandledCommandAt()}")
            _command.value =
                if (type != null && at != null && at > lastHandledCommandAt()) {
                    val song = s.child("videoId").getValue(String::class.java)?.let { id ->
                        fun str(k: String) = s.child(k).getValue(String::class.java).orEmpty()
                        FeaturedSong(id, str("title"), str("artist"), str("thumb"))
                    }
                    Command(type, at, song)
                } else {
                    null
                }
        }
        listen(db.getReference("announcement")) { s ->
            val at = s.child("at").getValue(Long::class.java)
            val text = s.child("text").getValue(String::class.java)
            _announcement.value = if (at != null && !text.isNullOrBlank()) at to text else null
        }
    }

    private fun resetDevState() {
        detach(adminSubs)
        _admins.value = emptySet()
        _isAdmin.value = false
        _banned.value = null
        _announcement.value = null
        _allUsers.value = emptyList()
        _badges.value = emptyMap()
        _alerts.value = emptyList()
        _command.value = null
    }

    private fun startAdminListeners() {
        detach(adminSubs)
        listen(db.getReference("users"), adminSubs) { s ->
            allNames = s.children.associate { it.key!! to (it.child("username").getValue(String::class.java) ?: "") }
            publishAllUsers()
        }
        listen(db.getReference("presence"), adminSubs) { s ->
            allPresence = s.children.associateBy { it.key!! }
            allOnline = allPresence.mapValues { (_, p) -> p.child("online").getValue(Boolean::class.java) == true }
            publishAllUsers()
        }
        listen(db.getReference("chatAt"), adminSubs) { s ->
            messageTimes = s.children.flatMap { chat -> chat.children.mapNotNull { it.getValue(Long::class.java) } }
            recomputeStats()
        }
        listen(db.getReference("sessions"), adminSubs) { s ->
            allSessions =
                s.children.mapNotNull { c ->
                    val sid = c.child("sid").getValue(String::class.java)
                    val key = c.child("key").getValue(String::class.java)
                    if (sid != null && key != null) c.key!! to HostedSession(sid, key) else null
                }.toMap()
            publishAllUsers()
        }
        listen(db.getReference("bans"), adminSubs) { s ->
            allBans = s.children.mapNotNull { it.key }.toSet()
            publishAllUsers()
        }
    }

    private fun publishAllUsers() {
        val me = _user.value?.uid
        _allUsers.value =
            (allNames.keys + allOnline.keys)
                .filter { it != me }
                .map { uid ->
                    val online = allOnline[uid] == true
                    val p = allPresence[uid]
                    UserRow(
                        uid = uid,
                        username = allNames[uid].orEmpty(),
                        online = online,
                        hosting = online && uid in allSessions,
                        banned = uid in allBans,
                        admin = uid in _admins.value,
                        playing = p?.child("playing")?.getValue(String::class.java)?.takeIf { online },
                        lastSeen = p?.child("at")?.getValue(Long::class.java),
                        badge = _badges.value[uid],
                    )
                }.sortedWith(compareByDescending<UserRow> { it.online }.thenByDescending { it.lastSeen ?: 0L })
        recomputeStats()
    }

    /** Returns an error message, or null on success. */
    suspend fun ban(row: UserRow): String? =
        runCatching { db.getReference("bans/${row.uid}").setValue("Banned by the developer").await() }
            .exceptionOrNull()?.let { "Couldn't ban: ${it.message}" }

    suspend fun unban(row: UserRow): String? =
        runCatching { db.getReference("bans/${row.uid}").removeValue().await() }
            .exceptionOrNull()?.let { "Couldn't unban: ${it.message}" }

    /** Frees the username so they pick a new one next time they open Friends. */
    suspend fun resetUsername(row: UserRow): String? {
        if (row.username.isEmpty()) return "They have no username"
        val updates = hashMapOf<String, Any?>("users/${row.uid}/username" to null, "usernames/${row.username}" to null)
        return runCatching { db.reference.updateChildren(updates).await() }
            .exceptionOrNull()?.let { "Couldn't reset: ${it.message}" }
    }

    suspend fun announce(text: String): String? {
        val t = text.trim().take(500)
        if (t.isEmpty()) return "Write something first"
        return runCatching { db.getReference("announcement").setValue(mapOf("text" to t, "at" to ServerValue.TIMESTAMP)).await() }
            .exceptionOrNull()?.let { "Couldn't send: ${it.message}" }
    }

    suspend fun clearAnnouncement(): String? =
        runCatching { db.getReference("announcement").removeValue().await() }
            .exceptionOrNull()?.let { "Couldn't clear: ${it.message}" }

    /** Developer only: listen in on anyone who is hosting, without asking. */
    fun adminJoin(row: UserRow, service: MusicService) {
        val myName = _username.value?.takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            val snap = runCatching { db.getReference("sessions/${row.uid}").get().await() }.getOrNull()
            val sid = snap?.child("sid")?.getValue(String::class.java)
            val key = snap?.child("key")?.getValue(String::class.java)
            val link = if (sid != null && key != null) joinLink(Friend(row.uid, row.username, true, HostedSession(sid, key))) else null
            if (link == null) {
                _vibeStatus.value = "@${row.username} isn't hosting right now"
            } else {
                service.joinTogether(link, myName)
                _vibeStatus.value = "listening in on @${row.username}"
            }
        }
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
