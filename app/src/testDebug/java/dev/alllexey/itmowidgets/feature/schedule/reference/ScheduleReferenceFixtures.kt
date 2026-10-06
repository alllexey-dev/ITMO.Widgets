package dev.alllexey.itmowidgets.feature.schedule.reference

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.plusMinutes
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.schedule.ui.changes.ScheduleChangesPreviewActivity
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl
import org.robolectric.util.ReflectionHelpers
import com.google.android.material.R as MaterialR

/**
 * Synthetic data of the schedule reference captures, on the debug hosts' clock: Monday, 7 September 2026, 12:00 in
 * Moscow. The lesson at 11:40 is the current one, the lesson at 10:00 has a changed room, and the auto-sign rows
 * come after the last lesson of the day.
 */
internal object ScheduleReferenceFixtures {

    val today: LocalDate = LocalDate(2026, 9, 7)

    const val FRIEND_ISU = 300100
    const val FRIEND_NAME = "Тестовая подруга Константинопольская-Преображенская"
    const val TEACHER_ISU = 300001
    const val TEACHER_NAME = "Тестовый преподаватель с очень длинным именем"
    const val CHANGED_PAIR_ID = 2L

    /** The hosts' [AcademicTimeProvider]: the same instant, for the view models a test builds itself. */
    object FixedTime : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today() = ScheduleReferenceFixtures.today
        override fun now() = today().atTime(12, 0).toInstant(timeZone)
    }

    fun lesson(
        pairId: Long,
        start: LocalTime,
        subject: String,
        typeId: Int,
        type: String,
        teacherIsu: Int? = null,
        teacher: String? = TEACHER_NAME,
        room: String? = "1506",
        building: String? = "Кронверкский проспект, 49",
        note: String? = null,
        zoomUrl: String? = null,
        zoomPassword: String? = null,
    ) = Lesson(
        pairId = pairId, start = start, end = start.plusMinutes(90), type = type, typeId = Lesson.TypeId(typeId),
        note = note, subjectName = subject, subjectId = pairId, groupName = "ФИЗ ПИИКТ 3.2", flowId = 1,
        flowTypeId = 2, teacherIsu = teacherIsu?.toLong(), teacherFio = teacher, room = room?.let(::Room),
        building = building?.let(::Building), buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = zoomUrl, zoomPassword = zoomPassword, zoomInfo = null,
    )

    /** The own schedule: yesterday, a full today with a changed lesson, and tomorrow. */
    fun ownDays(): List<DaySchedule> = listOf(
        day(today.minus(1, DateTimeUnit.DAY), lesson(10, LocalTime(10, 0), "Физическая культура", 11, "Спорт")),
        day(
            today,
            lesson(1, LocalTime(8, 20), "Математический анализ (продвинутый уровень)", 1, "Лекция",
                note = "Организационная информация о занятии"),
            lesson(CHANGED_PAIR_ID, LocalTime(10, 0), "Физика", 3, "Практика", room = "2202",
                building = "ул. Ломоносова, 9"),
            lesson(3, LocalTime(11, 40), "Программирование", 2, "Лабораторная"),
            lesson(4, LocalTime(15, 20), "Английский язык", 3, "Практика", teacher = null, room = null,
                building = null, zoomUrl = "https://example.invalid/meeting"),
        ),
        day(
            today.plus(1, DateTimeUnit.DAY),
            lesson(5, LocalTime(8, 20), "Дискретная математика", 1, "Лекция"),
            lesson(6, LocalTime(10, 0), "Базы данных", 2, "Лабораторная"),
        ),
    )

    /** A friend's schedule, different from the own one, with a long name on the selected-user card. */
    fun friendDays(): List<DaySchedule> = listOf(
        day(
            today,
            lesson(21, LocalTime(10, 0), "История России", 1, "Лекция"),
            lesson(22, LocalTime(11, 40), "Иностранный язык в профессиональной деятельности", 3, "Практика"),
            lesson(23, LocalTime(13, 30), "Алгоритмы и структуры данных", 2, "Лабораторная"),
        ),
        day(
            today.plus(1, DateTimeUnit.DAY),
            lesson(24, LocalTime(8, 20), "Линейная алгебра", 1, "Лекция"),
            lesson(25, LocalTime(10, 0), "Операционные системы", 2, "Лабораторная"),
        ),
    )

    /** A free queue the student waits in and an auto-sign prediction, after the last lesson of today. */
    fun pendingSport(): List<PendingSportBooking> = listOf(waitingBooking(), predictedBooking())

    fun waitingBooking() = booking(
        queueId = 1, kind = PendingSportBooking.QueueKind.FREE, lessonId = 100, start = LocalTime(17, 0),
        isPrediction = false,
    )

    fun predictedBooking() = booking(
        queueId = 2, kind = PendingSportBooking.QueueKind.AUTO, lessonId = 200, start = LocalTime(18, 40),
        isPrediction = true,
    )

    /** The room of [CHANGED_PAIR_ID] moved today: the list marks the lesson, its sheet shows "было -> стало". */
    fun roomChange() = change(
        "room", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.PLACE), "2026-09-07T06:00:00Z",
        subject = "Физика", typeId = 3,
        before = slot(CHANGED_PAIR_ID, today, LocalTime(10, 0)),
        after = slot(CHANGED_PAIR_ID, today, LocalTime(10, 0), room = "2202", building = "ул. Ломоносова, 9"),
    )

    /** The history screen: today, yesterday and an earlier day, read and unread rows, every kind of change. */
    fun history(): List<ScheduleChange> = listOf(
        change("added", ScheduleChangeKind.ADDED, emptySet(), "2026-09-07T08:00:00Z",
            before = null, after = slot(1, LocalDate(2026, 9, 9), LocalTime(10, 0))),
        change("cancelled", ScheduleChangeKind.CANCELLED, emptySet(), "2026-09-07T06:00:00Z", subject = "Физика",
            typeId = 3, flowName = "ФИЗ ПИИКТ 3.2", before = slot(2, LocalDate(2026, 9, 8)), after = null,
            read = true),
        change("same-day", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TIME), "2026-09-06T12:00:00Z",
            subject = "Программирование", typeId = 2, before = slot(3, LocalDate(2026, 9, 10)),
            after = slot(3, LocalDate(2026, 9, 10), LocalTime(10, 0))),
        change("moved", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TIME, ScheduleChangeField.PLACE),
            "2026-09-06T09:00:00Z", subject = "Физика", typeId = 1,
            before = slot(4, LocalDate(2026, 9, 8), LocalTime(13, 30)),
            after = slot(4, LocalDate(2026, 9, 11), LocalTime(15, 20), room = "2202",
                building = "ул. Ломоносова, 9")),
        change("format", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.FORMAT, ScheduleChangeField.PLACE),
            "2026-09-03T10:00:00Z", subject = "Английский язык", typeId = 3, flowName = null,
            before = slot(5, LocalDate(2026, 9, 9), LocalTime(11, 40)),
            after = slot(5, LocalDate(2026, 9, 9), LocalTime(11, 40), room = null, building = null,
                formatId = 3, format = "Дистанционный"),
            read = true),
        change("teacher", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TEACHER), "2026-09-03T09:00:00Z",
            subject = "Математический анализ", typeId = 3, before = slot(6, LocalDate(2026, 9, 14)),
            after = slot(6, LocalDate(2026, 9, 14), teacherIsu = 300002, teacherName = "Новый преподаватель")),
    )

    /** Friends on the lesson: one with a group, one without a name (the ISU placeholder). */
    fun friends(): List<UserSummary> = listOf(
        UserSummary(
            isu = FRIEND_ISU, name = FRIEND_NAME, pictureUrl = null,
            groups = listOf(UserGroup("P3212", course = 2, facultyShortName = "ФПИиКТ")),
            sharing = UserSharing(sport = true, schedule = true),
        ),
        UserSummary(isu = 300101, name = "", pictureUrl = null, groups = emptyList(),
            sharing = UserSharing(sport = false, schedule = true)),
    )

    /** Puts the schedule host back to its defaults; every reference starts from them. */
    fun resetScheduleHost() {
        ScheduleLifecycleTestActivity.days = MutableStateFlow(emptyList())
        ScheduleLifecycleTestActivity.friendDays = MutableStateFlow(emptyList())
        ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Success(Unit) }
        ScheduleLifecycleTestActivity.clearOutcome = {}
        ScheduleLifecycleTestActivity.restrictToRequestedRange = false
        ScheduleLifecycleTestActivity.showPendingSport = MutableStateFlow(false)
        ScheduleLifecycleTestActivity.pendingSport = MutableStateFlow(AppResult.Success(emptyList()))
        ScheduleLifecycleTestActivity.refreshPendingOutcome = {}
        ScheduleLifecycleTestActivity.servicesEnabled = false
        ScheduleLifecycleTestActivity.friendsOutcome = { AppResult.Success(emptyList()) }
        ScheduleLifecycleTestActivity.teacherLevelsByIsu = emptyMap()
        ScheduleLifecycleTestActivity.changes.value = emptyList()
    }

    fun resetChangesHost() {
        ScheduleChangesPreviewActivity.changes.value = emptyList()
        ScheduleChangesPreviewActivity.readCalls = 0
    }

    /**
     * Runs [beforeCreate] and [onCreated] around the `onCreate` of every [activity] while [capture] runs: a reference
     * launches its host once per appearance, and the state a test sets up (an unread history, a selected friend, an
     * open sheet) belongs to each launch.
     */
    fun <A : Activity> onEachLaunch(
        activity: Class<A>,
        beforeCreate: (A) -> Unit = {},
        onCreated: (A) -> Unit = {},
        capture: () -> Unit,
    ) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(created: Activity, savedInstanceState: Bundle?) {
                if (activity.isInstance(created)) beforeCreate(activity.cast(created)!!)
            }

            override fun onActivityPostCreated(created: Activity, savedInstanceState: Bundle?) {
                if (activity.isInstance(created)) onCreated(activity.cast(created)!!)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            capture()
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    /**
     * The surface of the bottom sheet [sheet] shows, its window focused. A sheet is a dialog window and Roborazzi
     * finds a view through Espresso in the focused window, which Robolectric leaves on the activity.
     */
    fun sheetSurface(activity: Activity, sheet: DialogFragment): View {
        val dialog = checkNotNull(sheet.dialog) { "The sheet is not shown" }
        focus(activity.window.decorView, false)
        focus(checkNotNull(dialog.window).decorView, true)
        return checkNotNull(dialog.findViewById(MaterialR.id.design_bottom_sheet)) { "No bottom sheet in the dialog" }
    }

    private fun focus(decor: View, hasFocus: Boolean) {
        val root = ReflectionHelpers.callInstanceMethod<Any>(decor, "getViewRootImpl")
        Shadow.extract<ShadowViewRootImpl>(root).callWindowFocusChanged(hasFocus)
    }

    private fun day(date: LocalDate, vararg lessons: Lesson) =
        DaySchedule(date.dayOfWeek.isoDayNumber, 1, date, null, lessons.toList())

    private fun booking(
        queueId: Long,
        kind: PendingSportBooking.QueueKind,
        lessonId: Long,
        start: LocalTime,
        isPrediction: Boolean,
    ): PendingSportBooking {
        val begin = today.atTime(start).toInstant(FixedTime.timeZone)
        return PendingSportBooking(
            queueId = queueId, queueKind = kind, lessonId = lessonId,
            sectionName = "Современные танцы: тестовая секция с длинным названием",
            start = begin, end = begin + 90.minutes,
            teacherFio = TEACHER_NAME, roomName = "Кронверкский проспект, 49, спортивный зал",
            isPrediction = isPrediction, teacherIsu = 300002,
        )
    }

    private fun change(
        id: String,
        kind: ScheduleChangeKind,
        fields: Set<ScheduleChangeField>,
        detectedAt: String,
        subject: String = "Математический анализ",
        typeId: Int = 1,
        flowName: String? = "Тестовый поток",
        before: LessonSlot?,
        after: LessonSlot?,
        read: Boolean = false,
    ) = ScheduleChange(
        id = id, detectedAt = Instant.parse(detectedAt), kind = kind, fields = fields, subjectName = subject,
        typeId = typeId, flowName = flowName, before = before, after = after, read = read, notified = true,
    )

    private fun slot(
        pairId: Long,
        date: LocalDate,
        start: LocalTime = LocalTime(8, 20),
        room: String? = "1506",
        building: String? = "Кронверкский проспект, 49",
        formatId: Int = 1,
        format: String? = "Очный",
        teacherIsu: Long? = TEACHER_ISU.toLong(),
        teacherName: String? = TEACHER_NAME,
    ) = LessonSlot(
        pairId, date, start, start.plusMinutes(90), room,
        building, formatId, format, teacherIsu, teacherName,
    )
}
