package com.prilozhuha.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.prilozhuha.app.MainActivity
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Recurrence
import com.prilozhuha.app.data.Repo
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {
    const val EXTRA_ID = "item_id"
    const val EXTRA_WHEN = "occurrence_at"
    const val EXTRA_MINUTES = "minutes"

    /** id для тестового будильника (не связан с записью). */
    const val TEST_ID = -1L

    private fun am(ctx: Context): AlarmManager = ctx.getSystemService(AlarmManager::class.java)

    fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun fireIntent(ctx: Context, id: Long, snooze: Boolean, occurrence: Long): PendingIntent {
        val kind = if (snooze) "snooze" else "item"
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .setData(Uri.parse("prilozhuha://$kind/$id"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_WHEN, occurrence)
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    private fun setExact(ctx: Context, at: Long, pi: PendingIntent, alarmClock: Boolean) {
        val am = am(ctx)
        try {
            if (!canExact(ctx)) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else if (alarmClock) {
                // setAlarmClock — самый надёжный способ: работает в режиме Doze и показывает значок будильника
                val show = PendingIntent.getActivity(
                    ctx, 1, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /**
     * Запланировать ближайшее срабатывание записи.
     * @param slackSec пропустить срабатывания, которые наступят в ближайшие N секунд (нужно сразу после звонка)
     */
    fun schedule(ctx: Context, item: Item, slackSec: Long = 0) {
        am(ctx).cancel(fireIntent(ctx, item.id, false, 0))
        val trig = Recurrence.nextTrigger(item, LocalDateTime.now().plusSeconds(slackSec)) ?: return
        val occurrence = trig.plusMinutes((item.reminder ?: 0).toLong())
        setExact(ctx, trig.toMillis(), fireIntent(ctx, item.id, false, occurrence.toMillis()), item.fullScreen)
    }

    fun rescheduleAll(ctx: Context) {
        Repo.init(ctx)
        Repo.items.value.forEach { schedule(ctx, it) }
    }

    fun cancel(ctx: Context, id: Long) {
        am(ctx).cancel(fireIntent(ctx, id, false, 0))
        am(ctx).cancel(fireIntent(ctx, id, true, 0))
    }

    fun snooze(ctx: Context, id: Long, minutes: Int, occurrence: Long) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setExact(ctx, at, fireIntent(ctx, id, true, occurrence), true)
    }

    /** Тестовый полноэкранный будильник через [seconds] секунд. */
    fun test(ctx: Context, seconds: Int = 10) {
        val at = System.currentTimeMillis() + seconds * 1000L
        setExact(ctx, at, fireIntent(ctx, TEST_ID, false, at), true)
    }
}
