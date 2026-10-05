package dev.alllexey.itmowidgets.core.demo

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.session.CurrentUser

/** A fictional person of the demo set; ISUs live in the 999xxx range no real student has. Students have a [group]. */
data class DemoPerson(
    val isu: Int,
    val name: String,
    val group: String? = null
) {
    /** The faculty a group letter belongs to: K — ФИКТ, M — ФИТиП. */
    val faculty: String? get() = group?.let { if (it.startsWith("K")) "ФИКТ" else "ФИТиП" }

    /** The identity Backend would return; every demo student shares everything with friends. */
    fun summary(sharing: UserSharing = UserSharing(sport = true, schedule = true, friends = true)) = UserSummary(
        isu = isu,
        name = name,
        pictureUrl = null,
        groups = listOfNotNull(group?.let { UserGroup(it, DemoPeople.ME_COURSE, faculty.orEmpty()) }),
        sharing = sharing
    )
}

/** The people of the demo session, shared by every feature's demo data. */
object DemoPeople {

    const val ME_ISU = 999001
    const val ME_NAME = "Анна Смирнова"
    const val ME_GROUP = "K3221"
    const val ME_COURSE = 2

    val ME = CurrentUser(isu = ME_ISU, name = ME_NAME, pictureUrl = null)
    val ME_PERSON = DemoPerson(ME_ISU, ME_NAME, ME_GROUP)

    val IVAN = DemoPerson(999002, "Иван Кузнецов", ME_GROUP)
    val MARIA = DemoPerson(999003, "Мария Волкова", ME_GROUP)
    val DMITRY = DemoPerson(999004, "Дмитрий Орлов", "M3205")
    val POLINA = DemoPerson(999005, "Полина Егорова", "K3220")

    /** Sent Anna a friend request. */
    val SOFIA = DemoPerson(999006, "София Лебедева", "K3222")

    /** Anna sent him a friend request. */
    val ARTEM = DemoPerson(999007, "Артём Новиков", "M3205")

    val FRIENDS = listOf(IVAN, MARIA, DMITRY, POLINA)

    val MATH_TEACHER = DemoPerson(999101, "Корнилов Андрей Викторович")
    val DISCRETE_TEACHER = DemoPerson(999102, "Белова Ирина Александровна")
    val ALGORITHMS_TEACHER = DemoPerson(999103, "Григорьев Павел Олегович")
    val DATABASES_TEACHER = DemoPerson(999104, "Соколова Елена Дмитриевна")
    val ENGLISH_TEACHER = DemoPerson(999105, "Миронова Ольга Сергеевна")
    val SWIMMING_COACH = DemoPerson(999106, "Захаров Николай Иванович")
    val VOLLEYBALL_COACH = DemoPerson(999107, "Тихонова Марина Юрьевна")
    val TABLE_TENNIS_COACH = DemoPerson(999108, "Фёдоров Игорь Михайлович")
    val FITNESS_COACH = DemoPerson(999109, "Васильева Анастасия Романовна")
    val HISTORY_TEACHER = DemoPerson(999110, "Зайцева Татьяна Николаевна")

    val TEACHERS = listOf(
        MATH_TEACHER,
        DISCRETE_TEACHER,
        ALGORITHMS_TEACHER,
        DATABASES_TEACHER,
        ENGLISH_TEACHER,
        SWIMMING_COACH,
        VOLLEYBALL_COACH,
        TABLE_TENNIS_COACH,
        FITNESS_COACH,
        HISTORY_TEACHER
    )

    val EVERYONE = listOf(ME_PERSON) + FRIENDS + listOf(SOFIA, ARTEM) + TEACHERS

    fun byIsu(isu: Int): DemoPerson? = EVERYONE.firstOrNull { it.isu == isu }
}
