package dev.alllexey.itmowidgets.ios.widgets

import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.feature.qr.widget.QrPassSnapshot
import dev.alllexey.itmowidgets.feature.qr.widget.QrPassSnapshotWriter
import dev.alllexey.itmowidgets.feature.qr.widget.QrWidgetAppearance
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewLabels
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimeline
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineJson
import dev.alllexey.itmowidgets.feature.schedule.widget.ScheduleTimelineWriter
import dev.alllexey.itmowidgets.feature.schedule.widget.withShortLocations
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleLabels
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleState
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSamples
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimeline
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimelineEntry
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimelineJson
import dev.alllexey.itmowidgets.feature.sport.widget.withShortBuildings
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The widgets gallery's live previews and the widget snapshot tests' samples: the App Group files the writers would
 * leave for the fixed sample (`SchedulePreviewScenario`, `SportWidgetSamples`, the demo pass) with the given settings
 * and widget theme, so Swift draws them with the very readers and views the widget extension uses. Never user data.
 */
object IosWidgetPreviews {

    private val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())

    /** The gallery's moment, 12:22 on the sample Monday (`SchedulePreviewMoment.LESSON`), in seconds since 1970. */
    val lessonMoment: Double get() = moment().toEpochMilliseconds() / MILLIS_PER_SECOND

    /** `schedule-timeline-v2.json` of the whole sample (the Monday and the Tuesday) with [settings] and [theme]. */
    suspend fun schedule(settings: ScheduleWidgetSettings, theme: ThemeSpec?): String {
        val timeline = scenario.timeline(settings, scheduleLabels()).withShortLocations().copy(palette = theme?.widgetPalette())
        return envelope(ScheduleWidgetTimelineJson.VERSION, ScheduleWidgetTimeline.serializer(), timeline, ScheduleTimelineWriter.JSON)
    }

    /** `sport-widget-v1.json` holding [state] of the sample from the gallery's moment on, with [theme]. */
    suspend fun sport(state: SportWidgetSampleState, theme: ThemeSpec?): String {
        val labels = SportWidgetSampleLabels(
            swimming = text(ScheduleStrings.widget_preview_subject_swimming),
            volleyball = text(SportStrings.widget_preview_sport_volleyball),
        )
        val from = moment()
        val timeline = SportWidgetTimeline(
            generatedAt = from.toString(),
            validUntil = (from + 1.days).toString(),
            entries = listOf(SportWidgetTimelineEntry(from.toString(), SportWidgetSamples.snapshot(state, labels))),
            palette = theme?.widgetPalette(),
        ).withShortBuildings()
        return envelope(SportWidgetTimelineJson.VERSION, SportWidgetTimeline.serializer(), timeline, SportWidgetTimelineJson.json)
    }

    /** `qr-pass-v1.json` of the demo pass with the QR widget [settings] and [theme] at the gallery's moment. */
    fun pass(settings: QrWidgetSettings, theme: ThemeSpec?): String {
        val appearance = QrWidgetAppearance(
            spoiler = settings.spoilerEnabled,
            dynamicColors = settings.dynamicColors,
            animation = settings.animationType,
            palette = theme?.widgetPalette(),
        )
        val file = QrPassSnapshotWriter.FILE
        return envelope(file.version, QrPassSnapshot.serializer(), QrPassSnapshotWriter.sample(appearance, moment()), Json)
    }

    private fun moment(): Instant = scenario.instant(SchedulePreviewMoment.LESSON)

    private suspend fun scheduleLabels() = SchedulePreviewLabels(
        math = text(CoreStrings.widget_preview_subject_math),
        physics = text(ScheduleStrings.widget_preview_subject_physics),
        programming = text(CoreStrings.widget_preview_subject_programming),
        english = text(ScheduleStrings.widget_preview_subject_english),
        swimming = text(ScheduleStrings.widget_preview_subject_swimming),
        algorithms = text(ScheduleStrings.widget_preview_subject_algorithms),
        history = text(CoreStrings.widget_preview_subject_history),
        teacher = text(ScheduleStrings.widget_preview_teacher),
    )

    private suspend fun text(key: String): String = UiText.Res(key).resolve()

    /** `{"version": N, "value": ...}`, as `AppGroupSnapshotWriter` stores every App Group file. */
    private fun <T> envelope(version: Int, serializer: KSerializer<T>, value: T, json: Json): String =
        json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("version", version)
                put("value", json.encodeToJsonElement(serializer, value))
            }
        )

    private const val MILLIS_PER_SECOND = 1000.0
}
