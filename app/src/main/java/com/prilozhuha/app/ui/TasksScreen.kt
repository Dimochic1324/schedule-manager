package com.prilozhuha.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import java.time.LocalDate
import java.time.LocalTime

private const val OVERDUE = "Просрочено"

private fun groupTasks(tasks: List<Item>, today: LocalDate): List<Pair<String, List<Item>>> {
    val buckets = linkedMapOf<String, MutableList<Item>>(
        OVERDUE to mutableListOf(),
        "Сегодня" to mutableListOf(),
        "Завтра" to mutableListOf(),
        "Ближайшие 7 дней" to mutableListOf(),
        "Позже" to mutableListOf(),
        "Без срока" to mutableListOf(),
    )
    val weekEnd = today.plusDays(7)
    for (t in tasks) {
        if (t.done) continue
        val d = t.date
        val key = when {
            d == null -> "Без срока"
            d < today -> OVERDUE
            d == today -> "Сегодня"
            d == today.plusDays(1) -> "Завтра"
            d <= weekEnd -> "Ближайшие 7 дней"
            else -> "Позже"
        }
        buckets.getValue(key) += t
    }
    return buckets.filterValues { it.isNotEmpty() }.map { (k, v) ->
        k to v.sortedWith(
            compareBy<Item>({ it.date }, { it.time ?: LocalTime.MAX }, { -it.priority }, { it.id })
        )
    }
}

/** «Купить хлеб завтра» → задача «Купить хлеб» со сроком на завтра. */
private fun parseQuick(text: String, today: LocalDate): Pair<String, LocalDate?> {
    val t = text.trim()
    val lower = t.lowercase()
    val words = listOf("послезавтра" to 2L, "завтра" to 1L, "сегодня" to 0L)
    for ((w, plus) in words) {
        if (lower.endsWith(" $w") || lower == w) {
            return t.dropLast(w.length).trim().ifEmpty { t } to today.plusDays(plus)
        }
    }
    return t to null
}

@Composable
fun TasksScreen(
    items: List<Item>,
    onOpen: (Item) -> Unit,
    onToggle: (Item) -> Unit,
    onQuickAdd: (String, LocalDate?) -> Unit,
) {
    val now = rememberNow()
    val today = now.toLocalDate()
    val tasks = remember(items) { items.filter { it.kind == Kind.TASK } }
    val groups = remember(tasks, today) { groupTasks(tasks, today) }
    val done = remember(tasks) { tasks.filter { it.done }.sortedByDescending { it.doneAt } }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (text.isBlank()) return
        val (title, date) = parseQuick(text, today)
        onQuickAdd(title, date)
        text = ""
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Задачи",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Новая задача… (можно добавить «завтра»)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                if (text.isNotBlank()) {
                    IconButton(onClick = { submit() }) { Icon(Icons.AutoMirrored.Filled.Send, "Добавить") }
                }
            },
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            if (groups.isEmpty() && done.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.TaskAlt,
                        "Задач пока нет",
                        "Напишите задачу в поле выше или нажмите «+», чтобы указать срок и напоминание",
                    )
                }
            }
            groups.forEach { (title, list) ->
                item(key = "h_$title") {
                    SectionHeader(
                        title,
                        list.size.toString(),
                        color = if (title == OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                items(list, key = { it.id }) { TaskRow(it, now, onOpen, onToggle) }
            }
            if (done.isNotEmpty()) {
                item(key = "h_done") {
                    Row(
                        Modifier.fillMaxWidth().clickable { showDone = !showDone },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(
                            "Выполнено",
                            done.size.toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (showDone) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                            modifier = Modifier.padding(end = 16.dp, top = 12.dp),
                        )
                    }
                }
                if (showDone) {
                    items(done, key = { "d${it.id}" }) { TaskRow(it, now, onOpen, onToggle) }
                }
            }
        }
    }
}
