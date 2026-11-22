package moe.rukamori.archivetune.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

val LocalArtworkSurfaceColor = compositionLocalOf<State<Color>?> { null }

@Immutable
data class ArtworkBackgroundState(
    val targetSurface: Color,
    val animatedColor: State<Color>,
    val isActive: Boolean = true,
) {
    val backgroundColor: Color get() = animatedColor.value
    val surfaceColor: Color get() = targetSurface
}

/**
 * Spry: pages (playlists, albums, artists) never tint their background from the artwork,
 * so the whole app keeps the true-black theme. Always returns an inactive state.
 */
@Composable
fun rememberArtworkBackground(
    @Suppress("UNUSED_PARAMETER") artworkModel: Any?,
    @Suppress("UNUSED_PARAMETER") enabled: Boolean = true,
): ArtworkBackgroundState {
    val surface = MaterialTheme.colorScheme.surface
    return ArtworkBackgroundState(
        targetSurface = surface,
        animatedColor = rememberUpdatedState(surface),
        isActive = false,
    )
}

fun Modifier.artworkBackground(artworkBackground: ArtworkBackgroundState): Modifier =
    if (artworkBackground.isActive) {
        this.drawBehind {
            drawRect(artworkBackground.animatedColor.value)
        }
    } else {
        this
    }

@Composable
fun ProvideArtworkSurface(
    artworkBackground: ArtworkBackgroundState,
    content: @Composable () -> Unit,
) {
    if (artworkBackground.isActive && artworkBackground.targetSurface != Color.Unspecified) {
        CompositionLocalProvider(
            LocalArtworkSurfaceColor provides artworkBackground.animatedColor,
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(surface = artworkBackground.targetSurface),
                content = content,
            )
        }
    } else {
        content()
    }
}
