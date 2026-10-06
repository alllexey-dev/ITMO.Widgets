package dev.alllexey.itmowidgets.core.time

import kotlinx.datetime.LocalDate

/** The academic date override is a debug tool, and debug tools stay Android-only: iOS always runs on the real date. */
object NoAcademicTimeOverride : AcademicTimeOverrideStore {

    override fun getOverrideDate(): LocalDate? = null

    override fun setOverrideDate(date: LocalDate?) = Unit
}
