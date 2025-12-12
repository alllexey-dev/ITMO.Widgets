package dev.alllexey.itmowidgets.ui.onboarding

import android.os.Build
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = if (Build.VERSION.SDK_INT >= 33) 5 else 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WelcomeFragment()
            1 -> ScheduleSetupFragment()
            2 -> QrSetupFragment()
            3 -> ServicesFragment()
            4 -> NotificationPermissionFragment()
            else -> throw IllegalArgumentException()
        }
    }
}