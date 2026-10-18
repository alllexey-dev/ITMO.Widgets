# 0033 Campus plans come from a static mirror, outside the Backend opt-in

**Status.** Accepted (2026-10-17).

**Decision.** The campus map reads ITMO's floor plans from a static mirror on alllexey.dev
(`https://map.alllexey.dev/app/v1/`, srvscripts `stacks/itmomap/mirror`), never from ITMO's map
API directly and never through Backend.
- The mirror publishes ITMO's server data unchanged (the MBTiles file and the routing graph of each
  building) plus a rooms file derived from the tiles, as content-addressed files under one
  `manifest.json`; the app downloads a building's files once per content hash and keeps them in its
  cache, so the map works offline after the first open.
- The map is not behind `BackendGate`: the files are public, identical for everyone and fetched with
  no account, token or session. The demo session reads the same mirror, since the demo lessons are in
  real buildings and nothing of the user is sent. `CampusMapApi` still takes `DemoMode`, as every
  holder of a network client does (`GateRulesTest`).
- The router is the campus app's, ported to shared Kotlin (`IndoorRouter`); the style, fonts and icons
  are the app's own, not the campus app's assets.

**Why.** Backend calls need the custom-services opt-in, and a map behind a consent screen would be
missing exactly when a student needs a room. Serving immutable files through Spring would add load
and a deploy dependency for no gain. Calling ITMO's API from every phone would multiply the load on a
service we do not own and break the app the day its address changes; the mirror absorbs that in one
place and keeps the last good files. The catalog versions of ITMO's API do not follow content changes,
so only content hashes are trusted.

**Consequence.**
- Before release the owner asks ITMO for permission to redistribute the plans; a takedown is an empty
  manifest, and the app then shows «У корпуса нет плана» with the Maps hand-off.
- A plan update reaches apps within a day of the mirror's run, without an app release.
- MapLibre Native is a dependency on both platforms (Android `org.maplibre.gl:android-sdk`, iOS
  `maplibre-gl-native-distribution`).

**Amended (2026-10-18).** The owner asked for the look students know from the official app and map.alllexey.dev,
so the map now uses the campus app's style and its 36 icons (from itmo-map-web), bundled in the app rather than
served by the mirror (144 KB, offline from the first frame, no server change). The labels stay Noto Sans: the campus
app's glyphs are cut from SF Pro, which may not be redistributed. The style and icons are ITMO's assets too, so they
fall under the same permission request; the map shows no caption, and Settings, `Лицензии` names the source.

**Revisit when.** ITMO publishes the plans under a licence or an official API for third parties, or
the mirror needs per-user data.
