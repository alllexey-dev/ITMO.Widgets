package dev.alllexey.itmowidgets.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.alllexey.itmowidgets.R

class SettingsFragment : Fragment(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_host, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)

        val callback = object : OnBackPressedCallback(
            childFragmentManager.backStackEntryCount > 0
        ) {
            override fun handleOnBackPressed() {
                childFragmentManager.popBackStack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        childFragmentManager.addOnBackStackChangedListener {
            callback.isEnabled = childFragmentManager.backStackEntryCount > 0
            updateToolbar(toolbar)
        }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_fragment_container, RootSettingsFragment())
                .commit()
        }

        updateToolbar(toolbar)
    }

    private fun updateToolbar(toolbar: Toolbar) {
        if (childFragmentManager.backStackEntryCount > 0) {
            val currentFragment = childFragmentManager.fragments.last() as PreferenceFragmentCompat
            toolbar.title = currentFragment.preferenceScreen.title
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            toolbar.setNavigationOnClickListener {
                childFragmentManager.popBackStack()
            }
        } else {
            toolbar.title = "Настройки"
            toolbar.setNavigationIcon(R.drawable.ic_settings_gray)
            toolbar.setNavigationOnClickListener {}
        }
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val args = pref.extras
        val fragment = childFragmentManager.fragmentFactory.instantiate(
            requireActivity().classLoader,
            pref.fragment!!
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.settings_fragment_container, fragment)
            .addToBackStack(null)
            .commit()

        return true
    }
}