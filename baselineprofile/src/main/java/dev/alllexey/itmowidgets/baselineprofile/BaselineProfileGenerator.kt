package dev.alllexey.itmowidgets.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `app/src/main/generated/baselineProfiles` with `./gradlew :app:generateBaselineProfile` on an emulator
 * (docs/testing.md "Baseline profiles"). The startup profile covers the start into the demo session only; the
 * baseline profile also covers the tabs, a push and pop and the pass sheet.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(PACKAGE, includeInStartupProfile = true) {
        startInDemo()
    }

    @Test
    fun navigation() = rule.collect(PACKAGE) {
        startInDemo()
        visitTabs()
        openAndClosePass()
        selectTab("ME")
        pushAndPopFriends()
        selectTab("HOME")
    }
}
