package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.feature.home.data.DataStoreHomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.data.DataStoreHomeHintStore
import dev.alllexey.itmowidgets.feature.home.data.HintHomeCardSource
import dev.alllexey.itmowidgets.feature.home.di.hintCardsQualifier
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleChangesHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleCardsQualifier
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleChangesCardsQualifier
import dev.alllexey.itmowidgets.feature.social.data.home.SocialHomeCardSource
import dev.alllexey.itmowidgets.feature.social.di.socialCardsQualifier
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The home feed's fixture in Koin: a debug host replaces the feed's sources, the card preferences, the hint store and
 * the wall clock with its own while it lives, so `HomeFragment` obtains its ViewModel exactly as in release. The fake
 * source stands in for the Hilt-built sources; the hint cards, which read the device, and the Koin-built sources of
 * other features (social's friend requests, the schedule's two cards) contribute nothing.
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process would get the fake. Main thread only, like the host callbacks.
 */
object HomeDebugFixtures {

    /** Builds a fresh set of fakes for each new `HomeViewModel`, reading the host's fixture at that moment. */
    interface Fakes {
        fun source(): HomeCardSource
        fun preferences(): HomeCardPreferences
        fun hintStore(): HomeHintStore
    }

    /** The bridge modules that define overridden types, loaded again once the last fixture goes. */
    private val bridgeModules: List<Module> get() = listOf(coreBridgeModule, homeBridgeModule)

    private var current: Module? = null

    /** Overrides the feed's sources, its stores and the wall clock; returns the handle [unload] takes. */
    fun load(context: Context, fakes: Fakes, clock: Clock): Module {
        val fixture = module {
            factory<HomeCardSource>(qualifier = hiltCardsQualifier) { fakes.source() }
            factory<HomeCardSource>(qualifier = hintCardsQualifier) { CompositeHomeCardSource(emptyList()) }
            factory<HomeCardSource>(qualifier = socialCardsQualifier) { CompositeHomeCardSource(emptyList()) }
            factory<HomeCardSource>(qualifier = scheduleCardsQualifier) { CompositeHomeCardSource(emptyList()) }
            factory<HomeCardSource>(qualifier = scheduleChangesCardsQualifier) { CompositeHomeCardSource(emptyList()) }
            factory<HomeCardPreferences> { fakes.preferences() }
            factory<HomeHintStore> { fakes.hintStore() }
            single<Clock> { clock }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release bindings. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the bridge modules load again (their singles forward Hilt's instances) and the keys of the feed's
     * own definitions point back at the singles of `homeModule`: reloading it would build a second hint source beside
     * the one an open feed holds. A fixture that a newer host already replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(bridgeModules, allowOverride = true)
        koin.declare<HomeCardPreferences>(koin.get<DataStoreHomeCardPreferences>(), allowOverride = true)
        koin.declare<HomeHintStore>(koin.get<DataStoreHomeHintStore>(), allowOverride = true)
        // After the stores: a hint source first built here must read the release hint store.
        koin.declare<HomeCardSource>(koin.get<HintHomeCardSource>(), hintCardsQualifier, allowOverride = true)
        koin.declare<HomeCardSource>(koin.get<SocialHomeCardSource>(), socialCardsQualifier, allowOverride = true)
        koin.declare<HomeCardSource>(koin.get<ScheduleHomeCardSource>(), scheduleCardsQualifier, allowOverride = true)
        koin.declare<HomeCardSource>(
            koin.get<ScheduleChangesHomeCardSource>(), scheduleChangesCardsQualifier, allowOverride = true
        )
        current = null
    }
}
