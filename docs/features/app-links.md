# App Links and sharing

Two things can be shared: a person's profile by ISU (students and teachers) and
a sport lesson from the catalog, a predicted one included. A shared link opens
the profile or the lesson card in the app; without the app it opens a plain
page of the site. Lessons from the schedule and subjects are not shared.

## Links

| Link | Opens |
|---|---|
| `https://<host>/u/{isu}` | the person profile |
| `https://<host>/sport/{lessonId}` | a catalog lesson |
| `https://<host>/sport/p/{prototypeLessonId}` | a predicted lesson, named by the catalog lesson it repeats |

`<host>` is `widgets.alllexey.dev` or `dev.widgets.alllexey.dev`.
`core/navigation/AppLinks.parse` reads them as common Kotlin (`core/url/StrictUri`):
`https` only, an ISU `[1-9][0-9]{0,9}` within `Int`, a lesson id
`[1-9][0-9]{0,18}` within `Long`, one trailing `/` allowed, query and fragment
ignored. Anything else under `/u/` and `/sport/` is `AppLink.Malformed`;
another scheme, host or path is not an app link at all.

The manifest gives `MainActivity` one `autoVerify` intent filter for `VIEW`
with both hosts and the path prefixes `/u/` and `/sport/` in every build, so a
damaged link still reaches the app and explains itself. `launchMode` is
unchanged: a source that starts the link without `NEW_TASK` (Telegram, for
example) gets `MainActivity` in its own task, and Back from home returns there.

## Routes

`EntryRouteParser` (`:shared:core`) turns `ACTION_VIEW` into an `EntryRoute`
like any widget, notification, tile or shortcut intent. `ShellHost` keeps it in
a `RouteQueue`, saved with the activity's state, until sign-in and the
first-run flow are done, runs it once and never repeats it from Recents
(`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`).

| Link | Root | Above it | Back |
|---|---|---|---|
| `/u/{isu}` | profile tab | `UserProfile` | profile tab → home → out |
| `/sport/{id}`, `/sport/p/{id}` | sport, page `Запись` | the lesson card or `Занятие недоступно` | card → sport → home → out |
| malformed | home | `Ссылка не открывается` (`LinkUnavailable`) | home → out |

The profile is the ordinary `UserProfile`: any positive ISU opens, an unknown
one is `Профиль не найден`, and what the ITMO.Widgets block shows follows the
capabilities Backend returns. The app never enforces privacy itself.

A lesson route carries the tab request `TabRequest.SportLesson` (lesson id,
`predicted`). The shell keeps it with the back stack, across recreation and
process death, until the sport tab's root (`SportTabRoot` in
`app/shell/entries/SportEntries.kt`) consumes it once: `SportRoute` switches to
`Запись` and passes it to `SportSignViewModel.openSharedLesson`. That
waits for the first answer of the merged catalog and looks the lesson up among
all lessons, ignoring filters (`findLinked` in
`feature/sport/domain/model/SportLessonPrediction.kt`):

- `/sport/{id}` names a real catalog lesson.
- `/sport/p/{id}` names the prototype. While the repeat two weeks later is
  missing, the catalog holds the prediction under the prototype's id and the
  predicted card opens with `Автозапись`. Once the real repeat is in the catalog
  (same section, teacher, levels, type and time slot at prototype +14 days, the
  rule `SportScheduleRepositoryImpl` uses), the real lesson's card opens.
- A lesson that ended, or is missing from the catalog (today..+21 days; for a
  prediction, the prototype is missing), shows `Занятие недоступно`. A catalog
  error is the usual snackbar.

When found, the lesson's day is selected and its card opens as on a tap; the
card's actions come back to the sport root on the sign page's result key and
go to `SportSignRoute` (`SportSheetAction.outcome`), which falls back to
`SportSignViewModel.linkedLesson` when filters hide the lesson from the list.
Both dialogs have a title, a text and `Понятно`.

## Sharing

`ShareLinkFactory` builds every shared URL from `BuildConfig.WIDGETS_BASE_URL`:
release builds share `https://widgets.alllexey.dev`, debug builds
`https://dev.widgets.alllexey.dev`. `core/ui/ShareText` opens the Sharesheet
(`ACTION_SEND`, `text/plain`, the text in `EXTRA_TEXT`, the title in
`EXTRA_TITLE`). The icon is Material Symbols `share` in a 48 dp target.

