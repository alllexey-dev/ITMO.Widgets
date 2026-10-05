# Recipes

Durable how-tos for repeated Android and shared-module work: the steps that
worked, the traps and the files to copy. One line per recipe.

- [Background check](background-check.md): a periodic or one-off WorkManager
  check: worker, unique work names, scheduler, switch, cleaner, backup
  exclusions, channel, debug trigger, gates, DI and the iOS mapping.
- [Screen in a shared module](shared-module-screen.md): a stateless CMP
  screen in `shared/feature-<x>`, its route, ViewModel and Koin module,
  strings, icons, previews, goldens, host tests and the Android and iOS hosts.
