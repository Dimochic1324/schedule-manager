package com.prilozhuha.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Тип записи. Все три живут в одном хранилище и показываются в календаре. */
enum class Kind(val label: String) {
    EVENT("Событие"),
    TASK("Задача"),
    ALARM("Будильник"),
}

enum class Repeat(val label: String) {
    NONE("Не повторять"),
    DAILY("Каждый день"),
    WEEKDAYS("По будням (Пн–Пт)"),
    WEEKLY("Каждую неделю"),
    MONTHLY("Каждый месяц"),
    YEARLY("Каждый год"),
}

/**
 * Единая запись: событие календаря, задача (дедлайн = date/time) или будильник.
 *
 * @param date     дата события / срок задачи / дата будильника (для задачи может быть null — «без срока»)
 * @param time     время; null = «весь день» для событий, «без времени» для задач
 * @param reminder за сколько минут до начала/срока напомнить; null = без напоминания
 * @param fullScreen напоминание показывается на весь экран со звуком и вибрацией, как будильник
 * @param repeatDays для WEEKLY: номера дней недели 1 (Пн) … 7 (Вс)
 */
data class Item(
    val id: Long = 0,
    val kind: Kind,
    val title: String = "",
    val notes: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val durationMin: Int = 60,
    val done: Boolean = false,
    val doneAt: Long = 0,
    val color: Int = 0,
    val priority: Int = 0,
    val reminder: Int? = null,
    val fullScreen: Boolean = true,
    val repeat: Repeat = Repeat.NONE,
    val repeatDays: Set<Int> = emptySet(),
) {
    val startMin: Int get() = time?.let { it.hour * 60 + it.minute } ?: 0

    val endTime: LocalTime get() = (time ?: LocalTime.MIDNIGHT).plusMinutes(durationMin.toLong())

    val displayTitle: String
        get() = title.ifBlank { if (kind == Kind.ALARM) "Будильник" else "Без названия" }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("title", title)
        put("notes", notes)
        date?.let { put("date", it.toString()) }
        time?.let { put("time", it.toString()) }
        put("duration", durationMin)
        put("done", done)
        put("doneAt", doneAt)
        put("color", color)
        put("priority", priority)
        reminder?.let { put("reminder", it) }
        put("fullScreen", fullScreen)
        put("repeat", repeat.name)
        put("repeatDays", JSONArray(repeatDays.sorted()))
    }

    companion object {
        fun fromJson(o: JSONObject): Item {
            val days = o.optJSONArray("repeatDays")
            return Item(
                id = o.getLong("id"),
                kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrDefault(Kind.TASK),
                title = o.optString("title"),
                notes = o.optString("notes"),
                date = o.optString("date").takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) },
                time = o.optString("time").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) },
                durationMin = o.optInt("duration", 60),
                done = o.optBoolean("done"),
                doneAt = o.optLong("doneAt"),
                color = o.optInt("color"),
                priority = o.optInt("priority"),
                reminder = if (o.has("reminder")) o.getInt("reminder") else null,
                fullScreen = o.optBoolean("fullScreen", true),
                repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.NONE),
                repeatDays = buildSet {
                    if (days != null) for (i in 0 until days.length()) add(days.getInt(i))
                },
            )
        }
    }
}
