# Integration of lane work in v2.3

How a lane's pull request reaches `v2.3/next` and how `v2.3/next` reaches the default branches of the
Android app (A), Backend (B), MyItmoApi (M) and the web app (W) while v2.3 is developed in parallel lanes. The
rules (allowed actions, the forbidden list, what needs the owner's word) live only in the "v2.3 lanes" section of
`AGENTS.md`; why they look this way is in
[0029 Lanes and the integrator](../decisions/0029-lanes-and-integrator.md), and the release lines in
[0030 Release lines](../decisions/0030-release-lines-and-data-continuity.md). Lane files, the board, batch files
and status ledgers are local (`vibe/`, ignored by Git).

## Roles

| Role | Works in | Does |
|---|---|---|
| Lane agent | `~/proj/.wt/<repo>/<lane-id>-<slug>` from `lane new` | one card per worktree, branch and PR into `v2.3/next`; pushes only its own `v2.3/<lane-id>/*` branch |
| Integrator | `~/proj/.wt/<repo>/next` from `lane next` (marker `integrator`) | merges, reverts, batches, version lines, the 2.2.x line, ledgers; prints owner-only commands and never runs them |
| Second integrator | the `next` worktrees of B, M and W only | the same duties for those repositories; started only by the owner (see below) |
| Owner | anywhere | batch OKs, protected-ref moves, dev deploys, tags, releases, store uploads, guard changes |

Main checkouts under `~/proj/` belong to the owner: agents never edit, switch, stash, pull or build there.

## Tools

| Command | Source | Notes |
|---|---|---|
| `~/proj/.wt/bin/lane` | `scripts/lane.sh` | `new`, `next`, `pin`, `done`, `list`; worktrees, the `itmo-lane` marker, MyItmoApi pins |
| `~/proj/.wt/bin/integrate` | `scripts/integrate.sh` | `list`, `merge`, `revert`, `batch`, `ready` |
| `~/proj/.wt/bin/slot.sh` | `scripts/slot.sh`, `scripts/slots.conf` | machine-wide build slots for every repository |
| `~/proj/.wt/bin/promote` | untracked | the only way protected refs move |
| `~/proj/.wt/git-hooks/pre-push` | untracked | which refs a worktree may push, by its marker and path |

- `lane` and `integrate` are wrappers: each run fetches `origin` into the remote-tracking refs of the main Android
  checkout and runs the script from `origin/v2.3/next` through bash. A change to either script takes effect when
  it is merged, never from a lane's copy. The scripts take every path from `ITMO_WT` (default `~/proj/.wt`, whose
  parent holds the main checkouts), never from their own location; the guard tests point `ITMO_WT` at a replica.
- `slot.sh` and `slots.conf` in `~/proj/.wt/bin/` are copies of the merged files, because `slot.sh` reads the
  `slots.conf` beside it and runs on every build. The integrator refreshes both copies whenever a batch changes
  either file. The verify scripts of B, M and W call `${ITMO_SLOT_SH:-~/proj/.wt/bin/slot.sh}`; A's
  `scripts/verify.sh` uses its own `scripts/slot.sh`.
- `promote` and the pre-push hook stay untracked and change only with the owner's word.
- Logs: `~/proj/.wt/run/integrate.log` (every merge, conflict and revert), `~/proj/.wt/run/promote.log` (every
  protected-ref move).

## Pull request fields

Title `<CARD-ID>: <summary>`, or `<ID>, <ID>: <summary>` for several items. The body carries one line per field:

| Field | Content |
|---|---|
| `Lane:` | the lane id, for example `L11-sport` |
| `Items:` | card ids |
| `Surface:` | comma-separated globs covering every changed path, starting from the card's `touches`; no commas or notes inside an entry |
| `Surface notes:` | optional: why a path outside `touches` is there (test code that exercises touched code counts as inside) |
| `Slots:` | the build slots the verification took |
| `Verify:` | the last line of `scripts/verify.sh` for the pushed head, or a log path where the repository has no PR CI |
| `Roborazzi diff:` | `none` or the artifact link |
| `Revertable alone:` | `yes`, or `no` with the reason |
| `Stable identifiers touched:` | `none`, or the identifiers and the green ship check that covers them |
| `Recipe written:` | `none` or the recipe name |

