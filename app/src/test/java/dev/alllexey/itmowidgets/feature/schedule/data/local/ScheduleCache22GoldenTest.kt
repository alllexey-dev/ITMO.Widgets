package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.schedule.data.copyStored22
import dev.alllexey.itmowidgets.feature.schedule.data.stored22
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** 2.2's gzip cache entries, `data` a JSON document in a string, read into the same days and are rewritten unchanged. */
class ScheduleCache22GoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    private val cacheDirectory get() = File(temporary.root, "cache/schedule_cache")

    @Test
    fun `a 2_2 entry of another user reads into the same day and is rewritten unchanged`() = runBlocking {
        val file = copyStored22("schedule_cache/123456_2026-10-05.json", cacheDirectory)
        val cache = cache(at = 1_791_104_400_000)

        assertEquals(listOf(FRIEND_DAY), cache.observeRange(123456, DATE, DATE).first())
        cache.save(FRIEND_DAY, 123456)

        assertSameEntry(stored22("schedule_cache/123456_2026-10-05.json"), file.readBytes())
        assertEquals(listOf(FRIEND_DAY), cache(at = 1_791_104_400_000).observeRange(123456, DATE, DATE).first())
    }

    @Test
    fun `a 2_2 entry of the signed-in account without userIsu reads and is rewritten without it`() = runBlocking {
        val file = copyStored22("schedule_cache/default_2026-10-05.json", cacheDirectory)
        val cache = cache(at = 1_791_180_000_000)

        assertEquals(listOf(OWN_DAY), cache.observeRange(null, DATE, DATE).first())
        assertNull(cache.get(null, DATE)?.userIsu)
        cache.save(OWN_DAY, null)

        assertSameEntry(stored22("schedule_cache/default_2026-10-05.json"), file.readBytes())
    }

    @Test
    fun `an entry that does not decode is a miss, not a crash`() = runBlocking {
        cacheDirectory.mkdirs()
        val entry = """{"date":"2026-10-05","timestamp":1791180000000,"data":"{\"dayNumber\":1}"}"""
        File(cacheDirectory, "default_2026-10-05.json").writeBytes(gzip(entry))
        File(cacheDirectory, "default_2026-10-06.json").writeText("not gzip")
        val cache = cache(at = 1_791_180_000_000)

        assertEquals(emptyList<DaySchedule>(), cache.observeRange(null, DATE, DATE.plus(1, DateTimeUnit.DAY)).first())
        assertNull(cache.get(null, DATE))
    }

    private fun cache(at: Long): ScheduleLocalDataSourceImpl {
        val root = temporary.root.toOkioPath()
        val directories = object : AppDirectories {
            override val files = root / "files"
            override val cache = root / "cache"
            override val noBackup = root / "no_backup"
        }
        val clock = Clock.fixed(Instant.ofEpochMilli(at), ZoneOffset.UTC)
        return ScheduleLocalDataSourceImpl(clock, directories, AppDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Unconfined))
    }

    /** The outer entries and their `data` documents are equal as JSON trees. */
    private fun assertSameEntry(expected: ByteArray, actual: ByteArray) {
        val (expectedEntry, expectedData) = entryAndData(expected)
        val (actualEntry, actualData) = entryAndData(actual)
        assertEquals(expectedEntry, actualEntry)
        assertEquals(expectedData, actualData)
    }

    private fun entryAndData(gzip: ByteArray): Pair<JsonObject, JsonObject> {
        val entry = Json.parseToJsonElement(GZIPInputStream(gzip.inputStream()).use { String(it.readBytes()) }).jsonObject
        val data = Json.parseToJsonElement(entry.getValue("data").jsonPrimitive.content).jsonObject
        return JsonObject(entry - "data") to data
    }

    private fun gzip(text: String): ByteArray =
        java.io.ByteArrayOutputStream().also { bytes -> GZIPOutputStream(bytes).use { it.write(text.toByteArray()) } }.toByteArray()

    private companion object {
        val DATE: LocalDate = LocalDate(2026, 10, 5)

        /** The G-04 capture's entry. */
        val FRIEND_DAY = DaySchedule(
            dayNumber = 1,
            weekNumber = 6,
            date = DATE,
            note = null,
            lessons = listOf(
                lesson(
                    pairId = 5001, start = "10:00", end = "11:30", subjectName = "Тестовая дисциплина", subjectId = 2001,
                    groupName = "Т3100", flowId = 3001, teacherIsu = 100101, room = "101", building = "Тестовый корпус"
                )
            )
        )

        /** SP-08's entry: the second lesson has no room and no building. */
        val OWN_DAY = DaySchedule(
            dayNumber = 1,
            weekNumber = 6,
            date = DATE,
            note = "Синтетический день",
            lessons = listOf(
                lesson(
                    pairId = 1, start = "08:20", end = "09:50", subjectName = "Тестовый предмет", subjectId = 42,
                    groupName = "T0000", flowId = 7001, teacherIsu = 300010, room = "1506", building = "Тестовый корпус"
                ),
                lesson(
                    pairId = 2, start = "10:00", end = "11:30", subjectName = "Тестовый предмет", subjectId = 42,
                    groupName = "T0000", flowId = 7001, teacherIsu = 300010, room = null, building = null
                )
            )
        )

        fun lesson(
            pairId: Long, start: String, end: String, subjectName: String, subjectId: Long, groupName: String,
            flowId: Long, teacherIsu: Long, room: String?, building: String?
        ) = Lesson(
            pairId = pairId,
            start = LocalTime.parse(start),
            end = LocalTime.parse(end),
            type = "Лекция",
            typeId = Lesson.TypeId(1),
            note = null,
            subjectName = subjectName,
            subjectId = subjectId,
            groupName = groupName,
            flowId = flowId,
            flowTypeId = 2,
            teacherIsu = teacherIsu,
            teacherFio = "Тестовый преподаватель",
            room = room?.let(::Room),
            building = building?.let(::Building),
            buildingId = 13,
            mainBuildingId = 13,
            format = "Очный",
            formatId = 1,
            zoomUrl = null,
            zoomPassword = null,
            zoomInfo = null
        )
    }
}
