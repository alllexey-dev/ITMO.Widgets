package dev.alllexey.itmowidgets.app.shell

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneState
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** A pushed full-screen screen's metadata: its scene previews the Back under the finger ([backPreview]). */
object BackPreviewKey : NavMetadataKey<Boolean>

fun backPreview(): Map<String, Any> = metadata { put(BackPreviewKey, true) }

/**
 * The shell's Back, Material's back preview (docs/design.md "Tab switching and Back"). Only a scene whose top entry
 * has [backPreview] previews it: while the finger is down the top screen follows it ([backFrame]) and the screen
 * under it is composed and still; on release the top screen dissolves for [DISSOLVE_MILLIS] from where the finger left
 * it; a cancel follows the system's progress back to rest. Any other scene (a tab root) ignores the gesture until it
 * commits, then pops with its usual transition; the start tab's root has no Back, so the system goes home.
 *
 * shortcut: Nav3 1.2's `NavDisplay` plays a predictive pop's release as the rest of the gesture's seek timeline, (1 -
 * progress) of it, so a long drag or a fling would end in no time. The shell takes the system gesture itself and feeds
 * `NavDisplay` ([displayState]) a gesture held at 0 through a private dispatcher, so the release always gets the whole
 * timeline; drop the split for Nav3 1.3's gesture/release handoff (`PredictivePopGesture`) once 1.3 is stable.
 */
@Stable
internal class ShellBack {
    /** The gesture while the finger is down over a previewing scene: the first and the latest event. */
    class Drag(val first: NavigationEvent, val latest: NavigationEvent)

    /** A committed gesture, until the scene it popped to ([settleKey]) changes again. */
    class Release(val drag: Drag, val settleKey: Any?)

    var drag by mutableStateOf<Drag?>(null)
        private set

    var release by mutableStateOf<Release?>(null)
        private set

    internal fun start(event: NavigationEvent) {
        release = null
        drag = Drag(event, event)
    }

    internal fun progress(event: NavigationEvent) {
        drag = drag?.let { Drag(it.first, event) }
    }

    internal fun cancel() {
        drag = null
    }

    internal fun commit(settleKey: Any?) {
        release = drag?.let { Release(it, settleKey) }
        drag = null
    }

    internal fun onScene(key: Any) {
        if (release?.settleKey != key) release = null
    }

    companion object {
        /** The release's dissolve: MDC's `hideDurationMax` (`motionDurationMedium2`), whatever the progress. */
        const val DISSOLVE_MILLIS = 300
    }
}

internal val LocalShellBack = staticCompositionLocalOf<ShellBack?> { null }

/** The Back of [sceneState] and the gesture state to hand `NavDisplay` ([ShellBack]'s shortcut). */
internal class ShellBackState(val back: ShellBack, val displayState: NavigationEventState<SceneInfo<AppRoute>>)

@Composable
internal fun rememberShellBack(sceneState: SceneState<AppRoute>, onBack: () -> Unit): ShellBackState {
    val back = remember { ShellBack() }
    val display = rememberNavigationEventDispatcherOwner(parent = null)
    val input = remember(display) { DirectNavigationEventInput() }
    DisposableEffect(display, input) {
        display.navigationEventDispatcher.addInput(input)
        onDispose { display.navigationEventDispatcher.removeInput(input) }
    }
    val displayState = rememberNavigationEventState(sceneState)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides display) {
        NavigationBackHandler(sceneState, displayState, onBackCompleted = onBack)
    }

    val system = checkNotNull(LocalNavigationEventDispatcherOwner.current) { "no NavigationEventDispatcher" }
    val handler = remember(back, input) { SystemBackHandler(back, input) }
    val scene = sceneState.currentScene
    SideEffect {
        handler.isBackEnabled = scene.previousEntries.isNotEmpty()
        handler.previews = scene.previewsBack
        handler.settleKey = sceneState.previousScenes.lastOrNull()?.key
    }
    DisposableEffect(system, handler) {
        system.navigationEventDispatcher.addHandler(handler)
        onDispose { handler.remove() }
    }
    LaunchedEffect(scene.key) { back.onScene(scene.key) }
    return remember(back, displayState) { ShellBackState(back, displayState) }
}

private val Scene<AppRoute>.previewsBack: Boolean
    get() = this !is OverlayScene && entries.lastOrNull()?.metadata?.get(BackPreviewKey) == true

/**
 * The system's Back on the activity, where `NavDisplay`'s own handler used to be, so the entries' handlers composed
 * inside it still come first. A previewing gesture reaches `NavDisplay` held at 0; every commit pops through it.
 */
