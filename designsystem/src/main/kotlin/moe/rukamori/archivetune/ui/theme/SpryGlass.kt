/*
 * Spry (2026) | dark liquid-glass panels tinted by the playing song.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The playing song's colour, already toned by [spryTone] (animated); Spry's surfaces take their tint from it. */
val LocalSongColor = compositionLocalOf { Color(0xFF0B0A10) }

private val Ink = Color(0xFF07060A)

private fun Color.toHsl(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        red -> ((green - blue) / d + (if (green < blue) 6f else 0f))
        green -> ((blue - red) / d + 2f)
        else -> ((red - green) / d + 4f)
    } * 60f
    return floatArrayOf(h, s, l)
}

/** HSL colour; h in degrees, s and l in 0..1. */
fun hsl(h: Float, s: Float, l: Float): Color = Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/**
 * Keeps a cover's hue but makes it usable behind white type: saturation capped, lightness clamped.
 * A near-white cover becomes a warm charcoal, a neon one a deep jewel tone.
 */
fun spryTone(c: Color): Color {
    val (h, s, l) = c.toHsl()
    return hsl(h, minOf(s, 0.55f), l.coerceIn(0.14f, 0.28f))
}

/** Dark app palette whose surfaces carry [song]'s hue (song should already be [spryTone]d). */
fun spryColorScheme(base: androidx.compose.material3.ColorScheme, song: Color): androidx.compose.material3.ColorScheme {
    val (h, s0) = song.toHsl()
    val s = s0 * 0.45f
    fun ink(l: Float) = hsl(h, s, l)
    val soft = Color(0xFFDCD8E4)
    return base.copy(
        primaryContainer = ink(0.2f),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFB2AEBC),
        onSecondary = Ink,
        secondaryContainer = ink(0.18f),
        onSecondaryContainer = Color.White,
        tertiary = soft,
        tertiaryContainer = ink(0.16f),
        onTertiaryContainer = Color.White,
        background = ink(0.075f),
        surface = ink(0.075f),
        surfaceVariant = ink(0.14f),
        surfaceTint = Color.Transparent,
        outline = ink(0.3f),
        outlineVariant = ink(0.17f),
        surfaceBright = ink(0.2f),
        surfaceDim = ink(0.045f),
        surfaceContainerLowest = ink(0.045f),
        surfaceContainerLow = ink(0.08f),
        surfaceContainer = ink(0.1f),
        surfaceContainerHigh = ink(0.125f),
        surfaceContainerHighest = ink(0.155f),
    )
}

/**
 * Dark glass slab: smoky fill tinted by [tint], a diagonal sheen, a light-catching rim
 * (bright top-left, fading, catching again bottom-right) and two small specular glints.
 */
@Composable
fun Modifier.spryGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = LocalSongColor.current,
    rim: Dp = 1.dp,
    glints: Boolean = true,
    fillAlpha: Float = 1f,
): Modifier {
    val top = lerp(tint, Ink, 0.62f).copy(alpha = 0.62f * fillAlpha)
    val bottom = lerp(tint, Ink, 0.84f).copy(alpha = 0.78f * fillAlpha)
    val tilt = LocalGlassTilt.current
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawWithContent {
            // tilt is read here, in the draw phase, so phone movement only redraws: no recomposition.
            val t = tilt.value
            val w = size.width
            val h = size.height
            val lx = (0.15f - t.x * 0.6f)
            val ly = (-0.1f + t.y * 0.5f)
            val lightStart = Offset(w * lx, h * ly)
            val lightEnd = Offset(w * (1f - lx), h * (1f - ly))
            // diagonal sheen that follows the light
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.42f to Color.Transparent,
                    1f to Color.Transparent,
                    start = lightStart,
                    end = lightEnd,
                ),
            )
            // thickness: a soft dark band inside the bottom edge
            val depth = 14.dp.toPx().coerceAtMost(h / 3f)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), startY = h - depth, endY = h),
                topLeft = Offset(0f, h - depth),
            )
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            // outer rim: bright where the light hits, fading, catching again on the far edge
            drawOutline(
                outline,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.6f),
                    0.35f to Color.White.copy(alpha = 0.10f),
                    0.7f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = 0.34f),
                    start = lightStart,
                    end = lightEnd,
                ),
                style = Stroke(rim.toPx() * 2f), // half falls outside the clip, leaving a [rim]-wide edge
            )
            // inner rim: a faint second edge that sells the glass thickness
            val inset = rim.toPx() * 2.5f
            val inner = shape.createOutline(Size(w - inset * 2, h - inset * 2), layoutDirection, this)
            translate(inset, inset) {
                drawOutline(inner, Color.White.copy(alpha = 0.06f), style = Stroke(rim.toPx()))
            }
            if (glints) {
                fun glint(at: Offset, r: Float) = drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), at, r),
                    r,
                    at,
                )
                glint(Offset(w * (0.84f - t.x * 0.35f).coerceIn(0.08f, 0.92f), 0f), 7.dp.toPx())
                glint(Offset(w * (0.14f - t.x * 0.35f).coerceIn(0.08f, 0.92f), h), 6.dp.toPx())
                glint(Offset(w, h * (0.35f + t.y * 0.3f).coerceIn(0.15f, 0.85f)), 4.dp.toPx())
            }
        }
}

/** Phone tilt in -1..1 per axis (x: left/right, y: forward/back), relative to how the phone is usually held. */
val LocalGlassTilt = compositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }
