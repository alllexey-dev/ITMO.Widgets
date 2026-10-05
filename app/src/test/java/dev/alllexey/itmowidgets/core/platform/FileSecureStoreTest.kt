package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.testing.SecureStoreContract
import java.io.File
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Android's [SecureStore]: the contract, and the token files of 2.2 as they are. */
class FileSecureStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val directory by lazy { folder.newFolder("no_backup") }

    @Test
    fun `it keeps the SecureStore contract`() = SecureStoreContract.checkAll {
        val storage = folder.newFolder().toOkioPath()
        val opener: () -> SecureStore = { FileSecureStore(storage, SealingCipher) }
        opener
    }

    @Test
    fun `a value is stored sealed by the cipher in the file of its name`() {
        noBackupStore().write(BARS, "123456\nBearer synthetic")

        assertEquals("sealed:123456\nBearer synthetic", File(directory, BARS).readText())
    }

    @Test
    fun `the 2_2 token files read and write back byte-identically`() {
        // The capture's plaintexts, as 2.2 wrote them through an identity cipher; the Keystore envelope is
        // SecureStoreUpgradeTest's on a device.
        for (name in listOf(MY_ITMO, BARS)) {
            val captured = CAPTURED_22.resolve(name).readBytes()
            val source = folder.newFolder("source-$name").also { File(it, name).writeBytes(captured) }
            val target = folder.newFolder("target-$name")

            val value = FileSecureStore(source.toOkioPath(), IdentityCipher).read(name)
            FileSecureStore(target.toOkioPath(), IdentityCipher).write(name, checkNotNull(value))

            assertEquals(captured.decodeToString(), value)
            assertArrayEquals(name, captured, File(target, name).readBytes())
            assertEquals(listOf(name), target.list()!!.toList())
        }
    }

    @Test
    fun `a 2_2 leftover of an interrupted write still reads the stored value`() {
        File(directory, "$BARS.bak").writeText("sealed:kept")
        File(directory, BARS).writeText("sealed:tru")

        assertEquals("kept", noBackupStore().read(BARS))
        assertFalse(File(directory, "$BARS.bak").exists())
    }

    @Test
    fun `an unreadable value throws and the caller may delete it`() {
        File(directory, BARS).writeText("not sealed")
        val store = noBackupStore()

        assertThrows(IllegalArgumentException::class.java) { store.read(BARS) }
        store.delete(BARS)

        assertNull(store.read(BARS))
        assertFalse(File(directory, BARS).exists())
    }

    @Test
    fun `a name is a plain file name`() {
        val store = noBackupStore()

        for (name in listOf("", ".", "..", "../$BARS", "nested/$BARS")) {
            assertThrows(name, IllegalArgumentException::class.java) { store.write(name, "value") }
        }
        assertTrue(directory.list()!!.isEmpty())
    }

    private fun noBackupStore(): SecureStore = FileSecureStore(directory.toOkioPath(), SealingCipher)

    private object SealingCipher : TokenCipher {
        override fun encrypt(value: String): String = "sealed:$value"

        override fun decrypt(value: String): String {
            require(value.startsWith("sealed:")) { "Not sealed" }
            return value.removePrefix("sealed:")
        }
    }

    private object IdentityCipher : TokenCipher {
        override fun encrypt(value: String): String = value

        override fun decrypt(value: String): String = value
    }

    private companion object {
        const val MY_ITMO = "myitmo_tokens.enc"
        const val BARS = "bars_tokens.enc"

        /** G-04's capture of 2.2, read in place; `UpgradeFrom22Test` pins its SHA-256. */
        val CAPTURED_22: File = listOf(File("."), File("app"))
            .map { File(it, "src/androidTest/assets/upgrade-2.2/plaintext") }
            .first { it.isDirectory }
    }
}
