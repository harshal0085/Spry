package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import moe.rukamori.archivetune.ui.theme.SpryRed

// Near-black instead of pure black so player surfaces derived from it still show depth.
private const val SpryPlayerBackground = 0xFF0A0A0A.toInt()

/**
 * Spry: the player always uses the fixed black + red palette instead of colors extracted
 * from the artwork, so every song matches the app theme.
 */
@Composable
internal fun PlayerPaletteExtractor(
    targetUrl: String?,
    trackUrl: String,
    onColorsExtracted: (vibrant: Int, darkMuted: Int, gradient: Int) -> Unit
) {
    LaunchedEffect(targetUrl, trackUrl) {
        onColorsExtracted(SpryRed.toArgb(), SpryPlayerBackground, SpryPlayerBackground)
    }
}
