package ru.avrora.exe

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.launch
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
data class HesperaPost(val id: Long, val at: Double, val text: String)
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
    else -> "${c.kind}: ${c.value}"
}

// ---------- сервер ----------

object HesperaApi {
    private fun msgsJson(list: List<ChatMessage>, limit: Int = 60): JSONArray {
        val a = JSONArray()
        list.takeLast(limit).forEach { a.put(JSONObject().put("role", it.role).put("content", it.content)) }
        return a
    }

    private fun toolsJson(tools: List<String>) = JSONArray(tools)

    data class Reply(val text: String, val changes: List<HesperaChange>, val trace: String)

    suspend fun chat(base: String, token: String, history: List<ChatMessage>, tools: List<String>, localTime: String): Reply {
        val body = JSONObject().put("messages", msgsJson(history)).put("tools", toolsJson(tools)).put("local_time", localTime)
        val r = AuroraApi.post(base, "/hespera/chat", token, body)
        return Reply(r.optString("reply"), changesFromJson(r.optJSONArray("changes")), r.optJSONArray("trace")?.toString() ?: "")
    }

    data class Wake(val message: String?, val wakeMin: Int?, val changes: List<HesperaChange>, val trace: String)

    suspend fun wake(base: String, token: String, history: List<ChatMessage>, tools: List<String>, localTime: String, gapMin: Long, unanswered: Int): Wake {
        val body = JSONObject().put("messages", msgsJson(history, 50)).put("tools", toolsJson(tools)).put("local_time", localTime)
            .put("gap_minutes", gapMin).put("unanswered", unanswered)
        val r = AuroraApi.post(base, "/hespera/wake", token, body)
        val wake = if (r.has("wake_min")) r.optInt("wake_min", 0).takeIf { it > 0 } else null
        val msg = if (r.optBoolean("skip", false)) null else r.optString("message", "").trim().ifBlank { null }
        return Wake(msg, wake, changesFromJson(r.optJSONArray("changes")), r.optJSONArray("trace")?.toString() ?: "")
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
            .map { HesperaPost(it.optLong("id"), it.optDouble("at"), it.optString("text")) } } ?: emptyList()
        return HesperaProfile(r.optString("status"), r.optDouble("born"), fields, lists, changes, times, posts,
            r.optInt("notes"), r.optInt("episodes"), r.optLong("avatar_v"))
    }

    /** Аватар, который она выбрала себе сама. null — ещё не выбрала. */
    suspend fun avatar(base: String, token: String): ByteArray? {
        val r = AuroraApi.get(base, "/hespera/avatar", token)
        val b64 = r.optString("b64")
        return if (b64.isBlank()) null else android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    }

    data class GroupReply(val who: String, val text: String)

    suspend fun group(base: String, token: String, history: List<ChatMessage>, memory: String, persona: String, localTime: String): Pair<List<GroupReply>, List<HesperaChange>> {
        val a = JSONArray()
        history.takeLast(60).forEach {
            a.put(JSONObject().put("speaker", if (it.role == "user") "adonis" else it.role).put("content", it.content))
        }
        val body = JSONObject().put("messages", a).put("memory", memory).put("persona", persona).put("local_time", localTime)
        val r = AuroraApi.post(base, "/group/chat", token, body)
        val replies = r.optJSONArray("replies")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { GroupReply(it.optString("who"), it.optString("text")) } } ?: emptyList()
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

    fun tools(handReady: Boolean): List<String> = buildList {
        if (toolSearch) add("поиск")
        if (toolRead) add("чтение")
        if (toolHand && handReady) add("рука")
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

// ---------- пробуждения: она просыпается сама, из любопытства ----------

class HesperaWakeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { HesperaEngine.tick(applicationContext, force = false) }
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
        val w = HesperaApi.wake(prefs.serverUrl, prefs.token, history, store.tools(handReady), nowTimeLine(history), gap, store.unanswered)
        store.nextWake = now + (w.wakeMin ?: DEFAULT_WAKE_MIN) * 60_000L
        val msg = w.message ?: return null.also { HesperaBus.signal() }
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

    fun show(ctx: Context, text: String) {
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
                putExtra("open", "hespera")
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Геспера")
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
    var wakeOn by mutableStateOf(store.wakeOn)
        private set

    init {
        HesperaScheduler.apply(app, store.wakeOn)
        viewModelScope.launch { HesperaBus.flow.collect { reload() } }
        refreshProfile()
    }

    fun selectChat(who: String) { chatWho = who; store.chatWho = who }

    fun setTool(kind: String, on: Boolean) {
        when (kind) {
            "поиск" -> { store.toolSearch = on; toolSearch = on }
            "чтение" -> { store.toolRead = on; toolRead = on }
            "рука" -> { store.toolHand = on; toolHand = on }
        }
    }

    fun setWake(on: Boolean) {
        store.wakeOn = on
        wakeOn = on
        HesperaScheduler.apply(ctx, on)
    }

    fun reload() {
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
            runCatching { HesperaApi.profile(base(), token()) }.onSuccess { p ->
                val needAvatar = p.avatarV != profile.avatarV || avatar == null
                profile = p
                if (needAvatar && p.avatarV > 0) runCatching { HesperaApi.avatar(base(), token()) }.onSuccess { avatar = it }
            }
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        val m = ChatMessage("user", t)
        chat.add(m)
        store.unanswered = 0
        synchronized(HesperaStore.LOCK) { store.saveChat(chat.toList()) }
        ask()
    }

    fun retry() { if (!sending && chat.lastOrNull()?.role == "user") ask() }

    private fun ask() {
        if (token().isBlank()) { error = "Укажи токен в настройках"; return }
        sending = true
        error = null
        val history = chat.toList()
        viewModelScope.launch {
            try {
                val r = HesperaApi.chat(base(), token(), history, tools(), nowTimeLine(history))
                chat.add(ChatMessage("assistant", r.text, trace = encodeHesperaTrace(r.trace, r.changes)))
                synchronized(HesperaStore.LOCK) { store.saveChat(chat.toList()) }
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

    fun sendGroup(text: String, aurora: ChatViewModel) {
        val t = text.trim()
        if (t.isEmpty() || groupSending) return
        group.add(ChatMessage("user", t))
        synchronized(HesperaStore.LOCK) { store.saveGroup(group.toList()) }
        askGroup(aurora)
    }

    fun retryGroup(aurora: ChatViewModel) { if (!groupSending && group.lastOrNull()?.role == "user") askGroup(aurora) }

    private fun askGroup(aurora: ChatViewModel) {
        if (token().isBlank()) { error = "Укажи токен в настройках"; return }
        groupSending = true
        error = null
        val history = group.toList()
        val recent = history.filter { it.role == "user" }.takeLast(3).joinToString(" ") { it.content }
        val (mem, persona) = aurora.auroraForGroup(recent)
        viewModelScope.launch {
            try {
                val (replies, changes) = HesperaApi.group(base(), token(), history, mem, persona, nowTimeLine(history))
                replies.forEachIndexed { i, r ->
                    val tr = if (r.who == WHO_HESPERA && i == replies.indexOfLast { it.who == WHO_HESPERA }) encodeHesperaTrace("", changes) else ""
                    group.add(ChatMessage(r.who, r.text, trace = tr))
                }
                if (replies.isEmpty()) group.add(ChatMessage("system", "обе промолчали"))
                synchronized(HesperaStore.LOCK) { store.saveGroup(group.toList()) }
                if (changes.isNotEmpty()) refreshProfile()
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
        val fresh = group.filter { it.time > store.reflectedGroup && it.role != "system" }
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
