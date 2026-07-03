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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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

@Composable
fun MedEditorScreen(
    medicationId: Long?,
    onDone: () -> Unit,
    viewModel: MedEditorViewModel = hiltViewModel(),
) {
    LaunchedLoad(medicationId, viewModel)
    MedEditorContent(
        state = viewModel.uiState,
        onChange = viewModel::update,
        onSave = { viewModel.save(onDone) },
    )
}

@Composable
private fun LaunchedLoad(medicationId: Long?, viewModel: MedEditorViewModel) {
    androidx.compose.runtime.LaunchedEffect(medicationId) {
        if (medicationId != null) viewModel.load(medicationId)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { new -> onChange { it.copy(name = new) } },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            isError = state.name.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.field_image), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.field_image_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 6,
            ) {
                medImageKeys.forEach { key ->
                    val selected = state.image == key
                    MedIcon(
                        key,
                        size = null, // fill the row: weight + square aspect
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable { onChange { it.copy(image = key) } }
                            .then(
                                if (selected) Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp),
                                ) else Modifier,
                            ),
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.dosage,
            onValueChange = { new -> onChange { it.copy(dosage = new) } },
            label = { Text(stringResource(R.string.field_dosage)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = { new -> onChange { it.copy(notes = new) } },
            label = { Text(stringResource(R.string.field_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )

        StartDateField(state.startDate) { picked -> onChange { it.copy(startDate = picked) } }

        ScheduleTypeDropdown(state.type) { picked -> onChange { it.copy(type = picked) } }

        // Type-specific inputs.
        if (state.needsTimes) {
            TimesSection(
                times = state.times,
                onAdd = { time -> onChange { it.copy(times = (it.times + time).distinct().sorted()) } },
                onRemove = { time -> onChange { it.copy(times = it.times - time) } },
            )
        }
        when (state.type) {
            ScheduleType.WEEKLY -> WeekdaysSection(state.daysOfWeek) { day ->
                onChange { it.copy(daysOfWeek = it.daysOfWeek.toggle(day)) }
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

        Spacer(Modifier.height(8.dp))
        val saveButtonHeight = ButtonDefaults.LargeContainerHeight
        Button(
            onClick = onSave,
            enabled = state.isValid && !state.saving,
            contentPadding = ButtonDefaults.contentPaddingFor(saveButtonHeight),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = saveButtonHeight),
        ) {
            Text(
                stringResource(R.string.action_save),
                style = ButtonDefaults.textStyleFor(saveButtonHeight),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_back)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTypeDropdown(type: ScheduleType, onSelect: (ScheduleType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(type.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_schedule)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScheduleType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimesSection(
    times: List<LocalTime>,
    onAdd: (LocalTime) -> Unit,
    onRemove: (LocalTime) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.editor_times))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            times.forEach { time ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(time) },
                    label = { Text(time.format(timeFormatter)) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove))
                    },
                )
            }
            TextButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
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
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_back)) }
            },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { TimePicker(state = pickerState) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekdaysSection(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Column {
        Text(stringResource(R.string.editor_weekdays))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in selected,
                    onClick = { onToggle(day) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
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
