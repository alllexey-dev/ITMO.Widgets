package me.alllexey123.itmowidgets.ui.widgets.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.alllexey123.itmowidgets.util.RuntimeTypeAdapterFactory

val Context.lessonListDataStore by preferencesDataStore(name = "lesson_list_data")

class LessonListRepository(private val context: Context) {
    private val gson: Gson by lazy {
        val lessonListEntryAdapterFactory = RuntimeTypeAdapterFactory.of(LessonListWidgetEntry::class.java)
                .registerSubtype(LessonListWidgetEntry.Error::class.java, "error")
                .registerSubtype(LessonListWidgetEntry.FullDayEmpty::class.java, "full_day_empty")
                .registerSubtype(LessonListWidgetEntry.NoMoreLessons::class.java, "no_more_lessons")
                .registerSubtype(LessonListWidgetEntry.LessonListEnd::class.java, "lesson_list_end")
                .registerSubtype(LessonListWidgetEntry.LessonData::class.java, "lesson_data")
        GsonBuilder()
            .registerTypeAdapterFactory(lessonListEntryAdapterFactory)
            .create()
    }
    private val KEY = stringPreferencesKey("lesson_list_json")

    val data: Flow<LessonListWidgetData> =
        context.lessonListDataStore.data.map { prefs ->
            prefs[KEY]?.let { gson.fromJson(it, LessonListWidgetData::class.java) }
                ?: LessonListWidgetData(listOf(LessonListWidgetEntry.Updating))
        }

    suspend fun setData(newData: LessonListWidgetData) {
        context.lessonListDataStore.edit { prefs ->
            prefs[KEY] = gson.toJson(newData)
        }
    }
}