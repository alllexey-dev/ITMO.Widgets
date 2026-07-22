package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.ItemRecordbookPeriodBinding
import dev.alllexey.itmowidgets.databinding.SheetRecordbookPeriodBinding
import kotlin.math.roundToInt

data class RecordbookPeriodOption(
    val programId: Long,
    val semester: Int,
    val course: Int,
    val studyYear: String,
    val actual: Boolean
)

class RecordbookPeriodBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetRecordbookPeriodBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetRecordbookPeriodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val options = readOptions()
        val selectedProgram = requireArguments().getLong(ARG_SELECTED_PROGRAM)
        val selectedSemester = requireArguments().getInt(ARG_SELECTED_SEMESTER)
        binding.programName.text = requireArguments().getString(ARG_PROGRAM_NAME)
        binding.recyclerView.adapter = PeriodAdapter(
            options = options,
            selectedProgram = selectedProgram,
            selectedSemester = selectedSemester,
            onClick = ::select
        )
        binding.closeButton.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.85f).roundToInt()
        }
        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun readOptions(): List<RecordbookPeriodOption> {
        val arguments = requireArguments()
        val programIds = arguments.getLongArray(ARG_PROGRAM_IDS) ?: longArrayOf()
        val semesters = arguments.getIntArray(ARG_SEMESTERS) ?: intArrayOf()
        val courses = arguments.getIntArray(ARG_COURSES) ?: intArrayOf()
        val years = arguments.getStringArrayList(ARG_YEARS).orEmpty()
        val actual = arguments.getBooleanArray(ARG_ACTUAL) ?: booleanArrayOf()
        return semesters.indices.map { index ->
            RecordbookPeriodOption(
                programId = programIds[index],
                semester = semesters[index],
                course = courses[index],
                studyYear = years[index],
                actual = actual[index]
            )
        }
    }

    private fun select(option: RecordbookPeriodOption) {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_PROGRAM_ID to option.programId,
                RESULT_SEMESTER to option.semester
            )
        )
        dismiss()
    }

    private class PeriodAdapter(
        private val options: List<RecordbookPeriodOption>,
        private val selectedProgram: Long,
        private val selectedSemester: Int,
        private val onClick: (RecordbookPeriodOption) -> Unit
    ) : RecyclerView.Adapter<PeriodAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemRecordbookPeriodBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(options[position])
        }

        override fun getItemCount(): Int = options.size

        inner class ViewHolder(
            private val binding: ItemRecordbookPeriodBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(option: RecordbookPeriodOption) {
                val semesterInCourse = if (option.semester % 2 == 0) 2 else 1
                binding.title.text = binding.root.context.getString(
                    R.string.recordbook_period_value,
                    option.course,
                    semesterInCourse
                )
                binding.subtitle.text = if (option.actual) {
                    binding.root.context.getString(
                        R.string.recordbook_current_period,
                        option.studyYear
                    )
                } else {
                    option.studyYear
                }
                binding.check.isVisible = option.programId == selectedProgram &&
                    option.semester == selectedSemester
                binding.root.setOnClickListener { onClick(option) }
            }
        }
    }

    companion object {
        const val RESULT_KEY = "recordbook_period_result"
        const val RESULT_PROGRAM_ID = "recordbook_program_id"
        const val RESULT_SEMESTER = "recordbook_semester"
        private const val TAG = "RecordbookPeriodBottomSheet"
        private const val ARG_PROGRAM_NAME = "program_name"
        private const val ARG_PROGRAM_IDS = "program_ids"
        private const val ARG_SEMESTERS = "semesters"
        private const val ARG_COURSES = "courses"
        private const val ARG_YEARS = "years"
        private const val ARG_ACTUAL = "actual"
        private const val ARG_SELECTED_PROGRAM = "selected_program"
        private const val ARG_SELECTED_SEMESTER = "selected_semester"

        fun show(
            manager: FragmentManager,
            programs: List<dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookProgram>,
            selection: RecordbookSelection
        ) {
            val options = programs.flatMap { program ->
                program.periods.map { period ->
                    RecordbookPeriodOption(
                        program.id,
                        period.semester,
                        period.course,
                        period.studyYear,
                        period.actual
                    )
                }
            }
            RecordbookPeriodBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROGRAM_NAME, selection.program.name)
                    putLongArray(ARG_PROGRAM_IDS, options.map { it.programId }.toLongArray())
                    putIntArray(ARG_SEMESTERS, options.map { it.semester }.toIntArray())
                    putIntArray(ARG_COURSES, options.map { it.course }.toIntArray())
                    putStringArrayList(ARG_YEARS, ArrayList(options.map { it.studyYear }))
                    putBooleanArray(ARG_ACTUAL, options.map { it.actual }.toBooleanArray())
                    putLong(ARG_SELECTED_PROGRAM, selection.program.id)
                    putInt(ARG_SELECTED_SEMESTER, selection.period.semester)
                }
            }.show(manager, TAG)
        }
    }
}
