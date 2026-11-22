package com.prilozhuha.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import com.prilozhuha.app.data.Recurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.max

private val HourH = 56.dp
private val LabelW = 44.dp
private const val DAY_MIN = 24 * 60

/** Положение блока на шкале дня. */
private class Placed(val item: Item, val startMin: Int, val endMin: Int) {
    var col = 0
    var cols = 1
}

private fun blockMinutes(item: Item): Int = when (item.kind) {
    Kind.EVENT -> max(item.durationMin, 20)
    else -> 30
}

/** Раскладываем пересекающиеся блоки по колонкам, как в Google Календаре. */
private fun layoutDay(blocks: List<Placed>): List<Placed> {
    val sorted = blocks.sortedWith(compareBy<Placed>({ it.startMin }, { -it.endMin }))
    val result = mutableListOf<Placed>()
    var cluster = mutableListOf<Placed>()
    val colEnds = mutableListOf<Int>()
    var clusterEnd = -1
    fun flush() {
        val n = max(colEnds.size, 1)
        cluster.forEach { it.cols = n }
        result += cluster
        cluster = mutableListOf()
        colEnds.clear()
        clusterEnd = -1
    }
    for (p in sorted) {
        if (cluster.isNotEmpty() && p.startMin >= clusterEnd) flush()
        var c = colEnds.indexOfFirst { it <= p.startMin }
        if (c == -1) {
            colEnds += p.endMin
            c = colEnds.lastIndex
        } else {
            colEnds[c] = p.endMin
        }
        p.col = c
        cluster += p
        clusterEnd = max(clusterEnd, p.endMin)
    }
    flush()
    return result
}

/** Сетка по часам для одного (день) или семи (неделя) дней. */
@Composable
fun Timeline(
    days: List<LocalDate>,
    items: List<Item>,
    now: LocalDateTime,
    onOpen: (Item) -> Unit,
    onCreateAt: (LocalDate, LocalTime) -> Unit,
    onDayHeader: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val today = now.toLocalDate()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        scroll.scrollTo(with(density) { (HourH * 7).roundToPx() })
    }
    val perDay = days.map { Recurrence.itemsOn(items, it) }

    Column(Modifier.fillMaxSize()) {
        // Заголовки дней
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(LabelW))
            days.forEach { d ->
                val isToday = d == today
                Column(
                    Modifier.weight(1f).clickable { onDayHeader(d) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        Fmt.dowShort(d.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) cs.primary else cs.onSurfaceVariant,
                    )
                    Box(
                        Modifier.size(30.dp).background(if (isToday) cs.primary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isToday) cs.onPrimary else cs.onSurface,
                        )
                    }
                }
            }
        }

        // Весь день + задачи без времени
        val allDay = perDay.map { list -> list.filter { it.time == null }.sortedForDay() }
        if (allDay.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                Text(
                    "весь\nдень",
                    modifier = Modifier.width(LabelW).padding(top = 2.dp),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.Center,
                    color = cs.onSurfaceVariant,
                )
                allDay.forEach { list ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                        list.take(3).forEach { ItemChip(it, now, onClick = { onOpen(it) }) }
                        if (list.size > 3) Text("+${list.size - 3}", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider()

        // Почасовая сетка
        Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            Column(Modifier.width(LabelW)) {
                for (h in 0 until 24) {
                    Box(Modifier.height(HourH).fillMaxWidth()) {
                        if (h > 0) {
                            Text(
                                "%02d:00".format(h),
                                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-7).dp),
                                fontSize = 10.sp,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            days.forEachIndexed { i, d ->
                DayColumn(
                    day = d,
                    timed = perDay[i].filter { it.time != null },
                    now = now,
                    onOpen = onOpen,
                    onCreateAt = onCreateAt,
                    modifier = Modifier.weight(1f).height(HourH * 24),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    timed: List<Item>,
    now: LocalDateTime,
    onOpen: (Item) -> Unit,
    onCreateAt: (LocalDate, LocalTime) -> Unit,
    modifier: Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val create by rememberUpdatedState(onCreateAt)
    BoxWithConstraints(
        modifier
            .drawBehind {
                val hourPx = HourH.toPx()
                for (h in 0 until 24) {
                    val y = h * hourPx
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
                }
                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), 1f)
            }
            .pointerInput(day) {
                detectTapGestures { off ->
                    val h = (off.y / HourH.toPx()).toInt().coerceIn(0, 23)
                    create(day, LocalTime.of(h, 0))
                }
            }
    ) {
        val placed = layoutDay(
            timed.map { Placed(it, it.startMin, (it.startMin + blockMinutes(it)).coerceAtMost(DAY_MIN)) }
        )
        val fullW = maxWidth
        placed.forEach { p ->
            val w = fullW / p.cols
            TimelineBlock(
                item = p.item,
                now = now,
                onOpen = onOpen,
                modifier = Modifier
                    .offset(x = w * p.col, y = HourH * (p.startMin / 60f))
                    .width(w)
                    .height(HourH * ((p.endMin - p.startMin) / 60f))
                    .padding(1.dp),
            )
        }
        if (day == now.toLocalDate()) {
            val y = HourH * ((now.hour * 60 + now.minute) / 60f)
            Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(Color(0xFFEA4335)))
            Box(Modifier.offset(x = (-4).dp, y = y - 5.dp).size(10.dp).background(Color(0xFFEA4335), CircleShape))
        }
    }
}

@Composable
private fun TimelineBlock(item: Item, now: LocalDateTime, onOpen: (Item) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val color = Palette.of(item)
    val shape = RoundedCornerShape(4.dp)
    val (bg, fg, border) = when (item.kind) {
        Kind.EVENT -> Triple(color, Color.White, Color.Transparent)
        Kind.ALARM -> Triple(color.copy(alpha = 0.18f), color, color)
        Kind.TASK -> {
            val c = when {
                item.done -> cs.outline
                isOverdue(item, now) -> cs.error
                else -> cs.primary
            }
            Triple(cs.surface, c, c)
        }
    }
    Column(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable { onOpen(item) }
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.kind != Kind.EVENT) {
                Icon(kindIcon(item.kind), null, Modifier.size(11.dp), tint = fg)
                Spacer(Modifier.width(2.dp))
            }
            Text(
                item.displayTitle,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
        }
        Text(
            timeRange(item),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = fg.copy(alpha = 0.85f),
            maxLines = 1,
        )
    }
}
