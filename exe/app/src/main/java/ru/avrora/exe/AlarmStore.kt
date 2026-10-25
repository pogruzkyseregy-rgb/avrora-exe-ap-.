package ru.avrora.exe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Будильники: файл exe_alarms.json. */
class AlarmStore(ctx: Context) {
    private val file = File(ctx.filesDir, "exe_alarms.json")

    @Synchronized
    fun list(): List<AlarmItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
                AlarmItem(
                    o.getString("id"), o.optInt("hour"), o.optInt("minute"), days, o.optString("label", ""),
                    o.optString("by", "adonis"), o.optBoolean("enabled", true), o.optLong("onceAt"), o.optLong("snoozeAt"),
                    o.optLong("created"), o.optLong("lastFired"), o.optString("lastAction", ""), o.optLong("lastActionAt")
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(list: List<AlarmItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("hour", it.hour).put("minute", it.minute).put("days", JSONArray(it.days.sorted()))
                .put("label", it.label).put("by", it.by).put("enabled", it.enabled).put("onceAt", it.onceAt).put("snoozeAt", it.snoozeAt)
                .put("created", it.created).put("lastFired", it.lastFired).put("lastAction", it.lastAction).put("lastActionAt", it.lastActionAt))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    @Synchronized
    fun update(id: String, f: (AlarmItem) -> AlarmItem) = save(list().map { if (it.id == id) f(it) else it })

    fun get(id: String): AlarmItem? = list().firstOrNull { it.id == id }
}

