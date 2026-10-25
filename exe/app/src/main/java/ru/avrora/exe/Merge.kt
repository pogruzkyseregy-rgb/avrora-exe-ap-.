package ru.avrora.exe

// Совмещение копий: новое добавляется к имеющемуся, повторы пропускаются, ничего не затирается.

/** Запись дневника. upTo: время последнего сообщения переписки, вошедшего в запись. */
data class DiaryEntry(val id: String, val at: Long, val text: String, val open: String, val upTo: Long)

private val NORM_SKIP = Regex("""[\s\p{P}]+""")

fun normText(s: String): String = NORM_SKIP.replace(s.lowercase(), " ").trim()

data class MemoryMerge(val data: MemoryData, val addedCore: Int, val addedEpisodes: Int)

fun mergeMemory(cur: MemoryData, add: MemoryData): MemoryMerge {
    val core = cur.core.toMutableList()
    var addedCore = 0
    for (e in add.core) {
        val n = normText(e.text)
        if (n.isEmpty()) continue
        if (core.any { it.id == e.id && normText(it.text) == n } || core.any { normText(it.text) == n }) continue
        val id = if (core.any { it.id == e.id }) newId("c") else e.id
        core.add(e.copy(id = id))
        addedCore++
    }
    val eps = cur.episodes.toMutableList()
    var addedEps = 0
    for (e in add.episodes) {
        val n = normText(e.text)
        if (n.isEmpty()) continue
        if (eps.any { normText(it.text) == n }) continue
        val id = if (eps.any { it.id == e.id }) newId("e") else e.id
        eps.add(e.copy(id = id))
        addedEps++
    }
    eps.sortBy { it.to }
    val pendIds = cur.pending.map { it.id }.toSet()
    val pending = cur.pending + add.pending.filter { it.id !in pendIds }
    val logKeys = cur.log.map { it.at to it.summary }.toSet()
    val log = (cur.log + add.log.filter { (it.at to it.summary) !in logKeys }).sortedBy { it.at }.takeLast(100)
    return MemoryMerge(
        MemoryData(core, eps, maxOf(cur.upTo, add.upTo), pending, log),
        addedCore, addedEps
    )
}

/** Сообщения из двух копий в одну ленту по времени. Совпавшие (роль, текст, время) не повторяются. */
fun mergeMessages(cur: List<ChatMessage>, add: List<ChatMessage>): List<ChatMessage> {
    val seen = cur.map { Triple(it.role, it.content, it.time) }.toHashSet()
    val extra = add.filter { seen.add(Triple(it.role, it.content, it.time)) }
    return (cur + extra).sortedBy { it.time }
}

fun mergeDiary(cur: List<DiaryEntry>, add: List<DiaryEntry>): List<DiaryEntry> {
    val ids = cur.map { it.id }.toHashSet()
    val texts = cur.map { normText(it.text) }.toHashSet()
    val extra = add.filter { ids.add(it.id) && texts.add(normText(it.text)) }
    return (cur + extra).sortedBy { it.at }
}
