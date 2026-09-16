# Home and quick actions

The home feed is a placeholder without pretend refresh. A labelled QR FAB at the
bottom end opens the contextual `QR_PASS` screen; Back returns to the unchanged
home tab, and root-tab selection closes the overlay normally.

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
