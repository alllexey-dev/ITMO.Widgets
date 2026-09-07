package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dev.alllexey.itmowidgets.R

/** Isolated renderer host: no authenticated graph, repositories, or session fixtures. */
class SettingsPreviewActivity : AppCompatActivity() {

    lateinit var sectionsContainer: LinearLayout
        private set

    override fun attachBaseContext(newBase: Context) {
        val preview = appearance
        val configuration = Configuration(newBase.resources.configuration).apply {
            fontScale = preview.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (preview.dark) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (appearance.dark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        super.onCreate(savedInstanceState)
        if (intent.hasExtra(EXTRA_COLOR_SEED)) {
            DynamicColors.applyToActivityIfAvailable(
                this,
                DynamicColorsOptions.Builder()
                    .setContentBasedSource(intent.getIntExtra(EXTRA_COLOR_SEED, 0))
                    .build()
            )
        }
        val frame = FrameLayout(this)
        val profile = intent.getBooleanExtra(EXTRA_PROFILE, false)
        val content = layoutInflater.inflate(if (profile) R.layout.fragment_me else R.layout.fragment_settings, frame, false)
        val widthDp = intent.getIntExtra(EXTRA_WIDTH_DP, 0)
        frame.addView(
            content,
            FrameLayout.LayoutParams(
                if (widthDp > 0) (widthDp * resources.displayMetrics.density).toInt() else -1,
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
        if (profile) return
        sectionsContainer = content.findViewById(R.id.sections_container)
        // Match the production Fragment while asynchronous settings have not arrived.
        content.findViewById<View>(R.id.settings_scroll).visibility = View.GONE
    }

    companion object {
        // Set before launch so overrides precede framework/instrumentation resource access.
        @Volatile
        var appearance = Appearance()

        const val EXTRA_PROFILE = "preview_profile"
        const val EXTRA_COLOR_SEED = "preview_color_seed"
        const val EXTRA_WIDTH_DP = "preview_width_dp"
    }

    data class Appearance(val fontScale: Float = 1f, val dark: Boolean = false)
}
