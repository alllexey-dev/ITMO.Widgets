package dev.alllexey.itmowidgets.feature.me.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.databinding.DialogSportScoreOverrideBinding
import dev.alllexey.itmowidgets.databinding.FragmentMeBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@AndroidEntryPoint
class MeFragment : Fragment() {

    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    @Inject
    lateinit var timeOverrideController: AcademicTimeOverrideController

    @Inject
    lateinit var sportScoreOverrideController: SportScoreOverrideController

    @Inject
    lateinit var sportLessonTemplateController: SportLessonTemplateController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.debugTimeContainer.isVisible = BuildConfig.DEBUG
        binding.debugSportScoreContainer.isVisible = BuildConfig.DEBUG
        binding.debugSportLessonsContainer.isVisible = BuildConfig.DEBUG
        if (!BuildConfig.DEBUG) return

        renderDebugDate()
        renderDebugSportScore()
        binding.debugTimeSelectButton.setOnClickListener { showDatePicker() }
        binding.debugTimeResetButton.setOnClickListener {
            timeOverrideController.setOverrideDate(null)
            requireActivity().recreate()
        }
        binding.debugSportScoreConfigureButton.setOnClickListener {
            showSportScoreOverrideDialog()
        }
        binding.debugSportScoreResetButton.setOnClickListener {
            sportScoreOverrideController.setOverride(null)
            requireActivity().recreate()
        }
        binding.debugSportLessonsSwitch.isChecked = sportLessonTemplateController.isEnabled()
        binding.debugSportLessonsSwitch.setOnCheckedChangeListener { _, isChecked ->
            sportLessonTemplateController.setEnabled(isChecked)
            requireActivity().recreate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showDatePicker() {
        val initialDate = timeOverrideController.getOverrideDate() ?: timeProvider.today()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                timeOverrideController.setOverrideDate(
                    LocalDate.of(year, month + 1, dayOfMonth)
                )
                requireActivity().recreate()
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).show()
    }

    private fun renderDebugDate() {
        val overrideDate = timeOverrideController.getOverrideDate()
        binding.debugTimeValue.text = if (overrideDate == null) {
            getString(R.string.debug_academic_time_system)
        } else {
            getString(
                R.string.debug_academic_time_value,
                overrideDate.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                )
            )
        }
        binding.debugTimeResetButton.isEnabled = overrideDate != null
    }

    private fun showSportScoreOverrideDialog() {
        val dialogBinding = DialogSportScoreOverrideBinding.inflate(layoutInflater)
        val currentOverride = sportScoreOverrideController.getOverride()
        dialogBinding.attendancePointsInput.setText(
            (currentOverride?.attendances ?: DEFAULT_ATTENDANCE_POINTS).toString()
        )
        dialogBinding.bonusPointsInput.setText(
            (currentOverride?.bonus ?: DEFAULT_BONUS_POINTS).toString()
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debug_sport_score_dialog_title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val attendances = dialogBinding.attendancePointsInput.text
                    ?.toString()
                    ?.toIntOrNull()
                val bonus = dialogBinding.bonusPointsInput.text
                    ?.toString()
                    ?.toIntOrNull()

                val attendancesValid = attendances != null && attendances in SCORE_RANGE
                val bonusValid = bonus != null && bonus in SCORE_RANGE
                dialogBinding.attendancePointsLayout.error = if (attendancesValid) {
                    null
                } else {
                    getString(R.string.debug_sport_score_invalid_value)
                }
                dialogBinding.bonusPointsLayout.error = if (bonusValid) {
                    null
                } else {
                    getString(R.string.debug_sport_score_invalid_value)
                }

                if (attendancesValid && bonusValid) {
                    sportScoreOverrideController.setOverride(
                        SportScoreOverride(
                            attendances = requireNotNull(attendances),
                            bonus = requireNotNull(bonus)
                        )
                    )
                    dialog.dismiss()
                    requireActivity().recreate()
                }
            }
        }
        dialog.show()
    }

    private fun renderDebugSportScore() {
        val override = sportScoreOverrideController.getOverride()
        binding.debugSportScoreValue.text = if (override == null) {
            getString(R.string.debug_sport_score_server)
        } else {
            getString(
                R.string.debug_sport_score_value,
                override.attendances,
                override.bonus
            )
        }
        binding.debugSportScoreResetButton.isEnabled = override != null
    }

    private companion object {
        const val DEFAULT_ATTENDANCE_POINTS = 100
        const val DEFAULT_BONUS_POINTS = 20
        val SCORE_RANGE = 0..9999
    }
}
