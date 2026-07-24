package dev.alllexey.itmowidgets.feature.me.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.DialogSportScoreOverrideBinding
import dev.alllexey.itmowidgets.databinding.DialogDebugRefreshTokenBinding
import dev.alllexey.itmowidgets.databinding.FragmentMeBinding
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.feature.me.presentation.MeEvent
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class MeFragment : Fragment() {

    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.debugRefreshTokenContainer.isVisible = BuildConfig.DEBUG
        binding.debugTimeContainer.isVisible = BuildConfig.DEBUG
        binding.debugSportScoreContainer.isVisible = BuildConfig.DEBUG
        binding.debugSportLessonsContainer.isVisible = BuildConfig.DEBUG
        if (!BuildConfig.DEBUG) return

        observeState()
        binding.debugRefreshTokenConfigureButton.setOnClickListener {
            showRefreshTokenDialog()
        }
        binding.debugTimeSelectButton.setOnClickListener { showDatePicker() }
        binding.debugTimeResetButton.setOnClickListener { viewModel.setDateOverride(null) }
        binding.debugSportScoreConfigureButton.setOnClickListener {
            showSportScoreOverrideDialog()
        }
        binding.debugSportScoreResetButton.setOnClickListener {
            viewModel.clearScoreOverride()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showDatePicker() {
        val state = viewModel.uiState.value as MeUiState.Content
        val initialDate = state.dateOverride ?: state.effectiveDate
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                viewModel.setDateOverride(
                    LocalDate.of(year, month + 1, dayOfMonth)
                )
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).show()
    }

    private fun observeState() {
        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                when (state) {
                    is MeUiState.Content -> renderContent(state)
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                when (event) {
                    MeEvent.RecreateActivity -> requireActivity().recreate()
                    MeEvent.RefreshTokenUpdated -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.debug_refresh_token_updated,
                            Toast.LENGTH_SHORT
                        ).show()
                        requireActivity().recreate()
                    }
                    is MeEvent.RefreshTokenUpdateFailed -> {
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.debug_refresh_token_failed,
                                getString(event.error.messageRes())
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderContent(state: MeUiState.Content) {
        renderDebugRefreshToken(state)
        renderDebugDate(state)
        renderDebugSportScore(state)
        binding.debugSportLessonsSwitch.setOnCheckedChangeListener(null)
        binding.debugSportLessonsSwitch.isChecked = state.lessonTemplatesEnabled
        binding.debugSportLessonsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setLessonTemplatesEnabled(isChecked)
        }
    }

    private fun showRefreshTokenDialog() {
        val dialogBinding = DialogDebugRefreshTokenBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debug_refresh_token_dialog_title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.debug_refresh_token_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val token = dialogBinding.refreshTokenInput.text?.toString().orEmpty()
                if (token.isBlank()) {
                    dialogBinding.refreshTokenLayout.error =
                        getString(R.string.debug_refresh_token_required)
                } else {
                    dialogBinding.refreshTokenInput.text?.clear()
                    viewModel.replaceRefreshToken(token)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun renderDebugRefreshToken(state: MeUiState.Content) {
        binding.debugRefreshTokenValue.setText(
            if (state.refreshTokenConfigured) {
                R.string.debug_refresh_token_configured
            } else {
                R.string.debug_refresh_token_not_configured
            }
        )
        binding.debugRefreshTokenConfigureButton.isEnabled =
            !state.refreshTokenUpdateInProgress
        binding.debugRefreshTokenConfigureButton.setText(
            when {
                state.refreshTokenUpdateInProgress ->
                    R.string.debug_refresh_token_checking
                state.refreshTokenConfigured ->
                    R.string.debug_refresh_token_replace
                else ->
                    R.string.debug_refresh_token_add
            }
        )
    }

    private fun renderDebugDate(state: MeUiState.Content) {
        val overrideDate = state.dateOverride
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
        val currentOverride = (viewModel.uiState.value as MeUiState.Content).scoreOverride
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
                    viewModel.setScoreOverride(
                        attendances = requireNotNull(attendances),
                        bonus = requireNotNull(bonus)
                    )
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun renderDebugSportScore(state: MeUiState.Content) {
        val override = state.scoreOverride
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
