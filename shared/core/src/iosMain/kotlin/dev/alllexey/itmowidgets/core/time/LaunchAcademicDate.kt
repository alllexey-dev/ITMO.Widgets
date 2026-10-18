package dev.alllexey.itmowidgets.core.time

import kotlinx.datetime.LocalDate

/**
 * The academic date a Debug build's launch argument `-itmoAcademicDate` pins, or null for the real date. iOS has no
 * debug tools screen, so nothing sets it at runtime.
 */
class LaunchAcademicDate(private val date: LocalDate?) : AcademicTimeOverrideStore {

    override fun getOverrideDate(): LocalDate? = date

    override fun setOverrideDate(date: LocalDate?) = Unit
}
