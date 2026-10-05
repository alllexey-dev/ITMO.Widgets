package dev.alllexey.itmowidgets.feature.weblogin.reference

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginUiState
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import dev.alllexey.itmowidgets.feature.weblogin.ui.WebLoginBottomSheet
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.google.android.material.R as MaterialR

/**
 * Today's web sign-in sheet under the names of LA-6's `WebLoginSheetContent` previews, in
 * `shared/feature-account/screenshots/`: the real sheet in its dialog, captured without the scrim, over synthetic
 * answers as `WebLoginPreviewActivity` gives them (nothing reaches Backend, Moscow time).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WebLoginReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-account")

    @Test
    fun inputError() = sheet("WebLoginSheetContent_input-error", { it is WebLoginUiState.Input }) {
        onCodeChanged("ABC")
        submit()
    }

    @Test
    fun checking() = sheet(
        "WebLoginSheetContent_checking",
        { it is WebLoginUiState.Checking },
        Answers(preview = CompletableDeferred()),
    ) { enter() }

    @Test
    fun confirm() = sheet("WebLoginSheetContent_confirm", { it is WebLoginUiState.Confirm }) { enter() }

    @Test
    fun approving() = sheet(
        "WebLoginSheetContent_approving",
        { it is WebLoginUiState.Confirm && it.approving },
        Answers(approval = CompletableDeferred()),
    ) {
        enter()
        approve()
    }

    @Test
    fun done() = sheet("WebLoginSheetContent_done", { it == WebLoginUiState.Done }) {
        enter()
        approve()
    }

    @Test
    fun error() = sheet(
        "WebLoginSheetContent_error",
        { it is WebLoginUiState.Error },
        Answers(approval = CompletableDeferred(AppResult.Failure(AppError.Network))),
    ) {
        enter()
        approve()
    }

    /**
     * Shows the sheet once per launch over a view model that [act] has driven, then waits for [state]. Repository
     * answers complete synchronously, so [act] reaches the next step before the sheet draws.
     */
    private fun sheet(
        preview: String,
        state: (WebLoginUiState) -> Boolean,
        answers: Answers = Answers(),
        act: WebLoginViewModel.() -> Unit,
    ) {
        var sheet: WebLoginBottomSheet? = null
        var model: WebLoginViewModel? = null
        references.host(
            preview,
            ReferenceHostActivity::class.java,
            appearance = {},
            ready = { host ->
                if (host.supportFragmentManager.findFragmentByTag(WebLoginBottomSheet.TAG) == null) {
                    val fragment = WebLoginBottomSheet()
                    host.supportFragmentManager.preset(fragment) {
                        WebLoginViewModel(SavedStateHandle(), answers, FixedAcademicTime()).also {
                            model = it
                            it.act()
                        }
                    }
                    fragment.show(host.supportFragmentManager, WebLoginBottomSheet.TAG)
                    sheet = fragment
                }
                sheet?.dialog?.isShowing == true && model?.uiState?.value?.let(state) == true
            },
            view = { host ->
                val surface = checkNotNull(sheet?.dialog?.findViewById<View>(MaterialR.id.design_bottom_sheet))
                host.mirror(surface)
            },
        )
    }

    private fun WebLoginViewModel.enter() {
        onCodeChanged(CODE)
        submit()
    }

    /** Synthetic Backend answers: [CODE] finds [SYNTHETIC_PREVIEW]; each answer waits for its deferred. */
    private class Answers(
        val preview: CompletableDeferred<AppResult<WebLoginPreview>> =
            CompletableDeferred(AppResult.Success(SYNTHETIC_PREVIEW)),
        val approval: CompletableDeferred<AppResult<Unit>> = CompletableDeferred(AppResult.Success(Unit)),
    ) : WebLoginRepository {
        override suspend fun preview(code: String): AppResult<WebLoginPreview> =
            if (code == CODE) preview.await() else AppResult.Failure(AppError.NotFound)

        override suspend fun approve(challengeId: Uuid): AppResult<Unit> = approval.await()
    }

    private companion object {
        const val CODE = "ABCD2345"

        /** The browser of `WebLoginPreviewActivity`: Chrome on macOS, requested at 12:04 Moscow time. */
        val SYNTHETIC_PREVIEW = WebLoginPreview(
            challengeId = Uuid.parse("00000000-0000-0000-0000-000000000042"),
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36",
            createdAt = Instant.parse("2026-09-24T09:04:30Z"),
            expiresAt = Instant.parse("2026-09-24T09:06:30Z"),
        )
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
