# 0030 Release lines, version codes and data continuity

**Decision (2026-10-03).**
- **Lines.** `master` is the development line. Each released minor has a
  maintenance branch `release/<major>.<minor>` cut at its tag (`release/2.2` at
  `v2.2`). Fixes and Play uploads for that minor come only from its branch,
  through a PR into it, and are ported forward by hand.
- **versionCode** = `major*10000 + minor*100 + patch` from 2.3 on: 2.3.0 = 20300,
  2.3.1 = 20301, 2.4.0 = 20400. A prerelease `2.3.0-beta.N` is 20290 + N with
  1 <= N <= 9 (beta.1 = 20291, beta.9 = 20299 < 20300). `master` builds
  `<next>-SNAPSHOT` at the code of the last beta. 2.2.x continues the old counter
  (6, 7, ...) and stays below 100, so every 2.3 build installs over every 2.2.x.
- **Prereleases** only as a GitHub `--prerelease` or a Play internal upload, each on
  an explicit decision.
- **Client floor.** `app.minimum` rises only after the release it names is in Play
  production; Backend stays additive while installed clients down to
  `app.minimum` decode its responses.
- **Backend releases** keep schema migrations expand-only, so a rollback to the
  previous Backend release stays image-only.
- **Data continuity.** Stable identifiers keep their values (ADR 0016). A changed
  on-disk format is read dual: the new reader accepts the old format and writes a
  version marker. The upgrade from the previous release's data directory runs in
  `scripts/ship-check.sh`; a failed upgrade blocks the release. Exactly one class
  writes `myitmo_tokens.enc` and `bars_tokens.enc`.

**Why.** A maintenance branch lets Play-forced fixes ship without development code
while `master` stays the one development line. The scheme is monotonic and
readable (20300 reads 2.3.0), leaves 99 patches per minor and keeps the old
counter far below it. N <= 9 twice over: 20290 + 10 is the release's own 20300, and
`AppVersionName` compares suffixes as strings, so `beta.10` sorts before `beta.9`
and the update offer would go backwards. A non-prerelease GitHub release becomes
`releases/latest` for every `github` user, and any Play upload burns its
versionCode. Testers cannot downgrade, so a broken upgrade is unrecoverable on the
device: hence dual reads, markers and the upgrade check.

**Consequence.**
- `versionName` and `versionCode` are set together; `AppVersionNameTest` pins
  `2.2.9 < 2.3-SNAPSHOT < 2.3.0-beta.1 < 2.3.0-beta.9 < 2.3 < 2.3.1`.
- `docs/product/releases.md` states the current lines and codes.
- iOS uses its own marketing version and CI build numbers (ADR 0023), independent
  of versionCode.
- A PR that changes a stored format ships the reader, the marker and the upgrade
  case together.

**Supersedes.** versionCode as a running counter ("a release takes the next free
number").

**Revisit when.** A tenth beta is needed (use `-rc` or compare suffixes numerically
first); a minor nears patch 99; 2.2.x nears code 100; a stored format cannot be
read dual (a one-time migration then).
