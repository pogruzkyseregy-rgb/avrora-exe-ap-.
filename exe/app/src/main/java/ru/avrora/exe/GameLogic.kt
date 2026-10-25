package ru.avrora.exe

import kotlin.random.Random

// Настолка «Город-сеть»: чистые правила без Android. Состояние неизменяемое, каждое действие возвращает новое.

enum class CellType { START, NODE, SIGNAL, TAX, PORTAL, PATROL, CACHE, QUARANTINE, RAID, GLITCH, FREE }

data class Cell(
    val type: CellType,
    val name: String,
    val short: String,
    val price: Int = 0,
    val rent: Int = 0,
    val district: Int = -1,
    val amount: Int = 0,
    val target: Int = -1
)

const val BOARD_SIZE = 40
const val START_MONEY = 1500
const val PASS_BONUS = 200
const val QUAR_INDEX = 10
const val QUAR_FEE = 50
const val QUAR_TURNS = 2
const val GAME_DEFAULT_LAPS = 3
const val GAME_LOG_MAX = 60
const val DISTRICTS = 8

private fun node(name: String, short: String, price: Int, d: Int) = Cell(CellType.NODE, name, short, price, price / 5, d)

/** Поле как в классической настолке: 40 клеток, четыре больших угла, по 9 клеток на стороне. */
val BOARD: List<Cell> = listOf(
    Cell(CellType.START, "Старт", "СТАРТ"),                                   // 0  угол
    node("Неоновый рынок", "Рынок", 60, 0),                                   // 1
    Cell(CellType.SIGNAL, "Сигнал", "?"),                                     // 2
    node("Переулок кабелей", "Кабели", 60, 0),                                // 3
    Cell(CellType.TAX, "Налог сети", "−₵", amount = 100),                     // 4
    Cell(CellType.PORTAL, "Портал «Юг»", "◎", target = 15),                   // 5
    node("Дата-бар", "Бар", 100, 1),                                          // 6
    Cell(CellType.PATROL, "Патруль", "!"),                                    // 7
    node("Станция «Восток»", "Восток", 100, 1),                               // 8
    node("Ночной ларёк", "Ларёк", 120, 1),                                    // 9
    Cell(CellType.QUARANTINE, "Карантин", "КАРАНТИН"),                        // 10 угол
    node("Серверная", "Сервер", 140, 2),                                      // 11
    Cell(CellType.CACHE, "Тайник", "₵+", amount = 100),                       // 12
    node("Голо-театр", "Голо", 140, 2),                                       // 13
    node("Башня связи", "Башня", 160, 2),                                     // 14
    Cell(CellType.PORTAL, "Портал «Запад»", "◎", target = 25),                // 15
    node("Ночной порт", "Порт", 180, 3),                                      // 16
    Cell(CellType.SIGNAL, "Сигнал", "?"),                                     // 17
    node("Склад дронов", "Дроны", 180, 3),                                    // 18
    node("Чёрный рынок", "Чёрный", 200, 3),                                   // 19
    Cell(CellType.FREE, "Нейтральная зона", "ЗОНА"),                          // 20 угол
    node("Небесный мост", "Мост", 220, 4),                                    // 21
    Cell(CellType.GLITCH, "Глитч", "≈"),                                      // 22
    node("Неон-арена", "Арена", 220, 4),                                      // 23
    node("Корпоративный шпиль", "Шпиль", 240, 4),                             // 24
    Cell(CellType.PORTAL, "Портал «Север»", "◎", target = 35),                // 25
    node("Архив", "Архив", 260, 5),                                           // 26
    node("Лаборатория", "Лаба", 260, 5),                                      // 27
    Cell(CellType.CACHE, "Тайник", "₵+", amount = 100),                       // 28
    node("Купол данных", "Купол", 280, 5),                                    // 29
    Cell(CellType.RAID, "Облава", "ОБЛАВА"),                                  // 30 угол
    node("Шахта охлаждения", "Шахта", 300, 6),                                // 31
    node("Зеркальный квартал", "Зеркала", 300, 6),                            // 32
    Cell(CellType.SIGNAL, "Сигнал", "?"),                                     // 33
    node("Ретранслятор", "Ретр.", 320, 6),                                    // 34
    Cell(CellType.PORTAL, "Портал «Восток»", "◎", target = 5),                // 35
    Cell(CellType.PATROL, "Патруль", "!"),                                    // 36
    node("Ядро сети", "Ядро", 350, 7),                                        // 37
    Cell(CellType.TAX, "Сбой оборудования", "−₵", amount = 75),               // 38
    node("Сердце AV", "AV", 400, 7)                                           // 39
)

