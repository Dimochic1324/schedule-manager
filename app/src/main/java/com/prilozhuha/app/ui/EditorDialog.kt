package com.prilozhuha.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import com.prilozhuha.app.data.Recurrence
import com.prilozhuha.app.data.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

object Drafts {
    private fun nextHour(): LocalTime = LocalTime.of((LocalTime.now().hour + 1) % 24, 0)

    fun new(kind: Kind, date: LocalDate?, time: LocalTime? = null): Item = when (kind) {
        Kind.EVENT -> Item(
            kind = kind, date = date ?: LocalDate.now(), time = time ?: nextHour(),
            durationMin = 60, reminder = 10, fullScreen = false,
        )
        Kind.TASK -> Item(kind = kind, date = date, time = time, reminder = null, fullScreen = false)
        Kind.ALARM -> Item(
            kind = kind, date = date ?: LocalDate.now(), time = time ?: nextHour(),
            reminder = 0, fullScreen = true, color = 5,
        )
    }

    fun switchKind(d: Item, k: Kind): Item = when (k) {
        Kind.EVENT -> d.copy(kind = k, date = d.date ?: LocalDate.now(), time = d.time ?: nextHour(), reminder = d.reminder ?: 10)
        Kind.TASK -> d.copy(kind = k)
        Kind.ALARM -> d.copy(kind = k, date = d.date ?: LocalDate.now(), time = d.time ?: nextHour(), reminder = 0, fullScreen = true)
    }

    /** Приводим запись в порядок перед сохранением. */
    fun normalize(src: Item): Item {
        var d = src.copy(title = src.title.trim(), notes = src.notes.trimEnd())
        if (d.kind == Kind.ALARM) {
            d = d.copy(reminder = 0, fullScreen = true, time = d.time ?: LocalTime.of(7, 0), date = d.date ?: LocalDate.now())
            // разовый будильник на уже прошедшее время переносим на завтра
            val date = d.date!!
            val time = d.time!!
            if (d.repeat == Repeat.NONE && !LocalDateTime.of(date, time).isAfter(LocalDateTime.now())) {
                var nd = date
                while (!LocalDateTime.of(nd, time).isAfter(LocalDateTime.now())) nd = nd.plusDays(1)
                d = d.copy(date = nd)
            }
        }
        if (d.kind == Kind.TASK && d.date == null) {
            d = d.copy(time = null, repeat = Repeat.NONE, repeatDays = emptySet(), reminder = null)
        }
        if (d.repeat == Repeat.WEEKLY && d.repeatDays.isEmpty()) {
            d.date?.let { d = d.copy(repeatDays = setOf(it.dayOfWeek.value)) }
        }
        if (d.repeat != Repeat.WEEKLY) d = d.copy(repeatDays = emptySet())
        return d
    }
}

private enum class TimeTarget { START, END }

