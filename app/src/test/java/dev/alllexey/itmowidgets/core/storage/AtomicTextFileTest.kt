package dev.alllexey.itmowidgets.core.storage

import android.app.Application
import android.os.Build
import android.util.AtomicFile
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every test runs against the real `android.util.AtomicFile` of SDK 29, which keeps the old file as `.bak` while it
 * writes, and of SDK 35, which writes `.new` and renames it; 2.2 left both kinds of files on devices.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: these tests need no app graph, and the manifest's one cannot boot under Robolectric.
@Config(sdk = [29, 35], application = Application::class)
class AtomicTextFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "store/state.json") }
    private val newFile by lazy { File(file.path + ".new") }
    private val backupFile by lazy { File(file.path + ".bak") }

    @Test
    fun `a missing file reads as null`() {
        assertNull(AtomicTextFile(file).read())
    }

    @Test
    fun `a written value reads back and replaces the previous one`() {
        val store = AtomicTextFile(file)

        store.write(FIRST)
        assertEquals(FIRST, store.read())
        store.write(SECOND)

        assertEquals(SECOND, AtomicTextFile(file).read())
        assertFalse(newFile.exists())
        assertFalse(backupFile.exists())
    }

    @Test
    fun `a null write deletes the file and every leftover`() {
        AtomicTextFile(file).write(FIRST)
        newFile.writeText("partial")
        backupFile.writeText(FIRST)

        AtomicTextFile(file).write(null)

        assertNull(AtomicTextFile(file).read())
        assertFalse(file.exists())
        assertFalse(newFile.exists())
        assertFalse(backupFile.exists())
    }

    @Test
    fun `a file AtomicFile wrote reads back byte for byte`() {
        writeWithAtomicFile(SECOND)

        assertEquals(SECOND, AtomicTextFile(file).read())
    }

    @Test
    fun `AtomicFile reads what this file wrote byte for byte`() {
        AtomicTextFile(file).write(SECOND)

        assertArrayEquals(SECOND.toByteArray(Charsets.UTF_8), AtomicFile(file).readFully())
    }

    @Test
    fun `an AtomicFile write interrupted on this SDK keeps the last complete value`() {
        writeWithAtomicFile(FIRST)
        val output = AtomicFile(file).startWrite()
        output.write("{\"trunc".toByteArray(Charsets.UTF_8))
        output.close()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            assertTrue(backupFile.exists())
        } else {
            assertTrue(newFile.exists())
        }

        assertEquals(FIRST, AtomicTextFile(file).read())
        assertFalse(newFile.exists())
        assertFalse(backupFile.exists())
        assertEquals(FIRST, readWithAtomicFile())
    }

    @Test
    fun `a legacy backup is restored over a partial file`() {
        file.parentFile!!.mkdirs()
        file.writeText("{\"trunc")
        backupFile.writeText(FIRST)

        assertEquals(FIRST, AtomicTextFile(file).read())
        assertFalse(backupFile.exists())
        assertEquals(FIRST, file.readText())
    }

    @Test
    fun `a legacy backup without the file reads as null, as before`() {
        file.parentFile!!.mkdirs()
        backupFile.writeText(FIRST)

        assertNull(AtomicTextFile(file).read())
        assertTrue(backupFile.exists())
    }

    @Test
    fun `a write over a legacy backup leaves only the new value`() {
        file.parentFile!!.mkdirs()
        file.writeText("{\"trunc")
        backupFile.writeText(FIRST)

        AtomicTextFile(file).write(SECOND)

        assertEquals(SECOND, AtomicTextFile(file).read())
        assertFalse(backupFile.exists())
        assertEquals(SECOND, readWithAtomicFile())
    }

    @Test
    fun `a partial new file beside the file is dropped on read`() {
        AtomicTextFile(file).write(FIRST)
        newFile.writeText("{\"trunc")

        assertEquals(FIRST, AtomicTextFile(file).read())
        assertFalse(newFile.exists())
    }

    @Test
    fun `a partial new file of a first write is kept until the next write`() {
        file.parentFile!!.mkdirs()
        newFile.writeText("{\"trunc")

        assertNull(AtomicTextFile(file).read())
        assertTrue(newFile.exists())

        AtomicTextFile(file).write(SECOND)
        assertEquals(SECOND, AtomicTextFile(file).read())
        assertFalse(newFile.exists())
    }

    private fun writeWithAtomicFile(value: String) {
        file.parentFile!!.mkdirs()
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        output.write(value.toByteArray(Charsets.UTF_8))
        atomicFile.finishWrite(output)
    }

    private fun readWithAtomicFile(): String = AtomicFile(file).readFully().toString(Charsets.UTF_8)

    private companion object {
        const val FIRST = "{\"accessToken\":\"first\"}"
        const val SECOND = "v1:Привет, ИТМО 🚀\n{\"line\":2}"
    }
}
