package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName

/** Two sections selected (first), one signed (next), the rest by name. */
@Preview(name = "ordered")
@Composable
private fun SportSectionPickerPreview() = Picker(
    available = SportSignFiltersSamples.sections,
    selected = setOf(SportSignFiltersSamples.sections[4], SportSignFiltersSamples.sections[2]),
    used = setOf(SportSignFiltersSamples.sections[1]),
)

/** Long section names wrap in their rows. */
@Preview(name = "long-names")
@Composable
private fun SportSectionPickerLongNamesPreview() = Picker(
    available = SportSignFiltersSamples.longSections + SportSignFiltersSamples.sections.take(2),
    selected = setOf(SportSignFiltersSamples.longSections[2]),
    used = emptySet(),
)

@Composable
private fun Picker(available: List<SectionName>, selected: Set<SectionName>, used: Set<SectionName>) = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.section)) {
        SportSectionPickerSurface(available, selected, used, onConfirm = {}, onDismiss = {})
    }
}
