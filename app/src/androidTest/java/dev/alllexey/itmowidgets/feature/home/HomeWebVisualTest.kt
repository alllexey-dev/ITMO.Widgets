package dev.alllexey.itmowidgets.feature.home

import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.MaterialToolbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.feature.web.data.WebSessionDataCleaner
import dev.alllexey.itmowidgets.feature.web.domain.MyItmoWebPolicy
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoWebPreviewFragment
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSettingsNavigation
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeWebVisualTest {
    @Test fun webFabAndBrowserStatesWorkAcrossThemesHistoryAndRecreation() {
        try {
            Appearances.default.forEachIndexed { index, spec ->
                SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
                MyItmoWebPreviewFragment.failMainFrame = false
                val gate = CountDownLatch(1)
                MyItmoWebPreviewFragment.gate = gate
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    settle()
                    capture("home-web-$index")
                    scenario.onActivity {
                        val web = it.findViewById<View>(R.id.web_fab)
                        assertTrue(web.height >= 48 * it.resources.displayMetrics.density)
                        assertEquals(it.getString(R.string.home_open_my_itmo), web.contentDescription)
                        web.performClick()
                    }
                    settle()
                    scenario.onActivity {
                        assertEquals(R.id.my_itmo_web, it.navigation.overlayHost!!.navController.currentDestination!!.id)
                        assertTrue(it.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment is MyItmoWebPreviewFragment)
                        assertEquals(View.VISIBLE, root(it).findViewById<View>(R.id.loading).visibility)
                    }
                    capture("web-loading-$index")
                    gate.countDown()
                    MyItmoWebPreviewFragment.gate = null
                    await(scenario) { contentReady(it) }
                    capture("web-content-$index")
                    scenario.onActivity {
                        val web = browser(it)
                        assertTrue(web.settings.javaScriptEnabled)
                        assertFalse(web.settings.allowFileAccess)
                        assertFalse(web.settings.allowContentAccess)
                        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
                        assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(web))
                    }
                    clickNext(scenario)
                    await(scenario) { browser(it).url?.endsWith("/test/second") == true && contentReady(it) }
                    scenario.onActivity { assertTrue("History size: ${browser(it).copyBackForwardList().size}", browser(it).canGoBack()); it.onBackPressedDispatcher.onBackPressed() }
                    await(scenario) { browser(it).url == MyItmoWebPolicy.HOME_URL && contentReady(it) }
                    scenario.recreate()
                    await(scenario) { contentReady(it) }
                    scenario.onActivity { assertEquals(MyItmoWebPolicy.HOME_URL, browser(it).url) }
                    scenario.onActivity { assertTrue(root(it).findViewById<MaterialToolbar>(R.id.toolbar).menu.performIdentifierAction(R.id.web_reload, 0)) }
                    await(scenario) { contentReady(it) }
                    MyItmoWebPreviewFragment.failMainFrame = true
                    scenario.onActivity { browser(it).loadUrl("https://my.itmo.ru/test/failure-$index") }
                    await(scenario) { root(it).findViewById<View>(R.id.state_container).visibility == View.VISIBLE }
                    capture("web-error-$index")
                    MyItmoWebPreviewFragment.failMainFrame = false
                    scenario.onActivity { root(it).findViewById<View>(R.id.state_action).performClick() }
                    await(scenario) { contentReady(it) }
                    clickNext(scenario)
                    await(scenario) { browser(it).url?.endsWith("/test/second") == true && contentReady(it) }
                    onView(withContentDescription(R.string.common_close)).perform(click())
                    settle()
                    scenario.onActivity {
                        assertNull(it.navigation.overlayHost)
                        assertTrue(it.findViewById<View>(R.id.web_fab).isShown)
                        assertTrue(it.findViewById<View>(R.id.home_feed).isShown)
                    }
                }
            }
        } finally {
            MyItmoWebPreviewFragment.gate?.countDown()
            MyItmoWebPreviewFragment.gate = null
            MyItmoWebPreviewFragment.failMainFrame = false
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
        }
    }

    @Test fun signOutCleanerRemovesBrowserCookies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            CookieManager.getInstance().setCookie("https://example.invalid/", "synthetic_session=fixture; Secure; Path=/") { done.countDown() }
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(CookieManager.getInstance().getCookie("https://example.invalid/").contains("synthetic_session"))
        runBlocking { WebSessionDataCleaner(instrumentation.targetContext).clearSessionData() }
        assertNull(CookieManager.getInstance().getCookie("https://example.invalid/"))
    }

    private fun clickNext(scenario: ActivityScenario<SettingsNavigationTestActivity>) {
        val point = FloatArray(2)
        val ready = CountDownLatch(1)
        scenario.onActivity {
            val web = browser(it)
            web.evaluateJavascript("(function(){const r=document.getElementById('next').getClientRects()[0];return [(r.left+r.width/2)/innerWidth,(r.top+r.height/2)/innerWidth];})()") { json ->
                val coordinates = org.json.JSONArray(json)
                val origin = IntArray(2).also(web::getLocationOnScreen)
                point[0] = origin[0] + (coordinates.getDouble(0) * web.width).toFloat()
                point[1] = origin[1] + (coordinates.getDouble(1) * web.width).toFloat()
                ready.countDown()
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val time = SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            android.view.MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, point[0], point[1], 0).let {
                instrumentation.sendPointerSync(it)
                it.recycle()
            }
        }
    }

    private fun root(activity: SettingsNavigationTestActivity) =
        activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!.requireView()
    private fun browser(activity: SettingsNavigationTestActivity) = root(activity).findViewById<WebView>(R.id.web_view)
    private fun contentReady(activity: SettingsNavigationTestActivity) = browser(activity).progress == 100 &&
        root(activity).findViewById<View>(R.id.state_container).visibility == View.GONE &&
        root(activity).findViewById<View>(R.id.loading).visibility == View.INVISIBLE && browser(activity).isShown

    private fun await(scenario: ActivityScenario<SettingsNavigationTestActivity>, predicate: (SettingsNavigationTestActivity) -> Boolean) {
        repeat(100) {
            var done = false
            scenario.onActivity { done = predicate(it) }
            if (done) { settle(); return }
            SystemClock.sleep(100)
        }
        fail("Browser state did not settle: requests=${MyItmoWebPreviewFragment.mainRequests.get()}, errors=${MyItmoWebPreviewFragment.errorResponses.get()}")
    }

    private fun settle() = TestUi.settle(350)
    private fun capture(name: String) = Screenshots.capture("home-web-screenshots", name)
}
