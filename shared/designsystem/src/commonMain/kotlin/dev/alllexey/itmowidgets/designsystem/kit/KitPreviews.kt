package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.designsystem.ic_notification
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code
import dev.alllexey.itmowidgets.shared.designsystem.ic_settings
import dev.alllexey.itmowidgets.shared.designsystem.ic_videocam
import dev.alllexey.itmowidgets.shared.designsystem.ic_widgets
import org.jetbrains.compose.resources.painterResource

// The Material 3 Expressive kit's previews: synthetic data, captured in the four Material appearances.

/** Every tone as a pill and a tile, and the solid `идёт`. */
@Preview
@Composable
private fun KitTonesPreview() = KitColumn {
    Tone.entries.forEach { tone ->
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(painterResource(Res.drawable.ic_notification), tone)
            StatusPill(tone.name.lowercase(), tone = tone)
        }
    }
    StatusPill(PillSpec("идёт", Tone.Primary, solid = true))
    StatusPill("онлайн", icon = painterResource(Res.drawable.ic_videocam))
}

/** A card, a tonal card and the one hero of a screen. */
@Preview
@Composable
private fun ItmoCardPreview() = KitColumn {
    ItmoCard(tone = Tone.Primary, hero = true) {
        Text("1506", style = ItmoTheme.typography.displaySmall)
        Text(PreviewFixtures.LongSubjectName, style = ItmoTheme.typography.titleMedium)
    }
    ItmoCard { Text("Одна тема на карточку", style = ItmoTheme.typography.bodyLarge) }
    ItmoCard(tone = Tone.Warning) { Text("Аудиторию перенесли", style = ItmoTheme.typography.bodyLarge) }
}

/** A gate with its fix, a card worth acting on, a lock without a fix and stale data under its date. */
@Preview
@Composable
private fun KitNoticesPreview() = KitColumn {
    GateCard("Очередь через подключение", "Очередь хранит сервер ITMO.Widgets.", "Подключить", onFix = {})
    ActionCard(
        "Добавьте ссылку на курс",
        "Однокурсники увидят её в предмете.",
        "Добавить ссылку",
        onAction = {},
        icon = painterResource(Res.drawable.ic_link),
    )
    LockNote("Расписание скрыто", "Подключение выключено на этом телефоне.")
    StaleContent("Данные на 11:44") { LessonCard(Lessons.next) }
}

/** Two, three and four options, and a choice that cannot change now. */
@Preview
@Composable
private fun SegmentedChoicePreview() = KitColumn {
    SegmentedChoice(listOf("Моё", "Запись"), 0, onSelect = {})
    SegmentedChoice(listOf("Баллы", "Пары", "Материалы"), 1, onSelect = {})
    SegmentedChoice(listOf("Пн", "Вт", "Ср", "Чт"), 3, onSelect = {})
    SegmentedChoice(listOf("Семестр", "Сессия"), 0, onSelect = {}, enabled = false)
}

/** Rows with tiles, an avatar, a value, a count, a check, a switch and a destructive row. */
@Preview
@Composable
private fun ListGroupPreview() = KitColumn {
    ListGroup {
        item {
            ListRow(
                "Друзья",
                subtitle = "2 заявки",
                icon = painterResource(Res.drawable.ic_group),
                iconTone = Tone.Primary,
                badge = 2,
                chevron = true,
                onClick = {},
            )
        }
        item {
            ListRow(
                PreviewFixtures.LongPersonName,
                subtitle = "M3205",
                leading = { Avatar(PreviewFixtures.LongPersonName, pictureUrl = null) },
                chevron = true,
                onClick = {},
            )
        }
        item { ListRow("Подключение к ITMO.Widgets", icon = painterResource(Res.drawable.ic_settings), trailing = "Включено") }
        item { ListRow("Удалить аккаунт", icon = painterResource(Res.drawable.ic_settings), danger = true, onClick = {}) }
    }
    ListGroupNote("Сервер хранит только то, что нужно друзьям.")
    ListGroup {
        item {
            SwitchListRow(
                "Баллы",
                checked = true,
                onCheckedChange = {},
                subtitle = "Матанализ: 9 из 10",
                icon = painterResource(Res.drawable.ic_widgets),
                iconTone = Tone.Tertiary,
            )
        }
        item { SwitchListRow("Тихие часы", checked = false, onCheckedChange = {}, enabled = false) }
    }
}

/** The lesson card in each state: now, next, a change, cancelled, done, a sport booking online. */
@Preview
@Composable
private fun LessonCardPreview() = KitColumn {
    LessonCard(Lessons.now)
    LessonCard(Lessons.next)
    LessonCard(Lessons.changed)
    LessonCard(Lessons.cancelled)
    LessonCard(Lessons.done)
    LessonCard(Lessons.online)
}

