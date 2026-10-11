package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Assert.assertEquals
import org.junit.Test

/** Schedule widget and worker invariants that the platform and the iOS widget rely on. */
class ScheduleRulesTest {

    @Test
    fun `schedule widgets, receiver and workers stay in app under their packages`() {
        // Launchers, AlarmManager and WorkManager address these classes by name (StableIdentifiersTest), and KEEP-ed
        // periodic work never heals after a move; RemoteViews and WorkManager are Android-only (ADR 0027).
        val components = productionClasses
            .filter { it.packagee?.name.orEmpty().isIn(SCHEDULE_PACKAGE) }
            .filter { it.hasParentWithName(ANDROID_COMPONENT_BASES) }
        assertEquals(
            "schedule widget providers, receiver, adapter service and workers",
            ANDROID_COMPONENTS,
            components.map { it.fqcn }.toSet(),
        )
        components.assertTrue { it.containingFile.projectPath.startsWith(APP_MAIN) }
    }

    @Test
    fun `the schedule widget timeline always writes its version`() {
        // iOS WidgetKit reads the timeline JSON and rejects a version it does not know; kotlinx skips defaults unless
        // asked, so the field keeps its serial name and the JSON writes defaults.
        productionClasses
            .filter { it.fqcn == TIMELINE_CLASS }
            .requireNonEmpty("the schedule widget timeline")
            .assertTrue { timeline -> timeline.versionParameterText()?.contains(VERSION_SERIAL_NAME) == true }
        productionFiles
            .flatMap { it.objects() }
            .filter { it.packagee?.name == WIDGET_DOMAIN_PACKAGE && it.name == TIMELINE_JSON_OBJECT }
            .requireNonEmpty("the schedule widget timeline JSON")
            .assertTrue { ENCODE_DEFAULTS.containsMatchIn(it.text) }
    }

    private fun KoClassDeclaration.versionParameterText(): String? =
        primaryConstructor?.parameters?.firstOrNull { it.name == VERSION }?.text

    private val KoClassDeclaration.fqcn: String get() = listOfNotNull(packagee?.name, name).joinToString(".")

    private fun String.isIn(pkg: String): Boolean = this == pkg || startsWith("$pkg.")

    private companion object {
        const val APP_MAIN = "/app/src/main/"
        const val SCHEDULE_PACKAGE = "dev.alllexey.itmowidgets.feature.schedule"
        const val WIDGET_DOMAIN_PACKAGE = "dev.alllexey.itmowidgets.feature.schedule.domain.widget"
        const val TIMELINE_CLASS = "$WIDGET_DOMAIN_PACKAGE.ScheduleWidgetTimeline"
        const val TIMELINE_JSON_OBJECT = "ScheduleWidgetTimelineJson"
        const val VERSION = "version"
        const val VERSION_SERIAL_NAME = "@SerialName(\"version\")"

        val ENCODE_DEFAULTS = Regex("""\bencodeDefaults\s*=\s*true\b""")

        val ANDROID_COMPONENT_BASES = setOf(
            "CoroutineWorker",
            "Worker",
            "ListenableWorker",
            "AppWidgetProvider",
            "BroadcastReceiver",
            "RemoteViewsService",
        )

        /** Stable identifiers: the names never change, and none of these leaves `:app`. */
        val ANDROID_COMPONENTS = setOf(
            "$SCHEDULE_PACKAGE.ui.widget.SingleLessonWidgetProvider",
            "$SCHEDULE_PACKAGE.ui.widget.DayScheduleWidgetProvider",
            "$SCHEDULE_PACKAGE.ui.widget.ScheduleWidgetRemoteViewsService",
            "$SCHEDULE_PACKAGE.work.ScheduleWidgetRefreshReceiver",
            "$SCHEDULE_PACKAGE.work.ScheduleWidgetUpdateWorker",
            "$SCHEDULE_PACKAGE.work.ScheduleChangesWorker",
            "$SCHEDULE_PACKAGE.work.CalendarSyncWorker",
        )
    }
}
