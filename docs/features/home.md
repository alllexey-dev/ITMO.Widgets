# Home and quick actions

The home tab is a feed of cards built from what the application already knows.
Each card reads like a home-screen widget: one object in focus, secondary
context, a tap that opens the details. The QR pass and `My ITMO` stay as the
two FABs at the bottom end; the list reserves space under them.

## Feed

`core/home` holds the contract: `HomeCard` (`Schedule`, `ScheduleChanges`,
`Marks`, `Sport`, `FriendRequests`, `Hint`) and `HomeCardSource` with `observe`,
`refresh`, `revalidate` and `dismiss(kind)`. `dismiss` is for a card with its
own close button: `HomeViewModel.dismissCard(kind)` passes it to every source,
and only the owner of that kind resets what the card shows; the default does
nothing. Every feature that owns data contributes a source from its `data`
package: a source still built by Hilt through the `@IntoSet` multibinding in its
Hilt module (`HomeBridge` hands the set to Koin as one composite), a source in a
shared module as a Koin single under the feature's own qualifier (home's hints,
social's friend requests, the schedule's two cards, the recordbook's new marks);
`HomeViewModel` (`:shared:feature-home`, built by Koin) receives the sources,
flattens the flows, drops the kinds hidden in settings and sorts by
`HomeCardKind`, whose declaration order is the feed order. Nothing in
`feature/home` imports another feature.

Each card is drawn by the feature that produces it. `core/home` also holds
`HomeCardRenderer` (the `kinds` it claims and a `@Composable` `Content(card,
actions, modifier)`), `HomeCardActions` (the host's navigation and
`onDismiss(kind)`) and `HomeCardTestTags`. A feature keeps its card in its
`ui/home` package and registers one renderer in its Koin module as a qualified
`single<HomeCardRenderer>`: `ScheduleHomeCardRenderer` (`SCHEDULE`,
`SCHEDULE_CHANGES`) in `:shared:feature-schedule`, `MarksHomeCardRenderer` in
`:shared:feature-recordbook`, `SportHomeCardRenderer` in
`:shared:feature-sport`, `FriendRequestsHomeCardRenderer` in
`:shared:feature-social` and `HintHomeCardRenderer` for the three hints in
home. Every kind has exactly one renderer (`HomeRenderersGraphTest` over the
release Koin modules); a card no renderer claims is left out of the feed. The
cards share the kit's frame (`FeedCard`, `FeedCardHeader`, `FeedRowBadge`,
`FeedCloseButton`, `ClosableFeedCard`), and each card's strings and goldens
live in its feature's module.

The screen is Compose in `:shared:feature-home` `commonMain`: `HomeRoute`
obtains the ViewModel and every Koin `HomeCardRenderer`, and `HomeScreen`
draws `HomeUiState` with them. The schedule and sport renderers format their
times and dates in the `AcademicTimeProvider` zone (`ScheduleHomeCardFormatter`,
`SportHomeCardFormatter`: `HH:mm` with `DateTexts`, the date as
`понедельник, 7 сентября`, sport queues as `пн, 7 сент. · 16:00–17:30`), so no
UI parses ISO text; `HomeRulesTest` keeps `java.time` and `kotlinx.datetime`
out of `feature.home.ui`. `HomeFragment` keeps its class name and hosts the
route through `itmoComposeView`; it owns what only Android does: navigation
through `AppNavigator`, the widget pin (`WidgetPinRequester`), the
notification permission and the services settings page (`SERVICES_PAGE`).
Home's own strings (the buttons, the empty state, the hints) are
`strings_home.xml` in the module's `composeResources`.

