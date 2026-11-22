package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// Spry: the player field is the song's dominant colour (PlayerBackgroundLayers); these are its base tones,
// with a warm cream accent instead of extracted artwork colours.
private const val SpryFieldDeep = 0xFF0B0A10.toInt()
private const val SpryFieldGlow = 0xFF141220.toInt()
private const val SpryCream = 0xFFF4F1F8.toInt()

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
