package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.feature.resources.data.LocalLink
import dev.alllexey.itmowidgets.feature.resources.data.LocalPin
import dev.alllexey.itmowidgets.feature.resources.data.StoredLinkRequest
import dev.alllexey.itmowidgets.feature.resources.data.StoredLinks
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksFileStore
import dev.alllexey.itmowidgets.upgrade.Captured22.LINK_ID
import dev.alllexey.itmowidgets.upgrade.Captured22.PERIOD
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT_ID
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * `files/subject_links/cache.json` (format 2): a device-only link, its pin and one cached server answer. Format 3 keeps
 * the link and the pin, drops the cached answer (refetched on the next open) and is written back as format 3.
 */
object SubjectLinksFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val scope = ResourceScope(SUBJECT_ID, SUBJECT, PERIOD)
        val expected = StoredLinks(
            local = mapOf(
                LINK_ID to LocalLink(
                    id = LINK_ID,
                    request = StoredLinkRequest(
                        SUBJECT_ID, SUBJECT, PERIOD, LinkCategory.MATERIALS, "https://example.com/upgrade22/materials",
                        "Тестовые материалы", LinkVisibility.PRIVATE, null
                    ),
                    updatedAt = Instant.parse("2026-10-04T12:00:00+03:00")
                )
            ),
            localPins = mapOf(scope.key to LocalPin(scope, LINK_ID)),
        )
        val store = SubjectLinksFileStore(AndroidAppDirectories(fixture.context))

        assertEquals(expected, store.read())
        store.write(expected)
        val written = File(fixture.filesDir, "subject_links/cache.json").readText()
        assertTrue(written, written.startsWith("{\"format\":3,"))
        assertEquals(expected, SubjectLinksFileStore(AndroidAppDirectories(fixture.context)).read())
    }
}
