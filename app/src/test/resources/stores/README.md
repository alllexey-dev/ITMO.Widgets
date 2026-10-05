# 2.2 store goldens

Files 2.2's Gson stores wrote, read by the JVM golden tests of the stores that moved to kotlinx JSON (recipe
`kotlinx-file-store`). Bytes are kept as written: never reformat them.

| File | Source |
|---|---|
| `teacher_lessons/weeks.json` | G-04 capture, `androidTest/assets/upgrade-2.2/files/teacher_lessons/weeks.json` |
| `schedule_changes/state.json` | G-04 capture, `androidTest/assets/upgrade-2.2/files/schedule_changes/state.json` |
| `calendar_sync/state.json` | G-04 capture, `androidTest/assets/upgrade-2.2/files/calendar_sync/state.json` |
| `calendar_sync/state-without-cleanups.json` | the shape builds before `cleanups` wrote: a Google calendar target, `calendarName`, `calendarAccount`, an event without `calendarId` and `description` |
| `schedule_cache/123456_2026-10-05.json` | G-04 capture, `androidTest/assets/upgrade-2.2/cache/schedule_cache/123456_2026-10-05.json` (gzip) |
| `schedule_cache/default_2026-10-05.json` | SP-08 fixture (2.2 on the host JVM): the signed-in account's entry without `userIsu`, a lesson without a room (gzip) |
| `marks/state.json` | G-04 capture, `androidTest/assets/upgrade-2.2/files/marks/state.json` |
| `marks/state-sp08.json` | SP-08 fixture `files/marks/state.json` (2.2 on the host JVM): null scores, rates and marks absent, `60.0` kept as a double, a plan with empty `marks` |
| `sheet_scores/state.json` | G-04 capture, `androidTest/assets/upgrade-2.2/files/sheet_scores/state.json` |
| `sheet_scores/state-sp08.json` | SP-08 fixture `files/sheet_scores/state.json` (2.2 on the host JVM): a connection with every nullable field absent, an empty `tabName` and `headerPath` |
