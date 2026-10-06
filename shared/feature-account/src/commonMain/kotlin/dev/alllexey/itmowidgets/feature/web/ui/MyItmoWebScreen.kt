package dev.alllexey.itmowidgets.feature.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_close
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.my_itmo_web_title
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_more_vert
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.my_itmo_web_error_description
import dev.alllexey.itmowidgets.shared.feature.account.my_itmo_web_error_title
import dev.alllexey.itmowidgets.shared.feature.account.my_itmo_web_external
import dev.alllexey.itmowidgets.shared.feature.account.my_itmo_web_more
import dev.alllexey.itmowidgets.shared.feature.account.my_itmo_web_reload
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What the My ITMO page shows around its browser. The host derives it from the browser's callbacks. */
enum class MyItmoWebState {
    /** A page loads: the progress line runs over the visible browser. */
    Loading,

    /** The page is loaded: the browser alone. */
    Shown,

    /** A main frame failed or left the official origins: the error page covers the browser. */
    Failed,
}

/**
 * The My ITMO page: the top bar (close, reload, «Открыть в браузере» in the overflow), a 4 dp progress line that keeps
 * its height while hidden so the page never jumps, and the platform [browser] below. [browser] is composed in every
 * state, so the host's platform view lives as long as the screen; on [MyItmoWebState.Failed] the error page covers it
 * and the host hides the view itself, since a platform view's touches and accessibility bypass Compose's drawing order
 * (recipe platform-view-slot).
 */
@Composable
fun MyItmoWebScreen(
    state: MyItmoWebState,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onOpenExternal: () -> Unit,
    onRetry: () -> Unit,
    browser: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(
        modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(CoreRes.string.my_itmo_web_title),
                navigation = {
                    AppTopBarAction(
                        painterResource(KitRes.drawable.ic_close),
                        stringResource(CoreRes.string.common_close),
                        onClose,
                        Modifier.testTag(MyItmoWebTestTags.CLOSE),
                    )
                },
                actions = {
                    AppTopBarAction(
                        painterResource(KitRes.drawable.ic_refresh),
                        stringResource(Res.string.my_itmo_web_reload),
                        onReload,
                        Modifier.testTag(MyItmoWebTestTags.RELOAD),
                    )
                    OverflowMenu(onOpenExternal)
                },
            )
            LoadingLine(visible = state == MyItmoWebState.Loading)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                browser(Modifier.fillMaxSize())
                if (state == MyItmoWebState.Failed) {
                    ContentState(
                        title = stringResource(Res.string.my_itmo_web_error_title),
                        modifier = Modifier
                            .background(ItmoTheme.colorScheme.surface)
                            // Taps on the empty part of the error page stay here instead of reaching the browser.
                            .pointerInput(Unit) {}
                            .testTag(MyItmoWebTestTags.STATE_CONTAINER),
                        icon = painterResource(KitRes.drawable.ic_error),
                        description = stringResource(Res.string.my_itmo_web_error_description),
                        action = ContentStateAction(stringResource(CoreRes.string.common_retry), onRetry),
                    )
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The 4 dp line under the top bar, empty while [visible] is false so the page never moves. material3 widens a progress
 * indicator's semantics bounds by 10 dp, which would cut the bottom off the top bar's 48 dp targets for
 * accessibility, so the line's own semantics are replaced by an indeterminate progress on its 4 dp box.
 */
@Composable
private fun LoadingLine(visible: Boolean) {
    val line = Modifier
        .fillMaxWidth()
        .height(LoadingLineHeight)
    if (visible) {
        Box(
            line
                .testTag(MyItmoWebTestTags.LOADING)
                .clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        ) {
            LinearProgressIndicator(Modifier.fillMaxSize())
        }
    } else {
        Spacer(line)
    }
}

/** The toolbar's overflow: one item, the website outside the app. */
@Composable
private fun OverflowMenu(onOpenExternal: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        AppTopBarAction(
            painterResource(KitRes.drawable.ic_more_vert),
            stringResource(Res.string.my_itmo_web_more),
            onClick = { expanded = true },
            modifier = Modifier.testTag(MyItmoWebTestTags.MORE),
        )
        ItmoMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            groups = listOf(listOf(ItmoMenuItem(stringResource(Res.string.my_itmo_web_external), onOpenExternal))),
        )
    }
}

/** Tags named after the View ids of the XML screen they replace, for host and instrumented tests. */
object MyItmoWebTestTags {
    const val CLOSE = "web_close"
    const val RELOAD = "web_reload"
    const val MORE = "web_more"
    const val LOADING = "loading"

    /** The error page; its only click target is «Повторить» (`state_action` of the XML screen). */
    const val STATE_CONTAINER = "state_container"
}

/** The XML `LinearProgressIndicator`'s `trackThickness`. */
private val LoadingLineHeight = 4.dp