val SIGNAL_CARDS = listOf(
    "Нашли брошенный кошелёк: +100 ₵",
    "Дрон-курьер подбросил вперёд на 3 клетки",
    "Слежка: пришлось отступить на 2 клетки",
    "Взлом банкомата сорвался: −60 ₵",
    "Подарок от сети: +150 ₵",
    "Патруль на хвосте: пропусти следующий ход",
    "Щит: следующий патруль или облава не подействуют",
    "Перевод от соперника: он платит тебе 80 ₵",
    "Портал открылся: прямо на Старт (+$PASS_BONUS ₵)",
    "Отключение района: −50 ₵"
)

/** Три фишки на выбор. Первым выбирает тот, кто ходит первым. */
val GAME_TOKENS = listOf("Взгляд", "Сердце AV", "Лиса-волна")
val GAME_TOKEN_HINTS = listOf(
    "светящиеся синие глаза сквозь глитч",
    "неоновое сердце с буквами AV",
    "звуковая волна с силуэтом лисы"
)

data class GPlayer(
    val who: String,             // user / aurora
    val pos: Int = 0,
    val money: Int = START_MONEY,
    val laps: Int = 0,
    val skip: Int = 0,
    val quarantine: Int = 0,
    val shield: Boolean = false,
    val token: Int = -1
)

/** phase: order (бросок за очерёдность), pick (выбор фишки), roll (ждёт броска), buy (решить, покупать ли узел), jail (платить ли за выход), over. */
data class GameState(
    val id: String,
    val laps: Int,
    val players: List<GPlayer>,
    val owners: Map<Int, Int> = emptyMap(),
    val turn: Int = 0,
    val first: Int = 0,
    val phase: String = "order",
    val dice: List<Int> = emptyList(),
    val log: List<String> = emptyList(),
    val finalRound: Boolean = false,
    val winner: Int = -1,
    val startedAt: Long = 0L,
    val turnNo: Int = 0,
    val seq: Int = 0        // сколько строк журнала было всего: так приложение видит, что случилось за ход
) {
    val over get() = phase == "over"
    val current get() = players[turn]
}

fun playerName(who: String) = if (who == "user") "Адонис" else "Аврора"

fun newGame(id: String, laps: Int, now: Long): GameState =
    GameState(id, laps.coerceIn(2, 8), listOf(GPlayer("user"), GPlayer("aurora")), startedAt = now,
        log = listOf("Партия «Город-сеть» на ${laps.coerceIn(2, 8)} круга(ов). Бросок кубика решает, кто ходит первым."))

private fun GameState.say(line: String) = copy(log = (log + line).takeLast(GAME_LOG_MAX), seq = seq + 1)
private fun GameState.withPlayer(i: Int, f: (GPlayer) -> GPlayer) = copy(players = players.mapIndexed { k, p -> if (k == i) f(p) else p })

fun wealth(s: GameState, i: Int): Int = s.players[i].money + s.owners.filterValues { it == i }.keys.sumOf { BOARD[it].price }

fun ownsDistrict(s: GameState, i: Int, district: Int): Boolean =
    BOARD.indices.filter { BOARD[it].type == CellType.NODE && BOARD[it].district == district }.all { s.owners[it] == i }

