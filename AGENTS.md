# ITMO.Widgets agent guide

Rules for changing the ITMO.Widgets ecosystem that the code does not show; detail is in `docs/` by link. A sibling
`AGENTS.md` adds rules and never contradicts these. Inspect, test and report a commit per repository.

## Repositories

| | Component | Local path | Remote | Owns |
|---|---|---|---|---|
| A | Apps | `/Users/alllexey/proj/ITMO.Widgets` | `alllexey-dev/ITMO.Widgets` | Android app, shared KMP modules, Core 2.0 (`shared/backend-client`), widgets, debug fixtures, this guide; tag `v2.0.1` is the read-only parity reference (`git show v2.0.1:<path>`) |
| B | Backend | `/Users/alllexey/proj/itmo-widgets-backend` | `alllexey-dev/itmo-widgets-backend` | Users, friendships, privacy audiences, sport queues, subject links, teacher reviews and AI summaries, web sessions and admin, service credentials, FCM delivery, the OpenAPI document |
| M | MyItmoApi | `/Users/alllexey/proj/MyItmoApi` | `alllexey-dev/my-itmo-api` | Clients for official MyITMO and BARS: Java 1.x and KMP 2.x |
| W | Web | `/Users/alllexey/proj/itmo-widgets-web` | `alllexey-dev/itmo-widgets-web` | Landing, App Link pages and their asset links, the web app with admin |
| C | Core 1.x | `/Users/alllexey/proj/itmo-widgets-core` | `alllexey-dev/itmo-widgets-core` | Frozen at `1.7.0` on Maven Central for 2.1 and 2.2 clients; no new work (ADR 0026) |

## Where things are documented

`docs/README.md` indexes `docs/product/` (releases, roadmap), `docs/architecture.md`, `docs/design.md`,
`docs/settings.md`, `docs/features/` (with the screen inventory), `docs/decisions/` (argued once, never re-litigated)
and `docs/process/` (tiers, cards, parallel agents, ownership, integration, release). Siblings:
`../itmo-widgets-backend/docs/`, `../itmo-widgets-web/web/docs/`, `../MyItmoApi/docs/`. Docs describe the current
state; history goes to `changelog.d/` fragments (A and B, format in its `README.md`), folded into `CHANGELOG.md` at a
release, and straight to `CHANGELOG.md` in M and W. Plans, lane files and the board live in the main checkout's
ignored `vibe/`, read by absolute path: one directory per release, archived after it, no secrets.

## Source-of-truth boundaries

- **MyItmoApi** is the only client for official MyITMO and BARS; extend it, never add a second one. Models document
  the observed shape, units, enums and nulls; 2.x reaches A pinned by `gradle/myitmoapi.ref` (ADRs 0024, 0025).
- **Backend** is authoritative for social data and authorization, owns the wire DTOs and the OpenAPI document, returns
  viewer-scoped capabilities, never another user's raw privacy settings, and never trusts a client.
- **Core 2.0** (`shared/backend-client`) is a client-only mirror of Backend with conformance tests (ADR 0026); Web
  calls Backend directly. **Apps** own presentation, caches, widgets and local preferences, never privacy.
- Cross-repository order, each step merged first: privacy boundary -> Backend + fixtures -> OpenAPI -> Core 2.0 +
  conformance -> apps (Android, iOS, Web) -> `https://dev.widgets.alllexey.dev`. No Maven Local.

## Hard rules

- Time comes from `AcademicTimeProvider` or an injected `Clock`; no direct `now()` in feature code (Konsist).
- UI is Compose Multiplatform in shared `commonMain` with Koin and Navigation 3 (ADRs 0017, 0019, 0020); `app/` keeps
  Android-only surfaces and RemoteViews widgets (ADR 0027). `commonMain` has no `android.*`, `java.*`, `R`, Hilt or
  `javax.inject`; features never import each other; `ui -> presentation -> domain <- data`.
- Every Backend call passes `BackendGate.mayCallBackend()` (the custom-services opt-in) in the repository layer; every
  network-client holder checks `DemoMode` first, so demo and fixture data never reach Backend (Konsist).
- User-visible text is Russian, one catalog file per owner module (`scripts/strings-owners.py --where`), shared files
  by L05 only; icons are Material Symbols Rounded from `docs/design/icons.tsv` (ADR 0028).
- Visual verification is tiered (ADR 0022): JVM screenshot baselines for every changed UI state, an emulator only for
  Android-only surfaces, the full appearance matrix before a release. Compilation is not visual verification.

## Secrets and data

Never open, commit or paste `.env*`, `properties.env`, `local.properties`, `keystore.properties`, `*.jks`,
`.refresh-token`, `*.har`, `serviceAccountKey.json`, the Gemini key in `vibe/`, database dumps, Firebase admin keys,
tokens or user data; redact output. `app/google-services.json` is Firebase client config and stays tracked. ITMO
credentials go only into ITMO pages; refresh tokens stay on the device. Never mutate production data.

## Build and verify

