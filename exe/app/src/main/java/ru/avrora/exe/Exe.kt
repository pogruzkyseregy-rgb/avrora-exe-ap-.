package ru.avrora.exe

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

// ---------- что она делала инструментами ----------

data class ToolTrace(val kind: String, val arg: String, val ok: Boolean, val out: String)

fun traceFromJson(s: String): List<ToolTrace> {
    if (s.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(s)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ToolTrace(o.optString("kind"), o.optString("arg"), o.optBoolean("ok", false), o.optString("out"))
        }
    }.getOrDefault(emptyList())
}

/** Короткие строки о недавних действиях: так она помнит, что делала руками и поиском, в следующих ответах. */
fun recentActions(messages: List<ChatMessage>): List<String> =
    messages.filter { it.role == "assistant" && it.trace.isNotBlank() }
        .takeLast(4)
        .flatMap { traceFromJson(it.trace) }
        .takeLast(6)
        .map {
            val res = it.out.replace('\n', ' ').take(90)
            "${it.kind}: ${it.arg.replace('\n', ' ').take(70)} → " + if (it.ok) res else "не вышло: $res"
        }

// ---------- хранилище: состояние, назначения, следы ----------

class ExeStore(ctx: Context) {
    private val dir = ctx.filesDir
    private fun file(name: String) = File(dir, name)

    private fun readArr(name: String): JSONArray =
        runCatching { JSONArray(file(name).readText()) }.getOrElse { JSONArray() }

    private fun writeArr(name: String, arr: JSONArray) {
        val f = file(name)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    // состояние
    @Synchronized
    fun states(): List<StateEntry> {
        val a = readArr("exe_states.json")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            StateEntry(o.optLong("at"), o.optString("name"), o.optString("why"))
        }
    }

    @Synchronized
    fun addState(s: StateEntry) {
        val list = (states() + s).takeLast(60)
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("at", it.at).put("name", it.name).put("why", it.why)) }
        writeArr("exe_states.json", a)
    }

    // следы
    @Synchronized
    fun notes(): List<NoteEntry> {
        val a = readArr("exe_notes.json")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            NoteEntry(o.optString("id"), o.optLong("at"), o.optString("kind"), o.optString("text"))
        }
    }

    private fun saveNotes(list: List<NoteEntry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("at", it.at).put("kind", it.kind).put("text", it.text)) }
        writeArr("exe_notes.json", a)
    }

    @Synchronized
    fun addNote(n: NoteEntry) = saveNotes((notes() + n).takeLast(150))

    @Synchronized
    fun removeNote(id: String) = saveNotes(notes().filter { it.id != id })

    // назначения
    @Synchronized
    fun plans(): List<PlanItem> {
        val a = readArr("exe_plans.json")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            PlanItem(o.optString("id"), o.optLong("at"), o.optString("topic"), o.optLong("created"), o.optString("status", "pending"), o.optString("outcome"))
        }
    }

    private fun savePlans(list: List<PlanItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("at", it.at).put("topic", it.topic).put("created", it.created).put("status", it.status).put("outcome", it.outcome))
        }
        writeArr("exe_plans.json", a)
    }

    @Synchronized
    fun addPlan(p: PlanItem) = savePlans((plans() + p).takeLast(40))

    @Synchronized
    fun updatePlan(id: String, f: (PlanItem) -> PlanItem) = savePlans(plans().map { if (it.id == id) f(it) else it })

    @Synchronized
    fun removePlan(id: String) = savePlans(plans().filter { it.id != id })
}

// ---------- применение её маркеров ----------

