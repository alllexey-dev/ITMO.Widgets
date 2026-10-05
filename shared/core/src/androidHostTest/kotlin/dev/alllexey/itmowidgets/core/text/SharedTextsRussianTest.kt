package dev.alllexey.itmowidgets.core.text

import android.app.Application
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The common builders resolve to the Russian text the `Context` versions in `:app` wrote before the port, character
 * for character, so notifications, widgets and Views read the same after it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SharedTextsRussianTest {

    @Test
    fun `added and cancelled lessons`() {
        val added = scheduleChange(kind = ScheduleChangeKind.ADDED)
        val cancelled = scheduleChange(kind = ScheduleChangeKind.CANCELLED, subject = " ")

        assertEquals("Добавлена: ср, 9 сентября, 08:20", added.summary().text())
        assertEquals("Математический анализ — добавлена: ср, 9 сентября, 08:20", added.headline().text())
        assertEquals(listOf("Добавлена: ср, 9 сентября, 08:20"), added.detailLines().texts())
        assertEquals("Отменена: вт, 8 сентября, 08:20", cancelled.listSummary().text())
        assertEquals("Предмет не указан — отменена: вт, 8 сентября, 08:20", cancelled.headline().text())
    }

    @Test
    fun `moved lessons`() {
        val acrossDays = scheduleChange()
        val sameDay = scheduleChange(before = slot(1, WED), after = slot(1, WED, start = LocalTime(10, 0)))

        assertEquals("Перенесена на ср, 9 сентября, 08:20", acrossDays.summary().text())
        assertEquals("Математический анализ — перенесена на ср, 9 сентября, 08:20", acrossDays.headline().text())
        assertEquals("Время: вт, 8 сентября, 08:20 → ср, 9 сентября, 08:20", acrossDays.listSummary().text())
        assertEquals(listOf("Время: 08:20–09:50 → 10:00–11:30"), sameDay.detailLines().texts())
    }

    @Test
    fun `format, place and teacher changes`() {
        val format = scheduleChange(fields = setOf(ScheduleChangeField.FORMAT), after = slot(1, WED, format = "Дистанционный"))
        val place = scheduleChange(
            fields = setOf(ScheduleChangeField.PLACE),
            after = slot(1, WED, room = "Актовый зал", building = "ул. Ломоносова, 9")
        )
        val noPlace = scheduleChange(fields = setOf(ScheduleChangeField.PLACE), after = slot(1, WED, room = null, building = " "))
        val teacher = scheduleChange(fields = setOf(ScheduleChangeField.TEACHER), after = slot(1, WED, teacherName = ""))

        assertEquals("Формат: Дистанционный", format.summary().text())
        assertEquals("Формат: Очный → Дистанционный", format.listSummary().text())
        assertEquals("Аудитория: Акт. зал · Ломо", place.summary().text())
        assertEquals("Аудитория: 1506 · Кронва → Акт. зал · Ломо", place.listSummary().text())
        assertEquals("Математический анализ — аудитория: Акт. зал · Ломо", place.headline().text())
        assertEquals("Аудитория: 1506 · Кронва → —", noPlace.listSummary().text())
        assertEquals("Преподаватель: —", teacher.summary().text())
        assertEquals("Математический анализ — преподаватель: —", teacher.headline().text())
    }

    @Test
    fun `several changed fields`() {
        val change = scheduleChange(
            fields = setOf(ScheduleChangeField.TEACHER, ScheduleChangeField.TIME, ScheduleChangeField.PLACE),
            after = slot(1, WED, room = "2304/1 (лаб.)", building = "Биржевая линия, 14", teacherName = "Другой преподаватель")
        )

        assertEquals("Перенесена на ср, 9 сентября, 08:20", change.listSummary().text())
        assertEquals(
            listOf(
                "Время: вт, 8 сентября, 08:20 → ср, 9 сентября, 08:20",
                "Аудитория: 1506 · Кронва → 2304/1 · Биржа",
                "Преподаватель: Тестовый преподаватель → Другой преподаватель"
            ),
            change.listLines().texts()
        )
    }

    @Test
    fun `location titles, marks and names`() {
        assertEquals("Кронва", buildingShortTitle("Кронверкский проспект, 49").text())
        assertEquals("Невский пр", buildingShortTitle("Невский проспект, 1", maxLength = 10).text())
        assertEquals("Акт. зал", roomShortTitle("Актовый зал").text())
        assertEquals("Физика, Химия, Биология", markSubjectList(listOf("Физика", "Химия", "Биология")).text())
        assertEquals(
            "Физика, Химия, Биология и ещё\u00A02",
            markSubjectList(listOf("Физика", "Химия", "Биология", "История", "Философия")).text()
        )
        assertEquals("Пользователь ИСУ 123456", userDisplayName("", 123456).text())
        assertEquals("Анна Иванова", userDisplayName(" Анна Иванова ", 123456).text())
    }

    @Test
    fun `lesson type names`() {
        val names = listOf(-1, 1, 2, 3, 4, 5, 6, 10, 11).map { UiText.Res(lessonTypeName(it)).text() }

        assertEquals(
            listOf("Нет пар", "Лекция", "Лабораторная", "Практика", "Пара", "Экзамен", "Зачёт", "Консультация", "Спорт"),
            names
        )
    }

    private fun UiText.text(): String = runBlocking { resolve() }

    private fun List<UiText>.texts(): List<String> = map { it.text() }

    private companion object {
        val WED = LocalDate(2026, 9, 9)
    }
}
