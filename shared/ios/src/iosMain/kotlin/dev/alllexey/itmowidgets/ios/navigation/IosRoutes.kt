@file:OptIn(ExperimentalSerializationApi::class)

package dev.alllexey.itmowidgets.ios.navigation

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.EntryRouteParser
import dev.alllexey.itmowidgets.core.navigation.appRouteSerializersModule
import kotlin.reflect.KClass
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector

/**
 * The feature whose `iosApp/Sources/App/Router/Routes+<Feature>.swift` maps a key. The Swift router switches over it
 * exhaustively, so a new feature breaks the iOS build until it has a file.
 */
enum class RouteFeature {
    /** Keys of the shell itself: the damaged-link explanation. */
    SHELL,
    ACCOUNT,
    HOME,
    QR,
    SCHEDULE,
    SPORT,
    SOCIAL,
    SETTINGS,
    RECORDBOOK,
    REVIEWS,
    RESOURCES,
    CALENDAR,
    DEBUG,
}

/**
 * The shared route model as the iOS shell reads it (SH-1a2): which feature maps each key, and the entry routes of
 * plain values Swift has (an `AppEntryIntents` action, a link), since Swift cannot use Kotlin default arguments.
 */
object IosRoutes {

    /** Every key the shell can be handed: the core keys and each feature module's registration (none yet). */
    val serializersModule: SerializersModule = appRouteSerializersModule()

    /** Null for a key no feature claims; [unmappedRoutes] keeps that list empty. */
    fun feature(route: AppRoute): RouteFeature? =
        if (route is AppRoutes.TabRoot) tabFeature(route.tab) else features[route::class]

    /** Serial names of the registered keys without a feature; RouterTests requires none. */
    fun unmappedRoutes(): List<String> = registeredRoutes()
        .filterKeys { it != AppRoutes.TabRoot::class && it !in features }
        .values
        .toList()

    /** Serial names of every registered key, in registration order. */
    fun registeredRouteNames(): List<String> = registeredRoutes().values.toList()

    /** The route of an `AppEntryIntents` action without arguments (widget, Control, quick action ids). */
    fun entryRoute(action: String): EntryRoute? = EntryRouteParser.parse(action)

    /** The route of an `https` app link (`AppLinks`); null for a link that is not the app's. */
    fun linkRoute(link: String): EntryRoute? = EntryRouteParser.parse(EntryRouteParser.ACTION_VIEW, link = link)

    private fun tabFeature(tab: AppTab): RouteFeature = when (tab) {
        AppTab.RECORDBOOK -> RouteFeature.RECORDBOOK
        AppTab.SCHEDULE -> RouteFeature.SCHEDULE
        AppTab.HOME -> RouteFeature.HOME
        AppTab.SPORT -> RouteFeature.SPORT
        // The me tab is the social card's (IO-09e).
        AppTab.ME -> RouteFeature.SOCIAL
    }

    private val features: Map<KClass<out AppRoute>, RouteFeature> = mapOf(
        AppRoutes.Auth::class to RouteFeature.ACCOUNT,
        AppRoutes.Onboarding::class to RouteFeature.ACCOUNT,
        AppRoutes.MyItmoWeb::class to RouteFeature.ACCOUNT,
        AppRoutes.WebLogin::class to RouteFeature.ACCOUNT,
        AppRoutes.Settings::class to RouteFeature.SETTINGS,
        AppRoutes.Diagnostics::class to RouteFeature.SETTINGS,
        AppRoutes.DebugTools::class to RouteFeature.DEBUG,
        AppRoutes.RecordbookSubject::class to RouteFeature.RECORDBOOK,
        AppRoutes.RecordbookPeriod::class to RouteFeature.RECORDBOOK,
        AppRoutes.SheetScores::class to RouteFeature.RECORDBOOK,
        AppRoutes.Friends::class to RouteFeature.SOCIAL,
        AppRoutes.UserFriends::class to RouteFeature.SOCIAL,
        AppRoutes.UserSearch::class to RouteFeature.SOCIAL,
        AppRoutes.UserProfile::class to RouteFeature.SOCIAL,
        AppRoutes.UserSchedule::class to RouteFeature.SCHEDULE,
        AppRoutes.ScheduleChanges::class to RouteFeature.SCHEDULE,
        AppRoutes.FriendSelector::class to RouteFeature.SCHEDULE,
        AppRoutes.LessonDetails::class to RouteFeature.SCHEDULE,
        AppRoutes.PendingSportDetails::class to RouteFeature.SCHEDULE,
        AppRoutes.UserSport::class to RouteFeature.SPORT,
        AppRoutes.CancelBookingConfirm::class to RouteFeature.SPORT,
        AppRoutes.QrPass::class to RouteFeature.QR,
        AppRoutes.ReviewEditor::class to RouteFeature.REVIEWS,
        AppRoutes.ReportReview::class to RouteFeature.REVIEWS,
        AppRoutes.SubjectLinks::class to RouteFeature.RESOURCES,
        AppRoutes.LinkEditor::class to RouteFeature.RESOURCES,
        AppRoutes.LinkActions::class to RouteFeature.RESOURCES,
        AppRoutes.ReportLink::class to RouteFeature.RESOURCES,
        AppRoutes.IcsExport::class to RouteFeature.CALENDAR,
        AppRoutes.LinkUnavailable::class to RouteFeature.SHELL,
    )

    private fun registeredRoutes(): Map<KClass<*>, String> {
        val routes = linkedMapOf<KClass<*>, String>()
        serializersModule.dumpTo(object : SerializersModuleCollector {
            override fun <Base : Any, Sub : Base> polymorphic(
                baseClass: KClass<Base>,
                actualClass: KClass<Sub>,
                actualSerializer: KSerializer<Sub>,
            ) {
                if (baseClass == AppRoute::class) routes[actualClass] = actualSerializer.descriptor.serialName
            }

            override fun <T : Any> contextual(
                kClass: KClass<T>,
                provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
            ) = Unit

            override fun <Base : Any> polymorphicDefaultSerializer(
                baseClass: KClass<Base>,
                defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
            ) = Unit

            override fun <Base : Any> polymorphicDefaultDeserializer(
                baseClass: KClass<Base>,
                defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
            ) = Unit
        })
        return routes
    }
}
