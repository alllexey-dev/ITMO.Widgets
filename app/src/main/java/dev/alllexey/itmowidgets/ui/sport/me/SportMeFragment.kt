package dev.alllexey.itmowidgets.ui.sport.me

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.misc.CircularProgressBar

class SportMeFragment : Fragment(R.layout.fragment_sport_me) {


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progressCircleView = view.findViewById<View>(R.id.progressCircle)
        val progressBar =
            progressCircleView.findViewById<CircularProgressBar>(R.id.circularProgressBar)
        val progressTextView = progressCircleView.findViewById<TextView>(R.id.progressTextView)

        val initialSectors = listOf(
            CircularProgressBar.Sector(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.red_sport_color
                ), 0F
            ),
            CircularProgressBar.Sector(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.blue_sport_color
                ), 0F
            )
        )
        progressBar.setSectors(initialSectors)
        progressTextView.text = "80"

        val newSectors = listOf(
            CircularProgressBar.Sector(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.red_sport_color
                ), 40F
            ),
            CircularProgressBar.Sector(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.blue_sport_color
                ), 40F
            )
        )

        progressBar.animateSectors(newSectors, 800L)
    }

}