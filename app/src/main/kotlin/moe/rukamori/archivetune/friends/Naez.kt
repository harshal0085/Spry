/*
 * Spry (2026) | Naez, the app's companion: a chibi sticker above the mini player who talks in short bubbles.
 * Stickers are still images (no animation); she reacts to playback, friends, time of day and the developer.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.R
import java.util.Calendar
import kotlin.random.Random

/** Her 12 half-body stickers, in the order of the sheet. The name is what the Dev tab sends as "mood". */
enum class NaezMood(val res: Int) {
    HAPPY(R.drawable.naez_01), CONFUSED(R.drawable.naez_02), ANNOYED(R.drawable.naez_03), FLUSTERED(R.drawable.naez_04),
    WAVE(R.drawable.naez_05), SLEEPY(R.drawable.naez_06), COOL(R.drawable.naez_07), CRY(R.drawable.naez_08),
    ANGRY(R.drawable.naez_09), TEA(R.drawable.naez_10), POUT(R.drawable.naez_11), SMUG(R.drawable.naez_12);

    companion object {
        // older names (the first 20-sticker set) still work in Dev messages
        private val aliases = mapOf("STARRY" to HAPPY, "LOVE" to HAPPY, "LAUGH" to HAPPY, "SURPRISED" to WAVE, "SHOCKED" to WAVE,
            "DIZZY" to CONFUSED, "NERVOUS" to FLUSTERED, "DETERMINED" to ANGRY, "COLD" to POUT)
        fun of(name: String?) = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: aliases[name?.uppercase()]
    }
}

/** One bubble. [sticky] bubbles wait for a tap (the intro); the rest fade on their own. */
/** [onOpen]: tapping this bubble does something (e.g. opens a chat) instead of just closing it. */
private data class NaezSay(
    val text: String,
    val mood: NaezMood,
    val sticky: Boolean = false,
    val onDone: (() -> Unit)? = null,
    val onOpen: (() -> Unit)? = null,
)

private object Lines {
    fun <T> List<T>.any1() = this[Random.nextInt(size)]

    val intro = listOf(
        "Oh? A new face. I'm Naez~ I live down here, above your music." to NaezMood.WAVE,
        "Play anything you like. I'll be listening too… quietly. Mostly." to NaezMood.TEA,
        "Add friends in the Friends tab. You can listen together, in sync." to NaezMood.HAPPY,
        "You can chat with them too. Messages vanish after a day, so say the nice things twice~" to NaezMood.SMUG,
        "Tap me whenever you're bored. I don't bite. Usually." to NaezMood.HAPPY,
    )

    fun greeting(hour: Int, name: String?) = when (hour) {
        in 5..11 -> listOf("Good morning${name.at()}~ Music before breakfast? Bold." to NaezMood.TEA, "Morning. I was awake first, just so you know." to NaezMood.SMUG)
        in 12..16 -> listOf("Afternoon${name.at()}. Let's find something good." to NaezMood.HAPPY, "Oh, it's you again. …I'm not complaining." to NaezMood.FLUSTERED)
        in 17..21 -> listOf("Evening${name.at()}~ Long day?" to NaezMood.TEA, "You came back. I knew you would~" to NaezMood.SMUG)
        else -> listOf("It's so late… even I sleep sometimes, you know." to NaezMood.SLEEPY, "Late night music with me again? I'm starting to think you like my company~" to NaezMood.FLUSTERED)
    }.any1()

    // Taps: about being poked, never about a song (those only come while music plays, below).
    val poke = listOf(
        "Hm? Did you need something~" to NaezMood.CONFUSED,
        "That tickles. Please stop. …Or don't." to NaezMood.FLUSTERED,
        "Poking an elf is rude, you know~" to NaezMood.POUT,
        "I've been alive for a thousand years and you still surprise me." to NaezMood.WAVE,
        "If you keep tapping me I'll start charging rent." to NaezMood.ANNOYED,
        "Fufu. You're cute when you're bored." to NaezMood.HAPPY,
        "Yes, yes, I'm here. I'm always here." to NaezMood.SMUG,
        "Careful, you'll wrinkle my coat." to NaezMood.COOL,
    )

    val flirt = listOf(
        "You keep coming back to tap me… should I be flattered?" to NaezMood.FLUSTERED,
        "Stop staring. …I didn't say stop completely~" to NaezMood.FLUSTERED,
        "You're lucky I like you." to NaezMood.SMUG,
    )

