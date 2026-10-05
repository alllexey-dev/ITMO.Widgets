package dev.alllexey.itmowidgets.testkit

import kotlin.reflect.KClass

/**
 * Lets a commonTest class choose its JUnit runner on the Android host: `runComposeUiTest` needs Robolectric there,
 * so such a class carries `@RunWith(RobolectricTestRunner::class)`. On iOS the annotation does not exist.
 */
expect abstract class Runner

/** `org.junit.runner.RunWith` on Android; absent on iOS. */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class RunWith(val value: KClass<out Runner>)

/** `org.robolectric.RobolectricTestRunner` on Android; a placeholder on iOS. */
expect class RobolectricTestRunner : Runner
