package com.devsecops.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Spacing tokens + calm motion helpers shared by every screen.

object Sp {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

const val MOTION_FAST = 150
const val MOTION_MED = 250

@Composable
fun rememberHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

fun Modifier.clickableScale(
    enabled: Boolean = true,
    haptic: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(MOTION_FAST),
        label = "clickScale"
    )
    val buzz = rememberHaptic()
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                if (haptic) buzz()
                onClick()
            }
        )
}

fun Modifier.appear(index: Int = 0): Modifier = composed {
    // Remembered per item (LazyColumn saves state by key) so scrolling back
    // doesn't replay the entrance animation.
    var played by rememberSaveable(index) { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(minOf(index, 6) * 40L)
            progress.animateTo(1f, tween(MOTION_MED, easing = FastOutSlowInEasing))
            played = true
        }
    }
    this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 12.dp.toPx()
    }
}

@Composable
fun AnimatedCount(
    target: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "animatedCount"
    )
    Text(
        text = value.toString(),
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color
    )
}

@Composable
fun animatedProgress(target: Float): Float {
    val v by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "animatedProgress"
    )
    return v
}
