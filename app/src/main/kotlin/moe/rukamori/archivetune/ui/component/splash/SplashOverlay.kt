package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.imageResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.SplashOverlayEnabledKey
import moe.rukamori.archivetune.utils.rememberPreference

private val Ink = Color(0xFF0B0A10)
private val Paper = Color(0xFFF4F1F8)
private val Seal = Color(0xFFD7263D)

/** Spry splash: an ensō brushes itself in, a red hanko stamps, then everything fades into the app. */
@Composable
fun SplashOverlay(
    modifier: Modifier = Modifier,
    isReady: Boolean = true,
    isDark: Boolean = true,
    contentColor: Color? = null,
    primaryColor: Color? = null,
    onBurstStart: () -> Unit = {},
    onPrewarmStart: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val splashOverlayEnabled by rememberPreference(SplashOverlayEnabledKey, defaultValue = true)
    if (!splashOverlayEnabled) return
    val animationsDisabled = LocalAnimationsDisabled.current
    var showSplash by remember { mutableStateOf(!animationsDisabled) }
    val currentOnBurstStart by rememberUpdatedState(onBurstStart)
    val currentOnPrewarmStart by rememberUpdatedState(onPrewarmStart)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val logo = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    val naez = remember { Animatable(0f) }

    LaunchedEffect(isReady, animationsDisabled) {
        if (!isReady) return@LaunchedEffect
        currentOnPrewarmStart()
        if (animationsDisabled) {
            currentOnBurstStart(); currentOnDismiss(); return@LaunchedEffect
        }
        // Spry: ~1s. The Naez icon settles in, the name follows, Naez pops up from the bottom waving.
        launch { delay(180); words.animateTo(1f, tween(300)) }
        launch { delay(220); naez.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 420f)) }
        logo.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        delay(520)
        currentOnBurstStart()
        showSplash = false
        delay(SplashConfig.Reveal.FADE_MS.toLong())
        currentOnDismiss()
    }

    AnimatedVisibility(
        visible = showSplash,
        enter = EnterTransition.None,
        exit = fadeOut(tween(SplashConfig.Reveal.FADE_MS.toInt())),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Ink)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // the launcher icon itself (Naez's face on dark), so the system launch screen flows straight into this
                androidx.compose.foundation.Image(
                    bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(moe.rukamori.archivetune.R.mipmap.spry_icon_face),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier
                        .size(124.dp)
                        .graphicsLayer {
                            alpha = logo.value
                            val s = 0.86f + 0.14f * logo.value
                            scaleX = s; scaleY = s
                            // show the adaptive-icon visible zone (middle 2/3), like on the home screen
                            clip = true
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
                        }
                        .background(Ink)
                        .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    "spry",
                    color = Paper,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 12f },
                )
            }
            // Naez pops up from the bottom edge, waving
            androidx.compose.foundation.Image(
                bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(moe.rukamori.archivetune.R.drawable.naez_05),
                contentDescription = null,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = 150.dp, height = 179.dp)
                    .graphicsLayer {
                        translationY = (1f - naez.value) * size.height
                        alpha = naez.value.coerceIn(0f, 1f)
                    },
            )
        }
    }
}
