/*
 * Spry (2026) | "Crane" visual world: black ground, a vermilion band, dreamy haze, film grain,
 * a fine golden-ratio construction and red line-art waves. Drawing helpers only; no layout.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Crane {
    val Ink = Color(0xFF0A0909)
    val Paper = Color(0xFFEFEAE2)
    val Band = Color(0xFF9E1410)
    val Vermilion = Color(0xFFE0261C)
    val Gold = Color(0xFFC9A96A)
    val Cream = Color(0xFFFADEC8)

    const val PHI = 1.618034f
}

/** Static film grain, generated once and tiled. */
private val grainBitmap: ImageBitmap by lazy {
    val size = 192
    val rnd = java.util.Random(1618)
    val pixels = IntArray(size * size) {
        val a = (rnd.nextFloat() * rnd.nextFloat() * 255).toInt() // mostly faint specks, a few bright ones
        (a shl 24) or 0x00F5EDE4
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val grainBrush: ShaderBrush by lazy { ShaderBrush(ImageShader(grainBitmap, TileMode.Repeated, TileMode.Repeated)) }

fun DrawScope.drawCraneGrain(alpha: Float = 0.10f) = drawRect(grainBrush, alpha = alpha)

/** Dreamy light bleeding out from [center]: cream core, red middle, fading into black. */
fun DrawScope.drawCraneHaze(
    center: Offset,
    radius: Float,
    strength: Float = 1f,
) {
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to Crane.Cream.copy(alpha = 0.42f * strength),
                0.38f to Color(0xFFBE281C).copy(alpha = 0.28f * strength),
                0.58f to Color(0xFF6E0C0A).copy(alpha = 0.14f * strength),
                0.74f to Color.Transparent,
                center = center,
                radius = radius,
            ),
        radius = radius,
        center = center,
    )
}

/**
 * Fine golden-ratio construction inside [frame] (a φ:1 landscape box): nested squares and golden
 * rectangles, a five-turn spiral, φ circles, dashed diagonals and a 0 / 0.618 / 1 ruler.
 */
fun DrawScope.drawCraneGolden(
    frame: Rect,
    alpha: Float = 1f,
) {
    val line = Stroke(width = 0.6.dp.toPx())
    val spiralStroke = Stroke(width = 0.9.dp.toPx())
    val faint = Crane.Gold.copy(alpha = 0.26f * alpha)
    val spiral = Crane.Gold.copy(alpha = 0.55f * alpha)

    drawRect(faint, frame.topLeft, frame.size, style = line)

    // Peel squares off the golden rectangle, rotating left, top, right, bottom.
    var r = frame
    val path = Path()
    repeat(6) { i ->
        val s = minOf(r.width, r.height)
        val sq: Rect
        val arcStart: Float
        when (i % 4) {
            0 -> { sq = Rect(r.left, r.top, r.left + s, r.top + s); r = Rect(sq.right, r.top, r.right, r.bottom); arcStart = 180f }
            1 -> { sq = Rect(r.right - s, r.top, r.right, r.top + s); r = Rect(r.left, sq.bottom, r.right, r.bottom); arcStart = 270f }
            2 -> { sq = Rect(r.right - s, r.bottom - s, r.right, r.bottom); r = Rect(r.left, r.top, sq.left, r.bottom); arcStart = 0f }
            else -> { sq = Rect(r.left, r.bottom - s, r.left + s, r.bottom); r = Rect(r.left, r.top, r.right, sq.top); arcStart = 90f }
        }
        drawRect(faint, sq.topLeft, sq.size, style = line)
        // Quarter circle of radius s centred on the square's inner corner.
        val c = when (i % 4) {
            0 -> Offset(sq.right, sq.bottom)
            1 -> Offset(sq.left, sq.bottom)
            2 -> Offset(sq.left, sq.top)
            else -> Offset(sq.right, sq.top)
        }
        path.arcTo(Rect(c.x - s, c.y - s, c.x + s, c.y + s), arcStart, 90f, forceMoveTo = i == 0)
    }
    drawPath(path, spiral, style = spiralStroke)

    // φ circles around the first square's centre, crosshair and diagonals.
    val firstSide = minOf(frame.width, frame.height)
    val c0 = Offset(frame.left + firstSide / 2, frame.top + firstSide / 2)
    drawCircle(faint, firstSide / 2, c0, style = line)
    drawCircle(faint, firstSide / 2 / Crane.PHI, c0, style = line)
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
    drawLine(faint, frame.topLeft, frame.bottomRight, line.width, pathEffect = dash)
    drawLine(faint, frame.bottomLeft, Offset(frame.left + firstSide, frame.top), line.width, pathEffect = dash)

    // Ruler under the frame: 0, 0.382, 0.618, 1.
    val y = frame.bottom + 10.dp.toPx()
    drawLine(faint.copy(alpha = 0.4f * alpha), Offset(frame.left, y), Offset(frame.right, y), line.width)
    listOf(0f, 0.382f, 0.618f, 1f).forEach { t ->
        val x = frame.left + frame.width * t
        val h = if (t == 0.618f) 6.dp.toPx() else 4.dp.toPx()
        drawLine(spiral, Offset(x, y - h), Offset(x, y + h), line.width)
    }
}

