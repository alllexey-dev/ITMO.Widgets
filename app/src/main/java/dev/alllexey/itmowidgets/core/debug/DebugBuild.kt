package dev.alllexey.itmowidgets.core.debug

/**
 * Whether this is a debug build. Code that cannot read `BuildConfig.DEBUG` (shared modules) takes this instead;
 * `di/DebugModule.kt` binds it from `BuildConfig.DEBUG`.
 */
class DebugBuild(val isDebug: Boolean)
