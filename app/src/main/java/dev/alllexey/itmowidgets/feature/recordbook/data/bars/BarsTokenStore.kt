package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import java.io.File

/** Access headers are never persisted unencrypted or included in a data-class toString. */
interface BarsTokenPersistence {
    fun read(): String?
    fun write(value: String?)
}

class BarsTokenStore(private val file: BarsTokenPersistence, private val cipher: TokenCipher) {
    constructor(file: File, cipher: TokenCipher) : this(object : BarsTokenPersistence {
        private val atomic = AtomicTextFile(file)
        override fun read() = atomic.read()
        override fun write(value: String?) = atomic.write(value)
    }, cipher)
    @Volatile var generation: Long = 0
        private set

    @Synchronized fun load(owner: Int): String? = runCatching {
        val parts = cipher.decrypt(file.read() ?: "").split('\n', limit = 2)
        parts.getOrNull(1)?.takeIf { parts[0] == owner.toString() && validHeader(it) }
    }.getOrNull()

    @Synchronized fun install(owner: Int, header: String) {
        require(validHeader(header))
        file.write(cipher.encrypt("$owner\n$header"))
        generation++
    }

    @Synchronized fun rotate(owner: Int, header: String, expectedGeneration: Long) {
        check(generation == expectedGeneration)
        require(validHeader(header))
        file.write(cipher.encrypt("$owner\n$header"))
    }

    @Synchronized fun clear() {
        generation++
        file.write(null)
    }

    companion object {
        fun validHeader(value: String): Boolean = value.startsWith("Bearer ") &&
            value.length in 16..16384 && value.none { it == '\r' || it == '\n' }
    }
}
