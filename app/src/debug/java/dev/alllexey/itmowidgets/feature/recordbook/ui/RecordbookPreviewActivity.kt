package dev.alllexey.itmowidgets.feature.recordbook.ui

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import java.time.LocalDate
import java.time.ZoneId

/** Real production Fragments with test-supplied in-memory repositories; never reads a session. */
@AndroidEntryPoint
class RecordbookPreviewActivity : AppCompatActivity(), AppNavigator {
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
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
                if (fragment !is RecordbookFragment && fragment !is RecordbookSubjectFragment) return
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val resolver = RecordbookSportResolver(checkNotNull(sportRepository))
                        return if (fragment is RecordbookFragment) {
                            RecordbookViewModel(checkNotNull(repository), bars ?: NoBars, preference, SavedStateHandle(), resolver, FixedTime) as T
                        } else {
                            val args = fragment.requireArguments()
                            val values = mutableMapOf<String, Any>(
                                "entry_id" to args.getLong("entry_id"), "program_id" to args.getLong("program_id"),
                                "semester" to args.getInt("semester"), "study_year" to checkNotNull(args.getString("study_year"))
                            )
                            @Suppress("DEPRECATION")
                            (args.get("bars_plan") as? Long)?.let { plan ->
                                values["bars_plan"] = plan
                                values["bars_type"] = checkNotNull(args.getString("bars_type"))
                                values["bars_identifier"] = checkNotNull(args.getString("bars_identifier"))
                            }
                            val handle = SavedStateHandle(values)
                            RecordbookSubjectViewModel(checkNotNull(repository), bars ?: NoBars, handle, resolver) as T
                        }
                    }
                }
                if (fragment is RecordbookFragment) ViewModelProvider(fragment, factory)[RecordbookViewModel::class.java]
                else ViewModelProvider(fragment, factory)[RecordbookSubjectViewModel::class.java]
            }
        }, false)
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        val frame = FrameLayout(this)
        val container = FrameLayout(this).apply { id = R.id.recordbook_test_container }
        frame.addView(container, FrameLayout.LayoutParams(
            if (appearance.widthDp > 0) (appearance.widthDp * resources.displayMetrics.density).toInt() else -1,
            -1, Gravity.CENTER_HORIZONTAL
        ))
        setContentView(frame)
        WindowCompat.getInsetsController(window, frame).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.recordbook_test_container, RecordbookFragment(), ROOT_TAG).commitNow()
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) {
        check(screen == AppScreen.RECORDBOOK_SUBJECT)
        supportFragmentManager.beginTransaction().replace(R.id.recordbook_test_container,
            RecordbookSubjectFragment().apply { this.arguments = arguments }, "detail")
            .addToBackStack("subject").commit()
    }

    override fun dismissOverlays() = Unit

    private object FixedTime : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.of(2026, 6, 1)
        override fun now() = today().atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }

    private object NoBars : BarsRecordbookRepository {
        override suspend fun getSubjects(period: RecordbookPeriod) = AppResult.Success(emptyList<RecordbookSubject>())
        override suspend fun getSubject(journal: BarsJournalReference) = AppResult.Failure(AppError.NotFound)
    }

    /** In-memory toggle so previews never touch DataStore or a BARS session. */
    private val preference = object : BarsPreferenceRepository {
        private var enabled = false
        override suspend fun isEnabled() = enabled
        override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> { this.enabled = enabled; return AppResult.Success(Unit) }
    }

    data class Appearance(val fontScale: Float = 1f, val dark: Boolean = false, val widthDp: Int = 0, val colorSeed: Int? = null)

    companion object {
        const val ROOT_TAG = "recordbook"
        @Volatile var appearance = Appearance()
        @Volatile var repository: RecordbookRepository? = null
        @Volatile var bars: BarsRecordbookRepository? = null
        @Volatile var sportRepository: SportScoreRepository? = null
    }
}
