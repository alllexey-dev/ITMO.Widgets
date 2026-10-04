# Parallel agents

Up to eight agents work at once: seven workers and one integrator. What every
agent may and may not do is in [`AGENTS.md`](../../AGENTS.md) (§ v2.3 lanes and
§ Secrets and data) and [ADR 0029](../decisions/0029-lanes-and-integrator.md); this page covers where they work and how they share the
machine.

## Worktrees and branches

| What | Rule |
|---|---|
| Lane worktree | `~/proj/.wt/<repo>/<lane-id-lowercase>-<slug>` (`android`, `backend`, `myitmoapi`, `web`), made by the integrator's `~/proj/.wt/bin/lane new` (outside the repository) from `origin/v2.3/next`; never a main checkout, `/tmp`, `/private/tmp` or a tool's own worktree directory |
| Lane branch | `v2.3/<lane-id-lowercase>/<card-id-lowercase>-<slug>`, lowercase only (APFS is case-insensitive) |
| Integration branch | `v2.3/next` in each repository; only the integrator worktree `~/proj/.wt/<repo>/next` pushes it |
| MyItmoApi pin | Once the composite build lands: one detached checkout per Android worktree, `~/proj/.wt/myitmoapi/pin-<worktree>-<sha7>`, at the commit of the pin file the integrator approved; `lane pin` re-pins after a rebase moved it |
| Spikes | `~/proj/.wt/spikes/<id>`: scratch or a detached worktree, no branch, no push, deleted at exit |
| 2.2.x fixes | Branch from `release/2.2`, PR into `release/2.2`; Play uploads only from it |
| Plans | Lane files, the board and recipes stay in the main checkout's ignored `vibe/`; worktrees read them by absolute path |

`lane done` removes the worktree, its pin and the branch after the merge.

## Slots

Heavy work runs inside `scripts/slot.sh <kind> -- <command>`, which takes lock
files `~/.cache/itmo-agents/slots/<kind>.<n>.lock` with `/usr/bin/lockf`
(`flock` is not installed on macOS). Counts and weights are in
`scripts/slots.conf`; `scripts/slot.sh status` shows the held slots.
`scripts/verify.sh` takes its slots itself.

| Kind | Count | Weight |
|---|---|---|
| `android` | 3 (2 with a second emulator up or IDEs open) | 1 |
| `kn` (iOS klibs, Xcode) | 1 | 2 |
| `backend` (Testcontainers) | 1 (2 after the colima resize) | 1 |
| `jvm` (MyItmoApi, Web) | 2 | 0.5 |
| `emulator` | 2 | — |

At most 4 heavy units run at once. `emulator-5554` is the integrator's; agents
start a pool AVD (`itmo-pool-api35`, `itmo-pool-api30`, ports from 5560) through
`scripts/emulator.sh up`. Every `connected*` or `install*` task needs
`ANDROID_SERIAL=emulator-*`; the owner's phone and iPhone are the integrator's
only.

## Admission

No new agent starts when any of these holds:

- 8 agents run, or 5 work in the Android repository;
- 3 Android builds are in flight (2 with a second emulator or IDEs open), or a
  `kn` build runs beside 2 Android builds;
- 2 agents work in Backend, MyItmoApi and Web together (3 while the Android
  repository has at most 3);
- more than 10 PRs wait for the integrator;
- the lane's maximum agents are busy;
- a card's `needs` are not all merged or open, or its `touches` overlap a
  running card or a frozen hot file ([ownership](ownership.md)).

## Pull rule

An idle slot takes, in order:

1. Critical-path cards.
2. Cross-repo, iOS and recipe unblockers.
3. Cards that remove hot-file conflicts.
4. Feature ports: sport, schedule, account, social, recordbook, settings,
   resources, home.
5. Fillers: Web, docs and catalog sweeps, cleanup.
