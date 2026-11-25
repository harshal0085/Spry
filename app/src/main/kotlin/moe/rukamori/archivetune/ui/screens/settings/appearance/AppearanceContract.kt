/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode

internal object AppearanceContract {
    const val HIGH_REFRESH_RATE_THRESHOLD_FPS = 60.5f
    const val DEFAULT_STANDARD_REFRESH_RATE_FPS = 60f
    const val DEFAULT_REFRESH_RATE_REQUEST = 0f


    const val PALETTE_PICKER_ROUTE = "settings/appearance/palette_picker"
    const val CANVAS_ROUTE = "settings/appearance/canvas"
    const val GRAPHICS_ROUTE = "settings/graphics"
}

enum class NavigationTab {
    HOME,
    FRIENDS,
    SEARCH,
    LIBRARY,
}

@Immutable
data class AppearanceSettingsUiState(
    val dynamicTheme: Boolean = true,
    val randomThemeOnStartup: Boolean = false,
    val splashOverlayEnabled: Boolean = true,
    val fontPreference: AppFontPreference = AppFontPreference.DEFAULT,
    val customFontUri: String = "",
    val customFontName: String = "",
    val quickPicksDisplayMode: QuickPicksDisplayMode = QuickPicksDisplayMode.HERO,
    val defaultOpenTab: NavigationTab = NavigationTab.HOME,
    val defaultChip: LibraryFilter = LibraryFilter.LIBRARY,
    val showHomeCategoryChips: Boolean = true,
    val showTagsInLibrary: Boolean = true,
    val swipeToSong: Boolean = false,
)

typealias AppearanceUiState = AppearanceSettingsUiState

@Immutable
data class AppearanceSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onNavigatePalettePicker: () -> Unit = {},
    val onNavigateGraphics: () -> Unit = {},
    val onNavigateCanvas: () -> Unit = {},
    val onDynamicThemeChange: (Boolean) -> Unit = {},
    val onRandomThemeOnStartupChange: (Boolean) -> Unit = {},
    val onSplashOverlayEnabledChange: (Boolean) -> Unit = {},
    val onFontPreferenceChange: (AppFontPreference) -> Unit = {},
    val onPickCustomFont: () -> Unit = {},
    val onQuickPicksDisplayModeChange: (QuickPicksDisplayMode) -> Unit = {},
    val onDefaultOpenTabChange: (NavigationTab) -> Unit = {},
    val onDefaultChipChange: (LibraryFilter) -> Unit = {},
    val onShowHomeCategoryChipsChange: (Boolean) -> Unit = {},
    val onShowTagsInLibraryChange: (Boolean) -> Unit = {},
    val onSwipeToSongChange: (Boolean) -> Unit = {},
)

typealias AppearanceUiActions = AppearanceSettingsUiActions
