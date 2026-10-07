package dev.alllexey.itmowidgets.feature.update.navigation

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRouteRegistration
import dev.alllexey.itmowidgets.core.navigation.RouteKind
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate as CheckedUpdate
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateArgs
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.subclass

/**
 * The update offer's key (route map `APP_UPDATE`). It lives here, not in `core/navigation`, because its payload is
 * the feature's own [AppUpdateArgs]; the shell joins it through [registration].
 */
object UpdateRoutes {

    /** The offer of [args], the result of the check, so the screen never repeats the request. */
    @Serializable
    data class AppUpdate(val args: AppUpdateArgs) : AppRoute {
        override val kind get() = RouteKind.SCREEN
    }

    /** The key of the offer the shell's update check found. */
    fun of(update: CheckedUpdate): AppRoute = AppUpdate(AppUpdateArgs.of(update))

    /** This module's keys, for `appRouteSerializersModule` and `rememberNav3AppNavigator`. */
    val registration: AppRouteRegistration = {
        subclass(AppUpdate::class)
    }
}
