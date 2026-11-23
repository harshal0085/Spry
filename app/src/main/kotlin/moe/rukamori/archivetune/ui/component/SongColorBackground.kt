/*
 * Spry (2026) | the background is simply the playing song's dominant colour, darkened for legibility.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.component

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
 * Fills its space with [url]'s dominant colour mixed toward [ground] by [darken] (0 = pure colour,
 * 1 = ground). Fades when the song changes; shows [ground] until a colour is known.
 */
@Composable
fun SongColorBackground(
    url: String?,
    modifier: Modifier = Modifier,
    darken: Float = 0.6f,
    ground: Color = Color(0xFF0B0A10),
) {
    val dominant = rememberSongColor(url, ground)
    val color by animateColorAsState(lerp(dominant, ground, darken), tween(800), label = "SongColor")
    Box(modifier.background(color))
}

/** [url]'s dominant colour, animated between songs; [ground] until one is known. */
@Composable
fun rememberSongColor(url: String?, ground: Color = Color(0xFF0B0A10)): Color {
    val context = LocalContext.current
    var dominant by remember { mutableStateOf(ground) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        dominant =
            withContext(Dispatchers.Default) {
                val request = ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build()
                val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
                Palette.from(bitmap).generate().dominantSwatch?.rgb?.let { moe.rukamori.archivetune.ui.theme.spryTone(Color(it)) }
            } ?: return@LaunchedEffect
    }
    return animateColorAsState(dominant, tween(800), label = "SongDominant").value
}
