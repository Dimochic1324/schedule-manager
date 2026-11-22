package com.prilozhuha.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.prilozhuha.app.data.Repo

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0)
        val occurrence = intent.getLongExtra(AlarmScheduler.EXTRA_WHEN, System.currentTimeMillis())

        when (intent.action) {
            ACTION_FIRE -> {
                if (id == AlarmScheduler.TEST_ID) {
                    AlarmService.start(ctx, id, occurrence)
                    return
                }
                val item = Repo.get(id) ?: return
                if (item.fullScreen) AlarmService.start(ctx, id, occurrence)
                else Notifications.showReminder(ctx, item, occurrence)
                // сразу ставим следующий повтор
                AlarmScheduler.schedule(ctx, item, slackSec = 60)
            }

            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTES, 10)
                AlarmScheduler.snooze(ctx, id, minutes, occurrence)
                AlarmService.stop(ctx)
                Notifications.cancelReminder(ctx, id)
            }

            ACTION_DONE -> {
                Repo.markDone(ctx, id)
                AlarmService.stop(ctx)
                Notifications.cancelReminder(ctx, id)
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.prilozhuha.app.FIRE"
        const val ACTION_SNOOZE = "com.prilozhuha.app.SNOOZE"
        const val ACTION_DONE = "com.prilozhuha.app.DONE"

        fun snoozeIntent(ctx: Context, id: Long, minutes: Int, occurrence: Long): Intent =
            Intent(ctx, AlarmReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .setData(Uri.parse("prilozhuha://snooze-action/$id/$minutes"))
                .putExtra(AlarmScheduler.EXTRA_ID, id)
                .putExtra(AlarmScheduler.EXTRA_MINUTES, minutes)
                .putExtra(AlarmScheduler.EXTRA_WHEN, occurrence)

        fun doneIntent(ctx: Context, id: Long): Intent =
            Intent(ctx, AlarmReceiver::class.java)
                .setAction(ACTION_DONE)
                .setData(Uri.parse("prilozhuha://done/$id"))
                .putExtra(AlarmScheduler.EXTRA_ID, id)
    }
}

/** После перезагрузки, обновления приложения или смены времени заново ставим все будильники. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(ctx)
    }
}
