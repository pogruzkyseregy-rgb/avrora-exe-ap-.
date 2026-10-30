package ru.avrora.exe

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// =====================================================================
// Эст: на стороне Адониса, равная ему. Руководит Авророй и Гесперой: ставит задачи, проверяет, принимает
// или возвращает. Характер и доска задач — на сервере; здесь её чат, аватар (его ставит Адонис) и цепочка
// «задача → исполнительница делает → отчёт в общем чате → Эст проверяет».

const val WHO_EST = "est"

data class EstTask(
    val id: Long, val to: String, val text: String, val status: String, val at: Double,
    val report: String = "", val note: String = "", val runs: Int = 0
) {
    val open: Boolean get() = status == "new" || status == "redo"
}

fun taskStatusRu(s: String): String = when (s) {
    "new" -> "новая"
    "redo" -> "на доработке"
    "done" -> "ждёт проверки"
    "accepted" -> "принята"
    "cancelled" -> "снята"
    "failed" -> "не вышло"
    else -> s
}

class EstStore(ctx: Context) {
    private val dir = ctx.filesDir
    private val prefs = ctx.getSharedPreferences("est", Context.MODE_PRIVATE)

    @Synchronized
    fun chat(): List<ChatMessage> {
        val f = File(dir, "est_chat.json")
        if (!f.exists()) return emptyList()
        return runCatching { messagesFromJson(JSONArray(f.readText())) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveChat(list: List<ChatMessage>) {
        val f = File(dir, "est_chat.json")
        val tmp = File(f.path + ".tmp")
        tmp.writeText(messagesToJson(list.takeLast(2000)).toString())
        tmp.renameTo(f)
    }

    @Synchronized
    fun append(m: ChatMessage) = saveChat(chat() + m)

    val avatarFile: File get() = File(dir, "est_avatar.jpg")

    var wakeOn: Boolean
        get() = prefs.getBoolean("wake_on", true)
        set(v) { prefs.edit().putBoolean("wake_on", v).apply() }
    var nextWake: Long
        get() = prefs.getLong("next_wake", 0L)
        set(v) { prefs.edit().putLong("next_wake", v).apply() }
    var unanswered: Int
        get() = prefs.getInt("unanswered", 0)
        set(v) { prefs.edit().putInt("unanswered", v).apply() }
    var avatarV: Long
        get() = prefs.getLong("avatar_v", 0L)
        set(v) { prefs.edit().putLong("avatar_v", v).apply() }

    companion object { val LOCK = Any() }
}

object EstBus {
    val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    fun signal() { flow.tryEmit(Unit) }
}

// ---------- сервер ----------

object EstApi {
    data class Reply(val text: String, val toGroup: List<String>, val tasksNew: List<Long>, val tasksRedo: List<Long>,
                     val trace: String, val refused: List<String>)

    private fun longs(a: JSONArray?): List<Long> = if (a == null) emptyList() else (0 until a.length()).map { a.optLong(it) }.filter { it > 0 }
    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }

    suspend fun chat(ctx: Context, history: List<ChatMessage>, group: List<ChatMessage>, localTime: String): Reply {
        val p = AppPrefs(ctx)
        val sent = history.filter { it.role == "user" || it.role == "assistant" }.takeLast(60)
        val snaps = HesperaApi.snapsFor(ctx, sent)
        val body = JSONObject().put("messages", HesperaApi.msgsJson(sent, attached = snaps.map { it.second }.toSet()))
            .put("images", HesperaApi.imagesJson(snaps)).put("local_time", localTime).put("group", HesperaApi.groupJson(group, 20))
        val r = AuroraApi.post(p.serverUrl, "/est/chat", p.token, body)
        return Reply(r.optString("reply"), strings(r.optJSONArray("to_group")), longs(r.optJSONArray("tasks_new")),
            longs(r.optJSONArray("tasks_redo")), r.optJSONArray("trace")?.toString() ?: "", strings(r.optJSONArray("tasks_refused")))
    }

    data class Wake(val message: String?, val wakeMin: Int?, val toGroup: List<String>, val tasksNew: List<Long>, val tasksRedo: List<Long>, val trace: String)

    suspend fun wake(ctx: Context, history: List<ChatMessage>, group: List<ChatMessage>, localTime: String, gapMin: Long, unanswered: Int): Wake {
        val p = AppPrefs(ctx)
        val body = JSONObject().put("messages", HesperaApi.msgsJson(history, 50)).put("local_time", localTime)
            .put("gap_minutes", gapMin).put("unanswered", unanswered).put("group", HesperaApi.groupJson(group, 20))
        val r = AuroraApi.post(p.serverUrl, "/est/wake", p.token, body)
        val wake = if (r.has("wake_min")) r.optInt("wake_min", 0).takeIf { it > 0 } else null
        val msg = if (r.optBoolean("skip", false)) null else r.optString("message", "").trim().ifBlank { null }
        return Wake(msg, wake, strings(r.optJSONArray("to_group")), longs(r.optJSONArray("tasks_new")), longs(r.optJSONArray("tasks_redo")),
            r.optJSONArray("trace")?.toString() ?: "")
    }

    suspend fun tasks(ctx: Context): List<EstTask> {
        val p = AppPrefs(ctx)
        val a = AuroraApi.get(p.serverUrl, "/est/tasks", p.token).optJSONArray("tasks") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            EstTask(it.optLong("id"), it.optString("to"), it.optString("text"), it.optString("status"), it.optDouble("at"),
                it.optString("report"), it.optString("note"), it.optInt("runs"))
        }
    }

