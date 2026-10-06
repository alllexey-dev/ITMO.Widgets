package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.ui.reviews.ReviewPreviewFixtures as F

/** The summary as it first shows: the tone, pros and cons, tags, the scales folded. */
@Preview(name = "collapsed")
@Composable
private fun TeacherSummaryCardPreview() = ItmoPreview {
    PreviewColumn { TeacherSummaryCard(F.summary, expanded = false, onToggle = {}) }
}

/** The scales open, one of them without enough data. */
@Preview(name = "expanded")
@Composable
private fun TeacherSummaryCardExpandedPreview() = ItmoPreview {
    PreviewColumn { TeacherSummaryCard(F.summary, expanded = true, onToggle = {}) }
}

/** Low confidence: no tone; no pros, so the cons follow the description; no tags; four scales missing. */
@Preview(name = "sparse")
@Composable
private fun TeacherSummaryCardSparsePreview() = ItmoPreview {
    PreviewColumn { TeacherSummaryCard(F.sparseSummary, expanded = true, onToggle = {}) }
}

/**
 * Every block long and open; also recorded at 320 dp and font 1.3 (`-Pshots.appearance=full`), in a window tall
 * enough for the whole card there.
 */
@Preview(name = "long", heightDp = 2000)
@Composable
private fun TeacherSummaryCardLongPreview() = ItmoPreview {
    PreviewColumn { TeacherSummaryCard(F.longSummary, expanded = true, onToggle = {}) }
}
