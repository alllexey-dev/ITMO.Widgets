# 0037 ISU is read on the device with the phone's ITMO.ID session

**Status.** Accepted (2026-10-26).

**Decision.** The app reads the user's own ISU pages (isu.ifmo.ru, Oracle APEX) on the phone through one client,
`IsuWebClient` in `:shared:core` (`core/network`). It signs in as a browser that is already signed in to ITMO.ID: it
opens the portal `f?p=2143:1`, follows ISU's OIDC redirects by hand and sends the WebView's ITMO.ID SSO cookies
(`ItmoIdCookies`, ADR 0012) only to `id.itmo.ru`; the ISU cookies of the sign-in live in memory for one `IsuSession`
and go only to ISU. Any other host and plain HTTP are refused; ITMO.ID's `Set-Cookie` goes back to the WebView's
store. Every feature that reads ISU takes this client (the Wi-Fi sheet first, ISU messages next), and every holder
checks `DemoMode` (`GateRulesTest`).

**Why on the device.** The page holds the user's own data (the Wi-Fi password, the inbox). Reading it on a server
would need each user's ITMO.ID cookie there: a 90-day credential to all of ITMO's services. The backend's ISU client
uses one technical account and must never see users' cabinets.

**Why not MyItmoApi.** MyItmoApi is the client of My ITMO, BARS and ITMO.ID's own endpoints. ISU is another
application behind the same SSO, scraped from HTML; the ITMO.ID hop here is ISU's redirect, answered by the cookies
the WebView already holds, not a call to an ITMO.ID API. If the library grows an ISU area later, this client moves
there unchanged.

**Boundaries.** Cookies, session numbers and page bodies are never logged, put into exceptions or `toString()`.
A missing SSO cookie or ITMO.ID's sign-in form means the user is needed (a refresh-token sign-in has no cookies);
clients show a way to the page in the browser instead of asking for a password. Nothing is written in ISU: no
client calls a state-changing APEX process.