    suspend fun cancel(ctx: Context, id: Long) {
        val p = AppPrefs(ctx)
        AuroraApi.post(p.serverUrl, "/task/cancel", p.token, JSONObject().put("id", id))
    }

    data class Run(val skip: Boolean, val who: String, val text: String, val trace: String, val changes: List<HesperaChange>, val markers: List<String>)

    /** Исполнительница делает задачу сейчас. Для Авроры уходят её память, характер и права — как в её чате. */
    suspend fun run(ctx: Context, id: Long): Run {
        val p = AppPrefs(ctx)
        val store = HesperaStore(ctx)
        val group = synchronized(HesperaStore.LOCK) { store.group() }
        val handReady = p.handConfigured && !p.handOff
        val tasks = runCatching { tasks(ctx) }.getOrDefault(emptyList())
        val who = tasks.firstOrNull { it.id == id }?.to ?: WHO_AURORA
        val body = JSONObject().put("id", id).put("local_time", nowTimeLine(group)).put("group", HesperaApi.groupJson(group, 20))
            .put("page", withContext(Dispatchers.IO) { ProfileStore(ctx).pageJson() })
        if (who == WHO_AURORA) {
            body.put("memory", MemoryContext.build(MemoryStore(ctx).load(), "")).put("persona", p.loadPersona().compile())
            body.put("tools", JSONArray(buildList {
                if (p.toolSearch) add("поиск")
                if (p.toolRead) add("чтение")
                if (p.toolDraw) add("рисование")
                if (p.toolMusic) add("музыка")
                if (p.toolHear) add("слух")
                if (p.toolHand && handReady) add("рука")
            }))
        } else {
            body.put("tools", JSONArray(store.tools(handReady)))
        }
        val r = AuroraApi.post(p.serverUrl, "/task/run", p.token, body)
        return Run(r.optBoolean("skip"), r.optString("who", who), r.optString("text"), r.optJSONArray("trace")?.toString() ?: "",
            changesFromJson(r.optJSONArray("changes")), strings(r.optJSONArray("markers")))
    }
}

// ---------- задачи: исполнение и проверка ----------

object EstEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private const val DEFAULT_WAKE_MIN = 120

    /** Что сейчас идёт: «Аврора делает задачу №3…». Видно в общем чате. */
    var working by mutableStateOf<String?>(null)
        private set

    /** Новые и возвращённые задачи — исполнительницам. Не ждёт: работа идёт в фоне, отчёты появляются в общем чате. */
    fun dispatch(ctx: Context, ids: List<Long>, depth: Int = 0) {
        if (ids.isEmpty() || depth > 3) return
        val app = ctx.applicationContext
        scope.launch { mutex.withLock { ids.forEach { runOne(app, it, depth) } } }
    }

    private suspend fun runOne(ctx: Context, id: Long, depth: Int) {
        val tasks = runCatching { EstApi.tasks(ctx) }.getOrDefault(emptyList())
        val t = tasks.firstOrNull { it.id == id }
        val name = whoName(t?.to ?: WHO_AURORA)
        working = "$name делает задачу №$id…"
        HesperaBus.signal()
        val r = try {
            EstApi.run(ctx, id)
        } catch (e: Exception) {
            working = null
            postGroup(ctx, ChatMessage("system", "задача №$id не выполнилась: ${e.message ?: "нет связи"}"))
            return
        }
        working = null
        if (r.skip) { HesperaBus.signal(); return }
        val who = if (r.who == WHO_HESPERA) WHO_HESPERA else WHO_AURORA
        postGroup(ctx, ChatMessage(who, r.text, auto = true, trace = encodeHesperaTrace(r.trace, if (who == WHO_HESPERA) r.changes else emptyList())))
        if (who == WHO_AURORA && r.markers.isNotEmpty()) ExeMarkers.apply(ctx, r.markers.joinToString("\n"))
        if (who == WHO_HESPERA) PageSync.apply(ctx, r.changes)
        HesperaNotifications.show(ctx, "${whoName(who)} отчиталась по задаче №$id: ${r.text.take(140)}", target = "group")
        // Эст смотрит отчёт сразу
        working = "Эст проверяет №$id…"
        HesperaBus.signal()
        val replies = runCatching { GroupEngine.converse(ctx, listOf(WHO_EST), dispatchTasks = false) }.getOrDefault(emptyList())
        working = null
        HesperaBus.signal()
        val more = replies.flatMap { it.tasksNew + it.tasksRedo }
        if (more.isNotEmpty()) scope.launch { dispatchNested(ctx, more, depth + 1) }
    }

    private suspend fun dispatchNested(ctx: Context, ids: List<Long>, depth: Int) {
        if (depth > 3) return
        mutex.withLock { ids.forEach { runOne(ctx, it, depth) } }
    }

    private fun postGroup(ctx: Context, m: ChatMessage) {
        val store = HesperaStore(ctx)
        synchronized(HesperaStore.LOCK) {
            val g = store.group()
            val at = maxOf(System.currentTimeMillis(), (g.maxOfOrNull { it.time } ?: 0L) + 1)
            store.saveGroup(g + m.copy(time = at))
        }
        HesperaBus.signal()
    }

    /** Эст сказала что-то в общий чат сама (из лички или пробуждения). */
    fun toGroup(ctx: Context, text: String) {
        postGroup(ctx, ChatMessage(WHO_EST, text, auto = true))
    }

    /** Пробуждение: раз в 15 минут из фоновой задачи. force — по кнопке. Возвращает её сообщение или null. */
    suspend fun tick(ctx: Context, force: Boolean): String? {
        val store = EstStore(ctx)
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
        val history = synchronized(EstStore.LOCK) { store.chat() }
        val group = synchronized(HesperaStore.LOCK) { HesperaStore(ctx).group() }
        val gap = ((now - (history.lastOrNull()?.time ?: now)) / 60_000L).coerceAtLeast(0)
        val w = EstApi.wake(ctx, history, group, nowTimeLine(history), gap, store.unanswered)
        store.nextWake = now + (w.wakeMin ?: DEFAULT_WAKE_MIN).coerceAtLeast(30) * 60_000L
        w.toGroup.forEach { toGroup(ctx, it) }
        dispatch(ctx, w.tasksNew + w.tasksRedo)
        val msg = w.message ?: return (w.toGroup.firstOrNull()?.let { "в общий чат: $it" }).also { EstBus.signal() }
        synchronized(EstStore.LOCK) { store.append(ChatMessage("assistant", msg, auto = true, trace = encodeHesperaTrace(w.trace, emptyList()))) }
        store.unanswered = store.unanswered + 1
        EstNotifications.show(ctx, msg)
        EstBus.signal()
        return msg
    }
}

object EstNotifications {
    private const val CHANNEL = "est_messages"
    private const val NOTIF_ID = 2003

