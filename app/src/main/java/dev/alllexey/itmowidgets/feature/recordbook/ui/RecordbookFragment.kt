package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.databinding.FragmentRecordbookBinding

@AndroidEntryPoint
class RecordbookFragment : Fragment() {

    // region Binding

    private var _binding: FragmentRecordbookBinding? = null
    private val binding get() = _binding!!

    // endregion

    // region Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordbookBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // endregion

}
