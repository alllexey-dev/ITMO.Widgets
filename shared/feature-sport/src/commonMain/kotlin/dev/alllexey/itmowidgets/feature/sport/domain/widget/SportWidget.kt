package dev.alllexey.itmowidgets.feature.sport.domain.widget

import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * The sport widget (W3): the score ring and the nearest bookings or queues, in `widgets/sport_snapshot.json` (Android)
 * and `sport-widget-v1.json` (iOS WidgetKit, ADR 0027). Data only; the drawing side says `записаны`, `очередь 2 из 5`
 * and `ждём расписание` from the sport catalog, as the Спорт tab does.
 */

@Serializable
enum class SportWidgetKind {
    @SerialName("LOADING") LOADING,
    @SerialName("SIGNED_OUT") SIGNED_OUT,
    @SerialName("ERROR") ERROR,
    @SerialName("CONTENT") CONTENT,
}

/** [BOOKED] `записаны`; [QUEUE] `очередь 2 из 5` (`очередь` without a position); [UNPUBLISHED] `ждём расписание`. */
@Serializable
enum class SportWidgetStatus {
    @SerialName("BOOKED") BOOKED,
    @SerialName("QUEUE") QUEUE,
    @SerialName("UNPUBLISHED") UNPUBLISHED,
}

/** Which day a booking is on, seen from the snapshot's instant: `Сегодня 17:00`, `Завтра 15:20`, `Чт 15:20`. */
@Serializable
enum class SportWidgetDay {
    @SerialName("TODAY") TODAY,
    @SerialName("TOMORROW") TOMORROW,
    @SerialName("LATER") LATER,
}

/**
 * A booking or a queue: [date] and [time] in academic time, [section] and the raw [place] (MyITMO's room text, the
 * drawing side shortens its building), the [status] with the queue [position] of [total]. [lessonId] opens its sheet.
 */
@Serializable
data class SportWidgetItem(
    @SerialName("lessonId") val lessonId: Long,
    @SerialName("date") val date: String,
    @SerialName("day") val day: SportWidgetDay,
    @SerialName("time") val time: String,
    @SerialName("section") val section: String,
    @SerialName("place") val place: String? = null,
    @SerialName("status") val status: SportWidgetStatus,
    @SerialName("position") val position: Int = 0,
    @SerialName("total") val total: Int = 0,
    @SerialName("predicted") val predicted: Boolean = false,
)

/**
 * [visits] and [bonus] (the credited bonus, at most 40) make [total]; [items] are up to two bookings or queues, null
 * when they could not be loaded (the ring still shows).
 */
@Serializable
data class SportWidgetSnapshot(
    @SerialName("kind") val kind: SportWidgetKind,
    @SerialName("visits") val visits: Int = 0,
    @SerialName("bonus") val bonus: Int = 0,
    @SerialName("items") val items: List<SportWidgetItem>? = null,
) {
    val total: Int get() = visits + bonus

    /** The credit is reached: the ring closes and turns green. */
    val done: Boolean get() = total >= CREDIT

    val remaining: Int get() = (CREDIT - total).coerceAtLeast(0)

    companion object {
        /** The points of the credit, as the Спорт ring counts them. */
        const val CREDIT = 100

        fun of(kind: SportWidgetKind) = SportWidgetSnapshot(kind)
    }
}

/** What the sport widget shows at an instant; Android renders it, iOS reads a [SportWidgetTimeline] of it. */
class SportWidgetSelector {

    /** [score] null: the score failed; [bookings] null: the bookings failed. */
    fun select(score: SportScoreSummary?, bookings: List<SportBooking>?, now: Instant, timeZone: TimeZone): SportWidgetSnapshot {
        if (score == null) return SportWidgetSnapshot.of(SportWidgetKind.ERROR)
        val today = now.toLocalDateTime(timeZone).date
        return SportWidgetSnapshot(
            kind = SportWidgetKind.CONTENT,
            visits = score.attendances,
            bonus = score.creditedBonus,
            items = bookings?.let { upcoming(it, now) }?.take(ITEMS)?.map { it.toItem(today, timeZone) },
        )
    }

