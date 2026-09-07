package com.victorfalcon.dose.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.unit.ColorProvider
import com.victorfalcon.dose.MainActivity
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.DoseStatus
import com.victorfalcon.dose.reminder.Reminders
import com.victorfalcon.dose.ui.common.medImageRes
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val OCCURRENCE_ID = ActionParameters.Key<Long>("occurrenceId")
private val HOUR_MINUTES = ActionParameters.Key<Int>("hourMinutes")
private val DOSES_JSON = stringPreferencesKey("doses_json")
private val DOSES_DATE = stringPreferencesKey("doses_date")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val json = Json { ignoreUnknownKeys = true }

/**
 * Green means taken here too, whatever the wallpaper does to the rest of the widget. It comes
 * from a color resource so the platform picks the light/dark value for us.
 */
private val TakenColor = ColorProvider(R.color.dose_taken)

/** Everything the widget needs to render/toggle one dose, pre-formatted so it can live in state. */
@Serializable
internal data class WidgetDose(
    val id: Long,
    val name: String,
    val time: String,
    /** Minutes from midnight: groups the doses of one hour and keeps them ordered. */
    val minutes: Int,
    val dosage: String? = null,
    val image: String? = null,
    val taken: Boolean = false,
)

/** The doses of a single scheduled time — the unit the widget actually shows. */
internal data class DoseHour(val minutes: Int, val time: String, val doses: List<WidgetDose>) {
    val pending: List<WidgetDose> get() = doses.filter { !it.taken }
}

/** Groups the day's doses by scheduled time, in order. Pure -> unit-testable. */
internal fun doseHours(doses: List<WidgetDose>): List<DoseHour> =
    doses.groupBy { it.minutes }
        .toSortedMap()
        .map { (minutes, list) -> DoseHour(minutes, list.first().time, list.sortedBy { it.id }) }

/**
 * The hour the widget leads with: the earliest one that still owes a dose. Null once the day is
 * done — that's the "all taken" state.
 */
internal fun focusHour(hours: List<DoseHour>): DoseHour? = hours.firstOrNull { it.pending.isNotEmpty() }

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
        .map {
            WidgetDose(
                id = it.occurrenceId,
                name = it.name,
                time = it.scheduledAt.toLocalTime().format(timeFormatter),
                minutes = it.scheduledAt.toLocalTime().toSecondOfDay() / 60,
                dosage = it.dosage,
                image = it.image,
                taken = it.status == DoseStatus.TAKEN,
            )
        }
    return json.encodeToString(doses)
}

/** Pushes the DB truth into every widget's state and repaints. Called after any dose write. */
suspend fun refreshWidgetState(context: Context) {
    val encoded = buildSnapshot(context)
    val today = LocalDate.now().toString()
    GlanceAppWidgetManager(context).getGlanceIds(DoseWidget::class.java).forEach { id ->
        updateAppWidgetState(context, id) { it[DOSES_JSON] = encoded; it[DOSES_DATE] = today }
    }
    DoseWidget().updateAll(context)
}

/**
 * Home-screen widget: the next scheduled *hour*, not the next pill. A time with one dose gets a
 * card with its own take button; a time with several gets a line per dose plus "take all N", so
 * three pills at 2 pm never hide behind one another.
 *
 * Rendered from Glance state, not from a live DB read: update()/updateAll() don't restart
 * provideGlance, so a captured DB list would never repaint. State changes, on the other hand,
 * reliably recompose — which is also what lets the toggle update optimistically (see the action).
 */
class DoseWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(260.dp, 60.dp),   // 4x1 slim
            DpSize(150.dp, 150.dp),  // 2x2
            DpSize(260.dp, 150.dp),  // 4x2
            DpSize(260.dp, 280.dp),  // 5x3 and taller
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val today = LocalDate.now().toString()
        // Rebuild when there's no snapshot yet or it's from a previous day, so the widget
        // never keeps showing yesterday's doses after the date rolls over.
        val seeded = state[DOSES_JSON]?.takeIf { state[DOSES_DATE] == today }
            ?: buildSnapshot(context).also { encoded ->
                updateAppWidgetState(context, id) { it[DOSES_JSON] = encoded; it[DOSES_DATE] = today }
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
    val size = LocalSize.current
    val hours = doseHours(doses)
    val focus = focusHour(hours)
    val taken = doses.count { it.taken }

    // Anything that isn't a take button opens the app on that dose: the whole surface is a
    // tap target, and the rows and buttons inside it just take precedence within their bounds.
    val openWhole = focus?.pending?.firstOrNull()?.let { openDose(it.id) } ?: openApp()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .clickable(openWhole)
            .padding(if (size.height <= 70.dp) 12.dp else 16.dp),
    ) {
        when {
            doses.isEmpty() || focus == null -> AllDone(taken, doses.size)
            size.height <= 70.dp -> SlimLayout(focus)
            size.width <= 200.dp -> SmallLayout(focus, taken, doses.size)
            size.height >= 240.dp -> LargeLayout(hours, taken, doses.size)
            else -> MediumLayout(focus, hours, taken, doses.size)
        }
    }
}

// ---------------------------------------------------------------- layouts

/** 4x1: just the dose that's due. */
@Composable
private fun SlimLayout(focus: DoseHour) {
    val dose = focus.pending.first()
    Row(
        modifier = GlanceModifier.fillMaxSize().clickable(openDose(dose.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillTile(dose, 40.dp)
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(dose.name, maxLines = 1, style = titleStyle(15.sp))
            Text(hourLabel(focus), style = captionStyle())
        }
        Spacer(GlanceModifier.width(8.dp))
        TakeButton(dose, 44.dp)
    }
}

/** 2x2: the hour, how many doses it holds, and one action. */
@Composable
private fun SmallLayout(focus: DoseHour, taken: Int, total: Int) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // The count is the headline here, so the caption stays just the time.
        Text(if (focus.pending.size > 1) focus.time else hourLabel(focus), style = captionStyle())
        Spacer(GlanceModifier.height(2.dp))
        if (focus.pending.size > 1) {
            Text(
                LocalContext.current.getString(R.string.widget_doses_count, focus.pending.size),
                style = titleStyle(20.sp),
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.defaultWeight()) {
                focus.pending.take(3).forEach { dose ->
                    // Each pill opens its own dose, so a three-dose hour is still reachable
                    // one by one from a 2x2.
                    Box(modifier = GlanceModifier.padding(end = 6.dp).clickable(openDose(dose.id))) {
                        PillTile(dose, 26.dp)
                    }
                }
            }
            TakeAllButton(focus)
        } else {
            val dose = focus.pending.first()
            Text(dose.name, maxLines = 2, style = titleStyle(18.sp))
            Spacer(GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    LocalContext.current.getString(R.string.widget_day_progress, taken, total),
                    style = captionStyle(),
                    modifier = GlanceModifier.defaultWeight(),
                )
                TakeButton(dose, 44.dp)
            }
        }
    }
}

