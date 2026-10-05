package dev.alllexey.itmowidgets.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every argument class survives the kotlinx JSON string the sheets and, later, the Nav3 keys carry. */
class NavigationArgsJsonTest {

    @Test fun `lesson details with every field and with only the required ones`() {
        assertRoundTrip(
            LessonDetailsArgs(
                pairId = 42, date = "2026-10-06", subjectName = "Алгоритмы \"и\" структуры", typeId = 2,
                format = "очно", start = "10:00", end = "11:30", teacherFio = "Иванов Иван", teacherIsu = 123456,
                room = "1404", building = "Кронверкский пр., 49", buildingId = 1, mainBuildingId = 1, note = "a\nb",
                zoomUrl = "https://zoom.us/j/1?pwd=x&y=1", zoomPassword = "p", zoomInfo = "i",
                flowName = "ФИЗ ПИИКТ 3.2",
            ),
        )
        assertRoundTrip(
            LessonDetailsArgs(
                pairId = 1, date = "2026-10-06", subjectName = "s", typeId = 0, format = "", start = "", end = "",
                teacherFio = null, teacherIsu = null, room = null, building = null, buildingId = null,
                mainBuildingId = null, note = null, zoomUrl = null, zoomPassword = null, zoomInfo = null,
            ),
        )
    }

    @Test fun `pending sport details with and without a teacher ISU`() {
        val args = PendingSportDetailsArgs(
            lessonId = Long.MAX_VALUE, sectionName = "Плавание", autoSign = true, isPrediction = false,
            start = "2026-10-06T10:00+03:00", end = "2026-10-06T11:30+03:00", teacherFio = "Петров",
            roomName = "Бассейн", teacherIsu = 654321,
        )
        assertRoundTrip(args)
        assertRoundTrip(args.copy(autoSign = false, isPrediction = true, teacherIsu = null))
    }

    @Test fun `recordbook subject with and without a BARS journal`() {
        assertRoundTrip(RecordbookSubjectArgs(11, 1, 3, "2026/2027", 7, "flow", "7"))
        assertRoundTrip(RecordbookSubjectArgs(11, 1, 3, "2026/2027"))
    }

    @Test fun `sheet scores in both steps`() {
        val url = "https://docs.google.com/spreadsheets/d/x"
        val args = SheetScoresArgs(5, "Матан", "2026/2027:1", url, SheetScoresArgs.Step.CONNECT)
        assertRoundTrip(args)
        assertRoundTrip(args.copy(step = SheetScoresArgs.Step.TOTAL))
    }

    @Test fun `subject links and teacher review`() {
        assertRoundTrip(SubjectLinksArgs(5, "Матан", "2026/2027:1"))
        assertRoundTrip(TeacherReviewArgs(Int.MAX_VALUE, "Сидорова Анна Петровна"))
    }

    private inline fun <reified T> assertRoundTrip(args: T) {
        assertEquals(args, decodeNavigationArgs<T>(encodeNavigationArgs(args)))
    }
}
