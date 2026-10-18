# Campus map

The campus map shows ITMO's indoor floor plans and plans «Маршрут» to a lesson's room. Its code is the
`:shared:feature-map` module; the spec is the UX overhaul's (`vibe/design-lab/ux-native/DECISIONS.md`, «Карта»),
the data decision [ADR 0033](../decisions/0033-campus-map-mirror.md).

## Screen

`AppRoutes.CampusMap(CampusMapArgs)`, a full screen pushed over the tabs, over `CampusMapViewModel` (Android
`ui/CampusMapScreen.kt` in `androidMain`, iOS `iosApp/Sources/Features/Map/CampusMapScreen.swift`).

- Opened from `Маршрут` in the lesson sheet and on the Сегодня hero (`CampusMapArgs(lesson, route = true)`): the
  lesson's building on the floor of its room, the room selected and the route shown at once.
- The route starts at the day's previous in-person lesson (from `DayLessonsGateway`) when its room is on a plan;
  without one, at the entrance of the target building. Another building is three parts: the walk to its entrance,
  `По городу` with `Открыть в Картах` (the system map app at the target building), the walk from the target entrance.
  Steps are a pager (`Шаг 2 из 4`); each step switches the building and the floor. No lengths, no times.
- Room view: the number big, `4 этаж, Кронверкский пр., 49`, `Маршрут` (from the entrance for any room but the
  lesson's) and `Открыть в Картах`. A near match says `1224в нет на плане, показана 1224`; a room not on the plan says
  `Аудитории 9999 нет на плане` and the plan opens on the entrance floor.
- Browse: the search over the shown building, then `В других корпусах`; the place chips (`Туалеты`, `Еда`,
  `Коворкинги`, ...) show only what the building has; the building chips switch the plan; a tap on the plan selects
  the room under it.
- A building without a plan (Вяземский, Чайковского today): `У корпуса нет плана` with `Открыть в Картах`.
- No mirror and no stored copy: `Не удалось загрузить` with `Повторить`.
- The attribution `План: Университет ИТМО` comes from the manifest.

## Mirror and offline

The mirror is a static tree on `https://map.alllexey.dev/app/v1/` (srvscripts `stacks/itmomap/mirror/sync.py`, a
daily run): the manifest and content-addressed `files/<sha256>.{mbtiles,json}`, plus the label glyphs
(`Noto Sans`). `CampusMapRepositoryImpl` fetches the manifest once per process, keeps the last valid one in
the cache directory `campus_map`, downloads a building's tiles, graph and rooms once, checks each against its
sha256 and deletes files no manifest names. MapLibre reads the tiles from the local file (`mbtiles://`); the glyphs
stay in MapLibre's own cache. Without network the stored manifest and files serve.

The address is `CAMPUS_MAP_URL`: Android `BuildConfig` (`-PcampusMapUrl=<url>` for another mirror), iOS
`Base.xcconfig` through the bundle's `CampusMapURL`. A local mirror for development:
`python3 srvscripts/stacks/itmomap/mirror/sync.py --out /tmp/mirror --once` writes the same tree; serve
`/tmp/mirror/app/v1` over HTTPS, or copy it into the app's `cache/campus_map` (the stored copy serves when the
mirror cannot be reached).

The style is one JSON built in shared code (`CampusMapStyle`): the plan in the app's surface colours, rooms and
classrooms, walls, labels, the route (current part bold), its ends and the selection. Floors switch by the per-layer
`level` filters that `CampusMapStyle.filters` returns and each platform applies.

## Data

