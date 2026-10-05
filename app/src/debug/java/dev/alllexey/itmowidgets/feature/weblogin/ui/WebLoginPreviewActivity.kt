package dev.alllexey.itmowidgets.feature.weblogin.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The real «Вход на сайт» sheet over an empty window, fed by [repository]; nothing reaches Backend
 * and the clock is fixed at 12:05 Moscow time.
 */
@AndroidEntryPoint
class WebLoginPreviewActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        supportFragmentManager.registerFragmentLifecycleCallbacks(PreviewModels(), false)
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        setContentView(FrameLayout(this))
        if (savedInstanceState == null) WebLoginBottomSheet().show(supportFragmentManager, WebLoginBottomSheet.TAG)
    }

    /** Hands the sheet a view model over [repository] before Hilt could create one, and narrows its window. */
    private class PreviewModels : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
            if (fragment !is WebLoginBottomSheet) return
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = WebLoginViewModel(SavedStateHandle(), repository,
                    TimeZone.of("Europe/Moscow")) as T
            }
            ViewModelProvider(fragment, factory)[WebLoginViewModel::class.java]
        }

        override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
            val width = appearance.widthDp.takeIf { it > 0 } ?: return
            val window = (fragment as? DialogFragment)?.dialog?.window ?: return
            window.setLayout((width * fragment.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    /** Synthetic answers: codes in [previews] exist, everything else is not found. */
    class FixtureRepository : WebLoginRepository {
        val previews = mutableMapOf<String, AppResult<WebLoginPreview>>()
        @Volatile var approval: AppResult<Unit> = AppResult.Success(Unit)
        /** When set, answers wait for it, so a test can look at the in-button progress. */
        @Volatile var gate: CompletableDeferred<Unit>? = null
        val approved = mutableListOf<Uuid>()

        override suspend fun preview(code: String): AppResult<WebLoginPreview> {
            gate?.await()
            return previews[code] ?: AppResult.Failure(AppError.NotFound)
        }

        override suspend fun approve(challengeId: Uuid): AppResult<Unit> {
            gate?.await()
            approved += challengeId
            return approval
        }
    }

    companion object {
        val SYNTHETIC_PREVIEW = WebLoginPreview(
            challengeId = Uuid.parse("00000000-0000-0000-0000-000000000042"),
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            createdAt = Instant.parse("2026-09-24T09:04:30Z"),
            expiresAt = Instant.parse("2026-09-24T09:06:30Z"),
        )

        @Volatile var appearance = PreviewAppearance()
        @Volatile var repository: WebLoginRepository = FixtureRepository()
    }
}
