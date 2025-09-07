package me.alllexey123.itmowidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import api.myitmo.model.Lesson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class LessonWidget : AppWidgetProvider() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (MyItmoProvider.myItmoSingleton == null) {
            val prefs = context.getSharedPreferences(
                context.getString(R.string.prefs_file_key),
                Context.MODE_PRIVATE
            )
            val myItmo = MyItmoProvider.createMyItmo(prefs)
            MyItmoProvider.myItmoSingleton = myItmo
        }

        val myItmo = MyItmoProvider.myItmoSingleton

        coroutineScope.launch {
            var lessonData: LessonData? = null
            try {
                val currentDateTime = LocalDateTime.now()
                val currentDate = LocalDate.now().plusDays(1)

                val response = myItmo!!.api().getPersonalSchedule(currentDate, currentDate)
                    .execute().body()

                if (response?.data != null) {
                    val lessons = response.data[0].lessons
                    var targetLesson: Lesson? = null

                    for (lesson in lessons) {
                        val startTime = lesson.timeStart;
                        val endTime = lesson.timeEnd;
                        val currTime = DateTimeFormatter.ofPattern("HH:mm").format(currentDateTime)
                        if (startTime > currTime || endTime > currTime) {
                            targetLesson = lesson
                        }
                    }

                    if (targetLesson != null) {
                        val startTime = targetLesson.timeStart
                        val endTime = targetLesson.timeEnd
                        val building = targetLesson.building
                        val shortBuilding =
                            if (building == null) "" else getShortBuildingName(building)
                        val room =
                            if (targetLesson.room == null) "без кабинета" else targetLesson.room + ", "

                        lessonData = LessonData(
                            (targetLesson.subject ?: "Неизвестная дисциплина") + " " + Random(System.currentTimeMillis()).nextInt(1000),
                            "$startTime - $endTime",
                            targetLesson.teacherName ?: "Неизвестный преподаватель",
                            targetLesson.workTypeId,
                            room,
                            shortBuilding
                        )
                    } else {
                        val lastLessonEndTime: String
                        if (lessons.isEmpty()) {
                            lastLessonEndTime = "00:00"
                        } else {
                            lastLessonEndTime = lessons.last().timeEnd
                        }
                        lessonData = LessonData(
                            "Сегодня больше нет пар!",
                            "$lastLessonEndTime - 23:59",
                            "Свобода выбора",
                            -1,
                            "дома",
                            ""
                        )
                    }
                }
            } catch (e: Exception) {
                lessonData = LessonData(
                    "Ошибка при получении данных",
                    "??:?? - ??:??",
                    "...",
                    0,
                    "...",
                    ""
                )
                e.printStackTrace()
            }


            withContext(Dispatchers.Main) {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, lessonData!!)
                }
            }
        }
    }

    override fun onEnabled(context: Context) {

    }

    override fun onDisabled(context: Context) {

    }
}

class LessonData(
    val subject: String, val times: String, val teacher: String,
    val workTypeId: Int, val room: String, val building: String
)

fun getShortBuildingName(buildingName: String): String {
    if (buildingName.contains("Кронв")) return "Кронва"
    if (buildingName.contains("Ломо")) return "Ломо";
    return buildingName.substring(0, 6)
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    lessonData: LessonData
) {

    val views = RemoteViews(context.packageName, R.layout.lesson_widget)

    views.setTextViewText(R.id.title, lessonData.subject)
    views.setTextViewText(R.id.teacher, lessonData.teacher)
    views.setTextViewText(R.id.location_room, lessonData.room)
    views.setTextViewText(R.id.location_building, lessonData.building)

    views.setTextViewText(R.id.time, lessonData.times)

    val colorId = when (lessonData.workTypeId) {
        -1 -> R.color.free_color
        1 -> R.color.lecture_color
        2 -> R.color.lab_color
        3 -> R.color.practice_color
        else -> R.color.subtext_color
    }

    views.setInt(
        R.id.divider, "setColorFilter",
        ContextCompat.getColor(context, colorId)
    )

    appWidgetManager.updateAppWidget(appWidgetId, views)
}