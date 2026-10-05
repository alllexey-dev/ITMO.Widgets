package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The person profile hero's avatar, the one place for a `MaterialShapes` mask: with [ItmoTheme.expressive] the
 * nine-sided cookie, otherwise the round [Avatar]. Photo, initials and [contentDescription] behave as in [Avatar].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoHeroAvatar(
    name: String?,
    pictureUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = HeroSize,
    contentDescription: String? = null,
) {
    val masked = if (ItmoTheme.expressive) modifier.clip(MaterialShapes.Cookie9Sided.toShape()) else modifier
    Avatar(name, pictureUrl, masked, size, contentDescription)
}

/** The profile hero's avatar size (`item_profile_header.xml`'s 72 dp). */
private val HeroSize = 72.dp
