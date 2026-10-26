package dev.alllexey.itmowidgets.designsystem.components.menus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_content_copy
import dev.alllexey.itmowidgets.shared.designsystem.ic_delete
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit
import dev.alllexey.itmowidgets.shared.designsystem.ic_flag
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import org.jetbrains.compose.resources.painterResource

/** A link's actions in two groups: icons, a disabled item and a destructive one, as the open menu draws them. */
@Preview
@Composable
private fun ItmoMenuPreview() = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
        ItmoMenuPanel(
            groups = listOf(
                ItmoMenuGroup(
                    listOf(
                        ItmoMenuItem("Открыть", {}, painterResource(Res.drawable.ic_open_in_new)),
                        ItmoMenuItem("Скопировать ссылку", {}, painterResource(Res.drawable.ic_content_copy)),
                        ItmoMenuItem("Изменить", {}, painterResource(Res.drawable.ic_edit), enabled = false),
                    ),
                ),
                ItmoMenuGroup(
                    listOf(
                        ItmoMenuItem("Пожаловаться", {}, painterResource(Res.drawable.ic_flag)),
                        ItmoMenuItem(
                            "Удалить",
                            onClick = {},
                            icon = painterResource(Res.drawable.ic_delete),
                            destructive = true,
                        ),
                    ),
                ),
            ),
            onDismissRequest = {},
        )
    }
}

/** The record book's period picker: two programmes under their names, two-line items, the current one checked. */
@Preview
@Composable
private fun ItmoMenuPickerPreview() = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
        ItmoMenuPanel(
            groups = listOf(
                ItmoMenuGroup(
                    listOf(
                        ItmoMenuItem("3 семестр", {}, supportingText = "2 курс, 2026/2027", selected = true),
                        ItmoMenuItem("2 семестр", {}, supportingText = "1 курс, 2025/2026"),
                        ItmoMenuItem("1 семестр", {}, supportingText = "1 курс, 2025/2026"),
                    ),
                    label = "Программная инженерия",
                ),
                ItmoMenuGroup(
                    listOf(ItmoMenuItem("2 семестр", {}, supportingText = "1 курс, 2024/2025", enabled = false)),
                    label = "Подготовительное отделение",
                ),
            ),
            onDismissRequest = {},
        )
    }
}
