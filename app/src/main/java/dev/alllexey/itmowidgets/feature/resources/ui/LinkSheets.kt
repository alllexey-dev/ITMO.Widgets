package dev.alllexey.itmowidgets.feature.resources.ui

import android.os.Bundle
import androidx.core.os.bundleOf
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.ResourceScope

/** The arguments every links sheet reads through its view model's `SavedStateHandle`. */
internal fun SubjectLinksArgs.toArguments(linkId: String? = null): Bundle = bundleOf(
    SubjectLinksArgs.SUBJECT_ID to subjectId,
    SubjectLinksArgs.SUBJECT_NAME to subjectName,
    SubjectLinksArgs.PERIOD_KEY to periodKey,
).apply { linkId?.let { putString(SubjectLinksArgs.LINK_ID, it) } }

internal fun ResourceScope.toArgs() = SubjectLinksArgs(subjectId, subjectName, periodKey)
