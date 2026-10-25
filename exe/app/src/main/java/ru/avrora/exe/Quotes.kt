package ru.avrora.exe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Общий цитатник: файл exe_quotes.json. Номера не переиспользуются. */
class QuoteStore(ctx: Context) {
    private val file = File(ctx.filesDir, "exe_quotes.json")

    fun load(): List<QuoteItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                QuoteItem(o.getInt("n"), o.getString("text"), o.optString("author", "user"),
                    o.optString("savedBy", "adonis"), o.optLong("at"), o.optLong("msgTime"))
            }
        }.getOrDefault(emptyList())
    }

    fun save(list: List<QuoteItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("n", it.n).put("text", it.text).put("author", it.author)
                .put("savedBy", it.savedBy).put("at", it.at).put("msgTime", it.msgTime))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
