package dev.alllexey.itmowidgets.core.testing

import java.lang.reflect.Proxy

// JVM-only demo-gate helpers; FakeDemoMode and noDemo() live in shared/core/src/testFixtures.

/** A client interface every call of which fails the test: the demo session must not reach it. */
inline fun <reified T : Any> unreachable(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
    if (method.declaringClass == Any::class.java) return@newProxyInstance method.name.hashCode()
    throw AssertionError("The demo session called ${T::class.java.simpleName}.${method.name}")
} as T
