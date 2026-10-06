package dev.alllexey.itmowidgets.feature.onboarding.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind

/** Synthetic states of the first-run flow, as the LA-1c references drove the View flow. */
internal object OnboardingPreviewSamples {

    /** A fresh install: the stored defaults answered, no custom spoiler image, nothing pinned. */
    fun state(
        step: OnboardingStep,
        servicesEnabled: Boolean = false,
        servicesBusy: Boolean = false,
        pinSupported: Boolean = true,
        notificationsGranted: Boolean = false,
        notificationsAsked: Boolean = false,
    ) = OnboardingUiState(
        step = step,
        pinSupported = pinSupported,
        appearance = WidgetAppearance(),
        servicesEnabled = servicesEnabled,
        servicesBusy = servicesBusy,
        notificationsGranted = notificationsGranted,
        notificationsAsked = notificationsAsked,
        customSpoiler = false,
    )
}

/**
 * A stand-in for the real widget preview, which Android draws with `WidgetPreviewFactory` (RemoteViews) and iOS with
 * its own renderer: a header, then the single lesson at its own height, the day list in the bounded 160 dp area that
 * grows with the text, or the spoiler square.
 */
@Composable
internal fun OnboardingWidgetPreviewSample(kind: WidgetKind, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(ItmoTheme.spacing.compact), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (kind == WidgetKind.QR) "Пример, не пропуск" else "Пример виджета",
                Modifier.weight(1f),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.labelLarge,
            )
            if (kind != WidgetKind.QR) {
                Text("12:50", color = ItmoTheme.colorScheme.primary, style = ItmoTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(ItmoTheme.spacing.compact))
        when (kind) {
            WidgetKind.SINGLE_LESSON -> WidgetFrame(Modifier.fillMaxWidth()) {
                Lesson("Лабораторная", "13:30–15:00", "Программирование", "1506 Кронва · Иванов И. И.")
            }
            WidgetKind.DAY_SCHEDULE -> {
                val height = with(LocalDensity.current) { 14.sp.toDp() } * (DAY_LIST_HEIGHT / 14f)
                WidgetFrame(Modifier.fillMaxWidth().height(height).clipToBounds()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
                        Lesson("Лекция", "10:00–11:30", "История", "2337 Ломоносова")
                        Lesson("Практика", "11:40–13:10", "Математика", "1405 Кронва · Иванов И. И.")
                        Lesson("Лабораторная", "13:30–15:00", "Программирование", "1506 Кронва")
                    }
                }
            }
            WidgetKind.QR -> Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(128.dp)
                    .background(ItmoTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(ItmoTheme.spacing.content)
                    .background(ItmoTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun WidgetFrame(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(ItmoTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .border(1.dp, ItmoTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.content),
    ) { content() }
}

@Composable
private fun Lesson(type: String, time: String, subject: String, place: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(ItmoTheme.colorScheme.tertiary, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(ItmoTheme.spacing.compact))
            Text(type, Modifier.weight(1f), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
            Text(time, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodySmall)
        }
        Text(subject, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(place, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
    }
}

/** `ScheduleSettingsPreview`'s bounded day list, scaled with the text like the real one. */
private const val DAY_LIST_HEIGHT = 160f
