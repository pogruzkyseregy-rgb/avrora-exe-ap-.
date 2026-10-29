package ru.avrora.exe

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

// =====================================================================
// Геспера: второй ИИ. Кто она — внешность, характер, отношение к Адонису и Авроре — решает она сама,
// это хранится на сервере. Здесь, в приложении, только переписка (её чат и общий на троих), права и пробуждения.

const val WHO_AURORA = "aurora"
const val WHO_HESPERA = "hespera"
const val WHO_GROUP = "group"

/** Что она изменила в себе за ответ: показывается под её сообщением. */
data class HesperaChange(val kind: String, val field: String, val value: String, val why: String)

data class HesperaField(val field: String, val value: String, val why: String, val at: Double)
data class HesperaPost(
    val id: Long, val at: Double, val text: String, val media: String = "", val liked: Boolean = false,
    val likedAurora: Boolean = false, val comments: List<PostComment> = emptyList()
)

/** Комментарии с сервера: время там в секундах. */
fun commentsFromJson(a: JSONArray?): List<PostComment> =
    if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
        .map { PostComment(it.optString("who"), it.optString("text"), (it.optDouble("at", 0.0) * 1000).toLong()) }
data class HesperaProfile(
    val status: String = "",
    val born: Double = 0.0,
    val fields: List<HesperaField> = emptyList(),
    val lists: Map<String, List<String>> = emptyMap(),
    val changes: List<HesperaChange> = emptyList(),
    val changeTimes: List<Double> = emptyList(),
    val posts: List<HesperaPost> = emptyList(),
    val notes: Int = 0,
    val episodes: Int = 0,
    val avatarV: Long = 0,
)

fun changesFromJson(arr: JSONArray?): List<HesperaChange> =
    if (arr == null) emptyList() else (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        HesperaChange(o.optString("kind"), o.optString("field"), o.optString("value", o.optString("new")), o.optString("why"))
    }

fun changesToJson(list: List<HesperaChange>): String {
    val a = JSONArray()
    list.forEach { a.put(JSONObject().put("kind", it.kind).put("field", it.field).put("value", it.value).put("why", it.why)) }
    return a.toString()
}

/** Короткая подпись перемены: «решила о себе · внешность: …». */
fun changeLabel(c: HesperaChange): String = when (c.kind) {
    "я" -> "решила о себе · ${c.field}: ${c.value}"
    "интересы" -> "новый интерес: ${c.value}"
    "люблю" -> "любит: ${c.value}"
    "не люблю" -> "не любит: ${c.value}"
    "хочу узнать" -> "хочет узнать: ${c.value}"
    "узнала" -> "узнала: ${c.value}"
    "забыть" -> "вычеркнула: ${c.value}"
    "память" -> "запомнила: ${c.value}"
    "статус" -> "статус: ${c.value}"
    "пост" -> "пост на странице: ${c.value}"
    "аватар" -> "сменила аватар"
    "лайк" -> if (c.field.isBlank()) "лайк не поставлен: ${c.value}" else "лайкнула пост: ${c.value}"
    "коммент" -> "комментарий" + (if (c.why.isNotBlank()) " ${c.why}" else "") + ": ${c.value}"
    else -> "${c.kind}: ${c.value}"
}

// ---------- сервер ----------

object HesperaApi {
    /** Метки вложений Адониса — словами для модели; приложенные фото помечены как видимые. */
    private fun said(m: ChatMessage, attached: Set<String> = emptySet()): String =
        if (m.role == "user") describeForModel(m.content, emptyList(), emptyList(), attached) else m.content

    private fun msgsJson(list: List<ChatMessage>, limit: Int = 60, attached: Set<String> = emptySet()): JSONArray {
        val a = JSONArray()
        list.filter { it.role == "user" || it.role == "assistant" }.takeLast(limit)
            .forEach { a.put(JSONObject().put("role", it.role).put("content", said(it, attached))) }
        return a
    }

