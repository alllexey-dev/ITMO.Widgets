# build-logic

The included build with the Gradle convention plugins every module applies.
See [modules](../docs/architecture.md#modules) and
[strings](../docs/architecture.md#strings), and ADRs
[0018](../docs/decisions/0018-module-graph-and-toolchain.md) (toolchain) and
[0028](../docs/decisions/0028-strings-and-icons.md) (strings and icons).

- `convention/`: `itmowidgets.kmp.library` (KMP targets and source sets, SDK
  levels, JVM 17), `itmowidgets.kmp.android` (the same, Android target only),
  `itmowidgets.cmp.kit` (plus Compose Multiplatform and its resources: the
  design system and the Compose strings), `itmowidgets.cmp.android` (Compose on
  the Android target only, no CMP `Res` of its own,
  [ADR 0035](../docs/decisions/0035-ios-without-compose.md)),
  `itmowidgets.testing` (Robolectric and Roborazzi defaults) and
  `itmowidgets.android.app`. Each module's `itmoIosKlib` feeds the root
  `verifyIosKlibs`.
- `strings/`: `itmowidgets.strings`: the catalog check, the `<Area>Strings` /
  `<Area>Plurals` key objects, the Android and Apple string exports
  (`exportAppleStrings`), golden tests under `src/test/resources/`;
  `strings/apple-tables.properties` lists the Apple-only tables.
- Versions come only from the root catalog `gradle/libs.versions.toml`; the
  plugin jars are `implementation` dependencies here, the one copy on the build
  classpath, so no module or root plugin block carries a version.
- `convention` depends on `strings`; neither depends on an app or shared
  module.

Test: `./gradlew -p build-logic test` (also part of `./gradlew verifyQuick`).
