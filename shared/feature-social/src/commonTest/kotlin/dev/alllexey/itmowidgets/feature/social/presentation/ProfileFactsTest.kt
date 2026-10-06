package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileFactsTest {

    @Test
    fun aPositionWithoutATitleUsesItsDepartmentWithoutRepeatingTheDetail() {
        val person = samplePerson(5).copy(positions = listOf(PersonPosition(null, "Подразделение")))

        val facts = profileFacts(person, null)

        assertEquals(listOf(ProfileFact(ProfileFactKind.POSITION, "Подразделение", null, null)), facts)
    }

    @Test
    fun aNamedPositionRetainsItsDepartmentAsTheDetail() {
        val person = samplePerson(5).copy(positions = listOf(PersonPosition("Преподаватель", "Подразделение")))

        val facts = profileFacts(person, null)

        assertEquals(listOf(ProfileFact(ProfileFactKind.POSITION, "Преподаватель", "Подразделение", null)), facts)
    }

    @Test
    fun educationWithoutAGroupUsesItsFacultyWithoutRepeatingTheDetail() {
        val person = samplePerson(5).copy(education = listOf(PersonEducation(null, null, "Факультет")))

        val facts = profileFacts(person, null)

        assertEquals(listOf(ProfileFact(ProfileFactKind.EDUCATION, "Факультет", null, null)), facts)
    }

    @Test
    fun educationWithAGroupRetainsItsFacultyAndCourse() {
        val person = samplePerson(5).copy(education = listOf(PersonEducation("M3200", 2, "Факультет")))

        val facts = profileFacts(person, null)

        assertEquals(listOf(ProfileFact(ProfileFactKind.EDUCATION, "M3200", "Факультет", 2)), facts)
    }

    @Test
    fun aMissingPersonUsesOneEducationFactFromTheBackendGroup() {
        val group = UserGroup("M3100", 1, "ФИТиП")

        val facts = profileFacts(null, group)

        assertEquals(listOf(ProfileFact(ProfileFactKind.EDUCATION, "M3100", "ФИТиП", 1)), facts)
    }

    @Test
    fun aMissingPersonAndGroupHaveNoFacts() {
        val facts = profileFacts(null, null)

        assertEquals(emptyList<ProfileFact>(), facts)
    }

    @Test
    fun theHeadlineIsTheShortRoleOfTheFirstPosition() {
        val person = samplePerson(5).copy(positions = listOf(
            PersonPosition("преподаватель (квалификационная категория \"преподаватель иностранного языка\")",
                "Центр изучения иностранных языков"),
            PersonPosition("Ассистент", "Кафедра"),
        ))

        assertEquals(ProfileHeadline.Position("Преподаватель"), profileHeadline(person, UserGroup("M3100", 1, "ФИТиП")))
    }

    @Test
    fun aPositionWithoutATitleIsHeadedByItsShortDepartment() {
        val person = samplePerson(5).copy(positions = listOf(
            PersonPosition(null, "Факультет информационных технологий и программирования, кафедра прикладной математики"),
        ))

        assertEquals(ProfileHeadline.Position("ФИТиП"), profileHeadline(person, null))
    }

    @Test
    fun rolesDropTheirQualificationsAndStartWithACapital() {
        assertEquals("Доцент", shortRole("доцент"))
        assertEquals("Старший преподаватель", shortRole("старший преподаватель, кафедра физики"))
        assertEquals("Преподаватель", shortRole("преподаватель (квалификационная категория)"))
    }

    @Test
    fun withoutPositionsTheHeadlineIsTheStudentGroup() {
        val person = samplePerson(5).copy(education = listOf(PersonEducation(null, null, "Факультет"), PersonEducation("M3200", 2, null)))

        assertEquals(ProfileHeadline.Group("M3200", 2), profileHeadline(person, null))
        assertEquals(ProfileHeadline.Group("M3100", 1), profileHeadline(null, UserGroup("M3100", 1, "ФИТиП")))
        assertEquals(null, profileHeadline(samplePerson(5), null))
    }

    @Test
    fun departmentsAreShortenedToTheirInitialsOrAGivenAbbreviation() {
        assertEquals("ФИТиП", shortDepartment("Факультет информационных технологий и программирования"))
        assertEquals("ИМРиП", shortDepartment("Институт международного развития и партнёрства"))
        assertEquals("НОЦИКТ", shortDepartment("Научно-образовательный центр ИКТ"))
        assertEquals("ФПИиКТ", shortDepartment("Факультет программной инженерии и компьютерной техники (ФПИиКТ)"))
        assertEquals("Кафедра физики", shortDepartment("Кафедра физики"))
        assertEquals("ЦРОД", shortDepartment("Центр по работе с «Одарёнными детьми»"))
    }
}
