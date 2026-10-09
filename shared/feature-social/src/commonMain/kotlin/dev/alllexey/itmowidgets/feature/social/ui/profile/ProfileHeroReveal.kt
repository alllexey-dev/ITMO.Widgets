package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import kotlinx.coroutines.launch

/** Scale and alpha of the profile hero's avatar reveal, at their end values when nothing animates. */
internal class ProfileHeroReveal(
    val scale: Animatable<Float, AnimationVector1D>,
    val alpha: Animatable<Float, AnimationVector1D>,
)

/**
 * The reveal of the person [isu] in the hero, held by the screen rather than the lazy item, so scrolling the hero away
 * and back never plays it again. A person who arrives while the screen is open (after the skeleton or a retry) grows
 * and fades in on [ItmoTheme.heroMotionScheme]: scale on its spatial spring (it may overshoot), alpha on its effects
 * spring. A person already there on the first frame, a refresh of the same person and everything under reduced motion
 * show at once.
 */
@Composable
internal fun rememberProfileHeroReveal(isu: Int?): ProfileHeroReveal {
    val firstFrame = remember { FirstFrame() }
    SideEffect { firstFrame.passed = true }
    val reducedMotion = rememberReducedMotion()
    val run = isu != null && firstFrame.passed && !reducedMotion
    val scheme = ItmoTheme.heroMotionScheme
    val reveal = remember(isu) {
        ProfileHeroReveal(Animatable(if (run) REVEAL_START_SCALE else 1f), Animatable(if (run) 0f else 1f))
    }
    LaunchedEffect(reveal) {
        if (!run) return@LaunchedEffect
        launch { reveal.scale.animateTo(1f, scheme.defaultSpatialSpec()) }
        launch { reveal.alpha.animateTo(1f, scheme.defaultEffectsSpec()) }
    }
    return reveal
}

/** Applies [reveal] in a layer read in the draw phase; the avatar's layout size and semantics stay as they are. */
internal fun Modifier.profileHeroReveal(reveal: ProfileHeroReveal): Modifier = graphicsLayer {
    scaleX = reveal.scale.value
    scaleY = reveal.scale.value
    alpha = reveal.alpha.value.coerceIn(0f, 1f)
}

/** False until the screen's first composition is applied; a person composed before that needs no reveal. */
private class FirstFrame {
    var passed = false
}

/** Where a revealed avatar starts growing from, as a fraction of its size. */
private const val REVEAL_START_SCALE = 0.8f
