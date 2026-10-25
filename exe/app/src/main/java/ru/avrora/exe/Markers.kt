package ru.avrora.exe

/** Теги во вложениях Адониса: [стикер:id], [фото:id], [снимок:файл]. */
val TAG_RE = Regex("""\[(стикер|фото|снимок|sticker|photo)\s*:\s*([A-Za-z0-9_.\-]+)\s*\]""")

/** Её маркеры молчания и пауз. */
val PAUSE_RE = Regex("""\[пауза(?:\s*:\s*(\d{1,2}))?\s*\]""", RegexOption.IGNORE_CASE)
val SILENCE_RE = Regex("""\[молчу\s*\]""", RegexOption.IGNORE_CASE)

/**
 * Все маркеры в тексте. Группы: 1-2 вложение (вид, id), 3 секунды паузы,
 * 4-5 состояние (слово, причина), 6-7 «напишу» (когда, тема), 8-9 след (вид, текст), 10 её цитата-ответ,
 * 11 пост, 12 статус, 13 лайк, 14 аватар, 15 оставить, 16 в цитаты, 17 цитата №.
 */
private val ALL_RE = Regex(
    """\[(?:(стикер|фото|снимок|sticker|photo)\s*:\s*([A-Za-z0-9_.\-]+)""" +
        """|пауза(?:\s*:\s*(\d{1,2}))?""" +
        """|состояние\s*:\s*([^\]|]{1,60})(?:\|\s*причина\s*:\s*([^\]]{1,300}))?""" +
        """|напишу\s*:\s*([^\]|]{1,40})\|\s*([^\]]{1,200})""" +
        """|след\s*:\s*([^\]|]{1,20})\|\s*([^\]]{1,300})""" +
        """|ответ\s*:\s*([^\]]{1,200})""" +
        """|пост\s*:\s*([^\]]{1,1500})""" +
        """|статус\s*:\s*([^\]]{1,120})""" +
        """|лайк\s*:\s*([^\]]{1,200})""" +
        """|аватар\s*:\s*([A-Za-z0-9_.\-]{1,60})""" +
        """|оставить\s*:\s*([^\]]{1,200})""" +
        """|в\s+цитаты\s*:\s*([^\]]{2,400})""" +
        """|цитата\s*№\s*(\d{1,4})""" +
        """|игра\s*:\s*(приглашение|принять|отказать|итог)(?:\s*\|\s*([^\]]{0,300}))?""" +
        """|будильник\s*:\s*([^\]|]{1,40})(?:\|\s*([^\]]{0,200}))?)\s*\]""",
    RegexOption.IGNORE_CASE
)

/** В конце поста может стоять «| фото:id» с картинкой из её библиотеки. */
val POST_PHOTO_RE = Regex("""\s*\|\s*(?:фото|photo)\s*:\s*([^\s\]|]{1,60})\s*$""", RegexOption.IGNORE_CASE)

sealed class Part {
    data class Text(val s: String) : Part()
    data class Sticker(val id: String) : Part()
    data class Photo(val id: String) : Part()
    data class Snap(val file: String) : Part()
    data class Pause(val seconds: Int) : Part()
    data class State(val name: String, val why: String) : Part()
    data class Plan(val whenText: String, val topic: String) : Part()
    data class Note(val kind: String, val text: String) : Part()
    data class Reply(val snippet: String) : Part()
    data class Post(val text: String, val photoId: String) : Part()
    data class Status(val text: String) : Part()
    data class Like(val snippet: String) : Part()
    data class Avatar(val photoId: String) : Part()
    data class Keep(val desc: String) : Part()
    data class SaveQuote(val text: String) : Part()
    data class QuoteRef(val n: Int) : Part()
    data class Game(val kind: String, val arg: String) : Part()
    data class Alarm(val whenText: String, val label: String) : Part()
    object Silence : Part()
}

