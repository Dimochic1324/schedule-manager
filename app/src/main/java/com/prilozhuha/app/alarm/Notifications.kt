package com.prilozhuha.app.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prilozhuha.app.MainActivity
import com.prilozhuha.app.R
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import com.prilozhuha.app.data.Repo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Notifications {
    const val CH_ALARM = "alarm_v1"
    const val CH_REMIND = "remind_v1"
    const val ALARM_NOTIF_ID = 4242

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CH_ALARM, "Полноэкранные будильники", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Звонок на весь экран. Звук и вибрацию включает само приложение."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val remind = NotificationChannel(CH_REMIND, "Обычные напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Короткое уведомление о событии или задаче"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
        }
        nm.createNotificationChannels(listOf(alarm, remind))
    }

    /** Строка «Событие в 14:30» / «Срок задачи: завтра, 18:00». */
    fun describe(item: Item?, occurrence: Long): String {
        if (item == null) return "Так будет выглядеть ваш будильник"
        val dt = Instant.ofEpochMilli(occurrence).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val today = LocalDate.now()
        val day = when (dt.toLocalDate()) {
            today -> ""
            today.plusDays(1) -> "завтра, "
            else -> dt.format(dateFmt) + ", "
        }
        val time = if (item.time == null) (if (day.isEmpty()) "сегодня" else day.trimEnd(',', ' '))
        else day + dt.format(timeFmt)
        return when (item.kind) {
            Kind.EVENT -> if (item.time == null) "Событие: $time" else "Событие в $time"
            Kind.TASK -> "Срок задачи: $time"
            Kind.ALARM -> "Будильник ${dt.format(timeFmt)}"
        }
    }

    fun buildAlarm(ctx: Context, id: Long, occurrence: Long): Notification {
        val item = Repo.get(id)
        val title = item?.displayTitle ?: "Проверка будильника"
        val full = PendingIntent.getActivity(
            ctx, 0, AlarmActivity.intent(ctx, id, occurrence),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            ctx, 0, Intent(ctx, AlarmService::class.java).setAction(AlarmService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, 0, AlarmReceiver.snoozeIntent(ctx, id, 10, occurrence),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CH_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(describe(item, occurrence))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Отложить 10 мин", snooze)
            .addAction(0, "Выключить", stop)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showAlarmFallback(ctx: Context, id: Long, occurrence: Long) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        NotificationManagerCompat.from(ctx).notify(ALARM_NOTIF_ID, buildAlarm(ctx, id, occurrence))
    }

    @SuppressLint("MissingPermission")
    fun showReminder(ctx: Context, item: Item, occurrence: Long) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, 0, AlarmReceiver.snoozeIntent(ctx, item.id, 10, occurrence),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = buildString {
            append(describe(item, occurrence))
            if (item.notes.isNotBlank()) append("\n").append(item.notes.trim())
        }
        val b = NotificationCompat.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.displayTitle)
            .setContentText(describe(item, occurrence))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (item.kind == Kind.TASK) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Отложить 10 мин", snooze)
        if (item.kind == Kind.TASK) {
            val done = PendingIntent.getBroadcast(
                ctx, 0, AlarmReceiver.doneIntent(ctx, item.id),
                PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, "Выполнено", done)
        }
        nm.notify(item.id.hashCode(), b.build())
    }

    fun cancelReminder(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(id.hashCode())
    }
}
