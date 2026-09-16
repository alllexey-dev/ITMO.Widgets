package dev.alllexey.itmowidgets.feature.sport.ui

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingAdapter
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingListener
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonItem
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonsAdapter
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSignActionsListener
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Isolated real adapters and details sheet, with synthetic test inputs and no network actions. */
@AndroidEntryPoint
class SportCardsPreviewActivity : AppCompatActivity(), SportBookingListener, SportSignActionsListener {
    lateinit var list: RecyclerView
    var actionCount = 0
    var lastAction: String? = null
    val bookingAdapter = SportBookingAdapter(FixedTime, this)
    val lessonAdapter = SportLessonsAdapter(this, FixedTime)

    override fun attachBaseContext(newBase: Context) {
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
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
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
        list = RecyclerView(this).apply {
            id = R.id.sport_cards_test_list
            layoutManager = LinearLayoutManager(this@SportCardsPreviewActivity)
            clipToPadding = false
        }
        frame.addView(list, FrameLayout.LayoutParams(
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

    fun showBookings(items: List<SportBooking>) {
        list.adapter = bookingAdapter
        bookingAdapter.submitList(items)
    }

    fun showLessons(items: List<SportLessonItem>) {
        list.adapter = lessonAdapter
        lessonAdapter.submitList(items)
    }

    fun showDetails(item: SportCommon) {
        SportCommonDetailsBottomSheet.newInstance(item, actionsEnabled = true).show(supportFragmentManager, SportCommonDetailsBottomSheet.TAG)
    }

    private fun action(name: String) { actionCount++; lastAction = name }
    override fun onUnSign(booking: SportBooking) = action("cancel")
    override fun onLocationClick(booking: SportBooking) = action("map")
    override fun onBookingClick(booking: SportBooking) = showDetails(booking)
    override fun onSignUpClick(lesson: SportLesson) = action("sign")
    override fun onUnSignClick(lesson: SportLesson) = action("unsign")
    override fun onAutoSignClick(lesson: SportLesson) = action("auto")
    override fun onUnAutoSignClick(lesson: SportLesson) = action("unauto")
    override fun onLessonClick(lesson: SportLesson) = showDetails(lesson)

    object FixedTime : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.of(2026, 9, 7)
        override fun now() = today().atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }

    data class Appearance(val fontScale: Float = 1f, val dark: Boolean = false, val widthDp: Int = 0, val colorSeed: Int? = null)
    companion object { @Volatile var appearance = Appearance() }
}
