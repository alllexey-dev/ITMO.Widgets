package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance

/**
 * Isolated appearance host: no authenticated graph, repositories, or session fixtures. It shows one column at the
 * requested width: a plain [ScrollView] (`settings_scroll`) around [sectionsContainer] for the View rows other
 * features' tests add, or the view a test passes to [showContent], such as a `ComposeView` with `SettingsScreen`.
 */
class SettingsPreviewActivity : AppCompatActivity() {

    lateinit var sectionsContainer: LinearLayout
        private set

    private lateinit var frame: FrameLayout

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
        frame = FrameLayout(this)
        val margin = resources.getDimensionPixelSize(R.dimen.design_screen_margin)
        sectionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // The margins of the settings column these rows were drawn in.
            setPadding(margin, 0, margin, margin)
        }
        val scroll = ScrollView(this).apply {
            id = R.id.settings_scroll
            // Like the settings page while its rows have not arrived; a test shows it when it adds rows.
            visibility = View.GONE
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            addView(sectionsContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        showContent(scroll)
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
    }

    /** Replaces the column's content with [content], at the width the intent asked for. */
    fun showContent(content: View) {
        frame.removeAllViews()
        val widthDp = intent.getIntExtra(EXTRA_WIDTH_DP, 0)
        frame.addView(
            content,
            FrameLayout.LayoutParams(
                if (widthDp > 0) (widthDp * resources.displayMetrics.density).toInt() else -1,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL
            )
        )
    }

    companion object {
        // Set before launch so overrides precede framework/instrumentation resource access.
        @Volatile
        var appearance = PreviewAppearance()

        const val EXTRA_COLOR_SEED = "preview_color_seed"
        const val EXTRA_WIDTH_DP = "preview_width_dp"
    }
}
