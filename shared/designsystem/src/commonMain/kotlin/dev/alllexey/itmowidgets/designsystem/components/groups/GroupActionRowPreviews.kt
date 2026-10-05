package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
private fun GroupActionRowPreview() = ItmoPreview {
    Column(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
        GroupActionRow(
            "Добавить ссылку",
            painterResource(Res.drawable.ic_add),
            onClick = {},
            modifier = Modifier.connectedGroupItem(GroupPosition.Single),
        )
    }
}