fun rentFor(s: GameState, cell: Int): Int {
    val owner = s.owners[cell] ?: return 0
    val c = BOARD[cell]
    return if (ownsDistrict(s, owner, c.district)) c.rent * 2 else c.rent
}

/** Бросок за очерёдность: оба кидают по кубику, у кого больше, тот первый; ничья перебрасывается. */
fun rollOrder(s: GameState, rnd: Random): GameState {
    if (s.phase != "order") return s
    var st = s
    while (true) {
        val a = rnd.nextInt(1, 7)
        val b = rnd.nextInt(1, 7)
        if (a == b) {
            st = st.say("Очерёдность: у обоих $a, перебрасываем")
            continue
        }
        val first = if (a > b) 0 else 1
        return st.copy(first = first, turn = first, dice = listOf(a, b), phase = "pick")
            .say("Очерёдность: Адонис $a, Аврора $b. Первым ходит ${playerName(st.players[first].who)}, фишку выбирает тоже первым")
    }
}

fun freeTokens(s: GameState): List<Int> = GAME_TOKENS.indices.filter { k -> s.players.none { it.token == k } }

/** Выбор фишки: сначала тот, кто ходит первым, потом второй из оставшихся. */
fun pickToken(s: GameState, k: Int): GameState {
    if (s.phase != "pick" || k !in freeTokens(s)) return s
    val st = s.withPlayer(s.turn) { it.copy(token = k) }.say("${playerName(s.current.who)} берёт фишку «${GAME_TOKENS[k]}»")
    val other = 1 - s.turn
    return if (st.players[other].token < 0) st.copy(turn = other)
    else prepareTurn(st.copy(turn = st.first, phase = "roll").say("Фишки на старте. Первый ход: ${playerName(st.players[st.first].who)}"))
}

/** Начало хода: пропуски и карантин срабатывают до броска. */
fun prepareTurn(s: GameState): GameState {
    var st = s
    repeat(6) {
        if (st.over) return st
        val p = st.current
        val name = playerName(p.who)
        when {
            p.skip > 0 -> {
                st = st.withPlayer(st.turn) { it.copy(skip = it.skip - 1) }.say("$name пропускает ход")
                st = advance(st)
            }
            p.quarantine > 0 && p.money >= QUAR_FEE -> return st.copy(phase = "jail")
            p.quarantine > 0 -> {
                st = st.withPlayer(st.turn) { it.copy(quarantine = it.quarantine - 1) }.say("$name ждёт в карантине: на выход не хватает кредитов")
                st = advance(st)
            }
            else -> return st.copy(phase = "roll")
        }
    }
    return st.copy(phase = "roll")
}

private fun advance(s: GameState): GameState {
    if (s.over) return s
    val next = 1 - s.turn
    if (s.finalRound && next == s.first) return finish(s)
    return s.copy(turn = next, turnNo = s.turnNo + 1)
}

fun finish(s: GameState, reason: String = ""): GameState {
    val w0 = wealth(s, 0)
    val w1 = wealth(s, 1)
    val winner = when {
        w0 > w1 -> 0
        w1 > w0 -> 1
        s.players[0].money != s.players[1].money -> if (s.players[0].money > s.players[1].money) 0 else 1
        else -> -2   // ничья
    }
    val head = if (reason.isNotBlank()) "$reason. " else ""
    val result = if (winner == -2) "Ничья: по $w0 ₵" else "Победа: ${playerName(s.players[winner].who)} (${maxOf(w0, w1)} ₵ против ${minOf(w0, w1)} ₵)"
    return s.copy(phase = "over", winner = winner).say(head + "Партия окончена. " + result)
}

