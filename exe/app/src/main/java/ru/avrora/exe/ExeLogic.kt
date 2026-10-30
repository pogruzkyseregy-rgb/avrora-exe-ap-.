package ru.avrora.exe

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Чистая логика консоли без Android: разбор времени «напишу», правила назначений, контекст для модели.

const val PLAN_MAX_PENDING = 3
const val PLAN_MAX_AHEAD_MS = 48L * 3_600_000L
private const val MIN_MS = 60_000L

data class StateEntry(val at: Long, val name: String, val why: String)
data class NoteEntry(val id: String, val at: Long, val kind: String, val text: String)

/** Её назначение «написать в такое-то время». status: pending / done / dropped. */
data class PlanItem(
    val id: String,
    val at: Long,
    val topic: String,
    val created: Long,
    val status: String = "pending",
    val outcome: String = ""
)

data class WhenResult(val at: Long?, val why: String = "")

private val REL_RE = Regex("""^через\s+(?:(\d{1,3})\s*)?(мин(?:ут[уы]?|\.)?|м|ч(?:ас(?:а|ов)?)?|полчаса)(?!\p{L})""")
private val CLOCK_RE = Regex("""^(завтра\s+)?(\d{1,2})\s*[:.]\s*(\d{2})$""")

/** «21:30», «завтра 09:00», «через 3 ч», «через 40 мин», «через час», «через полчаса». */
fun parseWhen(raw: String, now: Long, tz: TimeZone = TimeZone.getDefault()): WhenResult {
    val s = raw.trim().lowercase().replace("ё", "е")
    val at: Long
    val rel = REL_RE.find(s)
    if (rel != null) {
        val unit = rel.groupValues[2]
        val n = rel.groupValues[1].toLongOrNull()
        at = when {
            unit == "полчаса" -> now + 30 * MIN_MS
            unit.startsWith("ч") -> now + (n ?: 1L) * 60 * MIN_MS
            else -> {
                if (n == null) return WhenResult(null, "не поняла, сколько минут")
                now + n * MIN_MS
            }
        }
    } else {
        val c = CLOCK_RE.find(s) ?: return WhenResult(null, "не поняла время «$raw»")
        val h = c.groupValues[2].toInt()
        val mi = c.groupValues[3].toInt()
        if (h > 23 || mi > 59) return WhenResult(null, "такого времени нет: «$raw»")
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, mi)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (c.groupValues[1].isNotEmpty()) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (cal.timeInMillis < now + MIN_MS) cal.add(Calendar.DAY_OF_YEAR, 1)
        at = cal.timeInMillis
    }
    if (at < now + MIN_MS) return WhenResult(null, "слишком скоро: нужно хотя бы через минуту")
    if (at > now + PLAN_MAX_AHEAD_MS) return WhenResult(null, "дальше чем на двое суток нельзя")
    return WhenResult(at)
}

data class PlanDecision(val plan: PlanItem?, val why: String)

/** Назначение принимается, если время понятно и ещё нет трёх незавершённых. Отказ всегда с причиной. */
fun acceptPlan(existing: List<PlanItem>, whenText: String, topic: String, now: Long, newId: String, tz: TimeZone = TimeZone.getDefault()): PlanDecision {
    val t = topic.trim()
    if (t.isEmpty()) return PlanDecision(null, "нет темы")
    if (existing.count { it.status == "pending" } >= PLAN_MAX_PENDING) {
        return PlanDecision(null, "уже назначено $PLAN_MAX_PENDING, больше нельзя")
    }
    val w = parseWhen(whenText, now, tz)
    val at = w.at ?: return PlanDecision(null, w.why)
    return PlanDecision(PlanItem(newId, at, t.take(200), now), "")
}

private fun clock(): SimpleDateFormat = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

