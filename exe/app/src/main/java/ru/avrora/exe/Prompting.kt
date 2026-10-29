package ru.avrora.exe

/** Мои вложения для модели превращаются в понятный текст. Собственные теги Авроры остаются как есть. */
fun describeForModel(
    text: String,
    stickers: List<CatalogItem>,
    photos: List<CatalogItem>,
    attachedSnaps: Set<String> = emptySet()
): String {
    val (quote, body) = splitQuote(text)
    val described = describeAttachments(voiceForModel(body), stickers, photos, attachedSnaps)
    return if (quote == null) described else (describeQuote(quote) + "\n" + described).trim()
}

private fun describeAttachments(
    text: String,
    stickers: List<CatalogItem>,
    photos: List<CatalogItem>,
    attachedSnaps: Set<String>
): String =
    TAG_RE.replace(text) { mr ->
        val id = mr.groupValues[2]
        when (mr.groupValues[1]) {
            "стикер", "sticker" -> {
                val d = stickers.firstOrNull { it.id == id }?.desc
                if (d != null) "[Адонис отправил стикер: $d]" else "[Адонис отправил стикер]"
            }
            "фото", "photo" -> {
                val d = photos.firstOrNull { it.id == id }?.desc
                if (d != null) "[Адонис отправил фото из альбома: $d]" else "[Адонис отправил фото из альбома]"
            }
            else -> if (id in attachedSnaps) {
                "[Адонис прислал фото, оно приложено к этому сообщению, ты его видишь]"
            } else {
                "[Адонис прислал фото (сейчас оно тебе не показано)]"
            }
        }
    }.trim()

/** Служебные маркеры консоли (состояние, назначение, след): в пересказ для памяти не идут. */
private val EXE_MARKERS_RE = Regex("""\[(?:состояние|напишу|след|ответ|оставить|в\s+цитаты|будильник)\s*:[^\]]*\]""", RegexOption.IGNORE_CASE)

/** Для сжатия памяти: вложения любой стороны как нейтральные пометки. */
fun toPlainForMemory(text: String, stickers: List<CatalogItem>, photos: List<CatalogItem>): String {
    val (quote, body) = splitQuote(text)
    val plain = plainAttachments(voiceForModel(body), stickers, photos)
    return if (quote == null) plain else (describeQuote(quote) + "\n" + plain).trim()
}

private fun plainAttachments(text: String, stickers: List<CatalogItem>, photos: List<CatalogItem>): String =
    TAG_RE.replace(text) { mr ->
        val id = mr.groupValues[2]
        when (mr.groupValues[1]) {
            "стикер", "sticker" -> "[стикер: ${stickers.firstOrNull { it.id == id }?.desc ?: id}]"
            "фото", "photo" -> "[фото из альбома: ${photos.firstOrNull { it.id == id }?.desc ?: id}]"
            else -> "[фото от Адониса]"
        }
    }.let { EXE_MARKERS_RE.replace(it, "") }
        .let { PAUSE_RE.replace(it, " … ") }
        .let { SILENCE_RE.replace(it, "[Аврора промолчала]") }
        .trim()