    /** [select] at [from], at each booking's end and at every midnight before [until]. */
    fun timeline(
        score: SportScoreSummary?,
        bookings: List<SportBooking>?,
        from: Instant,
        until: Instant,
        timeZone: TimeZone,
    ): SportWidgetTimeline {
        require(from < until) { "Timeline ends at $until, not after its start $from" }
        val ends = bookings.orEmpty().map { it.end }
        val midnights = generateSequence(from.toLocalDateTime(timeZone).date.plus(1, DateTimeUnit.DAY)) {
            it.plus(1, DateTimeUnit.DAY)
        }.map { it.atStartOfDayIn(timeZone) }.takeWhile { it < until }
        val starts = (listOf(from) + (ends + midnights).filter { it > from && it < until }).distinct().sorted()
        val entries = mutableListOf<SportWidgetTimelineEntry>()
        for (start in starts) {
            val snapshot = select(score, bookings, start, timeZone)
            if (entries.lastOrNull()?.snapshot != snapshot) entries += SportWidgetTimelineEntry(start.toString(), snapshot)
        }
        return SportWidgetTimeline(generatedAt = from.toString(), validUntil = until.toString(), entries = entries)
    }

    /** As `Моё`: bookings and waiting queues that have not ended, nearest first. */
    private fun upcoming(bookings: List<SportBooking>, now: Instant) =
        bookings.filter { it.end > now && (it.signed || it.signEntry?.isWaiting() == true) }.sortedBy { it.start }

    private fun SportQueueEntry.isWaiting() =
        !isCancelled && status in SportQueueEntryStatus.notifiableStatuses

    private fun SportBooking.toItem(today: LocalDate, timeZone: TimeZone): SportWidgetItem {
        val start = start.toLocalDateTime(timeZone)
        val entry = signEntry?.takeIf { !signed }
        return SportWidgetItem(
            lessonId = lessonId,
            date = start.date.toString(),
            day = when (start.date) {
                today -> SportWidgetDay.TODAY
                today.plus(1, DateTimeUnit.DAY) -> SportWidgetDay.TOMORROW
                else -> SportWidgetDay.LATER
            },
            time = start.time.format(DateTexts.TIME),
            section = sectionName.raw.trim(),
            place = roomName.trim().takeIf(String::isNotEmpty),
            status = when {
                entry == null -> SportWidgetStatus.BOOKED
                !isLessonReal -> SportWidgetStatus.UNPUBLISHED
                else -> SportWidgetStatus.QUEUE
            },
            position = entry?.position?.coerceAtLeast(0) ?: 0,
            total = entry?.total?.coerceAtLeast(0) ?: 0,
            predicted = !isLessonReal,
        )
    }

    private companion object {
        const val ITEMS = 2
    }
}

/** The sport widget from [generatedAt] to [validUntil], one entry per change (`sport-widget-v1.json`). */
@Serializable
data class SportWidgetTimeline(
    @SerialName("version") val version: Int = SportWidgetTimelineJson.VERSION,
    @SerialName("generatedAt") val generatedAt: String,
    @SerialName("validUntil") val validUntil: String,
    @SerialName("entries") val entries: List<SportWidgetTimelineEntry>,
    @SerialName("palette") val palette: WidgetPalette? = null,
)

@Serializable
data class SportWidgetTimelineEntry(
    @SerialName("validFrom") val validFrom: String,
    @SerialName("snapshot") val snapshot: SportWidgetSnapshot,
)

/** The App Group contract's JSON rules, as the schedule timeline's: nulls omitted, defaults written. */
object SportWidgetTimelineJson {
    const val VERSION = 1

    val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(timeline: SportWidgetTimeline): String = json.encodeToString(SportWidgetTimeline.serializer(), timeline)

    /** Throws on corrupt JSON and on a version this build does not know. */
    fun decode(text: String): SportWidgetTimeline {
        val root = json.parseToJsonElement(text).jsonObject
        require(root["version"]?.jsonPrimitive?.intOrNull == VERSION) { "Unsupported sport widget timeline" }
        return json.decodeFromJsonElement(SportWidgetTimeline.serializer(), root)
    }
}
