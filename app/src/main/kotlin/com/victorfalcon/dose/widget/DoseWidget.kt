package com.victorfalcon.dose.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.victorfalcon.dose.MainActivity
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val OCCURRENCE_ID = ActionParameters.Key<Long>("occurrenceId")
private val DOSES_JSON = stringPreferencesKey("doses_json")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val json = Json { ignoreUnknownKeys = true }

/** Everything the widget needs to render/toggle one dose, pre-formatted so it can live in state. */
@Serializable
private data class WidgetDose(val id: Long, val name: String, val time: String, val taken: Boolean)

private fun repository(context: Context) =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()

/** Reads today's doses from the DB and encodes the widget snapshot. */
private suspend fun buildSnapshot(context: Context): String {
    val today = LocalDate.now()
    val doses = repository(context)
        .observeDosesBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), activeOnly = true)
        .first()
        .filter { it.status == DoseStatus.PENDING || it.status == DoseStatus.TAKEN }
        .sortedBy { it.scheduledAt }
        .map { WidgetDose(it.occurrenceId, it.name, it.scheduledAt.toLocalTime().format(timeFormatter), it.status == DoseStatus.TAKEN) }
    return json.encodeToString(doses)
}

/** Pushes the DB truth into every widget's state and repaints. Called after any dose write. */
suspend fun refreshWidgetState(context: Context) {
    val encoded = buildSnapshot(context)
    GlanceAppWidgetManager(context).getGlanceIds(DoseWidget::class.java).forEach { id ->
        updateAppWidgetState(context, id) { it[DOSES_JSON] = encoded }
    }
    DoseWidget().updateAll(context)
}

/**
 * Home-screen widget: today's doses (pending + taken) with a quick take/undo toggle.
 *
 * Rendered from Glance state, not from a live DB read: update()/updateAll() don't restart
 * provideGlance, so a captured DB list would never repaint. State changes, on the other hand,
 * reliably recompose — which is also what lets the toggle update optimistically (see the action).
 */
class DoseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val seeded = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[DOSES_JSON]
            ?: buildSnapshot(context).also { encoded ->
                updateAppWidgetState(context, id) { it[DOSES_JSON] = encoded }
            }
        provideContent {
            // currentState is reactive to update(); `seeded` covers the very first paint.
            val encoded = currentState(DOSES_JSON) ?: seeded
            val doses = runCatching { json.decodeFromString<List<WidgetDose>>(encoded) }.getOrDefault(emptyList())
            GlanceTheme { WidgetBody(doses) }
        }
    }
}

@Composable
private fun WidgetBody(doses: List<WidgetDose>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        val taken = doses.count { it.taken }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.nav_today),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (doses.isNotEmpty()) {
                Text(
                    "$taken/${doses.size}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (doses.isEmpty()) {
            Text(context.getString(R.string.widget_empty), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(doses) { dose ->
                    Box(GlanceModifier.padding(bottom = 8.dp)) { MedCard(dose) }
                }
            }
        }
    }
}

/** One medication as a filled, rounded row — the widget's take on the Today card. */
@Composable
private fun MedCard(dose: WidgetDose) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(14.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                dose.name,
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, color = GlanceTheme.colors.onSecondaryContainer),
            )
            Text(
                dose.time,
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSecondaryContainer),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        TakeButton(dose)
    }
}

/**
 * The take/undo toggle, matching Today's third column: filled when taken, an outlined ring when
 * pending. Glance renders to RemoteViews, so no shape morph or animation — the tap just swaps
 * state and the widget redraws.
 */
@Composable
private fun TakeButton(dose: WidgetDose) {
    val background = if (dose.taken) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.secondaryContainer
    val icon = if (dose.taken) R.drawable.ic_widget_check else R.drawable.ic_widget_circle
    val tint = if (dose.taken) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.onSecondaryContainer
    Box(
        modifier = GlanceModifier
            .size(30.dp)
            .cornerRadius(20.dp)
            .background(background)
            .clickable(
                actionRunCallback<ToggleTakenAction>(
                    actionParametersOf(OCCURRENCE_ID to dose.id),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(if (dose.taken) 20.dp else 18.dp),
        )
    }
}

/**
 * Optimistic toggle: flip the tapped dose in widget state and repaint *before* touching the DB,
 * so the tap feels instant. The DB write is the source of truth and, via WidgetRefresher, pushes
 * the reconciled snapshot back into state afterwards.
 */
class ToggleTakenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[OCCURRENCE_ID] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            val encoded = prefs[DOSES_JSON] ?: return@updateAppWidgetState
            val flipped = json.decodeFromString<List<WidgetDose>>(encoded)
                .map { if (it.id == id) it.copy(taken = !it.taken) else it }
            prefs[DOSES_JSON] = json.encodeToString(flipped)
        }
        DoseWidget().update(context, glanceId)

        val repository = repository(context)
        val makeTaken = repository.getOccurrence(id)?.status != DoseStatus.TAKEN
        repository.setOccurrenceStatus(id, if (makeTaken) DoseStatus.TAKEN else DoseStatus.PENDING, if (makeTaken) LocalDateTime.now() else null)
    }
}