    // Only while a song plays or when it changes.
    val songFlirt = listOf(
        "You have good taste… in music, I mean~" to NaezMood.FLUSTERED,
        "If this song were a person, I'd be jealous." to NaezMood.POUT,
        "Don't look at me like that while this song plays. It's unfair." to NaezMood.FLUSTERED,
        "I'd share my headphones with you for this one. Just this once~" to NaezMood.FLUSTERED,
    )

    val secretAngry = listOf(
        "THAT'S ENOUGH TAPPING. I am a dignified elf." to NaezMood.ANGRY,
        "…You found my limit. Congratulations. Now stop." to NaezMood.ANGRY,
    )

    fun songComment(title: String) = listOf(
        "Oh, \"$title\". Nice pick~" to NaezMood.HAPPY,
        "\"$title\"… I'll allow it." to NaezMood.SMUG,
        "I know this one! …Probably." to NaezMood.HAPPY,
        "Hmm. Not what I expected from you. Interesting~" to NaezMood.CONFUSED,
    ).any1()

    val skip = listOf(
        "Skipping already? You didn't even give it a chance~" to NaezMood.POUT,
        "Next! …You're picky today." to NaezMood.ANNOYED,
        "Wait, I liked that one!" to NaezMood.CRY,
    )

    fun repeat(title: String, n: Int) = when {
        n >= 25 -> "\"$title\", time number $n. At this point it's a relationship." to NaezMood.FLUSTERED
        n >= 10 -> "That's $n plays of \"$title\". I'm not judging~" to NaezMood.SMUG
        else -> "\"$title\" again? $n times now. You really like it, huh." to NaezMood.HAPPY
    }

    fun milestone(n: Int) = when (n) {
        10 -> "10 songs together. We're practically friends now~" to NaezMood.HAPPY
        50 -> "50 songs! Fine, I'll admit it. You're fun to listen with." to NaezMood.HAPPY
        100 -> "100 songs. I made you a tiny medal in my head. It's lovely." to NaezMood.HAPPY
        250 -> "250 songs… do you ever sleep? (Me neither.)" to NaezMood.CONFUSED
        else -> "$n songs together. I'm keeping count, you know~" to NaezMood.FLUSTERED
    }

    val lateNight = listOf(
        "It's past midnight and you're still here… so am I." to NaezMood.SLEEPY,
        "Late night songs always hit different, don't they." to NaezMood.TEA,
    )

    fun friendOnline(name: String) = listOf(
        "@$name just came online~ Go say hi." to NaezMood.WAVE,
        "Oh? @$name is here. Listen together?" to NaezMood.HAPPY,
    ).any1()

    private fun String?.at() = if (this.isNullOrBlank()) "" else ", @$this"
}

private val MILESTONES = setOf(10, 50, 100, 250, 500, 1000, 2500, 5000)
private const val AUTO_GAP_MS = 90_000L

