package com.prilozhuha.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import com.prilozhuha.app.data.Repeat
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")

fun String.cap(): String = replaceFirstChar { it.titlecase(RU) }

object Fmt {
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RU)
    val dowDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d MMM", RU)
    val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d MMMM yyyy", RU)

    fun monthYear(d: LocalDate): String =
        d.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).cap() + " " + d.year

    fun dowShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT_STANDALONE, RU).cap()
}

object Palette {
    val colors = listOf(
        Color(0xFF4285F4), // синий
        Color(0xFF0B8043), // зелёный
        Color(0xFFD50000), // красный
        Color(0xFFF4511E), // оранжевый
        Color(0xFFF6BF26), // жёлтый
        Color(0xFF8E24AA), // фиолетовый
        Color(0xFF039BE5), // голубой
        Color(0xFF616161), // графит
    )

    fun of(item: Item): Color = colors[item.color.coerceIn(0, colors.lastIndex)]

    val priority = listOf(Color.Unspecified, Color(0xFF4285F4), Color(0xFFF4B400), Color(0xFFDB4437))
    val priorityNames = listOf("Без приоритета", "Низкий", "Средний", "Высокий")
}

val WeekDayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

fun kindIcon(kind: Kind): ImageVector = when (kind) {
    Kind.EVENT -> Icons.Outlined.Event
    Kind.TASK -> Icons.Outlined.TaskAlt
    Kind.ALARM -> Icons.Outlined.Alarm
}

/** Текущее время, обновляется само (раз в 20 с, или каждую секунду при seconds = true). */
@Composable
fun rememberNow(seconds: Boolean = false): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(seconds) {
        while (true) {
            delay(if (seconds) 1_000 else 20_000)
            now = LocalDateTime.now()
        }
    }
    return now
}

/** Порядок внутри дня: сначала «весь день», потом по времени. */
fun List<Item>.sortedForDay(): List<Item> =
    sortedWith(compareBy<Item>({ it.time != null }, { it.startMin }, { it.kind.ordinal }))

fun isOverdue(item: Item, now: LocalDateTime): Boolean {
    if (item.kind != Kind.TASK || item.done) return false
    val d = item.date ?: return false
    val t = item.time ?: LocalTime.MAX
    return LocalDateTime.of(d, t).isBefore(now)
}

fun timeRange(item: Item): String = when {
    item.time == null -> "Весь день"
    item.kind == Kind.EVENT -> item.time.format(Fmt.time) + " – " + item.endTime.format(Fmt.time)
    else -> item.time.format(Fmt.time)
}

fun repeatLabel(item: Item): String = when (item.repeat) {
    Repeat.WEEKLY -> {
        val days = item.repeatDays.ifEmpty { setOfNotNull(item.date?.dayOfWeek?.value) }
        if (days.size == 7) "Каждый день" else "По " + days.sorted().joinToString(", ") { WeekDayNames[it - 1].lowercase() }
    }
    else -> item.repeat.label
}

fun deadlineText(item: Item, today: LocalDate): String {
    val d = item.date ?: return "Без срока"
    val day = when (d) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        today.minusDays(1) -> "Вчера"
        else -> d.format(Fmt.shortDate).cap()
    }
    return if (item.time != null) "$day, ${item.time.format(Fmt.time)}" else day
}

@Composable
fun SectionHeader(title: String, trailing: String? = null, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SmallInfo(icon: ImageVector?, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
fun TaskRow(task: Item, now: LocalDateTime, onOpen: (Item) -> Unit, onToggle: (Item) -> Unit) {
    val overdue = isOverdue(task, now)
    val prColor = Palette.priority.getOrElse(task.priority) { Color.Unspecified }
    val checkColor = if (prColor == Color.Unspecified) MaterialTheme.colorScheme.outline else prColor
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(task) }.padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = { onToggle(task) }) {
            Icon(
                if (task.done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (task.done) "Снять отметку" else "Выполнено",
                tint = if (task.done) MaterialTheme.colorScheme.primary else checkColor,
            )
        }
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text(
                task.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (task.notes.isNotBlank()) {
                Text(
                    task.notes.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hasMeta = task.date != null || task.repeat != Repeat.NONE || task.reminder != null
            if (hasMeta && !task.done) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (task.date != null) {
                        val c = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Text(
                            deadlineText(task, now.toLocalDate()),
                            style = MaterialTheme.typography.labelMedium,
                            color = c,
                            modifier = Modifier
                                .background(c.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (task.repeat != Repeat.NONE) SmallInfo(Icons.Outlined.Repeat, repeatLabel(task))
                    if (task.reminder != null) SmallInfo(Icons.Outlined.Notifications, if (task.fullScreen) "Будильник" else "")
                }
            }
        }
    }
}
