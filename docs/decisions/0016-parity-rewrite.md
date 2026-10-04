# 0016 v2.3 is a parity rewrite

**Decision (2026-10-03).** v2.3 moves the Android app onto Kotlin Multiplatform and Compose
Multiplatform and adds an iOS client. It ships no product feature beyond what
the iOS client needs. Every v2.2 behaviour, screen state, setting and text
stays as it is, and the stable identifiers keep their values: manifest
components, App Link hosts and paths, worker classes and work names, shortcut
ids, intent actions, notification channels, preference, file and Keystore
names, `BuildConfig` ids. `StableIdentifiersTest` holds them as literals; a
change needs its own ADR and a dual read of the old value.

**What iOS may add.** Only work without which the iOS client cannot run or
ship: the platform parameter of the app-version endpoint, in-app account
deletion, alert pushes with localisation keys, the widget snapshot in the App
Group. Each such item names its iOS consumer.

**Where the rest goes.**
- Product ideas wait for v2.4: Glance widgets, the sport score widget,
  English localisation and the other post-2.2 candidates. iOS reaches
  Android parity in v2.3 (11 Q8 (b), ADR 0023).
- Changes Google Play forces before 2.3 ships land on `release/2.2` as a
  2.2.x release and are forward-ported; they never widen v2.3.
- One visible deviation is accepted: sport «Запись» success messages become
  snackbars, because `commonMain` has no `Toast`.

**Why.** The rewrite touches every screen, the DI graph, navigation and the
data layer at once. Parity gives each port a yes/no test (the v2.2 screenshot,
the v2.2 test, the v2.2 identifier) and lets a port revert alone. Installed
widgets, alarms, shortcuts and pending work hold class names and actions, so a
renamed identifier breaks users without any visible code change.

**Consequence.** A lane that finds a v2.2 bug fixes it on `release/2.2` first
and ports it forward; a port does not "improve" a screen. Feature docs and the
roadmap name v2.4 for the parked items.

**Supersedes.** Nothing.

**Revisit when.** v2.3 ships, or a store requirement cannot wait for 2.2.x.

**Settled.**
- Strict parity (13 Q3 (a), by A1). An owner-picked item can still join a
  later card without changing the rest of the plan.
- The snackbar deviation stays unless the owner vetoes it before the sport
  port; on a veto the sport host shows a `Toast` from an effect callback.

**Evidence.**
- No spike gates this decision. The parity baseline is the `v2.2` tag; the
  identifier list is report 13's stable-identifier table as frozen by G-03.
