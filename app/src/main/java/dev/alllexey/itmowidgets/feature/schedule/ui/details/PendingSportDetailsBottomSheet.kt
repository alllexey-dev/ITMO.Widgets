package dev.alllexey.itmowidgets.feature.schedule.ui.details

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.ConditionTone
import dev.alllexey.itmowidgets.core.ui.DetailsHeaderContent
import dev.alllexey.itmowidgets.core.ui.alignRailIcon
import dev.alllexey.itmowidgets.core.ui.bind
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.MapLauncher
import dev.alllexey.itmowidgets.core.ui.navigation.openRoot
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentPendingSportDetailsBinding
import dev.alllexey.itmowidgets.databinding.ItemSportConditionBinding
import java.io.Serializable
import java.time.OffsetDateTime
import javax.inject.Inject

/**
 * A queued or predicted sport booking from the schedule, laid out like the sport
 * tab's own details: the shared header, then the booking conditions as one
 * condition card. Managing the queue happens on the sport tab.
 */
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
        val zone = timeProvider.zoneId
        val start = OffsetDateTime.parse(booking.start).atZoneSameInstant(zone)
        val end = OffsetDateTime.parse(booking.end).atZoneSameInstant(zone)
        header.bind(
            DetailsHeaderContent(
                title = booking.sectionName,
                kind = getString(if (booking.isPrediction) R.string.schedule_auto_sign_prediction else R.string.schedule_auto_sign_waiting),
                date = start.toLocalDate(),
                start = start.toLocalTime(),
                end = end.toLocalTime(),
                teacher = booking.teacherFio,
                location = booking.roomName,
                mapAvailable = booking.roomName.isNotBlank()
            )
        ) {
            val opened = MapLauncher.open(requireContext(), MapDestination(label = booking.sectionName, address = booking.roomName))
            if (!opened) Snackbar.make(root, R.string.schedule_map_unavailable, Snackbar.LENGTH_SHORT).show()
        }
        bindConditions()
        openSport.setOnClickListener {
            dismiss()
            openRoot(AppRoot.SPORT)
        }
    }

    /** One waiting card, the same one the sport tab shows for a queue or a prediction. */
    private fun bindConditions() = with(binding) {
        attentionContainer.removeAllViews()
        val row = ItemSportConditionBinding.inflate(layoutInflater, attentionContainer, false)
        val tone = ConditionTone.WAITING
        row.root.setCardBackgroundColor(tone.container(requireContext()))
        row.conditionTitle.setText(if (booking.isPrediction) R.string.sport_prediction_waiting else R.string.sport_registration_waiting)
        row.conditionTitle.setTextColor(tone.accent(requireContext()))
        row.conditionBody.text = getString(
            when {
                booking.isPrediction -> R.string.sport_prediction_hint
                booking.autoSign -> R.string.sport_queue_future_hint
                else -> R.string.sport_queue_free_hint
            }
        )
        row.conditionBody.setTextColor(requireContext().color.onSurface)
        row.conditionIcon.setImageResource(R.drawable.ic_schedule_rounded)
        row.conditionIcon.imageTintList = ColorStateList.valueOf(tone.accent(requireContext()))
        alignRailIcon(row.conditionIcon, row.conditionTitle)
        attentionContainer.addView(row.root)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "PendingSportDetailsBottomSheet"
        private const val ARG_BOOKING = "arg_pending_booking"

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
