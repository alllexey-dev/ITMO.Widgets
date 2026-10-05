package dev.alllexey.itmowidgets.feature.auth.reference

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthViewModel
import dev.alllexey.itmowidgets.feature.auth.ui.AuthFragment
import dev.alllexey.itmowidgets.feature.auth.ui.LoginActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Today's sign-in screens under the names of LA-5's previews, in `shared/feature-account/screenshots/`:
 * `AuthScreen` over an in-memory session (no token reaches anything), and only the error state of `LoginScreen`,
 * since a WebView does not render under Robolectric.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AuthReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-account")

    @Test
    fun initial() = auth("AuthScreen_initial", Session(SessionState.SignedOut))

    @Test
    fun reauthentication() = auth("AuthScreen_reauthentication", Session(SessionState.ReauthenticationRequired))

    @Test
    fun signingIn() = auth("AuthScreen_signing-in", Session(SessionState.SignedOut)) {
        it.signInWithRefreshToken(SYNTHETIC_TOKEN)
    }

    @Test
    fun error() = auth(
        "AuthScreen_error",
        Session(SessionState.SignedOut, CompletableDeferred(AppResult.Failure(AppError.Unauthorized))),
    ) { it.signInWithRefreshToken(SYNTHETIC_TOKEN) }

    @Test
    fun tokenDialog() {
        var dialog: Dialog? = null
        references.host(
            "AuthScreen_token-dialog",
            ReferenceHostActivity::class.java,
            appearance = {},
            ready = { host ->
                if (host.supportFragmentManager.fragments.none { it is AuthFragment }) {
                    val fragment = show(host, Session(SessionState.SignedOut))
                    fragment.requireView().findViewById<View>(R.id.refresh_token_login_button).performClick()
                    dialog = ShadowDialog.getLatestDialog()
                }
                dialog?.isShowing == true
            },
            view = { it.mirror(checkNotNull(dialog?.window).decorView) },
        )
    }

    @Test
    fun loginError() = references.host(
        "LoginScreen_error",
        LoginActivity::class.java,
        appearance = {},
        ready = { activity ->
            val browser = activity.findViewById<WebView>(R.id.login_web_view)
            val error = activity.findViewById<View>(R.id.login_error_container)
            // A main frame that is not HTTPS is the page error the activity shows instead of the browser.
            if (!error.isVisible) shadowOf(browser).webViewClient.onPageStarted(browser, OUTSIDE_URL, null)
            error.isVisible
        },
    )

    private fun auth(preview: String, session: Session, act: (AuthViewModel) -> Unit = {}) = references.host(
        preview,
        ReferenceHostActivity::class.java,
        appearance = {},
        ready = { host ->
            host.supportFragmentManager.fragments.any { it is AuthFragment } || run {
                show(host, session, act)
                true
            }
        },
        view = { it.content },
    )

    private fun show(host: ReferenceHostActivity, session: Session, act: (AuthViewModel) -> Unit = {}): AuthFragment {
        val fragment = AuthFragment()
        // A fresh session per launch: every appearance starts from the same state.
        host.supportFragmentManager.preset(fragment) { AuthViewModel(session.copy()).also(act) }
        host.show(fragment)
        return fragment
    }

    /** A session in [initial]; a refresh-token sign-in answers with [signIn], which never completes by default. */
    private data class Session(
        val initial: SessionState,
        val signIn: CompletableDeferred<AppResult<Unit>> = CompletableDeferred(),
    ) : SessionRepository {
        override val state = MutableStateFlow(initial)
        override suspend fun initialize() = Unit
        override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> = signIn.await()
        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> = signIn.await()
        override suspend fun startDemo() = Unit
        override suspend fun signOut() = Unit
    }

    private companion object {
        const val SYNTHETIC_TOKEN = "synthetic-refresh-token"
        const val OUTSIDE_URL = "http://example.com/"
    }
}

/** Hands [fragment] the view model [create] makes before Hilt could create one. */
private fun FragmentManager.preset(fragment: Fragment, create: () -> ViewModel) =
    registerFragmentLifecycleCallbacks(
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
                if (f !== fragment) return
                val model = create()
                ViewModelProvider(
                    f,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
                    },
                )[model.javaClass]
            }
        },
        false,
    )

/**
 * Shows [source], a view of a dialog window, in place of the host's content: a capture finds only views of the
 * activity's window and includes what lies under a transparent view, so the screen behind the dialog is hidden.
 */
private fun ReferenceHostActivity.mirror(source: View): View {
    for (index in 0 until container.childCount) container.getChildAt(index).visibility = View.INVISIBLE
    return WindowMirror(this, source).also(::show)
}

/** Draws [source] at its own size. */
private class WindowMirror(context: Context, private val source: View) : View(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(source.width, source.height)

    override fun onDraw(canvas: Canvas) = source.draw(canvas)
}
