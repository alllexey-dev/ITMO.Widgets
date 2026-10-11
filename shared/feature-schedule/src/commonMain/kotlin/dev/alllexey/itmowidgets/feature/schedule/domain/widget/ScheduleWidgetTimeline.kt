package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the schedule widgets show from [generatedAt] to [validUntil]: each entry holds from its `validFrom` to the next
 * entry's, the last one to [validUntil]. Every entry equals [ScheduleWidgetSelector.select] at any instant of its
 * interval, except that a pending-sport snapshot's `pendingValidUntil` is the end of the entry. [palette] is the app's
 * colours while «Виджеты в цвет темы» is on, absent otherwise (additive in version 1).
 */
@Serializable
data class ScheduleWidgetTimeline(
    @SerialName("version") val version: Int = ScheduleWidgetTimelineJson.VERSION,
    @SerialName("generatedAt") @Serializable(with = IsoInstantSerializer::class) val generatedAt: Instant,
    @SerialName("validUntil") @Serializable(with = IsoInstantSerializer::class) val validUntil: Instant,
    @SerialName("entries") val entries: List<ScheduleWidgetTimelineEntry>,
    @SerialName("palette") val palette: WidgetPalette? = null,
) {

    /** The entry shown at [instant], null before the first entry and from [validUntil] on. */
    fun entryAt(instant: Instant): ScheduleWidgetTimelineEntry? {
        if (instant >= validUntil) return null
        return entries.lastOrNull { it.validFrom <= instant }
    }

    /** Where the entry at [index] stops holding: the next entry's start, or [validUntil] for the last one. */
    fun validityEnd(index: Int): Instant = entries.getOrNull(index + 1)?.validFrom ?: validUntil
}

@Serializable
data class ScheduleWidgetTimelineEntry(
    @SerialName("validFrom") @Serializable(with = IsoInstantSerializer::class) val validFrom: Instant,
    @SerialName("snapshot") val snapshot: ScheduleWidgetSnapshot,
)

/**
 * The timeline's JSON, the App Group contract read by iOS WidgetKit (L18 IO-10b); reference fixture
 * `shared/feature-schedule/fixtures/schedule-widget-timeline-v1.json`. Snapshot keys and enum names are those of
 * `widgets/schedule_snapshot.json` (KM-05a1), nulls are omitted, defaults are written (Swift has no Kotlin defaults),
 * instants are ISO-8601 UTC strings that may carry fractional seconds. Data and keys only: labels such as lesson type
 * names are resolved on the reading side.
 *
 * [VERSION] stays for additive fields and is bumped for a rename or a removal; a reader rejects a higher one. It is
 * independent of the Android snapshot file's `formatVersion`.
 */
object ScheduleWidgetTimelineJson {
    const val VERSION = 1
    private const val VERSION_KEY = "version"

    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(timeline: ScheduleWidgetTimeline): String =
        json.encodeToString(ScheduleWidgetTimeline.serializer(), timeline)

    /** Throws on corrupt JSON and on a version this build does not know. */
    fun decode(text: String): ScheduleWidgetTimeline {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root[VERSION_KEY]?.jsonPrimitive?.intOrNull
        require(version != null && version in 1..VERSION) { "Unsupported schedule widget timeline version $version" }
        return json.decodeFromJsonElement(ScheduleWidgetTimeline.serializer(), root)
    }
}

/** `Instant.toString()` and back, the same text `pendingValidUntil` already holds. */
internal object IsoInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor(
        "dev.alllexey.itmowidgets.feature.schedule.domain.widget.IsoInstant", PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
