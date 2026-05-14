package com.proj.Musicality.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proj.Musicality.data.network.NetworkStatus
import com.proj.Musicality.data.network.NetworkStatusMonitor
import kotlin.math.abs
import kotlinx.coroutines.delay

private const val TOAST_DURATION_MS = 2500L

private enum class ToastKind(val message: String, val icon: ImageVector) {
    SlowNet("It's not your internet's day...", Icons.Rounded.Wifi),
    NoNetwork("Radio silence from your internet provider....", Icons.Rounded.WifiOff),
    Recovered("We're so back...", Icons.Rounded.Wifi)
}

@Composable
fun NetworkStatusToast(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val monitor = remember(context.applicationContext) {
        NetworkStatusMonitor.getInstance(context.applicationContext)
    }
    val status by monitor.status.collectAsStateWithLifecycle()
    val activeNow by rememberUpdatedState(isActive)

    // Track the prior status across emissions so we can classify transitions even while the
    // toast is gated off.
    val previousRef = remember { mutableStateOf<NetworkStatus>(NetworkStatus.Unknown) }
    var visibleToast by remember { mutableStateOf<ToastKind?>(null) }

    LaunchedEffect(status) {
        val prev = previousRef.value
        previousRef.value = status
        if (!activeNow) return@LaunchedEffect
        val kind = classifyTransition(prev, status) ?: return@LaunchedEffect
        visibleToast = kind
        delay(TOAST_DURATION_MS)
        if (visibleToast == kind) visibleToast = null
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedContent(
            targetState = visibleToast,
            transitionSpec = {
                val enter = slideInVertically(
                    initialOffsetY = { -it - 24 },
                    animationSpec = tween(durationMillis = 340)
                ) + fadeIn(animationSpec = tween(durationMillis = 300))
                val exit = slideOutVertically(
                    targetOffsetY = { -it - 24 },
                    animationSpec = tween(durationMillis = 280)
                ) + fadeOut(animationSpec = tween(durationMillis = 220))
                enter togetherWith exit using SizeTransform(clip = false)
            },
            label = "networkStatusToast"
        ) { kind ->
            if (kind != null) {
                ShimmerPill(message = kind.message, icon = kind.icon)
            }
        }
    }
}

private fun classifyTransition(prev: NetworkStatus, curr: NetworkStatus): ToastKind? {
    if (prev == curr) return null
    return when (curr) {
        // "Things are broken" toasts are useful on cold start too — when the app launches with
        // no connection the user should know right away. Only the "everything's fine" toast is
        // suppressed from Unknown so it doesn't pop on every healthy launch.
        is NetworkStatus.None -> ToastKind.NoNetwork
        is NetworkStatus.Slow -> ToastKind.SlowNet
        is NetworkStatus.Normal -> when (prev) {
            is NetworkStatus.Slow, is NetworkStatus.None -> ToastKind.Recovered
            else -> null
        }
        is NetworkStatus.Unknown -> null
    }
}

@Composable
private fun ShimmerPill(message: String, icon: ImageVector) {
    // Reuses the Crossfade-cue shimmer from PlayerSheet — same easing, same duration, widened
    // sweep so it spans the longer pill text.
    val baseColor = Color(0xFFB9BDC2).copy(alpha = 0.78f)
    val shimmerTransition = rememberInfiniteTransition(label = "networkToastShimmer")
    val shimmerProgress by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "networkToastShimmerProgress"
    )
    val shimmerBrush = remember(shimmerProgress, baseColor) {
        val startX = -200f + (760f * shimmerProgress)
        val endX = startX + 200f
        Brush.linearGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.48f),
                Color.White.copy(alpha = 0.95f),
                baseColor.copy(alpha = 0.48f)
            ),
            start = Offset(startX, 0f),
            end = Offset(endX, 18f)
        )
    }
    // Triangle wave 0→1→0 across the shimmer cycle so the icon brightens as the sweep passes.
    val iconShimmerT = (1f - abs(shimmerProgress * 2f - 1f)).coerceIn(0f, 1f)
    val iconTint = lerp(baseColor, Color.White, iconShimmerT * 0.35f)

    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 32.dp, start = 24.dp, end = 24.dp)
            .background(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(percent = 50)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge.copy(brush = shimmerBrush)
            )
        }
    }
}
