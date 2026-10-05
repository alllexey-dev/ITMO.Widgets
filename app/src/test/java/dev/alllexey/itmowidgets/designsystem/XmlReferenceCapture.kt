package dev.alllexey.itmowidgets.designsystem

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.loader.ResourcesLoader
import android.os.Bundle
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import dev.alllexey.itmowidgets.testkit.screenshot.CaptureSize
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsRun
import java.time.Duration
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import com.google.android.material.R as MaterialR
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance as HostAppearance

/**
 * Captures a View screen of today on the JVM in the appearances of [PreviewAppearance], under the name of the
 * Compose preview that will replace it, so the port's first record overwrites the reference and its diff is the
 * parity diff. With a [module] the baselines go to `shared/<module>/screenshots/` and the name is listed in its
 * `references.txt` (record adds it, verify requires it); without one, to `app/screenshots/`.
 *
 * The three ways in, all in the app theme, Russian, at the appearance's font scale, night mode and window width:
 * - [layout]: a layout inflated into [ReferenceHostActivity], filled by `bind`;
 * - [fragment]: a Fragment, Hilt included, in [ReferenceHostActivity];
 * - [host]: an `@AndroidEntryPoint` debug host from `app/src/debug`, which reads its own static appearance.
 *
 * Seeded appearances get MDC's content-based scheme (`DynamicColorsOptions.setContentBasedSource`), the one DS-01a's
 * `ColorSource.Seed` reproduces. `DynamicColors.applyToActivityIfAvailable`, which the debug hosts call, trips an
 * AssetManager check under Robolectric (SP-06), so the hosts get no seed and [SeededActivities] seeds every activity
 * before its `onCreate`.
 */
class XmlReferenceCapture(
    private val shots: AppScreenshotRule,
    private val module: String? = null,
    private val size: CaptureSize = CaptureSize(),
) {
    private val directory = module?.let(AppScreenshotRule::moduleBaselines) ?: AppScreenshotRule.appBaselines

    /** The activities of the current reference, destroyed after its captures. */
    private val launched = mutableListOf<ActivityController<*>>()

    fun layout(preview: String, @LayoutRes layout: Int, bind: (View) -> Unit = {}) = reference(preview) { appearance ->
        val host = launch(ReferenceHostActivity::class.java, appearance)
        host.show(LayoutInflater.from(host).inflate(layout, host.container, false).also(bind))
        host.content
    }

    fun fragment(preview: String, ready: (Fragment) -> Boolean = { true }, create: () -> Fragment) =
        reference(preview) { appearance ->
            val host = launch(ReferenceHostActivity::class.java, appearance)
            val fragment = create()
            host.show(fragment)
            settle { ready(fragment) }
            host.content
        }

    /**
     * [appearance] hands the debug host its appearance (its static `appearance` field) before each launch and the
     * default after the capture; [ready] waits for asynchronous state, [view] picks what to capture.
     */
    fun <A : Activity> host(
        preview: String,
        host: Class<A>,
        appearance: (HostAppearance) -> Unit,
        ready: (A) -> Boolean = { true },
        view: (A) -> View = { it.findViewById(android.R.id.content) },
    ) = try {
        reference(preview) { spec ->
            appearance(HostAppearance(spec.fontScale, spec.dark, spec.widthDp ?: 0, colorSeed = null))
            val activity = launch(host, spec)
            settle { ready(activity) }
            view(activity)
        }
    } finally {
        appearance(HostAppearance())
    }

    private fun reference(preview: String, render: (PreviewAppearance) -> View) {
        if (module != null) {
            if (ShotsRun.recording) {
                directory.addReference(preview)
            } else {
                check(preview in directory.references()) {
                    "$preview is not listed in shared/$module/screenshots/references.txt; record it with " +
                        "`scripts/verify.sh shots app --record`"
                }
            }
        }
        try {
            shots.capture(directory, preview, size, render)
        } finally {
            launched.forEach { runCatching { it.pause().stop().destroy() } }
            launched.clear()
        }
    }

    private fun <A : Activity> launch(activity: Class<A>, appearance: PreviewAppearance): A {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val seeding = appearance.colorSeed?.let(::SeededActivities)
        seeding?.let(application::registerActivityLifecycleCallbacks)
        try {
            return Robolectric.buildActivity(activity).also { launched += it }.setup().get()
        } finally {
            seeding?.let(application::unregisterActivityLifecycleCallbacks)
        }
    }

    private fun settle(ready: () -> Boolean) {
        val deadline = System.nanoTime() + SETTLE_TIMEOUT.toNanos()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()
        while (!ready()) {
            check(System.nanoTime() < deadline) { "The screen was not ready within $SETTLE_TIMEOUT" }
            looper.idleFor(SETTLE_STEP)
            Thread.sleep(SETTLE_STEP.toMillis())
        }
        looper.idle()
    }

    private companion object {
        val SETTLE_TIMEOUT: Duration = Duration.ofSeconds(10)
        val SETTLE_STEP: Duration = Duration.ofMillis(20)
    }
}

/** The host of [XmlReferenceCapture.layout] and [XmlReferenceCapture.fragment]; Robolectric needs no manifest entry. */
@AndroidEntryPoint
class ReferenceHostActivity : AppCompatActivity() {

    lateinit var container: FrameLayout
        private set

    val content: View get() = findViewById(android.R.id.content)

    /**
     * Its own Resources per launch, as the debug hosts get from `createConfigurationContext`. Robolectric changes the
     * shared configuration in place between appearances, so AppCompat's colour state list cache, keyed by the
     * Resources object, would hand the next appearance the previous one's button colours.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.createConfigurationContext(Configuration(newBase.resources.configuration)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply { id = View.generateViewId() }
        setContentView(container)
    }

    fun show(view: View) = container.addView(view)

    fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction().add(container.id, fragment).commitNow()
    }
}

/**
 * Seeds every activity created while registered with MDC's content-based scheme of [seed]: the colour resources MDC
 * computes for a wrapped context are loaded into the activity's resources, and its theme is rebuilt on them with the
 * personalized-colours overlay. Rebuilt, because a style applied to the theme that existed before the loader is what
 * trips Robolectric's AssetManager check.
 */
private class SeededActivities(private val seed: Int) : Application.ActivityLifecycleCallbacks {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        // The application context carries the run's night mode, which decides the light or dark scheme.
        val themed = ContextThemeWrapper(activity.applicationContext, R.style.AppTheme)
        val seeded = DynamicColors.wrapContextIfAvailable(
            themed,
            DynamicColorsOptions.Builder().setContentBasedSource(seed).build(),
        )
        check(seeded !== themed) { "MDC did not seed the scheme (no dynamic colour on this SDK?)" }
        // Resources.getLoaders() is not in the SDK stubs; android-all has it.
        val loaders = ReflectionHelpers.callInstanceMethod<List<ResourcesLoader>>(seeded.resources, "getLoaders")
        activity.resources.addLoaders(*loaders.toTypedArray())
        ReflectionHelpers.setField(activity, "mTheme", null)
        ReflectionHelpers.setField(activity, "mThemeResource", 0)
        activity.setTheme(R.style.AppTheme)
        activity.theme.applyStyle(MaterialR.style.ThemeOverlay_Material3_PersonalizedColors, true)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
