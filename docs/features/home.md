# Home and quick actions

The home feed is a placeholder without pretend refresh. Icon-only QR and MyITMO
FABs at the bottom end open contextual screens; Back returns to the unchanged
home tab, and root-tab selection closes the overlay normally. Localized content
descriptions identify both actions for accessibility without visible labels.

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
