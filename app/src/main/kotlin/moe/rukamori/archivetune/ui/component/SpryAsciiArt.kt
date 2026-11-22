/*
 * Spry (2026) | "Signal": album art drawn as fine ASCII, like the ink-on-charcoal poster references.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Darkest to brightest: white characters on charcoal, so brighter pixels get denser glyphs. */
private const val RAMP = " .'`^\",:;-~+=*!/?|#%@"

/** Tiny glyphs on purpose: the picture should read as fine texture, never as big letters. */
private val GlyphSize = 5.sp

/**
 * Renders [url] as ASCII art filling the given space (center-cropped like ContentScale.Crop).
 * Shows nothing until the image has loaded.
 */
@Composable
fun SpryAsciiArt(
    url: String?,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFEDEDED),
) {
    val context = LocalContext.current
    val glyphPx = with(LocalDensity.current) { GlyphSize.toPx() }
    BoxWithConstraints(modifier) {
        // Monospace cells are about 0.6 as wide as they are tall.
        val cols = (constraints.maxWidth / (glyphPx * 0.6f)).toInt().coerceIn(20, 220)
        val rows = (constraints.maxHeight / glyphPx).toInt().coerceIn(10, 400)
        var art by remember(url, cols, rows) { mutableStateOf<String?>(null) }
        LaunchedEffect(url, cols, rows) {
            if (url.isNullOrBlank()) return@LaunchedEffect
            art =
                withContext(Dispatchers.Default) {
                    val request = ImageRequest.Builder(context).data(url).size(512).allowHardware(false).build()
                    val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
                    toAscii(bitmap, cols, rows)
                }
        }
        art?.let {
            Text(
                text = it,
                softWrap = false,
                maxLines = rows,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = GlyphSize, lineHeight = GlyphSize, color = color),
            )
        }
    }
}

/** Center-crops [src] to the cols:rows cell aspect, samples one pixel per cell, maps luminance to [RAMP]. */
internal fun toAscii(
    src: Bitmap,
    cols: Int,
    rows: Int,
): String {
    val targetAspect = cols * 0.6f / rows
    val srcAspect = src.width.toFloat() / src.height
    val cropW = if (srcAspect > targetAspect) (src.height * targetAspect).toInt() else src.width
    val cropH = if (srcAspect > targetAspect) src.height else (src.width / targetAspect).toInt()
    val cropped = Bitmap.createBitmap(src, (src.width - cropW) / 2, (src.height - cropH) / 2, cropW.coerceAtLeast(1), cropH.coerceAtLeast(1))
    val small = Bitmap.createScaledBitmap(cropped, cols, rows, true)
    val sb = StringBuilder(rows * (cols + 1))
    for (y in 0 until rows) {
        for (x in 0 until cols) {
            val p = small.getPixel(x, y)
            val lum = (0.299f * (p shr 16 and 0xFF) + 0.587f * (p shr 8 and 0xFF) + 0.114f * (p and 0xFF)) / 255f
            sb.append(RAMP[(lum * (RAMP.length - 1)).toInt().coerceIn(0, RAMP.length - 1)])
        }
        if (y < rows - 1) sb.append('\n')
    }
    return sb.toString()
}
