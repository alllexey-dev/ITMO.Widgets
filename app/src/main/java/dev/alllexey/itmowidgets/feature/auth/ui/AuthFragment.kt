package dev.alllexey.itmowidgets.feature.auth.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The sign-in screen (`auth`, the main graph's start), kept by name. The screen is `AuthRoute` from
 * `:shared:feature-account`; its ViewModel is Koin's, in this Fragment's store. This host keeps what only Android
 * does: [LoginActivity] for a result, and the haptic and toast that confirm the hidden demo entry.
 */
@AndroidEntryPoint
class AuthFragment : Fragment() {

    // The same instance AuthRoute's koinViewModel() finds in this Fragment's store.
    private val viewModel: AuthViewModel by viewModel()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.clearError()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            AuthRoute(
                onSignInWithItmoId = { loginLauncher.launch(Intent(requireContext(), LoginActivity::class.java)) },
                onDemoStarted = ::confirmDemo,
                viewModel = viewModel,
            )
        }

    private fun confirmDemo(message: String) = confirmDemoEntry(requireContext(), view, message)
}

/**
 * Confirms the hidden demo entry the Android way: a haptic on [view] and a toast with [message]. Both hosts of
 * `AuthRoute` call it (this Fragment and the Compose shell's auth entry).
 */
fun confirmDemoEntry(context: Context, view: View?, message: String) {
    val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    view?.performHapticFeedback(confirm)
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
}
