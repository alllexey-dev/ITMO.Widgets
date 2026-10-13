package dev.alllexey.itmowidgets.app.shell

import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.navigation.ItmoNavigationBar
import dev.alllexey.itmowidgets.designsystem.components.navigation.ItmoNavigationBarItem
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/** Test tags of the shell's chrome, for the shell's tests and probes. */
object ShellTags {
    const val TAB_LAYER = "shell:tabs"
    const val TAB_CONTENT = "shell:tab_content"
    const val TAB_PAGER = "shell:tab_pager"
    const val BAR = "shell:bar"
    const val DEMO_BANNER = "shell:demo_banner"
    const val OVERLAY_LAYER = "shell:overlays"
    const val PROGRESS = "shell:progress"

    fun tab(tab: AppTab): String = "shell:tab:${tab.name}"

    fun overlay(contentKey: Any): String = "shell:overlay:$contentKey"
}

/**
 * Debug-only hook for the store screenshots (`StoreScreenshotCapture`): hides the demo banner, so the frames show the
 * tabs as a signed-in session sees them. Read when the tabs compose; the surface still says `demoBanner`, and release
 * builds always show the banner.
 */
object StoreLook {
    @VisibleForTesting
    @Volatile
    var hideDemoBanner = false
}

/**
 * The layers under the sheets and dialogs: the tab roots in the [TabPager] above the demo banner and the bar, and the
 * overlay screens full window above them, sliding in from the end in the standard 220 ms.
 * While an overlay covers it, the tab layer keeps its size, takes no touches and is hidden from TalkBack. Back pops the
 * top overlay with the predictive gesture, then leads a tab other than the start one to the start tab; on the start
 * tab with nothing above it the system handles Back (leaves the app). The tabs also switch by a swipe on a tab root
 * while nothing is open above it ([TabPages.swipeEnabled]).
 */
@Composable
internal fun ShellLayers(
    tabRoots: (AppTab) -> NavEntry<AppRoute>,
    overlays: List<NavEntry<AppRoute>>,
    navigator: Nav3AppNavigator,
    demoBanner: Boolean,
    onDemoSignIn: () -> Unit,
) {
    val state = navigator.state
    val nothingFloats = state.floating.isEmpty()
    BackHandler(enabled = nothingFloats && overlays.isEmpty() && state.tab != AppTab.START) { navigator.back() }

    var backProgress by remember { mutableFloatStateOf(0f) }
    var backingKey by remember { mutableStateOf<Any?>(null) }
    val topKey = overlays.lastOrNull()?.contentKey
    PredictiveBackHandler(enabled = nothingFloats && overlays.isNotEmpty()) { progress ->
        backingKey = topKey
        try {
            progress.collect { backProgress = it.progress }
            navigator.back()
        } catch (cancelled: CancellationException) {
            backProgress = 0f
            backingKey = null
            throw cancelled
        }
    }
    // A committed gesture leaves the outgoing overlay where the finger let go and the pop slides it on from there;
    // the offset is dropped once that slide is over.
    val slideMillis = ItmoTheme.motion.standardMillis.toLong()
    LaunchedEffect(topKey) {
        if (backingKey != null && backingKey != topKey) {
            delay(slideMillis)
            backProgress = 0f
            backingKey = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        TabLayer(
            navigator = navigator,
            covered = overlays.isNotEmpty(),
            demoBanner = demoBanner,
            onDemoSignIn = onDemoSignIn,
        ) { tab -> tabRoots(tab).Content() }
        OverlayLayer(overlays) { key -> if (key == backingKey) backProgress else 0f }
    }
}

@Composable
private fun TabLayer(
    navigator: Nav3AppNavigator,
    covered: Boolean,
    demoBanner: Boolean,
    onDemoSignIn: () -> Unit,
    page: @Composable (AppTab) -> Unit,
) {
    val selected = navigator.tab
    val exploring = rememberTouchExploration()
    val swipeEnabled = TabPages.swipeEnabled(ShellSurface.Tabs(demoBanner), navigator.state, exploring)
    val pages = rememberTabPagerState(selected, exploring)
    val hidden = if (covered) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier
    Column(Modifier.fillMaxSize().testTag(ShellTags.TAB_LAYER).then(hidden)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The bar below pads itself by the navigation bar; the tab's content must not pad again.
                .consumeWindowInsets(WindowInsets.navigationBars)
                .testTag(ShellTags.TAB_CONTENT),
        ) {
            TabPager(
                state = pages,
                selected = selected,
                swipeEnabled = swipeEnabled,
                touchExploration = exploring,
                onSwipe = navigator::swipeTo,
                modifier = Modifier.fillMaxSize(),
                page = page,
            )
        }
        if (demoBanner && !(BuildConfig.DEBUG && StoreLook.hideDemoBanner)) DemoBanner(onDemoSignIn)
        ShellNavigationBar(pages.barTab(selected), navigator::select)
    }
}