ITMO publishes six buildings with plans through the API of its «ITMO Campus» app: 13 Кронверкский 49,
273 Ломоносова 9, 275 Биржевая 14-16, 279 Гривцова 14-16, 15 Песочная 14, 6 Гастелло 12. A building is a vector
MBTiles file and a routing graph (ITMO's transition file). The app reads them from a mirror on alllexey.dev, never
from ITMO directly; the plans are not committed to this repository.

| Data | Format | Code |
|---|---|---|
| Routing graph | GeoJSON points `[lon, lat, level]` with `id`, `area_id` (the plan room), `transitions[{id, distance, type, weight}]`, `outdoor`, `access` | `data/graph/TransitionJson.kt` -> `domain/graph/IndoorGraph` |
| Rooms | the plan's label points: `id, level, class, subclass, name, ref, isu:room_id, lngLat`, every value a string | `data/rooms/RoomsJson.kt` -> `domain/rooms/MapRoom` |

Levels are numbers from 0: level 0 is the first floor.

## Room mapping

A My ITMO lesson has `room` (text), `building` (text), `bld_id` and `main_bld_id`.

- Building (`PlanBuildingResolver`): `main_bld_id` is the plan's building id (13, and 273 for every Ломоносова
  letter, whose `bld_id` is 23, 33, 35 or 37), then `bld_id`, then the alias match of `itmo_buildings.json`, whose
  entries carry `planBuildingId`.
- Room (`RoomMatcher`): the whole text and, for `Name (number)`, the number and the name are compared with the
  plan's `ref`, then its `name`, ignoring case, `ё`, spaces and the `(присв)`/`(усл)` marks; only `class=room`
  units count. `Lemon Classroom (1419)` finds 1419, `Актовый зал (1216/0 (усл))` finds the hall by its name.
- A number with a letter missing on the plan (`1224в`) falls back to the number without it and is marked
  approximate. On the owner's timetable 2025-2026, 47 of 48 rooms match exactly, the 48th approximately.

## Router

`IndoorRouter` is a port of itmo-map-web's `router.js`, itself a port of the campus app:

- Dijkstra as `dijkstrajs` (a sorted open list with stable insertion), so ties resolve as in the original.
- Edge cost `round(weight * distance)`; edges at the start and end node cost 0; `access=private` points are never
  passed.
- Two candidates, the plain route and one without yard points; equal ones are merged, the cheapest is recommended.
- The walk inside the start and the target room is trimmed (the intended `shrinkPath`, without the original's
  index bug).
- Parts: stairs with weight above 1 go up, the others down; a yard part ends at a yard point; the rest is a floor.
  Distances exist for parity only: the app shows no lengths or walking times.

ITMO's graphs have no way into about 7% of the rooms (checked 2026-10-17: 12 of 379 in 13, 56 of 494 in 273, 28
of 338 in 275, 14 of 297 in 279, 2 in 15 and 2 in 6; 1206 in 273 among them). For such a room the trip goes to the
closest place on its floor that has a way and the last step says `По 2 этажу к 1206: вход на плане не отмечен`.

`TripPlanner` turns a lesson into legs: from the previous lesson's room in the same building, or from the
target building's entrance; another building is the walk to its entrance, «По городу» (the system Maps) and the
walk from the target entrance.

## Tests

- `RouterParityTest` (host): the Kotlin router against `router.js` on a synthetic graph
  (`scripts/map/router-fixtures.mjs` writes the graph and the expected routes into the test resources). With
  `CAMPUS_MAP_GRAPHS=<dir of <id>.json>` and `CAMPUS_MAP_EXPECTED=<file from router-fixtures.mjs --graphs>` it also
  checks ITMO's real graphs; on 2026-10-17 all 360 pairs of the six buildings matched.
- `IndoorRouterTest`, `RoomMatcherTest`, `PlanBuildingResolverTest`, `TripPlannerTest` (`commonTest`).
- `CampusMapRepositoryImplTest` (MockEngine, fake file system: download once, offline copy, hash check),
  `CampusMapViewModelTest` (room, route from the entrance and through the city, missing room, no plan, search,
  chips), `CampusMapStyleTest`.
- Screens: `CampusMapScreenshotTest` (Android) and `CampusMapSnapshotTests` (iOS) over `CampusMapSamples`, with a
  placeholder instead of the GL view, which renders nothing in either test. The native view is checked on a device.