Two sections follow: `## Docs hand-in` (changes the documentation owner applies) and `## Catalog hand-in` (strings
and icons for the shared catalogs), each `none` when empty.

## Merge

`integrate merge <repo> <pr>` refuses unless:

- the base is `v2.3/next` and the head is `v2.3/<lane-id>/<slug>` with a matching title (in A also Dependabot
  branches, merged as `TC-08: <title> (#<n>)` while no toolchain PR touching build files is open);
- CI is green on the head; a repository without check runs needs `--local-verify <log>` ending in
  `VERIFY <repo letter> <mode> PASS ... <head sha7>` plus a passing `Verify:` line in the body;
- every changed path is inside `Surface:`, unless a PR comment starting with `surface OK` accepts the rest.

It squashes onto a fresh `origin/v2.3/next` as `<title> (#<n>)`, pushes, closes the PR with
`Squash-merged into v2.3/next as <sha>` and marks the items merged on the board. A conflict aborts the merge,
comments `rebase on v2.3/next` and leaves the PR open: the lane rebases, never the integrator.

- Order: hot files of the master plan first, in its order. `integrate list <repo>` shows the queue, CI, size
  (above 1,500 changed lines is flagged; the lane splits it) and hot files per PR.
- Generated files (string catalogs, exported resources, the Backend OpenAPI snapshot, contract fixtures) are
  regenerated by the lane, never merged by hand.
- More than 10 queued PRs in a repository stops new lane work there until the queue drains.
- After a lane's merge, the lane runs `lane done <worktree>`, which deletes its remote branch and worktree.

## Revert

`integrate revert <repo> <sha> <reason>` runs `git revert` on `v2.3/next`, pushes, comments on the PR and marks its
items blocked with the reason. `v2.3/next` only moves forward: squash merges and reverts, never a force-push. A red
batch head or a red ship check reverts the culprit; it is never fixed forward on `v2.3/next`.

## Batches and promote

- Two review windows a day, about 14:00 and 19:00 MSK. In each, `integrate batch <repo> --window 14|19` writes a
  local batch file for `origin/<default>..origin/v2.3/next` per repository with merges: PRs with their items, CI,
  diffstat, Roborazzi lines, Backend migrations, open risks and, for A, the `githubDebug` APK (`--build-apk`
  builds it in the android slot). The file ends with the exact `promote` command.
- Before a batch is offered its head passes: A `scripts/verify.sh` in `full` mode, the platform UI tests on the
  integrator's emulator and the iOS check; B, M and W `scripts/verify.sh`.
- The owner answers `OK <repo> <sha>`; the integrator then runs that `promote` (Claude asks first). `promote` moves
  only by fast-forward, checks provenance and CI and consumes a one-shot intent. A rejection reverts the named
  PRs.
- B (and W once its deploy pipeline exists) move `dev` only on the owner's "deploy dev", at most once a day; the
  delivery workflow then moves the default branch.
- Version lines: `versionCode` and `versionName` in `app/build.gradle.kts` change only through the integrator,
  per decision 0030.

## CI

Android verification of A runs on GitHub-hosted `ubuntu-latest` runners (the repository is public, so runner
minutes are free), in parallel jobs, so nothing heavy has to run on the laptop for a PR. Three checks carry it:

| Check | Workflow | Runs on | Jobs | Wall time |
|---|---|---|---|---|
| `verify-quick` | `android-ci.yml` | every PR into and push to `v2.3/next` and `master` | `checks` (`verify.sh checks`), `unit-app`, `unit-shared`, `lint-github`, `assemble` (the parts of the root `verifyQuick`), `lint-play`, `shots 0/3`..`2/3` (`verify.sh shots all --shard`), `klibs` | WALL_CI |
| `android-ui` | `android-ui.yml` | PRs into `v2.3/next` that touch what the app is built from, every push to `v2.3/next`, manual runs | `ui plain 0/6`..`5/6` (`verify.sh ui all --shard <i>/6`), `ui cutout 0/2`..`1/2` (`verify.sh ui ShellSuite --shard <i>/2` with the tall cutout overlay) | WALL_UI |
| `android-ship` | `android-ship.yml` | every push to `v2.3/next`, manual runs, PRs that change it or the scripts it runs | `ship version-release` (ship check stages 1 and 3), `ship upgrade` (stage 5), `ship shrunk` (stage 6) | WALL_SHIP |

