package com.victorfalcon.dose.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.domain.model.DoseView
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val OCCURRENCE_ID = ActionParameters.Key<Long>("occurrenceId")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun repository(context: Context) =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()

/** Home-screen widget: today's remaining doses with a quick "taken" action. */
class DoseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val remaining = repository(context)
            .observeDosesBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
            .first()
            .filter { it.status == DoseStatus.PENDING }
        provideContent { GlanceTheme { WidgetBody(remaining) } }
    }
}

@Composable
private fun WidgetBody(doses: List<DoseView>) {
    val context = LocalContext.current
    val onSurface = GlanceTheme.colors.onSurface
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        Text(
            context.getString(R.string.nav_today),
            style = TextStyle(fontWeight = FontWeight.Bold, color = onSurface),
        )
        Spacer(GlanceModifier.height(8.dp))
        if (doses.isEmpty()) {
            Text(context.getString(R.string.widget_empty), style = TextStyle(color = onSurface))
        } else {
            doses.take(6).forEach { dose -> DoseRow(dose, onSurface) }
        }
    }
}

@Composable
private fun DoseRow(dose: DoseView, color: ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${dose.scheduledAt.format(timeFormatter)}  ${dose.name}",
            style = TextStyle(color = color),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Button(
            text = LocalContext.current.getString(R.string.dose_taken),
            onClick = actionRunCallback<MarkTakenAction>(actionParametersOf(OCCURRENCE_ID to dose.occurrenceId)),
        )
    }
}

/** Marks the tapped dose taken from the widget, then refreshes it. */
class MarkTakenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[OCCURRENCE_ID] ?: return
        repository(context).setOccurrenceStatus(id, DoseStatus.TAKEN, LocalDateTime.now())
        DoseWidget().update(context, glanceId)
    }
}