/** Today at 12:22: a done lesson, the current one with the marker, a window, the rest of the day. */
@Preview
@Composable
private fun LessonTimelinePreview() = KitColumn {
    LessonTimeline(
        listOf(
            TimelineRow.Lesson(Lessons.done, progress = 1f, reached = true),
            TimelineRow.Lesson(Lessons.now, progress = 0.4f, reached = true),
            TimelineRow.Gap("Окно 2 ч 10 мин", progress = 0f),
            TimelineRow.Lesson(Lessons.changed, progress = 0f, reached = false, changeNote = "Изменено вчера в 20:14"),
            TimelineRow.Lesson(Lessons.cancelled, progress = 0f, reached = false),
            TimelineRow.Lesson(Lessons.online, progress = 0f, reached = false),
        ),
        showNow = true,
    )
}

/** Another day: no marker, nothing elapsed. */
@Preview
@Composable
private fun LessonTimelineOtherDayPreview() = KitColumn {
    LessonTimeline(
        listOf(
            TimelineRow.Lesson(Lessons.next.copy(state = LessonState.Upcoming, pills = emptyList()), 0f, reached = false),
            TimelineRow.Lesson(Lessons.online, progress = 0f, reached = false),
        ),
    )
}

/** The root header expanded, the bell in its row, the pass button. */
@Preview
@Composable
private fun ItmoRootScreenPreview() = ItmoPreview {
    ItmoRootScreen(
        title = "Сегодня",
        subtitle = "понедельник, 7 сентября",
        actions = { HeaderAction(painterResource(Res.drawable.ic_notification), "Уведомления", onClick = {}, badge = 3) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {},
                icon = { Icon(painterResource(Res.drawable.ic_qr_code), null) },
                text = { Text("Пропуск") },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = ItmoTheme.spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        ) {
            LessonCard(Lessons.now)
            LessonCard(Lessons.next)
        }
    }
}

/** A pushed screen: back, a two-line title over its subtitle. */
@Preview
@Composable
private fun ItmoDetailScreenPreview() = ItmoPreview {
    ItmoDetailScreen(
        title = PreviewFixtures.LongSubjectName,
        onBack = {},
        backLabel = "Назад",
        subtitle = "Экзамен, 3 семестр",
    ) { padding ->
        Column(Modifier.padding(padding).padding(ItmoTheme.spacing.screenMargin)) {
            SegmentedChoice(listOf("Баллы", "Пары", "Материалы"), 0, onSelect = {})
        }
    }
}

/** Inside a sheet the cards and groups step one surface up. */
@Preview
@Composable
private fun KitInSheetPreview() = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) {
        SheetScaffold(title = "Пара", close = SheetClose("Закрыть", onClick = {})) {
            Column(
                Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                LessonCard(Lessons.now)
                ListGroup {
                    item { ListRow("Ссылки", trailing = "3", chevron = true, onClick = {}) }
                    item { ListRow("Предмет", chevron = true, onClick = {}) }
                }
            }
        }
    }
}

@Composable
private fun KitColumn(content: @Composable ColumnScope.() -> Unit) = ItmoPreview {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        content = content,
    )
}

/** Synthetic lessons; the accents are the lesson-type colours of the extended scheme. */
private object Lessons {
    private val lecture = Color(0xFF4984E2)
    private val practice = Color(0xFFCDA634)
    private val lab = Color(0xFF8A5BE1)
    private val sport = Color(0xFF44C614)

    val done = LessonView("08:20", "09:50", "Физика", "Лекция, Смирнова Е. А.", "331, Ломо", lecture, LessonState.Done)
    val now = LessonView(
        "10:00", "11:30", PreviewFixtures.LongSubjectName, "Практика, Лебедев А. С.", "1506, Кронва", practice,
        LessonState.Now, pills = listOf(PillSpec("идёт", Tone.Primary, solid = true)),
        friends = listOf("Иван Петров", "Анна Смирнова"),
    )
    val next = LessonView(
        "11:40", "13:10", "Базы данных", "Лабораторная, Орлова Н. В.", "2304, Кронва", lab,
        LessonState.Next, pills = listOf(PillSpec("далее")),
    )
    val changed = LessonView(
        "15:20", "16:50", "Дискретная математика", "Лекция, Иванов П. П.", "1404, Кронва", lecture,
        previousPlace = "1216", pills = listOf(PillSpec("изменена", Tone.Warning)),
    )
    val cancelled = LessonView(
        "17:00", "18:30", "История", "Лекция, Кузнецов В. В.", "100, Ломо", lecture,
        cancelled = true, pills = listOf(PillSpec("отменена", Tone.Error)),
    )
    val online = LessonView(
        "18:40", "20:10", "Физкультура", "Плавание, Соколов Д. И.", null, sport,
        note = "Взять шапочку", pills = listOf(PillSpec("записаны", Tone.Success), PillSpec("онлайн")),
    )
}
