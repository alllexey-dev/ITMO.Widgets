# 0024 MyItmoApi 2.x reaches the app through a pinned composite build

**Decision (2026-10-03).** The app builds MyItmoApi 2.x from source, at one approved commit, and never through Maven Local.
- `gradle/myitmoapi.ref` holds one 40-character MyItmoApi commit that is on M `master` after an owner-approved M
  batch. Only the integrator bumps it.
- `~/proj/.wt/bin/lane new|pin` (the tracked `scripts/lane.sh` later) makes one detached MyItmoApi checkout per lane
  worktree at that commit, `~/proj/.wt/myitmoapi/pin-<worktree>-<sha7>`, and records its path in
  `$(git rev-parse --absolute-git-dir)/itmo-myitmoapi-dir`. One pin per worktree, not one per lane; `lane pin`
  re-pins after a rebase moved the ref, `lane done` removes the pin with the worktree.
- `scripts/verify.sh` passes that path as `-PmyItmoApiDir`; CI checks out the same commit and sets `MYITMOAPI_DIR`.
  `settings.gradle.kts` then calls `includeBuild("<dir>/kmp")`, or `includeBuild("<dir>")` once `kmp/` is the M root.
  Without the property nothing is included; a missing directory fails clearly, a checkout off the ref warns.
- Release builds after gate M2 pass `-PmyItmoApiFromCentral=true` and resolve `dev.alllexey:my-itmo-api-kmp:2.0.0`
  from Maven Central, with no included build.
- `mavenLocal()` leaves `settings.gradle.kts`. No repository uses `publishToMavenLocal`, `mvn install` or `~/.m2`.
- Core is not part of this: Core 2.0 is a module of this repository (ADR 0026). Until the 2.x session lands, the app
  keeps Central `my-itmo-api:1.8.2` and `itmo-widgets-core:1.7.0`, so Android CI works from the first day.

**Why.** Maven Local is one mutable directory shared by every agent and checkout: parallel agents overwrite each
other's SNAPSHOTs, a forgotten publish builds stale code, and a matching version string proves nothing (0003's own
consequence). A composite build of a recorded commit is reproducible on any machine and in CI, needs no publish step
and shows MyItmoApi sources in the IDE. One checkout per worktree, because two builds that include the same build
directory wait on each other's locks, and a shared checkout could move under a running build. Releases take the
signed Central artifact so a tag never depends on a local directory.

**Consequence.**
- An M change reaches the app in this order: M PR → M `v2.3/next` → owner batch on M `master` → ref bump →
  `lane pin`. A lane that needs an unmerged M change waits; no commit points at a local path.
- `kmp/` builds standalone on Gradle 9.7 with `group = "dev.alllexey"`, `rootProject.name = "my-itmo-api-kmp"` and
  no signing or credentials unless it publishes; otherwise substitution silently does not happen.
- The cross-repository order in `AGENTS.md` loses `publishToMavenLocal`; `docs/product/releases.md` records the
  MyItmoApi commit (development) or version (release) each Android revision builds against.
- Disk: one small MyItmoApi checkout per Android lane worktree, plus its `build/` while it is included.

**Supersedes.** 0003 (snapshot versions through Maven Local); in `AGENTS.md`, `publishToMavenLocal` in the
cross-repository change order and the Core build block of § Build and verify; in `docs/product/releases.md`
§ Version compatibility, Core in Maven Local during development.

**Revisit when.** The composite build fails or turns slow on the toolchain in use (fallback: a `file://` repository
per worktree filled by the pin's own publish task); MyItmoApi moves into this repository; a consumer other than
the app needs unreleased 2.x code.

**Settled.** D3 settled the shape; no owner question was asked. SP-09 and SP-10 passed, so the `file://` fallback
is not taken.

**Evidence.**
- SP-09: PASS (`includeBuild` substitution with the `jvm` variant, configuration cache reuse and two worktrees with two pins at once work; `settings-snippet.kts` is TC-05's start)
- SP-10: PASS (Apple targets configure and their klibs compile on the CLT-only Mac without an override flag; linking waits for Xcode)
