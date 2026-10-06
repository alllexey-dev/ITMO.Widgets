package dev.alllexey.itmowidgets.testkit

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle

/** [assertTouchTargets] at [style]'s minimum: 48 dp under Material, 44 pt under iOS. */
fun SemanticsNodeInteractionsProvider.assertTouchTargets(style: ItmoPlatformStyle) =
    assertTouchTargets(style.minTouchTarget)
