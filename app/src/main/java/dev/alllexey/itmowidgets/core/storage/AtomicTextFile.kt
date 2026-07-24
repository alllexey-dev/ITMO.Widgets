package dev.alllexey.itmowidgets.core.storage

import android.util.AtomicFile
import java.io.File

class AtomicTextFile(file: File) {

    private val atomicFile = AtomicFile(file)

    @Synchronized
    fun read(): String? {
        if (!atomicFile.baseFile.exists()) return null
        return atomicFile.openRead()
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    @Synchronized
    fun write(value: String?) {
        if (value == null) {
            atomicFile.delete()
            return
        }

        atomicFile.baseFile.parentFile?.mkdirs()
        val output = atomicFile.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }
}
