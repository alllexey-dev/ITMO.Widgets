# 0013 Schedule changes are detected on the device

**Decision (2026-09-30).** The application notices changes of the own personal
schedule itself. A periodic background check compares My ITMO's answer for
today and the next 7 days with the last snapshot on the device and keeps what
changed: added, cancelled, moved lessons and changes of room or building,
format and teacher, only for academic lessons. Nothing of it reaches Backend:
no request, no upload of lessons, no dependency on
`Подключение к ITMO.Widgets`.

**Why on the device.** The schedule is personal data that only the device
reads with the user's own token; Backend would need that token or a copy of the
schedule to compare it. The check needs no social data, so keeping it local
leaves the privacy boundary where it is and works without the connection.

**A file, not Room.** The state is one JSON file,
`filesDir/schedule_changes/state.json`, in the style of the other file stores
(`teacher_lessons`, `teacher_levels`, `subject_links`). The project has neither
Room nor KSP, and Room's compiler under kapt carries the same risk as
`androidx.hilt`'s processor, which cannot read Kotlin 2.0 metadata (see
`QrWidgetEntryPoint`). The data is small: up to about 60 lessons in a snapshot
and at most 500 changes of 30 days. The snapshot and the changes must be
written together, and one atomically written file gives that without
transactions; nothing queries the data. A corrupt file is deleted and the next
check is a baseline. Later local trackers (BARS marks) follow the same pattern.

**Two hours through WorkManager.** A unique periodic work every 2 hours with a
network constraint. Android picks the moment: Doze, App Standby and vendor
limits can delay a check by hours, so prompt delivery is not promised. A push
from Backend would need the schedule on the server, which the previous point
rules out.

**Comparison by `pair_id`.** My ITMO's `pair_id` names one lesson occurrence,
so the same positive `pair_id` in both snapshots is the same lesson wherever
its dates are. Lessons without such a match are linked by subject, flow and
type only inside the overlap of the two windows; days that left the window and
the new last day are never read as cancels or adds. One empty answer is held
once instead of cancelling everything, and more than 5 lessons landing on
empty weeks are a new schedule, not a list of additions.

**Quiet hours and one digest.** A run posts at most one summary notification
that replaces the previous one. From 00:00 to 06:00 nothing is shown; the first
run after 06:00 delivers what was found at night. Changes of today or tomorrow
make a sound, later ones arrive silently.

**Moscow time.** The window, «already over», «today and tomorrow» and the quiet
hours are counted by `AcademicTimeProvider` in Europe/Moscow, the zone of the
schedule itself, whatever the device's zone is. The moment of detection comes
from the wall clock.