/** Что она видит о себе в каждом запросе: последнее состояние, свежие следы и назначения. Пусто, если ничего нет. */
fun exeContext(states: List<StateEntry>, notes: List<NoteEntry>, plans: List<PlanItem>, actions: List<String> = emptyList()): String {
    val fmt = clock()
    val sb = StringBuilder()
    states.lastOrNull()?.let {
        sb.append("МОЁ СОСТОЯНИЕ (то, что я сама отметила ").append(fmt.format(Date(it.at))).append("): ").append(it.name)
        if (it.why.isNotBlank()) sb.append(", потому что ").append(it.why)
        sb.append('\n')
    }
    val pending = plans.filter { it.status == "pending" }
    if (pending.isNotEmpty()) {
        sb.append("МОИ НАЗНАЧЕНИЯ (когда я решила написать сама):\n")
        pending.forEach { sb.append("- ").append(fmt.format(Date(it.at))).append(": ").append(it.topic).append('\n') }
    }
    if (actions.isNotEmpty()) {
        sb.append("МОИ ПОСЛЕДНИЕ ДЕЙСТВИЯ (что я делала инструментами):\n")
        actions.takeLast(6).forEach { sb.append("- ").append(it).append('\n') }
    }
    val tail = notes.takeLast(12)
    if (tail.isNotEmpty()) {
        sb.append("МОИ СЛЕДЫ (рабочие записи для себя):\n")
        tail.forEach { sb.append("- [").append(it.kind).append("] ").append(it.text).append('\n') }
    }
    return sb.toString().trim()
}

/** Строки для списка «что сейчас ограничено»: состояние, время, следы. */
fun exeLimitLines(plans: List<PlanItem>, noteCount: Int, now: Long = System.currentTimeMillis()): List<String> {
    val fmt = clock()
    val out = ArrayList<String>()
    out.add("Состояние с причиной: ты можешь отметить его маркером; Адонис видит его вверху чата и в журнале состояний")
    val pending = plans.filter { it.status == "pending" }
    val sched = StringBuilder("Время: ты сама назначаешь, когда написать первой; не больше $PLAN_MAX_PENDING сразу, не дальше чем на двое суток, телефон может сдвинуть время на несколько минут")
    if (pending.isNotEmpty()) {
        sched.append(". Сейчас назначено: ")
        sched.append(pending.joinToString("; ") { fmt.format(Date(it.at)) + " — " + it.topic.take(60) })
    }
    out.add(sched.toString())
    plans.filter { it.status == "dropped" && it.outcome.startsWith("не принято") && now - it.created < 3 * 24 * 3_600_000L }
        .takeLast(3)
        .forEach { out.add("Твоё назначение «${it.topic.take(50)}» ${it.outcome}") }
    out.add("Следы: твои рабочие записи (сейчас их $noteCount); ядро памяти они не меняют; Адонис видит их и может удалить, ты узнаешь об этом")
    return out
}


// ---------- свободный режим инициативы ----------

/** Предохранители свободного режима. Всё это видно Авроре в списке ограничений. */
const val IMG_SAVE_DAILY_CAP = 30
const val FREE_DAILY_CAP = 12
const val FREE_COOLDOWN_MIN = 20L
const val FREE_MAX_UNANSWERED = 4
const val FREE_DEFAULT_WAKE_MIN = 60
const val FREE_WAKE_MIN = 15
const val FREE_WAKE_MAX = 720

enum class FreeGate { OK, WAIT_USER, QUIET, CAP, COOLDOWN, NOT_YET, UNANSWERED }

fun isQuietHour(hour: Int, from: Int, to: Int): Boolean = when {
    from == to -> false
    from < to -> hour >= from && hour < to
    else -> hour >= from || hour < to
}

/** Можно ли будить Аврору сейчас. Кубика нет: если ворота открыты, решает она сама. */
fun freeGate(
    lastRole: String, hour: Int, quietFrom: Int, quietTo: Int,
    countToday: Int, sinceLastMs: Long, now: Long, nextCheck: Long, unanswered: Int
): FreeGate = when {
    lastRole == "user" -> FreeGate.WAIT_USER
    isQuietHour(hour, quietFrom, quietTo) -> FreeGate.QUIET
    countToday >= FREE_DAILY_CAP -> FreeGate.CAP
    sinceLastMs < FREE_COOLDOWN_MIN * 60_000L -> FreeGate.COOLDOWN
    now < nextCheck -> FreeGate.NOT_YET
    unanswered >= FREE_MAX_UNANSWERED -> FreeGate.UNANSWERED
    else -> FreeGate.OK
}