private class SystemBackHandler(
    private val back: ShellBack,
    private val display: DirectNavigationEventInput,
) : NavigationEventHandler<NavigationEventInfo.None>(
    NavigationEventInfo.None,
    isBackEnabled = false,
    isForwardEnabled = false,
) {
    var previews = false
    var settleKey: Any? = null
    private var previewing = false

    override fun onBackStarted(event: NavigationEvent) {
        previewing = previews
        if (!previewing) return
        back.start(event)
        display.backStarted(NavigationEvent(swipeEdge = event.swipeEdge))
    }

    override fun onBackProgressed(event: NavigationEvent) {
        if (previewing) back.progress(event)
    }

    override fun onBackCancelled() {
        if (!previewing) return
        previewing = false
        back.cancel()
        display.backCancelled()
    }

    override fun onBackCompleted() {
        if (previewing) back.commit(settleKey)
        previewing = false
        display.backCompleted()
    }
}

/** Material's predictive back curve: material3 `PredictiveBack.transform`, AOSP `Interpolators.BACK_GESTURE`. */
private val BackGesture = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/** M3 standard easing, for the dissolve's last shrink. */
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The top screen's Back preview, on a pushed screen's layer: drawn from the gesture and, after a commit, from the pop's
 * timeline, which runs linearly over [ShellBack.DISSOLVE_MILLIS] because `NavDisplay` was held at 0.
 */
@Composable
internal fun Modifier.backPreview(): Modifier {
    val back = LocalShellBack.current ?: return this
    val transition = LocalNavAnimatedContentScope.current.transition
    val leaving by transition.animateFloat(
        transitionSpec = { tween(ShellBack.DISSOLVE_MILLIS, easing = LinearEasing) },
        label = "backDissolve",
    ) { state -> if (state == EnterExitState.PostExit) 1f else 0f }
    return graphicsLayer {
        val top = transition.targetState == EnterExitState.PostExit
        val drag = back.drag
        val release = back.release
        val frame = when {
            !top -> null
            drag != null -> backFrame(drag, size.width, size.height, this)
            release != null -> backFrame(release.drag, size.width, size.height, this).dissolve(leaving)
            else -> null
        } ?: return@graphicsLayer
        scaleX = frame.scale
        scaleY = frame.scale
        translationX = frame.translationX
        translationY = frame.translationY
        alpha = frame.alpha
        shape = RoundedCornerShape(frame.corner)
        clip = true
    }
}

internal data class BackFrame(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val corner: Float,
    val alpha: Float = 1f,
) {
    /** The release at [fraction] of the dissolve: transparent and a little smaller, from where the finger left it. */
    fun dissolve(fraction: Float): BackFrame {
        val shrink = Standard.transform(fraction)
        return copy(scale = lerp(scale, scale * DISSOLVE_SCALE, shrink), alpha = 1f - fraction)
    }
}

/**
 * material3 SearchBar and MDC `MaterialMainContainerBackHelper`: the eased progress shrinks the screen to 90 %, moves
 * it towards the far side until 8 dp from the edge and rounds its corners to 28 dp; it follows the finger by up to
 * 24 dp vertically. A button Back (no edge) shrinks it in place.
 */
internal fun backFrame(drag: ShellBack.Drag, width: Float, height: Float, density: Density): BackFrame = with(density) {
    val progress = BackGesture.transform(drag.latest.progress.coerceIn(0f, 1f))
    val scale = lerp(1f, MIN_SCALE, progress)
    val gap = EDGE_GAP.toPx()
    val towards = when (drag.latest.swipeEdge) {
        NavigationEvent.EDGE_LEFT -> 1f
        NavigationEvent.EDGE_RIGHT -> -1f
        else -> 0f
    }
    val dy = drag.latest.touchY - drag.first.touchY
    val maxY = min(MAX_FOLLOW_Y.toPx(), max(0f, (height - scale * height) / 2f - gap))
    BackFrame(
        scale = scale,
        translationX = towards * progress * max(0f, (width - MIN_SCALE * width) / 2f - gap),
        translationY = if (height > 0f) sign(dy) * maxY * min(1f, abs(dy) / height) else 0f,
        corner = progress * MAX_CORNER.toPx(),
    )
}

private const val MIN_SCALE = 0.9f
private const val DISSOLVE_SCALE = 0.95f
private val EDGE_GAP = 8.dp
private val MAX_FOLLOW_Y = 24.dp
private val MAX_CORNER = 28.dp
