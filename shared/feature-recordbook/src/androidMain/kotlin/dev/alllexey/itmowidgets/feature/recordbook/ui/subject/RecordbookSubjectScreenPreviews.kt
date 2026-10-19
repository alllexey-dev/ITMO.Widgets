package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookSamples

// Предмет at the design lab's axes; the names are `RecordbookSubjectScreen_<tab>-<case>`.

@Composable
private fun SubjectPreview(state: RecordbookSubjectUiState) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.background)) { RecordbookSubjectScreen(state, RecordbookSubjectActions()) }
}

internal class SubjectScoresBarsPreview {
    @Preview(name = "scores-bars")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.SCORES, scores = "bars"))
}

internal class SubjectScoresPlainPreview {
    @Preview(name = "scores-plain")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.SCORES, scores = "plain"))
}

internal class SubjectScoresSheetPreview {
    @Preview(name = "scores-sheet")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.SCORES, scores = "sheet"))
}

internal class SubjectLessonsSummaryPreview {
    @Preview(name = "lessons-summary")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.LESSONS, reviews = "summary"))
}

internal class SubjectLessonsFewPreview {
    @Preview(name = "lessons-few")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.LESSONS, reviews = "few"))
}

internal class SubjectLessonsOffPreview {
    @Preview(name = "lessons-off")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.LESSONS, reviews = "off"))
}

internal class SubjectMaterialsLinksPreview {
    @Preview(name = "materials-links")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.MATERIALS, materials = "links"))
}

internal class SubjectMaterialsEmptyPreview {
    @Preview(name = "materials-empty")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.MATERIALS, materials = "empty"))
}

internal class SubjectMaterialsOffPreview {
    @Preview(name = "materials-off")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.subject(SubjectTab.MATERIALS, materials = "off"))
}

internal class SubjectErrorPreview {
    @Preview(name = "error")
    @Composable
    fun RecordbookSubjectScreen() = SubjectPreview(RecordbookSamples.SubjectFailed)
}
