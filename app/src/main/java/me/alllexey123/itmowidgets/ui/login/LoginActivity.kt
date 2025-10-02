package me.alllexey123.itmowidgets.ui.login

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.databinding.ActivityLoginBinding
import me.alllexey123.itmowidgets.ui.widgets.WidgetUtils

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.buttonLogin.setOnClickListener {
            performLogin()
        }

    }

    private fun performLogin() {
        binding.loginInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.textViewResult.text = null

        val login = binding.editTextLogin.text.toString().trim()
        val password = binding.editTextPassword.text.toString()

        var isValid = true

        if (login.isEmpty()) {
            binding.loginInputLayout.error = "Логин не может быть пустым"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Пароль не может быть пустым"
            isValid = false
        }

        if (!isValid) {
            return
        }

        lifecycleScope.launch {
            try {
                binding.buttonLogin.isEnabled = false
                binding.textViewResult.text = "Выполняется вход..."

                val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
                val authResult = withContext(Dispatchers.IO) {
                    appContainer.myItmo.auth(login, password)
                    "Вход выполнен. Теперь можно использовать виджеты."
                }

                appContainer.scheduleRepository.clearCache()
                appContainer.qrCodeRepository.clearCache()
                WidgetUtils.updateAllWidgets(applicationContext)
                binding.textViewResult.text = authResult

            } catch (e: Exception) {
                if (e.message?.contains("Could not get authorize", ignoreCase = true) ?: false) {
                    binding.textViewResult.text = "Ошибка: проверьте введённые данные"
                } else {
                    binding.textViewResult.text = "Ошибка: ${e.message}"
                }

            } finally {
                binding.buttonLogin.isEnabled = true
            }
        }
    }
}