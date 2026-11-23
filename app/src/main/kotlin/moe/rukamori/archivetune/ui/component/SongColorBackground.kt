/*
 * Spry (2026) | the background is simply the playing song's dominant colour, darkened for legibility.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fills its space with [url]'s colour mixed toward [ground] by [darken] (0 = pure colour, 1 = ground).
 * The colour itself is already animated by [rememberSongColor]; animating again here made it lag.
 */
@Composable
fun SongColorBackground(
    url: String?,
    modifier: Modifier = Modifier,
    darken: Float = 0.6f,
    ground: Color = Color(0xFF0B0A10),
) {
    val song = rememberSongColor(url, ground)
    Box(modifier.background(lerp(song, ground, darken)))
}

/** Toned cover colours by URL, so a song seen before (or prefetched) changes colour instantly. */
private val songColors = LruCache<String, Color>(200)

/** Works out [url]'s toned colour ahead of time (e.g. for the next song in the queue). */
suspend fun prefetchSongColor(context: Context, url: String?) {
    if (url.isNullOrBlank() || songColors.get(url) != null) return
    computeSongColor(context, url)
}

private suspend fun computeSongColor(context: Context, url: String): Color? =
    withContext(Dispatchers.Default) {
        songColors.get(url)?.let { return@withContext it }
        val request = ImageRequest.Builder(context).data(url).size(64).allowHardware(false).build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
        val c = Palette.from(bitmap).generate().dominantSwatch?.rgb?.let { moe.rukamori.archivetune.ui.theme.spryTone(Color(it)) }
        c?.also { songColors.put(url, it) }
    }

/** [url]'s toned colour, animated between songs; [ground] until one is known. */
@Composable
fun rememberSongColor(url: String?, ground: Color = Color(0xFF0B0A10)): Color {
    val context = LocalContext.current
    var dominant by remember { mutableStateOf(ground) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        dominant = songColors.get(url) ?: computeSongColor(context, url) ?: return@LaunchedEffect
    }
    return animateColorAsState(dominant, tween(450), label = "SongDominant").value
}
