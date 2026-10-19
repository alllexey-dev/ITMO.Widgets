package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar

/**
 * One person in a list (friends, a lesson's teacher and friends, the friend picker): a [ListRow] led by the person's
 * [Avatar] at the row's 40 dp. [name] is whose avatar it is, [title] the line shown (the name by default); the rest is
 * [ListRow]'s.
 */
@Composable
fun PersonListRow(
    title: String,
    pictureUrl: String?,
    modifier: Modifier = Modifier,
    name: String? = title,
    initials: String? = null,
    subtitle: String? = null,
    accessory: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = false,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ListRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        leading = { Avatar(name, pictureUrl, size = PersonAvatarSize, initials = initials) },
        accessory = accessory,
        chevron = chevron,
        below = below,
        onClick = onClick,
    )
}

/** The avatar of a [PersonListRow], `Skeleton.List`'s 40 dp. */
private val PersonAvatarSize = 40.dp