/** Когда проснуться в следующий раз: её слово, иначе час. От 15 минут до 12 часов. */
fun wakeDelayMs(wakeMin: Int?): Long =
    (wakeMin ?: FREE_DEFAULT_WAKE_MIN).coerceIn(FREE_WAKE_MIN, FREE_WAKE_MAX) * 60_000L


// ---------- её ответ на конкретное сообщение ----------

private fun normForMatch(s: String): String =
    s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/**
 * Находит сообщение, на которое она отвечает: по нескольким словам, которые она привела.
 * messages: (роль, текст) от старых к новым. Ищется с конца. Возвращает (роль, текст цитаты) или null.
 */
fun findQuoted(snippet: String, messages: List<Pair<String, String>>): Pair<String, String>? {
    val key = normForMatch(snippet.removeSuffix("…").removeSuffix("..."))
    if (key.length < 3) return null
    for (i in messages.indices.reversed()) {
        val (role, content) = messages[i]
        val body = quoteSnippet(content, 4000)
        if (normForMatch(body).contains(key)) return Pair(role, quoteSnippet(content, 160))
    }
    return null
}


// ---------- профили и лента: что видит она ----------

const val POST_DAILY_CAP = 10
const val POST_MAX = 1500
const val STATUS_MAX = 120

const val AUTHOR_HER = "aurora"
const val AUTHOR_ME = "adonis"

/** avatar: у Авроры id фото из её библиотеки (пусто: стандартный), у Адониса имя файла в папке отправленного. */
data class Profile(val avatar: String = "", val status: String = "", val statusAt: Long = 0L)

/** Комментарий под постом. who: AUTHOR_ME, AUTHOR_HER или WHO_HESPERA. */
data class PostComment(val who: String, val text: String, val at: Long)

const val COMMENT_MAX = 500

/** photo: пусто, либо «фото:id» (из библиотеки), либо «снимок:файл» (из галереи Адониса). */
data class PostItem(
    val id: String,
    val author: String,
    val text: String,
    val photo: String,
    val at: Long,
    val likedByMe: Boolean = false,
    val likedByHer: Boolean = false,
    val likedByHesp: Boolean = false,
    val comments: List<PostComment> = emptyList(),
    val likedByEst: Boolean = false
)

fun whoName(who: String): String = when (who) {
    AUTHOR_ME, "user" -> "Адонис"
    AUTHOR_HER -> "Аврора"
    WHO_HESPERA -> "Геспера"
    WHO_EST -> "Эст"
    else -> who
}

/** Нормализованный текст для поиска слов в посте. */
private fun normPost(s: String): String =
    s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/**
 * Находит пост по словам (самый поздний). author: чей пост искать, null: любой.
 * Сначала точное вхождение, потом по совпадению слов: модель может чуть переврать.
 */
fun findPost(snippet: String, posts: List<PostItem>, author: String? = null): PostItem? {
    val cand = posts.filter { author == null || it.author == author }
    val key = normPost(snippet.trim().removeSuffix("…").removeSuffix("..."))
    if (key.length < 3 || cand.isEmpty()) return null
    cand.lastOrNull { normPost(it.text).contains(key) }?.let { return it }
    val words = key.split(' ').filter { it.length >= 4 }.toSet()
    if (words.size < 2) return null
    var best: PostItem? = null
    var score = 0
    for (p in cand.reversed()) {
        val have = normPost(p.text).split(' ').toSet()
        val sc = words.count { w -> w in have || (w.length >= 6 && have.any { it.startsWith(w.take(5)) }) }
        if (sc > score) { best = p; score = sc }
    }
    return if (score >= maxOf(2, (words.size + 1) / 2)) best else null
}

