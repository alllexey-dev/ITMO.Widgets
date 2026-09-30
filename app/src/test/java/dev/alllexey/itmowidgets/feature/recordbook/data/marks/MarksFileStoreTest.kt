package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarksFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "marks")
    private val store get() = MarksFileStore(directory, Gson())

    @Test
    fun `the written state reads back and no temporary file is left`() {
        val state = StoredMarks(
            owner = 123,
            myItmo = StoredMyItmoSnapshot(
                "2026/2027-1", 10L,
                listOf(StoredMyItmoSubject(1L, 3, 42L, 142L, "Тестовый предмет", 12.5, "4/C"))
            ),
            bars = StoredBarsSnapshot(
                "2026/2027-1", 20L,
                listOf(StoredBarsPlan(8L, "flow", "7", "Тестовый предмет", null, null, null, false,
                    listOf(StoredCheckpointMark(80L, 7.5, false), StoredCheckpointMark(-8L, null, true))))
            ),
            news = listOf(StoredMarkNews("2026/2027-1|тестовый предмет", "2026/2027-1", "тестовый предмет", "Тестовый предмет", 30L, false))
        )

        store.write(state)

        assertEquals(state, store.read())
        assertFalse(File(directory, "state.json.tmp").exists())
    }

    @Test
    fun `a missing file reads as null and clear removes the directory`() {
        assertNull(store.read())
        store.write(StoredMarks())

        store.clear()

        assertFalse(directory.exists())
        assertNull(store.read())
    }

    @Test
    fun `another format, a broken file or a missing required field fail to read`() {
        directory.mkdirs()
        val file = File(directory, "state.json")
        listOf(
            """{"format":2,"news":[]}""",
            """not json""",
            """{"format":1,"news":[{"id":"x","half":"2026/2027-1","name":"Тестовый предмет","detectedAt":1,"notified":false}]}""",
            """{"format":1,"news":[{"id":"x","half":"2026","nameKey":"k","name":"Тестовый предмет","detectedAt":1,"notified":false}]}""",
            """{"format":1,"myItmo":{"half":"2026/2027-1","fetchedAt":1},"news":[]}""",
            """{"format":1,"bars":{"half":"2026/2027-1","fetchedAt":1,"plans":[{"planId":1,"identifier":"7","name":"x","absent":false,"marks":[]}]},"news":[]}"""
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }
}
