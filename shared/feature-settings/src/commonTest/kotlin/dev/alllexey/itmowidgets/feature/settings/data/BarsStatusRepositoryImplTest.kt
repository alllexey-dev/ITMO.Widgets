package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.feature.settings.domain.BarsConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class BarsStatusRepositoryImplTest {

    @Test
    fun barsFollowsItsFirstAnswerAndTheEndedSession() = runTest {
        val stores = PreferenceStores()
        val repository = BarsStatusRepositoryImpl(stores.markSources)
        assertEquals(BarsConnection.NOT_CONNECTED, repository.observe().first())

        stores.markSources.setBarsMarksEnabled(true)
        assertEquals(BarsConnection.CONNECTED, repository.observe().first())

        stores.markSources.setBarsLoginPrompt(BarsLoginPrompt.PENDING)
        assertEquals(BarsConnection.EXPIRED, repository.observe().first())
    }
}
