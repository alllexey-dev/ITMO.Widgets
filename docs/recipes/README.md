# Recipes

Durable how-tos for repeated Android and shared-module work: the steps that
worked, the traps and the files to copy. One line per recipe.

- [Background check](background-check.md): a periodic or one-off WorkManager
  check: worker, unique work names, scheduler, switch, cleaner, backup
  exclusions, channel, debug trigger, gates, DI and the iOS mapping.
- [Screen in a shared module](shared-module-screen.md): a stateless CMP
  screen in `shared/feature-<x>`, its route, ViewModel and Koin module,
  strings, icons, previews, goldens, host tests and the Android and iOS hosts.
- [iOS host](ios-host.md): a shared feature on iOS: its CMP route in the
  SwiftUI `NavigationStack`, SwiftUI screens over a shared ViewModel, the Koin
  start, the App Group snapshot of a widget, the Control, App Shortcuts and
  quick actions, and strings from the `.xcstrings` tables.
- [Endpoint end to end](endpoint-end-to-end.md): a Backend route from the
  privacy boundary through fixtures, Core 2.0 and the gated repository to the
  screen, and a MyITMO endpoint from MyItmoApi through the pin to the data
  source.
- [Feature module generator](../../scripts/new-feature-module.sh): renders
  the QR pilot's module shape from `templates/feature-module/` into
  `shared/feature-<x>`, adding only missing files; its self-test
  `scripts/test-new-feature-module.sh` keeps it in step with the pilot.
