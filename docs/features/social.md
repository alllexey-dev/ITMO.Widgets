# Social

Friends, requests, people search and public profiles. Backend is the authority
for relationships and access; the app only renders viewer-scoped capabilities.
Everything here is gated on the custom-services opt-in.

## Repositories (`core/social`)

- `SocialRepository` loads friends, incoming and outgoing requests and the own
  Backend profile in one refresh, caches them as `SocialState` flows and exposes
  `profile(isu)`, `userFriends(isu)`, `lookup(isus)` and the actions `sendRequest`, `acceptRequest`,
  `rejectRequest`, `cancelRequest`, `removeFriend`. Every action returns the
  fresh `UserProfile` and folds it into the cached lists, so screens never
  refresh after acting. It is a `SessionDataCleaner`.
- `PeopleSearchRepository` searches people by name through MyITMO
  (`searchPersonalities`, 20 per page) and annotates registered users through
  Backend lookup in chunks of 50. Phone and e-mail from the directory never leave
  the data layer. See decision [0006](../decisions/0006-people-search.md).
- `core/friend/FriendRepository` is a facade over `SocialRepository` for the
  schedule picker, exposing friends as plain `UserSummary` values.

`RelationshipState` is viewer-relative: `NONE`, `OUTGOING`, `INCOMING`,
`FRIENDS`, `BLOCKED` (reserved, never produced yet). `UserSummary.sharing`
carries the viewer's capabilities, not the owner's audiences.

Identity publication: `BackendIdentitySync` uploads the ITMO.ID id token on cold
start, after sign-in and when services are enabled. A failed upload is retried
by `IdentitySyncWork` with exponential backoff, up to six attempts, and every
failure is recorded in the diagnostics journal. Until the upload lands,
`UserData.name` from Backend is empty and every screen renders
`Пользователь ИСУ N` through `Context.userDisplayName`.

## Profile tab (`feature/me`)

Header with avatar, name, study group from Backend and ISU. A `Друзья` card
with the friends count and an incoming-requests badge, `Найти людей` and
`Приватность` rows (the latter opens the settings privacy page); when services
are off, one row explains it and opens settings. An `Приложение` card holds
settings and debug tools; notification state and the version live in settings.
Above sign-out, two compact tonal buttons with logos open the GitHub repository
and the `@itmowidgets` Telegram channel. Telegram tries the `tg://` deep link
first and falls back to the web page. The tab refreshes social data on start
and when services are re-enabled.

## Friends screen (`feature/social`, overlay `FRIENDS`)

Two tabs on one list: `Друзья` (rows with `Удалить`, confirmed by a dialog) and
`Заявки` (incoming with `Принять`/`Отклонить`, outgoing with `Отменить`),
grouped by section headers. The requests tab carries a badge with the incoming
count. A search icon opens people search. Disabled services show a locked state
that leads to settings.

## People search (overlay `USER_SEARCH`)

Query after a 300 ms debounce; results split into `В ITMO.Widgets` (with the
relationship action: add, cancel, accept) and `Остальные` (with `Пригласить`,
which opens a share sheet with the release link). `Показать ещё` loads the next
page. Actions update the row from the returned profile without a new search.

## Public profile (overlay `USER_PROFILE`, argument `UserScreenArgs.ISU`)

Identity header; one primary action driven by the relationship (`Добавить в
друзья` filled, `Отменить заявку` tonal, `Принять заявку` filled with a
secondary `Отклонить`, `Удалить из друзей` tonal with confirmation); the own
profile shows `Это вы` and no actions. `Расписание` and `Спорт` rows are open
when the capability allows, otherwise locked with a hint that friends usually
unlock them. Open rows lead to `USER_SCHEDULE` and `USER_SPORT` with the ISU and
name. `NotFound` renders as "not an ITMO.Widgets user".

Profiles open from the friends list, requests, search results, the friend
picker (locked row tap or long-press) and the friends list in the sport details
sheet, always through `AppScreen.USER_PROFILE`.

## Another user’s friends (overlay `USER_FRIENDS`)

The public profile has a `Друзья` row controlled by Backend’s `canViewFriends`.
The screen shows only accepted friends, with every row and capability relative
to the signed-in viewer. Rows open public profiles; the list has no mutation
buttons or request tabs. First loading, empty, denied, disabled services and
retryable errors are distinct. Refresh retains content on network failure,
but discards it if authorization is revoked; returning to the screen rechecks
access. No target list is stored in the viewer’s own friends cache, but the
repository keeps the last answer per ISU (`cachedUserFriends`) and the last
profile per ISU from any list, screen or action (`cachedProfile`), both cleared
on sign-out, so a reopened profile or list renders at once with `refreshing`
and only an unseen person shows the skeleton.

`Кто видит список друзей` is an independent privacy choice: `Все` (the default
for both existing and new accounts), `Друзья`, `Никто`. Backend enforces it before
reading the list and never exposes pending requests or the owner’s raw audience.

## Shared list row

`item_user_row.xml` with `UserListAdapter` renders every social list: avatar,
name, `ISU • group` subtitle, optional status line, up to two action buttons or a
chevron, section headers and a load-more row. Presentation builds `UserRowUi`
values with `UiText` labels; the adapter maps `UserAction` to strings.

## Not implemented yet

Blocking (Backend has no block model), live privacy audiences on the profile
tab, and FCM-driven refresh of the badge when the app is closed.
