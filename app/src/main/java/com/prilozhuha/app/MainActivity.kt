package com.prilozhuha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.prilozhuha.app.alarm.AlarmScheduler
import com.prilozhuha.app.data.Item
import com.prilozhuha.app.data.Kind
import com.prilozhuha.app.data.Repo
import com.prilozhuha.app.ui.AppTheme
import com.prilozhuha.app.ui.CalendarScreen
import com.prilozhuha.app.ui.Drafts
import com.prilozhuha.app.ui.EditorDialog
import com.prilozhuha.app.ui.PermissionBanner
import com.prilozhuha.app.ui.TasksScreen
import com.prilozhuha.app.ui.TodayScreen
import com.prilozhuha.app.ui.kindIcon
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        setContent {
            AppTheme { MainScreen() }
        }
    }
}

private const val TAB_CALENDAR = 0
private const val TAB_TASKS = 1
private const val TAB_TODAY = 2

@Composable
private fun MainScreen() {
    val ctx = LocalContext.current
    val items by Repo.items.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var fabMenu by remember { mutableStateOf(false) }

    val tabs = listOf(
        "Календарь" to Icons.Outlined.CalendarMonth,
        "Задачи" to Icons.Outlined.Checklist,
        "Сегодня" to Icons.Outlined.Today,
    )
    val baseDate = if (tab == TAB_CALENDAR) selected else LocalDate.now()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, null) },
                        label = { Text(label) },
                    )
                }
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = {
                    if (tab == TAB_TASKS) editing = Drafts.new(Kind.TASK, null) else fabMenu = true
                }) { Icon(Icons.Filled.Add, "Создать") }
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    Kind.entries.forEach { k ->
                        DropdownMenuItem(
                            text = { Text(k.label) },
                            leadingIcon = { Icon(kindIcon(k), null) },
                            onClick = {
                                fabMenu = false
                                editing = Drafts.new(k, baseDate)
                            },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PermissionBanner()
            Box(Modifier.weight(1f)) {
                val open: (Item) -> Unit = { editing = it }
                val toggle: (Item) -> Unit = { Repo.toggleDone(ctx, it.id) }
                when (tab) {
                    TAB_CALENDAR -> CalendarScreen(
                        items = items,
                        selected = selected,
                        onSelect = { selected = it },
                        onOpen = open,
                        onCreateAt = { d, t -> editing = Drafts.new(Kind.EVENT, d, t) },
                    )
                    TAB_TASKS -> TasksScreen(
                        items = items,
                        onOpen = open,
                        onToggle = toggle,
                        onQuickAdd = { title, date ->
                            Repo.upsert(ctx, Drafts.new(Kind.TASK, date).copy(title = title))
                        },
                    )
                    else -> TodayScreen(
                        items = items,
                        onOpen = open,
                        onToggle = toggle,
                        onTestAlarm = { AlarmScheduler.test(ctx) },
                    )
                }
            }
        }
    }

    editing?.let { draft ->
        EditorDialog(
            initial = draft,
            onDismiss = { editing = null },
            onSave = {
                Repo.upsert(ctx, it)
                editing = null
            },
            onDelete = {
                Repo.delete(ctx, draft.id)
                editing = null
            },
        )
    }
}
