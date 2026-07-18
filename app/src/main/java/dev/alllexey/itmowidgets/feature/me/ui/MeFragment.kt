package dev.alllexey.itmowidgets.feature.me.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
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
        if (!BuildConfig.DEBUG) return

        renderDebugDate()
        binding.debugTimeSelectButton.setOnClickListener { showDatePicker() }
        binding.debugTimeResetButton.setOnClickListener {
            timeOverrideController.setOverrideDate(null)
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
}
