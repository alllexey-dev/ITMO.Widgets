package dev.alllexey.itmowidgets.feature.sport.data.widget

import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetKind
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSelector
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshot
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimeline
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimelineEntry
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The sport widget's data, fetched as `Моё` fetches it: the score from My ITMO, the bookings with their queues (the
 * queues only through the custom-services gate of [SportDataRepository], demo data in a demo session). Each call
 * refreshes; nothing is kept between calls but the repositories' own state.
 */
class SportWidgetDataProvider(
    private val score: SportDataRepository,
    private val bookings: SportBookingRepository,
    private val selector: SportWidgetSelector,
    private val time: AcademicTimeProvider,
    private val tokens: SessionTokenStore,
) {

    suspend fun load(): SportWidgetSnapshot {
        val data = fetch() ?: return SportWidgetSnapshot.of(SportWidgetKind.SIGNED_OUT)
        return selector.select(data.score, data.bookings, time.now(), time.timeZone)
    }

    /** From now to [until], for iOS WidgetKit (`sport-widget-v1.json`). */
    suspend fun loadTimeline(until: Instant): SportWidgetTimeline {
        val now = time.now()
        val data = fetch() ?: return SportWidgetTimeline(
            generatedAt = now.toString(),
            validUntil = until.toString(),
            entries = listOf(SportWidgetTimelineEntry(now.toString(), SportWidgetSnapshot.of(SportWidgetKind.SIGNED_OUT))),
        )
        return selector.timeline(data.score, data.bookings, now, until, time.timeZone)
    }

    private suspend fun fetch(): Fetched? {
        if (!tokens.hasRefreshToken()) return null
        coroutineScope {
            listOf(
                async { score.refreshSportScore() },
                async { bookings.refreshSportBookings() },
                async { score.refreshSportQueueEntries() },
                async { score.refreshFriendsBookings() },
            ).awaitAll()
        }
        if (!tokens.hasRefreshToken()) return null
        val summary = score.observeSportScore().settled()?.valueOrNull()?.summary
        val booked = bookings.observeSportBookings().settled()?.valueOrNull()
        return Fetched(summary, booked)
    }

    /** The first answer after the refresh, null if none comes in time. */
    private suspend fun <T> Flow<LoadState<T>>.settled(): LoadState<T>? =
        withTimeoutOrNull(SETTLE_TIMEOUT) { first { it != LoadState.Loading } }

    private class Fetched(val score: SportScoreSummary?, val bookings: List<SportBooking>?)

    private companion object {
        val SETTLE_TIMEOUT = 10.seconds
    }
}
