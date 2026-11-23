package moe.rukamori.archivetune.ui.scaffold

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import moe.rukamori.archivetune.extensions.metadata
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.valentinilk.shimmer.LocalShimmerTheme
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalMarqueeEnabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.home.effects.HomeBackgroundSettings
import moe.rukamori.archivetune.home.effects.LocalHomeBackgroundStyle
import moe.rukamori.archivetune.home.effects.ScreenBackground
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerTheme
import moe.rukamori.archivetune.ui.component.splash.SplashConfig
import moe.rukamori.archivetune.ui.component.splash.SplashOverlay
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalDownloadUtil
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.main.LocalSyncUtils
import moe.rukamori.archivetune.ui.player.host.PlayerOverlayHost
import moe.rukamori.archivetune.ui.system.SystemBarController
import moe.rukamori.archivetune.ui.system.rememberBarScrollVisibility
import moe.rukamori.archivetune.ui.utils.LocalGlobalVisibility
import moe.rukamori.archivetune.ui.utils.LocalPlayerIsExpanded
import moe.rukamori.archivetune.utils.SyncUtils

@Composable
fun ScaffoldShell(
    activity: ComponentActivity,
    navController: NavHostController,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
    playerConnection: PlayerConnection?,
    systemBarController: SystemBarController,
    playerViewModel: PlayerViewModel,
    bottomSheetPageState: BottomSheetPageState,
    menuState: MenuState,
    updateChannel: UpdateChannel,
    isHomeScreenVisible: Boolean,
    playerExpansionFraction: () -> Float = { 0f },
    onExpansionFraction: (() -> Float) -> Unit,
    disableAnimations: Boolean,
    marqueeEnabled: Boolean = true,
    splashEnabled: Boolean,
    useDarkTheme: Boolean,
    pureBlack: Boolean,
    homeBackgroundStyle: HomeBackgroundStyle,
    homeBackgroundParallaxEnabled: Boolean,
    homeBackgroundParallaxStrength: Float,
    homeBackgroundBrightness: Float,
    contentAlpha: () -> Float,
    contentMounted: Boolean,
    contentVisible: Boolean,
    onPrewarmStart: () -> Unit,
    onBurstStart: () -> Unit,
    splashDone: Boolean,
    onSplashDismiss: () -> Unit,
    pendingIntent: Intent?,
    onClearPendingIntent: () -> Unit,
    pendingBackupRestoreUri: Uri?,
    onClearPendingBackupRestoreUri: () -> Unit,
    aodModeLaunchRequestCount: Int,
    onResetAodLaunchRequestCount: () -> Unit,
    onHandleIntent: (Intent?, NavHostController) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface),
    ) {
        // Spry: the whole app sits on the playing song's dominant colour.
        val artUrl = playerConnection?.mediaMetadata?.collectAsState()?.value?.thumbnailUrl
        val songColor = moe.rukamori.archivetune.ui.component.rememberSongColorTarget(artUrl)
        // Work out the next song's colour while this one plays, so skipping recolours instantly.
        val colorContext = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(artUrl, playerConnection) {
            val player = playerConnection?.player ?: return@LaunchedEffect
            val next = player.nextMediaItemIndex.takeIf { it != androidx.media3.common.C.INDEX_UNSET } ?: return@LaunchedEffect
            val nextUrl = player.getMediaItemAt(next).metadata?.thumbnailUrl
            moe.rukamori.archivetune.ui.component.prefetchSongColor(colorContext, nextUrl)
        }
        val baseScheme = MaterialTheme.colorScheme
        val songScheme = remember(baseScheme, songColor) { moe.rukamori.archivetune.ui.theme.spryColorScheme(baseScheme, songColor) }
        // The page colour fades in the draw phase; the palette itself switches once per song (no per-frame recomposition).
        val pageColor = androidx.compose.animation.animateColorAsState(songScheme.background, androidx.compose.animation.core.tween(450), label = "PageColor")
        Box(Modifier.fillMaxSize().drawBehind { drawRect(pageColor.value) })
        val appState = rememberScaffoldAppState(
            activity = activity,
            navController = navController,
            playerViewModel = playerViewModel,
            updateChannel = updateChannel,
            maxHeight = maxHeight,
            pendingIntent = pendingIntent,
            onClearPendingIntent = onClearPendingIntent,
            onHandleIntent = onHandleIntent,
        )

        CompositionLocalProvider(
            moe.rukamori.archivetune.ui.theme.LocalSongColor provides songColor,            LocalYumaHaptics provides appState.yumaHaptics,
            LocalHapticFeedback provides appState.customHaptic,
            LocalAnimationsDisabled provides disableAnimations,
            // Spry: long text ends in "…" instead of scrolling (scrolling showed words cut mid-way); the player title still scrolls.
            LocalMarqueeEnabled provides false,
            LocalHomeBackgroundStyle provides HomeBackgroundSettings(homeBackgroundStyle, homeBackgroundParallaxEnabled, homeBackgroundParallaxStrength, homeBackgroundBrightness),
            LocalDatabase provides database,
            LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
            LocalPlayerConnection provides playerConnection,
            LocalPlayerAwareWindowInsets provides appState.playerAwareWindowInsets,
            LocalDownloadUtil provides downloadUtil,
            LocalShimmerTheme provides ShimmerTheme,
            LocalSyncUtils provides syncUtils,
            LocalBottomSheetPageState provides bottomSheetPageState,
            LocalMenuState provides menuState,
            LocalGlobalVisibility provides isHomeScreenVisible,
            LocalPlayerIsExpanded provides appState.playerBottomSheetState.isExpanded,
        ) {
          // Spry: every surface in the app carries the playing song's hue.
          MaterialTheme(colorScheme = songScheme) {
            ScreenBackground(
                isVisible = LocalGlobalVisibility.current && contentVisible,
                modifier = Modifier.fillMaxSize(),
            )
            if (splashEnabled) {
                SplashOverlay(
                    isDark = useDarkTheme,
                    onBurstStart = onBurstStart,
                    onPrewarmStart = onPrewarmStart,
                    onDismiss = onSplashDismiss,
                )
            }
            if (contentMounted || !splashEnabled) {
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            val alphaValue = contentAlpha()
                            alpha = alphaValue
                            translationY = (1f - alphaValue) * SplashConfig.Reveal.RISE_DP.dp.toPx()
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .pointerInput(contentVisible) {
                            if (!contentVisible) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                ) {
                    ScaffoldNavigationRailChrome(
                        appState = appState,
                        disableAnimations = disableAnimations,
                        pureBlack = pureBlack,
                    )

                    val scaffoldHazeState = rememberScaffoldHazeState(blurNavBar = appState.blurNavBar)
                    val barScrollVisibility = rememberBarScrollVisibility()

                    Box(
                        modifier = Modifier.fillMaxSize().nestedScroll(barScrollVisibility.nestedScrollConnection),
                    ) {
                        ScaffoldChrome(
                            appState = appState,
                            navController = navController,
                            scaffoldHazeState = scaffoldHazeState,
                            pureBlack = pureBlack,
                            splashDone = splashDone,
                            disableAnimations = disableAnimations,
                        ) {
                            CompositionLocalProvider(LocalScaffoldHazeState provides scaffoldHazeState.effectiveHazeState) {
                                ScaffoldBody(
                                    navController = navController,
                                    appState = appState,
                                    scaffoldHazeState = scaffoldHazeState,
                                    updateChannel = updateChannel,
                                    playerExpansionFraction = playerExpansionFraction,
                                )
                            }
                        }

                        PlayerOverlayHost(
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                            sheetState = appState.playerBottomSheetState,
                            shouldShowNav = appState.shouldShowNavigationBar,
                            isYearInMusic = appState.isYearInMusicScreen,
                            useRail = appState.useRail,
                            hazeState = scaffoldHazeState.effectiveHazeState,
                            blurRadius = appState.blurRadius,
                            pureBlack = pureBlack,
                            playerViewModel = playerViewModel,
                            blurNavBar = appState.blurNavBar,
                            homeViewModel = appState.homeViewModel,
                            systemBarController = systemBarController,
                            window = activity.window,
                            aodModeLaunchRequestCount = aodModeLaunchRequestCount,
                            onResetAodLaunchRequestCount = onResetAodLaunchRequestCount,
                            useDarkTheme = useDarkTheme,
                            shouldShowHomeShuffleButton = appState.shouldShowHomeShuffleButton,
                            navigationItems = appState.navigationItems,
                            navBackStackEntry = appState.navBackStackEntry,
                            handlePrimaryNavigationClick = appState.handlePrimaryNavigationClick,
                            onSearchItemDoubleClick = {
                                appState.searchState.onSearchSourceChange(SearchSource.ONLINE)
                                appState.searchState.openSearch()
                            },
                            disableAnimations = disableAnimations,
                            navVisibleHeight = appState.navVisibleHeight,
                            floatingToolbarBottomPadding = appState.floatingToolbarBottomPadding,
                            onExpansionFraction = onExpansionFraction,
                            scrollVisibilityFactor = barScrollVisibility.scrollVisibilityFactor,
                        )

                        GlobalDialogsHost(
                            navController = navController,
                            networkBannerState = appState.networkBannerState,
                            pendingBackupRestoreUri = pendingBackupRestoreUri,
                            onDismissBackupRestore = onClearPendingBackupRestoreUri,
                            splashDone = splashDone,
                            shouldShowTopBar = appState.shouldShowTopBar,
                            topInset = appState.topInset,
                            updateChannel = updateChannel,
                            coroutineScope = appState.coroutineScope,
                        )
                    }
                }
            }
          }
        }
    }
}