    /** Последние фото Адониса (не больше двух, из последних шести сообщений): позиция в отправленном списке и base64. */
    private suspend fun snapsFor(ctx: Context, sent: List<ChatMessage>): List<Triple<Int, String, String>> = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "sent")
        val out = ArrayList<Triple<Int, String, String>>()
        for (i in sent.indices.reversed()) {
            if (i < sent.size - 6 || out.size >= 2) break
            val m = sent[i]
            if (m.role != "user") continue
            val snap = parseParts(m.content).filterIsInstance<Part.Snap>().firstOrNull() ?: continue
            val b64 = encodeForModel(File(dir, snap.file)) ?: continue
            out.add(Triple(i, snap.file, b64))
        }
        out
    }

    private fun imagesJson(snaps: List<Triple<Int, String, String>>): JSONArray {
        val a = JSONArray()
        snaps.forEach { a.put(JSONObject().put("i", it.first).put("data", it.third)) }
        return a
    }

    private fun toolsJson(tools: List<String>) = JSONArray(tools)

    /** Общий чат глазами сервера: кто говорил (adonis, aurora, hespera) и что. */
    fun groupJson(list: List<ChatMessage>, limit: Int = 60, attached: Set<String> = emptySet()): JSONArray {
        val a = JSONArray()
        val now = System.currentTimeMillis()
        list.filter { it.role != "system" && it.time <= now }.takeLast(limit).forEach {
            a.put(JSONObject().put("speaker", if (it.role == "user") "adonis" else it.role).put("content", said(it, attached)))
        }
        return a
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() }

    data class Reply(val text: String, val changes: List<HesperaChange>, val trace: String, val toGroup: List<String>)

    suspend fun chat(ctx: Context, base: String, token: String, history: List<ChatMessage>, group: List<ChatMessage>, tools: List<String>, localTime: String): Reply {
        val page = ProfileStore(ctx).pageJson()
        val sent = history.filter { it.role == "user" || it.role == "assistant" }.takeLast(60)
        val snaps = snapsFor(ctx, sent)
        val body = JSONObject().put("messages", msgsJson(sent, attached = snaps.map { it.second }.toSet()))
            .put("images", imagesJson(snaps)).put("tools", toolsJson(tools)).put("local_time", localTime)
            .put("group", groupJson(group, 20)).put("page", page)
        val r = AuroraApi.post(base, "/hespera/chat", token, body)
        return Reply(r.optString("reply"), changesFromJson(r.optJSONArray("changes")), r.optJSONArray("trace")?.toString() ?: "",
            strings(r.optJSONArray("to_group")))
    }

    data class Wake(val message: String?, val wakeMin: Int?, val changes: List<HesperaChange>, val trace: String, val toGroup: List<String>)

    suspend fun wake(base: String, token: String, history: List<ChatMessage>, group: List<ChatMessage>, tools: List<String>, localTime: String, gapMin: Long, unanswered: Int,
                     page: JSONObject = JSONObject()): Wake {
        val body = JSONObject().put("messages", msgsJson(history, 50)).put("tools", toolsJson(tools)).put("local_time", localTime)
            .put("gap_minutes", gapMin).put("unanswered", unanswered).put("group", groupJson(group, 20)).put("page", page)
        val r = AuroraApi.post(base, "/hespera/wake", token, body)
        val wake = if (r.has("wake_min")) r.optInt("wake_min", 0).takeIf { it > 0 } else null
        val msg = if (r.optBoolean("skip", false)) null else r.optString("message", "").trim().ifBlank { null }
        return Wake(msg, wake, changesFromJson(r.optJSONArray("changes")), r.optJSONArray("trace")?.toString() ?: "",
            strings(r.optJSONArray("to_group")))
    }

    suspend fun reflect(base: String, token: String, history: List<ChatMessage>, upto: String): List<HesperaChange> {
        val r = AuroraApi.post(base, "/hespera/reflect", token, JSONObject().put("messages", msgsJson(history, 80)).put("upto", upto))
        return changesFromJson(r.optJSONArray("changes"))
    }

    suspend fun profile(base: String, token: String): HesperaProfile {
        val r = AuroraApi.get(base, "/hespera/profile", token)
        val fields = r.optJSONArray("fields")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }
            .map { HesperaField(it.optString("field"), it.optString("value"), it.optString("why"), it.optDouble("at")) } } ?: emptyList()
        val lists = mutableMapOf<String, List<String>>()
        r.optJSONObject("lists")?.let { o -> o.keys().forEach { k -> val a = o.getJSONArray(k); lists[k] = (0 until a.length()).map { a.getString(it) } } }
        val ch = r.optJSONArray("changes")
        val changes = changesFromJson(ch)
        val times = if (ch == null) emptyList() else (0 until ch.length()).map { ch.getJSONObject(it).optDouble("at") }
        val posts = r.optJSONArray("posts")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }
            .map { HesperaPost(it.optLong("id"), it.optDouble("at"), it.optString("text"), it.optString("media"), it.optBoolean("liked"),
                it.optBoolean("liked_aurora"), commentsFromJson(it.optJSONArray("comments"))) } } ?: emptyList()
        return HesperaProfile(r.optString("status"), r.optDouble("born"), fields, lists, changes, times, posts,
            r.optInt("notes"), r.optInt("episodes"), r.optLong("avatar_v"))
    }

    suspend fun like(base: String, token: String, id: Long, on: Boolean) {
        AuroraApi.post(base, "/hespera/like", token, JSONObject().put("id", id).put("on", on))
    }

    /** Комментарий под её постом (или удаление своего: deleteAt — время комментария). Возвращает комментарии поста. */
    suspend fun comment(base: String, token: String, id: Long, text: String, who: String = AUTHOR_ME, snippet: String = "", deleteAt: Long = 0L): List<PostComment> {
        val body = JSONObject().put("id", id).put("text", text).put("who", who).put("snippet", snippet)
        if (deleteAt > 0) body.put("delete_at", deleteAt / 1000.0)
        return commentsFromJson(AuroraApi.post(base, "/hespera/comment", token, body).optJSONArray("comments"))
    }

    /** Аватар, который она выбрала себе сама. null — ещё не выбрала. */
    suspend fun avatar(base: String, token: String): ByteArray? {
        val r = AuroraApi.get(base, "/hespera/avatar", token)
        val b64 = r.optString("b64")
        return if (b64.isBlank()) null else android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    }

    data class GroupReply(val who: String, val text: String, val private: List<String> = emptyList(), val markers: List<String> = emptyList())

    suspend fun group(
        ctx: Context, base: String, token: String, history: List<ChatMessage>, memory: String, persona: String, localTime: String,
        auroraPrivate: List<ChatMessage>, hesperaPrivate: List<ChatMessage>, responders: List<String>
    ): Pair<List<GroupReply>, List<HesperaChange>> {
        val nowT = System.currentTimeMillis()
        val sent = history.filter { it.role != "system" && it.time <= nowT }.takeLast(60)
        val snaps = snapsFor(ctx, sent)
        val body = JSONObject().put("messages", groupJson(sent, attached = snaps.map { it.second }.toSet()))
            .put("images", imagesJson(snaps))
            .put("memory", memory).put("persona", persona).put("local_time", localTime)
            .put("aurora_private", msgsJson(auroraPrivate, 15)).put("hespera_private", msgsJson(hesperaPrivate, 15))
            .put("responders", JSONArray(responders)).put("page", ProfileStore(ctx).pageJson())
        val r = AuroraApi.post(base, "/group/chat", token, body)
        val replies = r.optJSONArray("replies")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { GroupReply(it.optString("who"), it.optString("text"), strings(it.optJSONArray("private")), strings(it.optJSONArray("markers"))) } } ?: emptyList()
        return Pair(replies, changesFromJson(r.optJSONArray("changes")))
    }
}

