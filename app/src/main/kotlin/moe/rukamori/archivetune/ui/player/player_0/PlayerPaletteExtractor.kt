package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// Spry "Red field": the full player sits on deep blood red (like the red-field photographs),
// with a warm cream accent instead of extracted artwork colours.
private const val SpryFieldDeep = 0xFF4A0806.toInt()
private const val SpryFieldGlow = 0xFF8E120E.toInt()
private const val SpryCream = 0xFFF6E3D6.toInt()

/**
 * Spry: the player always uses the fixed red-field palette instead of colors extracted
 * from the artwork, so every song matches the app theme.
 */
@Composable
internal fun PlayerPaletteExtractor(
    targetUrl: String?,
    trackUrl: String,
    onColorsExtracted: (vibrant: Int, darkMuted: Int, gradient: Int) -> Unit
) {
    LaunchedEffect(targetUrl, trackUrl) {
        onColorsExtracted(SpryCream, SpryFieldDeep, SpryFieldGlow)
    }
}
