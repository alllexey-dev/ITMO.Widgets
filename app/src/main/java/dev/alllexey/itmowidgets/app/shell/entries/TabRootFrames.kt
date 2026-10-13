package dev.alllexey.itmowidgets.app.shell.entries

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.kit.HeaderAction
import dev.alllexey.itmowidgets.designsystem.kit.ItmoRootScreen
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.home.presentation.TodayHeaderUiState
import dev.alllexey.itmowidgets.feature.home.presentation.TodayHeaderViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.qr_pass_sheet_title
import dev.alllexey.itmowidgets.shared.compose.strings.today_notifications
import dev.alllexey.itmowidgets.shared.designsystem.ic_notification
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of the tab roots' frames. */
object TabRootTags {
    const val PASS_FAB = "shell:pass_fab"
    const val BELL = "shell:bell"
}

/**
 * The frame of a tab root (docs/design.md "Shell"): the kit's root header with the tab's name and the [content] under
 * it. Сегодня adds its date, the bell and the `Пропуск` button; Зачётка draws its own header with the period; the other
 * tabs show their name until their new screens bring their own subtitle and actions.
 */
@Composable
internal fun TabRootFrame(tab: AppTab, navigator: Nav3AppNavigator, content: @Composable () -> Unit) {
    when (tab) {
        AppTab.HOME -> TodayFrame(navigator, content)
        AppTab.RECORDBOOK -> content()
        else -> ItmoRootScreen(title = UiText.Res(tab.label).asString()) { padding -> Body(padding, content) }
    }
}

/** Сегодня with its header's view model: the date re-read whenever the tab resumes. */
@Composable
private fun TodayFrame(navigator: Nav3AppNavigator, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val header: TodayHeaderViewModel = viewModel { KoinStarter.ensureStarted(context).get() }
    val state by header.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(header) {
        header.refresh()
        onPauseOrDispose { }
    }
    TodayRootScreen(
        header = state,
        onOpenNotifications = { navigator.open(AppRoutes.Bell) },
        onOpenPass = { navigator.open(AppRoutes.QrPass) },
        content = content,
    )
}

/**
 * Сегодня's root: the date under the title, the bell with the count of unseen events, which opens its sheet, and the
 * `Пропуск` button that opens the pass sheet; the button and the bell are on this tab only.
 */
@Composable
internal fun TodayRootScreen(
    header: TodayHeaderUiState,
    onOpenNotifications: () -> Unit,
    onOpenPass: () -> Unit,
    content: @Composable () -> Unit,
) {
    ItmoRootScreen(
        title = header.title.asString(),
        subtitle = header.subtitle.asString(),
        actions = {
            HeaderAction(
                painterResource(KitRes.drawable.ic_notification),
                stringResource(Res.string.today_notifications),
                onClick = onOpenNotifications,
                modifier = Modifier.testTag(TabRootTags.BELL),
                badge = header.badge,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenPass,
                modifier = Modifier.testTag(TabRootTags.PASS_FAB),
                icon = { Icon(painterResource(KitRes.drawable.ic_qr_code), contentDescription = null) },
                text = { Text(stringResource(Res.string.qr_pass_sheet_title)) },
            )
        },
    ) { padding -> Body(padding, content) }
}

@Composable
private fun Body(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding)) { content() }
}
