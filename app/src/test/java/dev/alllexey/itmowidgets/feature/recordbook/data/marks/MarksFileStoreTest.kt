package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import com.google.gson.Gson
import dev.alllexey.itmowidgets.feature.recordbook.data.assertSameJson
import dev.alllexey.itmowidgets.feature.recordbook.data.copyStored22
import java.io.File
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarksFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "marks")
    private val file get() = File(directory, "state.json")
    private val store get() = MarksFileStore(directory.toOkioPath())

    private val state = StoredMarks(
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

    @Test
    fun `the written state reads back and no temporary file is left`() {
        store.write(state)

        assertEquals(state, store.read())
        assertFalse(File(directory, "state.json.new").exists())
    }

    @Test
    fun `the 2_2 capture reads, writes back the same document and reads again`() {
        val expected = StoredMarks(
            owner = 123456,
            myItmo = StoredMyItmoSnapshot(
                "2026/2027-1", 1791104400000,
                listOf(StoredMyItmoSubject(9001, 3, 9101, 1001, "Тестовая дисциплина", 87.5, "отлично"))
            ),
            bars = StoredBarsSnapshot(
                "2026/2027-1", 1791104400000,
                listOf(StoredBarsPlan(9201, "exam", "PLAN-9201", "Тестовая дисциплина", 42.0, null, 1, false,
                    listOf(StoredCheckpointMark(9301, 10.0, false), StoredCheckpointMark(9302, null, true))))
            ),
            news = listOf(StoredMarkNews("news-9001", "2026/2027-1", "тестовая дисциплина", "Тестовая дисциплина", 1791104400000, true))
        )

        assertRoundTrip("marks/state.json", expected)
    }

    @Test
    fun `the 2_2 file with absent nulls and whole-number scores reads, writes back the same document and reads again`() {
        val half = "2026/2027-1"
        val expected = StoredMarks(
            owner = 300001,
            myItmo = StoredMyItmoSnapshot(
                half, 1791180000000,
                listOf(
                    StoredMyItmoSubject(1001, 5, 2001, 3001, "Тестовый предмет", 85.5, "отлично"),
                    StoredMyItmoSubject(1001, 5, 2002, 3002, "Без оценки"),
                    StoredMyItmoSubject(1001, 5, 2003, 3003, "Целое", 60.0, "зачтено"),
                )
            ),
            bars = StoredBarsSnapshot(
                half, 1791180060000,
                listOf(
                    StoredBarsPlan(4001, "exam", "ID-1", "Тестовый предмет", 42.25, null, 1, false,
                        listOf(StoredCheckpointMark(5001, 10.0, false), StoredCheckpointMark(5002, null, true))),
                    StoredBarsPlan(4002, "credit", "ID-2", "Зачёт", null, "незачёт", null, true, emptyList()),
                )
            ),
            news = listOf(
                StoredMarkNews("n-1", half, "тестовый предмет", "Тестовый предмет", 1791180120000, false),
                StoredMarkNews("n-2", half, "зачёт", "Зачёт", 1791180180000, true),
            )
        )

        assertRoundTrip("marks/state-sp08.json", expected)
    }

    @Test
    fun `2_2 reads the written file - the field set is Gson's, format and an empty news included`() {
        val bare = StoredMarks(owner = 123)
        listOf(state, bare).forEach { written ->
            store.write(written)

            assertSameJson(Gson().toJson(written), file.readText())
            assertEquals(written, Gson().fromJson(file.readText(), StoredMarks::class.java))
        }
        assertEquals("""{"format":1,"owner":123,"news":[]}""", file.readText())
    }

    @Test
    fun `a 2_2 temporary leftover is ignored`() {
        directory.mkdirs()
        File(directory, "state.json.tmp").writeText("{partial")
        assertNull(store.read())

        store.write(state)

        assertEquals(state, store.read())
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
    fun `another format, a broken file or a missing or invalid required field fail to read`() {
        directory.mkdirs()
        listOf(
            """{"format":2,"news":[]}""",
            """not json""",
            """{"format":1,"news":[{"id":"x","half":"2026/2027-1","name":"Тестовый предмет","detectedAt":1,"notified":false}]}""",
            """{"format":1,"news":[{"id":"x","half":"2026","nameKey":"k","name":"Тестовый предмет","detectedAt":1,"notified":false}]}""",
            """{"format":1,"news":[{"id":null,"half":"2026/2027-1","nameKey":"k","name":"Тестовый предмет","detectedAt":1,"notified":false}]}""",
            """{"format":1,"myItmo":{"half":"2026/2027-1","fetchedAt":1},"news":[]}""",
            """{"format":1,"bars":{"half":"2026/2027-1","fetchedAt":1,"plans":[{"planId":1,"identifier":"7","name":"x","absent":false,"marks":[]}]},"news":[]}"""
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }

    private fun assertRoundTrip(golden: String, expected: StoredMarks) {
        val original = copyStored22(golden, directory, "state.json").readText()

        assertEquals(expected, store.read())
        store.write(expected)
        val written = file.readText()
        assertSameJson(original, written)
        assertTrue(written, written.startsWith("""{"format":1,"""))
        assertEquals(expected, MarksFileStore(directory.toOkioPath()).read())
    }
}
