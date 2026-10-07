package dev.alllexey.itmowidgets.feature.update.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppStoreListingTest {

    @Test
    fun aNumericIdIsTheAppStorePage() {
        assertEquals("https://apps.apple.com/app/id1234567890", AppStoreListing.url("1234567890"))
        assertEquals("https://apps.apple.com/app/id1234567890", AppStoreListing.url(" 1234567890 "))
    }

    @Test
    fun withoutAnIdThereIsNoPage() {
        for (id in listOf(null, "", "   ", "$(APP_STORE_ID)", "id123", "123/456", "https://apps.apple.com/app/id1")) {
            assertNull(AppStoreListing.url(id), "id '$id'")
        }
    }
}
