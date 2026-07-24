package dev.alllexey.itmowidgets.core.time

import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import java.io.File
import java.time.LocalDate

class FileAcademicTimeOverrideStore(file: File) : AcademicTimeOverrideStore {

    private val storage = AtomicTextFile(file)

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
