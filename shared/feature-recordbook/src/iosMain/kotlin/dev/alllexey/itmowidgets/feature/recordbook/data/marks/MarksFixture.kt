package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigest

/**
 * A synthetic digest for the Debug trigger of the background refresh (`-itmoRunRefresh`): four of the demo's
 * subjects, so the text names three and counts the fourth through the catalog as a real digest does. No user data.
 */
object MarksFixture {
    val digest: MarkDigest = MarkDigest(
        subjects = listOf(DemoStudy.DATABASES, DemoStudy.DISCRETE, DemoStudy.MATH, DemoStudy.ALGORITHMS)
            .map { it.name },
        single = null,
    )
}