/** 4x2: the hour in full — one line per dose when there are several. */
@Composable
private fun MediumLayout(focus: DoseHour, hours: List<DoseHour>, taken: Int, total: Int) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (focus.pending.size > 1) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(openDose(focus.pending.first().id)),
                ) {
                    Text(
                        context.getString(R.string.widget_doses_at, focus.time, focus.pending.size),
                        style = titleStyle(16.sp),
                    )
                    Text(
                        context.getString(R.string.widget_tap_hint),
                        style = captionStyle(),
                    )
                }
                TakeAllChip(focus)
            }
            Spacer(GlanceModifier.height(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                focus.doses.take(3).forEach { dose ->
                    Box(modifier = GlanceModifier.padding(bottom = 6.dp)) { DoseLine(dose, compact = true) }
                }
            }
        } else {
            DayHeader(taken, total)
            Spacer(GlanceModifier.height(12.dp))
            val dose = focus.pending.first()
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(openDose(dose.id)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillTile(dose, 46.dp)
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(dose.name, maxLines = 1, style = titleStyle(16.sp))
                    Text(
                        listOfNotNull(focus.time, dose.dosage).joinToString(" · "),
                        maxLines = 1,
                        style = captionStyle(),
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                TakeButton(dose, 52.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row {
                hours.filter { it.minutes > focus.minutes && it.pending.isNotEmpty() }.take(2).forEach { hour ->
                    Box(modifier = GlanceModifier.padding(end = 8.dp)) {
                        Text(
                            if (hour.pending.size > 1) {
                                context.getString(R.string.widget_doses_at, hour.time, hour.pending.size)
                            } else {
                                hour.time
                            },
                            style = captionStyle(),
                            modifier = GlanceModifier
                                .background(GlanceTheme.colors.surfaceVariant)
                                .cornerRadius(16.dp)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 5x3: the whole day, hour by hour. */
@Composable
private fun LargeLayout(hours: List<DoseHour>, taken: Int, total: Int) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        DayHeader(taken, total)
        Spacer(GlanceModifier.height(10.dp))
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(hours) { hour ->
                Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    if (hour.doses.size > 1) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                context.getString(R.string.widget_doses_at, hour.time, hour.doses.size),
                                style = captionStyle(),
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .clickable(openDose(hour.doses.first().id)),
                            )
                            if (hour.pending.size > 1) TakeAllChip(hour)
                        }
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    hour.doses.forEach { dose ->
                        Box(modifier = GlanceModifier.padding(bottom = 4.dp)) {
                            DoseLine(dose, compact = hour.doses.size > 1, showTime = hour.doses.size == 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllDone(taken: Int, total: Int) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(56.dp).clickable(openApp()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_shape_clover8),
                contentDescription = null,
                colorFilter = ColorFilter.tint(TakenColor),
                modifier = GlanceModifier.fillMaxSize(),
            )
            Image(
                provider = ImageProvider(R.drawable.ic_widget_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
                modifier = GlanceModifier.size(24.dp),
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        Text(
            context.getString(if (total == 0) R.string.widget_empty else R.string.widget_all_done),
            style = titleStyle(15.sp),
        )
        if (total > 0) {
            Text(context.getString(R.string.widget_day_progress, taken, total), style = captionStyle())
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun DayHeader(taken: Int, total: Int) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(context.getString(R.string.nav_today), style = titleStyle(16.sp))
        Spacer(GlanceModifier.width(12.dp))
        // RemoteViews can't draw the app's wavy indicator, so the widget keeps a flat one.
        LinearProgressIndicator(
            progress = if (total == 0) 0f else taken.toFloat() / total,
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
            modifier = GlanceModifier.defaultWeight().height(6.dp),
        )
        Spacer(GlanceModifier.width(12.dp))
        Text(context.getString(R.string.today_progress, taken, total), style = captionStyle())
    }
}

/** One dose as a row: tap the row to open it, tap the shape to mark it without leaving home. */
@Composable
private fun DoseLine(dose: WidgetDose, compact: Boolean, showTime: Boolean = false) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                if (dose.taken) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.surfaceVariant,
            )
            .cornerRadius(16.dp)
            .clickable(openDose(dose.id))
            .padding(start = 10.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            PillTile(dose, 32.dp)
            Spacer(GlanceModifier.width(10.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(dose.name, maxLines = 1, style = titleStyle(14.sp))
            if (showTime) Text(dose.time, style = captionStyle())
        }
        Spacer(GlanceModifier.width(8.dp))
        TakeButton(dose, 40.dp)
    }
}

@Composable
private fun PillTile(dose: WidgetDose, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(size * 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(medImageRes(dose.image)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            modifier = GlanceModifier.size(size * 0.55f),
        )
    }
}

/**
 * The take/undo toggle, in the app's shape vocabulary: a seven-sided cookie while the dose is
 * owed, an eight-leaf clover once it's taken. Glance renders to RemoteViews, so the shapes are
 * baked drawables and there's no morph — the tap just swaps state and the widget redraws.
 */
@Composable
private fun TakeButton(dose: WidgetDose, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .clickable(actionRunCallback<ToggleTakenAction>(actionParametersOf(OCCURRENCE_ID to dose.id))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (dose.taken) R.drawable.ic_shape_clover8 else R.drawable.ic_shape_cookie7,
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (dose.taken) TakenColor else GlanceTheme.colors.primaryContainer,
            ),
            modifier = GlanceModifier.fillMaxSize(),
        )
        Image(
            provider = ImageProvider(
                if (dose.taken) R.drawable.ic_widget_check else R.drawable.ic_widget_circle,
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (dose.taken) ColorProvider(Color.White) else GlanceTheme.colors.onPrimaryContainer,
            ),
            modifier = GlanceModifier.size(size * 0.4f),
        )
    }
}

@Composable
private fun TakeAllChip(hour: DoseHour) {
    Text(
        LocalContext.current.getString(R.string.widget_take_all_short),
        style = TextStyle(
            color = GlanceTheme.colors.onPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
        modifier = GlanceModifier
            .background(GlanceTheme.colors.primary)
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<TakeAllAction>(actionParametersOf(HOUR_MINUTES to hour.minutes)))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun TakeAllButton(hour: DoseHour) {
    Text(
        LocalContext.current.getString(R.string.focus_take_all, hour.pending.size),
        style = TextStyle(
            color = GlanceTheme.colors.onPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.primary)
            .cornerRadius(20.dp)
            .clickable(actionRunCallback<TakeAllAction>(actionParametersOf(HOUR_MINUTES to hour.minutes)))
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun hourLabel(hour: DoseHour): String {
    val context = LocalContext.current
    return if (hour.pending.size > 1) {
        context.getString(R.string.widget_doses_at, hour.time, hour.pending.size)
    } else {
        hour.time
    }
}

@Composable
private fun titleStyle(size: TextUnit) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = size,
    fontWeight = FontWeight.Medium,
)

@Composable
private fun captionStyle() = TextStyle(
    color = GlanceTheme.colors.onSurfaceVariant,
    fontSize = 13.sp,
)

/**
 * Tapping a dose opens the focus screen for it — the same entry point as a notification. The
 * per-dose data URI keeps Android from collapsing every row's PendingIntent into one.
 */
@Composable
private fun openApp(): Action {
    val context = LocalContext.current
    return actionStartActivity(Intent(context, MainActivity::class.java))
}

@Composable
private fun openDose(occurrenceId: Long): Action {
    val context = LocalContext.current
    return actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("dose://occurrence/$occurrenceId")
            putExtra(Reminders.EXTRA_OCCURRENCE_ID, occurrenceId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )
}

// ---------------------------------------------------------------- actions

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
        repository.setOccurrenceStatus(
            id,
            if (makeTaken) DoseStatus.TAKEN else DoseStatus.PENDING,
            if (makeTaken) LocalDateTime.now() else null,
        )
    }
}

/** "Take all N": resolves every pending dose of one scheduled time in a single tap. */
class TakeAllAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val minutes = parameters[HOUR_MINUTES] ?: return
        val ids = mutableListOf<Long>()
        updateAppWidgetState(context, glanceId) { prefs ->
            val encoded = prefs[DOSES_JSON] ?: return@updateAppWidgetState
            val doses = json.decodeFromString<List<WidgetDose>>(encoded)
            doses.filter { it.minutes == minutes && !it.taken }.forEach { ids += it.id }
            prefs[DOSES_JSON] = json.encodeToString(
                doses.map { if (it.minutes == minutes) it.copy(taken = true) else it },
            )
        }
        DoseWidget().update(context, glanceId)

        val repository = repository(context)
        val now = LocalDateTime.now()
        ids.forEach { repository.setOccurrenceStatus(it, DoseStatus.TAKEN, now) }
    }
}
