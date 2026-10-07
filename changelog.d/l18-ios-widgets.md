# iOS widgets

- The iOS app gets the schedule widgets of Android: «Пара» (small, medium and
  the Lock Screen) with the current or next lesson and «Расписание» (medium,
  large) with the day's lessons, both from a timeline the app precomputes to
  the end of tomorrow (`ScheduleTimelineWriter`, schedule-timeline-v1.json),
  so the widgets switch lessons without waking the app.
- On iOS a pending sport row in the widgets stays until the app updates them:
  there is no seven-minute queue refresh.
