package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFactsTest {

    @Test
    fun `a position without a title uses its department without repeating the detail`() {
        val person = samplePerson(5).copy(positions = listOf(PersonPosition(null, "Подразделение")))

        val facts = profileFacts(person, null)

        assertEquals(listOf(ProfileFact(ProfileFactKind.POSITION, "Подразделение", null, null)), facts)
    }

    @Test
    fun `a named position retains its department as the detail`() {
        val person = samplePerson(5).copy(positions = listOf(PersonPosition("Преподаватель", "Подразделение")))

        val facts = profileFacts(person, null)

        assertEquals(listOf(ProfileFact(ProfileFactKind.POSITION, "Преподаватель", "Подразделение", null)), facts)
    }

    @Test
    fun `education without a group uses its faculty without repeating the detail`() {
        val person = samplePerson(5).copy(education = listOf(PersonEducation(null, null, "Факультет")))

        val facts = profileFacts(person, null)

        assertEquals(listOf(ProfileFact(ProfileFactKind.EDUCATION, "Факультет", null, null)), facts)
    }

    @Test
    fun `education with a group retains its faculty and course`() {
        val person = samplePerson(5).copy(education = listOf(PersonEducation("M3200", 2, "Факультет")))

        val facts = profileFacts(person, null)

        assertEquals(listOf(ProfileFact(ProfileFactKind.EDUCATION, "M3200", "Факультет", 2)), facts)
    }

    @Test
    fun `a missing person uses one education fact from the backend group`() {
        val group = UserGroup("M3100", 1, "ФИТиП")

        val facts = profileFacts(null, group)

        assertEquals(listOf(ProfileFact(ProfileFactKind.EDUCATION, "M3100", "ФИТиП", 1)), facts)
    }

    @Test
    fun `a missing person and group have no facts`() {
        val facts = profileFacts(null, null)

        assertEquals(emptyList<ProfileFact>(), facts)
    }

    @Test
    fun `the headline is the first position with a short department`() {
        val person = samplePerson(5).copy(positions = listOf(
            PersonPosition("Доцент", "Факультет информационных технологий и программирования, кафедра прикладной математики"),
            PersonPosition("Ассистент", "Кафедра"),
        ))

        assertEquals(ProfileHeadline.Position("Доцент", "ФИТиП"), profileHeadline(person, UserGroup("M3100", 1, "ФИТиП")))
    }

    @Test
    fun `without positions the headline is the student group`() {
        val person = samplePerson(5).copy(education = listOf(PersonEducation(null, null, "Факультет"), PersonEducation("M3200", 2, null)))

        assertEquals(ProfileHeadline.Group("M3200", 2), profileHeadline(person, null))
        assertEquals(ProfileHeadline.Group("M3100", 1), profileHeadline(null, UserGroup("M3100", 1, "ФИТиП")))
        assertEquals(null, profileHeadline(samplePerson(5), null))
    }

    @Test
    fun `departments are shortened to their initials or a given abbreviation`() {
        assertEquals("ФИТиП", shortDepartment("Факультет информационных технологий и программирования"))
        assertEquals("ИМРиП", shortDepartment("Институт международного развития и партнёрства"))
        assertEquals("НОЦИКТ", shortDepartment("Научно-образовательный центр ИКТ"))
        assertEquals("ФПИиКТ", shortDepartment("Факультет программной инженерии и компьютерной техники (ФПИиКТ)"))
        assertEquals("Кафедра физики", shortDepartment("Кафедра физики"))
        assertEquals("ЦРОД", shortDepartment("Центр по работе с «Одарёнными детьми»"))
    }

    @Test
    fun `the ISU fact shows the number`() {
        assertEquals(ProfileFact(ProfileFactKind.ISU, "123456", null, null), isuFact(123456))
    }
}
