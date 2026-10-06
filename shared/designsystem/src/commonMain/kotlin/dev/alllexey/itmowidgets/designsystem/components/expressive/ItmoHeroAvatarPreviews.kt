package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.ProvideItmoExpressive

/** The switch off: the round avatar with initials. */
@Preview
@Composable
private fun ItmoHeroAvatarStandardPreview() = ItmoPreview { HeroAvatar() }

/** The switch on: the same initials under the nine-sided cookie mask. */
@Preview
@Composable
private fun ItmoHeroAvatarExpressivePreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) { HeroAvatar() }
}

@Composable
private fun HeroAvatar() {
    ItmoHeroAvatar(
        name = PreviewFixtures.LongPersonName,
        pictureUrl = null,
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin),
    )
}
