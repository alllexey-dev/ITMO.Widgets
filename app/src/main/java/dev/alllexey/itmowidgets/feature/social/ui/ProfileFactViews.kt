package dev.alllexey.itmowidgets.feature.social.ui

import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.GroupPosition
import dev.alllexey.itmowidgets.core.ui.bindGroupPosition
import dev.alllexey.itmowidgets.databinding.ItemProfileFactBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileFactsBinding
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFact
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFactKind

/** The heading of a facts section: «Должности», «Где найти» or «Учёба». */
@StringRes
fun ProfileFactKind.sectionTitle(): Int = when (this) {
    ProfileFactKind.POSITION -> R.string.person_section_positions
    ProfileFactKind.ROOM -> R.string.person_section_rooms
    ProfileFactKind.EDUCATION -> R.string.person_section_education
}

/** One section: its heading and the facts of one kind as a connected group of informational rows. */
fun ItemProfileFactsBinding.bindFacts(kind: ProfileFactKind, items: List<ProfileFact>) {
    heading.title.setText(kind.sectionTitle())
    facts.removeAllViews()
    val inflater = LayoutInflater.from(root.context)
    items.forEachIndexed { index, fact ->
        val row = ItemProfileFactBinding.inflate(inflater, facts, false)
        facts.addView(row.root)
        row.bindFact(fact)
        row.root.bindGroupPosition(GroupPosition.of(index, items.size))
    }
}

private fun ItemProfileFactBinding.bindFact(fact: ProfileFact) {
    val context = root.context
    val (icon, category) = when (fact.kind) {
        ProfileFactKind.POSITION -> R.drawable.ic_work to R.string.person_fact_position
        ProfileFactKind.ROOM -> R.drawable.ic_location_on to R.string.person_fact_room
        ProfileFactKind.EDUCATION -> R.drawable.ic_school to R.string.person_fact_education
    }
    factIcon.setImageResource(icon)
    factTitle.text = fact.title
    factTitle.contentDescription = context.getString(
        R.string.sport_detail_fact_description, context.getString(category), fact.title
    )
    val subtitle = if (fact.kind == ProfileFactKind.EDUCATION) {
        listOfNotNull(fact.course?.let { context.getString(R.string.person_course, it) }, fact.detail)
            .joinToString(", ").ifEmpty { null }
    } else {
        fact.detail
    }
    factSubtitle.text = subtitle
    factSubtitle.isVisible = !subtitle.isNullOrEmpty()
}
