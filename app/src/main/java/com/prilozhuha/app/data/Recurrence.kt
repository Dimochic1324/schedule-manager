package com.prilozhuha.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.min

object Recurrence {

    /** Время, в которое срабатывает напоминание для записей без времени. */
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    private const val SEARCH_DAYS = 800

    fun occursOn(item: Item, day: LocalDate): Boolean {
        val start = item.date ?: return false
        if (day < start) return false
        return when (item.repeat) {
            Repeat.NONE -> day == start
            Repeat.DAILY -> true
            Repeat.WEEKDAYS -> day.dayOfWeek.value <= 5
            Repeat.WEEKLY ->
                if (item.repeatDays.isEmpty()) day.dayOfWeek == start.dayOfWeek
                else day.dayOfWeek.value in item.repeatDays
            Repeat.MONTHLY -> day.dayOfMonth == min(start.dayOfMonth, day.lengthOfMonth())
            Repeat.YEARLY -> day.month == start.month &&
                day.dayOfMonth == min(start.dayOfMonth, day.lengthOfMonth())
        }
    }

    /**
     * Что показывать в календаре в этот день.
     * События и будильники разворачиваются по повторам; задача показывается в день своего текущего срока.
     */
    fun itemsOn(items: List<Item>, day: LocalDate): List<Item> = items.filter {
        if (it.kind == Kind.TASK) it.date == day else occursOn(it, day)
    }

    fun nextOccurrence(item: Item, after: LocalDate): LocalDate? {
        var d = after.plusDays(1)
        for (i in 0 until SEARCH_DAYS) {
            if (occursOn(item, d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** Ближайший момент срабатывания напоминания строго после [now], либо null. */
    fun nextTrigger(item: Item, now: LocalDateTime): LocalDateTime? {
        val offset = (item.reminder ?: return null).toLong()
        val start = item.date ?: return null
        if (item.kind == Kind.TASK && item.done) return null
        val t = item.time ?: DEFAULT_TIME

        if (item.repeat == Repeat.NONE) {
            val trig = LocalDateTime.of(start, t).minusMinutes(offset)
            return if (trig.isAfter(now)) trig else null
        }
        var d = maxOf(start, now.toLocalDate())
        for (i in 0 until SEARCH_DAYS) {
            if (occursOn(item, d)) {
                val trig = LocalDateTime.of(d, t).minusMinutes(offset)
                if (trig.isAfter(now)) return trig
            }
            d = d.plusDays(1)
        }
        return null
    }
}