private val reminderOptions: List<Pair<Int?, String>> = listOf(
    null to "Без напоминания",
    0 to "В момент начала",
    5 to "За 5 минут",
    10 to "За 10 минут",
    15 to "За 15 минут",
    30 to "За 30 минут",
    60 to "За 1 час",
    120 to "За 2 часа",
    1440 to "За 1 день",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorDialog(
    initial: Item,
    onDismiss: () -> Unit,
    onSave: (Item) -> Unit,
    onDelete: () -> Unit,
) {
    var d by remember(initial) { mutableStateOf(initial) }
    var showDate by remember { mutableStateOf(false) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = initial.id == 0L
    val focus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(if (isNew) "Создать" else "Изменить") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть") }
                    },
                    actions = {
                        if (!isNew) {
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Удалить") }
                        }
                        Button(onClick = { onSave(Drafts.normalize(d)) }, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Сохранить")
                        }
                    },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    // Тип записи
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Kind.entries.forEach { k ->
                            FilterChip(
                                selected = d.kind == k,
                                onClick = { d = Drafts.switchKind(d, k) },
                                label = { Text(k.label) },
                                leadingIcon = { Icon(kindIcon(k), null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = d.title,
                        onValueChange = { d = d.copy(title = it) },
                        label = { Text(if (d.kind == Kind.ALARM) "Подпись (необязательно)" else "Название") },
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Spacer(Modifier.height(8.dp))

                    when (d.kind) {
                        Kind.EVENT -> EventTimeSection(d, { d = it }, { showDate = true }, { timeTarget = it })
                        Kind.TASK -> TaskTimeSection(d, { d = it }, { showDate = true }, { timeTarget = it })
                        Kind.ALARM -> AlarmTimeSection(d, { showDate = true }, { timeTarget = it })
                    }

                    // Повтор
                    if (d.date != null) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SettingRow(Icons.Outlined.Repeat) {
                            Selector(
                                options = Repeat.entries.map { it to it.label },
                                selected = d.repeat,
                                onSelect = { r ->
                                    d = d.copy(
                                        repeat = r,
                                        repeatDays = if (r == Repeat.WEEKLY && d.repeatDays.isEmpty())
                                            setOfNotNull(d.date?.dayOfWeek?.value) else d.repeatDays,
                                    )
                                },
                            )
                        }
                        if (d.repeat == Repeat.WEEKLY) {
                            FlowRow(
                                Modifier.padding(start = 40.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                WeekDayNames.forEachIndexed { i, name ->
                                    val v = i + 1
                                    FilterChip(
                                        selected = v in d.repeatDays,
                                        onClick = {
                                            val set = if (v in d.repeatDays) d.repeatDays - v else d.repeatDays + v
                                            d = d.copy(repeatDays = set)
                                        },
                                        label = { Text(name) },
                                    )
                                }
                            }
                        }
                    }

                    // Напоминание
                    if (d.kind != Kind.ALARM && d.date != null) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SettingRow(Icons.Outlined.Notifications) {
                            Selector(
                                options = reminderOptions.map { (v, label) ->
                                    v to if (v == 0 && d.kind == Kind.TASK) "В момент срока" else label
                                },
                                selected = d.reminder,
                                onSelect = { d = d.copy(reminder = it) },
                            )
                        }
                        if (d.reminder != null && d.time == null) {
                            Text(
                                "Для записей без времени отсчёт идёт от ${Recurrence.DEFAULT_TIME.format(Fmt.time)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 40.dp),
                            )
                        }
                        if (d.reminder != null) {
                            SettingRow(Icons.Outlined.Fullscreen) {
                                Column(Modifier.weight(1f)) {
                                    Text("На весь экран", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Как будильник: звук и вибрация, пока не выключите, можно отложить",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = d.fullScreen, onCheckedChange = { d = d.copy(fullScreen = it) })
                            }
                        }
                    }
                    if (d.kind == Kind.ALARM) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            "Будильник открывается на весь экран, звонит и вибрирует, пока вы его не выключите или не отложите.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }

                    // Цвет
                    if (d.kind != Kind.TASK) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SettingRow(Icons.Outlined.Palette) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Palette.colors.forEachIndexed { i, c ->
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(c, CircleShape)
                                            .clickable { d = d.copy(color = i) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (d.color == i) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Приоритет
                    if (d.kind == Kind.TASK) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SettingRow(Icons.Outlined.Flag) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Palette.priorityNames.forEachIndexed { i, name ->
                                    FilterChip(
                                        selected = d.priority == i,
                                        onClick = { d = d.copy(priority = i) },
                                        label = { Text(name) },
                                        leadingIcon = if (i > 0) {
                                            { Box(Modifier.size(10.dp).background(Palette.priority[i], CircleShape)) }
                                        } else null,
                                    )
                                }
                            }
                        }
                    }

                    // Заметки
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SettingRow(Icons.AutoMirrored.Outlined.Notes) {
                        OutlinedTextField(
                            value = d.notes,
                            onValueChange = { d = d.copy(notes = it) },
                            label = { Text("Заметки") },
                            minLines = 4,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (isNew) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (d.date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        d = d.copy(date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = state)
        }
    }

    timeTarget?.let { target ->
        key(target) {
            val init = if (target == TimeTarget.END) d.endTime else (d.time ?: LocalTime.of(9, 0))
            val state = rememberTimePickerState(init.hour, init.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { timeTarget = null },
                confirmButton = {
                    TextButton(onClick = {
                        val t = LocalTime.of(state.hour, state.minute)
                        d = if (target == TimeTarget.START) {
                            d.copy(time = t)
                        } else {
                            val endMin = t.hour * 60 + t.minute
                            val dur = ((endMin - d.startMin) + 1440) % 1440
                            d.copy(durationMin = if (dur == 0) 1440 else dur.coerceAtLeast(5))
                        }
                        timeTarget = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { timeTarget = null }) { Text("Отмена") } },
                text = { TimePicker(state = state) },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${d.displayTitle}»?") },
            text = { if (d.repeat != Repeat.NONE) Text("Будут удалены все повторы.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun EventTimeSection(
    d: Item,
    onChange: (Item) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: (TimeTarget) -> Unit,
) {
    SettingRow(Icons.Outlined.Schedule) {
        Text("Весь день", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = d.time == null,
            onCheckedChange = { allDay ->
                onChange(if (allDay) d.copy(time = null) else d.copy(time = LocalTime.of(9, 0)))
            },
        )
    }
    Row(Modifier.padding(start = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPickDate) { Text((d.date ?: LocalDate.now()).format(Fmt.fullDate).cap()) }
        val t = d.time
        if (t != null) {
            TextButton(onClick = { onPickTime(TimeTarget.START) }) { Text(t.format(Fmt.time)) }
            Text("–")
            TextButton(onClick = { onPickTime(TimeTarget.END) }) { Text(d.endTime.format(Fmt.time)) }
        }
    }
}

@Composable
private fun TaskTimeSection(
    d: Item,
    onChange: (Item) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: (TimeTarget) -> Unit,
) {
    SettingRow(Icons.Outlined.CalendarMonth) {
        Text("Срок выполнения", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = d.date != null,
            onCheckedChange = { on ->
                onChange(if (on) d.copy(date = LocalDate.now()) else d.copy(date = null, time = null, reminder = null, repeat = Repeat.NONE))
            },
        )
    }
    val date = d.date
    if (date != null) {
        Row(Modifier.padding(start = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPickDate) { Text(date.format(Fmt.fullDate).cap()) }
            val t = d.time
            if (t != null) {
                TextButton(onClick = { onPickTime(TimeTarget.START) }) { Text(t.format(Fmt.time)) }
                IconButton(onClick = { onChange(d.copy(time = null)) }) {
                    Icon(Icons.Filled.Close, "Убрать время", Modifier.size(18.dp))
                }
            } else {
                TextButton(onClick = {
                    onChange(d.copy(time = LocalTime.of(18, 0)))
                    onPickTime(TimeTarget.START)
                }) { Text("+ время") }
            }
        }
    }
}

@Composable
private fun AlarmTimeSection(d: Item, onPickDate: () -> Unit, onPickTime: (TimeTarget) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { onPickTime(TimeTarget.START) }) {
            Text(
                (d.time ?: LocalTime.of(7, 0)).format(Fmt.time),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
            )
        }
        TextButton(onClick = onPickDate) {
            Text(
                (if (d.repeat == Repeat.NONE) "" else "Начиная с ") +
                    (d.date ?: LocalDate.now()).format(Fmt.fullDate).let { if (d.repeat == Repeat.NONE) it.cap() else it }
            )
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        content()
    }
}

@Composable
private fun <T> Selector(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .clickable { open = true }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (value == selected) FontWeight.Bold else null) },
                    onClick = { onSelect(value); open = false },
                )
            }
        }
    }
}