/** The bar: the tabs in [AppTab]'s order with their labels and icons; select and reselect both reach [onSelect]. */
@Composable
internal fun ShellNavigationBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    ItmoNavigationBar(modifier.testTag(ShellTags.BAR)) {
        AppTab.entries.forEach { tab ->
            ItmoNavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = UiText.Res(tab.label).asString(),
                icon = painterResource(tab.icon.drawable),
                selectedIcon = painterResource(tab.selectedIcon.drawable),
                modifier = Modifier.testTag(ShellTags.tab(tab)),
            )
        }
    }
}

/** The demo banner above the bar: the text and the sign-in action on `secondaryContainer`. */
@Composable
internal fun DemoBanner(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ItmoTheme.colorScheme
    Surface(modifier.fillMaxWidth().testTag(ShellTags.DEMO_BANNER), color = colors.secondaryContainer) {
        Row(
            Modifier
                .heightIn(min = TouchTarget)
                .padding(start = ItmoTheme.spacing.screenMargin, end = DemoBannerEndPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.demo_banner_text),
                Modifier
                    .weight(1f)
                    .padding(vertical = ItmoTheme.spacing.compact),
                color = colors.onSecondaryContainer,
                style = ItmoTheme.typography.bodyMedium,
            )
            TextButton(onClick = onSignIn, Modifier.heightIn(min = TouchTarget)) {
                Text(stringResource(R.string.demo_banner_sign_in), color = colors.onSecondaryContainer)
            }
        }
    }
}

/** The session or the first-run flag is not known yet: a centred progress. */
@Composable
internal fun GateProgress() {
    val description = stringResource(R.string.auth_initializing)
    Surface(Modifier.fillMaxSize(), color = ItmoTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier
                    .testTag(ShellTags.PROGRESS)
                    .semantics { contentDescription = description },
            )
        }
    }
}

/** The top overlay and its neighbour during a push or a pop; [backOffset] is the predictive gesture's progress. */
private data class OverlayTop(val entry: NavEntry<AppRoute>?, val depth: Int)

@Composable
private fun OverlayLayer(overlays: List<NavEntry<AppRoute>>, backOffset: (Any) -> Float) {
    val motion = ItmoTheme.motion
    val duration = if (rememberReducedMotion()) 0 else motion.standardMillis
    val fromEnd = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(
        targetState = OverlayTop(overlays.lastOrNull(), overlays.size),
        modifier = Modifier.fillMaxSize().testTag(ShellTags.OVERLAY_LAYER),
        transitionSpec = {
            val slide = tween<IntOffset>(duration, easing = motion.easing)
            if (targetState.depth >= initialState.depth) {
                slideInHorizontally(slide) { it * fromEnd } togetherWith ExitTransition.KeepUntilTransitionsFinished
            } else {
                // A pop slides the leaving screen out above the one it uncovers.
                (EnterTransition.None togetherWith slideOutHorizontally(slide) { it * fromEnd })
                    .apply { targetContentZIndex = -1f }
            }
        },
        contentKey = { it.entry?.contentKey },
        label = "overlays",
    ) { top ->
        val entry = top.entry
        if (entry == null) {
            // No overlay still fills the layer: AnimatedContent animates its size between the states, so an empty
            // state would shrink the last overlay's pop (and grow the first push) towards the centre, clipped.
            Spacer(Modifier.fillMaxSize())
            return@AnimatedContent
        }
        // A Surface takes every touch, so nothing reaches the covered tab through empty space.
        Surface(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = backOffset(entry.contentKey) * size.width * fromEnd }
                .testTag(ShellTags.overlay(entry.contentKey)),
            color = ItmoTheme.colorScheme.surface,
        ) { entry.Content() }
    }
}

private val TouchTarget = 48.dp
private val DemoBannerEndPadding = 4.dp
