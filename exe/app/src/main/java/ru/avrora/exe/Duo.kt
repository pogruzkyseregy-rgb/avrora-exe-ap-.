package ru.avrora.exe

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ---------- вдвоём: Аврора и Геспера разговаривают без Адониса ----------
// Сервер ведёт весь разговор сразу (мысли «про себя», очередь, концовку, итоги в память), а приложение
// показывает его так, будто он идёт сейчас: у каждого сообщения своё время, и «печатает…» видно заранее.
// Если Адонис пишет в общий чат посреди разговора, ещё не показанное отменяется: они его заметили.

data class DuoMood(val word: String, val why: String, val at: Double)
data class DuoNote(val text: String, val at: Double)
data class DuoTalk(val at: Double, val starter: String, val topic: String, val turns: Int, val endedBy: String)
data class DuoState(
    val mood: Map<String, DuoMood> = emptyMap(),
    val about: Map<String, List<DuoNote>> = emptyMap(),
    val talks: List<DuoTalk> = emptyList()
)

/** Метка в trace сообщения: часть разговора вдвоём и мысль «про себя». */
fun duoTrace(thought: String, tools: String, changes: List<HesperaChange>): String =
    JSONObject(encodeHesperaTrace(tools, changes)).put("duo", true).put("thought", thought).toString()

fun isDuo(trace: String): Boolean = trace.contains("\"duo\":true")

fun duoThought(trace: String): String =
    if (!isDuo(trace)) "" else runCatching { JSONObject(trace).optString("thought", "") }.getOrDefault("")

object DuoApi {
    data class Line(val who: String, val text: String, val thought: String, val delay: Int, val trace: String)
    data class Talk(
        val lines: List<Line>, val private: List<Pair<String, String>>, val changes: List<HesperaChange>,
        val auroraMarkers: List<String>, val topic: String
    )

    private fun msgs(list: List<ChatMessage>, n: Int): JSONArray {
        val a = JSONArray()
        list.filter { it.role == "user" || it.role == "assistant" }.takeLast(n).forEach {
            a.put(JSONObject().put("role", it.role).put("content", TAG_RE.replace(it.content, "[фото]")))
        }
        return a
    }

    suspend fun talk(
        base: String, token: String, memory: String, persona: String, localTime: String,
        auroraPrivate: List<ChatMessage>, hesperaPrivate: List<ChatMessage>, group: List<ChatMessage>,
        auroraTools: List<String>, hesperaTools: List<String>, starter: String
    ): Talk {
        val body = JSONObject().put("memory", memory).put("persona", persona).put("local_time", localTime)
            .put("aurora_private", msgs(auroraPrivate, 15)).put("hespera_private", msgs(hesperaPrivate, 15))
            .put("group", HesperaApi.groupJson(group, 20)).put("aurora_tools", JSONArray(auroraTools))
            .put("hespera_tools", JSONArray(hesperaTools)).put("starter", starter).put("max_turns", 12)
        val r = AuroraApi.post(base, "/duo/talk", token, body)
        val arr = r.optJSONArray("messages") ?: JSONArray()
        val lines = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Line(it.optString("who"), it.optString("text"), it.optString("thought"), it.optInt("delay", 0),
                it.optJSONArray("trace")?.toString() ?: "[]")
        }.filter { it.text.isNotBlank() && (it.who == WHO_AURORA || it.who == WHO_HESPERA) }
        val pr = r.optJSONArray("private") ?: JSONArray()
        val priv = (0 until pr.length()).map { pr.getJSONObject(it) }.map { Pair(it.optString("who"), it.optString("text")) }
        val am = r.optJSONArray("aurora_markers") ?: JSONArray()
        return Talk(lines, priv, changesFromJson(r.optJSONArray("changes")), (0 until am.length()).map { am.getString(it) }, r.optString("topic"))
    }

    suspend fun state(base: String, token: String): DuoState {
        val r = AuroraApi.get(base, "/duo/state", token)
        val mood = HashMap<String, DuoMood>()
        r.optJSONObject("mood")?.let { m ->
            m.keys().forEach { k -> m.optJSONObject(k)?.let { mood[k] = DuoMood(it.optString("word"), it.optString("why"), it.optDouble("at", 0.0)) } }
        }
        val about = HashMap<String, List<DuoNote>>()
        r.optJSONObject("about")?.let { m ->
            m.keys().forEach { k ->
                val a = m.optJSONArray(k) ?: JSONArray()
                about[k] = (0 until a.length()).map { a.getJSONObject(it) }.map { DuoNote(it.optString("text"), it.optDouble("at", 0.0)) }
            }
        }
        val t = r.optJSONArray("talks") ?: JSONArray()
        val talks = (0 until t.length()).map { t.getJSONObject(it) }.map {
            DuoTalk(it.optDouble("at", 0.0), it.optString("starter"), it.optString("topic"), it.optInt("turns", 0), it.optString("ended_by"))
        }
        return DuoState(mood, about, talks)
    }
}