Gradle in A runs only through `scripts/verify.sh` (slots of `scripts/slot.sh`, JDK 21, the MyItmoApi pin); never
`./gradlew --stop`, `publishToMavenLocal` or `install*`. Detail: `docs/process/parallel-agents.md`.

```bash
scripts/verify.sh quick | full | shots <module>|app [--record] | klibs | ship | run -- <gradle args>
ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui <Class>   # serial from scripts/emulator.sh up, then down
```

`quick` (per card, CI): `scripts/check-docs.sh`, `scripts/test-new-feature-module.sh`, tests, Konsist, lint,
assembles; `full` adds both lints and iOS klibs; `ship` adds `scripts/check-play-policy.sh`. PRs quote the last line,
`VERIFY A <mode> PASS|FAIL <secs>s <sha7>`. Never the owner's phone or `emulator-5554`; only L02 edits the shrinking
`scripts/check-docs.known`. B, M and W run their own `scripts/verify.sh`; C is frozen.

## Git hygiene

- Inspect `git status --short --branch` before editing and reporting; never commit IDE metadata, build outputs,
  `app/release/` or Maven Local artifacts. UTF-8, LF, no trailing whitespace, final newline.
- Do not reset, clean, stash, rebase, commit, push, tag, publish or deploy unless asked. Preserve the current branch
  name; no generated branch names.

## v2.3 lanes

Applies only to an agent executing a card of a v2.3 lane file (read from the main checkout's `vibe/` by absolute
path). For such an agent this section replaces the Git hygiene lines about commit, push and branch names and
Definition of done item 10; where it and the rest of this file disagree (for example `publishToMavenLocal`), this
section wins.

Allowed without asking:
- work in a worktree `~/proj/.wt/<repo>/<lane-id>-<slug>` created by `~/proj/.wt/bin/lane new` from
  `origin/v2.3/next`; never in a main checkout, `/tmp`, `/private/tmp` or `.claude/worktrees/`;
- the card's exact branch `v2.3/<lane-id>/<card-id>-<slug>` (lowercase);
- commits with explicit paths, one line `<CARD-ID>: <summary>`, no AI attribution, secrets, user data, build outputs
  or IDE files;
- rebase on `v2.3/next`, `--force-with-lease` on the own branch only, push the own branch, a PR into `v2.3/next` with
  the fields of the plan's integration protocol, deleting the own branch after merge.

Only the integrator merges into `v2.3/next`, and only through `~/proj/.wt/bin/integrate`; protected refs (`master`,
Backend `dev`, `release/*`) move only through `~/proj/.wt/bin/promote` after the owner's OK for that batch.

Forbidden for every agent, the integrator included:
- tags, `gh release`, `publishToMavenLocal`, `mvn install`/`deploy`, Maven Central publishing, the Backend release
  workflow, Play Console, App Store Connect, TestFlight, Firebase, Apple portal, server changes on alllexey.dev;
- `./gradlew --stop`, killing other agents' daemons or emulators, `colima stop/start`, editing
  `~/.gradle/gradle.properties`, AVDs or `~/.m2`;
- `git stash`, `reset --hard`, `clean`, `checkout` or branch switches in a main checkout; deleting branches or
  worktrees that are not the agent's own; `--no-verify`;
- opening secrets (see Secrets and data), real user data as fixtures, production data changes;
- changes outside parity or outside the card's declared surface without the integrator's OK;
- the owner's phone or iPhone (integrator only); `connected*`/`install*` without `ANDROID_SERIAL=emulator-*`.

Still the owner's explicit word each time: protected-ref moves, dev and production deploys, the Flyway V11 merge,
every tag and release, store uploads, `app.minimum` and `APP_VERSION`, ADR acceptance and M3E values, deletions of
branches and stashes, hooks, permission rules, rulesets, colima resize, AVD creation, installs on the owner's devices.

Plans in these repositories use the v2.3 card format; the user-global rules `plan.md`, `allsteps.md`,
`local-ci-check-manual.md`, `ya-build-test.md`, `linter-rules.md` and `ticket.md` under `~/.claude/rules/generated/`
do not apply here. `working-rules.md` "never commit or push on your own" yields for the lane actions above only; its
other rules (no AI attribution, test rules) still apply.

## Definition of done

1. Every change: the source-of-truth boundary is respected and no duplicate API layer exists.
2. Every change: time-dependent logic is deterministic; tests exist at the layer where the behaviour lives.
3. Every change: each affected repository passes its `scripts/verify.sh`; no secret or user data in diff, logs, docs.
4. Every change: `docs/` describes the new state; a closed feature has one `changelog.d/` fragment.
5. UI: loading, content, empty and error states have no layout jumps; baselines cover every changed state.
6. UI: widgets, the tile, shortcuts, notifications and WebView screens are checked on a pool emulator.
7. Cross-repository: authorization and privacy are enforced and tested on Backend; fixtures and conformance follow.
8. Cross-repository: the change works against `https://dev.widgets.alllexey.dev` once Backend is deployed there.
9. Release: the ADR 0022 appearance matrix and `docs/process/release-checklist.md` have passed.
10. Commit, push, publish and deploy happened only when requested and were verified.