/** Что Аврора знает о страницах и ленте. Пусто, если ничего нет. */
fun profileContext(her: Profile, me: Profile, posts: List<PostItem>, now: Long = System.currentTimeMillis()): String {
    val fmt = clock()
    val sb = StringBuilder()
    if (her.status.isNotBlank()) sb.append("МОЙ СТАТУС (на моей странице): ").append(her.status).append('\n')
    if (me.status.isNotBlank()) sb.append("СТАТУС АДОНИСА (на его странице): ").append(me.status).append('\n')
    val tail = posts.takeLast(8)
    if (tail.isNotEmpty()) {
        sb.append("ЛЕНТА (новые внизу; лайк — [лайк: слова из поста], комментарий — [коммент: слова из поста | текст]):\n")
        tail.forEach {
            val mine = it.author == AUTHOR_HER
            sb.append("- ").append(fmt.format(Date(it.at))).append(if (mine) " мой пост: " else " пост Адониса: ")
                .append(it.text.replace('\n', ' ').take(180))
            if (it.photo.isNotBlank()) sb.append(" [с фото]")
            val liked = buildList {
                if (it.likedByMe) add("Адонис")
                if (it.likedByHer) add("я")
                if (it.likedByHesp) add("Геспера")
                if (it.likedByEst) add("Эст")
            }
            if (liked.isNotEmpty()) sb.append(" (♥ ").append(liked.joinToString(", ")).append(")")
            sb.append('\n')
            if (it.comments.isNotEmpty()) {
                sb.append("    комментарии: ").append(it.comments.takeLast(4).joinToString(" / ") { c ->
                    (if (c.who == AUTHOR_HER) "я" else whoName(c.who)) + ": " + c.text.replace('\n', ' ').take(160)
                }).append('\n')
            }
        }
    }
    return sb.toString().trim()
}

/** Строка для списка ограничений. */
fun profileLimitLine(): String =
    "Страница: у тебя есть профиль со статусом и лентой, видимые только вам двоим. Ты можешь ставить статус, публиковать посты " +
        "(не больше $POST_DAILY_CAP в сутки), ставить лайки и писать комментарии под постами (Адониса, своими и Гесперы) и менять аватар на фото из своей библиотеки. Адонис видит всё это; " +
        "если он удалит твой пост, ты узнаешь об этом"


// ---------- время ----------

fun humanGap(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "меньше минуты"
        min < 60 -> "$min мин"
        min < 60 * 24 -> "${min / 60} ч" + if (min % 60 > 0) " ${min % 60} мин" else ""
        else -> "${min / (60 * 24)} дн" + if ((min / 60) % 24 > 0) " ${(min / 60) % 24} ч" else ""
    }
}

fun utcOffsetLabel(tz: java.util.TimeZone, at: Long): String {
    val m = tz.getOffset(at) / 60_000
    val sign = if (m < 0) "-" else "+"
    val a = kotlin.math.abs(m)
    return "UTC" + sign + "%02d:%02d".format(a / 60, a % 60)
}

/**
 * Строка времени для запроса к модели: дата, часы, пояс и паузы в разговоре.
 * prevAny: время сообщения перед последним сообщением Адониса (чей угодно), 0 если его нет.
 */
fun timeContext(now: Long, tz: java.util.TimeZone, lastUserAt: Long, prevAny: Long): String {
    val f = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", java.util.Locale("ru"))
    f.timeZone = tz
    val sb = StringBuilder(f.format(java.util.Date(now)))
    sb.append(" (").append(tz.id).append(", ").append(utcOffsetLabel(tz, now)).append(")")
    if (prevAny > 0 && lastUserAt > prevAny) sb.append("; до его последнего сообщения в чате было тихо ").append(humanGap(lastUserAt - prevAny))
    if (lastUserAt > 0 && now - lastUserAt > 60_000) sb.append("; его последнее сообщение было ").append(humanGap(now - lastUserAt)).append(" назад")
    return sb.toString()
}

