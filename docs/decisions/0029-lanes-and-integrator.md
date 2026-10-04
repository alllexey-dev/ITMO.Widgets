# 0029 Lanes push their own branches, one integrator merges, the owner moves master

**Decision (2026-10-03).** For v2.3 the owner gives agents that execute a card of a v2.3 lane a standing authorization (D4).
- A lane agent works in its own worktree, commits and pushes only its card's branch
  `v2.3/<lane-id>/<card-id>-<slug>` (lowercase) and opens a PR into the repository's `v2.3/next` without asking.
- One integrator agent merges lane PRs into `v2.3/next` (A, B, M, W; none in C) and presents batches. The owner's OK
  for a batch fast-forwards `master` (`main` in W); Backend `dev` moves after a separate "deploy dev".
- Deploys, tags, releases, Maven Central, Play, TestFlight and App Store need the owner's word each time.
- The rules themselves (allowed actions, integrator rights, the forbidden list, the owner-word list, the override of
  user-global agent rules) live in one place, `AGENTS.md` § v2.3 lanes; B, M and W carry a short block pointing to
  it. This record does not copy them.

**Guards**, installed by the owner at T9:
- A pre-push hook at the untracked path `~/proj/.wt/git-hooks/pre-push`, enabled by `core.hooksPath` in all five
  repositories: a lane worktree pushes only `refs/heads/v2.3/<lane-id>/*`; the integrator also `v2.3/next`,
  fast-forward only; `master`, `main`, B and W `dev` and `release/*` move only through `~/proj/.wt/bin/promote`
  (fast-forward, provenance, CI, a one-shot intent); tags are rejected from every `~/proj/.wt/**` path. No
  environment-variable bypass.
- Claude permission rules deny merges, releases, tags, publishing, `--no-verify` and writing `core.hooksPath`, and
  ask before every `promote`.
- GitHub rulesets: no force-push or deletion of default branches, B and W `dev`, `v2.3/next` and `release/*`;
  immutable tags; no required checks on `v2.3/next` or `release/*`; required checks on default branches once CI runs.

**Why.** Per-action approval makes the owner the merge queue for up to eight agents (93 Q1 (c)). Lane branches and one
integration branch keep every irreversible step (protected-ref move, deploy, tag, publish) behind one word per batch.
The `v2.3/` prefix is the one pattern the hook accepts; a bare `v2.3` branch would block every `v2.3/*` ref.
Agents push with the owner's credentials, so GitHub cannot tell them from the owner: the local guards enforce, the
rulesets are the backstop. One text in `AGENTS.md` and none here keeps two copies from drifting.

**Consequence.**
- Mistakes on `v2.3/next` are reverted, never force-pushed; a red batch head reverts the culprit.
- Review capacity is two windows a day (about 14:00 and 19:00 MSK), at most eight PR decisions and one Backend dev
  deploy a day (93 Q3 (b), Q6 (a)).
- Process documents link to this record; this record links only to `AGENTS.md`.
- Agents outside a lane card, and every other session, keep the old rules.

**Supersedes.** For lane actions only: the `AGENTS.md` § Git hygiene lines "Do not … commit, push … unless asked
… no generated branch names" and § Definition of done item 10; the user-global "never commit or push on your own"
(`working-rules.md` § GitHub projects) and the per-push word of the v2.2 working mode.

**Revisit when.** A guard is bypassed or an agent moves a protected ref, creates a tag or publishes: agents then move
to a bot account or GitHub App whose rulesets allow only `v2.3/**` (93 Q7 (b)); the integration queue holds more than
ten PRs for days; v2.3 ships (the authorization ends or is renewed for v2.4).

**Settled.**
- The owner approved the `AGENTS.md` § v2.3 lanes text and installed the hook path and the Claude rules on
  2026-10-03 (gate T9). 93 Q1 and Q2 are settled by D4 (option (a) each).
- 93 Q7 agent identity: (a) the owner's credentials plus local guards, by default; (b) a bot account or GitHub App
  if a guard is ever bypassed. 93 Q3–Q6 (review windows, machine posture, colima, dev deploys) take the defaults
  above.

**Evidence.**
- No spike gates this decision. L01's guard tests (G-12a) exercise the hook and `promote` outside every repository;
  they are installed only at T9.
