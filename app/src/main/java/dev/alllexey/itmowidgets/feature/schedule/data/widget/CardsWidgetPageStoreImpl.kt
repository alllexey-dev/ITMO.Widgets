package dev.alllexey.itmowidgets.feature.schedule.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.CardsWidgetPage
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.CardsWidgetPageStore
import kotlin.time.Instant
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/** Each placed cards widget's page under `cards_widget_page_<appWidgetId>` (`<page>:<epoch millis>`) in `app_preferences`. */
class CardsWidgetPageStoreImpl(private val dataStore: DataStore<Preferences>) : CardsWidgetPageStore {

    override suspend fun get(widgetId: Int): CardsWidgetPage? {
        val preferences = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        val (page, at) = preferences[key(widgetId)]?.split(':')?.takeIf { it.size == 2 } ?: return null
        return CardsWidgetPage(page.toIntOrNull() ?: return null, Instant.fromEpochMilliseconds(at.toLongOrNull() ?: return null))
    }

    override suspend fun set(widgetId: Int, page: CardsWidgetPage) {
        dataStore.edit { it[key(widgetId)] = "${page.page}:${page.at.toEpochMilliseconds()}" }
    }

    override suspend fun clear(widgetId: Int) {
        dataStore.edit { it.remove(key(widgetId)) }
    }

    private fun key(widgetId: Int) = stringPreferencesKey("cards_widget_page_$widgetId")
}
