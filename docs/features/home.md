# Home and quick actions

Product behaviour: [Как это работает: расписание](../how-it-works/schedule.md)
(home feed), [QR-пропуск](../how-it-works/qr.md) (tile and shortcuts).

The home tab is a feed of cards built from what the app already knows, plus
two FABs (QR pass, `My ITMO`).

## Feed

- Contract in `core/home`: `HomeCard` (`Schedule`, `ScheduleChanges`,
  `Marks`, `Sport`, `FriendRequests`, `Hint`) and `HomeCardSource`
  (`observe`, `refresh`, `revalidate`, `dismiss(kind)`). `dismiss` is passed
  to every source; only the owner of that kind acts.
- Each feature registers its source in its `data` package as a qualified Koin
  `single<HomeCardSource>`, and its card in `ui/home` as a qualified
  `single<HomeCardRenderer>` (`kinds` + `@Composable Content`; a Compose
  interface, so it sits in `:shared:designsystem` under `core/home`). Every
  kind has exactly one renderer (`HomeRenderersGraphTest`); an unclaimed card
  is left out. Nothing in `feature/home` imports another feature.
- `HomeViewModel` (`:shared:feature-home`, `homeModule`, sources via
  `getAll()`) flattens the flows, drops kinds hidden in settings
  (`home_hidden_cards`) and sorts by `HomeCardKind` declaration order.
- `HomeRoute` / `HomeScreen` are Compose in `commonMain`. On Android
  `HomeTabRoot` (`app/shell/entries/HomeEntries.kt`) keeps the ViewModel and
  does the Android-only parts: navigation (`homeActions`) and the hint actions
  in `feature/home/ui/HomeHints.kt` (widget pin, notification permission,
  `SERVICES_SETTINGS_PAGE`).
- Schedule and sport cards format times in the `AcademicTimeProvider` zone
  (`ScheduleHomeCardFormatter`, `SportHomeCardFormatter`); no UI parses ISO
  text.

| Card | Source | Shown when |
|---|---|---|
| `Сегодня` / `Завтра` | `feature/schedule/data/home/ScheduleHomeCardSource` (`HomeScheduleSelector`), one-minute ticker | always |
| `Изменения в расписании` | `ScheduleChangesHomeCardSource`, local [schedule changes](schedule-changes.md) only; `dismiss` marks all read and removes the notification; `refresh` asks nothing | an unread change of a lesson not yet over |
| `Новые оценки` | `feature/recordbook/data/home/MarksHomeCardSource`, local [mark tracking](marks-tracking.md) only; `dismiss(MARKS)` -> `markAllRead()`; `refresh` asks nothing | an unread subject |
| `Спорт` | `feature/sport/data/home/SportHomeCardSource` | score below 100 or a non-empty queue |
| `Заявки в друзья` | `feature/social/data/home/SocialHomeCardSource` | an incoming request, behind the opt-in |
| Hints | `feature/home/data/HintHomeCardSource`: no widget placed, notifications off, user services off | while the reason holds and not closed; closed hints are per installation in `home_dismissed_hints` |

- Lesson and pending sport rows go through `SportDetailsRedirect`, as in the
  schedule ([lesson details](schedule.md#lesson-details)); `SportBookingsHolder`
  loads the sport data when needed, so cancel works from the feed.
- Refresh: first show refreshes every source once (silent); pull and a return
  after 5 minutes (`STALE_AFTER_MILLIS`) refresh again; every return calls
  `revalidate`. Sources refresh in parallel; a failure keeps cached cards and
  shows one snackbar with retry. Only a pull sets `Content.refreshing`.
- `Loading` lasts only until every source answered from cache; an empty list
  is content. The list ends with `fabStackClearance` (152 dp) so the last card
  clears the FABs.
- Tests: goldens in `shared/feature-home/screenshots/` and each feature's
  `screenshots/`; `HomeScreenTest`; `HomeEntriesTest` (`app/src/test`) never
  touches MyITMO or Backend.

## QR pass

The home QR button opens the pass above the feed; see [QR pass](qr.md).

## Quick-settings tile and app shortcuts

- `QrTileService` (`feature/qr/ui`): `QrTileController` initialises the
  session in `onStartListening`; `STATE_ACTIVE` when signed in, else
  `STATE_INACTIVE`; not toggleable. A tap always opens `ACTION_OPEN_QR_PASS`
  with the widget's flags (`NEW_TASK | CLEAR_TOP | SINGLE_TOP`), via
  `unlockAndRun` when locked; without a session the route waits for sign-in.
  Android 14+ must use `startActivityAndCollapse(PendingIntent)`; the
  `Intent` overload throws there (`qrTileLaunchFor`).
- `qr_tile_added` (`DeviceHintPreferences`, kept on sign-out) is written by
  `onTileAdded`/`onTileRemoved` and the add-request answer, since Android has
  no API to ask. The add request (Android 13+, `Добавить в шторку`,
  [settings](../settings.md#qr-widget)) belongs to `feature/settings`
  (`QuickSettingsTileAccess`, `AndroidQuickSettingsTileAccess`,
  `requestAddQrTile`).
- Static shortcuts in `res/xml/shortcuts.xml` (ids in `app/AppShortcuts`):
  `qr_pass` (`ACTION_OPEN_QR_PASS`) and `today` (`ACTION_OPEN_TODAY`, the own
  schedule on today, also when a friend is selected). Routes report usage
  with `ShortcutManagerCompat.reportShortcutUsed` (the tile too, the in-app
  QR button not). Launchers start shortcuts with `NEW_TASK | CLEAR_TASK`, so
  `MainActivity` is recreated and tab state is lost; the tile keeps it.
- Tests: JVM in `app/src/test` and `QrTileFlowTest` on the emulator.

## MyITMO web

The second FAB opens `https://my.itmo.ru/` as `AppRoutes.MyItmoWeb` above the
feed. Security rules (`MyItmoWebPolicy` in `:shared:feature-account`):

- Only the exact HTTPS origins `my.itmo.ru` and `id.itmo.ru` stay embedded;
  no credentials in the URL, no non-standard ports.
- External HTTPS links open only after a main-frame user gesture; redirects
  and untrusted frames cannot launch apps. TLS errors cancel loading.
- File/content access, mixed content, pop-ups and third-party cookies are
  off. No JavaScript bridge, token injection or console logging.
- Own browser session; native refresh tokens are never copied in. Sign-out or
  account replacement clears web cookies, DOM storage and HTTP cache
  (`WebSessionDataCleaner`).

## iOS

Same shared feed ([iOS app](../ios.md)) as the today tab's root until its
SwiftUI screen lands: `TodayScreen` puts the root header (today's academic
date, the bell) over `HomeRoute`, hosted by `HomeScreen`
(`iosApp/Sources/Features/Home/`) through `homeViewController`
(`shared/ios`, `screens/HomeScreens.kt`); `homeModule`, `homeIosModule`.

- Cards and buttons open the shared keys through the Swift router; a key
  without an iOS screen opens nothing. `offeredBy` keeps renderers of kinds
  `PlatformCapabilities` offers (currently all).
- Hints (`IosHomeHintStatus`): the widget hint shows until any widget is
  placed and opens instructions (iOS lets no app place one); the notification
  hint asks once, then opens notification settings. Re-checked on every
  activation; same `home_dismissed_hints`.
- Debug: `-itmoForgetHomeHints` restores closed hints.
