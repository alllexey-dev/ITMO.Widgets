package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.model.resources.LinkCategory
import dev.alllexey.itmowidgets.core.model.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.model.resources.SaveSubjectLinkRequest
import dev.alllexey.itmowidgets.core.model.resources.SubjectLinksResponse
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.feature.resources.data.CachedLinks
import dev.alllexey.itmowidgets.feature.resources.data.LocalLink
import dev.alllexey.itmowidgets.feature.resources.data.LocalPin
import dev.alllexey.itmowidgets.feature.resources.data.StoredLinks
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksFileStore
import dev.alllexey.itmowidgets.upgrade.Captured22.LINK_ID
import dev.alllexey.itmowidgets.upgrade.Captured22.PERIOD
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT_ID
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals

/** `files/subject_links/cache.json` (format 2): a device-only link, its pin and one cached server answer. */
object SubjectLinksFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val scope = ResourceScope(SUBJECT_ID, SUBJECT, PERIOD)
        val expected = StoredLinks(
            local = mapOf(
                LINK_ID to LocalLink(
                    id = LINK_ID,
                    request = SaveSubjectLinkRequest(
                        SUBJECT_ID, SUBJECT, PERIOD, LinkCategory.MATERIALS, "https://example.com/upgrade22/materials",
                        "Тестовые материалы", LinkVisibility.PRIVATE, null
                    ),
                    updatedAt = OffsetDateTime.parse("2026-10-04T12:00:00+03:00")
                )
            ),
            localPins = mapOf(scope.key to LocalPin(scope, LINK_ID)),
            scopes = mapOf(
                scope.key to CachedLinks(
                    scope,
                    SubjectLinksResponse(emptyList(), emptyList(), emptyList(), null, emptyList(), true)
                )
            )
        )

        assertEquals(expected, SubjectLinksFileStore(File(fixture.filesDir, "subject_links"), fixture.gson).read())
    }
}
