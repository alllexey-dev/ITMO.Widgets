package dev.alllexey.itmowidgets.ui.onboarding

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.appContainer
import dev.alllexey.itmowidgets.util.getColorFromAttr

class ServicesFragment : Fragment(R.layout.fragment_onboarding_services) {

    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var statusIcon: ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
        val settings = appContainer.storage.settings

        statusCard = view.findViewById(R.id.card_auth_status)
        statusText = view.findViewById(R.id.text_auth_status)
        statusIcon = view.findViewById(R.id.icon_auth_status)

        val switchServices = view.findViewById<MaterialSwitch>(R.id.switch_custom_services)
        val featuresText = view.findViewById<TextView>(R.id.text_services_features)

        val isAuth = isAuthenticatedBlocking()
        updateAuthUI(isAuth)

        switchServices.isChecked = settings.getCustomServicesState()
        updateFeaturesUI(switchServices.isChecked, featuresText)

        switchServices.setOnCheckedChangeListener { _, isChecked ->
            settings.setCustomServicesState(isChecked)
            if (!isChecked) appContainer.storage.itmoWidgets.clearTokens()
            updateFeaturesUI(isChecked, featuresText)
            updateAuthUI(isAuthenticatedBlocking())
        }
    }

    fun isAuthenticatedBlocking(): Boolean {
        return try {
            requireContext().appContainer().itmoWidgets.getValidTokens()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun updateAuthUI(isAuth: Boolean) {
        val context = requireContext()

        if (isAuth) {
            val bgColor = context.getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer)
            val contentColor = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)

            statusCard.setCardBackgroundColor(bgColor)
            statusText.text = "Вы успешно авторизованы"
            statusText.setTextColor(contentColor)

            statusIcon.setImageResource(R.drawable.ic_check)
            statusIcon.imageTintList = ColorStateList.valueOf(contentColor)
        } else {
            val bgColor = context.getColorFromAttr(com.google.android.material.R.attr.colorErrorContainer)
            val contentColor = context.getColorFromAttr(com.google.android.material.R.attr.colorOnErrorContainer)

            statusCard.setCardBackgroundColor(bgColor)
            statusText.text = "Авторизация не выполнена"
            statusText.setTextColor(contentColor)

            statusIcon.setImageResource(R.drawable.ic_error)
            statusIcon.imageTintList = ColorStateList.valueOf(contentColor)
        }
    }

    private fun updateFeaturesUI(isEnabled: Boolean, textView: TextView) {
        if (isEnabled) {
            textView.alpha = 1.0f
        } else {
            textView.alpha = 0.38f
        }
    }
}