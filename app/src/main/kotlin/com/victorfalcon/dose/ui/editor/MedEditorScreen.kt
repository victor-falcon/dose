@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.victorfalcon.dose.ui.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.victorfalcon.dose.R
import com.victorfalcon.dose.domain.model.ScheduleType
import com.victorfalcon.dose.ui.common.MedIcon
import com.victorfalcon.dose.ui.common.medImageKeys
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Fields keep a 12 dp corner instead of the component default, per the redesign. */
private val FieldShape = RoundedCornerShape(12.dp)

@Composable
fun MedEditorScreen(
    medicationId: Long?,
    onDone: () -> Unit,
    viewModel: MedEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(medicationId) {
        if (medicationId != null) viewModel.load(medicationId)
    }
    MedEditorContent(
        state = viewModel.uiState,
        onChange = viewModel::update,
        onSave = { viewModel.save(onDone) },
    )
}

@Composable
private fun MedEditorContent(
    state: MedEditorUiState,
    onChange: ((MedEditorUiState) -> MedEditorUiState) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { new -> onChange { it.copy(name = new) } },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            isError = state.name.isBlank(),
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )

        ShapePicker(state.image) { key -> onChange { it.copy(image = key) } }

        OutlinedTextField(
            value = state.dosage,
            onValueChange = { new -> onChange { it.copy(dosage = new) } },
            label = { Text(stringResource(R.string.field_dosage)) },
            singleLine = true,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = { new -> onChange { it.copy(notes = new) } },
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 2,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )

        StartDateField(state.startDate) { picked -> onChange { it.copy(startDate = picked) } }

        // The pauta is a row of chips, not a dropdown: five options, all visible.
        Section(stringResource(R.string.field_schedule)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScheduleType.entries.forEach { option ->
                    FilterChip(
                        selected = state.type == option,
                        onClick = { onChange { it.copy(type = option) } },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
        }

        if (state.needsTimes) {
            TimesSection(
                times = state.times,
                onAdd = { time -> onChange { it.copy(times = (it.times + time).distinct().sorted()) } },
                onRemove = { time -> onChange { it.copy(times = it.times - time) } },
            )
        }
        when (state.type) {
            ScheduleType.WEEKLY -> Section(stringResource(R.string.editor_weekdays)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in state.daysOfWeek,
                            onClick = { onChange { it.copy(daysOfWeek = it.daysOfWeek.toggle(day)) } },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
            }
            ScheduleType.INTERVAL -> NumberField(
                label = stringResource(R.string.editor_interval_days),
                value = state.intervalDays,
                onValueChange = { n -> onChange { it.copy(intervalDays = n) } },
            )
            ScheduleType.CYCLIC -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NumberField(
                    label = stringResource(R.string.editor_cycle_active),
                    value = state.cycleActiveDays,
                    onValueChange = { n -> onChange { it.copy(cycleActiveDays = n) } },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.editor_cycle_rest),
                    value = state.cycleRestDays,
                    onValueChange = { n -> onChange { it.copy(cycleRestDays = n) } },
                    modifier = Modifier.weight(1f),
                )
            }
            else -> Unit
        }

        Spacer(Modifier.height(4.dp))
        val saveHeight = 72.dp
        Button(
            onClick = onSave,
            enabled = state.isValid && !state.saving,
            shapes = ButtonDefaults.shapesFor(saveHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(saveHeight),
            modifier = Modifier
                .fillMaxWidth()
                .height(saveHeight),
        ) {
            Text(
                stringResource(R.string.action_save),
                style = ButtonDefaults.textStyleFor(saveHeight),
            )
        }
    }
}

/** The pill shapes, six across. The chosen one gets a primary outline and a check badge. */
@Composable
private fun ShapePicker(selectedKey: String?, onSelect: (String) -> Unit) {
    Section(stringResource(R.string.field_image)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 6,
        ) {
            medImageKeys.forEach { key ->
                val selected = selectedKey == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                ) {
                    MedIcon(
                        key,
                        size = null,
                        shape = RoundedCornerShape(16.dp),
                        container = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { onSelect(key) }
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                } else {
                                    Modifier
                                }
                            ),
                    )
                    if (selected) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 5.dp, y = (-5).dp)
                                .size(20.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.1.sp,
        )
        content()
    }
}

@Composable
private fun StartDateField(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    // Disabled field + clickable parent: the disabled field ignores touches so the
    // Box's onClick opens the picker (the standard read-only-picker workaround).
    Box(Modifier.clickable { showPicker = true }) {
        OutlinedTextField(
            value = date.format(dateFormatter),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(R.string.field_start_date)) },
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochMillisUtc(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(it.toLocalDateUtc()) }
                    showPicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TimesSection(
    times: List<LocalTime>,
    onAdd: (LocalTime) -> Unit,
    onRemove: (LocalTime) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Section(stringResource(R.string.editor_times)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            times.forEach { time ->
                InputChip(
                    selected = true,
                    onClick = { onRemove(time) },
                    label = { Text(time.format(timeFormatter)) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_remove),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            FilledTonalButton(
                onClick = { showPicker = true },
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier.height(36.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.editor_add_time))
            }
        }
    }
    if (showPicker) {
        // is24Hour: a dose app can't afford the 12 AM/PM foot-gun (noon saved as midnight).
        val pickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
        DatePickerDialog( // reuse the dialog scaffold for the time picker
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onAdd(LocalTime.of(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { TimePicker(state = pickerState) }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).toIntOrNull() ?: 0) },
        label = { Text(label) },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun Set<DayOfWeek>.toggle(day: DayOfWeek): Set<DayOfWeek> =
    if (day in this) this - day else this + day

private val ScheduleType.labelRes: Int
    get() = when (this) {
        ScheduleType.DAILY_TIMES -> R.string.schedule_daily_times
        ScheduleType.WEEKLY -> R.string.schedule_weekly
        ScheduleType.INTERVAL -> R.string.schedule_interval
        ScheduleType.CYCLIC -> R.string.schedule_cyclic
        ScheduleType.AS_NEEDED -> R.string.schedule_as_needed
    }

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun LocalDate.toEpochMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
