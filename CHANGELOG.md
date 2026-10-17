# Changelog

Reference documents in `docs/` describe the current state; this file records
what changed and when. Unreleased entries describe local development, not a
publication or deployment.

## Unreleased (2.3.0)

The app is rebuilt on Kotlin Multiplatform and Compose Multiplatform: Android
keeps its features and the data of 2.2, and the same shared screens run in a
new iOS app.

### Data (2026-10-17)

- `TeacherNowGateway`: the lesson a teacher gives now or next today, from My ITMO's teacher timetable (MyItmoApi 2.2.0 `getTeacherSchedule`), for the person card's «Сейчас ведёт»; parallel groups in one room are one lesson.

### Fixes (2026-10-17)

- A sheet's total skips old copies and templates of a tab, columns still empty for everyone and text columns, checks the total against the sum of the row's points, and finds full names from the ITMO.ID name parts; on 13 real sheets 1805 of 1816 students get the right total, up from 931 (#419).
- A doubtful total shows `Проверьте итог` on the subject page; the review shows the total with its reason, the row around it and the other candidates, and `Вернуть авто` undoes a hand-picked total. On iOS `Мои баллы` is a native sheet (#419).

### Fixes (2026-10-15)

- Schedule week paging uses stable dates on both platforms and no longer treats programmatic movement as a swipe;
  prepending weeks and interrupted jumps no longer reset the selected weekday. iOS animates week changes and cancels
  stale positioning passes. Timeline projection runs off main and is reused while scrolling; cached timestamps no
  longer reread day files, disabled queue rows skip sport refreshes, and duplicate/edge requests are bounded.

- Past days in the schedule and sport week strips are dimmer; today and the selected day keep full emphasis (#424).
- `Главный экран` settings can hide the QR pass entry on Сегодня on both platforms without hiding widgets or shortcuts (#420).
- Sport friend rows and lesson sheets preserve avatar URLs on both platforms; iOS friend-request notifications use their photo too (#426).
- Sport lessons whose MyITMO end falls on the wrong date (two days before the start) end on their own day at the time slot end, so rows, sheets, the schedule, widgets and calendar export show the right time and length.
- Avatar initials are the surname and the given name on both platforms: «Фамилия Имя Отчество» shows ФИ instead of ФО, and the own name reads «Фамилия Имя» from ITMO.ID's `family_name` and `given_name` instead of its two-word `name`, as other students' names do with Backend that stores the parts (itmo-widgets-backend#52). Avatars of other students take the initials from Backend's new `familyName` and `givenName` when it sends them (contract from Backend e475238). iOS friend requests, sport friends and recordbook teachers use the shared `personInitials`; the demo students follow the same order.

- Subject link votes use like/dislike thumbs on Android and iOS, filled for the selected vote (#421).
- iOS publishes the ITMO.ID identity to Backend, including groups, with foreground retries (#422).
- iOS loads avatar photos in shared Compose screens and native friend rows, with initials while loading or on failure (#425).

### iOS app

- Sign-in through ITMO.ID or a refresh token, the demo session (five taps on
  the logo), the first-run flow with `Как добавить виджет` hints, `Вход на
  сайт` with the system QR scanner, and My ITMO in the app's browser (other
  sites open in Safari).
- The tabs are the shared screens of Android: `Зачётка` with BARS and the
  subject page (links, chats, teacher reviews with the AI summary), the
  schedule with friends' schedules and lesson sheets, sport with `Мой спорт` and `Запись`, the home feed, and the Me
  tab with friends, people search, privacy and settings; rows iOS cannot
  support are hidden.
- The QR pass at full brightness, the QR widget with the spoiler and dynamic
  colours, the `QR-пропуск` control in Control Center, on the Lock Screen and
  the Action button, App Shortcuts `QR-пропуск` and `Сегодня` in Siri and
  Spotlight, and quick actions on the app icon.
- Schedule widgets `Пара` (small, medium, Lock Screen) and `Расписание`
  (medium, large) read a timeline the app precomputes to the end of tomorrow,
  so they switch lessons without waking the app.
- Background refresh checks schedule changes and new marks (My ITMO, BARS,
  connected sheets) with Android's periods and texts; what is found at night
  arrives at 06:00, and a tap on a notification opens the screen it is about.
- The notification service books a freed sport place as Android's push
  handler does, never for another account, the demo or without the services
  opt-in.
- Phone calendar sync into an `ITMO.Widgets` calendar, and `.ics` export
  through the share sheet.
- A newer iOS release from Backend is offered with a link to the App Store
  (inactive until the App Store record exists); a Kotlin crash is saved to
  `Журнал ошибок`.
- The app icon is Android's launcher icon, with iOS 18 dark and tinted
  variants.
- New shell for iOS 26.1 and later: a Liquid Glass tab bar `Сегодня`,
  `Расписание`, `Зачётка`, `Спорт`, `Профиль`; a `QR-пропуск` accessory over
  `Сегодня` opens the pass sheet; `Сегодня` has a header with the date and the
  bell; sheets open at the content's height, 0.82 of the screen or full.

### Android screens on Compose Multiplatform

- Every screen, sheet and dialog (home, QR pass, schedule, sport, recordbook,
  social, settings, links, reviews, sign-in, first-run flow, Me tab, update
  offer, web sign-in, My ITMO) is a Compose Multiplatform screen shared with
  iOS; copy, layout and behaviour stay as in 2.2. XML layouts, adapters and
  View-based visual tests are gone.
- One Navigation 3 shell in `MainActivity` replaces the Fragments and nav
  graphs; widget, notification, shortcut and App Link routes go through one
  shared parser and queue.
- Predictive back on Android 13-15, as on 16: the gesture previews closing a
  screen or a sheet, the start tab shows the back-to-home animation, and My
  ITMO goes back in its web history first.
- The bottom tabs switch by a tap on the bar with a fade through, no swipe;
  each tab keeps its scroll and state. Pushed screens move on the shared X
  axis over the bar, and the predictive Back shows the previous screen under
  the one being closed, already in place when the gesture ends (2026-10-13).
  Back from a pushed screen keeps each tab root's scroll and no longer reloads
  Профиль; Сегодня and its bell refresh when the app returns to the
  foreground, not when a sheet closes over it (2026-10-13).
- The predictive Back on a pushed screen is Material's back preview: the
  screen shrinks to 90 % under the finger and dissolves over 300 ms on
  release, also after a long drag or a fling; tab roots have no preview
  (2026-10-14).
- Smaller changes: the `.ics` sheet buttons stand side by side when they fit;
  deleting an own link asks first; report dialogs keep their text across
  rotation; the review editor's button stays above the keyboard; another
  user's sport offers `Открыть на карте`; the My ITMO top bar no longer shows
  the current site.

### Appearance

- Android gets the new shell of the redesign: five tabs Зачётка, Расписание, Сегодня, Спорт, Профиль in the
  Material 3 Expressive bar, a static header with the tab's name that the content scrolls under (Сегодня shows the
  date and the bell), the `Пропуск` button on Сегодня that opens the pass in a sheet (code, name, group and ISU, when
  it was loaded, the last code offline), sheets of three sizes, and `My ITMO` as a row of Профиль.
- Сегодня on Android replaces the home feed: the hero with the lesson to go to now (the room in large type with the
  building's address, the time left on a wavy bar, or `Онлайн` with `Подключиться`), the whole day as a timeline
  (done lessons dimmed, the current one tinted, `далее`, `изменена` with the old room struck through, `отменена`,
  `очередь`, `записаны`, `Окно 2 ч 10 мин`, the now marker), tomorrow in the evening, and `Нужно сделать` only when
  there is something (`Войдите в БАРС заново`). The hints and the `Главный экран` settings page are gone.
- The bell opens a sheet with new marks and their values (`Матанализ: контрольная работа 2`, `9 из 10, итого 74`,
  `+9`), schedule changes as `было` over `стало`, sport queue results and friend requests with `Принять` and
  `Отклонить`; each row leads to its subject, lesson, Спорт or person. Opening it reads everything, new events are
  marked with a dot and bold, the badge counts the unseen ones. The schedule changes history screen is gone; a change
  notification opens the bell.
- Сегодня and the bell's sheet are native SwiftUI screens on iOS over the same view models, under the same static
  header; the notification service leaves sport queue results for the bell, so they show on both
  platforms.
- A lesson whose room is a placeholder such as `дистант` counts as online on every lesson list and in the hero: no
  room line, the `онлайн` pill. The hero's large room or `Онлайн` never clips: the address or `Подключиться` goes
  under it on a narrow screen or with large text. Prominent buttons on iOS keep a readable label in the dark theme.
- A marks notification about one subject carries the value in its title (`Матанализ: 9 из 10`); the lock screen
  still shows `Новые оценки` only.
- Зачётка is rebuilt on both platforms (Compose on Android, native SwiftUI on iOS, one view model): what needs
  action first, each number over a quiet source (`БАРС`, `My ITMO`, `таблица`, `Спорт`) with a 0-100 bar in the tone
  of its reason, passed subjects as a result pill, one list with `Сдано N из M` and the average only near the end of
  the semester and in the session, the period in the header (`3 семестр`). BARS is optional: without it the list is complete from My ITMO
  with one quiet `Подключить` line that can be hidden; an ended BARS session keeps its numbers dimmed with their date
  and offers `Войти`. Физкультура shows the sport points in a ring and opens Спорт. A tap on a source opens `Откуда
  баллы` with every source side by side. The BARS chip and the period sheet are gone.
- The subject page has the tabs `Баллы` (the points over the grade scale with `до 3D ещё 4,5`, the connected sheet,
  checkpoints by group with their minimums), `Пары` (teachers as rows with the tone of their reviews in words, the
  lessons ahead) and `Материалы` (chats, links by category with votes, own links with their review state; a gate
  without `Подключение к ITMO.Widgets`). The My ITMO LMS link is no longer shown.
- Расписание is rebuilt on both platforms (Compose on Android, native SwiftUI on iOS, one view model): an endless
  feed of days with sticky day headers under a week strip that swipes by weeks, the month in the header opens a
  month sheet with a dot per lesson and `Открыть`, `Сегодня` appears off the current week, days without lessons are
  a short `пар нет`, weekends collapse, a booked sport lesson is a lesson with its status, and a change sits on its
  lesson. The friends button opens `Чьё расписание`: one tap, avatars, where each friend is now; a friend whose
  schedule is closed is dimmed. A friend's schedule is the same feed with `Вы` on shared lessons and `Вернуться к
  своему`. A lesson opens one lesson sheet everywhere: the time, room and building in large type, only the actions
  that apply (`Видео`, `Друзья`), the teacher with the tone of the reviews in words, a sport queue as a row to Спорт.
  The old schedule screen, the lesson details sheet and the friend selector with its recent friends are gone.
- Overlapping lessons on every day timeline: one now marker, a rail that only fills forward, the lessons in start
  order without an `Окно` between them and a quiet `одновременно с «...»` on the later one. A room booking shows
  `Бронь помещения` instead of `Пара`. The static root header on Android no longer lets scrolled content show
  through above it, and the iOS root header is opaque.
- Спорт is rebuilt on both platforms (Compose on Android, native SwiftUI on iOS, one view model per screen): `Моё`
  and `Запись` as a segmented choice under the static header instead of the pager. `Моё` has the score ring (it
  opens `Откуда баллы`), bookings and queues as lesson cards by day (`записаны`, `очередь 2 из 5`, `ждём
  расписание`), `Сделать секцией` under booked lessons and the user's sections. `Запись` has a search by sport or teacher, chips (`Есть места`,
  `Утро`, `Вечер`, `Друзья`), a week strip that pages by weeks and dense rows with `22 из 24`, friends,
  a clash with a lesson by name and one action; more filters are in a sheet. The lesson sheet shows the places large,
  one main action, the conditions and the clash as an orange line that does not block. `Встать в очередь?` is a sheet
  with the steps, `Пытаться до самого начала` and the queues left. Sections replace the
  device-local `Мои секции`: a booked lesson becomes a section (in its card or its sheet), and Backend queues the same
  lesson every two weeks as soon as auto-sign for it opens, in subscription order and while the monthly limit has a
  free slot; `Моё` lists the sections with the place (`3-й из 7 в секции`), an off switch and the next three dates,
  each of which can be skipped. `Только мои секции` in the filters sheet of `Запись` keeps the lessons of the sections. `Откуда баллы` shows visits plus the bonus counted up to 40 and My ITMO's
  awards by month with the day the credit was reached; no forecast. Another user's sport shows lesson cards.
- `Запись` filters: the sheet shows six sport types first (the ones the student books or attended, then the most
  booked) with `Ещё` for the rest and a search over them; chosen types stay in sight. The building filter lists every
  building with lessons, `Другие корпуса` for venues My ITMO's list lacks and `Онлайн`. The week strip runs from this
  week to the week of the last published or predicted lesson, at least to the next week. The chosen day's
  indicator slides to a new day on a spring and the colours crossfade (none under reduced motion), and the
  places bar in the rows is twice as wide.
- New settings page `Оформление` with a live sample: `Цвет оформления` (`Как
  обои` on Android 12+, `Фирменный`, six presets or `Свой цвет`), `Стиль
  палитры`, `Контраст` and `Чёрный фон` for the dark theme. The defaults keep
  the look of 2.2.
- Material 3 Expressive: below Android 12 and on iOS the app is painted in
  the brand blue `#4984E2` instead of the baseline purple, every bottom tab
  shows its label, a few hero moments get expressive motion, and waits show
  the morphing loading indicator. Under reduced motion everything shows its
  end state at once.
- New switch `Виджеты в цвет темы` (off by default) paints the schedule and QR
  widgets in the app's colours on Android and iOS; with `Динамические цвета`
  off the QR code stays black on white.
- Headers are one line on both platforms: the tab's title and the date on one baseline (the date goes under it when
  it does not fit), and on a pushed screen such as a subject the title and its subtitle flow as one paragraph that
  wraps in full instead of being cut. The Сегодня hero joins an online lesson with a small `Войти` next to
  `Онлайн`. Progress tracks in cards are a soft groove in the card's own colour instead of a dark bar, and tonal
  buttons such as `Записаться` in Спорт are in the accent colour instead of a grey that looked disabled.
- Every icon is a Material Symbols Rounded glyph, and the selected bottom tab
  shows its filled variant. On iOS the shared screens use iOS controls, text
  styles and haptics.
- The app speaks Russian on a phone in any language: Russian plurals (`5
  пар`, `Ещё 2 балла`) and library texts, notifications, widgets and the QR
  tile included.

### Fixes

- iOS names its channel in `X-App-Version`: `sideload`, `testflight` or
  `appstore` (`dev` for debug builds) instead of `appstore` for every release
  build, so the admin tells sideloaded builds from store ones.
- Android sheets follow the finger when dragged down instead of jittering as
  they leave the status bar, and no sheet goes under the status bar or a
  cutout any more.
- Connecting BARS right after signing in no longer asks for the ITMO.ID
  password again: the sign-in keeps its ITMO.ID session in the app's browser,
  and BARS signs in with it on Android and iOS.
- Зачётка and the subject page show a connected sheet's total again when the subject has an empty BARS journal: the
  most current source with a value wins (the sheet, then BARS, then a sheet that failed to update, then My ITMO),
  and a source without points never hides one with points.
- The schedule widgets show lessons again on Android 8-11; the day-list
  widget no longer shows an older list after a refresh; on Android 12+ both
  schedule widgets follow the launcher's corner radius.
- When ITMO.ID rejects the refresh token while the app runs, the session ends
  with `Сессия истекла. Войдите снова, чтобы продолжить.`; a timeout or a
  server error keeps the session.
- Sign-out also forgets the sport points, auto-sign limits, queues and
  friends' bookings, so the next account or the demo never sees them.
- The prediction of a lesson you are booked on no longer shows as booked: it
  offers auto-sign again.
- A setting changed while a screen starts watching it is no longer lost until
  restart.
- The `Запись` week strip no longer flashes grey when the selection moves to
  or from a day without lessons.
- Links and system pages opened from the new screens (privacy policy, account
  deletion, subject links, calendar settings) work again after 2.3.0-beta.1.
- Android no longer deletes the stored session when the Keystore or the token
  file fails for a moment: only content that cannot be parsed or
  authenticated is discarded, so a transient error no longer signs the user
  out for good. The file format is unchanged.
- A schedule or QR cache that cannot be deleted now fails the sign-in instead
  of being ignored, so the next account never sees the previous user's or a
  friend's schedule or the previous QR pass.
- iOS keeps the session and BARS Keychain items on this device only
  (`AfterFirstUnlockThisDeviceOnly`), so a backup no longer restores them on
  another device; existing items move over on their next write.
- The debug refresh-token tool signs in through the session like any other
  sign-in instead of writing the token file directly.
- The link editor's `Кто видит` shows `после проверки` under each flow while
  premoderation is on: Backend publishes a flow link after an ISU check (or a
  moderator for big flows), no longer at once.

### Architecture and build

- 16 shared Kotlin Multiplatform modules with iOS targets hold each feature's
  domain, data, view models and screens; `:shared:designsystem` is the one
  source of the look and exports its tokens for iOS and the web app.
- The data layer runs on MyItmoApi 2.x and Core 2.0 over Ktor; Retrofit,
  Gson, MyItmoApi 1.x and Core 1.x are no longer app dependencies. Core 2.0 is
  built in as `:shared:backend-client` and tested against Backend's golden
  fixtures and OpenAPI snapshot; privacy fields have no wire defaults, so a
  missing one fails instead of widening access.
- Every Backend request carries `X-App-Version` (version, build, platform,
  distribution), and the device registers again once after an update. Every
  Backend call checks the services opt-in and the demo session in one place.
- Koin is the only dependency-injection graph; Hilt, Dagger and KSP are gone.
- `UiText` carries generated string keys and `:shared:core` builds without
  Compose; `:shared:compose-strings` resolves the keys for Compose.
- Files and settings stay compatible with 2.2 (caches, change history, widget
  snapshot, QR pass, tokens); `UpgradeFrom22Test` and
  `StableIdentifiersTest` guard the upgrade.
- Gradle 9.7, AGP 9.3, Kotlin 2.4.20, compileSdk 37, minSdk 26, targetSdk 36;
  MyItmoApi comes from Maven Central (`MYITMOAPI_DIR` builds against a local
  checkout). The QR encoder is a Kotlin port, Google Sheets parse
  with Ksoup, avatars load with Coil 3, OkHttp is 5.5.
- MyItmoApi 2.1.0: MyITMO's HTTP 403 means no access, not an ended session.
- Release builds ship a baseline and a startup profile generated from the demo
  session: startup, the tabs, sheets and pushed screens run compiled ahead of
  time (from install on Google Play, after the first background dexopt for the
  GitHub APK) instead of interpreted. Frame-timing benchmarks of tab taps and
  of a push and pop run nightly.
- `scripts/verify.sh` is the one build entry point. A PR runs the unit and
  host tests, Konsist, the debug APK and the Roborazzi screenshot baselines of
  the modules it changes (four Android appearances, plus iOS ones for the
  design system) in about 10 minutes; both lints and every screenshot run on
  `master`, the instrumented tests on GitHub's emulators and the release checks
  nightly, and everything on a PR labelled `full-ci`. The Konsist architecture
  rules cover the app and every shared module.
- Developer documentation: `docs/getting-started.md`, `docs/feature-flow.md`,
  `docs/testing.md`, `docs/troubleshooting.md` and `CONTRIBUTING.md`; the
  architecture and feature docs describe the Compose, Koin and Navigation 3
  code.
- `Подключить устройство` replaces `Вход на сайт` on the profile: the same
  sheet signs a browser in and connects a self-hosted agent from its
  `https://<host>/pair#v1...` QR, a pasted link or an Android App Link. The
  card shows the device's own name, version, permissions, the shared My ITMO
  session and a verification code; after the screen lock the phone hands its
  previous ITMO session over HPKE (Tink on Android, CryptoKit on iOS) and
  Backend sees only ciphertext. Typed codes work only for the site.
- `Подключённые устройства` (from the sheet, `Настройки → Доступ` and the new
  push) lists web sessions and agents with `Отключить`. Pushes
  `DEVICE_CONNECTED_PAYLOAD` and `SPORT_AGENT_BOOKED_PAYLOAD` show on Android
  (channel `Подключения устройств`) and iOS.

## 2.2 — 2026-10-03

### 2026-10-03

- Pre-release visual pass (full matrix on emulator-5554) fixes: the home and
  schedule lists end with `design_fab_stack_clearance` so the last item
  scrolls clear of the FABs on short screens; `Принять заявку` and `Отклонить`
  stack instead of breaking inside a word (`ButtonRow`); a wrapped review
  author, source or `Анонимный отзыв` no longer overlaps the review text or
  runs past its row; the `.ics` sheet keeps one height at font scale 1.3;
  long lesson titles, teacher names, schedule change lines, summary scale
  names, the profile name and the sport points card labels hyphenate instead
  of breaking mid-word; `и ещё N`
  in the marks digest keeps the number with its words.
- Demo: the sport sign page always has a lesson to sign up for today, also
  late in the evening and on Sundays.

### 2026-10-02

- A hidden demo session for the Google Play review: five quick taps on the
  sign-in logo open the app as the fictional student «Анна Смирнова» with one
  consistent set of schedule, sport, recordbook, friends, links and reviews
  (`core/demo`, each feature's `data/demo`). No tokens, nothing reaches the
  network, no device registration, background work or update offer; writes
  and outside pages answer `Недоступно в демо`. The banner `Демо-режим ·
  вымышленные данные` with `Войти` sits above the bottom bar; the session
  survives process death until sign-out. Landing screenshots
  (`SiteScreenshotCapture`) and the debug home fixture read the same set.
- Every class that takes a My ITMO, BARS, Backend or public web client checks
  the demo gate before the call; the Konsist rule `network clients are gated by
  demo mode` enforces it, `debug code is gated` pins the `BuildConfig.DEBUG`
  checks of the debug stores and screens. The Konsist suite filters agent
  worktrees by the project path, so it no longer passes vacuously inside one.
- `Удалить аккаунт ITMO.Widgets` on the `Подключение к ITMO.Widgets` page
  (also with the switch off) opens `/delete-account` on the site; deletion is
  a request handled by hand with Backend's runbook. `Политика
  конфиденциальности` in `Обслуживание` opens `/privacy.html`; that group and
  the sign-in screen say `Неофициальное приложение. Не связано с
  Университетом ИТМО.`
- A shared link whose author deleted the account no longer offers the
  author's profile.
- Two distribution variants with one `applicationId` and key: `github` (APK,
  «Обновить» opens the latest GitHub release) and `play` (AAB, Google Play
  In-App Updates: flexible in the background with the `Обновление загружено` /
  `Перезапустить` snackbar, immediate below the minimum version, otherwise the
  Play card). `BuildConfig.DOWNLOAD_URL` replaces `latest_release_url`, also in
  the people-search invite. Gradle tasks now carry the variant
  (`testGithubDebugUnitTest`, `assembleGithubRelease`, `bundlePlayRelease`).
- WorkManager's unused foreground service and `FOREGROUND_SERVICE` are removed
  from the manifest. `scripts/check-play-policy.sh` checks the release
  manifests, `targetSdk` 36, 16 KB alignment and that the `play` bundle has no
  GitHub update link.
- Shared links open in the app: `https://widgets.alllexey.dev/u/{isu}` the
  person profile above the profile tab, `/sport/{lessonId}` the lesson card on
  `Запись` with its day selected, `/sport/p/{prototypeLessonId}` a predicted
  lesson rebuilt from the recipient's catalog (or its real repeat once it
  exists). Verified App Links on both hosts; the route waits for sign-in and
  the first-run flow and is not repeated from Recents. A damaged link shows
  `Ссылка не открывается`, an ended or missing lesson `Занятие недоступно`.
- `Поделиться` in the person profile, beside the own name on the profile tab
  and in the sport lesson card (real lessons, bookings and predictions that
  have not ended) opens the Sharesheet with a text and the link; debug builds
  share `dev.widgets.alllexey.dev` links.
- `Синхронизация с календарём` on the `Расписание` settings page keeps the
  own My ITMO schedule of today and the next 28 days in the app's own calendar
  `ITMO.Widgets` on the phone, which calendar apps reading the phone's
  calendars (Xiaomi, Samsung) show and Google and Yandex Calendar do not. Every
  lesson of the answer goes as it is, sport and room bookings included; new
  lessons are added, changed ones updated, vanished ones removed, past events
  stay, repeated syncs add no duplicates. It runs every 2 hours and right
  after turning on or a pull on the own schedule; turning it off deletes the
  calendar. A revoked permission or a deleted calendar turns it off with the
  reason under the switch. The calendar permission is asked only there.
- Google-account calendars are not written: Google brought back events the
  app deleted in bulk (Android's guard against too many deletions). A Google
  calendar picked in an earlier development build is left: the app's events
  there are deleted and swept again by the tag `ITMO.Widgets · <lesson>` in
  their description for 3 days, and synchronization is off until turned on.
- `Выгрузить в .ics` on the same page opens a sheet: a week, two weeks, the
  rest of the semester or chosen dates, each with its days; then the file
  (an RFC 5545 calendar with stable UIDs) with `Отправить`, `Открыть в
  календаре` and the advice to import it into a separate calendar; an empty
  range and a failure stay in the same sheet. The calendar permission is
  explained in a short Material dialog with `Разрешить` or, after a refusal for
  good, `Открыть настройки`.
- Core, Backend and MyItmoApi are unchanged for the calendar.
- The «QR-пропуск» quick-settings tile opens the QR pass above home; it is
  active with a session, and on a locked device it asks to unlock first. On
  Android 13+ the `Виджет QR-кода` settings page offers `Добавить в шторку`
  until the tile is added.
- Static app shortcuts «QR-пропуск» and «Сегодня»; «Сегодня» opens the own
  schedule on today's day, also from a friend's schedule.
- Widget, notification, tile and shortcut taps share one route queue: a route
  runs once, waits for sign-in and the first-run flow, and is not repeated when
  the app is reopened from Recents; Back leads home.
- After the first-run flow home becomes the navigation graph's start, so tabs
  keep their scroll and state instead of piling up in the back stack until the
  app restarts.
- Core, Backend and MyItmoApi are unchanged.
- The link actions sheet names the author of another student's link («Автор: …»);
  the row opens their profile.
- A link's second line says who sees it (`Все` or the flow) for others' links too,
  instead of the author's group.
- The link actions sheet votes with the same pill as the lists, at the end of its title;
  the vertical arrow column, whose arrows had come to overlap the score, is gone.
- Connected groups and the AI summary card moved to the quieter
  `colorSurfaceContainerLow` of schedule days; the hero cards keep
  `colorSurfaceContainer` and groups inside the links sheet stay on
  `colorSurfaceContainerHigh`.
- A review's footer stacks who wrote it over its verification («✓ Вёл у
  автора» or «Не подтверждён» as small text, no chip) with the votes centred
  beside; nothing ends flush with a row's edge.
- Who wrote a review (the author, the source link or `Анонимный отзыв`) moved
  from its top into its footer, before the verification and left of the votes;
  the top line is the caption with `⋮`. The own review is a group of its own
  above the others and every review row ends with the same padding.
- A vote no longer moves a row: profile reviews, the links sheet and the
  subject page's three links keep the order they were shown in until the screen
  is opened again or refreshed by hand.

### 2026-10-01

- The person profile follows the subject page: a result card with the photo,
  the name, one short line (`Преподаватель` instead of the full position and
  department, `M3234, 2 курс` for a student) and the ISU number that copies on
  a tap; the friendship badge, status and buttons sit in the card. `Должности`,
  `Где найти`, `ITMO.Widgets` (with `Удалить из друзей` for a friend) and
  `Учёба` are headings over connected groups; the `ИСУ` row is gone.
- Reviews are rows of one group under `Отзывы N` and `Написать`: the own one
  with `мой` and its status, others headed by the author, the Reviews source
  link or `Анонимный отзыв`, with the vote pill `▲ N ▼`. Captions use commas
  instead of « · ».

- The subject page is a result card and connected groups under accent
  headings: `Ссылки`, `Чаты`, `Контрольные точки`, `Преподаватели`,
  `Ближайшие пары`. The sheet total (or `Мои баллы из таблицы`) moved into the
  result card under a hairline. Links are rows instead of chips: at most three,
  the own one marked `моя`, others with the vote pill `▲ N ▼` that votes from
  the page, then `Все ссылки, N`, or `Добавить ссылку` without links. Control
  groups are headings with their sum over their own group of controls.
- The links sheet groups every category the same way; own and others' links
  share one row style (`моя` instead of a tonal row, the pill instead of the
  vote column), captions name the review state of an own link.
- « · » is gone from these screens: `Экзамен, 2 семестр`, `Все пары, N`,
  `путь, лист «Лист»`, comma-separated captions.

- `Мои баллы` for any subject link whose address is a public Google Sheet:
  the app downloads every tab on the device (CSV, or the HTML view when the
  export is forbidden; at most 5 MiB per answer), finds the own row by the ISU
  or the ITMO.ID name and the total by its header, and asks only when the sheet
  leaves a choice of row, tab or total. One connection per subject period,
  stored in `filesDir/sheet_scores/state.json`; names and marks of others are
  never stored, nothing reaches Backend, and `Подключение к ITMO.Widgets` is
  not needed (decision [0014](docs/decisions/0014-sheet-scores-on-device.md)).
- The subject page shows the total under `Баллы` with the tab, the header and
  when it was read, `Нет связи`, `Таблица закрыта`, `Строка не найдена`,
  `Столбец не найден` or `Таблица слишком большая`, and `Открыть таблицу`,
  `Изменить итог`, `Отключить`; a subject with sheet links and no connection
  offers `Мои баллы из таблицы` (`Какая таблица?` for several). The recordbook
  list shows the total with a table mark while My ITMO and BARS have no points.
  A long list of people to choose from has `Поиск по фамилии`.
- Subject links have `Скопировать ссылку` in their actions.
- A changed total is a `Новые оценки` subject of the existing mark check
  behind the new switch `Оценки из таблиц` on the `Зачётка` page, on by
  default; a total already seen in the app is not notified.
- New explicit dependencies: OkHttp 4.12.0 (it came transitively before) and
  Jsoup 1.21.1. Core, Backend and MyItmoApi are unchanged.
- The app checks the own marks of the current half-year by itself: every
  3 hours with a network one background check reads the My ITMO recordbook
  (points and grade of every subject) and the BARS journals (checkpoint marks,
  additional points, the statement) and compares them with the last snapshot
  on the device. The first answer of each source after installation, sign-in,
  switching on or a new half-year only takes a snapshot; what is missing from
  an answer is carried over, so a short answer is never news. Android picks the
  moment of a check, so delivery is not immediate. Snapshots and unread
  subjects live in `filesDir/marks/state.json`; nothing reaches Backend, and
  the check does not need `Подключение к ITMO.Widgets`.
- One notification per check in the new `Оценки` channel: `Новые оценки` with
  the names of the unread subjects, up to three and `… и ещё N`, never the
  marks; the lock screen shows the title only. A subject that changed in both
  sources is named once, and a My ITMO change that only repeats BARS is not
  notified. Nothing from 00:00 to 06:00 Moscow time. A tap opens the subject
  page when one subject is unread, otherwise the recordbook.
- BARS is renewed in the background without a WebView, by repeating the
  official ITMO.ID request with the WebView's cookies (decision
  [0012](docs/decisions/0012-bars-background-renewal.md)); the check selects
  the user's BARS period back after reading. When the ITMO.ID session has
  ended, one `Войдите в БАРС` notification opens the BARS sign-in, and nothing
  more until BARS answers again.
- The home card `Новые оценки` after `Изменения в расписании`: the number of
  unread subjects and their names; a tap opens the recordbook, the close button
  marks everything read. It can be hidden in `Главный экран`.
- In the recordbook a subject with unread marks has a dot until its page is
  opened; a list opened in the app advances the snapshots, so a mark already
  seen there is not notified later.
- The settings page `Зачётка`: `Оценки My ITMO`, on by default, and
  `Оценки БАРС`, which appears and turns on with the first successful BARS
  answer. Off forgets that source's snapshot; on without the notification
  permission asks for it.
- Both background checks (marks and schedule changes) treat a network failure
  before any answer as temporary, including a My ITMO token refresh that could
  not reach ITMO.ID: the run is retried, the snapshot stays, and nobody is asked
  to sign in. On Xiaomi the default battery mode cuts the network of a
  backgrounded app, so the `Расписание` and `Зачётка` settings show
  `Работа в фоне` (`Разрешите работу без ограничений.`) while Android restricts
  the app; it opens `Контроль активности` on Xiaomi and the battery
  optimisation list elsewhere. Turning a check on while restricted shows a
  one-time dialog.
- Debug tools: `Проверить оценки` runs one mark check,
  `Проверить продление БАРС` probes the cookie renewal and writes only its
  outcome to logcat.
- Needs MyItmoApi 1.8.2-SNAPSHOT (Maven Local) with
  `BarsAuthHelper.requestCodeWithCookies`; Core and Backend are unchanged.

### 2026-09-30

- The app checks the own schedule for changes by itself: every 2 hours with a
  network it asks My ITMO for today and the next 7 days and compares the
  academic lessons with the last snapshot on the device. Added, cancelled and
  moved lessons and changes of room, format and teacher are kept for 30 days in
  `filesDir/schedule_changes/state.json`; the first check after installation,
  sign-in or switching on only takes a snapshot, a single empty answer is held
  once, a new term landing on empty weeks is not a list of additions, and
  changes of lessons already over are dropped. Android picks the moment of a
  check, so delivery is not immediate. Nothing reaches Backend; see decision
  [0013](docs/decisions/0013-schedule-changes-on-device.md).
- One notification per check in the new `Изменения расписания` channel,
  `Расписание изменилось: 3 пары` with the nearest change; with a sound only
  for today and tomorrow, nothing from 00:00 to 06:00 Moscow time (the first
  check after 06:00 delivers it). A tap opens the history over the schedule.
- The history `Изменения в расписании` groups changes by the day they were
  found, marks new ones with a dot and marks everything read while open. A
  change of one field reads as its `было → стало` line.
- The home card `Изменения в расписании` after `Сегодня`/`Завтра`: the number
  of unread changes and the latest one; the close button marks them read. It
  can be hidden in `Главный экран`.
- Changed lessons get a small mark in the own and in friends' schedules, and
  the lesson sheet shows `Изменения` with `было → стало` lines.
- `Изменения расписания` in the schedule settings, on by default: off cancels
  the check and forgets the snapshot, the history stays; on without the
  notification permission asks for it. An untitled settings group after
  another one now keeps the group gap.
- The lesson sheet shows the My ITMO flow of the lesson as `Поток` between the
  teacher and the room.
- Debug tools: `Проверить изменения расписания` runs one check.
- Core, Backend and MyItmoApi are unchanged.

### 2026-09-29

- Own teacher reviews in the person profile: `Написать` in the `Отзывы · N`
  heading when the viewer may write, has no review of the person yet and the
  person teaches (Backend's `knownTeacher` or a My ITMO position). The editor
  sheet (`Новый отзыв` / `Изменить отзыв` with the teacher's short name below)
  has an optional subject with filter-chip suggestions from the viewer's own
  lessons with the teacher from the personal schedule, a text of 30–3000
  characters and `Анонимно` on by default; unsaved changes ask
  `Не сохранять отзыв?`. Every version waits for moderation.
- The own review comes first on an outlined card with its status
  (`На проверке`, `Отклонён` with the reason, `Скрыт`), anonymity and, once
  published, its score; its menu edits or deletes it.
- Others' reviews and the Reviews copies are one list in Backend's order with
  +1/−1 votes. A named author heads the card as a link to their profile;
  `Вёл у автора` marks reviews Backend verified through ISU, every other own
  review shows `Не подтверждён`. `Пожаловаться` in the menu opens a report with
  `Оскорбления`, `Не тот преподаватель`, `Спам` or `Другое` and a comment.
- Mutations update every open profile of the teacher without a reload.
- Review suggestions and candidate flows come from up to 17 sampled weeks of the
  personal schedule instead of 8 whole study periods one after another: in the
  current and the 3 previous academic years the weeks of 25 September,
  3 December, 24 February and 5 March, plus the current week. All weeks are
  asked at once and suggestions appear as they answer; a save sends the flows
  collected by then. Finished weeks are kept in
  `filesDir/teacher_lessons/weeks.json` until the session is cleared and come
  without a request.
- The profile header shows one line under the name (position with a short
  department, or the group); `ИСУ N` moved to the end of the facts card.
- Needs Core and Backend `1.7.0-SNAPSHOT` with `saveMyTeacherReview` and V9
  (`V9__teacher_reviews.sql`, after `V8__service_credentials.sql`); an older
  Backend answers with `external`, which this build does not read.
- AI summary of a teacher's reviews first in the profile's `Отзывы`, on a tonal
  card (`Card.Content.Tonal`): `Сводка по N отзывам` with `ИИ`, the tone of the
  reviews with a coloured dot when Backend is confident enough, a short
  description, pros and cons, tags, and five scales in words collapsed by
  default behind `Подробнее`/`Свернуть`. The open state lives in the profile's
  saved state, so it survives recreation. Unknown tags are skipped.
- A tone dot next to the teacher in the lesson sheet and in the subject page's
  teacher rows, with its place reserved so a late dot moves nothing. Tones come
  from `GET /api/teachers/summary-levels` in batches of 50 behind the
  connection and are kept for a day in `filesDir/teacher_levels/levels.json`,
  cleared with the session.
- Needs Core and Backend `1.7.0-SNAPSHOT` with `summary`,
  `teacherSummaryLevels` and V10 (`V10__teacher_summaries.sql`); without V10 the
  reviews come without a summary and teachers without dots. See decision
  [0011](docs/decisions/0011-ai-review-summaries.md).

### 2026-09-28

- One person profile opens for any ISU: the device loads My ITMO name, photo,
  positions, rooms and education; registered users keep their ITMO.Widgets
  relationship and sharing block. Contacts, gender and exchange status are not
  shown. The endpoint's observed HTTP 400 / numeric 100 / explicit null result
  is interpreted as missing only in the personality repository.
- Older anonymous Reviews text appears at the bottom of the profile with its
  optional subject, month or before-year date and source link, without ratings
  or truncation. Needs Core and Backend `1.7.0-SNAPSHOT` with `teacherReviews`
  and `GET /api/teachers/{isu}/reviews` (existing Backend V7 schema).
- Teachers in lesson, pending-sport and sport details and on subject pages open
  the same profile when they have an ISU; both people-search sections do too.
  Informational rows without an ISU and friend-sport cards remain read-only.
- Backend profile/friends/review caches stay behind the connection and clear on
  opt-out or sign-out; local generations reject older replies after off/on.
  `SocialRepositoryImpl` and all session-cleaning repository implementations
  are singletons shared with their cleaners.
- The composite profile waits for complete initial parts, with a 3-second
  deadline once an identity is ready. Late reviews append; late identity blocks
  wait for retry instead of moving visible content. One snackbar reports partial
  failure. Avatar image errors show current initials.
- Synthetic emulator tests cover the profile, entry points, delayed replies,
  recycling, recreation, accessibility and full appearance matrices. Instrumented
  runs preserve installed APKs/data; snapshots wait for completed transitions.

### 2026-09-24

- `Добавить к себе` is gone: chips and rows are ranked by score, so saving
  another student's link changed nothing. The `+`/check button of list rows,
  `Добавить к себе` / `Убрать из своих` in the link actions sheet,
  `SubjectLink.isSaved` and `SubjectLinksRepository.setSaved` are removed.
  Needs Core and Backend `1.7.0-SNAPSHOT` without `setSubjectLinkSaved`
  (Backend `V6__drop_subject_link_saves.sql`).
- An own row in the links sheet is quieter: `colorSurfaceContainer`, one tonal
  step above the sheet's `colorSurfaceContainerLow`, instead of
  `colorSurfaceContainerHigh`. Subject page chips are unchanged.

- A link is shared with one schedule flow of the subject instead of the fixed
  `Группа` and `Поток`: `Кто видит` lists `Только я`, every flow of the viewer
  by nesting (`ФИЗ ПИИКТ 3`, `3.2`, `3.2.1`) with its kind of classes, and
  `Все`. Flow links are labelled with the flow name. Needs Core and Backend
  `1.7.0-SNAPSHOT` with `LinkVisibility` `PRIVATE`, `FLOW`, `ALL` and `flowId`.
- `Вход на сайт` in the profile (only with `Подключение к ITMO.Widgets`) signs a
  browser in to the web version at `/app/`: scan the QR with Google's code
  scanner (no camera permission, `barcode_ui` module installed with the app) or
  type the eight-character code, check the browser (`Chrome на macOS`) and the
  request time, then `Войти`. Codes live 2 minutes and are approved only with
  the app's ITMO.ID token. New `core/weblogin` and `feature/weblogin`; Core
  `1.7.0-SNAPSHOT` adds `webLoginPreview`, `approveWebLogin` and `myRoles`;
  Backend `1.7.0-SNAPSHOT` with `V5__web_sessions_and_admin.sql` is required.
- `expandToContent()` moved to `core/ui/BottomSheets.kt` for every sheet;
  `UiText.resolve()` resolves `UiText` arguments.
- `Кто видит` is ordered from the widest audience to the narrowest: `Все`, the
  flows from broad to nested, then `Только я`.
- Public `t.me` links (channels, posts, invites) open in the Telegram client
  through `tg://` (`core/util/TelegramLinks`) instead of the browser.
- The subject page's links start with a `Ссылки` header whose `Все` opens the
  links sheet even without links; `Ещё N` and `+` stay.
- Own links are no longer ranked first: chips go pin, `LMS`, then own, added
  and shared links together by score (newer first on ties), replacing the old
  own / added / flow / best-public-per-category order; the links sheet ranks
  each category the same way. Own chips are filled, own rows sit on a rounded
  `colorSurfaceContainerHigh` surface.
- The long-press sheet of another student's link has the vote arrows and the
  live score of the list row; an own shared link shows its score. A vote keeps
  the sheet open.

### 2026-09-23

- Trimmed app copy: no pull-to-refresh or tap instructions, no settings
  descriptions and footers that repeat their titles, shorter recordbook source,
  diagnostics, consent, privacy and auto-sign texts, title-only empty states
  where the title is enough.
- One name per thing: `My ITMO` for the university site and
  `Подключение к ITMO.Widgets` for the server opt-in.
- Builds as `2.2-SNAPSHOT` on Core `1.7.0-SNAPSHOT`; Backend `1.7.0-SNAPSHOT`
  with the `V4__subject_links.sql` schema is required for links.
- Recordbook rows are compact: the points with a thin bar, or a grade badge once
  the result is final, instead of rings. `Требуют внимания` lists no-shows,
  subjects with a known control under its minimum (from MyITMO or BARS controls
  already loaded), failed subjects and PE short of 100 sport points in the last
  28 days of its period or after it. The summary appears only in the session;
  the source info button is gone.
- The subject is one page without tabs: the name in the toolbar, the ITMO grade
  scale (5A/4B/4C/3D/3E, a plain credit at 60) with a hint to the next grade,
  link chips, chats, controls grouped by tree or by numbered names with the sum
  of known scores, teachers, and the two nearest lessons with `Все пары`.
  A control shows its minimum only when it is not met.
- Subject links: a category and a visibility (`Только я`, `Группа`, `Поток`,
  `Все`), chips on the subject page, a sheet with every link, chats and links
  from past years, an editor that pastes the copied link and guesses its
  category, an actions sheet (open, add to own, pin, edit, delete, report) and
  +1/−1 votes. Without the connection links stay private on the device and are
  uploaded on the next refresh with it.
- Shared `LinkOpener` and link texts in `core/ui`; the BARS repository keeps
  the controls it has read; the current sport period carries its end date.
- Instrumentation screenshots are exported before the test APK is uninstalled.

## 2.1.1 — 2026-09-21

### 2026-09-21
- Sign-in follows ITMO.ID to any https page, so VK and the other providers on
  the ITMO.ID page work inside the app; the token is still accepted only on
  `https://my.itmo.ru/login/callback`. The BARS sign-in does the same
  (`core/util/HttpsNavigationPolicy`).
- A user with several groups is shown with the highest-course one everywhere
  (`UserSummary.primaryGroup()`): feed, lesson sheet, profile, `Профиль`.
- Screens keep their content while they refresh instead of replacing it with
  an indicator: both sport tabs carry `refreshing` inside `Content` and a
  failed refresh keeps the last snapshot with a snackbar; the schedule renders
  its memory cache in the first frame (`ScheduleRepository.peekScheduleForRange`);
  the QR pass, the recordbook and its subject hub, a public profile and another
  user's friends open from the last answer (`RecordbookRepository` memory
  cache, `SocialRepository.cachedProfile` / `cachedUserFriends`, all cleared on
  sign-out). `UserFriendsFragment` no longer reloads on every start.
- Automatic refreshes are silent: the first load of a screen, a stale resume,
  a period change and the BARS switch no longer show the pull-to-refresh
  indicator over the content (or after the skeleton); only a pull or
  `Повторить` does. The sport catalogue shows its filters and week calendar
  before the lessons answer (`SportSignUiState.Content.initialLoading`).
- The subject hub shows its `Баллы` / `Расписание` tab strip at once for a
  current period instead of after the subject answers.
- A first load without any cache shows a skeleton (`core/ui/SkeletonView`,
  `SkeletonListAdapter` for the sport catalogue) instead of a circular
  indicator on the feed, schedule, both sport tabs, recordbook, subject hub,
  friends, people search, public profile, another user's friends and sport.
- Version `2.1.1` (version code 5); no Core, Backend or wire change.

## 2.1 — 2026-09-21

### 2026-09-21
- Version `2.1` (version code 4) on Core `1.2.0` and MyItmoApi `1.8.1`, both on
  Maven Central. Release signing reads `keystore.properties` (ignored; see
  `keystore.properties.example`), so `assembleRelease` produces a signed APK.

### 2026-09-20
- First-run flow: the QR widget step offers `Изображение спойлера`, the same
  photo picker and crop screen as settings, with `Выбрать другое` / `Вернуть
  стандартное` once an image is stored. The crop screen and the spoiler image
  contract moved to `core` (`core/ui/spoiler`, `core/settings`), and the
  repository now refreshes the widgets itself instead of the settings ViewModel.
- Home tab: a feed of cards instead of the placeholder. `Сегодня`/`Завтра` with
  the current lesson marked `сейчас`, the rest of the day, pending sport rows
  and a count of finished lessons; `Спорт` with the score progress and own
  queues; `Заявки в друзья`; dismissible hints for a missing widget, disabled
  notifications and disabled user services. Pull-to-refresh asks every source
  at once and a partial failure is one snackbar. Both FABs stay.
  `Настройки → Главный экран` hides card kinds (`home_hidden_cards`).
- The current lesson on the home card is marked by its accented time, the
  `сейчас` badge and a progress line instead of a filled row.
- Widget previews in the launcher picker match the current widgets: rebuilt
  Android 12+ preview layouts for all three widgets (the QR widget had none) and
  preview images rendered from the real widget renderers, with a night variant.
- The second fetch after a sport action (bookings, own schedule, schedule widgets)
  now runs 1 s later instead of 3 s and also reloads the sport catalogue, so a
  lesson that was full no longer stays full after cancelling.
- Every details sheet (lesson, pending sport, sport lesson) now starts with the
  same header: title, kind, the date with the time range and duration, teacher,
  place, `Открыть на карте`. A pending sport row on the home feed or in the
  schedule opens the sport tab's own sheet with the queue position, history and
  `Отменить`, and so does a booked sport lesson (matched by date and start, a confirmed
  booking before a queue for the same slot, then by section name);
  the sport data loads on first use exactly as the sport tab loads it; the schedule's simpler sheet remains only when the sport data has
  nothing about the queue. The QR pass screen shows the code at up to 300 dp
  instead of the full width.
- Subject hub in `Учёба`: a swipeable `Расписание` tab next to `Баллы` with the subject's upcoming lessons
  (next four weeks) with type, room and building, its teachers with the lesson
  types they run, and the LMS link when MyITMO sends one. A discipline whose
  id matches the schedule binds silently; a name-only match is offered as
  `Связать`, several matches ask which one, and confirmed links persist until
  sign-out.
- Lesson details: every schedule card opens a sheet with the subject, type and
  format, time, teacher, room and building, Zoom link and note; `Открыть на
  карте` hands the building to any map app through a `geo:` URI, using a
  curated directory of ITMO buildings with verified coordinates.
- Lessons with a meeting link get `Открыть видеозвонок` (no "Zoom" wording, the
  link itself is not shown) with the Material `videocam` icon, and schedule
  cards mark such lessons with the same icon. Pending sport rows open their own sheet with the status
  explanation, time, place and `Открыть в спорте`.
- `Друзья на паре` inside the sheet: the viewer's friends who attend the same
  lesson and share their schedule, from Backend `GET
  /api/schedule/lessons/{pairId}/friends?date=` (requires Core 1.2.0-SNAPSHOT
  with `friendsOnLesson`). Hidden without the opt-in; a row opens the profile.

### 2026-09-19
- Schedule widgets get a `Размер текста` choice per format (`Обычный`,
  `Крупный`, `Очень крупный`) on their settings pages and on the first-run
  widget steps; the preview follows it.
- After a sport sign-up or cancellation the app fetches the schedule and the
  bookings a second time 3 s later, outside the screen that asked, because
  MyITMO often answers the first fetch with the state from before the change.
- Schedule widget descriptors on Android 12+ ask only for the span the layouts
  need (180 dp minimum, explicit resize bounds, default span through
  `targetCellWidth/Height`), so a launcher with smaller cells has no reason to
  scale the widget down.
- Schedule widgets refresh a second time 3 s after a sport sign-up or
  cancellation, so a MyITMO reply that predates the change no longer leaves
  the widget stale until the next automatic update; failed widget refreshes
  are recorded in the diagnostics journal.
- Instrumentation runs skip the 45 s activity-lifecycle timeout that every
  `launchActivityForResult` close used to wait for.

### 2026-09-17
- First-run flow after sign-in: one screen per widget with its live preview,
  its own settings rows and a pin straight to the launcher; the ITMO.Widgets
  opt-in as a switch with the stored data named in full; the notification
  permission on its own step behind the opt-in. No summary screen. Every
  choice is stored when it is made, so skipping leaves a consistent app. The
  flow is a root destination gated on a per-installation flag that survives
  sign-out; `Повторить первоначальную настройку` in maintenance replays it.
- The single-lesson widget preview is now as tall as the widget; only the day
  list keeps the bounded preview area.
- Signed-out welcome now names what the app does in three lines instead of one
  sentence; the expired-session notice appears only when the session expired.

- People search shows Cyrillic names: MyItmoApi 1.8.1 sends `Accept-Language: ru`
  with every MyITMO request (without it the directory transliterates).

- Friend selector: recent chips no longer jump to the front on each tap. Their
  opening order and pending selection survive recreation; selection updates only
  its markers, and history changes after confirmation for the next opening.

### 2026-09-17
- Identity upload retries through WorkManager after a failure; an empty name
  from Backend renders as `Пользователь ИСУ N` instead of raw text.

### 2026-09-16
- Roadmap: BARS mark tracking and notifications added as Stages 37–38 inside
  v2.2; the QR quick-settings tile and static app shortcuts added to the home-feed
  stages; later stages renumbered to 39–45.
- Roadmap: Stages 11–45 now name files in the package-by-feature layout
  (`feature/<name>/{ui,presentation,domain,data,work}`, `app/`, `core/`) instead
  of the pre-refactor global `data`/`domain` packages; contextual destinations
  point at the overlay graph; Backend migrations renumbered after the existing
  `V3`; Core commands use JDK 17.
- Local diagnostics journal: `core/diagnostics` records warnings, errors and
  uncaught crashes to a bounded file after redacting tokens; `Журнал ошибок`
  in maintenance lists, copies and clears it. Sync, FCM, push and update
  failures that used to go to logcat now land there.

- Home MyITMO FAB opens an origin-restricted embedded browser with history,
  retry and external-browser fallback; web session data is cleared on sign-out
  or native account replacement, without exposing native tokens to JavaScript.
  Both home FABs are icon-only, with localized accessibility descriptions.

- Home QR FAB opens a full-screen pass with cached loading, refresh/retry and
  active cache-expiry handling; widget fallback semantics are unchanged.

- Sport details now offer sign in/out and queue actions in addition to the
  unchanged card controls, with one-shot routing and deadline revalidation.

- Public profiles now open viewer-scoped friends lists; independent friends
  privacy defaults to ALL and supports FRIENDS / NOBODY. Loading, empty, denied,
  retry and refresh-failure states use the shared social row and state styles.
- Profile: notifications and version rows removed (both remain in settings);
  compact GitHub and Telegram buttons with logos above sign-out, Telegram via
  `tg://` deep link with a web fallback.

- Split compact and full schedule-widget settings into independent pages, previews
  and preference keys, preserving existing choices. Early switching is compact-only;
  past/tomorrow controls are full-only; each has separate teacher visibility. The
  full list no longer removes an ongoing lesson because compact switched early.
- Another user's sport no longer waits for the merged sport schedule; it resolves
  confirmed lessons against the raw catalog, so it loads before the sport tab
  was ever opened in the process.
- FCM restored: messaging service, token sync, `sport` and `friends` channels,
  serialized WorkManager processing, sport auto-sign booking from pushes,
  friendship notifications that open the profile. Delivery is guarded by the
  recipient ISU and a signed-in session.
- Documentation restructured: tracked `AGENTS.md`, `docs/README.md` index,
  per-feature documents, decision records, English everywhere except README.

### 2026-09-15
- Social layer: `SocialRepository`, friends and requests screen, people search
  through MyITMO with Backend lookup, public profiles with schedule and sport
  entry points, another user's schedule and sport screens.
- Profile tab: study group, friends card with request badge, privacy,
  notifications and version rows.
- Friend picker aligned with the design language: quiet rows, container
  selection, `Друзья / Все` toggle, profile on long-press.
- BARS overlay on the recordbook behind a chip, using MyItmoApi 1.8.0.
- Resource and naming clean-up: one file per resource, semantic colours, uniform
  layout and view ids, one voice in user-facing strings.
- Real sport venues kept out of the building filter categories.

### 2026-09-08 — 2026-09-09
- Sharing audiences (`Все / Друзья / Никто`) with viewer capabilities from Core.
- Schedule timeline markers aligned; pending sport rows in the own schedule and
  both schedule widgets behind a default-off setting.
- Unified design pass: shared card styles, state styles, refresh helper,
  preserved schedule range and scroll on refresh.
- Sport session cards and details redesigned; collapsing score card.

### 2026-09-07
- Recordbook redesigned with live sport progress for physical education.
- Contextual overlay navigation stack; profile and settings with live widget
  previews; authentication and sign-out.

### 2026-07 — 2026-08
- QR widget and schedule widgets on the refactored architecture.
- Sport sign double-tap guard; friend schedule disabled without services.
