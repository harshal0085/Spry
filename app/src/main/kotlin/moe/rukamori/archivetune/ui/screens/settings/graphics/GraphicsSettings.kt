/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.graphics

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisablePlayerCoverAnimationsKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.settings.BlurRadiusSliderItem
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.screens.settings.rememberSupportedHighestFps
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun GraphicsSettings(navController: NavController) {
    val context = LocalContext.current
    val defaultDisableAnimations = remember(context) { context.isLowRamDevice() }

    val (blurNavBar, onBlurNavBarChange) = rememberPreference(BlurNavBarKey, defaultValue = true)
    val (blurRadius, onBlurRadiusChange) = rememberPreference(BlurRadiusKey, defaultValue = SettingsDimensions.BlurRadiusDefault)
    val (forceHighRefreshRate, onForceHighRefreshRateChange) = rememberPreference(ForceHighRefreshRateKey, defaultValue = false)
    val (disablePlayerCoverAnimations, onDisablePlayerCoverAnimationsChange) = rememberPreference(DisablePlayerCoverAnimationsKey, defaultValue = false)
    val (disableAnimations, onDisableAnimationsChange) = rememberPreference(DisableAnimationsKey, defaultValue = defaultDisableAnimations)

    val supportedHighestFps = rememberSupportedHighestFps()
    val isHighRefreshRateSupported = supportedHighestFps > GraphicsContract.HIGH_REFRESH_RATE_THRESHOLD_FPS
    // The refresh rate itself is applied app-wide in YumaApp.

    val state = GraphicsSettingsUiState(
        blurNavBar = blurNavBar,
        blurRadius = blurRadius,
        forceHighRefreshRate = forceHighRefreshRate,
        isHighRefreshRateSupported = isHighRefreshRateSupported,
        supportedHighestFps = supportedHighestFps,
        disablePlayerCoverAnimations = disablePlayerCoverAnimations,
        disableAnimations = disableAnimations,
    )

    val actions = remember(navController) {
        GraphicsSettingsUiActions(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = navController::backToMain,
            onBlurNavBarChange = onBlurNavBarChange,
            onBlurRadiusChange = onBlurRadiusChange,
            onForceHighRefreshRateChange = onForceHighRefreshRateChange,
            onDisablePlayerCoverAnimationsChange = onDisablePlayerCoverAnimationsChange,
            onDisableAnimationsChange = onDisableAnimationsChange,
        )
    }

    GraphicsSettingsScreen(state = state, actions = actions)
}

@Composable
internal fun GraphicsSettingsScreen(
    state: GraphicsSettingsUiState,
    actions: GraphicsSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.settings_graphics_title)) },
        onBackClick = actions.onNavigateUp,
        onBackLongClick = actions.onNavigateHome,
        modifier = modifier,
    ) {
        PreferenceGroup(title = stringResource(R.string.settings_graphics_blur_group)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.blur_nav_bar)) },
                    description = stringResource(R.string.blur_nav_bar_desc),
                    icon = { Icon(painterResource(R.drawable.blur_on), null, modifier = Modifier.size(24.dp)) },
                    checked = state.blurNavBar,
                    onCheckedChange = actions.onBlurNavBarChange,
                )
            }
            item(visible = state.blurNavBar) {
                BlurRadiusSliderItem(
                    value = state.blurRadius,
                    onValueChangeFinished = actions.onBlurRadiusChange,
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.settings_graphics_performance_group)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.force_high_refresh_rate)) },
                    description = stringResource(R.string.max_supported_refresh_rate, state.supportedHighestFps.roundToInt()),
                    icon = { Icon(painterResource(R.drawable.speed), null, modifier = Modifier.size(24.dp)) },
                    checked = state.forceHighRefreshRate,
                    onCheckedChange = actions.onForceHighRefreshRateChange,
                    isEnabled = state.isHighRefreshRateSupported,
                )
            }
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.disable_animations)) },
                    description = stringResource(R.string.disable_animations_desc),
                    icon = { Icon(painterResource(R.drawable.animation), null, modifier = Modifier.size(24.dp)) },
                    checked = state.disableAnimations,
                    onCheckedChange = actions.onDisableAnimationsChange,
                )
            }
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.disable_player_cover_animations)) },
                    description = stringResource(R.string.disable_player_cover_animations_desc),
                    icon = { Icon(painterResource(R.drawable.animation), null, modifier = Modifier.size(24.dp)) },
                    checked = state.disablePlayerCoverAnimations,
                    onCheckedChange = actions.onDisablePlayerCoverAnimationsChange,
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun GraphicsSettingsPreview() {
    TestThemeWrapper {
        GraphicsSettings(navController = rememberNavController())
    }
}
