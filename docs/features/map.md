# Campus map

The campus map shows ITMO's indoor floor plans and plans «Маршрут» to a lesson's room. Its code is the
`:shared:feature-map` module; the plan of the whole feature (mirror, MapLibre, screens) is in the UX overhaul
decisions (`vibe/design-lab/ux-native/DECISIONS.md`, «Карта»).

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

`TripPlanner` turns a lesson into legs: from the previous lesson's room in the same building, or from the
target building's entrance; another building is the walk to its entrance, «По городу» (the system Maps) and the
walk from the target entrance.

## Tests

- `RouterParityTest` (host): the Kotlin router against `router.js` on a synthetic graph
  (`scripts/map/router-fixtures.mjs` writes the graph and the expected routes into the test resources). With
  `CAMPUS_MAP_GRAPHS=<dir of <id>.json>` and `CAMPUS_MAP_EXPECTED=<file from router-fixtures.mjs --graphs>` it also
  checks ITMO's real graphs; on 2026-10-17 all 360 pairs of the six buildings matched.
- `IndoorRouterTest`, `RoomMatcherTest`, `PlanBuildingResolverTest`, `TripPlannerTest` (`commonTest`).
