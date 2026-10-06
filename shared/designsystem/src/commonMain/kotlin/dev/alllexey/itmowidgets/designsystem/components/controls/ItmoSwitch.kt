package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.rememberItmoHaptics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion

/**
 * An on/off switch. Under Material it is material3's `Switch` as the settings rows draw it today; under the iOS style
 * it is `UISwitch`: a capsule track, `systemGreen` when on, with a white capsule thumb that slides across and a light
 * impact when the user flips it. With [onCheckedChange] null the switch only shows [checked] and the caller's row
 * carries the toggle (and its semantics), as `SettingsToggleRow` does.
 */
@Composable
fun ItmoSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> Switch(checked, onCheckedChange, modifier, enabled = enabled)
        ItmoPlatformStyle.Ios -> IosSwitch(checked, onCheckedChange, modifier, enabled)
    }
}

@Composable
private fun IosSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean) {
    val haptics = rememberItmoHaptics()
    val motion = ItmoTheme.motion
    val colors = ItmoTheme.iosColors
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (rememberReducedMotion()) snap() else tween(motion.standardMillis, easing = motion.easing),
        label = "thumb",
    )
    val toggle = if (onCheckedChange == null) {
        Modifier
    } else {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(checked, enabled = enabled, role = Role.Switch, interactionSource = null, indication = null) {
                haptics.perform(ItmoHapticEvent.Toggle)
                onCheckedChange(it)
            }
    }
    val travel = IosMetrics.switchWidth - IosMetrics.switchThumbWidth - IosMetrics.switchThumbInset * 2
    Box(
        modifier
            .then(toggle)
            .size(IosMetrics.switchWidth, IosMetrics.switchHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(lerp(colors.tertiaryLabel, colors.systemGreen, position), CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = IosMetrics.switchThumbInset + travel * position)
                .size(IosMetrics.switchThumbWidth, IosMetrics.switchThumbHeight)
                .shadow(ThumbElevation, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** A disabled control's dimming, as UIKit draws a switch with `isEnabled` off. */
private const val DISABLED_ALPHA = 0.5f

/** The soft shadow under the thumb that keeps it apart from a white cell. */
private val ThumbElevation = 1.dp
