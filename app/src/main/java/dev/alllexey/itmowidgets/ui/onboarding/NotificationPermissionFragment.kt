package dev.alllexey.itmowidgets.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import dev.alllexey.itmowidgets.R

class NotificationPermissionFragment : Fragment(R.layout.fragment_onboarding_notifications) {

    private lateinit var btnAllow: MaterialButton

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        updateUiState(isGranted)
        if (isGranted) {
            (activity as? OnboardingActivity)?.nextPage()
        } else {
            Toast.makeText(requireContext(), "Уведомления можно включить позже в настройках", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnAllow = view.findViewById(R.id.btn_allow_notifications)

        val isGranted = checkPermissionStatus()
        updateUiState(isGranted)

        btnAllow.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(requireContext(), "На вашей версии Android уведомления включены по умолчанию", Toast.LENGTH_SHORT).show()
                updateUiState(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUiState(checkPermissionStatus())
    }

    private fun checkPermissionStatus(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updateUiState(isGranted: Boolean) {
        if (isGranted) {
            btnAllow.text = "Разрешено"
            btnAllow.isEnabled = false
            btnAllow.setIconResource(R.drawable.ic_check)
        } else {
            btnAllow.text = "Разрешить уведомления"
            btnAllow.isEnabled = true
            btnAllow.icon = null
        }
    }
}