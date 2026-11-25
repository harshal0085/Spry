@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import moe.rukamori.archivetune.ui.theme.LocalSongColor
import moe.rukamori.archivetune.ui.theme.spryGlass
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.constants.NavigationBarCornerRadius
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarHideMinScale
import moe.rukamori.archivetune.constants.NavigationBarHideOffsetY
import moe.rukamori.archivetune.constants.NavigationBarInnerPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationBarOverflowPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationBarVisibilityDurationMs
import moe.rukamori.archivetune.ui.screens.Screens
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassStroke

internal object NavBarColors {
    @Composable
    // Spry: dark liquid glass (smoky near-black) instead of a light surface.
    fun container(pureBlack: Boolean) = Color(0xFF0E0D12)

    @Composable
    fun iconActive(pureBlack: Boolean) = Color.White

    @Composable
    fun iconInactive(pureBlack: Boolean) = Color.White.copy(alpha = 0.55f)
}

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    capsuleShape: Shape = RoundedCornerShape(NavigationBarCornerRadius),
    hazeState: HazeState? = null,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    showBorder: Boolean = true,
    blurEnabled: Boolean = true,
    blurNavBar: Boolean = true,
    visibilityFactor: Float = 1f,
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    onMusicTogetherClick: (() -> Unit)? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val hasOverflow = false
    val containerColor = NavBarColors.container(pureBlack)

    val fixedTintAlpha = 0.55f
    val hazeStyle = remember(containerColor, blurRadius, pureBlack, blurEnabled) {
        val tintEffect = HazeColorEffect.tint(containerColor.copy(alpha = fixedTintAlpha))
        HazeBlurStyle {
            blurEnabled(blurEnabled)
            backgroundColor(containerColor)
            colorEffects(listOf(tintEffect))
            fallbackColorEffect(tintEffect)
            blurRadius(blurRadius.dp)
            noiseFactor(SettingsDimensions.HazeNoiseFactor)
        }
    }

    val shadowPaint = rememberNavigationShadowPaint()

    val animatedVisibilityFactor by animateFloatAsState(
        targetValue = visibilityFactor.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = NavigationBarVisibilityDurationMs,
            easing = EaseOut,
        ),
        label = "FloatingToolbarVisibility",
    )

    val gestureState = rememberNavigationBarGestureState(
        items = items,
        isSelected = isSelected,
        onItemClick = onItemClick,
    )

    val selectedIndex = items.indexOfFirst { isSelected(it) }

    androidx.compose.runtime.LaunchedEffect(
        selectedIndex,
        gestureState.selectorState.rowWidthPx,
        gestureState.isDragging,
    ) {
        if (
            selectedIndex >= 0 &&
            !gestureState.isDragging &&
            gestureState.selectorState.tabWidthPx > 0f
        ) {
            gestureState.selectorState.syncToTab(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val hideOffsetY = NavigationBarHideOffsetY.toPx() * (1f - animatedVisibilityFactor)
                translationY = hideOffsetY + gestureState.barTranslationYAnimatable.value
                translationX = gestureState.barTranslationXAnimatable.value

                val baseScale = lerp(NavigationBarHideMinScale, 1.0f, animatedVisibilityFactor)
                val scale = baseScale * gestureState.barScaleAnimatable.value
                scaleX = scale
                scaleY = scale
                alpha = animatedVisibilityFactor
            }
            .fillMaxWidth()
            .height(NavigationBarHeight)
            .navigationShapeShadow(capsuleShape, shadowPaint)
            .clip(capsuleShape)
            .then(
                if (hazeState != null && blurNavBar && blurEnabled) {
                    Modifier.hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style = hazeStyle,
                        performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                    )
                } else {
                    Modifier.background(containerColor)
                },
            )
            // Spry: song-tinted glass rim + glints over the blur (glass fill is transparent so the blur shows).
            .spryGlass(shape = capsuleShape, tint = LocalSongColor.current, fillAlpha = 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NavigationBarInnerPaddingHorizontal),
            contentAlignment = Alignment.CenterStart,
        ) {
            NavigationDragSelectorOverlay(gestureState.selectorState)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationTabDragGestures(gestureState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { screen ->
                    val selected = isSelected(screen)
                    NavigationTabItem(
                        modifier = if (screen == Screens.Library) {
                            // Spry: Naez stands above the Library tab when there's no mini player to stand on
                            Modifier.onGloballyPositioned { NavAnchors.library = it.boundsInRoot() }
                        } else {
                            Modifier
                        },
                        screen = screen,
                        selected = selected,
                        pureBlack = pureBlack,
                        drawSelector = false,
                        onClick = {
                            gestureState.selectorState.onTabTap(screen, selected)
                        },
                        onDoubleClick = remember(screen, onSearchItemDoubleClick) {
                            if (screen == Screens.Search) onSearchItemDoubleClick else null
                        },
                    )
                }

                if (hasOverflow) {
                    Box(
                        modifier = Modifier
                            .padding(
                                start = NavigationBarOverflowPaddingHorizontal,
                                end = NavigationBarOverflowPaddingHorizontal,
                            )
                            .wrapContentSize(),
                    ) {
                        ToolbarOverflowMenu(
                            pureBlack = pureBlack,
                            onShuffleClick = onShuffleClick,
                            shuffleIconRes = shuffleIconRes,
                            shuffleContentDescription = shuffleContentDescription,
                            onMusicRecognitionClick = onMusicRecognitionClick,
                            musicRecognitionContentDescription = musicRecognitionContentDescription,
                            onMusicTogetherClick = onMusicTogetherClick,
                        )
                    }
                }
            }
        }
    }
}

/** Where nav tabs sit on screen (root pixels), for things that stand on the bar, like Naez. */
object NavAnchors {
    var library by mutableStateOf<Rect?>(null)
}
