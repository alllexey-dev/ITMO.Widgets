package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserData

class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val image: ImageView
    private val text: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_avatar, this, true)
        image = findViewById(R.id.avatar_image)
        text = findViewById(R.id.avatar_text)

        background = ContextCompat.getDrawable(context, R.drawable.bg_avatar_circle)
        clipToOutline = true
    }

    fun setUser(user: UserData?) {
        setUser(user?.name, user?.pictureUrl)
    }

    fun setUser(name: String?, pictureUrl: String?) {
        if (name == null) {
            Glide.with(this).clear(image)
            image.visibility = GONE
            text.visibility = GONE
            return
        }

        if (!pictureUrl.isNullOrBlank()) {
            image.visibility = VISIBLE
            text.visibility = GONE

            Glide.with(this)
                .load(pictureUrl)
                .circleCrop()
                .into(image)
        } else {
            Glide.with(this).clear(image)
            image.visibility = GONE
            text.visibility = VISIBLE
            text.text = initials(name)
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
