# Home and quick actions

The home tab is a feed of cards built from what the application already knows.
Each card reads like a home-screen widget: one object in focus, secondary
context, a tap that opens the details. The QR pass and `Мой ИТМО` stay as the
two FABs at the bottom end; the list reserves space under them.

## Feed

`core/home` holds the contract: `HomeCard` (`Schedule`, `Sport`,
`FriendRequests`, `Hint`) and `HomeCardSource` with `observe`, `refresh` and
`revalidate`. Every feature that owns data contributes a source from its `data`
package through the `@IntoSet` multibinding in its Hilt module; `HomeViewModel`
receives the set, flattens the flows, drops the kinds hidden in settings and
sorts by `HomeCardKind`, whose declaration order is the feed order. Nothing in
`feature/home` imports another feature.

| Card | Source | Shown when |
|---|---|---|
| `Сегодня` / `Завтра` | `feature/schedule/data/home/ScheduleHomeCardSource` on `HomeScheduleSelector`: today's remaining lessons and pending sport rows, the lesson in progress marked `сейчас`, the next one `далее`, finished lessons counted in the footer; tomorrow once today is over; a one-minute ticker moves the focus | always (an empty day says so) |
| `Спорт` | `feature/sport/data/home/SportHomeCardSource`: score progress out of 100 and own queues (three, then `ещё N`) | a score below 100 or a non-empty queue |
| `Заявки в друзья` | `feature/social/data/home/SocialHomeCardSource`: incoming requests as `item_user_row.xml` rows, `Все заявки` opens the friends screen | at least one incoming request behind the opt-in |
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
The state is `Loading` only until every source has answered from its cache; an
empty list of cards is content and shows the `Пока пусто` state. The list and
the empty state switch atomically after `submitList` commits.

`Настройки → Главный экран` hides a card kind (`home_hidden_cards`); hints are
not settings, only dismissible. Debug visual tests replace the whole feed with
one in-memory source (`HomeFixture`) and never touch MyITMO or Backend.

## QR pass

`feature/qr` uses the existing MyItmoApi repository and QR renderer, not a second
API client. The screen shows a valid cached code immediately after rendering,
can force a refresh, and distinguishes loading, content, empty and error. A
refresh failure keeps only a still-valid code and offers retry feedback.

`QrCodeSnapshot` carries the cache deadline from the local source. The screen
hides the pass at that deadline even while a refresh is pending, and revalidates
on return. Its jobs stop while the screen is not visible. It uses the injected
wall clock, never the debug academic clock. The widget’s explicit expired-cache
fallback remains unchanged.

The QR area keeps a square geometry in every state, the refresh control remains
in place, and the existing widget palette determines QR contrast. Preview and
test screenshots use synthetic non-credential payloads only.

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
