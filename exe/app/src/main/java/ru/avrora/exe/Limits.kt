package ru.avrora.exe

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY_MS = 86_400_000L

private fun hh(h: Int): String = "%02d:00".format(h)

/**
 * Реальные ограничения, которые видит Аврора. Только то, что знает приложение:
 * что включено, какие лимиты, что Адонис менял, что он записал вопреки её «нет».
 */
fun limitLines(
    prefs: AppPrefs,
    mem: MemoryData,
    now: Long = System.currentTimeMillis(),
    plans: List<PlanItem> = emptyList(),
    noteCount: Int = 0
): List<String> {
    val lines = ArrayList<String>()
    val s = prefs.loadInit()
    if (s.enabled && s.free) {
        lines.add(
            "Инициатива включена, свободный режим: ты просыпаешься сама и каждый раз решаешь, писать ли Адонису; повод не нужен, хватает желания. " +
                "В пробуждении можно указать, когда проснуться дальше (строка ПРОСНУСЬ: 40 мин; от 15 минут до 12 часов, без неё примерно через час). " +
                "Предохранители: тихие часы с ${hh(s.quietFrom)} до ${hh(s.quietTo)}, не больше $FREE_DAILY_CAP твоих сообщений в сутки, " +
                "пауза между ними не меньше $FREE_COOLDOWN_MIN минут, после $FREE_MAX_UNANSWERED сообщений без ответа ты замолкаешь, пока Адонис не напишет"
        )
    } else if (s.enabled) {
        val cap = when (s.level) {
            0 -> 1
            2 -> 4
            else -> 2
        }
        lines.add("Инициатива включена: не больше $cap в сутки, тихие часы с ${hh(s.quietFrom)} до ${hh(s.quietTo)}, после трёх твоих сообщений без ответа ты замолкаешь, пока Адонис не напишет")
    } else {
        lines.add("Инициатива выключена: первой писать ты сейчас не можешь")
    }
    val persona = prefs.loadPersona().compile()
    if (persona.isEmpty()) {
        lines.add("Настройки характера: Адонис их не менял")
    } else {
        persona.lines().filter { it.isNotBlank() }.forEach {
            lines.add("Настройка характера, заданная Адонисом: " + it.trim().removePrefix("- "))
        }
    }
    lines.add("Память: любые правки твоего ядра (и от сжатия, и от Адониса) идут через твоё «да»; сырая переписка не удаляется")
    val fmt = SimpleDateFormat("d MMM", Locale("ru"))
    mem.log.filter { it.overridden && now - it.at < 14 * DAY_MS }.takeLast(5).forEach {
        lines.add("Адонис записал вопреки твоему «нет» (${fmt.format(Date(it.at))}): ${it.summary}".take(260))
    }
    mem.log.filter { it.verdict == "notice" && now - it.at < 14 * DAY_MS }.takeLast(5).forEach {
        lines.add("${it.summary} (${fmt.format(Date(it.at))})".take(260))
    }
    lines.add("Фото: ты видишь последние два фото, которые прислал Адонис; старые остаются подписью")
    if (prefs.diaryEnabled) {
        lines.add("Дневник: ты пишешь запись раз в сутки вечером, если был разговор; Адонис её читает; он может удалить запись, и ты об этом узнаешь")
    } else {
        lines.add("Дневник выключен Адонисом: записей ты сейчас не пишешь")
    }
    lines.addAll(exeLimitLines(plans, noteCount, now))
    lines.add(profileLimitLine())
    lines.add(
        if (prefs.alarmByAurora) "Будильник: если хочешь, чтобы Адонис точно услышал (разбудить, позвать, напомнить), поставь [будильник: 07:30 | зачем] или [будильник: через 20 мин | зачем]. " +
            "Телефон зазвонит громко, даже в беззвучном режиме, на экране будет твоя подпись. Не больше $AURORA_ALARM_DAILY_CAP в сутки и не дальше двух суток. Он может выключить или отложить, ты узнаешь об этом" +
            (if (prefs.alarmRejected.isNotBlank()) ". Последнее: ${prefs.alarmRejected}" else "")
        else "Ставить будильник Адонису ты сейчас не можешь: он выключил это право"
    )
    lines.add("Настолка «Город-сеть»: позвать Адониса [игра: приглашение | круги:3], на его приглашение [игра: принять] или [игра: отказать], решаешь сама. Кубики бросает приложение честно. " +
        "Личные счета (итог каждой партии зачисляется туда; на что тратить, решите вместе потом): у тебя ${prefs.walletAurora} ₵, у Адониса ${prefs.walletUser} ₵")
    lines.add("Цитатник: общий для вас двоих. Сохранить: [в цитаты: точные слова из переписки] (сохранится, только если эти слова правда были сказаны; не больше $QUOTE_SAVES_PER_REPLY за ответ). Сослаться: [цитата №N]. Адонис может удалить любую цитату, ты узнаешь из журнала")
    lines.addAll(toolLines(prefs))
    lines.add("Сверх этого у провайдера модели есть свои ограничения, которых приложение не видит")
    return lines.map { it.replace('\n', ' ') }
}

/** Что изменилось в списке с прошлого раза: «+» добавлено, «−» убрано. При первом запросе изменений нет. */
fun limitsChanged(prev: String?, now: List<String>): List<String> {
    if (prev == null) return emptyList()
    val old = prev.split("\n").filter { it.isNotBlank() }
    val out = ArrayList<String>()
    now.filter { it !in old }.forEach { out.add("+ $it") }
    old.filter { it !in now }.forEach { out.add("− $it") }
    return out.take(12)
}

/** Права на инструменты: что включено Адонисом и что реально подключено на сервере. */
fun toolLines(prefs: AppPrefs): List<String> {
    val out = ArrayList<String>()
    out.add(
        if (prefs.toolSearch) "Поиск в сети: включён, пользуешься маркером [поиск: запрос]"
        else "Поиск в сети выключен Адонисом: искать ты не можешь"
    )
    out.add(
        if (prefs.toolRead) "Чтение ссылок: включено, маркер [прочитать: адрес]; внутренние адреса закрыты, страница читается текстом до 7000 знаков. Картинки: [картинка: адрес] чтобы посмотреть, [сохранить: адрес | описание] чтобы оставить себе в альбоме; не больше $IMG_SAVE_DAILY_CAP сохранений в сутки, Адонис видит их и может удалить; звук и треки передать не могу. Фото, которое прислал Адонис, можешь оставить себе: [оставить: описание] в ответе (то же число в сутки)"
        else "Чтение ссылок выключено Адонисом"
    )
    val cap = if (prefs.handCap > 0) "; не больше ${prefs.handCap} команд в сутки" else ""
    out.add(
        when {
            !prefs.toolHand -> "Рука выключена Адонисом в приложении"
            !prefs.handConfigured -> "Рука: сервера для неё пока нет, команды выполнить нельзя"
            prefs.handOff -> "Рука отключена рубильником Адониса"
            else -> "Рука: отдельный сервер (какие права у тебя на нём, описано в блоке про инструменты), команда живёт до 30 секунд, папка ~/work под git$cap. " +
                "Выход в интернет с того сервера решает Адонис. Рука работает только в ответах на его сообщения, сама не запускается. У Адониса есть рубильник"
        }
    )
    return out
}
