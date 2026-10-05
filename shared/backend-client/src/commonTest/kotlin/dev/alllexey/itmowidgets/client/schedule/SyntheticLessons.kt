package dev.alllexey.itmowidgets.client.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Synthetic lessons for the schedule tests; the two lessons equal Backend's vendored `LessonSyncRequest`. */
object SyntheticLessons {
    val day = LocalDate(2026, 10, 6)
    val from = LocalDate(2026, 10, 5)
    val to = LocalDate(2026, 10, 11)

    /** Every optional field set. */
    val lecture = LessonDto(
        pairId = 3000001,
        date = day,
        start = LocalTime(8, 20),
        end = LocalTime(9, 50),
        type = "Лекции",
        typeId = 1,
        note = "Принести ноутбук",
        subjectName = "Математический анализ",
        subjectId = 501,
        groupName = "ЛЕК МАТАН 3.1",
        flowId = 7001,
        flowTypeId = 2,
        teacherIsu = 200001,
        teacherFio = "Преподаватель Тестовый",
        room = "1404",
        building = "Кронверкский пр., д.49",
        buildingId = 13,
        mainBuildingId = 13,
        format = "Очно",
        formatId = 1,
    )

    /** Every nullable field `null`. */
    val practice = LessonDto(
        pairId = 3000002,
        date = day,
        start = LocalTime(10, 0),
        end = LocalTime(11, 30),
        type = "Практические занятия",
        typeId = 3,
        note = null,
        subjectName = "Программирование",
        subjectId = 502,
        groupName = "ПРАК ПРОГ 3.1.2",
        flowId = 7002,
        flowTypeId = 2,
        teacherIsu = null,
        teacherFio = null,
        room = null,
        building = null,
        buildingId = null,
        mainBuildingId = null,
        format = "Дистанционно",
        formatId = 3,
    )

    /** The vendored `requests/LessonSyncRequest.json`. */
    val request = LessonSyncRequest(listOf(lecture, practice), from, to)

    /** [practice] as Backend's Jackson writes it: `HH:mm:ss` times and explicit nulls. */
    const val PRACTICE_JSON = """{"pairId":3000002,"date":"2026-10-06","start":"10:00:00","end":"11:30:00",
        "type":"Практические занятия","typeId":3,"note":null,"subjectName":"Программирование","subjectId":502,
        "groupName":"ПРАК ПРОГ 3.1.2","flowId":7002,"flowTypeId":2,"teacherIsu":null,"teacherFio":null,
        "room":null,"building":null,"buildingId":null,"mainBuildingId":null,"format":"Дистанционно","formatId":3}"""
}
