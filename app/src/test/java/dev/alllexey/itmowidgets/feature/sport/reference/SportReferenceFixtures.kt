package dev.alllexey.itmowidgets.feature.sport.reference

import android.animation.ValueAnimator
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.migration.DisableInstallInCheck
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.time.AcademicClock
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.DefaultAcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.di.TimeModule
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaZoneId

/**
 * What the sport references share: the demo session (`DemoSport`) on a fixed clock, so the real Fragments show the
 * same catalog, bookings and points on every run; animations off, so the score count-up and the skeleton pulse are
 * captured at their end.
 */
internal object SportReferenceFixtures {

    val zone: TimeZone = TimeZone.of("Europe/Moscow")

    /** Monday noon, the day `SportCardsPreviewActivity.FixedTime` also uses. */
    val now: Instant = LocalDateTime(2026, 9, 7, 12, 0).toInstant(zone)

    fun startDemo(preferences: DemoPreferences) = runBlocking { preferences.setDemoActive(true) }

    /** Runs [block] with the animator duration scale at zero, then restores it. */
    fun <T> withoutAnimations(block: () -> T): T {
        val scale = ValueAnimator.getDurationScale()
        setDurationScale(0f)
        try {
            return block()
        } finally {
            setDurationScale(scale)
        }
    }

    /** Laid out with rows and no pending update. */
    fun RecyclerView.showsRows(): Boolean =
        isShown && childCount > 0 && !hasPendingAdapterUpdates() && !isAnimating

    /** `ValueAnimator.setDurationScale` is hidden; android-all has it. */
    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }
}

/**
 * `TimeModule` with every clock stopped at [SportReferenceFixtures.now]. A test uninstalls `TimeModule` and includes
 * this module from a nested `@InstallIn` one, since Hilt installs only nested modules per test.
 */
@Module
@DisableInstallInCheck
object SportReferenceTime {
    @Provides
    @Singleton
    fun clock(): Clock = object : Clock {
        override fun now(): Instant = SportReferenceFixtures.now
    }

    @Provides
    @Singleton
    fun zone(): TimeZone = SportReferenceFixtures.zone

    @Provides
    @Singleton
    @AcademicClock
    fun academicClock(zone: TimeZone): java.time.Clock =
        java.time.Clock.fixed(SportReferenceFixtures.now.toJavaInstant(), zone.toJavaZoneId())

    @Provides
    @Singleton
    @WallClock
    fun wallClock(): java.time.Clock =
        java.time.Clock.fixed(SportReferenceFixtures.now.toJavaInstant(), java.time.ZoneOffset.UTC)

    @Provides
    @Singleton
    fun overrideStore(@ApplicationContext context: Context): AcademicTimeOverrideStore =
        TimeModule.provideAcademicTimeOverrideStore(context)

    @Provides
    @Singleton
    fun defaultProvider(clock: Clock, zone: TimeZone, store: AcademicTimeOverrideStore): DefaultAcademicTimeProvider =
        TimeModule.provideDefaultAcademicTimeProvider(clock, zone, store)

    @Provides
    fun provider(provider: DefaultAcademicTimeProvider): AcademicTimeProvider = provider

    @Provides
    fun overrideController(provider: DefaultAcademicTimeProvider): AcademicTimeOverrideController = provider
}
