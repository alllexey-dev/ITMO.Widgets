package dev.alllexey.itmowidgets.app.shell

import androidx.activity.compose.BackHandler
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
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.designsystem.components.navigation.ItmoNavigationBar
import dev.alllexey.itmowidgets.designsystem.components.navigation.ItmoNavigationBarItem
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_account_circle
import dev.alllexey.itmowidgets.shared.designsystem.ic_account_circle_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_home
import dev.alllexey.itmowidgets.shared.designsystem.ic_home_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule_filled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Test tags of the shell's chrome, for the shell's tests and probes. */
object ShellTags {
    const val TAB_LAYER = "shell:tabs"
    const val TAB_CONTENT = "shell:tab_content"
    const val BAR = "shell:bar"
    const val DEMO_BANNER = "shell:demo_banner"
    const val OVERLAY_LAYER = "shell:overlays"
    const val PROGRESS = "shell:progress"

    fun tab(tab: AppTab): String = "shell:tab:${tab.name}"

    fun overlay(contentKey: Any): String = "shell:overlay:$contentKey"
}

/**
 * The layers under the sheets and dialogs: the selected tab's root above the demo banner and the bar, and the overlay
 * screens full window above them, sliding in from the end in the standard 220 ms (`AppOverlayHostFragment`). While an
 * overlay covers it, the tab layer keeps its size, takes no touches and is hidden from TalkBack. Back pops the top
 * overlay with the predictive gesture, then leads a tab other than the start one to the start tab; on the start tab
 * with nothing above it the system handles Back (leaves the app).
 */
@Composable
internal fun ShellLayers(
    tabRoot: NavEntry<AppRoute>,
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
            tab = state.tab,
            onSelect = navigator::select,
            covered = overlays.isNotEmpty(),
            demoBanner = demoBanner,
            onDemoSignIn = onDemoSignIn,
        ) { tabRoot.Content() }
        OverlayLayer(overlays) { key -> if (key == backingKey) backProgress else 0f }
    }
}

@Composable
private fun TabLayer(
    tab: AppTab,
    onSelect: (AppTab) -> Unit,
    covered: Boolean,
    demoBanner: Boolean,
    onDemoSignIn: () -> Unit,
    content: @Composable () -> Unit,
) {
    val hidden = if (covered) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier
    Column(Modifier.fillMaxSize().testTag(ShellTags.TAB_LAYER).then(hidden)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The bar below pads itself by the navigation bar; the tab's content must not pad again.
                .consumeWindowInsets(WindowInsets.navigationBars)
                .testTag(ShellTags.TAB_CONTENT),
        ) { content() }
        if (demoBanner) DemoBanner(onDemoSignIn)
        ShellNavigationBar(tab, onSelect)
    }
}

/** `activity_main.xml`'s bar: `bottom_nav.xml`'s order, labels and icons; select and reselect both reach [onSelect]. */
@Composable
internal fun ShellNavigationBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    ItmoNavigationBar(modifier.testTag(ShellTags.BAR)) {
        AppTab.entries.forEach { tab ->
            val icons = tab.icons
            ItmoNavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = stringResource(tab.label),
                icon = painterResource(icons.first),
                selectedIcon = painterResource(icons.second),
                modifier = Modifier.testTag(ShellTags.tab(tab)),
            )
        }
    }
}

private val AppTab.label: Int
    get() = when (this) {
        AppTab.RECORDBOOK -> R.string.title_recordbook
        AppTab.SCHEDULE -> R.string.title_schedule
        AppTab.HOME -> R.string.title_home
        AppTab.SPORT -> R.string.title_sport
        AppTab.ME -> R.string.title_me
    }

/** The outlined icon and the FILL 1 one of the selected tab (`nav_*.xml`). */
private val AppTab.icons: Pair<DrawableResource, DrawableResource>
    get() = when (this) {
        AppTab.RECORDBOOK -> Res.drawable.ic_menu_book to Res.drawable.ic_menu_book_filled
        AppTab.SCHEDULE -> Res.drawable.ic_schedule to Res.drawable.ic_schedule_filled
        AppTab.HOME -> Res.drawable.ic_home to Res.drawable.ic_home_filled
        AppTab.SPORT -> Res.drawable.ic_exercise to Res.drawable.ic_exercise_filled
        AppTab.ME -> Res.drawable.ic_account_circle to Res.drawable.ic_account_circle_filled
    }

/** `activity_main.xml`'s demo banner above the bar: the text and the sign-in action on `secondaryContainer`. */
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

/** The session or the first-run flag is not known yet: `activity_main.xml`'s centred progress. */
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
        val entry = top.entry ?: return@AnimatedContent
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
