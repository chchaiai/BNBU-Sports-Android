package edu.bnbu.student.mvp.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/** Shared motion rhythm for the app. Keep transitions short and physically damped. */
object BNBUMotion {
    const val Quick = 140
    const val Standard = 240
    const val Emphasized = 360

    val progressSpec = tween<Float>(
        durationMillis = 520,
        easing = FastOutSlowInEasing
    )

    val colorSpec = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = Standard,
        easing = FastOutSlowInEasing
    )
}

/** Adds a subtle scale response while preserving the Material ripple and semantics. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.975f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bnbuPressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Standard clickable treatment for custom cards and rows. */
@Composable
fun Modifier.bnbuClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    pressedScale: Float = 0.985f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    return pressScale(
        interactionSource = interactionSource,
        enabled = enabled,
        pressedScale = pressedScale
    ).clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}
