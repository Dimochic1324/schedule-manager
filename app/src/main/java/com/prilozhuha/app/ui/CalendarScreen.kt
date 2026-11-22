package com.prilozhuha.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import com.prilozhuha.app.data.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class CalMode(val label: String) {
    MONTH("Месяц"),
    WEEK("Неделя"),
    DAY("День"),
    AGENDA("Расписание"),
}

private fun shift(mode: CalMode, d: LocalDate, dir: Int): LocalDate = when (mode) {
    CalMode.MONTH -> d.plusMonths(dir.toLong())
    CalMode.WEEK, CalMode.AGENDA -> d.plusWeeks(dir.toLong())
    CalMode.DAY -> d.plusDays(dir.toLong())
}

@Composable
fun CalendarScreen(
    items: List<Item>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpen: (Item) -> Unit,
    onCreateAt: (LocalDate, LocalTime) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(CalMode.MONTH) }
    val now = rememberNow()
    val today = now.toLocalDate()

    val title = when (mode) {
        CalMode.DAY -> selected.format(Fmt.shortDate).cap()
        else -> Fmt.monthYear(selected)
    }

    Column(Modifier.fillMaxSize()) {
        CalendarHeader(
            title = title,
            mode = mode,
            onMode = { mode = it },
            onPrev = { onSelect(shift(mode, selected, -1)) },
            onNext = { onSelect(shift(mode, selected, 1)) },
            onToday = { onSelect(today) },
        )
        when (mode) {
            CalMode.MONTH -> MonthView(
                items = items,
                selected = selected,
                now = now,
                onDayClick = { onSelect(it); mode = CalMode.DAY },
                onSwipe = { dir -> onSelect(shift(CalMode.MONTH, selected, dir)) },
            )
            CalMode.WEEK -> {
                val start = selected.with(DayOfWeek.MONDAY)
                Timeline(
                    days = (0L..6L).map { start.plusDays(it) },
                    items = items,
                    now = now,
                    onOpen = onOpen,
                    onCreateAt = onCreateAt,
                    onDayHeader = { onSelect(it); mode = CalMode.DAY },
                )
            }
            CalMode.DAY -> Timeline(
                days = listOf(selected),
                items = items,
                now = now,
                onOpen = onOpen,
                onCreateAt = onCreateAt,
                onDayHeader = {},
            )
            CalMode.AGENDA -> AgendaView(items, selected, now, onOpen)
        }
    }
}

@Composable
private fun CalendarHeader(
    title: String,
    mode: CalMode,
    onMode: (CalMode) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад") }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Вперёд") }
        IconButton(onClick = onToday) { Icon(Icons.Outlined.Today, "Сегодня") }
        Box {
            TextButton(onClick = { menu = true }) {
                Text(mode.label)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                CalMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.label, fontWeight = if (m == mode) FontWeight.Bold else null) },
                        onClick = { onMode(m); menu = false },
                    )
                }
            }
        }
    }
}

// ───────────────────────── Месяц ─────────────────────────