- Each check is an aggregate job: it fails when any of its jobs failed, was skipped where it had to run, or left
  no report. The jobs of `verify-quick` together are `verify.sh full`; `android-ui` together is `verify.sh ui all`,
  judged by `scripts/ui-report.py` per shard (every run of every test, not Gradle's last result), plus the shell on
  a cutout. A PR that touches no app input still gets a green `android-ui`.
- The emulators come from `scripts/ci-emulator.sh`: `system-images;android-35;google_apis;x86_64` with the pool
  AVD's Pixel 7 geometry, cold-booted while Gradle builds, animations as the image ships them (as on the pool).
- `android-ship` uploads `ship-<sha7>`: a summary in the ship check's format with stages 1, 3, 5 and 6, and their
  logs; `scripts/ship-check.sh` reads stages 5 and 6 from it (see Ship check). Every shard of `android-ui` uploads
  `ui-<config>-<i>` (JUnit XML, the HTML report, logcat, the `verify.sh` output) and every Roborazzi shard that
  fails uploads its diffs.
- Caches: the Gradle home (dependencies, keyed by the build files) and one Gradle build cache line per job kind
  (`gbc-v1-<job>-<sha>`, `.github/actions/android-setup` and `build-cache-save`) are written only by pushes to
  `v2.3/next`; PRs read their base's newest line. A run after a build file change starts cold.
- Re-run: `gh run rerun <run-id> --failed`, or one job with `gh run rerun --job <job-id>` (the job ids are in
  `gh run view <run-id> --json jobs`). The aggregate job reruns with it. Locally the same shard is
  `ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui all --shard <i>/6` on a pool emulator (AndroidJUnitRunner
  splits by a hash of the test names, so the shard holds the same tests) or `scripts/verify.sh shots all --shard
  <i>/3`. A manual run of another ref: `gh workflow run android-ui.yml -f ref=<ref>` (possible once the workflow
  is on the default branch).
- The free plan runs at most 20 jobs at once across the organisation: a push to `v2.3/next` starts about 23, so
  some shards queue for a few minutes when several PRs push together.

## Ship check

The ship check is `scripts/ship-check.sh`, also reached through `scripts/verify.sh ship`. The integrator runs it in
`~/proj/.wt/android/next`. Each stage writes PASS or FAIL to `~/proj/.wt/run/ship/<sha7>/summary.md`, with its time,
source and log next to it, and any FAIL makes the exit code non-zero:

1. version lines per [0030](../decisions/0030-release-lines-and-data-continuity.md), and `origin/release/2.2`
   below 100;
2. `scripts/verify.sh full`, whose unit tests include `StableIdentifiersTest` and Konsist for both flavors;
3. unsigned `:app:assembleGithubRelease :app:bundlePlayRelease` with both release manifests, then
   `SKIP_BUILD=1 scripts/check-play-policy.sh`. `--central` builds against MyItmoApi from Maven Central;
4. `scripts/verify.sh ui all` on `emulator-5554`, `UpgradeFrom22Test` included;
5. the real upgrade path on `emulator-5554`. The script installs the `v2.2` `githubDebug` APK, which is built
   once in `~/proj/.wt/android/ship-v22` and cached in `~/proj/.wt/run/apk/`. It launches that APK once, seeds
   the 2.2 data directory from `app/src/androidTest/assets/upgrade-2.2/` and runs `adb install -r` with the
   head `githubDebug`. The stage passes when 30 s after the start there is no `FATAL EXCEPTION`, the process is
   alive and every seeded file is either present or migrated with a format marker.
6. the shrunk app on `emulator-5554`: `githubMinifiedSmoke` and `playMinifiedSmoke` (the release build type's R8
   and resource shrinking, debug-signed, pointed at dev) are each installed fresh and started; the stage passes
   when the sign-in screen shows within 30 s, no `FATAL EXCEPTION` follows and the process is alive. Debug builds
   never run R8, so only this stage sees a crash of the shrunk app, such as two Koin keys merged into one class
   (`app/proguard-rules.pro`, "Class identity").

By default, when `gh` is installed and signed in, only stages 1 and 3 run on the laptop and the rest are read from
GitHub Actions for the head SHA: stage 2 from the `verify-quick` check, stage 4 from `android-ui`, and stages 5 and
6 from `android-ship` together with their rows in the summary of its artifact `ship-<sha7>`. The newest run of the
SHA with a job of that name counts, and the `Source` column of each row links it (`CI run <url>`). A check that is
missing, still running or not `success` fails its stage; `--wait` polls every minute, for up to two hours, until
the checks finish. CI results belong to a commit, so a dirty worktree is refused. Once CI has finished on the
head, the CI-backed check takes as long as stage 3's release build and needs no emulator.

`--local` runs all six stages on the laptop, as before CI carried them; without `gh` the script falls back to it.
`--no-device` runs stages 1-3 locally, as a lane does for its own PR. Local device stages refuse every serial
except `emulator-5554` and any worktree other than the integrator's. The release-signed upgrade belongs to the
owner at T16. The last line has the `--local-verify` format:
`VERIFY A ship|ship-no-device PASS|FAIL <secs>s <sha7>`. `scripts/test-ship-check.sh`, run by
`scripts/verify.sh quick`, covers the CI source against a `gh` stub.

It runs:

- on a batch head (per the integrator card L01-INT) when the batch carries a toolchain, module-graph, shell or
  storage change, and otherwise weekly;
- on every prerelease head and on the release candidate.

A red ship check reverts the culprit like a red batch head.

## Merge queue

The integrator drains the open PRs of A with a local queue loop (untracked integrator tooling) that never drops a
PR and lands one merge at a time under a serial lock. A PR merges only when its current head already contains the
current `origin/v2.3/next` and every check on that head is green, `verify-quick` and `android-ui` included, so CI
has run on exactly the state the squash produces:

1. A PR behind `v2.3/next` is updated on GitHub (`PUT /repos/{owner}/{repo}/pulls/{n}/update-branch` with the
   expected head SHA). That pushes a merge of `v2.3/next` into the lane branch and CI runs again on it; the queue
   waits for the new head's checks.
2. A PR that contains `v2.3/next` and is green goes through `integrate merge android <n>` (with the `surface OK`
   comment of the standing approvals), then the cleanup of merged lane branches and worktrees.
3. Red checks are rerun a bounded number of times per head; a PR still red after that waits for a new head.
4. When GitHub cannot update the branch (a conflict), the queue squashes the PR onto `v2.3/next` locally and runs
   `scripts/verify.sh quick` and `shots all` as before. A green local run merges; a conflict or a red run is
   reported as needing a rebase by the lane.

Every merge moves `v2.3/next`, so the other green PRs are updated and checked again before they merge: the queue
spends CI time instead of the laptop's.

## The 2.2.x line

`release/2.2` starts at tag `v2.2` and carries only 2.2.x fixes and the Play uploads made before 2.3. The
integrator works it from `lane new android L01-integration m22-<slug> --base release/2.2`: one commit, a PR into
`release/2.2`, the owner's OK, then `promote android release/2.2 <head> --local-verify <log>` (the line has no
`scripts/verify.sh`; the Android command of `AGENTS.md` and `scripts/check-play-policy.sh` run under the android
slot). Version codes there continue 7, 8, ... and stay below 100. Every fix is ported to v2.3 by hand through the
card that owns that code, never cherry-picked, and recorded in the local forward-ports ledger.

## Second integrator

After two consecutive days with more than 10 queued PRs, the integrator asks the owner to start a second one. It
takes only the `next` worktrees of B, M and W; A, its promote, the integrator's emulator and the owner's devices
stay with the first integrator.
