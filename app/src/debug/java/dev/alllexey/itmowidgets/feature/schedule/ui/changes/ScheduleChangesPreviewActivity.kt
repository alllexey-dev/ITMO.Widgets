package dev.alllexey.itmowidgets.feature.schedule.ui.changes

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
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleCheckResult
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesViewModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/** Hosts the real history screen over changes kept in memory; no file, no check and no notification. */
@AndroidEntryPoint
class ScheduleChangesPreviewActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            // The dates are formatted in Russian, so the configuration locale has to be Russian too.
            setLocale(Locale.forLanguageTag("ru"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
                if (fragment !is ScheduleChangesFragment) return
                ViewModelProvider(fragment, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        ScheduleChangesViewModel(MemoryChanges, FixedTime, SavedStateHandle()) as T
                })[ScheduleChangesViewModel::class.java]
            }
        }, false)
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        val frame = FrameLayout(this)
        // A named id, not a generated one: recreation restores the Fragment into the same container.
        val container = FrameLayout(this).apply { id = R.id.schedule_changes_test_container }
        frame.addView(container, FrameLayout.LayoutParams(
            if (appearance.widthDp > 0) (appearance.widthDp * resources.displayMetrics.density).toInt() else -1,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER_HORIZONTAL
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
        if (savedInstanceState != null) return
        supportFragmentManager.beginTransaction()
            .add(container.id, ScheduleChangesFragment(), FRAGMENT)
            .commitNow()
    }

    val fragment: ScheduleChangesFragment
        get() = supportFragmentManager.findFragmentByTag(FRAGMENT) as ScheduleChangesFragment

    /** Marking read flips the rows in [changes] in place, like the file-backed repository. */
    private object MemoryChanges : ScheduleChangesRepository {
        init { check(BuildConfig.DEBUG) }

        override fun observeChanges() = changes

        override suspend fun check(): AppResult<ScheduleCheckResult> = AppResult.Success(ScheduleCheckResult.Compared(0))

        override suspend fun markNotified(ids: Set<String>) = Unit

        override suspend fun markAllRead() {
            readCalls++
            changes.value = changes.value.map { it.copy(read = true) }
        }

        override suspend fun resetSnapshot() = Unit
    }

    private object FixedTime : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.of(2026, 9, 7)
        override fun now(): OffsetDateTime = today().atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }

    data class Appearance(val fontScale: Float = 1f, val dark: Boolean = false, val widthDp: Int = 0, val colorSeed: Int? = null)

    companion object {
        private const val FRAGMENT = "schedule-changes-preview"

        // Set before launch so overrides precede framework/instrumentation resource access.
        @Volatile var appearance = Appearance()
        val changes = MutableStateFlow<List<ScheduleChange>>(emptyList())
        @Volatile var readCalls = 0
    }
}
