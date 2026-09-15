package dev.alllexey.itmowidgets.feature.update.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import java.util.Locale

/** Hosts the real update screen with a synthetic offer and no version check. */
@AndroidEntryPoint
class AppUpdatePreviewActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            // The copy is Russian, so the configuration locale has to be Russian too.
            setLocale(Locale.forLanguageTag("ru"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode =
            if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(
                this,
                DynamicColorsOptions.Builder().setContentBasedSource(it).build()
            )
        }
        val frame = FrameLayout(this)
        // A named id, not a generated one: recreation restores the Fragment into the same container.
        val container = FrameLayout(this).apply { id = R.id.app_update_test_container }
        frame.addView(
            container,
            FrameLayout.LayoutParams(
                if (appearance.widthDp > 0) (appearance.widthDp * resources.displayMetrics.density).toInt() else -1,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL
            )
        )
        setContentView(frame)
        WindowCompat.getInsetsController(window, frame).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (savedInstanceState != null) return
        supportFragmentManager.beginTransaction()
            .add(container.id, AppUpdateFragment().apply { arguments = offer.toScreenArguments() }, FRAGMENT)
            .commitNow()
    }

    val fragment: AppUpdateFragment
        get() = supportFragmentManager.findFragmentByTag(FRAGMENT) as AppUpdateFragment

    companion object {
        private const val FRAGMENT = "app-update-preview"

        // Set before launch so overrides precede framework/instrumentation resource access.
        @Volatile
        var appearance = Appearance()

        @Volatile
        var offer = AppUpdate(AppVersionName("2.1"), AppVersionName("2.2"), "", false)
    }

    data class Appearance(
        val widthDp: Int = 0,
        val fontScale: Float = 1f,
        val dark: Boolean = false,
        val colorSeed: Int? = null
    )
}