// ---------- хранилище и настройки ----------

class HesperaStore(ctx: Context) {
    private val dir = ctx.filesDir
    private val prefs = ctx.getSharedPreferences("hespera", Context.MODE_PRIVATE)

    private fun load(name: String): List<ChatMessage> {
        val f = File(dir, name)
        if (!f.exists()) return emptyList()
        return runCatching { messagesFromJson(JSONArray(f.readText())) }.getOrDefault(emptyList())
    }

    private fun save(name: String, list: List<ChatMessage>) {
        val f = File(dir, name)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(messagesToJson(list.takeLast(2000)).toString())
        tmp.renameTo(f)
    }

    @Synchronized fun chat() = load("hespera_chat.json")
    @Synchronized fun saveChat(list: List<ChatMessage>) = save("hespera_chat.json", list)
    @Synchronized fun group() = load("group_chat.json")
    @Synchronized fun saveGroup(list: List<ChatMessage>) = save("group_chat.json", list)

    @Synchronized
    fun append(m: ChatMessage) = saveChat(chat() + m)

    var toolSearch: Boolean
        get() = prefs.getBoolean("t_search", true)
        set(v) { prefs.edit().putBoolean("t_search", v).apply() }
    var toolRead: Boolean
        get() = prefs.getBoolean("t_read", true)
        set(v) { prefs.edit().putBoolean("t_read", v).apply() }
    var toolHand: Boolean
        get() = prefs.getBoolean("t_hand", false)
        set(v) { prefs.edit().putBoolean("t_hand", v).apply() }
    var toolDraw: Boolean
        get() = prefs.getBoolean("t_draw", true)
        set(v) { prefs.edit().putBoolean("t_draw", v).apply() }
    var toolMusic: Boolean
        get() = prefs.getBoolean("t_music", true)
        set(v) { prefs.edit().putBoolean("t_music", v).apply() }
    var toolHear: Boolean
        get() = prefs.getBoolean("t_hear", true)
        set(v) { prefs.edit().putBoolean("t_hear", v).apply() }
    var wakeOn: Boolean
        get() = prefs.getBoolean("wake_on", true)
        set(v) { prefs.edit().putBoolean("wake_on", v).apply() }
    var nextWake: Long
        get() = prefs.getLong("next_wake", 0L)
        set(v) { prefs.edit().putLong("next_wake", v).apply() }
    var unanswered: Int
        get() = prefs.getInt("unanswered", 0)
        set(v) { prefs.edit().putInt("unanswered", v).apply() }
    var reflectedChat: Long
        get() = prefs.getLong("refl_chat", 0L)
        set(v) { prefs.edit().putLong("refl_chat", v).apply() }
    var reflectedGroup: Long
        get() = prefs.getLong("refl_group", 0L)
        set(v) { prefs.edit().putLong("refl_group", v).apply() }
    var chatWho: String
        get() = prefs.getString("chat_who", WHO_AURORA) ?: WHO_AURORA
        set(v) { prefs.edit().putString("chat_who", v).apply() }

    /** Разговаривают вдвоём без Адониса. */
    var duoOn: Boolean
        get() = prefs.getBoolean("duo_on", true)
        set(v) { prefs.edit().putBoolean("duo_on", v).apply() }
    /** 0 редко, 1 обычно, 2 часто. */
    var duoFreq: Int
        get() = prefs.getInt("duo_freq", 1)
        set(v) { prefs.edit().putInt("duo_freq", v).apply() }
    var duoLast: Long
        get() = prefs.getLong("duo_last", 0L)
        set(v) { prefs.edit().putLong("duo_last", v).apply() }
    var duoUntil: Long
        get() = prefs.getLong("duo_until", 0L)
        set(v) { prefs.edit().putLong("duo_until", v).apply() }
    var showThoughts: Boolean
        get() = prefs.getBoolean("show_thoughts", true)
        set(v) { prefs.edit().putBoolean("show_thoughts", v).apply() }

