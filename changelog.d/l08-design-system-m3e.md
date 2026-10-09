# Design system and Material 3 Expressive

- `:shared:designsystem` is the one source of the look: `ItmoTheme` (colour
  scheme with wallpaper, brand or seeded source, extended colours, shapes,
  spacing, type with emphasized twins, motion schemes) and a kit of stateless
  components (top bar, navigation bar, content states, skeleton,
  pull-to-refresh, buttons, pills, avatars, connected groups, settings, user
  and link rows, details header, sheets, dialogs, menu, score ring, grade
  scale, timeline, steps indicator) that every Compose screen builds from.
  `shared/designsystem/tokens/itmo-tokens.json` exports the tokens for the
  iOS client and the web app.
- On iOS the kit draws an iOS style (`ItmoPlatformStyle.Ios`): Apple's text
  styles, UIKit spacing and radii, inset-grouped rows, a switch, segmented
  control, activity indicator, alerts and menus like UIKit's, and haptics;
  Android's look is unchanged by it.
- Screens are checked by JVM screenshot baselines (Roborazzi) in four
  appearances, the kit and the QR pass also in three iOS ones:
  `scripts/verify.sh shots <module>|app|all`, run by CI on every PR.
- The app takes the owner's Material 3 Expressive values in one token change:
  below Android 12 and on iOS it is painted in the brand blue `#4984E2`
  (TonalSpot) instead of the M3 baseline purple, every tab of the bottom
  navigation shows its label, and the PE sport card is 28 dp round. Android
  12+ keeps the wallpaper's colours.
- Each feature then took one expressive pass: a few hero moments on the hero
  motion scheme (the QR pass reveal, the lesson in progress with a wavy bar on
  home, the sport status chip, the subject result card's emphasized points and
  scale fill, the profile hero's cookie-shaped avatar), the expressive loading
  indicator for short waits and in pull-to-refresh, the friend picker's scope
  as a connected button group, and springs instead of fixed tweens. Under
  reduced motion everything shows its end state at once.
- The expressive loading indicator is the app's own `ItmoMorphingIndicator`:
  it morphs through Material's shapes without Cookie9Sided and Pentagon
  (`ItmoLoadingShapes`), turns about the shape's centre on one eased step per
  shape, and hands over from the pulled shape in pull-to-refresh without a
  jump in size or angle.
- The days of the «Запись» week strip no longer flash grey when the
  selection moves to or from a day without lessons.
- Widgets, the QR tile, shortcut and notification icons and the ITMO.ID and
  BARS pages keep their v2.2 look.
