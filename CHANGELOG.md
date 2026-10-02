# Changelog

Reference documents in `docs/` describe the current state; this file records
what changed and when. Unreleased entries describe local development, not a
publication or deployment.

## 2.2 — development

### 2026-10-02

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
