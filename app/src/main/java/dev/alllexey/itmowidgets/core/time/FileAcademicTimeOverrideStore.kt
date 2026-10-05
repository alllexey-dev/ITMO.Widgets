package dev.alllexey.itmowidgets.core.time

import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.debug.DebugOnly
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import kotlinx.datetime.LocalDate

/** The override date as ISO text (`2026-02-16`) in [storage]; debug builds only. */
@DebugOnly
class FileAcademicTimeOverrideStore(private val storage: AtomicTextFile) : AcademicTimeOverrideStore {

    override fun getOverrideDate(): LocalDate? {
        if (!BuildConfig.DEBUG) return null
        return runCatching {
            storage.read()?.let(LocalDate::parse)
        }.getOrNull()
    }

    override fun setOverrideDate(date: LocalDate?) {
        if (!BuildConfig.DEBUG) return
        storage.write(date?.toString())
    }
}