| Card | Source | Shown when |
|---|---|---|
| `Сегодня` / `Завтра` | `feature/schedule/data/home/ScheduleHomeCardSource` on `HomeScheduleSelector`: today's remaining lessons and pending sport rows, the lesson in progress marked `сейчас` with a progress line for the elapsed share, the next one `далее`, finished lessons counted in the footer; tomorrow once today is over; a one-minute ticker moves the focus | always (an empty day says so) |
| `Изменения в расписании` | `feature/schedule/data/home/ScheduleChangesHomeCardSource`, from the local [schedule changes](schedule-changes.md) only: a badge with the number of unread changes of lessons still ahead and the headline of the newest one (by detection, then the sooner lesson), for example `Физика — перенесена на ср, 9 сентября, 10:00`. A tap opens the history; the 48 dp close button `Прочитано` (`dismiss`) marks every change read and removes the notification. A one-minute ticker drops changes whose lessons are over; `refresh` asks nothing, the check runs in the background. TalkBack reads `Изменения в расписании, N. <headline>` | at least one unread change of a lesson not yet over |
| `Новые оценки` | `feature/recordbook/data/home/MarksHomeCardSource`, from the local [mark tracking](marks-tracking.md) only: `ic_menu_book`, a badge with the number of unread subjects and their names without marks, up to three and then `… и ещё N` (`core/ui/markSubjectList`), wrapping without truncation. A tap opens the recordbook root; the 48 dp close button `Прочитано` (`dismiss(MARKS)` → `markAllRead()`) marks every subject read and removes the notification. `refresh` asks nothing, the check runs in the background. TalkBack reads `Новые оценки, N. <names>` | at least one unread subject |
| `Спорт` | `feature/sport/data/home/SportHomeCardSource`: score progress out of 100 and own queues (three, then `ещё N`) | a score below 100 or a non-empty queue |
| `Заявки в друзья` | `feature/social/data/home/SocialHomeCardSource`: the first three incoming requests as the kit's `UserRow` with `Avatar` and the primary group, `Все заявки` opens the friends screen | at least one incoming request behind the opt-in |
| Hints | `feature/home/data/HintHomeCardSource`: no widget on the launcher (`Добавить` pins the single-lesson widget through `core/ui/widget/WidgetPinRequester`), notifications off (`Включить` asks for the permission while the dialog can still appear, otherwise opens the app's notification page), user services off (`Включить` opens the services settings page) | while the reason holds and the hint was not closed; closed hints are kept per installation in `home_dismissed_hints` |

Lesson and pending rows open sheets through `AppNavigator.openLessonDetails` /
`openPendingSportDetails`; their arguments live in `core/navigation`. A pending
row resolves to the sport tab's own `SportCommonDetailsBottomSheet` when the
sport data knows the queue (`MainActivity` loads the sport tab's data through the shared
`SportMyViewModel` when needed and looks the lesson up in `SportBookingRepository`), so the queue
position, history and `Отменить` work from the feed and the schedule alike; the
cancellation goes through the shared `SportMyViewModel` after the usual
confirmation. The schedule's own `PendingSportDetailsBottomSheet` is the
fallback. A booked sport lesson in the schedule card takes the same road by its
date and start time, so an existing booking can be cancelled from the feed. The sport card opens the sport tab, a friend row the public profile.

Refresh: the first show refreshes every source once; pull-to-refresh and a
return to the screen after five minutes do it again; every return also calls
`revalidate` on all sources (a permission the user just granted). Sources refresh in parallel; a failure leaves the cached card in place
and the feed shows one `Часть данных не загрузилась` snackbar with `Повторить`.
The first show and a stale resume refresh silently (`refresh(silent = true)`);
only a pull sets `Content.refreshing`.
The state is `Loading` (three placeholder cards, `Skeleton`) only until every
source has answered from its cache; an empty list of cards is content and
shows the `Пока пусто` state inside the same pull-to-refresh (`AppRefreshBox`).
The list ends with `fabStackClearance` (152 dp) of padding, so the last card
scrolls clear of the two FABs; the snackbar sits above them. Test tags are in
`HomeTestTags`.

`Настройки → Главный экран` hides a card kind (`home_hidden_cards`); hints are
not settings, only dismissible. The look is covered by JVM goldens: the screen
(loading, empty, content, refreshing) and the hints in
`shared/feature-home/screenshots/`, every other card with long names in its
feature's `screenshots/` (`Home<Card>Preview`, from synthetic samples in
`ui/home/preview`). Behaviour by `HomeScreenTest` (touch targets, the FABs
never covering the last card, drawing by kind, every action), each card's own
test and the formatter tests.
Instrumented flows (`HomeQrVisualTest`, `HomeWebVisualTest`,
`MainNavigationTest`) replace the whole feed with one in-memory source
(`HomeFixture`) and never touch MyITMO or Backend.

## QR pass

The home QR button opens the pass above the feed. Screen, data, cache and expiry
are described in [QR pass](qr.md).

## Quick-settings tile and app shortcuts

`QrTileService` (`feature/qr/ui`) is the «QR-пропуск» quick-settings tile. In
`onStartListening` `QrTileController` initialises the session and shows the
tile `STATE_ACTIVE` with a signed-in session, `STATE_INACTIVE` otherwise; the
tile is not toggleable and declares no active-tile metadata. While a cold
process answers, SystemUI may show the tile as unavailable for a moment. A tap
always opens the app with `ACTION_OPEN_QR_PASS` (the widget's flags
`NEW_TASK | CLEAR_TOP | SINGLE_TOP`); without a session the route waits for
sign-in. On a locked device the tap goes through `unlockAndRun`. Android 14+
starts the activity with `startActivityAndCollapse(PendingIntent)`, older
versions with the `Intent` overload, which throws on Android 14+ for apps
targeting it (`qrTileLaunchFor`). The icon `ic_tile_qr.xml` is white without a
theme tint; SystemUI colours it.