private fun moveForward(s: GameState, steps: Int): GameState {
    val p = s.current
    val raw = p.pos + steps
    var st = s.withPlayer(s.turn) { it.copy(pos = raw % BOARD_SIZE) }
    if (raw >= BOARD_SIZE) {
        val laps = p.laps + 1
        st = st.withPlayer(st.turn) { it.copy(laps = laps, money = it.money + PASS_BONUS) }
            .say("${playerName(p.who)} проходит Старт: +$PASS_BONUS ₵ (круг $laps из ${st.laps})")
        if (laps >= st.laps && !st.finalRound) st = st.copy(finalRound = true).say("Это последний круг: партия закончится, когда оба сделают равное число ходов")
    }
    return st
}

private fun moveBack(s: GameState, steps: Int): GameState =
    s.withPlayer(s.turn) { it.copy(pos = (it.pos - steps + BOARD_SIZE) % BOARD_SIZE) }

/** Платёж: to = -1 значит в банк. Если денег не хватает, узлы продаются банку за полцены, начиная с дешёвых. */
private fun pay(s: GameState, from: Int, to: Int, amount: Int): GameState {
    var st = s.withPlayer(from) { it.copy(money = it.money - amount) }
    if (to >= 0) st = st.withPlayer(to) { it.copy(money = it.money + amount) }
    while (st.players[from].money < 0) {
        val mine = st.owners.filterValues { it == from }.keys.sortedBy { BOARD[it].price }
        if (mine.isEmpty()) {
            val other = 1 - from
            return st.copy(phase = "over", winner = other)
                .say("${playerName(st.players[from].who)}: кредиты кончились, продавать нечего. Партия окончена. Победа: ${playerName(st.players[other].who)}")
        }
        val cell = mine.first()
        val back = BOARD[cell].price / 2
        st = st.copy(owners = st.owners - cell).withPlayer(from) { it.copy(money = it.money + back) }
            .say("${playerName(st.players[from].who)} продаёт «${BOARD[cell].name}» банку за $back ₵")
    }
    return st
}

/** Бросок двух кубиков и ход. */
fun rollMove(s: GameState, rnd: Random): GameState {
    if (s.phase != "roll") return s
    val a = rnd.nextInt(1, 7)
    val b = rnd.nextInt(1, 7)
    val p = s.current
    var st = s.copy(dice = listOf(a, b)).say("${playerName(p.who)}: бросок $a + $b = ${a + b}")
    st = moveForward(st, a + b)
    st = landCell(st, rnd, 0)
    return if (st.over || st.phase == "buy") st else endTurn(st)
}

/** Что происходит на клетке. Ход здесь не завершается: это делает rollMove. */
private fun landCell(s: GameState, rnd: Random, depth: Int): GameState {
    if (s.over) return s
    val i = s.turn
    val p = s.current
    val name = playerName(p.who)
    val c = BOARD[p.pos]
    return when (c.type) {
        CellType.START -> s.say("$name на Старте")
        CellType.NODE -> {
            val owner = s.owners[p.pos]
            when {
                owner == null && p.money >= c.price -> s.copy(phase = "buy").say("$name на узле «${c.name}»: свободен, цена ${c.price} ₵")
                owner == null -> s.say("$name на узле «${c.name}»: на покупку не хватает (${c.price} ₵)")
                owner == i -> s.say("$name на своём узле «${c.name}»")
                else -> {
                    val rent = rentFor(s, p.pos)
                    pay(s.say("$name на чужом узле «${c.name}»: плата владельцу $rent ₵"), i, owner, rent)
                }
            }
        }
        CellType.SIGNAL -> {
            val k = rnd.nextInt(SIGNAL_CARDS.size)
            applyCard(s.say("$name тянет карту сигнала: ${SIGNAL_CARDS[k]}"), k, rnd, depth)
        }
        CellType.TAX -> pay(s.say("$name: ${c.name}, −${c.amount} ₵"), i, -1, c.amount)
        CellType.PORTAL -> {
            moveForward(s.say("$name входит в ${c.name.lowercase()} и выходит у «${BOARD[c.target].name}»"), (c.target - p.pos + BOARD_SIZE) % BOARD_SIZE)
        }
        CellType.PATROL -> if (p.shield) s.withPlayer(i) { it.copy(shield = false) }.say("$name встречает патруль, но щит спасает")
            else s.withPlayer(i) { it.copy(skip = it.skip + 1) }.say("$name натыкается на патруль: пропуск хода")
        CellType.CACHE -> s.withPlayer(i) { it.copy(money = it.money + c.amount) }.say("$name находит тайник: +${c.amount} ₵")
        CellType.QUARANTINE -> s.say("$name проходит мимо карантина")
        CellType.FREE -> s.say("$name в нейтральной зоне: передышка")
        CellType.RAID -> if (p.shield) s.withPlayer(i) { it.copy(shield = false) }.say("$name попадает в облаву, но щит спасает")
            else s.withPlayer(i) { it.copy(pos = QUAR_INDEX, quarantine = QUAR_TURNS) }.say("$name попадает в облаву: в карантин на $QUAR_TURNS хода")
        CellType.GLITCH -> {
            val st = moveBack(s.say("$name ловит глитч: назад на 3 клетки"), 3)
            if (depth < 2) landCell(st, rnd, depth + 1) else st
        }
    }
}

