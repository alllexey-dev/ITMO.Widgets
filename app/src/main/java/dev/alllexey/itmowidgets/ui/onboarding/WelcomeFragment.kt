package dev.alllexey.itmowidgets.ui.onboarding

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.login.LoginActivity

class WelcomeFragment : Fragment(R.layout.fragment_onboarding_welcome) {

    private lateinit var btnLogin: Button

    private lateinit var statusText: TextView

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            (activity as? OnboardingActivity)?.nextPage()
            val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
            updateAuthStatus(!appContainer.myItmo.isRefreshTokenExpired, statusText, btnLogin)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
        btnLogin = view.findViewById(R.id.btn_login)
        statusText = view.findViewById(R.id.auth_status_text)

        updateAuthStatus(!appContainer.myItmo.isRefreshTokenExpired, statusText, btnLogin)

        btnLogin.setOnClickListener {
            val intent = Intent(requireContext(), LoginActivity::class.java)
            loginLauncher.launch(intent)
        }
    }

    private fun updateAuthStatus(isValid: Boolean, text: TextView, btn: Button) {
        if (isValid) {
            text.text = "Вы успешно авторизованы"
            text.setTextColor(requireContext().getColor(R.color.free_sport_color))
            btn.text = "Войти заново"
        } else {
            text.text = "Для работы виджетов необходима авторизация"
            text.setTextColor(requireContext().getColor(R.color.subtext_color))
            btn.text = "Войти через ITMO.ID"
        }
    }
}