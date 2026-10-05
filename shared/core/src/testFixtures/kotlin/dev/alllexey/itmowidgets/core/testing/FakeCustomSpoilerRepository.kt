package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import kotlinx.coroutines.CompletableDeferred

/** Saves and resets wait for the test to complete [result]; saved sources and resets are recorded. */
class FakeCustomSpoilerRepository(private val hasImage: Boolean = false) : CustomSpoilerRepository {
    val result = CompletableDeferred<Boolean>()
    val saved = mutableListOf<String>()
    var resets = 0
        private set

    override suspend fun hasImage(): Boolean = hasImage

    override suspend fun saveImage(sourceUri: String): Boolean {
        saved += sourceUri
        return result.await()
    }

    override suspend fun resetImage(): Boolean {
        resets++
        return result.await()
    }
}
