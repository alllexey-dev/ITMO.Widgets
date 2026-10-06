package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState

/** Nothing set, every selector shown: the section label inside its field, the default toggles on. */
@Preview(name = "none-set")
@Composable
private fun SportSignFiltersNoneSetPreview() = Filters(SportSignFiltersSamples.noneSet())

/** Every filter set and every toggle flipped: the reset chip at the end of the row. */
@Preview(name = "all-set")
@Composable
private fun SportSignFiltersAllSetPreview() = Filters(SportSignFiltersSamples.allSet())

/** Today's default display options: no teacher and no time selector. */
@Preview(name = "hidden-selectors")
@Composable
private fun SportSignFiltersHiddenSelectorsPreview() = Filters(SportSignFiltersSamples.hiddenSelectors())

/** Long section, building and teacher names wrap inside their fields. */
@Preview(name = "long-names")
@Composable
private fun SportSignFiltersLongNamesPreview() = Filters(SportSignFiltersSamples.longNames())

@Composable
private fun Filters(state: SportSignUiState.Content) = ItmoPreview {
    SportSignFilters(state, SportSignFilterActions(), Modifier.padding(vertical = ItmoTheme.spacing.group))
}
