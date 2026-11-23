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

    val brush = remember { Animatable(0f) }
    val seal = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }

    LaunchedEffect(isReady, animationsDisabled) {
        if (!isReady) return@LaunchedEffect
        currentOnPrewarmStart()
        if (animationsDisabled) {
            currentOnBurstStart(); currentOnDismiss(); return@LaunchedEffect
        }
        brush.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        launch { words.animateTo(1f, tween(500)) }
        seal.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        delay(450)
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
                Canvas(Modifier.size(140.dp)) {
                    val stroke = size.minDimension * 0.06f
                    val inset = stroke
                    // the brush starts upper-right and travels round, leaving a gap like a real ensō
                    drawArc(
                        color = Paper,
                        startAngle = -50f,
                        sweepAngle = 300f * brush.value,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(width = stroke * (1f - 0.35f * brush.value), cap = StrokeCap.Round),
                    )
                    val s = size.minDimension * 0.15f
                    val scale = 1.6f - 0.6f * seal.value
                    val side = s * scale
                    drawRoundRect(
                        color = Seal.copy(alpha = seal.value),
                        topLeft = Offset(size.width * 0.62f - (side - s) / 2, size.height * 0.62f - (side - s) / 2),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(side * 0.12f),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Column(
                    Modifier.graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 12f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("spry", color = Paper, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text("スプライ", color = Paper.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 4.sp)
                }
            }
        }
    }
}