    fun tools(handReady: Boolean): List<String> = buildList {
        if (toolSearch) add("поиск")
        if (toolRead) add("чтение")
        if (toolHand && handReady) add("рука")
        if (toolDraw) add("рисование")
        if (toolMusic) add("музыка")
        if (toolHear) add("слух")
    }

    companion object {
        /** Пишут и экран, и фоновое пробуждение: один замок на всё. */
        val LOCK = Any()
    }
}

object HesperaBus {
    val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    fun signal() { flow.tryEmit(Unit) }
}

fun nowTimeLine(history: List<ChatMessage>): String {
    val lastUser = history.indexOfLast { it.role == "user" }
    return if (lastUser < 0) timeContext(System.currentTimeMillis(), java.util.TimeZone.getDefault(), 0L, 0L)
    else timeContext(System.currentTimeMillis(), java.util.TimeZone.getDefault(), history[lastUser].time, if (lastUser > 0) history[lastUser - 1].time else 0L)
}

// ---------- общий чат: одна логика для экрана, пробуждений и маркеров ----------
// Аврора и Геспера пишут и в личку, и в общий, как им удобнее. Память одна: в каждом запросе
// личный чат видит последнее из общего, а общий — последнее из личного.

private val GROUP_TO_RE = Regex("""\[в общий\s*:\s*([^\]]{1,1500})\]""", RegexOption.IGNORE_CASE)

/** Маркер «[в общий: …]» в ответе Авроры: возвращает текст без него и то, что надо сказать в общем чате. */
fun splitToGroup(text: String): Pair<String, List<String>> {
    val found = GROUP_TO_RE.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(1).toList()
    val clean = GROUP_TO_RE.replace(text, "").replace(Regex("\n{3,}"), "\n\n").trim()
    return Pair(clean, found)
}

object GroupEngine {
    private val mutex = kotlinx.coroutines.sync.Mutex()

    /** Последнее из общего чата — Авроре в память для лички и пробуждений. */
    fun groupBlock(ctx: Context): String {
        val now = System.currentTimeMillis()
        val g = synchronized(HesperaStore.LOCK) { HesperaStore(ctx).group() }.filter { it.role != "system" && it.time <= now }.takeLast(20)
        if (g.isEmpty()) return ""
        val names = mapOf("user" to "Адонис", WHO_AURORA to "Ты", WHO_HESPERA to "Геспера")
        return "ОБЩИЙ ЧАТ НА ТРОИХ — последние реплики (ты там тоже была, это та же ты):\n" +
            g.joinToString("\n") { "${names[it.role] ?: it.role}: ${TAG_RE.replace(it.content, "[фото]").take(400)}" }
    }

    private fun auroraPrivate(ctx: Context): List<ChatMessage> = ChatStore(ctx).load() + Inbox(ctx).peek()

    /**
     * Один круг общего чата: отвечают те, кому можно (responders, пусто — обе), ответы ложатся в общий чат,
     * а «[лично: …]» — в их личку. Возвращает ответы.
     */
    suspend fun converse(ctx: Context, responders: List<String> = emptyList()): List<HesperaApi.GroupReply> = mutex.withLock {
        val prefs = AppPrefs(ctx)
        val store = HesperaStore(ctx)
        val history = synchronized(HesperaStore.LOCK) { store.group() }
        val recent = history.filter { it.role == "user" }.takeLast(3).joinToString(" ") { it.content }
        val mem = MemoryContext.build(MemoryStore(ctx).load(), recent)
        val (replies, changes) = HesperaApi.group(
            ctx, prefs.serverUrl, prefs.token, history, mem, prefs.loadPersona().compile(), nowTimeLine(history),
            auroraPrivate(ctx), synchronized(HesperaStore.LOCK) { store.chat() }, responders
        )
        val now = System.currentTimeMillis()
        synchronized(HesperaStore.LOCK) {
            val g = store.group().toMutableList()
            replies.forEachIndexed { i, r ->
                if (r.text.isNotBlank()) {
                    val tr = if (r.who == WHO_HESPERA && i == replies.indexOfLast { it.who == WHO_HESPERA }) encodeHesperaTrace("", changes) else ""
                    g.add(ChatMessage(r.who, r.text, now + i, trace = tr))
                }
            }
            if (replies.none { it.text.isNotBlank() } && responders.isEmpty()) g.add(ChatMessage("system", "обе промолчали", now))
            store.saveGroup(g)
        }
        replies.forEach { r -> r.private.forEach { toPrivate(ctx, r.who, it) } }
        // лайки и комментарии к постам: Авроры — её маркерами, Гесперы — из перемен
        replies.filter { it.who == WHO_AURORA && it.markers.isNotEmpty() }.forEach { ExeMarkers.apply(ctx, it.markers.joinToString("\n"), now) }
        PageSync.apply(ctx, changes)
        HesperaBus.signal()
        replies.filter { it.text.isNotBlank() || it.private.isNotEmpty() }
    }

