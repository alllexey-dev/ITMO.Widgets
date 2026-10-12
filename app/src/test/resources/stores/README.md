# 2.2 store goldens

Files 2.2's Gson stores wrote, read by the JVM golden tests of the stores that moved to kotlinx JSON.
Bytes are kept as written: never reformat them.

| File | Source |
|---|---|
| `teacher_lessons/weeks.json` | device capture, `androidTest/assets/upgrade-2.2/files/teacher_lessons/weeks.json` |
| `schedule_changes/state.json` | device capture, `androidTest/assets/upgrade-2.2/files/schedule_changes/state.json` |
| `calendar_sync/state.json` | device capture, `androidTest/assets/upgrade-2.2/files/calendar_sync/state.json` |
| `calendar_sync/state-without-cleanups.json` | the shape builds before `cleanups` wrote: a Google calendar target, `calendarName`, `calendarAccount`, an event without `calendarId` and `description` |
| `schedule_cache/123456_2026-10-05.json` | device capture, `androidTest/assets/upgrade-2.2/cache/schedule_cache/123456_2026-10-05.json` (gzip) |
| `schedule_cache/default_2026-10-05.json` | written by 2.2 on the host JVM: the signed-in account's entry without `userIsu`, a lesson without a room (gzip) |
| `subject_links/cache.json` | device capture, `androidTest/assets/upgrade-2.2/files/subject_links/cache.json` (format 2) |
| `subject_links/cache-sp08.json` | written by 2.2 on the host JVM (format 2): two local links (`updatedAt` without seconds and with nanoseconds), a pin, a cached answer with an author, a `previous` link and audiences |
| `subject_links/cache-format1.json` | hand-derived from `cache-sp08.json`: `"format":1` and a `scopes` entry of an unknown shape |
| `teacher_levels/levels.json` | device capture, `androidTest/assets/upgrade-2.2/files/teacher_levels/levels.json` |
| `teacher_levels/levels-sp08.json` | written by 2.2 on the host JVM: one `level` absent, `VERY_NEGATIVE` |
| `schedule_snapshot/schedule_snapshot.json` | device capture, `androidTest/assets/upgrade-2.2/no_backup/widgets/schedule_snapshot.json` |
| `schedule_snapshot/schedule_snapshot-sp08.json` | `no_backup/widgets/schedule_snapshot.json` written by 2.2 on the host JVM: an `officialFallback`, a pending sport row with no teacher, room or building, `fullTextSize` absent |
| `schedule_snapshot/schedule_snapshot-v2.json` | hand-written, the shape no capture has: `schedule_snapshot-sp08.json` as this build writes it, `formatVersion` 2 first |

The recordbook's goldens (`marks/`, `sheet_scores/`) live in `shared/feature-recordbook/src/androidHostTest/resources/stores/`.