@Composable
private fun MonthView(
    items: List<Item>,
    selected: LocalDate,
    now: LocalDateTime,
    onDayClick: (LocalDate) -> Unit,
    onSwipe: (Int) -> Unit,
) {
    val today = now.toLocalDate()
    val first = selected.withDayOfMonth(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val swipe by rememberUpdatedState(onSwipe)
    var drag by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag > 120f) swipe(-1) else if (drag < -120f) swipe(1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, delta -> drag += delta }
            }
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            WeekDayNames.forEachIndexed { i, s ->
                Text(
                    s,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i >= 5) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (w in 0 until 6) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (dIdx in 0 until 7) {
                    val day = gridStart.plusDays((w * 7 + dIdx).toLong())
                    MonthCell(
                        day = day,
                        inMonth = day.month == selected.month,
                        isToday = day == today,
                        isSelected = day == selected,
                        dayItems = Recurrence.itemsOn(items, day).sortedForDay(),
                        now = now,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onDayClick(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    dayItems: List<Item>,
    now: LocalDateTime,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .border(0.5.dp, cs.outlineVariant.copy(alpha = 0.6f))
            .background(if (isSelected) cs.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp, vertical = 2.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(22.dp)
                .background(if (isToday) cs.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                fontSize = 12.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> cs.onPrimary
                    !inMonth -> cs.onSurface.copy(alpha = 0.35f)
                    else -> cs.onSurface
                },
            )
        }
        val maxShown = 3
        dayItems.take(maxShown).forEach { ItemChip(it, now, alpha = if (inMonth) 1f else 0.45f) }
        if (dayItems.size > maxShown) {
            Text(
                "+${dayItems.size - maxShown}",
                fontSize = 10.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

/** Маленькая плашка: событие (заливка), задача-дедлайн (рамка + флажок), будильник (⏰). */
@Composable
fun ItemChip(item: Item, now: LocalDateTime, alpha: Float = 1f, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val color = Palette.of(item)
    val shape = RoundedCornerShape(3.dp)
    var m = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    when (item.kind) {
        Kind.EVENT -> {
            Text(
                (item.time?.let { it.format(Fmt.time) + " " } ?: "") + item.displayTitle,
                modifier = m.background(color.copy(alpha = alpha)).padding(horizontal = 2.dp),
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                lineHeight = 13.sp,
            )
        }
        Kind.TASK -> {
            val c = when {
                item.done -> cs.outline
                isOverdue(item, now) -> cs.error
                else -> cs.primary
            }
            Text(
                (if (item.done) "✓ " else "⚑ ") + item.displayTitle,
                modifier = m.border(1.dp, c.copy(alpha = alpha), shape).padding(horizontal = 2.dp),
                color = c.copy(alpha = alpha),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                lineHeight = 13.sp,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
        }
        Kind.ALARM -> {
            Text(
                "⏰ " + (item.time?.format(Fmt.time) ?: "") + " " + item.title,
                modifier = m.background(color.copy(alpha = 0.18f * alpha)).padding(horizontal = 2.dp),
                color = color.copy(alpha = alpha),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                lineHeight = 13.sp,
            )
        }
    }
}

// ───────────────────────── Расписание (список) ─────────────────────────

@Composable
private fun AgendaView(items: List<Item>, from: LocalDate, now: LocalDateTime, onOpen: (Item) -> Unit) {
    val today = now.toLocalDate()
    val days = remember(items, from) {
        (0L until 90L).map { from.plusDays(it) }.mapNotNull { d ->
            Recurrence.itemsOn(items, d).sortedForDay().takeIf { it.isNotEmpty() }?.let { d to it }
        }
    }
    if (days.isEmpty()) {
        EmptyState(Icons.Outlined.CalendarMonth, "Пусто", "В ближайшие 3 месяца ничего не запланировано")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(days, key = { it.first.toString() }) { (day, list) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Fmt.dowShort(day.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier.size(34.dp).background(
                            if (day == today) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (day == today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    list.forEach { AgendaRow(it, now, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(item: Item, now: LocalDateTime, onOpen: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val color = when (item.kind) {
        Kind.TASK -> if (isOverdue(item, now)) cs.error else cs.primary
        else -> Palette.of(item)
    }
    val filled = item.kind == Kind.EVENT
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) color else color.copy(alpha = 0.10f))
            .clickable { onOpen(item) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (filled) Color.White else color
        Icon(kindIcon(item.kind), null, Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.displayTitle,
                color = if (filled) Color.White else cs.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when (item.kind) {
                Kind.TASK -> "Срок: " + (item.time?.format(Fmt.time) ?: "в течение дня")
                else -> timeRange(item)
            }
            Text(sub, color = if (filled) Color.White.copy(alpha = 0.85f) else cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
    Spacer(Modifier.height(0.dp))
}
