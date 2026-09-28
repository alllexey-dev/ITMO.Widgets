package dev.alllexey.itmowidgets.feature.social.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.alignRailIcon
import dev.alllexey.itmowidgets.databinding.ItemProfileFactBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileFactsBinding
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFact
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFactKind

fun ItemProfileFactsBinding.bindFacts(items: List<ProfileFact>) {
    facts.removeAllViews()
    val inflater = LayoutInflater.from(root.context)
    items.forEachIndexed { index, fact ->
        if (index > 0) facts.addView(facts.divider())
        val row = ItemProfileFactBinding.inflate(inflater, facts, false)
        row.bindFact(fact)
        facts.addView(row.root)
    }
}

private fun ItemProfileFactBinding.bindFact(fact: ProfileFact) {
    val context = root.context
    val (icon, category) = when (fact.kind) {
        ProfileFactKind.POSITION -> R.drawable.ic_work to R.string.person_fact_position
        ProfileFactKind.ROOM -> R.drawable.ic_location_on_rounded to R.string.person_fact_room
        ProfileFactKind.EDUCATION -> R.drawable.ic_school to R.string.person_fact_education
    }
    factIcon.setImageResource(icon)
    factTitle.text = fact.title
    factTitle.contentDescription = context.getString(
        R.string.sport_detail_fact_description, context.getString(category), fact.title
    )
    val subtitle = if (fact.kind == ProfileFactKind.EDUCATION) {
        listOfNotNull(fact.course?.let { context.getString(R.string.person_course, it) }, fact.detail)
            .joinToString(" · ").ifEmpty { null }
    } else {
        fact.detail
    }
    factSubtitle.text = subtitle
    factSubtitle.isVisible = !subtitle.isNullOrEmpty()
    alignRailIcon(factIcon, factTitle)
}

private fun LinearLayout.divider(): View = View(context, null, 0, R.style.Widget_ItmoWidgets_CompactSettingsDivider).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.design_card_stroke)).apply {
        marginStart = (56 * resources.displayMetrics.density).toInt()
        marginEnd = resources.getDimensionPixelSize(R.dimen.design_spacing_group)
    }
}