The device flag `qr_tile_added` (`DeviceHintPreferences`, kept on sign-out)
remembers whether the tile is in the quick settings: `onTileAdded` and
`onTileRemoved` write it in the application scope, and so does the answer to
the add request. Android has no public way to ask whether a tile is added. On
Android 13+ the `Виджет QR-кода` settings page offers `Добавить в шторку` while
the flag is off (see [settings](../settings.md#qr-widget)). That request belongs
to `feature/settings`: `QuickSettingsTileAccess` and `QrTileAddResult` in
`domain`, `AndroidQuickSettingsTileAccess` in `data` and `requestAddQrTile` in
`ui`. The tile itself is `feature/qr`: `QrTilePreferences` in `domain`,
`QrTileController` in `presentation`, `QrTileService` and `QrTileClick` in `ui`.

Two static shortcuts (`res/xml/shortcuts.xml`, ids in `app/AppShortcuts`):

| Id | Label (short / long) | Action | Opens |
|---|---|---|---|
| `qr_pass` | `QR-пропуск` / `Открыть QR-пропуск` | `ACTION_OPEN_QR_PASS` | home with the QR pass above it |
| `today` | `Сегодня` / `Расписание на сегодня` | `ACTION_OPEN_TODAY` | the own schedule on today's day |

Their icons are adaptive, without a tint: a white background like the launcher
icon and the Material Symbols `qr_code` and `schedule` in `#4984E2`. The
launcher shows the long or the short label depending on the width. `Сегодня`
with a friend selected returns to the own schedule. Running a route reports its
shortcut with `ShortcutManagerCompat.reportShortcutUsed`, for the tile too;
the QR button inside the app does not. The launcher starts a shortcut with
`NEW_TASK | CLEAR_TASK`, so a shortcut used while the app is open recreates
`MainActivity` in the same task: the earlier tab state is not kept. The tile
uses the widget's flags and keeps it.

Tests: `QrTileClickTest`, `QrTileControllerTest`, `QrTilePreferencesImplTest`,
`QuickSettingsTilesTest`, `MainActivityIntentRoutingTest`, `MainRouteQueueTest`
(JVM); `AppShortcutsTest`, `MainActivityDeepLinkTest`, `QrTileFlowTest`
(`cmd statusbar add-tile` and `click-tile` on the emulator) and the today
request cases of `ScheduleRouteTest` (`:shared:feature-schedule`, JVM).

## MyITMO web

The second FAB opens the official `https://my.itmo.ru/` website in the
`MY_ITMO_WEB` overlay. The toolbar provides close, reload and an external-browser
fallback. Android Back follows web history first; rotation restores web history
and an error state retains retry. A thin loading indicator reserves its space.

Only the exact HTTPS origins `my.itmo.ru` and `id.itmo.ru` remain embedded, with
no credentials in the URL and no non-standard ports. External HTTPS links open
only after a main-frame user gesture; redirects and untrusted frames cannot
launch external apps. TLS errors cancel loading. File/content access, mixed
content, pop-up windows and third-party cookies are disabled. There is no native
JavaScript bridge, token injection or console logging.

The website uses its own browser session. If it needs authentication, credentials
are entered only on the official ITMO pages; native refresh tokens are not copied
into the website. Signing out or replacing the native account clears app-wide
web cookies, DOM storage and HTTP cache through `WebSessionDataCleaner`.

Debug visual tests intercept every WebView request with synthetic HTML and never
send fixture data to MyITMO or Backend. URL policy is separately unit-tested.

## iOS

The iOS app ([iOS app](../ios.md)) shows the same feed on the same shared
code: `HomeRoute` of `:shared:feature-home` is the root of the home tab's
stack, hosted by `HomeScreen` (`iosApp/Sources/Features/Home/`) through
`homeViewController` (`shared/ios`, `screens/HomeScreens.kt`). There is no top
bar, as on Android; the feed keeps clear of the status bar, the tab bar and
the demo banner.

- Cards and buttons open the shared keys through the Swift router, as
  Android's `homeActions` does: the QR button the pass above home, the My ITMO
  button the website (in the demo «Недоступно в демо», as Android's toast),
  the sport card the sport tab, a friend row the profile. A key iOS has no
  screen for yet opens nothing.
- Sources and renderers come from the Koin modules iOS loads: the hints with
  this feature (`homeModule`, `homeIosModule`), the schedule, sport and friend
  cards with their features' iOS cards. `offeredBy` keeps only the renderers
  of kinds `PlatformCapabilities` offers; every kind is offered since mark
  tracking shipped on iOS (IO-09d3), and the new-marks card opens the
  recordbook tab.
- Hints. `IosHomeHintStatus`: the widget hint shows until any widget of the
  app is placed (WidgetKit's current configurations through `IosPlatform`);
  iOS lets no app place one, so «Добавить» opens a sheet with «Как добавить
  виджет» and the steps of the first-run flow. The notification hint shows
  until iOS allows alerts (provisional and ephemeral too); «Включить» asks
  iOS once, then opens the app's notification settings. The services hint
  opens the services page of the settings. The hints are re-checked whenever
  the app becomes active again; closed hints are kept in the same
  `home_dismissed_hints`.
- Tests: `HomeIosModuleTest` (the graph resolves with no request),
  `IosHomeHintStatusTest`, `HomeCardCapabilitiesTest`
  (`scripts/ios/test.sh kn :shared:feature-home`); `HomeUITests` on the demo
  session (the feed, a closed hint after a relaunch, the instruction sheet,
  the QR and My ITMO buttons, AX1). A Debug launch with
  `-itmoForgetHomeHints` brings the closed hints back
  (`HomeLayoutPreferences.forgetDismissedHomeHints`).
