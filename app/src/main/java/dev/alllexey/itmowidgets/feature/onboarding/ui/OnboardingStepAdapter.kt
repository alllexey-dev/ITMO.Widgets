package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind

/** Pages follow the ViewModel's step list, which grows by one when the opt-in is switched on. */
class OnboardingStepAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private var steps: List<OnboardingStep> = emptyList()

    @Suppress("NotifyDataSetChanged")
    fun submit(steps: List<OnboardingStep>) {
        if (steps == this.steps) return
        this.steps = steps
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = steps.size

    override fun getItemId(position: Int): Long = steps[position].ordinal.toLong()

    override fun containsItem(itemId: Long): Boolean = steps.any { it.ordinal.toLong() == itemId }

    override fun createFragment(position: Int): Fragment = when (steps[position]) {
        OnboardingStep.COMPACT_WIDGET -> WidgetStepFragment.newInstance(WidgetKind.SINGLE_LESSON)
        OnboardingStep.FULL_WIDGET -> WidgetStepFragment.newInstance(WidgetKind.DAY_SCHEDULE)
        OnboardingStep.QR_WIDGET -> WidgetStepFragment.newInstance(WidgetKind.QR)
        OnboardingStep.SERVICES -> ServicesStepFragment()
        OnboardingStep.NOTIFICATIONS -> NotificationsStepFragment()
    }
}
