package dev.alllexey.itmowidgets.feature.resources.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs

// The Koin parameters of the links SwiftUI sheets: a SavedStateHandle with what Android's shell seeds from the route
// (`SubjectLinksArgs.toArguments`), since a SwiftUI store has no saved-state registry.

/**
 * `SubjectLinksViewModel` of all links ([linkId] null), of one link's actions and its report, or `LinkEditorViewModel`
 * of a new link ([linkId] null) or the viewer's own one.
 */
fun subjectLinksParameters(args: SubjectLinksArgs, linkId: String?): List<Any> = listOf(
    SavedStateHandle(
        listOfNotNull(
            SubjectLinksArgs.SUBJECT_ID to args.subjectId,
            SubjectLinksArgs.SUBJECT_NAME to args.subjectName,
            SubjectLinksArgs.PERIOD_KEY to args.periodKey,
            linkId?.let { SubjectLinksArgs.LINK_ID to it },
        ).toMap(),
    ),
)
