# Schedule

- The schedule, another user's schedule, the schedule changes history, the
  lesson sheet and the pending sport sheet are Compose Multiplatform screens
  of `:shared:feature-schedule` (`ScheduleScreen`, `UserScheduleScreen`,
  `ScheduleChangesScreen`, `LessonDetailsContent`,
  `PendingSportDetailsContent`), drawn by the unchanged `ScheduleFragment`,
  `UserScheduleFragment`, `ScheduleChangesFragment`,
  `LessonDetailsBottomSheet` and `PendingSportDetailsBottomSheet`; they look
  and behave as before. Their XML layouts, adapters and View tests are gone,
  replaced by JVM tests and four-appearance goldens in
  `ScheduleScreenshotTest`.
- The schedule's domain, ViewModels and data live in `commonMain` behind
  Koin: the cache, the change history, the lessons with a teacher and the
  phone calendar sync are shared with the iOS app, and only the widgets, the
  workers, the phone calendar writer and the `.ics` export stay in `:app`.
- The schedule reads My ITMO through MyItmoApi 2.x and Backend through Core
  2.0, and its files moved from Gson to kotlinx JSON with the same keys, so
  the cache, the change history, the teacher weeks and the calendar sync of
  2.2 keep working after the update.
- `ScheduleRulesTest` keeps Fragments, Views and `R` out of the shared
  schedule code, the widget providers, the receiver and the workers in
  `:app` under their packages, and the `version` field in the widget
  timeline.
