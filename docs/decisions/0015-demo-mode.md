# 0015 A hidden demo session gated in the repositories

**Decision (2026-10-02).** The app has a demo session with fictional data for
the Google Play review. It is entered by five quick taps on the sign-in logo,
lives as a flag in DataStore and is served by the same Hilt graph as a real
session: every class that calls My ITMO, BARS, Backend or a public web page
checks `DemoMode` right before the call and answers with its feature's demo
data or `AppError.DemoUnavailable`. Details are in
[the demo session](../features/demo.md).

**Why a demo at all.** Every screen of the app needs an ITMO.ID student
account, which a Play reviewer does not have and the owner cannot hand out.
Play requires access to the whole app or a working demo; a demo also gives
store and landing screenshots without real people in them.

**Why hidden.** A visible `Демо` button would be the first thing every student
sees on the sign-in screen and would invite them into fictional data instead
of their own. The reviewer gets the gesture in the access instructions of the
listing. The logo stays decorative for accessibility services, so TalkBack
users meet no unexplained focus stop.

**Why a flag, not a build.** The reviewed build is the one users install; a
separate demo flavor or a debug-only switch would not be reviewed as shipped.
The flag survives process death, so the reviewer is not thrown back to sign-in.

**Why gates in the repositories, not a swapped Hilt graph.** Replacing the
network clients or the repositories with demo bindings would decide at graph
creation, while the session changes at runtime, and would need a second
implementation of every repository to keep in step. A check at the call keeps
one implementation, lets caches, view models and screens work unchanged and
makes the boundary testable: the Konsist rule `network clients are gated by
demo mode` fails the build when a new class takes a network client without
`DemoMode`. Gating at the repository rather than in one OkHttp interceptor
also lets reads return typed demo models instead of faked HTTP bodies.

**What it never does.** The demo never reaches the network, never registers a
device, never schedules background work and never changes the stored
`Подключение к ITMO.Widgets` choice; leaving the demo clears its caches.
Synthetic data of debug builds stays in debug fixtures.
