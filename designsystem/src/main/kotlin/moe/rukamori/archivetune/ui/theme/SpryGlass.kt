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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The playing song's dominant colour (animated); Spry's surfaces take their tint from it. */
val LocalSongColor = compositionLocalOf { Color(0xFF0B0A10) }

private val Ink = Color(0xFF07060A)

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
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .background(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.09f),
                0.42f to Color.Transparent,
                1f to Color.Transparent,
            ),
        )
        .border(
            rim,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.55f),
                0.35f to Color.White.copy(alpha = 0.10f),
                0.7f to Color.White.copy(alpha = 0.06f),
                1f to Color.White.copy(alpha = 0.32f),
            ),
            shape,
        )
        .then(
            if (!glints) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    drawContent()
                    fun glint(at: Offset, r: Float) = drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), at, r),
                        r,
                        at,
                    )
                    glint(Offset(size.width * 0.84f, 0f), 7.dp.toPx())
                    glint(Offset(size.width * 0.14f, size.height), 6.dp.toPx())
                    glint(Offset(size.width, size.height * 0.35f), 4.dp.toPx())
                }
            },
        )
}