| Where | When | Text |
|---|---|---|
| `AppRoutes.UserProfile` top bar | the page is `Content`, the own profile too | `<name> в ITMO.Widgets: <url>` |
| profile tab, beside the own name | the ISU is known | the same |
| sport lesson card, toolbar menu | the lesson has not ended: a real lesson or booking with a positive id, or a prediction | `<section>, <weekday, date, time> — ITMO.Widgets: <url>` |

A predicted card shares `/sport/p/{prototypeLessonId}`
(`SportCommonDetailsArgs.prototypeLessonId`: the id of a predicted lesson, the
negated id of a predicted booking). Debug template lessons have negative ids
and are never shared. The shared date is absolute, because the recipient reads
it on another day.

## iOS

The iOS app ([iOS app](../ios.md#shell-and-routes)) parses links with the same
`AppLinks` and `EntryRouteParser` (`RouteURL` hands an `https` URL to
`IosRoutes.linkRoute`), and its router runs the same routes as the table
above once the session is ready. Today only the app's own URL scheme reaches
it: `itmowidgets://route/<id>` from widgets, the Control, App Shortcuts and
quick actions. `https` links on these hosts open the site, because Universal
Links need the associated domains of a signed build, which needs the Apple
developer account
([degradations](../ios.md#degradations)).

Sharing works as on Android through the system share sheet
(`PlatformActions.shareText`): the profile link from the Me tab and a
profile, the sport lesson link from its card; the invitation names the App
Store page once the app has one, else the site.

## Verification and the site

`itmo-widgets-web` serves `site/.well-known/assetlinks.json` on both hosts: one
statement for `dev.alllexey.itmowidgets` with the release fingerprint
`93:9E:9B:D5:…:F0:71` and the debug fingerprint `68:5B:19:14:…:88:B7` (the
public fingerprint of `~/.android/debug.keystore`). It is JSON without
redirects. The debug fingerprint on the production host is harmless: debug
builds share dev links.

Android 12+ verifies each host separately: a release-signed build is verified
on both hosts, a debug-signed one only on the dev host. Android 8–11 verify all
hosts of the filter together, so a debug-signed build is verified on none and
links open in the browser or a chooser there. `pm get-app-links
dev.alllexey.itmowidgets` shows the state; `pm verify-app-links --re-verify`
repeats it.

Without the app, nginx answers `/u/*` with `site/link/profile.html` and
`/sport/*` (predicted links included) with `site/link/sport.html`, status 200,
`noindex` and `no-referrer`. The pages make no API request and never print the
ISU or the lesson id. On Android `Открыть в приложении` is an `intent://` URL
with the app's package and the releases page as `browser_fallback_url`, so it
also works in in-app browsers; elsewhere the button is hidden. `Скачать` leads
to the latest GitHub release.

## Tests

- JVM: `AppLinksTest`, `ShareLinkFactoryTest`, `EntryRouteParserTest`,
  `RouteQueueTest` (`:shared:core`), `ShellHostTest` (`app/src/test`: every
  entry intent runs once, the malformed-link alert, Recents),
  `SportSignViewModelTest` (shared, hidden, ended and missing lessons;
  predictions, real repeats and a missing prototype; a request before the
  catalog answers), `SportSessionPresentationTest` (prototype id, shared date),
  `UserProfileScreenTest.theStatesOfferRetryOnlyForAFailureAndShareOnlyForAPage`
  (`:shared:feature-social`).
- `SportDetailsSheetTest.upcomingLessonsBookingsAndPredictionsCanBeShared`
  (`:shared:feature-sport`, Robolectric).
- Instrumented: `MainActivityDeepLinkTest` (profile, sport and predicted links,
  the malformed dialog, waiting for sign-in, Recents), `ShareTextTest`.

## Manual check

1. Install the build, `pm verify-app-links --re-verify dev.alllexey.itmowidgets`,
   then `pm get-app-links`: the dev host is `verified` (and the production host
   for a release-signed build).
2. `am start -a android.intent.action.VIEW -d https://dev.widgets.alllexey.dev/u/<isu>`
   opens the profile without a chooser; `/sport/abc` shows `Ссылка не
   открывается`; `/sport/1` shows `Занятие недоступно` on `Запись`.
3. A link typed into the browser shows the page; `Открыть в приложении` opens
   the app.
