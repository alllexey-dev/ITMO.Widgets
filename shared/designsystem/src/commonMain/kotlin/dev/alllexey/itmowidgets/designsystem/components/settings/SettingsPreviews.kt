package dev.alllexey.itmowidgets.designsystem.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_content_copy
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
private fun SettingsGroupCompactPreview() = ItmoPreview {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group),
    ) {
        SettingsGroup(
            title = "Расписание",
            footer = { SettingsGroupFooter("Настройки применяются ко всем виджетам расписания.") },
        ) {
            row("teacher") {
                SettingsToggleRow(
                    "Фильтр по преподавателю",
                    checked = true,
                    onCheckedChange = {},
                    description = "Показывать выбор преподавателя на экране записи.",
                )
            }
            row("loading") { SettingsToggleRow("Подключение к ITMO.Widgets", checked = null, onCheckedChange = {}) }
            row("theme") { SettingsChoiceRow("Тема", value = "Как в системе", onClick = {}) }
        }
        SettingsGroup {
            row("qr") { SettingsNavigationRow("QR-код", onClick = {}, description = "Спойлер, динамические цвета") }
            row("copy") {
                SettingsActionRow(
                    "Скопировать ID",
                    onClick = {},
                    trailingIcon = painterResource(Res.drawable.ic_content_copy),
                )
            }
            row("version") { SettingsInfoRow("Версия", value = "2.3") }
        }
    }
}

@Preview
@Composable
private fun SettingsGroupDefaultPreview() = ItmoPreview {
    Column(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
        SettingsGroup(title = "Профиль", density = SettingsDensity.Default) {
            row { SettingsNavigationRow("Друзья", onClick = {}, value = "12") }
            row { SettingsToggleRow("Показывать расписание", checked = false, onCheckedChange = {}) }
            row { SettingsInfoRow("Группа", value = "M3207") }
        }
    }
}

@Preview
@Composable
private fun SettingsGroupDisabledPreview() = ItmoPreview {
    Column(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
        SettingsGroup(title = "Спорт") {
            row {
                SettingsToggleRow(
                    "Автозапись",
                    checked = true,
                    onCheckedChange = {},
                    description = "Нужно подключение к ITMO.Widgets",
                    enabled = false,
                )
            }
            row { SettingsNavigationRow("Уведомления", onClick = {}, enabled = false) }
        }
    }
}

@Preview
@Composable
private fun SettingsGroupLongTextPreview() = ItmoPreview {
    Column(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
        SettingsGroup(
            title = PreviewFixtures.LongSubjectName,
            footer = { SettingsGroupFooter(PreviewFixtures.LongSubjectName) },
        ) {
            row {
                SettingsToggleRow(
                    PreviewFixtures.LongSubjectName,
                    checked = false,
                    onCheckedChange = {},
                    description = PreviewFixtures.LongPersonName,
                )
            }
            row {
                SettingsChoiceRow(
                    PreviewFixtures.LongPersonName,
                    value = PreviewFixtures.LongSubjectName,
                    onClick = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsSelectionPreview() = ItmoPreview {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group),
    ) {
        SettingsGroup(title = "Строка в таблице") {
            row { SettingsSelectionRow("Автоматически", selected = true, onSelect = {}, description = "Строка 14") }
            row { SettingsSelectionRow(PreviewFixtures.LongPersonName, selected = false, onSelect = {}, value = "87") }
        }
        SettingsGroup(title = "Друзья") {
            row {
                SettingsSelectionRow(
                    PreviewFixtures.ShortPersonName,
                    selected = true,
                    onSelect = {},
                    mode = SelectionMode.Multiple,
                )
            }
            row {
                SettingsSelectionRow(
                    PreviewFixtures.LongPersonName,
                    selected = false,
                    onSelect = null,
                    description = "Расписание скрыто",
                    mode = SelectionMode.Multiple,
                )
            }
        }
    }
}