object DuoEngine {
    private val mutex = Mutex()
    /** Сколько разговоров в сутки: редко, обычно, часто. */
    val PER_DAY = intArrayOf(1, 3, 6)
    private val MIN_GAP_MIN = intArrayOf(8 * 60, 3 * 60, 90)

    /** Есть сообщения, которые ещё «пишутся»: разговор идёт прямо сейчас. */
    fun playing(group: List<ChatMessage>, now: Long = System.currentTimeMillis()): Boolean = group.any { it.time > now }

    /**
     * Проверка раз в 15 минут из фонового пробуждения. force — кнопка «Пусть поговорят сейчас».
     * Возвращает тему начатого разговора или null.
     */
    suspend fun tick(ctx: Context, force: Boolean): String? {
        val prefs = AppPrefs(ctx)
        val store = HesperaStore(ctx)
        if (prefs.token.isBlank()) return null
        val now = System.currentTimeMillis()
        val group = synchronized(HesperaStore.LOCK) { store.group() }
        if (playing(group, now)) return null
        if (!force) {
            if (!store.duoOn) return null
            val init = prefs.loadInit()
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (isQuietHour(hour, init.quietFrom, init.quietTo)) return null
            val f = store.duoFreq.coerceIn(0, 2)
            if (now - store.duoLast < MIN_GAP_MIN[f] * 60_000L) return null
            // Адонис сейчас сам в общем чате — не перебиваем
            val lastAdonis = group.lastOrNull { it.role == "user" }?.time ?: 0L
            if (now - lastAdonis < 40 * 60_000L) return null
            val quietHours = if (init.quietFrom == init.quietTo) 0 else (init.quietTo - init.quietFrom + 24) % 24
            val ticks = ((24 - quietHours).coerceAtLeast(4)) * 4
            if (Random.nextDouble() > PER_DAY[f].toDouble() / ticks * 1.6) return null
        }
        return mutex.withLock { run(ctx, "") }
    }

    private suspend fun run(ctx: Context, starter: String): String? {
        val prefs = AppPrefs(ctx)
        val store = HesperaStore(ctx)
        val now = System.currentTimeMillis()
        val group = synchronized(HesperaStore.LOCK) { store.group() }
        val auroraPrivate = ChatStore(ctx).load() + Inbox(ctx).peek()
        val mem = MemoryContext.build(MemoryStore(ctx).load(), "")
        val handReady = prefs.handConfigured && !prefs.handOff
        val auroraTools = buildList {
            if (prefs.toolSearch) add("поиск")
            if (prefs.toolRead) add("чтение")
            if (prefs.toolDraw) add("рисование")
            if (prefs.toolMusic) add("музыка")
        }
        store.duoLast = now
        val t = DuoApi.talk(
            prefs.serverUrl, prefs.token, mem, prefs.loadPersona().compile(), nowTimeLine(auroraPrivate),
            auroraPrivate, synchronized(HesperaStore.LOCK) { store.chat() }, group,
            auroraTools, store.tools(handReady), starter
        )
        if (t.lines.isEmpty()) return null
        // у каждого сообщения своё время: разговор разворачивается на глазах
        var at = System.currentTimeMillis()
        val lastHesp = t.lines.indexOfLast { it.who == WHO_HESPERA }
        val out = t.lines.mapIndexed { i, l ->
            at += l.delay * 1000L
            ChatMessage(l.who, l.text, at, auto = true, trace = duoTrace(l.thought, l.trace, if (i == lastHesp) t.changes else emptyList()))
        }
        synchronized(HesperaStore.LOCK) { store.saveGroup(store.group() + out) }
        store.duoUntil = out.last().time
        if (t.auroraMarkers.isNotEmpty()) ExeMarkers.apply(ctx, t.auroraMarkers.joinToString("\n"), now)
        t.private.forEach { (who, text) -> GroupEngine.toPrivate(ctx, who, text) }
        HesperaBus.signal()
        val first = out.first()
        HesperaNotifications.show(ctx, (if (first.role == WHO_AURORA) "Аврора" else "Геспера") + " заговорила с " +
            (if (first.role == WHO_AURORA) "Гесперой" else "Авророй") + ": " + first.content, target = "group")
        return t.topic.ifBlank { first.content.take(60) }
    }

    /** Адонис вошёл в разговор: то, что ещё не «написано», отменяется. */
    fun interrupt(ctx: Context) {
        val store = HesperaStore(ctx)
        val now = System.currentTimeMillis()
        synchronized(HesperaStore.LOCK) {
            val g = store.group()
            if (g.any { it.time > now }) store.saveGroup(g.filter { it.time <= now })
        }
        store.duoUntil = 0L
    }
}
