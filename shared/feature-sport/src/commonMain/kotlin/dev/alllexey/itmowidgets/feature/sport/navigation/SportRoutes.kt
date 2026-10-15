package dev.alllexey.itmowidgets.feature.sport.navigation

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRouteRegistration
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.RouteKind
import dev.alllexey.itmowidgets.core.navigation.SheetPolicy
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.subclass

/**
 * The sport keys whose payload is this feature's own (route map `SportCommonDetails`). They live here, not in
 * `core/navigation`, because [SportCommonDetailsArgs] is a sport type; the shell joins them through [registration].
 */
object SportRoutes {

    /**
     * The details sheet of a lesson or a booking: [item] is its snapshot, [actionsEnabled] says whether the sheet
     * offers actions and [busy] whether the screen behind it runs one. [replyTo] says where the sheet's booking action
     * goes: the page that opened it or the activity. Never shown together with the schedule's pending-sport sheet
     * ([AppRoutes.SPORT_DETAILS_GROUP]).
     */
    @Serializable
    data class SportCommonDetails(
        val item: SportCommonDetailsArgs,
        val replyTo: SportDetailsOpener,
        val actionsEnabled: Boolean = true,
        val busy: Boolean = false,
    ) : AppRoute {
        override val kind get() = RouteKind.SHEET

        /** 0.82 of the screen, draggable to full: the places and the action show first, the details below. */
        override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.TALL)
        override val exclusiveGroup get() = AppRoutes.SPORT_DETAILS_GROUP
    }

    /** The details sheet of [item] with its actions, whose action goes to [replyTo]. */
    fun details(item: SportCommon, replyTo: SportDetailsOpener, busy: Boolean = false): AppRoute =
        details(item.toDetailsArgs(), replyTo, busy)

    /** The details sheet of the snapshot [item] a screen made, whose action goes to [replyTo]. */
    fun details(item: SportCommonDetailsArgs, replyTo: SportDetailsOpener, busy: Boolean = false): AppRoute =
        SportCommonDetails(item, replyTo, actionsEnabled = true, busy = busy)

    /** This module's keys, for `appRouteSerializersModule` and `rememberNav3AppNavigator`. */
    val registration: AppRouteRegistration = {
        subclass(SportCommonDetails::class)
    }
}

/** Who opened a [SportRoutes.SportCommonDetails] sheet and hears its booking action. */
@Serializable
enum class SportDetailsOpener {
    /** The sport tab's `Моё`. */
    MY,

    /** The sport tab's `Запись`. */
    SIGN,

    /** The shell, for a sport lesson or a pending sport row of the feed or the schedule: it asks before cancelling. */
    SHELL,
    ;

    /** The result-bus key of this opener's actions (`sport_action/<opener>`). */
    val resultKey: String get() = "sport_action/${name.lowercase()}"
}