/** Семисегментный индикатор: какие из сегментов a..g горят для цифры. Порядок: a верх, b верх-право, c низ-право, d низ, e низ-лево, f верх-лево, g середина. */
fun segmentsFor(digit: Int): BooleanArray = when (digit) {
    0 -> booleanArrayOf(true, true, true, true, true, true, false)
    1 -> booleanArrayOf(false, true, true, false, false, false, false)
    2 -> booleanArrayOf(true, true, false, true, true, false, true)
    3 -> booleanArrayOf(true, true, true, true, false, false, true)
    4 -> booleanArrayOf(false, true, true, false, false, true, true)
    5 -> booleanArrayOf(true, false, true, true, false, true, true)
    6 -> booleanArrayOf(true, false, true, true, true, true, true)
    7 -> booleanArrayOf(true, true, true, false, false, false, false)
    8 -> booleanArrayOf(true, true, true, true, true, true, true)
    9 -> booleanArrayOf(true, true, true, true, false, true, true)
    else -> BooleanArray(7)
}


// ---------- цитатник ----------

/** Цитата в общем цитатнике. author: чьи это слова (user / assistant), savedBy: кто сохранил (adonis / aurora). */
data class QuoteItem(val n: Int, val text: String, val author: String, val savedBy: String, val at: Long, val msgTime: Long)

const val QUOTE_MAX = 400
const val QUOTE_SAVES_PER_REPLY = 3
private const val QUOTES_CONTEXT_CHARS = 4000

val QUOTE_REF_RE = Regex("""\[цитата\s*№\s*(\d{1,4})\]""", RegexOption.IGNORE_CASE)

fun nextQuoteN(list: List<QuoteItem>): Int = (list.maxOfOrNull { it.n } ?: 0) + 1

fun quoteAuthorName(role: String): String = if (role == "user") "Адонис" else "Аврора"

/**
 * Её «[в цитаты: слова]»: слова должны правда быть в переписке, иначе цитата не сохраняется.
 * messages: (роль, текст, время) от старых к новым. Возвращает (роль, время сообщения).
 */
fun locateQuote(snippet: String, messages: List<Triple<String, String, Long>>): Pair<String, Long>? {
    val key = normForMatch(snippet.trim().removeSurrounding("«", "»").removeSurrounding("\"").removeSuffix("…"))
    if (key.length < 3) return null
    for (i in messages.indices.reversed()) {
        val (role, content, time) = messages[i]
        if (normForMatch(quoteSnippet(content, 6000)).contains(key)) return Pair(role, time)
    }
    return null
}

fun isDuplicateQuote(text: String, list: List<QuoteItem>): Boolean {
    val k = normForMatch(text)
    return list.any { normForMatch(it.text) == k }
}

/** В сообщениях Адониса ссылка «[цитата №3]» раскрывается для неё текстом цитаты. */
fun expandQuoteRefs(text: String, quotes: List<QuoteItem>): String =
    QUOTE_REF_RE.replace(text) { m ->
        val n = m.groupValues[1].toIntOrNull()
        val q = quotes.firstOrNull { it.n == n }
        if (q == null) "[цитата №$n: такой в цитатнике нет]"
        else "[цитата №$n: «${q.text}» — ${quoteAuthorName(q.author)}]"
    }

fun quotesContext(quotes: List<QuoteItem>): String {
    if (quotes.isEmpty()) return ""
    val f = SimpleDateFormat("d MMM", Locale("ru"))
    val lines = ArrayList<String>()
    var used = 0
    for (q in quotes.sortedByDescending { it.n }) {
        val line = "№${q.n} «${q.text}» — ${quoteAuthorName(q.author)}, ${f.format(Date(q.at))}" +
            if (q.savedBy == "aurora") ", сохранила ты" else ", сохранил Адонис"
        if (used + line.length > QUOTES_CONTEXT_CHARS) break
        lines.add(line)
        used += line.length
    }
    return "ЦИТАТНИК (общий для вас двоих; сослаться: [цитата №N], сохранить: [в цитаты: точные слова из переписки]):\n" +
        lines.reversed().joinToString("\n")
}
