package dev.alllexey.itmowidgets.designsystem.preview

/**
 * Synthetic preview data: long Russian names that push a layout to its limits at 1.3 and 320 dp. Only `@Preview`
 * functions and `preview/` files read it; it never reaches a runtime string.
 */
object PreviewFixtures {
    /** Fits one line at 1.0 on a phone, not at 1.3 in 320 dp. */
    const val LongPersonName = "Преображенская Александра Вячеславовна"

    const val LongSubjectName = "Математический анализ и дифференциальные уравнения в частных производных"

    const val ShortPersonName = "Иванов Иван"
}
