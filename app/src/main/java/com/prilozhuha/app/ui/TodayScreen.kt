package com.prilozhuha.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import com.prilozhuha.app.data.Recurrence
import com.prilozhuha.app.data.Repeat
import java.time.LocalDateTime
import java.time.LocalTime

private fun endOf(item: Item): LocalTime? {
    val t = item.time ?: return null
    if (item.kind != Kind.EVENT) return t
    val end = item.startMin + item.durationMin
    return if (end >= 24 * 60) LocalTime.MAX else t.plusMinutes(item.durationMin.toLong())
}

@Composable
fun TodayScreen(
    items: List<Item>,
    onOpen: (Item) -> Unit,
    onToggle: (Item) -> Unit,
    onTestAlarm: () -> Unit,
) {
    val ctx = LocalContext.current
    val now = rememberNow()
    val today = now.toLocalDate()
    val nowTime = now.toLocalTime()

    val schedule = Recurrence.itemsOn(items, today).filter { it.kind != Kind.TASK }.sortedForDay()
    val tasks = items.filter { t ->
        val d = t.date
        t.kind == Kind.TASK && d != null && (d == today || (d < today && !t.done))
    }.sortedWith(compareBy<Item>({ it.done }, { it.date }, { it.time ?: LocalTime.MAX }, { -it.priority }))
    val doneCount = tasks.count { it.done }
    val upcoming = schedule.firstOrNull { val e = endOf(it); it.time != null && e != null && e > nowTime }
    val nowIndex = schedule.indexOfFirst { it.time != null && it.time > nowTime }
    val hasTimed = schedule.any { it.time != null }

    var menu by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Сегодня", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        today.format(Fmt.dowDayMonth).cap(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Ещё") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Проверить полноэкранный будильник") },
                            leadingIcon = { Icon(Icons.Outlined.NotificationsActive, null) },
                            onClick = {
                                menu = false
                                onTestAlarm()
                                Toast.makeText(ctx, "Будильник сработает через 10 секунд. Можно заблокировать экран.", Toast.LENGTH_LONG).show()
                            },
                        )
                    }
                }
            }
        }

        item {
            SummaryCard(
                upcoming = upcoming,
                now = now,
                events = schedule.count { it.kind == Kind.EVENT },
                tasksLeft = tasks.size - doneCount,
                onOpen = onOpen,
            )
        }

        item { SectionHeader("Расписание", if (schedule.isEmpty()) null else schedule.size.toString()) }
        if (schedule.isEmpty()) {
            item {
                Text(
                    "На сегодня событий нет. Нажмите «+», чтобы добавить событие или будильник.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else {
            schedule.forEachIndexed { i, it ->
                if (i == nowIndex) item(key = "now") { NowDivider(now) }
                item(key = "s${it.id}") { ScheduleRow(it, nowTime, onOpen) }
            }
            if (nowIndex == -1 && hasTimed) item(key = "now") { NowDivider(now) }
        }

        item {
            SectionHeader("Задачи", if (tasks.isEmpty()) null else "$doneCount из ${tasks.size}")
        }
        if (tasks.isEmpty()) {
            item {
                Text(
                    "Нет задач со сроком на сегодня.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else {
            item {
                LinearProgressIndicator(
                    progress = { if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(tasks, key = { "t${it.id}" }) { TaskRow(it, now, onOpen, onToggle) }
        }
    }
}

@Composable
private fun SummaryCard(upcoming: Item?, now: LocalDateTime, events: Int, tasksLeft: Int, onOpen: (Item) -> Unit) {
    val nowTime = now.toLocalTime()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (upcoming != null) {
                val start = upcoming.time!!
                val inProgress = upcoming.kind == Kind.EVENT && start <= nowTime
                Text(
                    if (inProgress) "Сейчас идёт" else "Далее, в ${start.format(Fmt.time)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    upcoming.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpen(upcoming) },
                )
                if (!inProgress) {
                    val mins = (start.toSecondOfDay() - nowTime.toSecondOfDay()) / 60
                    Text(
                        "через " + if (mins >= 60) "${mins / 60} ч ${mins % 60} мин" else "$mins мин",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "Событий: $events  ·  Осталось задач: $tasksLeft",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun NowDivider(now: LocalDateTime) {
    val red = Color(0xFFEA4335)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(now.format(Fmt.time), style = MaterialTheme.typography.labelMedium, color = red, modifier = Modifier.width(56.dp))
        Box(Modifier.size(8.dp).background(red, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(red))
    }
}

@Composable
private fun ScheduleRow(item: Item, nowTime: LocalTime, onOpen: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val color = Palette.of(item)
    val start = item.time
    val end = endOf(item)
    val past = start != null && end != null && end <= nowTime && !(item.kind == Kind.ALARM && start > nowTime)
    val inProgress = item.kind == Kind.EVENT && start != null && end != null && start <= nowTime && end > nowTime

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onOpen(item) }
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (past) 0.5f else 1f),
    ) {
        Column(Modifier.width(56.dp)) {
            Text(
                start?.format(Fmt.time) ?: "Весь",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (start == null) "день" else if (item.kind == Kind.EVENT) item.endTime.format(Fmt.time) else "",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
        Box(Modifier.width(4.dp).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.kind == Kind.ALARM) {
                    Icon(Icons.Outlined.Alarm, null, Modifier.size(16.dp), tint = color)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    item.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.notes.isNotBlank()) {
                Text(
                    item.notes.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (inProgress) SmallInfo(null, "Идёт сейчас", cs.primary)
                if (item.repeat != Repeat.NONE) SmallInfo(Icons.Outlined.Repeat, repeatLabel(item))
                if (item.reminder != null && item.kind != Kind.ALARM) {
                    SmallInfo(Icons.Outlined.NotificationsActive, if (item.fullScreen) "на весь экран" else "уведомление")
                }
            }
        }
    }
}
