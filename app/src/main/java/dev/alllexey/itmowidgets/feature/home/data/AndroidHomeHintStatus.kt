package dev.alllexey.itmowidgets.feature.home.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidHomeHintStatus @Inject constructor(
    @param:ApplicationContext private val context: Context
) : HomeHintStatus {

    override suspend fun anyWidgetPlaced(): Boolean = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        val pinnable = try {
            manager.isRequestPinAppWidgetSupported
        } catch (_: IllegalStateException) {
            false
        }
        !pinnable || PROVIDERS.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    override suspend fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private companion object {
        val PROVIDERS = listOf(WidgetProviders.SINGLE_LESSON, WidgetProviders.DAY_SCHEDULE, WidgetProviders.QR_CODE)
    }
}
