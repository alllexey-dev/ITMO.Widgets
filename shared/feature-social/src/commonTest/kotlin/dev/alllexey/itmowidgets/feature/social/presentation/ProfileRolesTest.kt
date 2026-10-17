package dev.alllexey.itmowidgets.feature.social.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileRolesTest {

    @Test
    fun rolesDropTheirQualificationsAndStartWithACapital() {
        assertEquals("Доцент", shortRole("доцент"))
        assertEquals("Старший преподаватель", shortRole("старший преподаватель, кафедра физики"))
        assertEquals("Преподаватель", shortRole("преподаватель (квалификационная категория)"))
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
