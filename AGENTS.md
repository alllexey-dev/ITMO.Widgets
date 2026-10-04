# ITMO.Widgets agent guide

This file is the working agreement for anyone changing the ITMO.Widgets
ecosystem. It states the rules that are not derivable from the code. Detailed
references live in `docs/`; this file only points at them. A nested `AGENTS.md`
in a sibling repository adds repository-specific rules and never contradicts this one.

## Repositories

| Component | Local path | Remote | Owns |
|---|---|---|---|
| Android app | `/Users/alllexey/proj/ITMO.Widgets` | `alllexey-dev/ITMO.Widgets` | UI, local caches, widgets, debug fixtures, this guide |
| Legacy app | tag `v2.0.1` in the same repository | same remote | Read-only reference for v2.0.1 parity (`git show v2.0.1:<path>`); no separate checkout |
| Core | `/Users/alllexey/proj/itmo-widgets-core` | `alllexey-dev/itmo-widgets-core` | Typed Retrofit contract and client for Backend |
| Backend | `/Users/alllexey/proj/itmo-widgets-backend` | `alllexey123/itmo-widgets-backend` | Users, friendships, privacy, sport queues, FCM delivery |
| MyItmoApi | `/Users/alllexey/proj/MyItmoApi` | `alllexey123/MyItmoApi` | Typed Java client for official MyITMO and BARS |

They are independent Git repositories: inspect status, diff, tests and commits
separately, and report a commit hash per repository.

## Where things are documented

| Topic | Document |
|---|---|
| Release sequence, version compatibility, deferred features | `docs/product/releases.md` |
| Delivery order of the social and study features | `docs/product/roadmap.md` |
| Android package structure, layers, enforced rules | `docs/architecture.md` |
| Visual language, spacing, states, verification matrix | `docs/design.md` |
| User-facing settings and their defaults | `docs/settings.md` |
| Per-feature behaviour and invariants | `docs/features/*.md` |
| Decisions that were argued once and must not be re-litigated | `docs/decisions/*.md` |
| Backend contracts, database and deployment | `../itmo-widgets-backend/docs/` |
| Core wire conventions | `../itmo-widgets-core/docs/contract.md` |
| Plans and progress trackers for work in flight | `vibe/` (ignored, local only) |

Documents describe the current state. History goes to each repository's
`CHANGELOG.md`, never into a reference document.

## Source-of-truth boundaries

- **MyItmoApi** is the only client for official MyITMO and BARS endpoints. Extend
  it instead of adding a second Retrofit interface anywhere else. Models document
  the observed response shape, units, enum-like values and whether null was
  actually observed; unknown fields stay as commented declarations, not `Any?`.
- **Core** is the typed contract for Backend. Backend changes first, Core mirrors
  them with serialization and MockWebServer tests, Android consumes last.
  Retrofit annotations mirror the controllers exactly.
- **Backend** is authoritative for social data and authorization. It returns
  viewer-scoped capabilities, never another user's raw privacy settings, and does
  not trust the client to enforce access.
- **Android** owns presentation, caches, widgets and local preferences. It never
  enforces privacy on behalf of Backend.

Cross-repository change order: privacy boundary → Backend + tests → Core + tests
→ Android → test against `https://dev.widgets.alllexey.dev` → release and deploy
only on explicit request. Nothing goes through Maven Local (ADRs 0024 and 0026).

## Hard rules

- Academic logic takes time from `AcademicTimeProvider` or an injected `Clock`.
  No direct `now()` calls in feature code; the Konsist suite enforces it.
- UI moves to Compose Multiplatform screens in shared modules, with Koin and
  Navigation 3, as ADRs 0017, 0019 and 0020 decide.
- Dependency direction inside a feature is `ui -> presentation -> domain <- data`;
  features never import each other. See `docs/architecture.md`.
- Everything that reaches Backend is gated on the custom-services opt-in inside
  the repository layer.
- Synthetic data lives in debug fixtures and the demo session (`DemoMode`); it
  never reaches Backend. Every class that takes a network client checks
  `DemoMode` before the call; the Konsist suite enforces it.
- User-visible text lives in string resources and is Russian.
- Visual verification follows ADR 0022: JVM screenshot baselines in every UI
  change, the full emulator appearance matrix before a release. Compilation is
  not visual verification.

## Secrets and data

Never commit or paste `.refresh-token`, `local.properties`, `app-keystore.jks`,
Backend `.env`, database dumps, Firebase keys, tokens or user data. Redact
command output before quoting it. ITMO credentials are entered only on official
ITMO pages; refresh tokens stay on the device. Backend authenticates requests
with the ITMO.ID access token and never stores the user's refresh token.
Never mutate production data for testing.

## Build and verify

Run commands from the repository they belong to. Every affected repository must
build and test before a cross-repository change is called done.

