package dev.alllexey.itmowidgets.feature.settings.data

import androidx.core.net.toUri
import dev.alllexey.itmowidgets.core.qr.CustomSpoilerManager
import dev.alllexey.itmowidgets.feature.settings.domain.CustomSpoilerRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CustomSpoilerRepositoryImpl @Inject constructor(
    private val manager: CustomSpoilerManager
) : CustomSpoilerRepository {

    override suspend fun hasImage(): Boolean = withContext(Dispatchers.IO) {
        manager.hasCustomSpoiler()
    }

    override suspend fun saveImage(sourceUri: String): Boolean = withContext(Dispatchers.IO) {
        manager.saveCustomSpoiler(sourceUri.toUri())
    }

    override suspend fun resetImage(): Boolean = withContext(Dispatchers.IO) {
        manager.deleteCustomSpoiler()
    }
}
