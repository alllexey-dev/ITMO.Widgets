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
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.di.bridge.ScheduleDebugFixtures
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleCheckResult
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.koin.core.module.Module

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

    private lateinit var koinFixture: Module

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate(): a restored ScheduleChangesFragment obtains its ViewModel from Koin there.
        koinFixture = ScheduleDebugFixtures.loadChanges(this, MemoryChanges, FixedTime)
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

    override fun onDestroy() {
        ScheduleDebugFixtures.unload(this, koinFixture)
        super.onDestroy()
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
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today() = LocalDate(2026, 9, 7)
        override fun now() = today().atTime(12, 0).toInstant(timeZone)
    }

    companion object {
        private const val FRAGMENT = "schedule-changes-preview"

        // Set before launch so overrides precede framework/instrumentation resource access.
        @Volatile var appearance = PreviewAppearance()
        val changes = MutableStateFlow<List<ScheduleChange>>(emptyList())
        @Volatile var readCalls = 0
    }
}
