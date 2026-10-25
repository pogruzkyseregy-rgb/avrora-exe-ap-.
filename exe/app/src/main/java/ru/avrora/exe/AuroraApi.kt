package ru.avrora.exe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GameAnswer(val yes: Boolean?, val say: String, val choice: Int? = null)
data class SavedImage(val desc: String, val bytes: ByteArray)
data class ChatReply(val reply: String, val trace: String, val saves: List<SavedImage> = emptyList())

data class HandStatus(val configured: Boolean, val off: Boolean, val used: Int, val cap: Int)

data class DiaryResult(val entry: String, val open: String)

data class ConsentDecision(val n: Int, val verdict: String, val text: String, val why: String)

object AuroraApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)   // с инструментами ответ идёт в несколько кругов
        .callTimeout(280, TimeUnit.SECONDS)
        .build()

    private const val HISTORY_LIMIT = 60

    private fun catalogJson(items: List<CatalogItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("desc", it.desc)) }
        return arr
    }

    private fun historyJson(history: List<ChatMessage>): JSONArray {
        val arr = JSONArray()
        history.takeLast(HISTORY_LIMIT).forEach {
            arr.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        return arr
    }

    internal suspend fun post(baseUrl: String, path: String, token: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException(errorText(resp.code, text))
                JSONObject(text)
            }
        }

    /** Короткий текст ошибки: без страниц и дампов от прокси. */
    private fun errorText(code: Int, text: String): String {
        val detail = runCatching { JSONObject(text).optString("detail", "") }.getOrDefault("").replace('\n', ' ')
        return when {
            code == 401 -> "Сервер не принял токен. Проверь его в настройках."
            code == 502 || code == 504 ->
                "Провайдер модели не ответил ($code). Повтори чуть позже." +
                    (if (detail.isNotBlank()) " (" + detail.take(90) + ")" else "")
            detail.isNotBlank() -> "Сервер ответил $code: ${detail.take(200)}"
            text.trimStart().startsWith("<") -> "Сервер ответил $code (вместо ответа пришла страница ошибки)."
            else -> "Сервер ответил $code: ${text.take(200).replace('\n', ' ')}"
        }
    }

    internal suspend fun get(baseUrl: String, path: String, token: String): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException(errorText(resp.code, text))
                JSONObject(text)
            }
        }

    /** Состояние руки на сервере: подключена ли, включён ли рубильник, сколько команд сегодня. */
    suspend fun handStatus(baseUrl: String, token: String): HandStatus = parseHand(get(baseUrl, "/hand/status", token))

    /** Рубильник: off=true выключает руку на сервере, off=false возвращает. */
    suspend fun handSwitch(baseUrl: String, token: String, off: Boolean): HandStatus =
        parseHand(post(baseUrl, if (off) "/hand/off" else "/hand/on", token, JSONObject()))

    suspend fun handLog(baseUrl: String, token: String): List<String> {
        val arr = get(baseUrl, "/hand/log", token).optJSONArray("lines") ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun parseHand(o: JSONObject) = HandStatus(
        configured = o.optBoolean("configured", false),
        off = o.optBoolean("off", false),
        used = o.optInt("used_today", 0),
        cap = o.optInt("cap", 0)
    )

    suspend fun chat(
        baseUrl: String,
        token: String,
        history: List<ChatMessage>,
        stickers: List<CatalogItem>,
        photos: List<CatalogItem>,
        memory: String,
        persona: String,
        images: List<Pair<Int, String>> = emptyList(),
        limits: List<String> = emptyList(),
        limitsChanged: List<String> = emptyList(),
        tools: List<String> = emptyList(),
        localTime: String = ""
    ): ChatReply {
        val imgs = JSONArray()
        images.forEach { imgs.put(JSONObject().put("i", it.first).put("data", it.second)) }
        val body = JSONObject()
            .put("messages", historyJson(history))
            .put("memory", memory)
            .put("persona", persona)
            .put("stickers", catalogJson(stickers))
            .put("photos", catalogJson(photos))
            .put("images", imgs)
            .put("limits", JSONArray(limits))
            .put("limits_changed", JSONArray(limitsChanged))
            .put("tools", JSONArray(tools))
            .put("exe", true)
            .put("local_time", localTime)
        val res = post(baseUrl, "/chat", token, body)
        val saves = mutableListOf<SavedImage>()
        res.optJSONArray("saves")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val bytes = try { android.util.Base64.decode(o.getString("b64"), android.util.Base64.DEFAULT) } catch (e: Exception) { continue }
                saves.add(SavedImage(o.optString("desc", "из сети"), bytes))
            }
        }
        return ChatReply(res.getString("reply"), res.optJSONArray("trace")?.toString() ?: "", saves)
    }

    /**
     * Порция сырой переписки превращается в эпизод (неизменяемый подробный пересказ)
     * и точечные правки ядра. Ядро целиком не переписывается.
     */
    suspend fun compressMemory(
        baseUrl: String,
        token: String,
        core: List<CoreEntry>,
        messages: List<Pair<String, String>>
    ): CompressResult {
        val coreArr = JSONArray()
        core.forEach { coreArr.put(JSONObject().put("id", it.id).put("kind", it.kind).put("text", it.text)) }
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val res = post(baseUrl, "/memory", token, JSONObject().put("core", coreArr).put("messages", arr))
        val opsArr = res.optJSONArray("ops")
        val ops = if (opsArr == null) emptyList<MemOp>() else (0 until opsArr.length()).map { i ->
            val o = opsArr.getJSONObject(i)
            MemOp(o.optString("op"), o.optString("id"), o.optString("kind"), o.optString("text"))
        }
        return CompressResult(res.optString("episode", "").trim(), ops)
    }

    /** Её решение в настолке. yes == null: ответ без решения, тогда решает правило. */
    suspend fun game(
        baseUrl: String,
        token: String,
        persona: String,
        memory: String,
        state: String,
        question: String,
        kind: String,
        history: List<ChatMessage>
    ): GameAnswer {
        val body = JSONObject()
            .put("persona", persona)
            .put("memory", memory)
            .put("state", state)
            .put("question", question)
            .put("kind", kind)
            .put("messages", historyJson(history.takeLast(6)))
        val res = post(baseUrl, "/game", token, body)
        val yes = if (res.isNull("yes")) null else res.optBoolean("yes")
        val choice = if (res.has("choice") && !res.isNull("choice")) res.optInt("choice") else null
        return GameAnswer(yes, res.optString("say", ""), choice)
    }

    /** Аврора решает, писать ли первой. message == null означает, что она решила промолчать; wakeMin: когда разбудить в следующий раз. */
    suspend fun initiative(
        baseUrl: String,
        token: String,
        history: List<ChatMessage>,
        memory: String,
        persona: String,
        reason: String,
        gapMinutes: Long,
        localTime: String,
        unanswered: Int,
        stickers: List<CatalogItem>,
        photos: List<CatalogItem>,
        limits: List<String> = emptyList(),
        topic: String = ""
    ): InitResult {
        val body = JSONObject()
            .put("messages", historyJson(history))
            .put("limits", JSONArray(limits))
            .put("memory", memory)
            .put("persona", persona)
            .put("reason", reason)
            .put("gap_minutes", gapMinutes)
            .put("local_time", localTime)
            .put("unanswered", unanswered)
            .put("topic", topic)
            .put("exe", true)
            .put("stickers", catalogJson(stickers))
            .put("photos", catalogJson(photos))
        val res = post(baseUrl, "/initiative", token, body)
        val wake = if (res.has("wake_min")) res.optInt("wake_min", 0).takeIf { it > 0 } else null
        if (res.optBoolean("skip", false)) return InitResult(null, wake, res.optString("markers", "").ifBlank { null })
        val msg = res.optString("message", "").trim()
        return InitResult(if (msg.isEmpty()) null else msg, wake)
    }

    /** Её решение по предложенным правкам ядра. Если решения нет, бросает исключение: пункты остаются в очереди. */
    suspend fun consent(
        baseUrl: String,
        token: String,
        core: List<CoreEntry>,
        proposals: List<Proposal>,
        episode: String
    ): List<ConsentDecision> {
        val coreArr = JSONArray()
        core.forEach { coreArr.put(JSONObject().put("id", it.id).put("kind", it.kind).put("text", it.text)) }
        val pArr = JSONArray()
        proposals.forEachIndexed { i, p ->
            pArr.put(
                JSONObject().put("n", i).put("op", p.op).put("id", p.targetId)
                    .put("kind", p.kind).put("text", p.text).put("source", p.source)
            )
        }
        val res = post(baseUrl, "/consent", token, JSONObject().put("core", coreArr).put("proposals", pArr).put("episode", episode))
        val arr = res.optJSONArray("decisions") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ConsentDecision(o.optInt("n", -1), o.optString("verdict"), o.optString("text"), o.optString("why"))
        }
    }

    /** Запись в дневник. null означает, что она решила: писать нечего. */
    suspend fun diary(
        baseUrl: String,
        token: String,
        messages: List<Pair<String, String>>,
        memory: String,
        localTime: String,
        limits: List<String>
    ): DiaryResult? {
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val body = JSONObject()
            .put("messages", arr)
            .put("memory", memory)
            .put("local_time", localTime)
            .put("limits", JSONArray(limits))
        val res = post(baseUrl, "/diary", token, body)
        if (res.optBoolean("skip", false)) return null
        val entry = res.optString("entry", "").trim()
        if (entry.isEmpty()) return null
        return DiaryResult(entry, res.optString("open", "").trim())
    }
}

/** markers: пост, статус или лайк, которые она поставила, промолчав в чате. */
data class InitResult(val message: String?, val wakeMin: Int? = null, val markers: String? = null)
