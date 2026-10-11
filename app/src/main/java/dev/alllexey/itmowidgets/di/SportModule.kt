package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.DefaultSportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportScoreOverridePoints
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportScoreOverrideSource
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The app-only inputs of the sport data `sportModule` constructs: the debug lesson templates and the debug score
 * override of `core/debug`, behind the sport ports. Release builds read no override.
 */
val androidSportModule = module {
    singleOf(::DefaultSportLessonTemplateProvider) { bind<SportLessonTemplateProvider>() }
    single<SportScoreOverrideSource> {
        val provider = get<SportScoreOverrideProvider>()
        SportScoreOverrideSource {
            provider.getOverride()?.let { SportScoreOverridePoints(attendances = it.attendances, bonus = it.bonus) }
        }
    }
}
