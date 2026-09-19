package dev.alllexey.itmowidgets.core.coroutines

import javax.inject.Qualifier

/** A scope that outlives screens, for work that must finish after the caller is gone. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
