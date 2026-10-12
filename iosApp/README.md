# iosApp

The iOS client: a SwiftUI app over the Kotlin framework `Shared` (`shared/ios`), a Swift-only widget extension and a
Swift-only notification service extension. Everything about it (layout, identifiers, data sharing, build, tests,
signing) is in [docs/ios.md](../docs/ios.md); new screens follow
[Adding a SwiftUI screen](../docs/recipes/ios-host.md).

```bash
scripts/ios/test.sh            # generate the project, build, unit and snapshot tests
scripts/ios/test.sh ui         # UITests
```

The `.xcodeproj` is generated from `project.yml` and never committed; `Config/Signing.local.xcconfig` is local and
ignored.
