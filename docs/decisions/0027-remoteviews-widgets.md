# 0027 Android widgets stay RemoteViews; a shared timeline feeds them and WidgetKit

**Decision (2026-10-03).** In v2.3 the three Android widgets (QR, single lesson, day schedule) stay RemoteViews renderers in
`app/` (`QrCodeWidgetProvider`, `SingleLessonWidgetProvider`, `DayScheduleWidgetProvider` with
`ScheduleWidgetRemoteViewsService`). What they show moves to shared code, and the same data feeds iOS:
- **Domain timeline API (LS-3).** `ScheduleWidgetSelector.timeline(...)` in `shared/feature-schedule` returns
  `ScheduleWidgetTimeline(version = 1, generatedAt, entries)`, one `ScheduleWidgetTimelineEntry(validFrom, snapshot)`
  per boundary to the end of tomorrow. `select(now)` keeps its exact output, so Android renders the current entry
  and its worker, 7-minute periodic delay and 60 s enqueue throttle do not change.
- **Versioned App Group JSON.** iOS code in the shared modules writes `schedule-timeline-v1.json` (LS-3, IO-10b) and
  `qr-pass-v1.json` (`version`, `generatedAt`, `expiresAt`, `demo`, `matrix` from the shared QR generator; IO-21)
  into the App Group. WidgetKit widgets read them in Swift and link no Kotlin; the QR Control only opens the QR route.
- **Glance waits for v2.4** (M3-07). RemoteViews only modernise inside v2.3: `RemoteCollectionItems` for the day
  list (AA-12) and the launcher corner radius on API 31+ (M3-05).

**Rules.**
- JSON carries data and string keys, never Russian labels; Swift resolves keys from the generated `.xcstrings`
  (ADR 0028). Fields are written with defaults encoded (Swift has no Kotlin defaults).
- Versioning: an added field keeps `version`; a rename or removal bumps it; a reader treats a higher `version`, a
  missing or a corrupt file as a placeholder state, never a crash. The App Group contract is separate from the
  Android widget file's `formatVersion`.
- Time and academic decisions stay in Kotlin: Swift picks the entry whose `validFrom` has passed and nothing more.
- Widget receivers, provider XML, work names and widget kinds (`dev.alllexey.itmowidgets.widget.qr`, …) are stable
  identifiers (ADR 0016); widgets keep their res palette and get no M3E (ADR 0021).
- The session cleaners delete the files on sign-out; the QR file marks a demo session with `demo`.

**Why.** Glance cannot be shown to replay the 18-frame QR reveal (`QrCodeWidgetProvider.kt:255-268`, 16 ms frames)
until SP-20 runs, and has no M3E either; moving three working widgets during a parity rewrite is risk without a user
gain (02 Q2, 03 Q8, against 12 TR-21). iOS widgets run in an extension capped at about 30 MB that loads no Kotlin
framework (master §3.4), so the app has to compute the decisions ahead; a timeline is that precomputation, and
Android reuses it, so "which lesson is shown when" has one source of truth.

**Consequence.** Widget rendering code stays Android-only in `app/` (master §3.5) and its visual checks stay on the
emulator (ADR 0022). iOS degrades where WidgetKit forbids: no reveal animation, no 7-minute pending-sport refresh.
Every change to a snapshot model updates the JSON fixtures that both Kotlin and Swift tests decode.

**Supersedes.** Nothing. 12 TR-21 (Glance in v2.3) is not scheduled.

**Revisit when.** SP-20 passes (Glance ≥ 15 of 18 frames within 450 ms) and v2.4 plans M3-07; the iOS widget
extension memory (SP-16b) leaves room for a Kotlin reader; SP-16b fails (a Swift-only widget over 25 MB); Android
adds widget types that RemoteViews cannot draw.

**Settled.** A9 settled it (02 Q2 (a), 02 Q9 (a), 03 Q8 (a)); the owner set the SP-20 threshold on 2026-10-03.
The decision holds whatever SP-20 finds; a pass only feeds the v2.4 plan.

**Evidence.**
- SP-20: pending (Glance vs RemoteViews frame delivery for the 18-frame QR reveal; v2.4 input for M3-07)
- SP-16: pending (SP-16a FAIL (estimate): the K/N floor alone is about 5 MB RSS, which supports Swift-only widgets; SP-16b: a Swift-only widget reading App Group JSON of real size stays ≤ 25 MB)