object ExeMarkers {
    /**
     * Состояние, назначения и следы из её текста. Вызывается один раз для каждого её сообщения:
     * для ответа в чате из ViewModel, для сообщений, написанных в фоне, из движка инициативы.
     */
    @Synchronized
    fun apply(ctx: Context, content: String, now: Long = System.currentTimeMillis()) {
        val parts = parseParts(content)
        if (parts.any { it is Part.Silence }) return
        val store = ExeStore(ctx)
        for (p in parts) {
            when (p) {
                is Part.State -> store.addState(StateEntry(now, p.name.take(60), p.why.take(300)))
                is Part.Note -> store.addNote(NoteEntry(newId("t"), now, p.kind, p.text))
                is Part.Plan -> {
                    val d = acceptPlan(store.plans(), p.whenText, p.topic, now, newId("n"))
                    val plan = d.plan
                    if (plan != null) {
                        store.addPlan(plan)
                        PlanScheduler.schedule(ctx, plan)
                    } else {
                        // Отказ не прячется: он попадёт в список ограничений, и она увидит причину
                        store.addPlan(
                            PlanItem(newId("n"), 0L, p.topic.take(200), now, "dropped", "не принято: ${d.why}")
                        )
                    }
                }
                is Part.Post -> if (p.text.isNotBlank()) {
                    val ps = ProfileStore(ctx)
                    if (ps.countSince(AUTHOR_HER, now - 24 * 3_600_000L) < POST_DAILY_CAP) {
                        // «[пост: текст | картинка]» — приложить то, что она нарисовала в этом же сообщении
                        val drawn = Regex("""\s*\|\s*(картинк|рисун)\w*\s*$""", RegexOption.IGNORE_CASE).find(p.text)
                        val media = if (drawn != null) parts.filterIsInstance<Part.Media>().firstOrNull { !it.audio } else null
                        val text = if (drawn != null) p.text.substring(0, drawn.range.first).trim() else p.text
                        val photo = when {
                            media != null -> "медиа:${media.id}"
                            p.photoId.isNotBlank() && photoExists(ctx, p.photoId) -> "фото:${p.photoId}"
                            else -> ""
                        }
                        ps.addPost(PostItem(newId("p"), AUTHOR_HER, text.take(POST_MAX), photo, now))
                    }
                }
                is Part.Status -> if (p.text.isNotBlank()) ProfileStore(ctx).setStatus(AUTHOR_HER, p.text, now)
                is Part.Like -> {
                    val ps = ProfileStore(ctx)
                    findPost(p.snippet, ps.posts(), AUTHOR_ME)?.let { ps.setLike(it.id, AUTHOR_HER, true) }
                }
                is Part.Avatar -> if (photoExists(ctx, p.photoId)) ProfileStore(ctx).setAvatar(AUTHOR_HER, p.photoId)
                is Part.Alarm -> applyAlarm(ctx, p, now)
                else -> {}
            }
        }
    }

    /** Её будильник: если Адонис разрешил, ставится разовый звонок; отказ с причиной она увидит в своих ограничениях. */
    private fun applyAlarm(ctx: Context, p: Part.Alarm, now: Long) {
        val prefs = AppPrefs(ctx)
        if (!prefs.alarmByAurora) {
            prefs.alarmRejected = "будильник «${p.label.take(60)}» не поставлен: Адонис выключил тебе это право"
            return
        }
        val store = AlarmStore(ctx)
        val d = acceptAuroraAlarm(store.list(), p.whenText, p.label, now, newId("al"))
        val a = d.alarm
        if (a == null) {
            prefs.alarmRejected = "будильник «${p.whenText}» не поставлен: ${d.why}"
            return
        }
        prefs.alarmRejected = ""
        store.save(store.list() + a)
        AlarmScheduler.reschedule(ctx)
    }

    private fun photoExists(ctx: Context, id: String): Boolean {
        val lib = Library(ctx)
        return (lib.builtIn().second + lib.user().second).any { it.id == id }
    }
}

// ---------- её собственное время ----------

object PlanScheduler {
    private fun name(id: String) = "exe_plan_$id"

    fun schedule(ctx: Context, plan: PlanItem, replace: Boolean = true) {
        val delay = (plan.at - System.currentTimeMillis()).coerceAtLeast(0L)
        val req = OneTimeWorkRequestBuilder<PlanWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("id" to plan.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            name(plan.id),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            req
        )
    }

    fun cancel(ctx: Context, id: String) {
        WorkManager.getInstance(ctx).cancelUniqueWork(name(id))
    }

    /** При запуске приложения: убедиться, что у каждого ожидающего назначения есть задача. */
    fun resync(ctx: Context) {
        ExeStore(ctx).plans().filter { it.status == "pending" }.forEach { schedule(ctx, it, replace = false) }
    }
}

class PlanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.success()
        val outcome = try {
            InitiativeEngine.tickSelf(applicationContext, id)
        } catch (e: Exception) {
            InitiativeOutcome.FAILED
        }
        if (outcome == InitiativeOutcome.FAILED) {
            if (runAttemptCount < 3) return Result.retry()
            ExeStore(applicationContext).updatePlan(id) { it.copy(status = "dropped", outcome = "не отправлено: не было связи с сервером") }
        }
        return Result.success()
    }
}
