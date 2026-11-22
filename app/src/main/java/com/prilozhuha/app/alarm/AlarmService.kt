package com.prilozhuha.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.prilozhuha.app.data.Repo

/**
 * Сервис полноэкранного будильника: держит уведомление с full-screen intent,
 * играет звук будильника по кругу и вибрирует, пока пользователь не выключит или не отложит.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        Repo.init(this)
        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ID, 0L) ?: 0L
        val occurrence = intent?.getLongExtra(AlarmScheduler.EXTRA_WHEN, System.currentTimeMillis())
            ?: System.currentTimeMillis()

        val notification = Notifications.buildAlarm(this, id, occurrence)
        goForeground(notification, id, occurrence)

        acquireWakeLock()
        startSound()
        startVibration()

        // Если разрешено «поверх других приложений» — раскрываем экран будильника даже на разблокированном телефоне.
        if (Settings.canDrawOverlays(this)) {
            runCatching {
                startActivity(AlarmActivity.intent(this, id, occurrence).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopAlarm() }, AUTO_STOP_MS)
        return START_NOT_STICKY
    }

    private fun goForeground(n: android.app.Notification, itemId: Long, occurrence: Long) {
        val id = Notifications.ALARM_NOTIF_ID
        try {
            when {
                Build.VERSION.SDK_INT >= 34 ->
                    ServiceCompat.startForeground(this, id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
                Build.VERSION.SDK_INT >= 29 ->
                    ServiceCompat.startForeground(this, id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                else -> startForeground(id, n)
            }
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ServiceCompat.startForeground(this, id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(id, n)
                }
            } catch (e2: Exception) {
                Notifications.showAlarmFallback(this, itemId, occurrence)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Prilozhuha:alarm").apply {
            acquire(AUTO_STOP_MS + 5_000)
        }
    }

    private fun alarmUri(): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun startSound() {
        stopSound()
        val uri = alarmUri() ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        sendBroadcast(Intent(ACTION_CLOSED).setPackage(packageName))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.prilozhuha.app.STOP_ALARM"
        const val ACTION_CLOSED = "com.prilozhuha.app.ALARM_CLOSED"

        /** Если никто не выключил будильник за 10 минут — замолкаем, чтобы не сажать батарею. */
        private const val AUTO_STOP_MS = 10 * 60_000L

        fun start(ctx: Context, id: Long, occurrence: Long) {
            val i = Intent(ctx, AlarmService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, id)
                .putExtra(AlarmScheduler.EXTRA_WHEN, occurrence)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                // Система не дала запустить сервис — показываем хотя бы полноэкранное уведомление
                Notifications.showAlarmFallback(ctx, id, occurrence)
            }
        }

        fun stop(ctx: Context) {
            runCatching {
                ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP))
            }
            runCatching {
                androidx.core.app.NotificationManagerCompat.from(ctx).cancel(Notifications.ALARM_NOTIF_ID)
            }
        }
    }
}
