package dev.alllexey.itmowidgets.app.shell

import dev.alllexey.itmowidgets.app.AppShortcutsTest
import dev.alllexey.itmowidgets.app.DemoModeFlowTest
import dev.alllexey.itmowidgets.app.MainActivityDeepLinkTest
import dev.alllexey.itmowidgets.app.MainActivitySessionRoutingTest
import dev.alllexey.itmowidgets.core.notification.FcmNotificationFlowTest
import dev.alllexey.itmowidgets.feature.qr.QrTileFlowTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * The instrumented tests of `MainActivity`'s navigation: entry intents and App Links, session and first-run routing,
 * the demo session, the launcher shortcuts, the QR tile, notifications, the window insets, Back and the predictive
 * Back through pushed screens, sheets and tabs, and the system pages the entries open. Every card that changes the shell runs it on a pool
 * emulator (`scripts/verify.sh ui dev.alllexey.itmowidgets.app.shell.ShellSuite`). A member whose navigation assertions
 * read `ShellProbe` names the shell in a failure through `ShellModeRule`.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    MainActivityDeepLinkTest::class,
    MainActivitySessionRoutingTest::class,
    DemoModeFlowTest::class,
    AppShortcutsTest::class,
    QrTileFlowTest::class,
    FcmNotificationFlowTest::class,
    ShellInsetsTest::class,
    ShellNavigationTest::class,
    ShellPlatformActionsTest::class,
)
class ShellSuite
