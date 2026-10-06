# shared/ios

## Owns
- The static framework `Shared` (`iosArm64`, `iosSimulatorArm64`) that `iosApp/` links; `:shared:core` is
  exported, so `UiText` and `AppIcon` reach Swift without a module prefix.
- SKIE (sealed classes as Swift enums, suspend as `async`, Flow as `AsyncSequence`) and the export list.
- `src/iosMain/kotlin/.../ios/`: the Swift-facing entry points: `IosPlatform`, `di/` (`startKoinIos`, `IosKoin`,
  `IosKoinModules`), `bridge/` (`ScreenViewModelStore`; the test probe `BridgeProbeViewModel` in
  `bridge/presentation/`, where Konsist wants every ViewModel), `screens/` (the
  Compose hosts: `ScreenControllers.kt` and one `<Feature>Screens.kt` per feature), `navigation/` (`IosRoutes`: the
  feature of each shared route key and the entry routes Swift builds), `IosStrings`, `IosSecureStore`.
- No resources of its own: the Compose plugins pack every dependency's `composeResources` into the app bundle.

## Depends on
- `:shared:core` (`api`, exported), the kit, `:shared:backend-client` and every feature module (`implementation`),
  CMP runtime and ui, the KMP lifecycle ViewModel; the SKIE Gradle plugin from the catalog.
- Never `:shared:testing`, an Android target or Android code.
- Xcode builds it through the app target's Run Script (`embedAndSignAppleFrameworkForXcode`); compiling the klibs
  needs no Xcode, linking does.

## Verify
`scripts/verify.sh klibs ios` (compile); `scripts/ios/test.sh` for the link and the Swift side, on a Mac with Xcode.

## Hot files
- Every file here: lane L18.
- `build.gradle.kts`: the framework settings and the export list; other lanes hand in a change.
- `di/IosKoinModules.kt`: one appended line per feature; `screens/<Feature>Screens.kt`: one file per IO card.

## Docs
- [iOS app](../../docs/ios.md): [layout](../../docs/ios.md#layout),
  [Swift bridge](../../docs/ios.md#swift-bridge) and [build and test](../../docs/ios.md#build-and-test).
- ADRs [0023](../../docs/decisions/0023-ios-client.md) (iOS client) and
  [0018](../../docs/decisions/0018-module-graph-and-toolchain.md) (module graph).
- The Swift side: [iosApp/AGENTS.md](../../iosApp/AGENTS.md).
