package dev.alllexey.itmowidgets.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * The demo-session journeys the profile generators and the benchmarks share. They find the shell by its test tags,
 * which `ShellHost` exposes as resource ids (`ShellTags`, `TabRootTags`, `AuthTestTags`, `MeTestTags`).
 */
internal const val PACKAGE = "dev.alllexey.itmowidgets"

/** The bar's tabs in the order a visit takes them, ending on the start tab. */
private val TABS = listOf("SCHEDULE", "RECORDBOOK", "SPORT", "ME", "HOME")

// Generous: CI emulators on shared runners stall for seconds.
private const val TIMEOUT_MS = 20_000L
private const val LOGO = "auth_logo"
private const val PASS_FAB = "shell:pass_fab"
private const val FRIENDS_ROW = "friends_row"
private val OVERLAY: BySelector = By.res(Pattern.compile("shell:overlay:.*"))

private fun tab(name: String): BySelector = By.res("shell:tab:$name")

/** Starts the app; on the sign-in screen enters the demo by five quick taps on the logo. Ends on Сегодня. */
internal fun MacrobenchmarkScope.startInDemo() {
    pressHome()
    startActivityAndWait()
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (!device.hasObject(tab("HOME")) && !device.hasObject(By.res(LOGO))) {
        check(System.currentTimeMillis() < deadline) { "Neither the sign-in screen nor the tabs showed" }
        Thread.sleep(100)
    }
    device.findObject(By.res(LOGO))?.let { logo -> repeat(5) { logo.click() } }
    checkNotNull(device.wait(Until.findObject(tab("HOME")), TIMEOUT_MS)) { "The demo session did not open" }
    selectTab("HOME")
}

/** Taps [name] in the bar and waits until it is selected and the screen is idle. */
internal fun MacrobenchmarkScope.selectTab(name: String) {
    checkNotNull(device.wait(Until.findObject(tab(name)), TIMEOUT_MS)) { "No tab $name in the bar" }.click()
    checkNotNull(device.wait(Until.findObject(tab(name).selected(true)), TIMEOUT_MS)) { "Tab $name was not selected" }
    device.waitForIdle()
}

/** Visits every tab by a tap, ending on Сегодня. */
internal fun MacrobenchmarkScope.visitTabs() = TABS.forEach { selectTab(it) }

/** On Профиль: pushes Друзья over the bar and pops it with Back. */
internal fun MacrobenchmarkScope.pushAndPopFriends() {
    checkNotNull(device.wait(Until.findObject(By.res(FRIENDS_ROW)), TIMEOUT_MS)) { "No Друзья row" }.click()
    check(device.wait(Until.hasObject(OVERLAY), TIMEOUT_MS)) { "Друзья was not pushed" }
    device.waitForIdle()
    device.pressBack()
    check(device.wait(Until.gone(OVERLAY), TIMEOUT_MS)) { "Друзья was not popped" }
    device.waitForIdle()
}

/** On Сегодня: opens the pass sheet with the Пропуск button and closes it with Back. */
internal fun MacrobenchmarkScope.openAndClosePass() {
    checkNotNull(device.wait(Until.findObject(By.res(PASS_FAB)), TIMEOUT_MS)) { "No Пропуск button" }.click()
    // The sheet is a window of its own: the tab layer's button leaves the active window while it shows.
    device.wait(Until.gone(By.res(PASS_FAB)), TIMEOUT_MS)
    device.waitForIdle()
    device.pressBack()
    checkNotNull(device.wait(Until.findObject(By.res(PASS_FAB)), TIMEOUT_MS)) { "The pass sheet did not close" }
    device.waitForIdle()
}