```bash
# Android (JDK 17+): both distribution variants, github (APK) and play (AAB)
./gradlew :app:testGithubDebugUnitTest :app:testPlayDebugUnitTest :app:lintGithubDebug :app:lintPlayDebug \
  :app:assembleGithubDebug :app:assemblePlayDebug
# Before a Play upload: manifests, targetSdk, 16 KB alignment, no GitHub update link in play
scripts/check-play-policy.sh
```

Core 1.x is frozen at `1.7.0` on Maven Central; Core 2.0 is the module
`:shared:backend-client` of this repository and builds with the Android commands
above (ADR 0026). MyItmoApi 2.x enters through the pinned composite build (ADR
0024); no repository publishes to Maven Local.

```bash
# Backend (JDK 21, Docker via colima for Testcontainers)
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true \
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build
```

Instrumentation tests run on an emulator, not on the user's phone, unless a
device-specific issue requires it.

## Git hygiene

- Inspect `git status --short --branch` before editing and before reporting.
- Do not reset, clean, stash, rebase, commit, push, tag, publish or deploy unless
  asked. Preserve the current branch name; no generated branch names.
- Do not commit IDE metadata, build outputs, `app/release/` or local Maven artifacts.
- UTF-8, LF, no trailing whitespace, final newline. Comments explain invariants,
  not obvious code.

## v2.3 lanes

Applies only to an agent executing a card of a v2.3 lane file (read from the main checkout's
`vibe/` by absolute path). For such an agent this section replaces the Git hygiene lines about
commit, push and branch names (lines 115-116) and Definition of done item 10; where it and the
rest of this file disagree (for example `publishToMavenLocal`), this section wins.

Allowed without asking:
- work in a worktree `~/proj/.wt/<repo>/<lane-id>-<slug>` created by `~/proj/.wt/bin/lane new`
  from `origin/v2.3/next`; never in a main checkout, `/tmp`, `/private/tmp` or `.claude/worktrees/`;
- the card's exact branch `v2.3/<lane-id>/<card-id>-<slug>` (lowercase);
- commits with explicit paths, one line `<CARD-ID>: <summary>`, no AI attribution, secrets,
  user data, build outputs or IDE files;
- rebase on `v2.3/next`, `--force-with-lease` on the own branch only, push the own branch,
  a PR into `v2.3/next` with the fields of the plan's integration protocol, deleting the own
  branch after merge.

Only the integrator merges into `v2.3/next`, and only through `~/proj/.wt/bin/integrate`;
protected refs (`master`, Backend `dev`, `release/*`) move only through `~/proj/.wt/bin/promote`
after the owner's OK for that batch.

Forbidden for every agent, the integrator included:
- tags, `gh release`, `publishToMavenLocal`, `mvn install`/`deploy`, Maven Central publishing,
  the Backend release workflow, Play Console, App Store Connect, TestFlight, Firebase, Apple
  portal, server changes on alllexey.dev;
- `./gradlew --stop`, killing other agents' daemons or emulators, `colima stop/start`, editing
  `~/.gradle/gradle.properties`, AVDs or `~/.m2`;
- `git stash`, `reset --hard`, `clean`, `checkout` or branch switches in a main checkout;
  deleting branches or worktrees that are not the agent's own; `--no-verify`;
- opening secrets (see Secrets and data), real user data as fixtures, production data changes;
- changes outside parity or outside the card's declared surface without the integrator's OK;
- the owner's phone or iPhone (integrator only); `connected*`/`install*` without
  `ANDROID_SERIAL=emulator-*`.

Still the owner's explicit word each time: protected-ref moves, dev and production deploys,
the Flyway V11 merge, every tag and release, store uploads, `app.minimum` and `APP_VERSION`,
ADR acceptance and M3E values, deletions of branches and stashes, hooks, permission rules,
rulesets, colima resize, AVD creation, installs on the owner's devices.

Plans in these repositories use the v2.3 card format; the user-global rules `plan.md`,
`allsteps.md`, `local-ci-check-manual.md`, `ya-build-test.md`, `linter-rules.md` and
`ticket.md` under `~/.claude/rules/generated/` do not apply here. `working-rules.md`
"never commit or push on your own" yields for the lane actions above only; its other rules
(no AI attribution, test rules) still apply.

## Definition of done

1. The source-of-truth boundary is respected and no duplicate API layer exists.
2. Authorization and privacy are enforced and tested on Backend.
3. Time-dependent logic is deterministic.
4. Loading, content, empty and error states are handled without layout jumps.
5. Screenshot baselines cover every changed UI state; releases run the ADR 0022 matrix.
6. Tests exist at the layer where the behaviour lives.
7. Every affected repository builds and tests with its JDK.
8. No secret or user data appears in the diff, logs or docs.
9. `docs/` describes the new current state; `CHANGELOG.md` records the change.
10. Commit, push, publish and deploy happened only when requested and were verified.
