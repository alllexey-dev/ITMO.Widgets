# QR pass

- The QR pass screen is Compose in `:shared:feature-qr`, hosted by the same
  `QrCodeFragment`; its `QrCodeViewModel` comes from Koin.
- The QR pass is fetched through MyItmoApi 2.x.
- The pass data moved to `:shared:feature-qr` `commonMain`: the cache, the
  widget reveal state, the colour and tile settings. Koin owns
  them; the widget, the tile and the worker read them through
  `di/bridge/QrBridge.kt`, so the screen and the widget share one cached pass.
- `cache/qr_hex` keeps the 2.2 format and the v2.0 fallback; a pass still
  expires 60 minutes after it was saved, on the wall clock.
- Sign-out clears Koin-built session data through
  `di/bridge/SessionCleanersBridge.kt`; the QR cache and the QR bitmaps go as
  before.
