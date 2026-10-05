package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil3.dispose
import coil3.load
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserSummary

class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val image: ImageView
    private val text: TextView
    private var bindingVersion = 0L

    init {
        LayoutInflater.from(context).inflate(R.layout.view_avatar, this, true)
        image = findViewById(R.id.avatar_image)
        text = findViewById(R.id.avatar_text)

        background = ContextCompat.getDrawable(context, R.drawable.bg_avatar_circle)
        clipToOutline = true
    }

    fun setUser(user: UserSummary?) {
        setUser(user?.name, user?.pictureUrl)
    }

    fun setUser(name: String?, pictureUrl: String?) {
        val version = ++bindingVersion
        text.text = name?.let(::initials)
        if (name == null) {
            image.dispose()
            image.visibility = GONE
            text.visibility = GONE
            return
        }

        if (!pictureUrl.isNullOrBlank()) {
            image.visibility = VISIBLE
            text.visibility = GONE

            image.load(pictureUrl) {
                transformations(CircleCropTransformation())
                // A recycled view may already show another user; only the latest binding may switch the views.
                listener(
                    onError = { _, _ ->
                        if (bindingVersion == version) {
                            image.visibility = GONE
                            text.visibility = VISIBLE
                        }
                    },
                    onSuccess = { _, _ ->
                        if (bindingVersion == version) {
                            image.visibility = VISIBLE
                            text.visibility = GONE
                        }
                    },
                )
            }
        } else {
            image.dispose()
            image.visibility = GONE
            text.visibility = VISIBLE
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val size = minOf(w, h)

        val textSizePx = size * 0.4f

        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
    }

    private fun initials(name: String): String {
        val parts = name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts.first().take(1).uppercase()
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }
}
