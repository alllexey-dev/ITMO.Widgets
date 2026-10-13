package dev.alllexey.itmowidgets.app.shell

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.RouteKind
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.navigation.ItmoNavigationBar
import dev.alllexey.itmowidgets.designsystem.components.navigation.ItmoNavigationBarItem
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import org.jetbrains.compose.resources.painterResource

/** Test tags of the shell's chrome, for the shell's tests and probes. */
object ShellTags {
    /** The demo banner and the bar under the display. */
    const val CHROME = "shell:chrome"

    /** The shown tab root's area above the chrome. */
    const val TAB_CONTENT = "shell:tab_content"
    const val BAR = "shell:bar"
    const val DEMO_BANNER = "shell:demo_banner"
    const val PROGRESS = "shell:progress"

    fun tab(tab: AppTab): String = "shell:tab:${tab.name}"

    /** A pushed screen's surface. */
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

/** The height of the chrome under the tab roots; the shell's layout provides it above the `NavDisplay`. */
internal val LocalTabChromeHeight = staticCompositionLocalOf { 0.dp }

/**
 * The entry in its layer of the shell: a tab root ends above the chrome, which pads the navigation bar itself; a pushed
 * screen sits on an opaque full-window surface that takes every touch, so the chrome under it is never reached.
 * Sheets, dialogs and gates are their own windows or surfaces.
 */
internal fun NavEntry<AppRoute>.inShellLayer(kind: RouteKind): NavEntry<AppRoute> {
    val entry = this
    return when (kind) {
        RouteKind.TAB_ROOT -> NavEntry<AppRoute>(navEntry = entry) { TabRootLayer { entry.Content() } }
        RouteKind.SCREEN -> NavEntry<AppRoute>(navEntry = entry) { ScreenLayer(entry.contentKey) { entry.Content() } }
        RouteKind.GATE, RouteKind.SHEET, RouteKind.DIALOG -> entry
    }
}

@Composable
private fun TabRootLayer(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = LocalTabChromeHeight.current)
            .consumeWindowInsets(WindowInsets.navigationBars)
            .testTag(ShellTags.TAB_CONTENT),
    ) { content() }
}

/** A leaving screen rounds its corners as it shrinks in the predictive Back (M3), square again once it settles. */
@Composable
private fun ScreenLayer(contentKey: Any, content: @Composable () -> Unit) {
    val corner by LocalNavAnimatedContentScope.current.transition.animateDp(label = "screenCorner") { state ->
        if (state == EnterExitState.PostExit) LeavingCorner else 0.dp
    }
    Surface(
        Modifier.fillMaxSize().testTag(ShellTags.overlay(contentKey)),
        shape = RoundedCornerShape(corner),
        color = ItmoTheme.colorScheme.surface,
    ) { content() }
}

/**
 * The chrome under the tab roots: the demo banner above the bar. While a pushed screen [covered] it, it keeps its place for the predictive Back to
 * uncover and is hidden from TalkBack.
 */
@Composable
internal fun TabChrome(
    selected: AppTab,
    covered: Boolean,
    demoBanner: Boolean,
    onSelect: (AppTab) -> Unit,
    onDemoSignIn: () -> Unit,
) {
    val hidden = if (covered) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier
    Column(Modifier.fillMaxWidth().testTag(ShellTags.CHROME).then(hidden)) {
        if (demoBanner && !(BuildConfig.DEBUG && StoreLook.hideDemoBanner)) DemoBanner(onDemoSignIn)
        ShellNavigationBar(selected, onSelect)
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

private val TouchTarget = 48.dp
private val LeavingCorner = 28.dp
private val DemoBannerEndPadding = 4.dp