/** Red line-art waves along the bottom [height] of the area, like a woodblock sea drawn in one colour. */
fun DrawScope.drawCraneWaves(
    height: Float,
    alpha: Float = 1f,
) {
    val top = size.height - height
    listOf(0f to 1f, 0.18f to 0.65f, 0.36f to 0.45f).forEachIndexed { i, (dy, a) ->
        val y0 = top + height * (0.25f + dy)
        val amp = height * (0.18f - i * 0.03f)
        val p = Path().apply {
            moveTo(0f, y0)
            val step = size.width / 4
            for (k in 0 until 4) {
                val x = step * k
                quadraticTo(x + step * 0.25f, y0 - amp, x + step * 0.5f, y0)
                quadraticTo(x + step * 0.75f, y0 + amp * 0.6f, x + step, y0)
            }
        }
        drawPath(p, Color(0xFFC21A12).copy(alpha = a * alpha), style = Stroke(width = (1.2f - i * 0.2f).dp.toPx()))
    }
}

/** The vermilion band with a short vertical Japanese line, as in the crane photograph. */
@Composable
fun CraneBand(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().width(44.dp).background(Crane.Band.copy(alpha = 0.92f)).padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        text.forEach { ch ->
            Text(ch.toString(), style = TextStyle(color = Crane.Paper, fontSize = 15.sp, fontFamily = FontFamily.Serif))
        }
    }
}

/** A φ landscape frame of [width] at [topLeft]. */
fun goldenFrame(
    topLeft: Offset,
    width: Float,
) = Rect(topLeft, Size(width, width / Crane.PHI))

/** The two composed Crane scenes, so screens only call one function. */
object CraneScenes {
    /** Home hero: art veiled in ink, light bleeding from upper left, φ construction, grain. */
    fun DrawScope.hero() {
        drawRect(Crane.Ink.copy(alpha = 0.45f))
        drawCraneHaze(Offset(size.width * 0.28f, size.height * 0.34f), size.width * 0.95f)
        val w = size.width * 0.62f
        drawCraneGolden(goldenFrame(Offset(16.dp.toPx(), size.height * 0.16f), w), alpha = 0.9f)
        drawCraneGrain(0.12f)
    }

    /** Player field: black, glow behind the cover (upper third), φ around it, red sea along the bottom, grain. */
    fun DrawScope.player() {
        val cover = Offset(size.width / 2, size.height * 0.33f)
        drawCraneHaze(cover, size.width * 0.85f, strength = 1.1f)
        val w = size.width * 0.86f
        drawCraneGolden(goldenFrame(Offset(cover.x - w / 2, cover.y - w / Crane.PHI / 2), w), alpha = 0.7f)
        drawCraneWaves(size.height * 0.12f, alpha = 0.8f)
        drawCraneGrain(0.10f)
    }
}