fun parseParts(content: String): List<Part> {
    // Если она ответила [молчу], остального текста не показываем: это решение промолчать
    if (SILENCE_RE.containsMatchIn(content)) return listOf(Part.Silence)
    val out = ArrayList<Part>()
    var last = 0
    for (m in ALL_RE.findAll(content)) {
        val before = content.substring(last, m.range.first).trim()
        if (before.isNotEmpty()) out.add(Part.Text(before))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> {
                val kind = g[1].lowercase()
                when (kind) {
                    "стикер", "sticker" -> out.add(Part.Sticker(g[2]))
                    "фото", "photo" -> out.add(Part.Photo(g[2]))
                    else -> out.add(Part.Snap(g[2]))
                }
            }
            g[4].isNotEmpty() -> out.add(Part.State(g[4].trim(), g[5].trim()))
            g[6].isNotEmpty() -> out.add(Part.Plan(g[6].trim(), g[7].trim()))
            g[8].isNotEmpty() -> out.add(Part.Note(g[8].trim().lowercase(), g[9].trim()))
            g[10].isNotEmpty() -> out.add(Part.Reply(g[10].trim()))
            g[11].isNotEmpty() -> {
                val raw = g[11].trim()
                val ph = POST_PHOTO_RE.find(raw)
                out.add(Part.Post(if (ph != null) raw.substring(0, ph.range.first).trim() else raw, ph?.groupValues?.get(1) ?: ""))
            }
            g[12].isNotEmpty() -> out.add(Part.Status(g[12].trim()))
            g[13].isNotEmpty() -> out.add(Part.Like(g[13].trim()))
            g[14].isNotEmpty() -> out.add(Part.Avatar(g[14].trim()))
            g[15].isNotEmpty() -> out.add(Part.Keep(g[15].trim()))
            g[16].isNotEmpty() -> out.add(Part.SaveQuote(g[16].trim()))
            g[17].isNotEmpty() -> out.add(Part.QuoteRef(g[17].toInt()))
            g[18].isNotEmpty() -> out.add(Part.Game(g[18].lowercase(), g[19].trim()))
            g[20].isNotEmpty() -> out.add(Part.Alarm(g[20].trim(), g[21].trim()))
            else -> out.add(Part.Pause((g[3].toIntOrNull() ?: 2).coerceIn(1, 8)))
        }
        last = m.range.last + 1
    }
    val tail = content.substring(last).trim()
    if (tail.isNotEmpty()) out.add(Part.Text(tail))
    return out
}


// ---------- ответ и пересылка ----------

/** Цитата в сообщении Адониса: «в ответ на» или «пересланное». Хранится маркером в самом тексте. */
data class Quote(val kind: String, val who: String, val text: String) {
    val forward: Boolean get() = kind == "forward"
}

private val QUOTE_RE = Regex(
    """\[(в ответ на|пересланное)\s*:\s*(аврора|адонис)\s*\|\s*([^\]\n]{0,600})\]""",
    RegexOption.IGNORE_CASE
)

/** Короткий чистый текст сообщения для цитаты: без маркеров, вложения словами. */
fun quoteSnippet(content: String, limit: Int = 300): String {
    val (_, body) = splitQuote(content)
    val sb = StringBuilder()
    for (p in parseParts(body)) {
        val piece = when (p) {
            is Part.Text -> p.s
            is Part.Photo, is Part.Snap -> "[фото]"
            is Part.Sticker -> "[стикер]"
            else -> ""
        }
        if (piece.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(piece)
        }
    }
    var s = sb.toString().replace(Regex("\\s+"), " ").replace('[', '(').replace(']', ')').trim()
    if (s.isEmpty()) s = "вложение"
    return if (s.length > limit) s.take(limit).trimEnd() + "…" else s
}

/** Маркер цитаты, который ставится в начало сообщения. who: «аврора» или «адонис». */
fun buildQuote(forward: Boolean, who: String, content: String): String =
    "[" + (if (forward) "пересланное" else "в ответ на") + ": " + who + " | " + quoteSnippet(content) + "]"

/** Отделяет цитату от текста. Возвращает (цитата или null, остальной текст). */
fun splitQuote(content: String): Pair<Quote?, String> {
    val m = QUOTE_RE.find(content) ?: return Pair(null, content)
    val q = Quote(
        if (m.groupValues[1].lowercase() == "пересланное") "forward" else "reply",
        m.groupValues[2].lowercase(),
        m.groupValues[3].trim()
    )
    val rest = (content.substring(0, m.range.first) + content.substring(m.range.last + 1)).trim()
    return Pair(q, rest)
}

/** Как цитату читает модель. */
fun describeQuote(q: Quote): String {
    val mineQ = q.who == "адонис"
    return when {
        q.forward && mineQ -> "[Адонис пересылает тебе своё прежнее сообщение: «${q.text}»]"
        q.forward -> "[Адонис пересылает тебе твоё сообщение: «${q.text}»]"
        mineQ -> "[Адонис отвечает на своё прежнее сообщение: «${q.text}»]"
        else -> "[Адонис отвечает на твоё сообщение: «${q.text}»]"
    }
}
