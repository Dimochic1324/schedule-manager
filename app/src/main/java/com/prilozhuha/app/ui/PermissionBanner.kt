package com.prilozhuha.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.prilozhuha.app.alarm.AlarmScheduler

private enum class Perm(val title: String, val text: String, val required: Boolean) {
    NOTIFICATIONS(
        "Разрешите уведомления",
        "Без них напоминания и будильники не смогут появиться.",
        true,
    ),
    EXACT_ALARMS(
        "Разрешите точные будильники",
        "Чтобы напоминания срабатывали минута в минуту.",
        true,
    ),
    FULL_SCREEN(
        "Разрешите полноэкранные уведомления",
        "Чтобы будильник открывался на весь экран, когда телефон заблокирован.",
        true,
    ),
    OVERLAY(
        "Показ поверх других приложений",
        "Чтобы будильник раскрывался на весь экран, даже когда вы пользуетесь телефоном. Без этого он появится сверху как баннер.",
        false,
    ),
}

private fun missing(ctx: Context): List<Perm> = buildList {
    val notifOk = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
    if (!notifOk) add(Perm.NOTIFICATIONS)
    if (!AlarmScheduler.canExact(ctx)) add(Perm.EXACT_ALARMS)
    if (Build.VERSION.SDK_INT >= 34 &&
        !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    ) add(Perm.FULL_SCREEN)
    if (!Settings.canDrawOverlays(ctx)) add(Perm.OVERLAY)
}

private fun settingsIntent(ctx: Context, p: Perm): Intent {
    val pkg = Uri.parse("package:${ctx.packageName}")
    return when (p) {
        Perm.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        Perm.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        Perm.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        Perm.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
    }
}

/** Карточка сверху, если не хватает разрешений для будильников. */
@Composable
fun PermissionBanner() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }
    var overlayHidden by remember { mutableStateOf(prefs.getBoolean("overlay_hidden", false)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick) { AlarmScheduler.rescheduleAll(ctx) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val list = remember(tick, overlayHidden) {
        missing(ctx).filter { it != Perm.OVERLAY || !overlayHidden }
    }
    val p = list.firstOrNull() ?: return

    fun open() {
        if (p == Perm.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notif_asked", false)) {
            prefs.edit().putBoolean("notif_asked", true).apply()
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { ctx.startActivity(settingsIntent(ctx, p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (p.required) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, null, Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleSmall)
                Text(p.text, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!p.required) {
                        TextButton(onClick = {
                            prefs.edit().putBoolean("overlay_hidden", true).apply()
                            overlayHidden = true
                        }) { Text("Не сейчас") }
                    }
                    TextButton(onClick = { open() }) { Text("Разрешить") }
                }
            }
        }
    }
}
