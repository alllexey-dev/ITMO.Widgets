package dev.alllexey.itmowidgets.feature.settings.data

import androidx.core.net.toUri
import dev.alllexey.itmowidgets.core.qr.CustomSpoilerManager
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CustomSpoilerRepositoryImpl @Inject constructor(
    private val manager: CustomSpoilerManager,
    private val widgetRefreshRequester: WidgetRefreshRequester
) : CustomSpoilerRepository {

    override suspend fun hasImage(): Boolean = withContext(Dispatchers.IO) {
        manager.hasCustomSpoiler()
    }

    override suspend fun saveImage(sourceUri: String): Boolean = write {
        manager.saveCustomSpoiler(sourceUri.toUri())
    }

    override suspend fun resetImage(): Boolean = write { manager.deleteCustomSpoiler() }

    /** Widgets are refreshed only after the file really changed. */
    private suspend fun write(block: () -> Boolean): Boolean {
        val changed = withContext(Dispatchers.IO) { block() }
        if (changed) widgetRefreshRequester.refreshAll()
        return changed
    }
}
