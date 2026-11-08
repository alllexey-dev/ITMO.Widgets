package dev.alllexey.itmowidgets.feature.finances.di

import dev.alllexey.itmowidgets.core.profile.ProfileEntrySource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.finances.data.TuitionFileStore
import dev.alllexey.itmowidgets.feature.finances.data.TuitionRepositoryImpl
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionRepository
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionProfileEntrySource
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Finances: the Договоры об обучении (the scholarship joins later). The platform provides the core types
 * (`MyItmoClient`, `DemoMode`, `CurrentUserProvider`, `Clock`, `AppDirectories`, `AppDispatchers`,
 * `AcademicTimeProvider`); the module adds its row to Профиль and its file to sign-out.
 */
val financesModule = module {
    singleOf<TuitionFileStore, AppDirectories>(::TuitionFileStore)
    // One instance keeps the answer in memory for the screen and Профиль; sign-out removes its file.
    singleOf(::TuitionRepositoryImpl) { bind<TuitionRepository>() }
    single<SessionDataCleaner>(named("tuition")) { get<TuitionRepositoryImpl>() }
    single<ProfileEntrySource>(named("tuition")) { TuitionProfileEntrySource(get(), get()) }
    viewModelOf(::TuitionViewModel)
}
