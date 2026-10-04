# 0030 Release lines, version codes and data continuity

**Decision (2026-10-03).** (A12)
- **Lines.** At T5 `release/2.2` is cut at tag `v2.2` and `master` becomes 2.3 development (`2.3-SNAPSHOT`). 2.2.x
  fixes and every Play upload before 2.3 come only from `release/2.2`, through a PR into it, and are ported forward
  by hand; v2.3 lanes never touch it.
- **versionCode** = `major*10000 + minor*100 + patch` from 2.3 on: 2.3.0 = 20300, 2.3.1 = 20301, 2.4.0 = 20400.
  Development `2.3-SNAPSHOT` = 20290; prerelease `2.3.0-beta.N` = 20290 + N with 1 ≤ N ≤ 9 (beta.1 = 20291,
  beta.9 = 20299 < 20300). 2.2 keeps 6; 2.2.x continues that counter (7, 8, …) and stays below 100, so every 2.3
  build installs over every 2.2.x.
- **Prereleases** only as a GitHub `--prerelease` or a Play internal upload, each on the owner's word.
- **Client floor.** `app.minimum` stays 2.1 until T7 (2.2 in Play production), may rise to 2.2 after it and never
  goes above 2.2 in v2.3 (92 Q2 (b)); Backend stays additive while it is ≤ 2.2.
- **Backend release** (92 Q4 (b)): one production release, 1.8.0, at the end of v2.3 and before the RC (gate R),
  carrying phases A–E and MyItmoApi 2.0.0; dev gets each phase on the owner's word, Spring Boot 4 alone as its own dev
  batch with a soak; V11 and V12 expand-only, so the rollback to 1.7.0 stays image-only.
- **Data continuity.** Report 13's stable identifiers keep their values (ADR 0016). A changed on-disk format is read
  dual: the new reader accepts the old format and writes a version marker. A 2.2 data-directory upgrade fixture
  (G-04) runs in SS-01; a failed upgrade blocks the merge. While MyItmoApi 1.x and 2.x coexist exactly one class
  writes `myitmo_tokens.enc` and `bars_tokens.enc`. SP-08 showed the 2.2 DataStore and file stores read and write
  back byte-compatibly on the v2.3 pins.

**Why.** A maintenance branch lets Play-forced 2.2.x work ship without v2.3 code while `master` stays the one
development line (13 Q1 (a)); a long-lived `v2.3` branch would leave `master` stale for months. The scheme is
monotonic and readable (20300 reads 2.3.0), leaves 99 patches per minor and keeps the old counter far below it
(13 Q2 (b)). N ≤ 9 twice over: 20290 + 10 is the release's own 20300, and `AppVersionName` compares suffixes as
strings, so `beta.10` sorts before `beta.9` and the update offer would go backwards. A non-prerelease GitHub release
becomes `releases/latest` for every `github` user, and any Play upload burns its versionCode. Testers cannot
downgrade, so a broken upgrade is unrecoverable on the device: hence dual reads, markers and the fixture.

**Consequence.**
- G-01 sets `versionName = "2.3-SNAPSHOT"` and `versionCode = 20290` together; `AppVersionNameTest` pins
  `2.2.9 < 2.3-SNAPSHOT < 2.3.0-beta.1 < 2.3.0-beta.9 < 2.3 < 2.3.1`.
- `docs/product/releases.md` § Distribution states the lines and the scheme; the forward-port ledger is the
  integrator's (local).
- iOS uses marketing version 2.3.0 and CI build numbers (ADR 0023), independent of versionCode.
- A PR that changes a stored format ships the reader, the marker and the fixture case together.
- Once `app.minimum` is 2.2, Backend's `compatCore120Test` can go.

**Supersedes.** `docs/product/releases.md` § Distribution, versionCode as a running counter ("a release takes the
next free number").

**Revisit when.** A tenth beta is needed (use `-rc` or compare suffixes numerically first); a minor nears patch 99;
2.2.x nears code 100; T7 passes (raise `app.minimum`); a stored format cannot be read dual (a one-time migration then).

**Settled.** Owner, 2026-10-03, unless named otherwise.
- 13 Q1: (a) `master` = 2.3 from T5 plus `release/2.2` (A12; default applied at T4).
- 13 Q2: (b) this versionCode scheme. It becomes irreversible with the first upload of a 2.3 code.
- 92 Q2: (b) plan for a 2.2 floor after T7; the floor stays 2.1 until the owner raises it.
- 92 Q4: (b) one Backend 1.8.0 at the end of v2.3 instead of three releases. If Boot 4 fails the dev soak, 1.8.0
  ships on Boot 3.5.x without it.

**Evidence.**
- SP-08: PASS (2.2 DataStore and file stores read on v2.3 pins and write back byte-compatibly; dual read only where KM-05c bumps the links store to format 3, which 2.2 cannot read)
