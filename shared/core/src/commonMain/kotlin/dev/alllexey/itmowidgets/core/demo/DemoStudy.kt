package dev.alllexey.itmowidgets.core.demo

/**
 * A subject of Anna's autumn semester. The schedule, the recordbook, the subject links and the reviews all use these
 * names and ids, so the demo screens agree with each other: [id] is both My ITMO's schedule `subject_id` and the
 * recordbook `discipline_id`.
 */
data class DemoSubject(val id: Long, val name: String, val teacher: DemoPerson, val flow: String) {
    /** My ITMO's schedule flow of this subject's lessons of [typeId]: 1 lectures, 2 labs, 3 practice. */
    fun flowId(typeId: Int): Long = id * 10 + typeId
}

object DemoStudy {

    const val PROGRAM_ID = 990_101L
    const val PROGRAM_NAME = "Мобильные и сетевые технологии"

    val MATH = DemoSubject(990_201, "Математический анализ", DemoPeople.MATH_TEACHER, "МАТ АН")
    val DISCRETE = DemoSubject(990_202, "Дискретная математика", DemoPeople.DISCRETE_TEACHER, "ДИСКР МАТ")
    val ALGORITHMS = DemoSubject(990_203, "Алгоритмы и структуры данных", DemoPeople.ALGORITHMS_TEACHER, "АИСД")
    val DATABASES = DemoSubject(990_204, "Базы данных", DemoPeople.DATABASES_TEACHER, "БД")
    val ENGLISH = DemoSubject(990_205, "Английский язык", DemoPeople.ENGLISH_TEACHER, "АНГЛ B2")
    val PHYSICAL_EDUCATION = DemoSubject(
        990_206, "Физическая культура и спорт (элективная)", DemoPeople.FITNESS_COACH, "ФК"
    )

    /** The faculty's short name in schedule flow names, e.g. «АИСД ФИКТ 2.1». */
    const val FLOW_FACULTY = "ФИКТ"
    const val LECTURE_STREAM = "2"
    const val PRACTICE_GROUP = "2.1"

    /** The stream's sheet of points for algorithms: a link of the subject and the recordbook's own total. */
    const val ALGORITHMS_SCORES_SHEET = "https://docs.google.com/spreadsheets/d/1qD8m3VbXkT0ZsLw9aE2rPnYcU4hJ6fGx7yN5oKiR2Ac/edit"

    val CURRENT = listOf(MATH, DISCRETE, ALGORITHMS, DATABASES, ENGLISH, PHYSICAL_EDUCATION)
}