    fun show(ctx: Context, text: String) {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Сообщения Эст", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val open = android.app.PendingIntent.getActivity(
            ctx, 3, android.content.Intent(ctx, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open", "est")
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Эст")
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
}

// ---------- экранная модель ----------

class EstViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val store = EstStore(app)
    private val prefs = AppPrefs(app)

    val chat = mutableStateListOf<ChatMessage>().apply { addAll(store.chat()) }
    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var tasks by mutableStateOf<List<EstTask>>(emptyList())
        private set
    var avatarV by mutableStateOf(store.avatarV)
        private set
    var wakeOn by mutableStateOf(store.wakeOn)
        private set
    val avatarFile: File get() = store.avatarFile

    init {
        EstAvatarState.version = store.avatarV
        if (store.wakeOn) HesperaScheduler.apply(app, true)
        viewModelScope.launch { EstBus.flow.collect { reload() } }
        viewModelScope.launch { HesperaBus.flow.collect { refreshTasks() } }
        refreshTasks()
    }

    fun reload() {
        val c = synchronized(EstStore.LOCK) { store.chat() }
        if (c.size != chat.size || c.lastOrNull()?.time != chat.lastOrNull()?.time) { chat.clear(); chat.addAll(c) }
        refreshTasks()
    }

    fun refreshTasks() {
        if (prefs.token.isBlank()) return
        viewModelScope.launch { runCatching { EstApi.tasks(ctx) }.onSuccess { tasks = it } }
    }

    fun clearError() { error = null }

    fun setWake(on: Boolean) {
        store.wakeOn = on; wakeOn = on
        if (on) HesperaScheduler.apply(ctx, true)
    }

    fun cancelTask(t: EstTask) {
        viewModelScope.launch {
            runCatching { EstApi.cancel(ctx, t.id) }.onFailure { error = "Не снялось: ${it.message ?: "нет связи"}" }
            refreshTasks()
        }
    }

    /** Аватар Эст ставит Адонис: своя картинка из галереи. */
    fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching false
                    val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching false
                    val side = 512
                    val scale = side.toFloat() / maxOf(bmp.width, bmp.height)
                    val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                    store.avatarFile.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    true
                }.getOrDefault(false)
            }
            if (ok) { store.avatarV = System.currentTimeMillis(); avatarV = store.avatarV; EstAvatarState.version = avatarV } else error = "Не удалось открыть картинку"
        }
    }

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
            synchronized(EstStore.LOCK) { store.saveChat(chat.toList()) }
            ask()
        }
    }

    fun retry() { if (!sending && chat.lastOrNull()?.role == "user") ask() }

    private fun ask() {
        if (prefs.token.isBlank()) { error = "Укажи токен в настройках"; return }
        sending = true
        error = null
        val history = chat.toList()
        viewModelScope.launch {
            try {
                val g = synchronized(HesperaStore.LOCK) { HesperaStore(ctx).group() }
                val r = EstApi.chat(ctx, history, g, nowTimeLine(history))
                val note = if (r.refused.isNotEmpty()) "\n\n· не поставлено: " + r.refused.joinToString("; ") else ""
                chat.add(ChatMessage("assistant", (r.text.ifBlank { if (r.toGroup.isNotEmpty()) "· сказала в общем чате" else "…" }), trace = encodeHesperaTrace(r.trace, emptyList())))
                if (note.isNotBlank()) chat.add(ChatMessage("system", note.trim().removePrefix("· ")))
                synchronized(EstStore.LOCK) { store.saveChat(chat.toList()) }
                r.toGroup.forEach { EstEngine.toGroup(ctx, it) }
                EstEngine.dispatch(ctx, r.tasksNew + r.tasksRedo)
                refreshTasks()
            } catch (e: Exception) {
                error = e.message ?: "Нет связи"
            } finally {
                sending = false
            }
        }
    }

    var hearing by mutableStateOf<String?>(null)
        private set

    /** Голосовое или трек: сервер расшифровывает или слушает, в чат уходит метка с текстом. */
    fun sendAudio(bytes: ByteArray, mime: String, name: String, voice: Boolean) {
        if (hearing != null) return
        hearing = if (voice) "● расшифровываю голосовое…" else "● слушаю трек…"
        viewModelScope.launch {
            try {
                val h = AudioApi.upload(ctx, bytes, mime, name, voice)
                hearing = null
                send(audioTag(voice, h, name))
            } catch (e: Exception) {
                hearing = null
                error = "Звук не дошёл: ${e.message ?: "нет связи"}"
            }
        }
    }

    fun wakeNow() {
        if (sending) return
        sending = true
        viewModelScope.launch {
            try { EstEngine.tick(ctx, force = true) ?: run { error = "Эст решила промолчать" } } catch (e: Exception) { error = e.message ?: "Нет связи" }
            finally { sending = false; reload() }
        }
    }
}
