package dev.alllexey.itmowidgets.core.navigation

/** One synthetic instance of every core key, in the order of [AppRoutes.registration]. */
object RouteSamples {
    val subject = RecordbookSubjectArgs(11, 1, 3, "2026/2027", 7, "flow", "7")
    val links = SubjectLinksArgs(subjectId = 5, subjectName = "Математика", periodKey = "2026/2027:1")
    val teacher = TeacherReviewArgs(teacherIsu = 100002, teacherName = "Иванов Иван Иванович")
    val lesson = LessonDetailsArgs(
        pairId = 9, date = "2026-10-06", subjectName = "Физика", typeId = 1, format = "Очно",
        start = "10:00", end = "11:30", teacherFio = null, teacherIsu = null, room = "101",
        building = null, buildingId = null, mainBuildingId = null, note = null,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )
    val pendingSport = PendingSportDetailsArgs(
        lessonId = 42, sectionName = "Плавание", autoSign = true, isPrediction = false,
        start = "2026-10-06T10:00+03:00", end = "2026-10-06T11:30+03:00",
        teacherFio = "Петров П. П.", roomName = "Бассейн"
    )

    val all: List<AppRoute> = listOf(
        AppRoutes.Auth,
        AppRoutes.Onboarding,
        AppRoutes.TabRoot(AppTab.SPORT),
        AppRoutes.Settings("PRIVACY"),
        AppRoutes.Diagnostics,
        AppRoutes.DebugTools,
        AppRoutes.RecordbookSubject(subject),
        AppRoutes.Friends,
        AppRoutes.UserFriends(100001, "Тестовый Пользователь"),
        AppRoutes.UserSearch,
        AppRoutes.UserProfile(100001),
        AppRoutes.UserSchedule(100001, "Тестовый Пользователь"),
        AppRoutes.UserSport(100001),
        AppRoutes.ScheduleChanges,
        AppRoutes.QrPass,
        AppRoutes.MyItmoWeb,
        AppRoutes.RecordbookPeriod(
            programName = "Программа", programNames = listOf("Программа", "Программа"),
            programIds = listOf(1, 1), semesters = listOf(1, 2), courses = listOf(1, 1),
            years = listOf("2026/2027", "2026/2027"),
            actual = listOf(true, false), selectedProgram = 1, selectedSemester = 1
        ),
        AppRoutes.FriendSelector(),
        AppRoutes.SheetScores(
            SheetScoresArgs(5, "Математика", "2026/2027:1", "https://example.com/s", SheetScoresArgs.Step.TOTAL)
        ),
        AppRoutes.WebLogin(),
        AppRoutes.ReviewEditor(teacher),
        AppRoutes.SubjectLinks(links),
        AppRoutes.LinkEditor(links, linkId = "link-1"),
        AppRoutes.IcsExport,
        AppRoutes.LinkActions(links, "link-1"),
        AppRoutes.LessonDetails(lesson),
        AppRoutes.PendingSportDetails(pendingSport),
        AppRoutes.ReportReview(teacher, "review-1"),
        AppRoutes.ReportLink(links, "link-1"),
        AppRoutes.LinkUnavailable,
        AppRoutes.CancelBookingConfirm(42)
    )

    fun ofKind(kind: RouteKind): List<AppRoute> = all.filter { it.kind == kind }
}