@Composable
fun NaezHost(
    playerExpansion: () -> Float,
    modifier: Modifier = Modifier,
    onOpenChat: (uid: String, username: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("naez", Context.MODE_PRIVATE) }
    val connection = LocalPlayerConnection.current
    val queue = remember { mutableStateListOf<NaezSay>() }
    var idleMood by remember { mutableStateOf(NaezMood.TEA) }
    var lastAuto by remember { mutableStateOf(0L) }
    val hidden by remember { derivedStateOf { playerExpansion() > 0.05f } }

    fun say(line: Pair<String, NaezMood>, sticky: Boolean = false, onDone: (() -> Unit)? = null) {
        // sticky lines (intro, developer messages) are never dropped
        if (sticky || queue.size < 4) queue.add(NaezSay(line.first, line.second, sticky, onDone))
    }

    /** Lines she offers on her own, rate-limited so she never nags. */
    fun sayAuto(line: Pair<String, NaezMood>) {
        val now = System.currentTimeMillis()
        if (now - lastAuto < AUTO_GAP_MS || queue.isNotEmpty()) return
        lastAuto = now
        say(line)
    }

    // intro on first open, otherwise a greeting
    LaunchedEffect(Unit) {
        delay(2500)
        if (!prefs.getBoolean("intro_done", false)) {
            Lines.intro.forEachIndexed { i, l ->
                say(l, sticky = true, onDone = if (i == Lines.intro.lastIndex) ({ prefs.edit().putBoolean("intro_done", true).apply() }) else null)
            }
        } else {
            lastAuto = System.currentTimeMillis()
            say(Lines.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), SpryFriends.username.value))
        }
    }

    // the current bubble: sticky ones wait for a tap, others fade after a few seconds
    val current = queue.firstOrNull()
    LaunchedEffect(current) {
        if (current == null || current.sticky) return@LaunchedEffect
        delay(3500L + current.text.length * 45L + if (current.onOpen != null) 6000L else 0L)
        if (queue.firstOrNull() === current) queue.removeAt(0).onDone?.invoke()
    }

    // playback: sleepy when paused for a while, comments and counts on song changes
    if (connection != null) {
        val playing by connection.isPlaying.collectAsState()
        val song by connection.mediaMetadata.collectAsState()
        LaunchedEffect(playing) {
            if (playing) {
                idleMood = if (Random.nextBoolean()) NaezMood.TEA else NaezMood.HAPPY
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val night = java.time.LocalDate.now().toString()
                if (hour in 0..3 && prefs.getString("late_night", null) != night) {
                    prefs.edit().putString("late_night", night).apply()
                    sayAuto(Lines.lateNight.random())
                }
            } else {
                delay(20_000)
                idleMood = NaezMood.SLEEPY
            }
        }
        var songStart by remember { mutableStateOf(0L) }
        LaunchedEffect(song?.id) {
            val s = song ?: return@LaunchedEffect
            val now = System.currentTimeMillis()
            val quickSkip = songStart != 0L && now - songStart < 15_000
            songStart = now
            val total = prefs.getInt("plays", 0) + 1
            val mine = prefs.getInt("p_${s.id}", 0) + 1
            prefs.edit().putInt("plays", total).putInt("p_${s.id}", mine).apply()
            delay(4000) // let the song start before she talks
            if (!connection.isPlaying.value) return@LaunchedEffect // song lines only while music is actually playing
            when {
                total in MILESTONES -> { lastAuto = 0L; sayAuto(Lines.milestone(total)) }
                mine in setOf(5, 10, 25, 50, 100) -> { lastAuto = 0L; sayAuto(Lines.repeat(s.title, mine)) }
                quickSkip && Random.nextInt(4) == 0 -> sayAuto(Lines.skip.random())
                Random.nextInt(8) == 0 -> sayAuto(if (Random.nextInt(3) == 0) Lines.songFlirt.random() else Lines.songComment(s.title))
            }
        }
    }

    // friends coming online (not the ones already online when the app opened)
    val friends by SpryFriends.friends.collectAsState()
    var seenOnline by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(friends) {
        val online = friends.filter { it.online }.map { it.uid }.toSet()
        val before = seenOnline
        seenOnline = online
        if (before == null) return@LaunchedEffect
        friends.firstOrNull { it.online && it.uid !in before }?.let { sayAuto(Lines.friendOnline(it.username)) }
    }

    // new chat messages: "@name says…", tap the bubble to open that chat
    val me = SpryFriends.user.collectAsState().value?.uid
    friends.forEach { f ->
        androidx.compose.runtime.key(f.uid) {
            val msgs by remember(f.uid) { SpryChat.messages(f.uid) }.collectAsState(initial = null)
            LaunchedEffect(msgs) {
                val last = msgs?.maxByOrNull { it.at } ?: return@LaunchedEffect
                val seenKey = "msg_${f.uid}"
                val seen = prefs.getLong(seenKey, -1L)
                if (last.at > seen) prefs.edit().putLong(seenKey, last.at).apply()
                // first look at this chat, old messages, my own messages, or already reading this chat: stay quiet
                if (seen < 0 || last.at <= seen || last.from == me || NaezAnchor.awayScreens > 0) return@LaunchedEffect
                val preview = last.text.replace('\n', ' ').let { if (it.length > 60) it.take(60) + "…" else it }
                queue.removeAll { it.onOpen != null && it.text.startsWith("@${f.username} ") } // one bubble per friend
                queue.add(
                    NaezSay(
                        text = "@${f.username} says: \"$preview\"\nTap to read~",
                        mood = NaezMood.WAVE,
                        onOpen = { onOpenChat(f.uid, f.username) },
                    ),
                )
            }
        }
    }

    // the developer speaking through her: to everyone (config/naez) or to this phone (a naez alert)
    val devLine by SpryFriends.naezLine.collectAsState()
    LaunchedEffect(devLine) {
        val l = devLine ?: return@LaunchedEffect
        if (l.at <= prefs.getLong("dev_line_at", 0L)) return@LaunchedEffect
        prefs.edit().putLong("dev_line_at", l.at).apply()
        say(l.text to (NaezMood.of(l.mood) ?: NaezMood.WAVE), sticky = true)
    }
    val alerts by SpryFriends.alerts.collectAsState()
    LaunchedEffect(alerts) {
        alerts.filter { it.naez }.forEach { a ->
            if (queue.none { it.text == a.text }) say(a.text to (NaezMood.of(a.mood) ?: NaezMood.WAVE), sticky = true) { SpryFriends.dismissAlert(a) }
        }
    }

    // taps: dismiss/advance the bubble, or poke her (tap fast enough and she snaps)
    val taps = remember { ArrayDeque<Long>() }
    fun onTap() {
        queue.firstOrNull()?.let { queue.removeAt(0); it.onDone?.invoke(); it.onOpen?.invoke(); return }
        val now = System.currentTimeMillis()
        taps.addLast(now)
        while (taps.isNotEmpty() && now - taps.first() > 4000) taps.removeFirst()
        if (taps.size >= 8) {
            taps.clear()
            say(Lines.secretAngry.random())
        } else {
            say(if (Random.nextInt(5) == 0) Lines.flirt.random() else Lines.poke.random())
        }
    }

    if (hidden || NaezAnchor.awayScreens > 0) return
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val hasSong = connection?.mediaMetadata?.collectAsState()?.value != null
    // No mini player to stand on: only come out while she has something to say (e.g. the intro).
    if (!hasSong && current == null) return
    // She always sits in the bottom-right corner (no dragging).
    val onRight = true
    val corner = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    var myTop by remember { mutableStateOf(0f) }
    var bubbleH by remember { mutableStateOf(0) }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxSize().onGloballyPositioned { myTop = it.boundsInRoot().top },
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cornerX = with(density) { (maxWidth - STICKER_W).toPx() }
        val boxH = constraints.maxHeight.toFloat()
        val stickerHPx = with(density) { STICKER_H.toPx() }
        val sink = with(density) { 1.dp.toPx() } // overlap the edge by a hair so there's no gap
        // y (in this box) where her feet... well, her waist, rests: the mini player's top edge, read at layout
        // time so she follows it when the nav bar hides; without a song, the top of the nav area.
        fun standY(): Float {
            val mini = NaezAnchor.miniTop
            val lib = moe.rukamori.archivetune.ui.component.NavAnchors.library
            return when {
                hasSong && !mini.isNaN() && mini > 0f -> mini - myTop + sink
                lib != null && lib.top > 0f -> lib.top - myTop + sink // no song: on the nav bar, above Library
                else -> boxH - with(density) { bottom.toPx() } + sink
            }
        }
        fun standX(): Float {
            val play = NaezAnchor.playCenterX
            val lib = moe.rukamori.archivetune.ui.component.NavAnchors.library
            val half = with(density) { STICKER_W.toPx() } / 2f
            return when {
                hasSong && !play.isNaN() && play > 0f -> play - half
                lib != null -> lib.center.x - half
                else -> cornerX
            }
        }
        AnimatedVisibility(
            visible = current != null,
            modifier = Modifier
                .align(if (onRight) Alignment.TopEnd else Alignment.TopStart)
                .offset { androidx.compose.ui.unit.IntOffset(0, (standY() - stickerHPx - bubbleH).toInt()) }
                .onSizeChanged { bubbleH = it.height }
                .padding(start = 10.dp, end = 10.dp, bottom = 4.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (onRight) 1f else 0f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (onRight) 1f else 0f, 1f)),
        ) {
            val shown = current
            Text(
                text = shown?.text.orEmpty() + if (shown?.sticky == true) "  ›" else "",
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .widthIn(max = 230.dp)
                    .background(Color(0xE61C1A22), corner)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), corner)
                    .pointerInput(Unit) { detectTapGestures { onTap() } }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
        Crossfade(
            targetState = current?.mood ?: idleMood,
            label = "NaezMood",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    androidx.compose.ui.unit.IntOffset(standX().toInt(), (standY() - stickerHPx).toInt())
                }
                .size(STICKER_W, STICKER_H)
                .pointerInput(Unit) { detectTapGestures { onTap() } },
        ) { mood ->
            Image(bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(mood.res), contentDescription = "Naez", filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
        }
    }
}

/** The mini player's top edge in root pixels (set by the mini player itself); NaN until it's laid out. */
object NaezAnchor {
    var miniTop by mutableStateOf(Float.NaN)

    /** The mini player's play/pause button centre x in root pixels; she stands centred over it. */
    var playCenterX by mutableStateOf(Float.NaN)

    /** Screens open right now where she'd be in the way (e.g. chat); she hides while this is above 0. */
    var awayScreens by mutableStateOf(0)
}

// half-body art (344x410 px, kept full size so she stays sharp); she stands on the mini player's top edge
private val STICKER_H = 88.dp
private val STICKER_W = 74.dp
