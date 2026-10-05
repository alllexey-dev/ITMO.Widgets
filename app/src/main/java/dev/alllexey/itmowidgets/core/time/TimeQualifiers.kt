package dev.alllexey.itmowidgets.core.time

import javax.inject.Qualifier

// App-only: javax.inject stays out of the time contracts, which compile in commonMain.

/** The `java.time.Clock` in the academic zone, for consumers not yet on [AcademicTimeProvider]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AcademicClock

/** The UTC `java.time.Clock`, for consumers not yet on the injected `kotlin.time.Clock`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WallClock
