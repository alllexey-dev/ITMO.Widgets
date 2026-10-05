package dev.alllexey.itmowidgets.designsystem.tokens

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/** The same check as `SkeletonView` and the QR preview: animators are off when the duration scale is 0. */
@Composable
actual fun rememberReducedMotion(): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration) { !ValueAnimator.areAnimatorsEnabled() }
}
