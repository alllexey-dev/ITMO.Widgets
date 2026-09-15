package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import api.bars.Bars
import api.bars.storage.BarsStorage
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Access headers are never persisted unencrypted or included in a data-class toString. */
interface BarsTokenPersistence {
    fun read(): String?
    fun write(value: String?)
}

/** Encrypted, owner-bound BARS session; a header saved for one ISU is never handed to another. */
class BarsTokenStore(private val file: BarsTokenPersistence, private val cipher: TokenCipher) {
    constructor(file: File, cipher: TokenCipher) : this(object : BarsTokenPersistence {
        private val atomic = AtomicTextFile(file)
        override fun read() = atomic.read()
        override fun write(value: String?) = atomic.write(value)
    }, cipher)

    @Synchronized fun load(owner: Int): String? = runCatching {
        val parts = cipher.decrypt(file.read() ?: "").split('\n', limit = 2)
        parts.getOrNull(1)?.takeIf { parts[0] == owner.toString() && Bars.isValidAuthorization(it) }
    }.getOrNull()

    @Synchronized fun install(owner: Int, header: String) {
        require(Bars.isValidAuthorization(header))
        file.write(cipher.encrypt("$owner\n$header"))
    }

    @Synchronized fun clear() = file.write(null)
}

/** Library-facing view of the store: the client sets the owner before any BARS work. */
@Singleton
class OwnerBoundBarsStorage @Inject constructor(private val tokens: BarsTokenStore) : BarsStorage {
    @Volatile var owner: Int? = null

    override fun getAuthorization(): String? = owner?.let(tokens::load)

    override fun setAuthorization(authorization: String?) {
        val current = owner ?: return
        if (authorization == null) tokens.clear() else tokens.install(current, authorization)
    }

    fun clear() = tokens.clear()
}