    /** Аврора или Геспера сама пишет в общий чат. Вторая может ответить — один раз, без бесконечной переписки ИИ с ИИ. */
    suspend fun autoPost(ctx: Context, who: String, text: String, notify: Boolean = true) {
        val store = HesperaStore(ctx)
        val busy = synchronized(HesperaStore.LOCK) {
            val g = store.group()
            val playing = DuoEngine.playing(g)
            val at = if (playing) (g.maxOf { it.time } + 20_000L) else System.currentTimeMillis()
            store.saveGroup(g + ChatMessage(who, text, at, auto = true))
            playing
        }
        HesperaBus.signal()
        if (busy) return   // они сейчас разговаривают вдвоём: реплика встанет после разговора
        if (notify) notifyGroup(ctx, who, text)
        val other = if (who == WHO_AURORA) WHO_HESPERA else WHO_AURORA
        val replies = runCatching { converse(ctx, listOf(other)) }.getOrDefault(emptyList())
        replies.filter { it.text.isNotBlank() }.forEach { if (notify) notifyGroup(ctx, it.who, it.text) }
    }

    /** Сообщение в личку от её имени (из общего чата, маркером «[лично: …]»). */
    fun toPrivate(ctx: Context, who: String, text: String) {
        if (who == WHO_AURORA) {
            Inbox(ctx).add(ChatMessage("assistant", text, auto = true))
            InboxBus.signal()
            AuroraNotifications.show(ctx, text)
        } else {
            synchronized(HesperaStore.LOCK) { HesperaStore(ctx).append(ChatMessage("assistant", text, auto = true)) }
            HesperaBus.signal()
            HesperaNotifications.show(ctx, text)
        }
    }

    private fun notifyGroup(ctx: Context, who: String, text: String) {
        val name = if (who == WHO_AURORA) "Аврора" else "Геспера"
        HesperaNotifications.show(ctx, "$name в общем чате: $text")
    }
}

// ---------- пробуждения: она просыпается сама, из любопытства ----------

class HesperaWakeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { HesperaEngine.tick(applicationContext, force = false) }
        runCatching { DuoEngine.tick(applicationContext, force = false) }
        return Result.success()
    }
}

