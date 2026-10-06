# shared/ios

## Owns
- The static framework `Shared` (`iosArm64`, `iosSimulatorArm64`) that `iosApp/` links; `:shared:core` is
  exported, so `UiText` and `AppIcon` reach Swift without a module prefix.
- `src/iosMain/kotlin/.../ios/`: the Swift-facing entry points (`IosShell`, `IosStrings`, `IosSecureStore`).
- No resources of its own: the Compose plugins pack every dependency's `composeResources` into the app bundle.

## Depends on
- `:shared:core` (`api`, exported), the kit, `:shared:backend-client` and every feature module (`implementation`),
  CMP runtime and ui.
- Never `:shared:testing`, an Android target or Android code.
- Xcode builds it through the app target's Run Script (`embedAndSignAppleFrameworkForXcode`); compiling the klibs
  needs no Xcode, linking does.

## Verify
`scripts/verify.sh klibs ios` (compile); `scripts/ios/test.sh` for the link and the Swift side, on a Mac with Xcode.

## Hot files
- Every file here: lane L18.
- `build.gradle.kts`: the framework settings and the export list; other lanes hand in a change.

## Docs
- [iOS app](../../docs/ios.md): [layout](../../docs/ios.md#layout) and
  [build and test](../../docs/ios.md#build-and-test).
- ADRs [0023](../../docs/decisions/0023-ios-client.md) (iOS client) and
  [0018](../../docs/decisions/0018-module-graph-and-toolchain.md) (module graph).
- The Swift side: [iosApp/AGENTS.md](../../iosApp/AGENTS.md).
