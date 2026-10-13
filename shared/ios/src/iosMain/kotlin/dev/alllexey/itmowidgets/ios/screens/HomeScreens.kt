package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.ui.TodayActions
import dev.alllexey.itmowidgets.feature.home.ui.TodayRoute
import platform.UIKit.UIViewController

/**
 * The home tab's root (`AppRoutes.TabRoot(HOME)`), Сегодня as Android's shell hosts it, until its SwiftUI screen
 * replaces this host. Lessons and things to do open their keys through [open], the Swift router; the meeting link
 * goes to [openUrl]. The content keeps clear of the status bar, the tab bar and the home indicator on every side.
 */
fun homeViewController(open: (AppRoute) -> Unit, openUrl: (String) -> Unit): UIViewController = screenController {
    val actions = TodayActions(
        onLesson = { target ->
            when (target) {
                is LessonTarget.Lesson -> open(AppRoutes.LessonDetails(target.args))
                is LessonTarget.PendingSport -> open(AppRoutes.PendingSportDetails(target.args))
            }
        },
        onJoin = openUrl,
        onTodo = { route -> open(route.overlay ?: AppRoutes.TabRoot(route.tab)) },
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TodayRoute(actions)
    }
}
