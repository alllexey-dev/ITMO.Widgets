# 0016 Stable identifiers keep their values

**Status.** Historical. This record planned the v2.3 move to Kotlin Multiplatform as
a behaviour-preserving rewrite; that work is done. Only the rule below still holds.

**Rule.** Identifiers that installed apps, widgets and the system hold keep their
values: manifest components, App Link hosts and paths, worker classes and work
names, shortcut ids, intent actions, notification channels, preference, file and
Keystore names, `BuildConfig` ids, and on iOS the bundle IDs, App Group, Keychain
group, URL scheme, route ids and widget and Control kinds. `StableIdentifiersTest`
(Android) and `StableIdentifiersTests` (iOS) hold them as literals. Changing one
needs its own ADR and a dual read of the old value.

**Why.** Installed widgets, alarms, shortcuts and pending work hold class names and
actions, so a renamed identifier breaks users without any visible code change.

**Consequence.** The debug tools stay Android-only; iOS Debug builds take launch
arguments instead.
