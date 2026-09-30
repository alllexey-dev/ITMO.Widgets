# 0012 BARS is renewed in the background by replaying ITMO.ID with cookies

**Decision (2026-09-30).** The background mark check renews the BARS token
without a WebView: it repeats the official ITMO.ID authorization request of the
`bars` client through OkHttp with the ITMO.ID cookies the app's WebView already
holds, and exchanges the returned code through the library's
`BarsCodeSupplier`. This is the only background renewal path. Screens keep
renewing in a hidden WebView (`BarsWebSilentLogin`).

**Why a renewal at all.** The BARS token lives 30 minutes, has no refresh token
and cannot be exchanged from a My ITMO token. The ITMO.ID session in the app's
WebView lives about 90 days. A check every 3 hours therefore always needs a new
BARS token, and only that session can give one without the user.

**Boundaries.** `BarsCookieSilentLogin` reads the `Cookie` header that
`CookieManager` holds for the `bars` authorization URL and sends it only there,
over HTTPS, with redirects off and without the client's own cookie jar or
cache. Only a redirect to the exact callback with the same `state` gives a code;
a page of ITMO.ID means the user is needed; any other address is rejected. The
answer's `Set-Cookie` values go back to `CookieManager` for that URL only, as
the WebView would store them. Codes, cookies and tokens never reach the log,
exceptions, `toString()`, files or Backend; a code is exchanged at once.

**Parsing in MyItmoApi.** MyItmoApi is the only client of the official ITMO.ID
and BARS endpoints, so the request and the reading of its answer are
`BarsAuthHelper.requestCodeWithCookies(state, cookieHeader)` in the library
(1.8.2-SNAPSHOT): `CODE`, `LOGIN_REQUIRED`, `REJECTED` or `HTTP_ERROR`, and a
network failure as an exception. The app adds no second HTTP client for
ITMO.ID.

**Ended session versus failure.** Only no cookies or `LOGIN_REQUIRED` end the
session. A rejected callback, an HTTP error or no network is a failure: the
saved BARS header stays, the run is retried later, and nothing asks the user to
sign in. An ended session posts one `Войдите в БАРС` notification; after it the
app stays silent until the next successful BARS answer, while every run still
tries the cookies once in case the ITMO.ID session came back.

**The user's period and the device.** Reading marks selects the half-year on
the BARS server, a selection shared with web BARS; the read selects the user's
previous period back afterwards. BARS data, like the token and the cookies,
stays on the device.

**Rejected alternatives.**

- `BarsAuthHelper.obtainCodeFromSession`: it returns `null` both for the
  sign-in form and for a 5xx answer, so a server failure would become a false
  `Войдите в БАРС`.
- A hidden WebView in the worker: it needs the main thread of a process that
  has no screen at that moment and is heavy there, while the cookie request
  gets the same code with one HTTP request.
- Backend: it would need the user's ITMO.ID cookies or BARS token and a copy
  of the marks on the server, which the privacy boundary rules out, and it
  would make the check depend on `Подключение к ITMO.Widgets`.
