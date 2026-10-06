# build-logic

## Owns
- `convention/`: the plugins `itmowidgets.android.app`, `itmowidgets.kmp.library`, `itmowidgets.cmp.ui` and
  `itmowidgets.testing` (SDK levels, JVM 17, targets and source sets, Robolectric and Roborazzi defaults).
- `strings/`: the `itmowidgets.strings` plugin: catalog check, Android string export and ids, Apple string and
  symbol export (`exportAppleStrings`), with golden tests under `src/test/resources/`.
- `strings/apple-tables.properties`: the Apple-only string tables (Info.plist, app shortcuts).

## Depends on
- The root catalog `gradle/libs.versions.toml` only; its plugin jars are `implementation` dependencies here, the
  one copy on the build classpath, so no module or root plugin block carries a version.
- `convention` depends on `strings`; neither depends on an app or shared module.

## Verify
`scripts/verify.sh quick` (runs `:convention:test` and `:strings:test` of this build, then every module that
applies the conventions).

## Hot files
- `convention/`, this build's `settings.gradle.kts`: lane L04, one open PR at a time; other lanes hand in.
- `strings/`: lane L05.

## Docs
- [Modules](../docs/architecture.md#modules) and [strings](../docs/architecture.md#strings) in the architecture doc.
- ADRs [0018](../docs/decisions/0018-module-graph-and-toolchain.md) (toolchain),
  [0024](../docs/decisions/0024-pinned-composite-build.md) (pinned composite build) and
  [0028](../docs/decisions/0028-strings-and-icons.md) (strings and icons).
