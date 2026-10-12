# 0024 MyItmoApi 2.x from Maven Central, a local checkout as an opt-in composite build

**Decision (2026-10-03, revised 2026-10-12).** The app resolves `dev.alllexey:my-itmo-api-kmp` from Maven Central at
the version in `gradle/libs.versions.toml` (`myItmoApiKmp`), and never through Maven Local.
- To change MyItmoApi together with the app, set `MYITMOAPI_DIR` to a my-itmo-api checkout. `settings.gradle.kts`
  then calls `includeBuild("<dir>/kmp")` (or `includeBuild("<dir>")` when the KMP build is the root) and substitutes
  the artifact with the included project; the catalog version is ignored. A directory without a Gradle build fails
  the configuration with a clear message.
- `mavenLocal()` is not in `settings.gradle.kts`. No repository uses `publishToMavenLocal`, `mvn install` or `~/.m2`.
- Core is not part of this: Core 2.0 is a module of this repository (ADR 0026).

**Why.** Maven Local is one mutable directory shared by every checkout: a forgotten publish builds stale code, and a
matching version string proves nothing. The Central artifact is reproducible on any machine
and in CI with no extra checkout. A composite build of a local checkout needs no publish step and shows MyItmoApi
sources in the IDE, so it stays available for library development.

**Consequence.**
- A MyItmoApi change reaches the app as a release on Maven Central and a version bump in the catalog. Work in
  progress is tried with `MYITMOAPI_DIR`; no commit points at a local path.
- The included build must build standalone on the app's Gradle with `group = "dev.alllexey"` and
  `rootProject.name = "my-itmo-api-kmp"`, with no signing or credentials unless it publishes; otherwise the
  substitution silently does not happen.
- `docs/product/releases.md` records the MyItmoApi version each Android release builds against.

**Supersedes.** Snapshot versions of Core and Backend published through Maven Local.

**Revisit when.** MyItmoApi moves into this repository, or a consumer needs unreleased 2.x code in CI.