private fun applyCard(s: GameState, k: Int, rnd: Random, depth: Int): GameState {
    val i = s.turn
    return when (k) {
        0 -> s.withPlayer(i) { it.copy(money = it.money + 100) }
        1 -> moveForward(s, 3).let { if (depth < 2) landCell(it, rnd, depth + 1) else it }
        2 -> moveBack(s, 2).let { if (depth < 2) landCell(it, rnd, depth + 1) else it }
        3 -> pay(s, i, -1, 60)
        4 -> s.withPlayer(i) { it.copy(money = it.money + 150) }
        5 -> if (s.current.shield) s.withPlayer(i) { it.copy(shield = false) }.say("Щит гасит погоню") else s.withPlayer(i) { it.copy(skip = it.skip + 1) }
        6 -> s.withPlayer(i) { it.copy(shield = true) }
        7 -> pay(s, 1 - i, i, 80)
        8 -> moveForward(s, BOARD_SIZE - s.current.pos)
        else -> pay(s, i, -1, 50)
    }
}

fun endTurn(s: GameState): GameState =
    if (s.over || s.phase == "buy" || s.phase == "jail") s else prepareTurn(advance(s.copy(phase = "roll")))

fun decideBuy(s: GameState, yes: Boolean): GameState {
    if (s.phase != "buy") return s
    val p = s.current
    val c = BOARD[p.pos]
    val st = if (yes && p.money >= c.price && s.owners[p.pos] == null)
        s.withPlayer(s.turn) { it.copy(money = it.money - c.price) }.copy(owners = s.owners + (p.pos to s.turn))
            .say("${playerName(p.who)} покупает «${c.name}» за ${c.price} ₵")
    else s.say("${playerName(p.who)} не покупает «${c.name}»")
    return prepareTurn(advance(st.copy(phase = "roll")))
}

fun decideJail(s: GameState, payOut: Boolean): GameState {
    if (s.phase != "jail") return s
    val p = s.current
    return if (payOut && p.money >= QUAR_FEE)
        s.withPlayer(s.turn) { it.copy(money = it.money - QUAR_FEE, quarantine = 0) }.copy(phase = "roll")
            .say("${playerName(p.who)} платит $QUAR_FEE ₵ и выходит из карантина")
    else prepareTurn(advance(s.withPlayer(s.turn) { it.copy(quarantine = it.quarantine - 1) }.copy(phase = "roll")
        .say("${playerName(p.who)} остаётся в карантине")))
}

/** Если Аврора не ответила (нет связи), решает простое правило. Это пишется в журнал честно. */
fun autoBuy(s: GameState): Boolean = s.current.money - BOARD[s.current.pos].price >= 300
fun autoJail(s: GameState): Boolean = s.current.money >= 400

/**
 * Где клетка на поле, в долях клетки: поле 12×12, углы 1.5×1.5, обычные клетки 1×1.5.
 * side: 0 низ, 1 лево, 2 верх, 3 право (цветная полоска узла смотрит внутрь поля).
 */
