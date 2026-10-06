package dev.alllexey.itmowidgets.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.modules.subclass
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppRouteSerializationTest {
    private val json = Json { serializersModule = appRouteSerializersModule() }

    @Test
    fun theSamplesCoverEveryRegisteredKey() {
        val registered = appRouteSerializersModule().subclassesOf(AppRoute::class)

        assertEquals(registered, RouteSamples.all.map { it::class }.toSet())
        assertEquals(registered, appRouteSerializersModule().subclassesOf(NavKey::class))
    }

    @Test
    fun everyKeyRoundTripsAsAnAppRouteAndAsANavKey() {
        RouteSamples.all.forEach { route ->
            assertEquals(route, json.decodeFromString(APP_ROUTE, json.encodeToString(APP_ROUTE, route)), "$route")
            assertEquals(route, json.decodeFromString(NAV_KEY, json.encodeToString(NAV_KEY, route)), "$route")
        }
    }

    @Test
    fun aFeatureKeyJoinsTheModule() {
        val module = appRouteSerializersModule({ subclass(FeatureSheet::class) })
        val featureJson = Json { serializersModule = module }
        val route: AppRoute = FeatureSheet(7, replyTo = "my")

        assertEquals(route, featureJson.decodeFromString(NAV_KEY, featureJson.encodeToString(NAV_KEY, route)))
        assertTrue(FeatureSheet::class in module.subclassesOf(NavKey::class))
        assertTrue(AppRoutes.QrPass::class in module.subclassesOf(NavKey::class))
    }

    @Test
    fun theWholeStackSurvivesTheSavedState() {
        val stack = ShellBackStack()
            .select(AppTab.SCHEDULE)
            .request(TabRequest.ScheduleToday)
            .request(TabRequest.SportLesson(42, predicted = true))
            .open(AppRoutes.UserProfile(100001))
            .open(AppRoutes.Settings())
            .open(AppRoutes.SubjectLinks(RouteSamples.links))
            .open(AppRoutes.LinkActions(RouteSamples.links, "link-1"))

        val encoded = json.encodeToString(ShellBackStack.serializer(), stack)
        val restored = json.decodeFromString(ShellBackStack.serializer(), encoded)

        assertEquals(stack, restored)
    }

    @Test
    fun keysDeclareTheirLayerAndOnlySheetsCarryASheetPolicy() {
        RouteSamples.all.forEach { route ->
            if (route.kind == RouteKind.SHEET) assertNotNull(route.sheetPolicy, "$route")
            else assertNull(route.sheetPolicy, "$route")
        }
        assertEquals(SheetPolicy.Dismissal.FORM, AppRoutes.ReviewEditor(RouteSamples.teacher).sheetPolicy.dismissal)
        assertEquals(
            listOf(
                AppRoutes.FriendSelector::class,
                AppRoutes.LessonDetails::class,
                AppRoutes.PendingSportDetails::class
            ),
            RouteSamples.ofKind(RouteKind.SHEET)
                .filter { it.sheetPolicy?.height == SheetPolicy.Height.TALL }
                .map { it::class }
        )
        assertEquals(
            listOf(13, 11, 4, 2),
            listOf(RouteKind.SCREEN, RouteKind.SHEET, RouteKind.DIALOG, RouteKind.GATE)
                .map { RouteSamples.ofKind(it).size }
        )
    }

    @Serializable
    private data class FeatureSheet(val lessonId: Long, val replyTo: String) : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val sheetPolicy get() = SheetPolicy(height = SheetPolicy.Height.TALL)
        override val exclusiveGroup get() = AppRoutes.SPORT_DETAILS_GROUP
    }

    private companion object {
        val APP_ROUTE = PolymorphicSerializer(AppRoute::class)
        val NAV_KEY = PolymorphicSerializer(NavKey::class)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerializersModule.subclassesOf(base: KClass<*>): Set<KClass<*>> {
    val found = mutableSetOf<KClass<*>>()
    dumpTo(object : SerializersModuleCollector {
        override fun <T : Any> contextual(
            kClass: KClass<T>,
            provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
        ) = Unit

        override fun <Base : Any, Sub : Base> polymorphic(
            baseClass: KClass<Base>,
            actualClass: KClass<Sub>,
            actualSerializer: KSerializer<Sub>,
        ) {
            if (baseClass == base) found += actualClass
        }

        override fun <Base : Any> polymorphicDefaultSerializer(
            baseClass: KClass<Base>,
            defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
        ) = Unit

        override fun <Base : Any> polymorphicDefaultDeserializer(
            baseClass: KClass<Base>,
            defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
        ) = Unit
    })
    return found
}
