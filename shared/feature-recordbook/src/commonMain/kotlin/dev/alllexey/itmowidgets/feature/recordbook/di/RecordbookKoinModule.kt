package dev.alllexey.itmowidgets.feature.recordbook.di

import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.presentation.BarsLoginViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLinksLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacherLevelsLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The recordbook screens Koin constructs: the four ViewModels, the subject page's loaders and the two resolvers. The
 * recordbook data (MyITMO and BARS repositories, mark tracking, sheet scores, subject bindings) stays on Hilt until
 * KM-11b and reaches Koin through the app's `RecordbookBridge`; the core contracts (time, sport scores, the schedule
 * gateways) come from `CoreBridge`, subject links and teacher levels from the resources and reviews bridges.
 */
val recordbookModule = module {
    // Stateless helpers: every ViewModel gets its own, as with Hilt's unscoped constructors before.
    factoryOf(::SubjectContextResolver)
    factoryOf(::RecordbookSportResolver)
    // A loader keeps per-page state (the binding version, the stable link order), so each subject page owns one.
    factoryOf(::SubjectLessonsLoader)
    factoryOf(::SubjectLinksLoader)
    factoryOf(::SubjectSheetLoader)
    factoryOf(::SubjectTeacherLevelsLoader)
    viewModelOf(::RecordbookViewModel)
    viewModelOf(::RecordbookSubjectViewModel)
    viewModelOf(::SheetScoresViewModel)
    viewModelOf(::BarsLoginViewModel)
}
