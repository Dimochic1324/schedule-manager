package com.prilozhuha.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import com.prilozhuha.app.data.Repo
import com.prilozhuha.app.ui.AppTheme
import com.prilozhuha.app.ui.Fmt
import com.prilozhuha.app.ui.Palette
import com.prilozhuha.app.ui.cap
import com.prilozhuha.app.ui.rememberNow

/** Экран будильника: показывается поверх экрана блокировки и включает экран. */
class AlarmActivity : ComponentActivity() {

    private var itemId by mutableLongStateOf(0L)
    private var occurrence by mutableLongStateOf(0L)

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        Repo.init(this)
        readIntent(intent)
        ContextCompat.registerReceiver(
            this, closeReceiver, IntentFilter(AlarmService.ACTION_CLOSED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            AppTheme(dark = true) {
                AlarmScreen(
                    item = Repo.get(itemId),
                    occurrence = occurrence,
                    onDismiss = ::dismiss,
                    onSnooze = ::snooze,
                    onDone = ::done,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closeReceiver) }
        super.onDestroy()
    }

    private fun readIntent(i: Intent?) {
        itemId = i?.getLongExtra(AlarmScheduler.EXTRA_ID, 0L) ?: 0L
        occurrence = i?.getLongExtra(AlarmScheduler.EXTRA_WHEN, System.currentTimeMillis())
            ?: System.currentTimeMillis()
    }

    private fun dismiss() {
        AlarmService.stop(this)
        finish()
    }

    private fun snooze(minutes: Int) {
        AlarmScheduler.snooze(this, itemId, minutes, occurrence)
        AlarmService.stop(this)
        finish()
    }

    private fun done() {
        Repo.markDone(this, itemId)
        AlarmService.stop(this)
        finish()
    }

    companion object {
        fun intent(ctx: Context, id: Long, occurrence: Long): Intent =
            Intent(ctx, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, id)
                .putExtra(AlarmScheduler.EXTRA_WHEN, occurrence)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun AlarmScreen(
    item: Item?,
    occurrence: Long,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val now = rememberNow(seconds = true)
    val accent = item?.let { Palette.colors[it.color.coerceIn(0, Palette.colors.lastIndex)] } ?: Palette.colors[0]
    val icon = when (item?.kind) {
        Kind.EVENT -> Icons.Outlined.Event
        Kind.TASK -> Icons.Outlined.TaskAlt
        else -> Icons.Outlined.Alarm
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF101418), Color(0xFF101418))))
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                now.format(Fmt.time),
                fontSize = 84.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
            )
            Text(
                now.format(Fmt.dowDayMonth).cap(),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(40.dp))
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                item?.displayTitle ?: "Проверка будильника",
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                Notifications.describe(item, occurrence),
                fontSize = 17.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            val notes = item?.notes?.trim().orEmpty()
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(notes, color = Color.White, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            Text("Отложить на", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 30).forEach { m ->
                    OutlinedButton(
                        onClick = { onSnooze(m) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) { Text("$m мин", fontSize = 16.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (item != null && item.kind == Kind.TASK && !item.done) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853), contentColor = Color.White),
                ) {
                    Icon(Icons.Outlined.TaskAlt, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Выполнено", fontSize = 18.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(68.dp),
                shape = RoundedCornerShape(34.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF101418)),
            ) {
                Icon(Icons.Outlined.AlarmOff, null)
                Spacer(Modifier.size(8.dp))
                Text("Выключить", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
