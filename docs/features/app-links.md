# App Links and sharing

Product behaviour: [Как это работает: друзья](../how-it-works/friends.md), [спорт](../how-it-works/sport.md).

## Links

| Link | Opens |
|---|---|
| `https://<host>/u/{isu}` | the person profile |
| `https://<host>/sport/{lessonId}` | a catalog lesson |
| `https://<host>/sport/p/{prototypeLessonId}` | a predicted lesson, named by the catalog lesson it repeats |
| `https://<host>/pair#1.<code>.<key>.<secret>` | the pairing sheet with a client's link ([connected clients](connections.md#pairing-pairingviewmodel)) |

- `<host>` is `widgets.alllexey.dev` or `dev.widgets.alllexey.dev`.
- `core/navigation/AppLinks.parse` (`core/url/StrictUri`): `https` only, ISU
  `[1-9][0-9]{0,9}` within `Int`, lesson id `[1-9][0-9]{0,18}` within `Long`, one
  trailing `/`, query and fragment ignored. Anything else under `/u/` and
  `/sport/` is `AppLink.Malformed`; another scheme, host or path is no app link.
- `/pair` is read by `core/connections/PairLink.parse`: path exactly `/pair`, no
  query, user info or port, fragment `1.<code>.<key>.<secret>` (the alpha's
  `v1.` is read too) with an 8-character pairing code, a 32-byte key and a
  16-byte secret in base64url without padding. A damaged one is
  `AppLink.Malformed`. The fragment holds the client's key, so `PairLink` and
  the route never print it.
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
| `/sport/{id}`, `/sport/p/{id}` | sport, `Запись` | the lesson sheet or "lesson unavailable" |
| `/pair#...` | profile tab | `Pairing(link = link)`, read as a scan |
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
  prediction, a missing prototype) is unavailable. The view model keeps the
  linked lesson, so its sheet and its action work while filters hide it.
  iOS: `SportTabScreen` consumes the request the same way.

## Sharing

`ShareLinkFactory` builds every URL from `BuildConfig.WIDGETS_BASE_URL` (release:
production host, debug: dev host); `core/ui/ShareText` opens the Sharesheet.

- Profile: from the `AppRoutes.UserProfile` top bar (only a `Content` page, the
  own profile too) and from the profile tab once the ISU is known.
- Sport lesson: from the lesson sheet's `Поделиться` while the lesson has not ended (a real
  lesson or booking with a positive id, or a prediction). A predicted card shares
  `/sport/p/{prototypeLessonId}` (`SportCommonDetailsArgs.prototypeLessonId`:
  the id of a predicted lesson, the negated id of a predicted booking). Debug
  template lessons have negative ids and are never shared.

## iOS

The iOS app ([iOS app](../ios.md#shell-and-routes)) opens the same links as
Universal Links and parses them with the same `AppLinks` and `EntryRouteParser`.

- `ITMOWidgets.signed.entitlements` has `com.apple.developer.associated-domains`
  with `applinks:` for both hosts, in every signed build, as the Android filter.
  The unsigned entitlements have none: simulator, CI and sideloaded builds open
  the links in Safari ([degradations](../ios.md#degradations)).
- SwiftUI hands a Universal Link to `.onOpenURL` as the `https` URL itself
  (`ITMOWidgetsApp`), as it does `itmowidgets://route/<id>`: `AppRouter.open(url:)`
  -> `RouteURL.entryRoute(url:)` -> `IosRoutes.linkRoute`, then the router queue
  (sign-in and first run first). The fragment of `/pair` reaches the app.
  `RouterTests.testUniversalLinksParse` covers both hosts and every kind.
- Each host's `/.well-known/apple-app-site-association` names the app ID
  `<team ID>.dev.alllexey.itmowidgets` with the components `/u/*`, `/sport/*`
  and `/pair` (see the site below). iOS reads it through Apple's CDN when the
  app is installed or updated, so a change of the file reaches phones only after
  the CDN refreshes (up to a day) and the app is reinstalled or updated.
- Sharing uses `PlatformActions.shareText`.

## Verification and the site

- `itmo-widgets-web` serves `web/public/.well-known/assetlinks.json` on both hosts: one statement for
  `dev.alllexey.itmowidgets` with the release and the debug (`~/.android/debug.keystore`)
  fingerprints, JSON without redirects.
- It also serves `web/public/.well-known/apple-app-site-association` (no extension) on both hosts, JSON
  without redirects: `applinks.details` for `<team ID>.dev.alllexey.itmowidgets`, components `/u/*`,
  `/sport/*`, `/pair`. The team ID is the one in the owner's `Signing.local.xcconfig`; a build signed
  by another team (a sideload) is not in the file. Check what Apple's CDN holds with
  `curl https://app-site-association.cdn-apple.com/a/v1/widgets.alllexey.dev`.
- Android 12+ verifies each host separately (a debug-signed build only on dev); Android 8-11 verify
  all hosts together, so a debug-signed build is verified on none. A local debug build uses the
  release key when `keystore.properties` is present, so only CI debug builds are debug-signed. Check
  with `pm get-app-links dev.alllexey.itmowidgets`.
- Without the app, the site answers `/u/*` and `/sport/*` with its link pages (200, `noindex`,
  `no-referrer`): no API request, the ISU or lesson id is never printed, App Store, Google Play and APK
  buttons; on Android the open button is an `intent://` URL falling back to the releases page, on
  other systems it is hidden (iOS never opens a Universal Link from a page of the same site).
