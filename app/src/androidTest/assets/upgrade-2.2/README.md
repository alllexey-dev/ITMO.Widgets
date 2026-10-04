# 2.2 data directory fixture

The data directory of an ITMO.Widgets 2.2 installation, written once by 2.2's own stores and read by head stores in
`app/src/androidTest/java/dev/alllexey/itmowidgets/upgrade/UpgradeFrom22Test.kt`. Every value is synthetic: ISU
`123456`, made-up tokens, subjects, teachers and sheet ids.

Never edit, add to or re-capture these files. `UpgradeFrom22Test.fixtureIsTheUnchanged22Capture` pins their SHA-256.
A lane that moves or reformats a store changes its checker in `upgrade/stores/`, not the assets.

## Source

- Tag `v2.2`, commit `b9b6238d821ce39cf8b9a1773cf41e91b4e4b262` ("Release 2.2").
- Captured on 2026-10-04 on the pool emulator `itmo-pool-api35` (API 35), githubDebug build.
- Capture code: `capture/UpgradeFixtureCapture22.kt.txt` (a `.kt` file in the capture worktree; kept as `.txt` so
  head never compiles it).

## Layout

| Path | 2.2 location | Written by |
|---|---|---|
| `files/datastore/app_preferences.preferences_pb` | `filesDir` | `AppSettingsStorage` (28 keys, 4 of them the legacy shared widget keys written raw), `UtilityStorage` (8), `DataStoreSubjectBindingStore`, `BarsPreferenceRepositoryImpl`, `QrWidgetStateStoreImpl`, `DataStoreFriendSelectionHistory` (1 each) |
| `files/marks/state.json` | `filesDir` | `MarksFileStore` (format 1) |
| `files/sheet_scores/state.json` | `filesDir` | `SheetScoresFileStore` (format 1) |
| `files/schedule_changes/state.json` | `filesDir` | `ScheduleChangesFileStore` (format 1) |
| `files/calendar_sync/state.json` | `filesDir` | `CalendarSyncFileStore` (format 1) |
| `files/subject_links/cache.json` | `filesDir` | `SubjectLinksFileStore` (format 2) |
| `files/teacher_levels/levels.json` | `filesDir` | `TeacherLevelsFileStore` (format 1) |
| `files/teacher_lessons/weeks.json` | `filesDir` | `TeacherWeeksFileStore` (format 1) |
| `files/qr_custom_spoiler/custom_spoiler.png` | `filesDir` | `CustomSpoilerManager` |
| `files/diagnostics/log.jsonl` | `filesDir` | `FileAppDiagnostics` |
| `no_backup/widgets/schedule_snapshot.json` | `noBackupFilesDir` | `ScheduleWidgetSnapshotStoreImpl` |
| `no_backup/debug/{sport_lesson_templates,sport_score_override,academic_date_override}` | `noBackupFilesDir` | `FileSportLessonTemplateStore`, `FileSportScoreOverrideStore`, `FileAcademicTimeOverrideStore` |
| `cache/schedule_cache/123456_2026-10-05.json` | `cacheDir` | `ScheduleLocalDataSourceImpl` (gzip) |
| `cache/qr_hex` | `cacheDir` | `QrCodeLocalDataSourceImpl` |
| `cache/bitmap_cache/noise/<sha256>.png` | `cacheDir` | `QrBitmapCacheImpl` |
| `plaintext/{myitmo_tokens.enc,bars_tokens.enc}` | `noBackupFilesDir`, encrypted | `MyItmoStorage`, `BarsTokenStore` through an identity `TokenCipher` |

Token files cannot be golden: the Keystore key is per device. The capture wrote their 2.2 plaintext (expiries 100
years after the fixed capture clock, 2026-10-04T09:00:00Z); `Upgrade22Fixture` seals them at test time with
`Legacy22TokenEnvelope`, a frozen copy of the 2.2 `v1:` envelope, and head `AndroidKeystoreTokenCipher` reads them.

The 2.2 widget keys: the per-format keys hold values that differ from the legacy shared keys, so the checker sees which
one wins, then removes the per-format keys and checks the legacy fallback.

## Capture commands

Run once; the result is what is committed here.

```bash
git worktree add --detach ~/proj/.wt/android/l06-architecture-g04-capture v2.2
# add capture/UpgradeFixtureCapture22.kt.txt as
#   app/src/androidTest/java/dev/alllexey/itmowidgets/upgrade/UpgradeFixtureCapture22.kt
cd <lane worktree> && scripts/emulator.sh up --api 35          # ANDROID_SERIAL=emulator-<port>
cd ~/proj/.wt/android/l06-architecture-g04-capture
ANDROID_SERIAL=emulator-<port> <lane worktree>/scripts/slot.sh android -- \
  ./gradlew :app:installGithubDebug :app:installGithubDebugAndroidTest
adb -s emulator-<port> shell am instrument -w \
  -e class dev.alllexey.itmowidgets.upgrade.UpgradeFixtureCapture22 \
  dev.alllexey.itmowidgets.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-<port> pull /sdcard/Android/data/dev.alllexey.itmowidgets/cache/upgrade-2.2 <tmp>/
# copied <tmp>/upgrade-2.2/{files,no_backup,cache,plaintext} here unchanged
git worktree remove --force ~/proj/.wt/android/l06-architecture-g04-capture
```

## Run

```bash
scripts/emulator.sh up
ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui dev.alllexey.itmowidgets.upgrade.UpgradeFrom22Test
scripts/emulator.sh down
```
