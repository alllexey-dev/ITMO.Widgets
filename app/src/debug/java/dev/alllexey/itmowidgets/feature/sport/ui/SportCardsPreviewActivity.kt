package dev.alllexey.itmowidgets.feature.sport.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.NoOpAppNavigator
import dev.alllexey.itmowidgets.designsystem.host.ItmoComposeHost
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingActions
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingCard
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonActions
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonList
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonListState
import java.time.LocalDate
import java.util.Locale
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDate

/**
 * Isolated real `Мой спорт` booking cards, `Запись` lesson list and details sheet (Compose), with synthetic test inputs
 * and no network actions.
 */
@AndroidEntryPoint
class SportCardsPreviewActivity : AppCompatActivity(), AppNavigator by NoOpAppNavigator {
    lateinit var cards: ComposeView
    var actionCount = 0
    var lastAction: String? = null
    private var shown by mutableStateOf<Shown>(Shown.Bookings(emptyList()))
    val openedScreens = mutableListOf<Pair<AppScreen, Bundle?>>()

    override fun attachBaseContext(newBase: Context) {
        // AppCompat chooses its night configuration while attaching, before onCreate.
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            // The copy is Russian, so plural rules have to be Russian too: `getQuantityString`
            // picks the form by the configuration locale, not by the language of the strings.
            setLocale(Locale.forLanguageTag("ru"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
                if (fragment is SportCommonDetailsBottomSheet) fragment.timeProvider = FixedTime
            }
            override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
                if (fragment is SportCommonDetailsBottomSheet && appearance.widthDp > 0) {
                    fragment.dialog?.window?.setLayout((appearance.widthDp * resources.displayMetrics.density).toInt(), -1)
                }
            }
        }, false)
        super.onCreate(savedInstanceState)
        supportFragmentManager.setFragmentResultListener(SportCommonDetailsBottomSheet.ACTION_REQUEST, this) { _, result ->
            action(when (result.getString(SportCommonDetailsBottomSheet.RESULT_ACTION)) {
                "SIGN" -> "sign"
                "CANCEL" -> "unsign"
                "AUTO" -> "auto"
                "CANCEL_AUTO" -> "unauto"
                else -> error("Unexpected details action")
            })
        }
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        val frame = FrameLayout(this)
        cards = ComposeView(this).apply { setContent { Cards() } }
        frame.addView(cards, FrameLayout.LayoutParams(
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) {
        openedScreens.add(screen to arguments?.let(::Bundle))
    }

    /** The `Мой спорт` cards of [items] as the page lists them, on [FixedTime]. */
    fun showBookings(items: List<SportBooking>) {
        shown = Shown.Bookings(items)
    }

    /** The `Запись` cards of [items] as the page lists them, on [FixedTime]. */
    fun showLessons(items: List<SportLesson>) {
        shown = Shown.Lessons(items)
    }

    @Composable
    private fun Cards() = ItmoComposeHost.locals {
        ItmoTheme(colorSource = appearance.colorSeed?.let(ColorSource::Seed) ?: ColorSource.Platform) {
            when (val cards = shown) {
                is Shown.Bookings -> LazyColumn {
                    items(cards.items, key = { it.lessonId }) { booking ->
                        SportBookingCard(booking, FixedTime, bookingActions)
                    }
                }
                is Shown.Lessons -> SportLessonList(
                    state = SportLessonListState.Lessons(cards.items),
                    time = FixedTime,
                    actions = SportLessonActions(
                        onOpen = ::showDetails,
                        onAction = { _, action -> action(action.actionName()) },
                    ),
                    onRetry = {},
                )
            }
        }
    }

    private val bookingActions = SportBookingActions(
        onOpen = ::showDetails,
        onCancel = { action("cancel") },
        onOpenMap = { action("map") },
    )

    private fun SportBookingAction.actionName(): String = when (this) {
        SportBookingAction.SIGN -> "sign"
        SportBookingAction.CANCEL -> "unsign"
        SportBookingAction.AUTO -> "auto"
        SportBookingAction.CANCEL_AUTO -> "unauto"
        SportBookingAction.NONE -> "none"
    }

    fun showDetails(item: SportCommon) {
        SportCommonDetailsBottomSheet.newInstance(item, actionsEnabled = true).show(supportFragmentManager, SportCommonDetailsBottomSheet.TAG)
    }

    private fun action(name: String) { actionCount++; lastAction = name }

    private sealed interface Shown {
        data class Bookings(val items: List<SportBooking>) : Shown
        data class Lessons(val items: List<SportLesson>) : Shown
    }

    object FixedTime : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today() = LocalDate.of(2026, 9, 7).toKotlinLocalDate()
        override fun now() = today().atTime(12, 0).toInstant(timeZone)
    }

    companion object { @Volatile var appearance = PreviewAppearance() }
}
