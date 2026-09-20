package dev.alllexey.itmowidgets.feature.schedule.ui.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.MapLauncher
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentLessonDetailsBinding
import dev.alllexey.itmowidgets.databinding.ItemLessonFriendBinding
import dev.alllexey.itmowidgets.databinding.ItemSportDetailFactBinding
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonFriendsState
import dev.alllexey.itmowidgets.feature.schedule.ui.colorRes
import dev.alllexey.itmowidgets.feature.schedule.ui.nameRes
import dev.alllexey.itmowidgets.feature.schedule.ui.shortTitle
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * One lesson occurrence: what the schedule card shows, in full, plus the map
 * hand-off and the viewer's friends on the same lesson.
 */
@AndroidEntryPoint
class LessonDetailsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentLessonDetailsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var buildings: BuildingDirectory

    private val viewModel: LessonDetailsViewModel by viewModels()

    private val lesson: LessonDetailsArgs by lazy {
        requireNotNull(requireArguments().serializable(ARG_LESSON, LessonDetailsArgs::class.java))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLessonDetailsBinding.inflate(inflater, container, false)
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
        subjectName.text = lesson.subjectName.ifBlank { getString(R.string.schedule_unknown_subject) }
        val typeId = Lesson.TypeId(lesson.typeId)
        typeIndicator.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), typeId.colorRes()))
        lessonKind.text = listOf(getString(typeId.nameRes()), lesson.format).filter { it.isNotBlank() }.joinToString(" · ")

        val date = LocalDate.parse(lesson.date)
        val start = LocalTime.parse(lesson.start)
        val end = LocalTime.parse(lesson.end)
        timeFact.bind(
            R.string.schedule_lesson_details_time,
            getString(R.string.schedule_lesson_details_time_value, date.format(DATE_FORMATTER), start.format(TIME_FORMATTER), end.format(TIME_FORMATTER)),
            R.drawable.ic_schedule_rounded
        )
        teacherFact.bind(R.string.schedule_lesson_details_teacher, lesson.teacherFio.orEmpty(), R.drawable.ic_person_rounded)
        locationFact.bind(R.string.schedule_lesson_details_location, locationText(), R.drawable.ic_location_on_rounded)
        zoomFact.bind(R.string.schedule_lesson_details_zoom, listOfNotNull(lesson.zoomInfo, lesson.zoomPassword?.let {
            getString(R.string.schedule_lesson_details_zoom_password, it)
        }).joinToString("\n"), R.drawable.ic_globe)

        val destination = mapDestination()
        mapButton.isVisible = destination != null
        mapButton.setOnClickListener { destination?.let(::openMap) }
        zoomButton.isVisible = lesson.zoomUrl != null
        zoomButton.setOnClickListener { lesson.zoomUrl?.let(::openLink) }
        actions.isVisible = mapButton.isVisible || zoomButton.isVisible

        noteCard.isVisible = lesson.note != null
        note.text = lesson.note
        friendsRetry.setOnClickListener { viewModel.retry() }

        viewModel.friends
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::renderFriends)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderFriends(state: LessonFriendsState) = with(binding) {
        friendsCard.isVisible = state != LessonFriendsState.Disabled
        friendsProgress.isVisible = state == LessonFriendsState.Loading
        friendsRetry.isVisible = state is LessonFriendsState.Error
        friendsMessage.isVisible = state is LessonFriendsState.Error ||
            (state is LessonFriendsState.Content && state.friends.isEmpty())
        friendsMessage.text = when (state) {
            is LessonFriendsState.Error -> getString(state.error.messageRes())
            is LessonFriendsState.Content -> getString(R.string.schedule_lesson_friends_none)
            else -> null
        }
        friendsTitle.text = if (state is LessonFriendsState.Content && state.friends.isNotEmpty()) {
            getString(R.string.schedule_lesson_friends_count, state.friends.size)
        } else {
            getString(R.string.schedule_lesson_friends_title)
        }
        friendsContainer.removeAllViews()
        (state as? LessonFriendsState.Content)?.friends?.forEach { friend -> friendsContainer.addView(friendRow(friend)) }
    }

    private fun friendRow(friend: UserSummary): View {
        val row = ItemLessonFriendBinding.inflate(layoutInflater, binding.friendsContainer, false)
        row.friendAvatar.setUser(friend.name, friend.pictureUrl)
        row.friendName.text = friend.name.ifBlank { getString(R.string.user_name_placeholder, friend.isu) }
        val group = friend.groups.firstOrNull()?.name
        row.friendGroup.isVisible = !group.isNullOrBlank()
        row.friendGroup.text = group
        row.root.setOnClickListener { openFriendProfile(friend.isu) }
        return row.root
    }

    /** The profile is a contextual screen above the tabs; the sheet has nothing to add once it opens. */
    private fun openFriendProfile(isu: Int) {
        dismiss()
        openScreen(AppScreen.USER_PROFILE, bundleOf(UserScreenArgs.ISU to isu))
    }

    private fun locationText(): String {
        val room = lesson.room?.let { Room(it).shortTitle(requireContext()) }
        return listOfNotNull(room, lesson.building).joinToString(" · ")
    }

    private fun mapDestination(): MapDestination? {
        buildings.find(lesson.buildingId, lesson.mainBuildingId, lesson.building)?.let { return it.toMapDestination() }
        val building = lesson.building ?: return null
        return MapDestination(label = Building(building).shortTitle(requireContext()), address = building)
    }

    private fun openMap(destination: MapDestination) {
        if (!MapLauncher.open(requireContext(), destination)) {
            Snackbar.make(binding.root, R.string.schedule_map_unavailable, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    /** The icon carries the category, so [title] only survives for screen readers. */
    private fun ItemSportDetailFactBinding.bind(title: Int, value: String, icon: Int) {
        root.isVisible = value.isNotBlank()
        factValue.text = value
        factValue.contentDescription = getString(R.string.sport_detail_fact_description, getString(title), value)
        factIcon.setImageResource(icon)
        alignRailIcon(factIcon, factValue)
    }

    /** Centres a fixed-dp rail icon on the first text line at any font scale. */
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
        const val TAG = "LessonDetailsBottomSheet"
        private const val ARG_LESSON = "arg_lesson"
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))

        fun newInstance(lesson: Lesson, date: LocalDate): LessonDetailsBottomSheet = newInstance(lesson.toDetailsArgs(date))

        fun newInstance(args: LessonDetailsArgs): LessonDetailsBottomSheet = LessonDetailsBottomSheet().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_LESSON, args)
                putLong(LessonDetailsViewModel.ARG_PAIR_ID, args.pairId)
                putString(LessonDetailsViewModel.ARG_DATE, args.date)
            }
        }

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        private fun <T : Serializable> Bundle.serializable(key: String, type: Class<T>): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getSerializable(key, type)
            else getSerializable(key) as? T
    }
}
