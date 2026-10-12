# App Links and sharing

Product behaviour: [Как это работает: друзья](../how-it-works/friends.md), [спорт](../how-it-works/sport.md).

## Links

| Link | Opens |
|---|---|
| `https://<host>/u/{isu}` | the person profile |
| `https://<host>/sport/{lessonId}` | a catalog lesson |
| `https://<host>/sport/p/{prototypeLessonId}` | a predicted lesson, named by the catalog lesson it repeats |
| `https://<host>/pair#v1.<code>.<key>.<secret>` | the web sign-in sheet with a device pairing link ([web sign-in](web-login.md#device-pairing)) |

- `<host>` is `widgets.alllexey.dev` or `dev.widgets.alllexey.dev`.
- `core/navigation/AppLinks.parse` (`core/url/StrictUri`): `https` only, ISU
  `[1-9][0-9]{0,9}` within `Int`, lesson id `[1-9][0-9]{0,18}` within `Long`, one
  trailing `/`, query and fragment ignored. Anything else under `/u/` and
  `/sport/` is `AppLink.Malformed`; another scheme, host or path is no app link.
- `/pair` is read by `core/weblogin/PairLink.parse`: path exactly `/pair`, no
  query, user info or port, fragment `v1.<code>.<key>.<secret>` with an
  8-character web-login code, a 32-byte key and a 16-byte secret in base64url
  without padding. A damaged one is `AppLink.Malformed`. The fragment holds the
  device's key, so `PairLink` and the route never print it.
- The manifest gives `MainActivity` one `autoVerify` `VIEW` filter with both
  hosts, the prefixes `/u/` and `/sport/` and the path `/pair` in every build,
  so a damaged link still reaches the app and explains itself. `launchMode` is
  unchanged.

## Routes

`EntryRouteParser` (`:shared:core`) turns `ACTION_VIEW` into an `EntryRoute`.
`ShellHost` keeps it in a `RouteQueue` (saved with the activity state) until
sign-in and first run are done, runs it once and never repeats it from Recents
(`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`).

| Link | Root | Above it |
|---|---|---|
| `/u/{isu}` | profile tab | `UserProfile` |
| `/sport/{id}`, `/sport/p/{id}` | sport, page `Запись` | the lesson card or "lesson unavailable" |
| `/pair#...` | profile tab | `WebLogin(code = link)`, read as a scan |
| malformed | home | `LinkUnavailable` |

- The profile is the ordinary `UserProfile`; the app never enforces privacy.
- A lesson route carries `TabRequest.SportLesson` (lesson id, `predicted`), kept
  with the back stack across process death until `SportTabRoot`
  (`app/shell/entries/SportEntries.kt`) consumes it once and passes it to
  `SportSignViewModel.openSharedLesson`. That waits for the first merged catalog
  answer and looks the lesson up among all lessons, ignoring filters (`findLinked`
  in `feature/sport/domain/model/SportLessonPrediction.kt`).
- `/sport/p/{id}`: while the repeat two weeks later is missing, the catalog holds
  the prediction under the prototype's id; once the real repeat exists (same
  section, teacher, levels, type and slot at prototype +14 days, the rule of
  `SportScheduleRepositoryImpl`), the real lesson opens.
- An ended lesson or one missing from the catalog (today..+21 days; for a
  prediction, a missing prototype) is unavailable. `SportSignRoute` falls back
  to `SportSignViewModel.linkedLesson` when filters hide the lesson.

## Sharing

`ShareLinkFactory` builds every URL from `BuildConfig.WIDGETS_BASE_URL` (release:
production host, debug: dev host); `core/ui/ShareText` opens the Sharesheet.

- Profile: from the `AppRoutes.UserProfile` top bar (only a `Content` page, the
  own profile too) and from the profile tab once the ISU is known.
- Sport lesson: from the card's menu while the lesson has not ended (a real
  lesson or booking with a positive id, or a prediction). A predicted card shares
  `/sport/p/{prototypeLessonId}` (`SportCommonDetailsArgs.prototypeLessonId`:
  the id of a predicted lesson, the negated id of a predicted booking). Debug
  template lessons have negative ids and are never shared.

## iOS

The iOS app ([iOS app](../ios.md#shell-and-routes)) parses links with the same
`AppLinks` and `EntryRouteParser` (`RouteURL` -> `IosRoutes.linkRoute`). Only the
app's own scheme `itmowidgets://route/<id>` reaches it today; `https` links open
the site, because Universal Links need a signed build with associated domains
([degradations](../ios.md#degradations)). Sharing uses
`PlatformActions.shareText`.

## Verification and the site

- `itmo-widgets-web` serves `site/.well-known/assetlinks.json` on both hosts:
  one statement for `dev.alllexey.itmowidgets` with the release and the debug
  (`~/.android/debug.keystore`) fingerprints, JSON without redirects.
- Android 12+ verifies each host separately: release-signed builds on both, a
  debug-signed one only on dev. Android 8-11 verify all hosts together, so a
  debug-signed build is verified on none. Check with
  `pm get-app-links dev.alllexey.itmowidgets`.
- Without the app, nginx answers `/u/*` with `site/link/profile.html` and
  `/sport/*` with `site/link/sport.html` (200, `noindex`, `no-referrer`). The pages
  make no API request and never print the ISU or lesson id. On Android the open
  button is an `intent://` URL with `browser_fallback_url` to the releases page.
- Tests: `AppLinksTest`, `EntryRouteParserTest`, `ShellHostTest`,
  `SportSignViewModelTest`, instrumented `MainActivityDeepLinkTest`.
