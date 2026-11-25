/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.graphics

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

object GraphicsContract {
    const val GRAPHICS_SETTINGS_ROUTE = "settings/graphics"

    const val HIGH_REFRESH_RATE_THRESHOLD_FPS = 60.5f
}

@Immutable
data class GraphicsSettingsUiState(
    val blurNavBar: Boolean = true,
    val blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    val forceHighRefreshRate: Boolean = false,
    val isHighRefreshRateSupported: Boolean = false,
    val supportedHighestFps: Float = 60f,
    val disablePlayerCoverAnimations: Boolean = false,
    val disableAnimations: Boolean = false,
)

@Immutable
data class GraphicsSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onBlurNavBarChange: (Boolean) -> Unit = {},
    val onBlurRadiusChange: (Float) -> Unit = {},
    val onForceHighRefreshRateChange: (Boolean) -> Unit = {},
    val onDisablePlayerCoverAnimationsChange: (Boolean) -> Unit = {},
    val onDisableAnimationsChange: (Boolean) -> Unit = {},
)
