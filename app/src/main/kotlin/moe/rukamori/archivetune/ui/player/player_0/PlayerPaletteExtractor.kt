package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// Spry "Crane": the full player sits on ink black; its haze and red sea are drawn by CraneScenes,
// with a warm cream accent instead of extracted artwork colours.
private const val SpryFieldDeep = 0xFF0A0909.toInt()
private const val SpryFieldGlow = 0xFF1A0705.toInt()
private const val SpryCream = 0xFFEFEAE2.toInt()

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
