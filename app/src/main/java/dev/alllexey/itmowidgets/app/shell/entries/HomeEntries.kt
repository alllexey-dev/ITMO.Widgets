package dev.alllexey.itmowidgets.app.shell.entries

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.app.shell.ActivityRoutes
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.ui.permission.openAppNotificationSettings
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.home.presentation.TodayViewModel
import dev.alllexey.itmowidgets.feature.home.ui.BellRoute
import dev.alllexey.itmowidgets.feature.home.ui.TodayActions
import dev.alllexey.itmowidgets.feature.home.ui.TodayRoute
import dev.alllexey.itmowidgets.feature.qr.ui.QrPassRoute

/**
 * Сегодня's overlays: the pass sheet (the `Пропуск` button, the widgets, the tile and the shortcut open it) and the
 * bell's sheet, whose rows close it and open their event's home.
 */
internal fun EntryRegistry.Builder.homeEntries() {
    entry<AppRoutes.QrPass> { key, navigator -> QrPassRoute(onClose = { navigator.close(key) }) }
    entry<AppRoutes.Bell> { key, navigator ->
        val context = LocalContext.current
        BellRoute(
            onOpen = { route ->
                navigator.close(key)
                navigator.run(context, route)
            },
            onSettings = {
                navigator.close(key)
                context.openAppNotificationSettings()
            },
            onClose = { navigator.close(key) },
            viewModel = viewModel { KoinStarter.ensureStarted(context).get() },
        )
    }
}

/**
 * Сегодня's root: `TodayRoute` with its ViewModel from Koin, kept in the root's own store. A lesson opens the lesson
 * sheet ([openLesson]), `Подключиться` the meeting link, a thing to do its route.
 */
@Composable
internal fun HomeTabRoot(navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val viewModel: TodayViewModel = viewModel { KoinStarter.ensureStarted(context).get() }
    val actions = remember(navigator, context) {
        TodayActions(
            onLesson = { target -> navigator.openLesson(target) },
            onJoin = { url -> context.openUrl(url) },
            onTodo = { route -> navigator.run(context, route) },
        )
    }
    TodayRoute(actions, viewModel)
}

/** An entry route from a screen: its tab and overlay, then its Activity above everything, as the shell runs intents. */
private fun Nav3AppNavigator.run(context: Context, route: EntryRoute) {
    apply(route)
    route.activity?.let { context.startActivity(ActivityRoutes.intent(context, it)) }
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser or meeting app: nothing to open it with.
    }
}