object HesperaScheduler {
    private const val NAME = "hespera_wake"
    fun apply(ctx: Context, on: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (on) {
            val req = PeriodicWorkRequestBuilder<HesperaWakeWorker>(15L, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else wm.cancelUniqueWork(NAME)
    }
}

object HesperaEngine {
    private const val DEFAULT_WAKE_MIN = 90

    /** Одно пробуждение. force — по кнопке «Разбудить сейчас»: без ожидания и тихих часов. Возвращает её сообщение или null. */
    suspend fun tick(ctx: Context, force: Boolean): String? {
        val store = HesperaStore(ctx)
        val prefs = AppPrefs(ctx)
        val now = System.currentTimeMillis()
        if (prefs.token.isBlank()) return null
        if (!force) {
            if (!store.wakeOn || now < store.nextWake) return null
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val init = prefs.loadInit()
            if (isQuietHour(hour, init.quietFrom, init.quietTo)) {
                store.nextWake = now + 60 * 60_000L
                return null
            }
        }
        val history = synchronized(HesperaStore.LOCK) { store.chat() }
        val gap = ((now - (history.lastOrNull()?.time ?: now)) / 60_000L).coerceAtLeast(0)
        val handReady = prefs.handConfigured && !prefs.handOff
        val group = synchronized(HesperaStore.LOCK) { store.group() }
        val w = HesperaApi.wake(prefs.serverUrl, prefs.token, history, group, store.tools(handReady), nowTimeLine(history), gap, store.unanswered,
            ProfileStore(ctx).pageJson())
        store.nextWake = now + (w.wakeMin ?: DEFAULT_WAKE_MIN) * 60_000L
        PageSync.apply(ctx, w.changes)
        w.toGroup.forEach { GroupEngine.autoPost(ctx, WHO_HESPERA, it) }
        val msg = w.message ?: return (if (w.toGroup.isNotEmpty()) "в общий чат: " + w.toGroup.first() else null).also { HesperaBus.signal() }
        synchronized(HesperaStore.LOCK) {
            store.append(ChatMessage("assistant", msg, auto = true, trace = encodeHesperaTrace(w.trace, w.changes)))
        }
        store.unanswered = store.unanswered + 1
        HesperaNotifications.show(ctx, msg)
        HesperaBus.signal()
        return msg
    }
}

/** В trace сообщения Гесперы лежит и то, что она делала инструментами, и что поменяла в себе. */
fun encodeHesperaTrace(trace: String, changes: List<HesperaChange>): String =
    JSONObject().put("tools", trace.ifBlank { "[]" }).put("changes", changesToJson(changes)).toString()

fun decodeHesperaChanges(trace: String): List<HesperaChange> =
    runCatching { changesFromJson(JSONArray(JSONObject(trace).optString("changes", "[]"))) }.getOrDefault(emptyList())

fun decodeHesperaTools(trace: String): List<ToolTrace> =
    runCatching { traceFromJson(JSONObject(trace).optString("tools", "")) }.getOrDefault(emptyList())

object HesperaNotifications {
    private const val CHANNEL = "hespera_messages"
    private const val NOTIF_ID = 2002

    fun show(ctx: Context, text: String, target: String = "hespera") {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Сообщения Гесперы", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val open = android.app.PendingIntent.getActivity(
            ctx, 2, android.content.Intent(ctx, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open", target)
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(if (target == "group") "Аврора и Геспера" else "Геспера")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            androidx.core.app.NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) {
        }
    }

    fun clear(ctx: Context) = androidx.core.app.NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
}

// ---------- экранная модель ----------

class HesperaViewModel(app: Application) : AndroidViewModel(app) {
    private val store = HesperaStore(app)
    private val prefs = AppPrefs(app)
    private val ctx: Application = app

    val chat = mutableStateListOf<ChatMessage>().apply { addAll(store.chat()) }
    val group = mutableStateListOf<ChatMessage>().apply { addAll(store.group()) }
    var profile by mutableStateOf(HesperaProfile())
        private set
    var avatar by mutableStateOf<ByteArray?>(null)
        private set
    var sending by mutableStateOf(false)
        private set
    var groupSending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var waking by mutableStateOf(false)
        private set
    var chatWho by mutableStateOf(store.chatWho)
        private set

    var toolSearch by mutableStateOf(store.toolSearch)
        private set
    var toolRead by mutableStateOf(store.toolRead)
        private set
    var toolHand by mutableStateOf(store.toolHand)
        private set
    var toolDraw by mutableStateOf(store.toolDraw)
        private set
    var toolMusic by mutableStateOf(store.toolMusic)
        private set
    var toolHear by mutableStateOf(store.toolHear)
        private set
    var wakeOn by mutableStateOf(store.wakeOn)
        private set
    /** Когда она назначила себе проснуться. */
    val nextWakeAt: Long get() = store.nextWake

    init {
        HesperaScheduler.apply(app, store.wakeOn || store.duoOn)
        viewModelScope.launch { HesperaBus.flow.collect { reload() } }
        refreshProfile()
    }

    fun selectChat(who: String) { chatWho = who; store.chatWho = who }

    /** «Слушаю…» / «расшифровываю…», пока сервер работает с голосовым или треком. */
    var hearing by mutableStateOf<String?>(null)
        private set

    /** Голосовое или трек: на сервер (расшифровка/прослушивание), потом сообщением в её личку или в общий чат. */
    fun sendAudio(bytes: ByteArray, mime: String, name: String, voice: Boolean, groupWith: ChatViewModel?) {
        if (hearing != null) return
        hearing = if (voice) "● расшифровываю голосовое…" else "● слушаю трек…"
        error = null
        viewModelScope.launch {
            try {
                val h = AudioApi.upload(ctx, bytes, mime, name, voice)
                val tag = audioTag(voice, h, name)
                hearing = null
                if (groupWith != null) sendGroup(tag, groupWith) else send(tag)
            } catch (e: Exception) {
                hearing = null
                error = "Звук не дошёл: ${e.message ?: "нет связи"}"
            }
        }
    }

    /** Лайк её посту: сразу на экране, потом на сервере (она увидит в следующем разговоре). */
    fun likePost(post: HesperaPost) {
        val on = !post.liked
        profile = profile.copy(posts = profile.posts.map { if (it.id == post.id) it.copy(liked = on) else it })
        viewModelScope.launch {
            runCatching { HesperaApi.like(base(), token(), post.id, on) }.onFailure {
                profile = profile.copy(posts = profile.posts.map { if (it.id == post.id) it.copy(liked = !on) else it })
                error = "Лайк не дошёл: ${it.message ?: "нет связи"}"
            }
        }
    }

    /** Мой комментарий под её постом: сразу на экране, потом на сервере. */
    fun commentPost(post: HesperaPost, text: String) {
        val t = text.replace(Regex("\\s+"), " ").trim().take(COMMENT_MAX)
        if (t.isEmpty()) return
        val mine = PostComment(AUTHOR_ME, t, System.currentTimeMillis())
        profile = profile.copy(posts = profile.posts.map { if (it.id == post.id) it.copy(comments = it.comments + mine) else it })
        viewModelScope.launch {
            runCatching { HesperaApi.comment(base(), token(), post.id, t) }.onSuccess { cs ->
                profile = profile.copy(posts = profile.posts.map { if (it.id == post.id) it.copy(comments = cs) else it })
            }.onFailure {
                profile = profile.copy(posts = profile.posts.map { if (it.id == post.id) it.copy(comments = it.comments - mine) else it })
                error = "Комментарий не дошёл: ${it.message ?: "нет связи"}"
            }
        }
    }

    fun deleteComment(post: HesperaPost, c: PostComment) {
        if (c.who != AUTHOR_ME) return
        profile = profile.copy(posts = profile.posts.map { if (it.id == post.id) it.copy(comments = it.comments - c) else it })
        viewModelScope.launch {
            runCatching { HesperaApi.comment(base(), token(), post.id, "", deleteAt = c.at) }.onFailure { refreshProfile() }
        }
    }

    // ---------- вдвоём ----------
    var duoOn by mutableStateOf(store.duoOn)
        private set
    var duoFreq by mutableStateOf(store.duoFreq)
        private set
    var showThoughts by mutableStateOf(store.showThoughts)
        private set
    var duoBusy by mutableStateOf(false)
        private set
    var duo by mutableStateOf(DuoState())
        private set
    val duoLast: Long get() = store.duoLast

    fun setDuo(on: Boolean) {
        store.duoOn = on; duoOn = on
        HesperaScheduler.apply(ctx, on || store.wakeOn)
    }
    fun pickDuoFreq(f: Int) { store.duoFreq = f; duoFreq = f }
    fun setThoughts(on: Boolean) { store.showThoughts = on; showThoughts = on }

    /** «Пусть поговорят сейчас». */
    fun duoNow() {
        if (duoBusy || token().isBlank()) return
        duoBusy = true
        error = null
        viewModelScope.launch {
            try {
                val topic = DuoEngine.tick(ctx, force = true)
                reloadGroup()
                if (topic == null) error = "Разговор не сложился: попробуй ещё раз"
                refreshProfile()
            } catch (e: Exception) {
                error = e.message ?: "Нет связи"
            } finally {
                duoBusy = false
            }
        }
    }

    fun setTool(kind: String, on: Boolean) {
        when (kind) {
            "поиск" -> { store.toolSearch = on; toolSearch = on }
            "чтение" -> { store.toolRead = on; toolRead = on }
            "рука" -> { store.toolHand = on; toolHand = on }
            "рисование" -> { store.toolDraw = on; toolDraw = on }
            "музыка" -> { store.toolMusic = on; toolMusic = on }
            "слух" -> { store.toolHear = on; toolHear = on }
        }
    }

    fun setWake(on: Boolean) {
        store.wakeOn = on
        wakeOn = on
        HesperaScheduler.apply(ctx, on || store.duoOn)
    }

    fun reloadGroup() {
        val g = synchronized(HesperaStore.LOCK) { store.group() }
        if (g.size != group.size || g.lastOrNull()?.time != group.lastOrNull()?.time) {
            group.clear(); group.addAll(g)
        }
    }

    fun reload() {
        reloadGroup()
        val c = synchronized(HesperaStore.LOCK) { store.chat() }
        if (c.size != chat.size || c.lastOrNull()?.time != chat.lastOrNull()?.time) {
            chat.clear(); chat.addAll(c)
            refreshProfile()
        }
    }

    fun clearError() { error = null }

    private fun base() = prefs.serverUrl
    private fun token() = prefs.token
    private fun tools() = store.tools(prefs.handConfigured && !prefs.handOff)

    fun refreshProfile() {
        if (token().isBlank()) return
        viewModelScope.launch {
            runCatching { DuoApi.state(base(), token()) }.onSuccess { duo = it }
            runCatching { HesperaApi.profile(base(), token()) }.onSuccess { p ->
                val needAvatar = p.avatarV != profile.avatarV || avatar == null
                profile = p
                if (needAvatar && p.avatarV > 0) runCatching { HesperaApi.avatar(base(), token()) }.onSuccess { avatar = it }
            }
        }
    }

    /** Фото из галереи кладётся туда же, где фото для Авроры; в сообщении — метка [снимок:файл] и подпись. */
    private suspend fun withSnap(text: String, photo: Uri?): String? {
        val t = text.trim()
        if (photo == null) return t.ifEmpty { null }
        val name = try { withContext(Dispatchers.IO) { Library(ctx).saveSnap(photo) } } catch (e: Exception) {
            error = "Не удалось открыть фото"; return null
        }
        return if (t.isEmpty()) "[снимок:$name]" else "[снимок:$name]\n$t"
    }

    fun send(text: String, photo: Uri? = null) {
        if (sending || (text.isBlank() && photo == null)) return
        sending = true
        viewModelScope.launch {
            val content = withSnap(text, photo)
            sending = false
            if (content == null) return@launch
            chat.add(ChatMessage("user", content))
            store.unanswered = 0
            synchronized(HesperaStore.LOCK) { store.saveChat(chat.toList()) }
            ask()
        }
    }

    fun retry() { if (!sending && chat.lastOrNull()?.role == "user") ask() }

    private fun ask() {
        if (token().isBlank()) { error = "Укажи токен в настройках"; return }
        sending = true
        error = null
        val history = chat.toList()
        viewModelScope.launch {
            try {
                val g = synchronized(HesperaStore.LOCK) { store.group() }
                val r = HesperaApi.chat(ctx, base(), token(), history, g, tools(), nowTimeLine(history))
                if (r.text.isNotBlank() || r.toGroup.isEmpty()) {
                    chat.add(ChatMessage("assistant", r.text.ifBlank { "…" }, trace = encodeHesperaTrace(r.trace, r.changes)))
                } else {
                    chat.add(ChatMessage("system", "Геспера ответила в общем чате", trace = encodeHesperaTrace(r.trace, r.changes)))
                }
                synchronized(HesperaStore.LOCK) { store.saveChat(chat.toList()) }
                r.toGroup.forEach { t -> launch { runCatching { GroupEngine.autoPost(ctx, WHO_HESPERA, t, notify = false) }; reloadGroup() } }
                PageSync.apply(ctx, r.changes, notify = false)
                if (r.changes.isNotEmpty()) refreshProfile()
                maybeReflect()
            } catch (e: Exception) {
                error = e.message ?: "Нет связи"
            } finally {
                sending = false
            }
        }
    }

    /** Раз в 30 сообщений она пересматривает разговор: пишет эпизод в свою память и, если надо, меняет что-то в себе. */
    private fun maybeReflect() {
        val fresh = chat.filter { it.time > store.reflectedChat }
        if (fresh.size < REFLECT_EVERY) return
        val upto = fresh.last().time
        viewModelScope.launch {
            runCatching { HesperaApi.reflect(base(), token(), fresh, "chat-$upto") }.onSuccess {
                store.reflectedChat = upto
                refreshProfile()
            }
        }
    }

    fun sendGroup(text: String, aurora: ChatViewModel, photo: Uri? = null) {
        if (groupSending || (text.isBlank() && photo == null)) return
        groupSending = true
        viewModelScope.launch {
            val content = withSnap(text, photo)
            groupSending = false
            if (content == null) return@launch
            // вошёл посреди их разговора: недосказанное отменяется, дальше они говорят уже с ним
            DuoEngine.interrupt(ctx)
            reloadGroup()
            group.add(ChatMessage("user", content))
            synchronized(HesperaStore.LOCK) { store.saveGroup(group.toList()) }
            askGroup(aurora)
        }
    }

    fun retryGroup(aurora: ChatViewModel) { if (!groupSending && group.lastOrNull()?.role == "user") askGroup(aurora) }

    private fun askGroup(aurora: ChatViewModel) {
        if (token().isBlank()) { error = "Укажи токен в настройках"; return }
        groupSending = true
        error = null
        viewModelScope.launch {
            try {
                GroupEngine.converse(ctx)
                reloadGroup()
                reload()
                refreshProfile()
                maybeReflectGroup()
            } catch (e: Exception) {
                error = e.message ?: "Нет связи"
            } finally {
                groupSending = false
            }
        }
    }

    /** Общий чат тоже входит в её память: для неё Адонис и Аврора здесь собеседники, свои реплики — её. */
    private fun maybeReflectGroup() {
        val fresh = group.filter { it.time > store.reflectedGroup && it.role != "system" && !isDuo(it.trace) && it.time <= System.currentTimeMillis() }
        if (fresh.size < REFLECT_EVERY) return
        val upto = fresh.last().time
        val view = fresh.map {
            when (it.role) {
                WHO_HESPERA -> it.copy(role = "assistant")
                WHO_AURORA -> it.copy(role = "user", content = "[Аврора] " + it.content)
                else -> it.copy(role = "user", content = "[Адонис] " + it.content)
            }
        }
        viewModelScope.launch {
            runCatching { HesperaApi.reflect(base(), token(), view, "group-$upto") }.onSuccess {
                store.reflectedGroup = upto
                refreshProfile()
            }
        }
    }

    fun clearGroup() {
        group.clear()
        synchronized(HesperaStore.LOCK) { store.saveGroup(emptyList()) }
    }

    /** Кнопка «Разбудить сейчас»: проверить, как она проснётся, не дожидаясь своего времени. */
    fun wakeNow() {
        if (waking) return
        waking = true
        viewModelScope.launch {
            try {
                val msg = HesperaEngine.tick(ctx, force = true)
                reload()
                refreshProfile()
                if (msg == null) error = "Геспера проснулась и решила не писать (могла что-то оставить на странице)"
            } catch (e: Exception) {
                error = e.message ?: "Нет связи"
            } finally {
                waking = false
            }
        }
    }

    companion object {
        private const val REFLECT_EVERY = 30
    }
}

/** Лайки и комментарии Авроры к постам Гесперы: эти посты на сервере, поэтому запрос уходит туда в фоне. */
object HesperaSocial {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    fun likeByAurora(ctx: Context, snippet: String) {
        val p = AppPrefs(ctx)
        if (p.token.isBlank() || snippet.isBlank()) return
        scope.launch {
            runCatching {
                AuroraApi.post(p.serverUrl, "/hespera/like", p.token, JSONObject().put("who", WHO_AURORA).put("snippet", snippet).put("on", true))
            }
        }
    }

    fun commentByAurora(ctx: Context, snippet: String, text: String) {
        val p = AppPrefs(ctx)
        if (p.token.isBlank() || snippet.isBlank()) return
        scope.launch { runCatching { HesperaApi.comment(p.serverUrl, p.token, 0L, text, WHO_AURORA, snippet) } }
    }
}