data class CellRect(val x: Float, val y: Float, val w: Float, val h: Float, val side: Int, val corner: Boolean)

fun cellRect(i: Int): CellRect = when (i) {
    0 -> CellRect(10.5f, 10.5f, 1.5f, 1.5f, 0, true)
    in 1..9 -> CellRect(10.5f - i, 10.5f, 1f, 1.5f, 0, false)
    10 -> CellRect(0f, 10.5f, 1.5f, 1.5f, 1, true)
    in 11..19 -> CellRect(0f, 10.5f - (i - 10), 1.5f, 1f, 1, false)
    20 -> CellRect(0f, 0f, 1.5f, 1.5f, 2, true)
    in 21..29 -> CellRect(1.5f + (i - 21), 0f, 1f, 1.5f, 2, false)
    30 -> CellRect(10.5f, 0f, 1.5f, 1.5f, 3, true)
    else -> CellRect(10.5f, 1.5f + (i - 31), 1.5f, 1f, 3, false)
}

/** Что видит Аврора о партии: коротко и честно. */
fun gameContext(s: GameState, me: Int = 1): String {
    if (s.over) return ""
    fun line(i: Int): String {
        val p = s.players[i]
        val nodes = s.owners.filterValues { it == i }.keys.sorted().joinToString(", ") { BOARD[it].name }
        val st = buildList {
            if (p.quarantine > 0) add("в карантине")
            if (p.skip > 0) add("пропускает ход")
            if (p.shield) add("есть щит")
        }.joinToString(", ")
        val tok = if (p.token >= 0) " (фишка «${GAME_TOKENS[p.token]}»)" else ""
        return "${playerName(p.who)}$tok: ${p.money} ₵, клетка «${BOARD[p.pos].name}», круг ${p.laps}/${s.laps}" +
            (if (nodes.isNotBlank()) ", узлы: $nodes" else ", узлов нет") + (if (st.isNotBlank()) ", $st" else "")
    }
    val whoTurn = playerName(s.current.who)
    return "ИДЁТ ПАРТИЯ «Город-сеть» (поле из 40 клеток, побеждает тот, кто богаче после ${s.laps} кругов; кубики бросает приложение честно)\n" +
        line(me) + "\n" + line(1 - me) + "\nСейчас ход: $whoTurn. Последнее: " + s.log.takeLast(4).joinToString(" · ") +
        "\nПока идёт партия, в чате отвечай коротко, как за игровым столом: одна-две фразы."
}

private val GAME_LAPS_RE = Regex("""(\d{1,2})""")

fun parseInviteLaps(arg: String): Int = GAME_LAPS_RE.find(arg)?.value?.toIntOrNull()?.coerceIn(2, 8) ?: GAME_DEFAULT_LAPS

val GAME_MARKER_RE = Regex("""\[игра\s*:\s*(приглашение|принять|отказать|итог)(?:\s*\|\s*([^\]]{0,300}))?\]""", RegexOption.IGNORE_CASE)

/** Игровые пометки в сообщениях Адониса превращаются для неё в понятный текст. */
fun expandGameMarkers(text: String): String = GAME_MARKER_RE.replace(text) { m ->
    when (m.groupValues[1].lowercase()) {
        "приглашение" -> "[Адонис приглашает тебя сыграть в настолку «Город-сеть» на ${parseInviteLaps(m.groupValues[2])} круга(ов). " +
            "Решаешь сама: ответь с пометкой [игра: принять] или [игра: отказать]]"
        "принять" -> "[Адонис принял твоё приглашение, партия началась]"
        "отказать" -> "[Адонис отказался от твоего приглашения в игру]"
        else -> "[Партия окончена: ${m.groupValues[2].trim()}]"
    }
}

/** Добавить строку в журнал партии снаружи (например, «нет связи, решило правило»). */
fun gameNote(s: GameState, line: String): GameState = s.say(line)

