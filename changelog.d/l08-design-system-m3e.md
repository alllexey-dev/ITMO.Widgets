# Material 3 Expressive

- The Compose screens take the owner's Material 3 Expressive values in one
  token change: below Android 12 and on iOS the app is painted in the brand
  blue `#4984E2` (TonalSpot) instead of the M3 baseline purple, every tab of
  the bottom navigation shows its label, `cardSummary` is 28 dp, and the
  expressive components (loading indicator, pull-to-refresh, connected button
  group, wavy progress for heroes, hero avatar mask) are on. Widgets, the QR
  tile, shortcut and notification icons keep their palette.
- The expressive loading indicator turns about its own centre: it morphs
  through Material's shapes with Cookie9Sided and Pentagon swapped for
  Cookie12Sided and Clover4Leaf (`ItmoLoadingShapes`), so the shape no longer
  wobbles by about 3 px. In pull-to-refresh the pulled shape keeps its size
  and angle when it hands over to the turning one, without the double image.
- The expressive loading indicator no longer twitches every 650 ms: it is
  the app's own `ItmoMorphingIndicator` (Material's size and colours), whose
  morph and turn ride one eased animation per step (the web indicator's
  curve, 112.5 degrees per shape) instead of a spring that stops short and
  snaps; the shape turns about its centroid and opens at rest, so the
  pull-to-refresh hand-off stays still.
- The days of the «Запись» week strip no longer flash grey when the
  selection moves to or from a day without lessons: the day card fades the
  alpha of its own container colour instead of blending it with transparent
  black.
