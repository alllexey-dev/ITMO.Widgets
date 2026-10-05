# iosApp

## Owns
- `project.yml`: the XcodeGen spec; the `.xcodeproj` is generated and ignored, never committed.
- `Config/`: identifiers, versions and signing switches as xcconfig build settings.
- `Resources/`: `Info/` plists and per-target entitlements, unsigned and `.signed`.
- `Sources/`: the SwiftUI app shell `ITMOWidgets`, the only target that links the Kotlin framework `Shared`.
- `Extensions/Widgets/`, `Extensions/NotificationService/`: Swift-only extensions (no Kotlin).
- `Tests/UnitTests/`: `ITMOWidgetsTests`, hosted in the app.

## Depends on
- `shared/ios` (`Shared`, static), built by the app's Run Script through `scripts/slot.sh kn`.
- System frameworks only; Swift packages are pinned in `project.yml`. No Kotlin in an extension.
- No team ID, App Group or Keychain group literal in Swift: read `AppGroupID` and `KeychainGroup` from the own
  process's `Bundle.main`.

## Verify
`scripts/ios/test.sh` (or `--only <Target>/<Class>`); `scripts/ios/test.sh --cleanup` at the end of a card.

## Hot files
- Every file here: lane L18 (single writer).
- `Tests/UnitTests/StableIdentifiersTests.swift`: one appended assertion per card that adds an identifier.
- `Config/Signing.local.xcconfig`: ignored, written only by the integrator after T13.

## Docs
- [docs/ios.md](../docs/ios.md): prerequisites, layout, identifiers, build and test.
- [ADR 0023](../docs/decisions/0023-ios-client.md): the hybrid iOS client.
