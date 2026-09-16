package dev.alllexey.itmowidgets.feature.home.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.databinding.FragmentHomeBinding
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen

@AndroidEntryPoint
class HomeFragment : Fragment() {

    // region Binding

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // endregion

    // region Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.qrFab.setOnClickListener { openScreen(AppScreen.QR_PASS) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // endregion

}
