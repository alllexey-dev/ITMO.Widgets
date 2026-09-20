package dev.alllexey.itmowidgets.feature.schedule.ui.details

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.MapLauncher
import dev.alllexey.itmowidgets.core.ui.navigation.openRoot
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentPendingSportDetailsBinding
import dev.alllexey.itmowidgets.databinding.ItemSportDetailFactBinding
import java.io.Serializable
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** A queued or predicted sport booking from the schedule; managing it happens on the sport tab. */
@AndroidEntryPoint
class PendingSportDetailsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentPendingSportDetailsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var timeProvider: AcademicTimeProvider

    private val booking: PendingSportDetailsArgs by lazy {
        requireNotNull(requireArguments().serializable(ARG_BOOKING, PendingSportDetailsArgs::class.java))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPendingSportDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.backgroundTintList = ColorStateList.valueOf(
                requireContext().color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLowest)
            )
            (it.background as? com.google.android.material.shape.MaterialShapeDrawable)?.elevation = 0f
            it.layoutParams = it.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.90f).toInt() }
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        toolbar.setNavigationOnClickListener { dismiss() }
        sectionName.text = booking.sectionName
        status.setText(if (booking.isPrediction) R.string.schedule_auto_sign_prediction else R.string.schedule_auto_sign_waiting)
        statusDescription.text = listOf(
            getString(if (booking.isPrediction) R.string.schedule_auto_sign_prediction_description else R.string.schedule_auto_sign_waiting_description),
            getString(if (booking.autoSign) R.string.sport_queue_future_hint else R.string.sport_queue_free_hint)
        ).joinToString("\n")

        val zone = timeProvider.zoneId
        val start = OffsetDateTime.parse(booking.start).atZoneSameInstant(zone)
        val end = OffsetDateTime.parse(booking.end).atZoneSameInstant(zone)
        timeFact.bind(
            R.string.schedule_lesson_details_time,
            getString(R.string.schedule_lesson_details_time_value, start.format(DATE_FORMATTER), start.format(TIME_FORMATTER), end.format(TIME_FORMATTER)),
            R.drawable.ic_schedule_rounded
        )
        teacherFact.bind(R.string.schedule_lesson_details_teacher, booking.teacherFio, R.drawable.ic_person_rounded)
        locationFact.bind(R.string.schedule_lesson_details_location, booking.roomName, R.drawable.ic_location_on_rounded)
        mapButton.isVisible = booking.roomName.isNotBlank()
        mapButton.setOnClickListener {
            val opened = MapLauncher.open(requireContext(), MapDestination(label = booking.sectionName, address = booking.roomName))
            if (!opened) Snackbar.make(root, R.string.schedule_map_unavailable, Snackbar.LENGTH_SHORT).show()
        }
        openSport.setOnClickListener {
            dismiss()
            openRoot(AppRoot.SPORT)
        }
    }

    private fun ItemSportDetailFactBinding.bind(title: Int, value: String, icon: Int) {
        root.isVisible = value.isNotBlank()
        factValue.text = value
        factValue.contentDescription = getString(R.string.sport_detail_fact_description, getString(title), value)
        factIcon.setImageResource(icon)
        alignRailIcon(factIcon, factValue)
    }

    private fun alignRailIcon(icon: ImageView, text: TextView) {
        icon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = ((text.lineHeight - icon.layoutParams.height) / 2).coerceAtLeast(0)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "PendingSportDetailsBottomSheet"
        private const val ARG_BOOKING = "arg_pending_booking"
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))

        fun newInstance(booking: PendingSportBooking): PendingSportDetailsBottomSheet = newInstance(booking.toDetailsArgs())

        fun newInstance(args: PendingSportDetailsArgs): PendingSportDetailsBottomSheet = PendingSportDetailsBottomSheet().apply {
            arguments = Bundle().apply { putSerializable(ARG_BOOKING, args) }
        }

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        private fun <T : Serializable> Bundle.serializable(key: String, type: Class<T>): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getSerializable(key, type)
            else getSerializable(key) as? T
    }
}
