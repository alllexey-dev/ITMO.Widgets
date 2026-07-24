package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import java.io.File

class FileSportLessonTemplateStore(file: File) : SportLessonTemplateStore {

    private val storage = AtomicTextFile(file)

    override fun isEnabled(): Boolean {
        return BuildConfig.DEBUG && runCatching {
            storage.read()?.toBooleanStrictOrNull() ?: false
        }.getOrDefault(false)
    }

    override fun setEnabled(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        storage.write(enabled.toString())
    }
}
