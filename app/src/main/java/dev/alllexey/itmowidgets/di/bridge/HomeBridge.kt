package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.supervisorScope
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the home feed. Until every feature's `HomeCardSource` moves to Koin (KM-11a-d, KM-11g2), Hilt
 * still collects them with `@IntoSet`, and this bridge hands the whole set to Koin as one [CompositeHomeCardSource].
 * A lane that moves its source binds it in its own Koin module (`bind HomeCardSource::class`) and drops its
 * `@IntoSet`; nothing goes back into the Hilt set, or the card would show twice.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeBridgeEntryPoint {
    fun homeCardSources(): Set<@JvmSuppressWildcards HomeCardSource>
    fun homeCardPreferences(): HomeCardPreferences
    fun homeHintStore(): HomeHintStore

    companion object {
        fun from(context: Context): HomeBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, HomeBridgeEntryPoint::class.java)
    }
}

/**
 * Several sources as one: [observe] concatenates their cards, [refresh] asks all at once and returns the first
 * failure, [revalidate] and [dismiss] reach every part.
 */
class CompositeHomeCardSource(val parts: List<HomeCardSource>) : HomeCardSource {

    // `combine` over no flows never emits, which would keep the feed loading.
    override fun observe(): Flow<List<HomeCard>> =
        if (parts.isEmpty()) flowOf(emptyList())
        else combine(parts.map { it.observe() }) { lists -> lists.flatMap { it } }

    override suspend fun refresh(): AppResult<Unit> {
        val results = supervisorScope { parts.map { part -> async { part.refresh() } } }.map { it.await() }
        return results.firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Unit)
    }

    override suspend fun revalidate() {
        parts.forEach { it.revalidate() }
    }

    override suspend fun dismiss(kind: HomeCardKind) {
        parts.forEach { it.dismiss(kind) }
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val homeBridgeModule = module {
    single<HomeCardSource> {
        CompositeHomeCardSource(HomeBridgeEntryPoint.from(androidContext()).homeCardSources().toList())
    }
    single<HomeCardPreferences> { HomeBridgeEntryPoint.from(androidContext()).homeCardPreferences() }
    single<HomeHintStore> { HomeBridgeEntryPoint.from(androidContext()).homeHintStore() }
}
