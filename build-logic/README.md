# build-logic

The included build with the Gradle convention plugins every module applies.
See [modules](../docs/architecture.md#modules) and
[strings](../docs/architecture.md#strings), and ADRs
[0018](../docs/decisions/0018-module-graph-and-toolchain.md) (toolchain) and
[0028](../docs/decisions/0028-strings-and-icons.md) (strings and icons).

- `convention/`: `itmowidgets.android.app`, `itmowidgets.kmp.library`,
  `itmowidgets.cmp.ui` and `itmowidgets.testing` (SDK levels, JVM 17, targets
  and source sets, Robolectric and Roborazzi defaults).
- `strings/`: `itmowidgets.strings`: the catalog check, the Android string
  export and ids, the Apple string and symbol export (`exportAppleStrings`),
  with golden tests under `src/test/resources/`.
  `strings/apple-tables.properties` lists the Apple-only string tables.
- Versions come only from the root catalog `gradle/libs.versions.toml`; the
  plugin jars are `implementation` dependencies here, the one copy on the build
  classpath, so no module or root plugin block carries a version.
- `convention` depends on `strings`; neither depends on an app or shared
  module.

Test: `./gradlew -p build-logic test` (also part of `./gradlew verifyQuick`).
