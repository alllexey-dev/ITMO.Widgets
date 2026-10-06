package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.TemporaryDirectory
import dev.alllexey.itmowidgets.core.storage.WidgetReloader
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.FileSystem

class SessionSnapshotWriterTest {

    private val temporary = TemporaryDirectory()
    private val directory = AppGroupDirectory(temporary.root / "group", isShared = true)
    private val reloaded = mutableListOf<String>()
    private val writer = SessionSnapshotWriter(AppGroupSnapshotWriter(directory, WidgetReloader { reloaded += it }))

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    /** The extensions decode these field names in Swift; a rename is a new version. */
    @Test
    fun writesSessionV1AsTheExtensionsReadIt() {
        writer.write(SessionSnapshot(isu = 123456, demo = false, alertsAllowed = true), reloadKinds = listOf("kind"))

        assertEquals(
            """{"version":1,"value":{"isu":123456,"demo":false,"alertsAllowed":true}}""",
            FileSystem.SYSTEM.read(directory.file("session-v1.json")) { readUtf8() }
        )
        assertEquals(listOf("kind"), reloaded)
    }

    @Test
    fun readsBackWhatItWroteAndNothingWhenSignedOut() {
        assertNull(writer.read())

        val demo = SessionSnapshot(isu = null, demo = true, alertsAllowed = false)
        writer.write(demo)

        assertEquals(demo, writer.read())
    }
}
