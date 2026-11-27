package dev.alllexey.itmowidgets.feature.me.di

import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.me.moderation.data.ModerationNoticesRepositoryImpl
import dev.alllexey.itmowidgets.feature.me.moderation.data.bell.ModerationBellSource
import dev.alllexey.itmowidgets.feature.me.moderation.domain.ModerationNoticesRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The moderation notices' place in the open set of `BellSource`s that Сегодня reads. */
val moderationQualifier: Qualifier = named("moderation")

/**
 * The user's moderation notices: Backend's list in memory and its bell rows. `ModerationNoticesApi`, the gate, the
 * demo switch and the dispatchers come from the platform.
 */
val moderationNoticesModule = module {
    singleOf(::ModerationNoticesRepositoryImpl) { bind<ModerationNoticesRepository>() }
    single<SessionDataCleaner>(moderationQualifier) { get<ModerationNoticesRepositoryImpl>() }
    single<BellSource>(moderationQualifier) { ModerationBellSource(get()) }
}
