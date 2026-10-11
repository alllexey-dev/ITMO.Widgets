# 0027 Android widgets stay RemoteViews; a shared timeline feeds them and WidgetKit

**Decision (2026-10-03).** In v2.3 the three Android widgets (QR, single lesson, day schedule) stay RemoteViews renderers in
`app/` (`QrCodeWidgetProvider`, `SingleLessonWidgetProvider`, `DayScheduleWidgetProvider` with
`ScheduleWidgetRemoteViewsService`). What they show moves to shared code, and the same data feeds iOS:
- **Domain timeline API.** `ScheduleWidgetSelector.timeline(...)` in `shared/feature-schedule` returns
  `ScheduleWidgetTimeline(version = 1, generatedAt, entries)`, one `ScheduleWidgetTimelineEntry(validFrom, snapshot)`
  per boundary to the end of tomorrow. `select(now)` keeps its exact output, so Android renders the current entry
  and its worker, 7-minute periodic delay and 60 s enqueue throttle do not change.
- **Versioned App Group JSON.** iOS code in the shared modules writes `schedule-timeline-v1.json` and
  `qr-pass-v1.json` (`version`, `generatedAt`, `expiresAt`, `demo`, `matrix` from the shared QR generator)
  into the App Group. WidgetKit widgets read them in Swift and link no Kotlin; the QR Control only opens the QR route.
- **Glance waits for v2.4.** RemoteViews only modernise inside v2.3: `RemoteCollectionItems` for the day
  list and the launcher corner radius on API 31+.

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

**Why.** Glance has not been shown to replay the 18-frame QR reveal (`QrCodeWidgetProvider.kt:255-268`, 16 ms
frames) and has no M3E either; moving three working widgets during a behaviour-preserving rewrite is risk without a
user gain. iOS widgets run in an extension capped at about 30 MB that loads no Kotlin framework, so the app has to
compute the decisions ahead; a timeline is that precomputation, and Android reuses it, so "which lesson is shown
when" has one source of truth.

**Consequence.** Widget rendering code stays Android-only in `app/` and its visual checks stay on the
emulator (ADR 0022). iOS degrades where WidgetKit forbids: no reveal animation, no 7-minute pending-sport refresh.
Every change to a snapshot model updates the JSON fixtures that both Kotlin and Swift tests decode.

**Supersedes.** Nothing. Glance in v2.3 is not scheduled.

**Revisit when.** A Glance check shows at least 15 of 18 frames within 450 ms and v2.4 plans Glance widgets; the
iOS widget extension memory on a device leaves room for a Kotlin reader; a Swift-only widget goes over 25 MB;
Android adds widget types that RemoteViews cannot draw.

**Settled.** 2026-10-03, with the Glance threshold above. The decision holds whatever the Glance check finds; a pass
only feeds the v2.4 plan.

**Evidence** (state at the time of the decision).
- Glance vs RemoteViews frame delivery for the 18-frame QR reveal: pending (v2.4 input)
- Widget memory: the K/N floor alone is about 5 MB RSS (estimate), which supports Swift-only widgets; a Swift-only widget reading App Group JSON of real size staying ≤ 25 MB on a device was pending