/** Итог одной строкой для чата и личных счетов. */
fun gameResult(s: GameState): String {
    val w0 = wealth(s, 0)
    val w1 = wealth(s, 1)
    val who = when (s.winner) {
        0 -> "победил Адонис"
        1 -> "победила Аврора"
        else -> "ничья"
    }
    return "Аврора $w1 ₵, Адонис $w0 ₵ · $who · кругов ${s.laps}"
}

/** Карточка клетки после хода: где встали по кубикам, кто, что случилось. */
data class Landing(val cell: Int, val who: String, val lines: List<String>, val seq: Int, val mood: String = "neutral")

fun cellKind(c: Cell): String = when (c.type) {
    CellType.START -> "СТАРТ"
    CellType.NODE -> "УЗЕЛ · РАЙОН ${c.district + 1}"
    CellType.SIGNAL -> "СИГНАЛ"
    CellType.TAX -> "НАЛОГ"
    CellType.PORTAL -> "ПОРТАЛ"
    CellType.PATROL -> "ПАТРУЛЬ"
    CellType.CACHE -> "ТАЙНИК"
    CellType.QUARANTINE -> "КАРАНТИН"
    CellType.RAID -> "ОБЛАВА"
    CellType.GLITCH -> "ГЛИТЧ"
    CellType.FREE -> "НЕЙТРАЛЬНАЯ ЗОНА"
}

fun cellInfo(i: Int, g: GameState): String {
    val c = BOARD[i]
    return when (c.type) {
        CellType.START -> "За каждый проход через Старт +$PASS_BONUS ₵."
        CellType.NODE -> {
            val owner = g.owners[i]
            "Цена ${c.price} ₵. Плата за проход ${c.rent} ₵, вдвое, если у владельца весь район. " +
                if (owner == null) "Узел свободен." else "Владелец: ${playerName(g.players[owner].who)}."
        }
        CellType.SIGNAL -> "Случайная карта сигнала: бывает удача, бывает подстава."
        CellType.TAX -> "${c.name}: −${c.amount} ₵ в банк."
        CellType.PORTAL -> "Переносит к порталу «${BOARD[c.target].name.substringAfter("«").removeSuffix("»")}»."
        CellType.PATROL -> "Наткнулся на патруль: пропусти ход. Щит спасает."
        CellType.CACHE -> "Тайник: +${c.amount} ₵."
        CellType.QUARANTINE -> "Сюда отправляет облава. Выход за $QUAR_FEE ₵ или ждать $QUAR_TURNS хода. Если просто встал, ничего не будет."
        CellType.RAID -> "Облава: прямо в карантин. Щит спасает."
        CellType.GLITCH -> "Глитч: откатывает на 3 клетки назад."
        CellType.FREE -> "Передышка, здесь ничего не происходит."
    }
}

private val GOOD_CARDS = setOf(0, 1, 4, 6, 7, 8)

/**
 * Какая карточка: buff (бонус), debuff (дебаф) или neutral (узел, старт, зона).
 * Для сигнала смотрим, какая карта выпала; на чужом узле заплатить это дебаф, щит от патруля это бонус.
 */
fun landingMood(cell: Int, lines: List<String>): String {
    val c = BOARD[cell]
    val text = lines.joinToString("\n")
    return when (c.type) {
        CellType.CACHE, CellType.PORTAL -> "buff"
        CellType.TAX, CellType.GLITCH -> "debuff"
        CellType.PATROL, CellType.RAID -> if (text.contains("щит спасает")) "buff" else "debuff"
        CellType.SIGNAL -> {
            val k = SIGNAL_CARDS.indexOfFirst { text.contains(it) }
            when {
                k < 0 -> "neutral"
                k in GOOD_CARDS -> "buff"
                else -> "debuff"
            }
        }
        CellType.NODE -> if (text.contains("плата владельцу")) "debuff" else "neutral"
        else -> "neutral"
    }
}
