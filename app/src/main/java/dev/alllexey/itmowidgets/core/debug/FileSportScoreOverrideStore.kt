package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import java.io.File

class FileSportScoreOverrideStore(file: File) : SportScoreOverrideStore {

    private val storage = AtomicTextFile(file)

    override fun getOverride(): SportScoreOverride? {
        if (!BuildConfig.DEBUG) return null

        return runCatching {
            val (attendances, bonus) = storage.read()
                ?.split(SEPARATOR, limit = 2)
                ?: return null
            SportScoreOverride(
                attendances = attendances.toInt(),
                bonus = bonus.toInt()
            )
        }.getOrNull()
    }

    override fun setOverride(value: SportScoreOverride?) {
        if (!BuildConfig.DEBUG) return

        storage.write(
            value?.let { "${it.attendances}$SEPARATOR${it.bonus}" }
        )
    }

    private companion object {
        const val SEPARATOR = ","
    }
}
