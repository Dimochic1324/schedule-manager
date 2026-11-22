package com.prilozhuha.app.data

import android.content.Context
import com.prilozhuha.app.alarm.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/** Простое хранилище: все записи в одном JSON-файле во внутренней памяти приложения. */
object Repo {
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    private var file: File? = null
    private var lastId = 0L

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "items.json")
        file = f
        _items.value = runCatching {
            val arr = JSONArray(f.readText())
            List(arr.length()) { Item.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun get(id: Long): Item? = _items.value.firstOrNull { it.id == id }

    @Synchronized
    private fun save(list: List<Item>) {
        _items.value = list
        val f = file ?: return
        val tmp = File(f.parentFile, "items.json.tmp")
        tmp.writeText(JSONArray(list.map { it.toJson() }).toString())
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    @Synchronized
    private fun newId(): Long {
        val t = System.currentTimeMillis()
        lastId = if (t > lastId) t else lastId + 1
        return lastId
    }

    fun upsert(ctx: Context, item: Item): Item {
        init(ctx)
        val saved = if (item.id == 0L) item.copy(id = newId()) else item
        save(_items.value.filterNot { it.id == saved.id } + saved)
        AlarmScheduler.schedule(ctx, saved)
        return saved
    }

    fun delete(ctx: Context, id: Long) {
        init(ctx)
        save(_items.value.filterNot { it.id == id })
        AlarmScheduler.cancel(ctx, id)
    }

    /**
     * Отметить задачу выполненной / снять отметку.
     * У повторяющейся задачи отметка переносит срок на следующий повтор.
     */
    fun toggleDone(ctx: Context, id: Long) {
        val item = get(id) ?: return
        val now = System.currentTimeMillis()
        val date = item.date
        val updated = if (!item.done && item.repeat != Repeat.NONE && date != null) {
            val next = Recurrence.nextOccurrence(item, date)
            if (next != null) item.copy(date = next) else item.copy(done = true, doneAt = now)
        } else {
            item.copy(done = !item.done, doneAt = if (!item.done) now else 0)
        }
        upsert(ctx, updated)
    }

    fun markDone(ctx: Context, id: Long) {
        val item = get(id) ?: return
        if (item.kind == Kind.TASK && !item.done) toggleDone(ctx, id)
    }
}
