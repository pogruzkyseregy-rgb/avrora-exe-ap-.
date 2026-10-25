package ru.avrora.exe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

private val DistrictColors = listOf(
    Color(0xFF5EEAD4), Color(0xFFA78BFA), Color(0xFFD946EF), Color(0xFFF472B6),
    Color(0xFF60A5FA), Color(0xFFFBBF24), Color(0xFF34D399), Color(0xFFF87171)
)
private val UserColor get() = Violet
private val HerColor get() = Glow
private val TokenRes = listOf(R.drawable.token_eyes, R.drawable.token_heart, R.drawable.token_wave)

private fun cellAccent(c: Cell): Color = when (c.type) {
    CellType.NODE -> DistrictColors[c.district.coerceIn(0, DistrictColors.lastIndex)]
    CellType.PATROL, CellType.RAID, CellType.GLITCH -> Magenta
    CellType.TAX -> ErrorRed
    CellType.PORTAL -> Violet
    else -> Glow
}

private fun playerColor(who: String) = if (who == "user") UserColor else HerColor

@Composable
fun GameScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var chatOpen by remember { mutableStateOf(false) }
    var journal by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Int?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    val g = vm.game
    Box(Modifier.fillMaxSize().background(Night)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = TextMain) }
                Column(Modifier.weight(1f)) {
                    Text("ГОРОД-СЕТЬ", color = Glow, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
                    if (g != null) {
                        val lap = (g.players.maxOf { it.laps } + 1).coerceAtMost(g.laps)
                        Text(if (g.over) "партия окончена" else "круг $lap из ${g.laps}" + if (g.finalRound) " · последний" else "",
                            color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (g != null) TextButton(onClick = { journal = true }) { Text("ЖУРНАЛ", color = Violet, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }
            GradientLine()
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (g == null) {
                    Lobby(vm, onBack)
                } else {
                    PlayersRow(g, vm)
                    Board(g, vm, info, onCell = { info = if (info == it) null else it })
                    if (g.over) {
                        Button(onClick = { vm.closeGame() }, modifier = Modifier.fillMaxWidth()) { Text("Новая партия") }
                    } else {
                        TextButton(onClick = { confirmStop = true }) { Text("Завершить досрочно", color = TextDim) }
                    }
                    Spacer(Modifier.height(60.dp))
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(Panel)
                .border(1.5.dp, Glow, CircleShape)
                .clickable { chatOpen = !chatOpen },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(24.dp)) { drawGlyph(Glyph.Chat, Glow) }
        }
        // карточка клетки вылетает поверх поля
        val landing = vm.landing
        AnimatedVisibility(visible = landing != null && g != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            // затемнение: нажатие куда угодно закрывает карточку
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { vm.dismissLanding() }
            )
        }
        AnimatedVisibility(
            visible = landing != null && g != null,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(tween(260), initialScale = 0.55f) + fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            if (landing != null && g != null) LandingCard(g, landing, vm)
        }
        if (g != null && g.phase == "pick") {
            Box(Modifier.align(Alignment.Center)) { PickCard(g, vm) }
        }
        if (chatOpen) GameChat(vm, Modifier.align(Alignment.BottomCenter)) { chatOpen = false }
    }
    if (g != null && !g.over && g.phase != "order" && !vm.gameResumeAsked) {
        AlertDialog(
            onDismissRequest = { vm.gameResumeAsked = true },
            containerColor = Panel,
            title = { Text("Партия сохранена") },
            text = {
                val lap = (g.players.maxOf { it.laps } + 1).coerceAtMost(g.laps)
                Text("Круг $lap из ${g.laps}. Адонис ${g.players[0].money} ₵, Аврора ${g.players[1].money} ₵. Продолжить с того же места или начать заново? Брошенная партия на счета не идёт.")
            },
            confirmButton = { TextButton(onClick = { vm.gameResumeAsked = true; vm.resumeGame() }) { Text("Продолжить") } },
            dismissButton = { TextButton(onClick = { vm.restartGame() }) { Text("Начать заново") } }
        )
    }
    if (journal && g != null) {
        AlertDialog(
            onDismissRequest = { journal = false },
            containerColor = Panel,
            title = { Text("Журнал партии", fontFamily = FontFamily.Monospace) },
            text = {
                Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    g.log.reversed().forEach { Text("› $it", color = TextDim, fontSize = 12.sp, lineHeight = 16.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { journal = false }) { Text("Закрыть") } }
        )
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = Panel,
            title = { Text("Завершить партию?") },
            text = { Text("Победит тот, кто богаче прямо сейчас. Итог уйдёт на личные счета.") },
            confirmButton = { TextButton(onClick = { vm.gameStop(); confirmStop = false }) { Text("Завершить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Играть дальше") } }
        )
    }
}

@Composable
private fun TokenImage(k: Int, size: Dp, ring: Color) {
    Image(
        painter = painterResource(TokenRes[k.coerceIn(0, TokenRes.lastIndex)]),
        contentDescription = GAME_TOKENS.getOrNull(k),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape).border(1.5.dp, ring, CircleShape)
    )
}

@Composable
private fun Lobby(vm: ChatViewModel, onBack: () -> Unit) {
    var laps by remember { mutableStateOf(GAME_DEFAULT_LAPS) }
    Text("Настолка на двоих: круговое поле, узлы, патрули, карантин и кредиты. Побеждает тот, кто богаче после выбранного числа кругов. Кубики бросает приложение, решения Аврора принимает сама.",
        color = TextMain, fontSize = 14.sp, lineHeight = 20.sp)
    Text("Личные счета: Адонис ${vm.walletUser} ₵ · Аврора ${vm.walletAurora} ₵", color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    Text("КРУГОВ", color = Violet, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(2, 3, 5, 8).forEach { n ->
            val on = laps == n
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) BubbleUser else Panel)
                    .border(1.dp, if (on) Violet else Edge, RoundedCornerShape(16.dp))
                    .clickable { laps = n }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) { Text("$n", color = if (on) TextMain else TextDim) }
        }
    }
    Button(onClick = { vm.inviteToGame(laps); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Пригласить Аврору") }
    Text("Приглашение уйдёт в чат, и она решит, играть ли. Она может позвать тебя и сама.", color = TextDim, fontSize = 12.sp)
}

@Composable
private fun PlayersRow(g: GameState, vm: ChatViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        g.players.forEachIndexed { i, p ->
            val color = playerColor(p.who)
            val turn = !g.over && g.turn == i
            Row(
                Modifier
                    .weight(1f)
                    .background(Terminal, RoundedCornerShape(4.dp))
                    .border(1.dp, if (turn) color else Edge, RoundedCornerShape(4.dp))
                    .then(if (turn) Modifier.cornerBrackets(color) else Modifier)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (p.token >= 0) TokenImage(p.token, 34.dp, color)
                else Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(playerName(p.who).uppercase(), color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("${p.money} ₵", color = TextMain, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
                    val nodes = g.owners.count { it.value == i }
                    Text("узлов $nodes · всего ${wealth(g, i)}", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    val flags = buildList {
                        if (p.quarantine > 0) add("карантин")
                        if (p.skip > 0) add("пропуск")
                        if (p.shield) add("щит")
                        if (turn && p.who == "aurora" && vm.gameThinking) add("думает…")
                    }
                    if (flags.isNotEmpty()) Text(flags.joinToString(" · "), color = Magenta, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun Board(g: GameState, vm: ChatViewModel, info: Int?, onCell: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF07050D)).border(1.dp, Edge)
    ) {
        val u = maxWidth / 12
        // центр: логотип AV и две колоды карт, как на классическом поле
        Box(Modifier.offset(x = u * 1.5f, y = u * 1.5f).size(u * 9)) {
            Image(
                painter = painterResource(R.drawable.av_logo),
                contentDescription = "AV",
                colorFilter = ColorFilter.tint(Glow),
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.82f).alpha(0.16f)
            )
            Deck("СИГНАЛ", Glow, Modifier.align(Alignment.TopEnd).padding(top = u * 0.9f, end = u * 0.5f).rotate(-32f), u)
            Deck("ТАЙНИК", Magenta, Modifier.align(Alignment.BottomStart).padding(bottom = u * 0.9f, start = u * 0.5f).rotate(-32f), u)
            Box(Modifier.align(Alignment.Center).padding(u * 0.6f)) { CenterPanel(g, vm, info) }
        }
        BOARD.forEachIndexed { i, c ->
            val r = cellRect(i)
            val owner = g.owners[i]
            val ownerColor = owner?.let { playerColor(g.players[it].who) }
            val accent = cellAccent(c)
            Box(
                Modifier
                    .offset(x = u * r.x, y = u * r.y)
                    .size(u * r.w, u * r.h)
                    .background(if (info == i) BubbleUser else Terminal)
                    .border(if (ownerColor != null) 1.5.dp else 0.5.dp, ownerColor ?: Edge)
                    .clickable { onCell(i) }
            ) {
                if (c.type == CellType.NODE) {
                    val strip = u * 0.32f
                    val m = when (r.side) {
                        0 -> Modifier.align(Alignment.TopCenter).fillMaxWidth().height(strip)
                        1 -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(strip)
                        2 -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(strip)
                        else -> Modifier.align(Alignment.CenterStart).fillMaxHeight().width(strip)
                    }
                    Box(m.background(accent))
                    Text("${c.price}", color = TextDim, fontSize = 6.sp, lineHeight = 7.sp, maxLines = 1,
                        modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        c.short,
                        color = accent,
                        fontSize = if (r.corner) 6.sp else 10.sp,
                        lineHeight = if (r.corner) 7.sp else 11.sp,
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        // фишки поверх клеток, плавно переезжают
        g.players.forEachIndexed { k, p ->
            if (p.token >= 0) {
                val r = cellRect(p.pos)
                val t = u * 0.62f
                val shift = if (g.players[1 - k].pos == p.pos) (if (k == 0) -0.22f else 0.22f) else 0f
                val x by animateDpAsState(u * (r.x + r.w / 2 + shift) - t / 2, tween(450), label = "tx$k")
                val y by animateDpAsState(u * (r.y + r.h / 2 + shift) - t / 2, tween(450), label = "ty$k")
                Box(Modifier.offset(x = x, y = y)) { TokenImage(p.token, t, playerColor(p.who)) }
            }
        }
    }
}

@Composable
private fun Deck(title: String, color: Color, modifier: Modifier, u: Dp) {
    Box(
        modifier
            .size(u * 2.6f, u * 1.6f)
            .background(Night.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) { Text(title, color = color.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace) }
}

@Composable
private fun CenterPanel(g: GameState, vm: ChatViewModel, info: Int?) {
    var flick by remember { mutableStateOf(Pair(1, 1)) }
    LaunchedEffect(vm.diceRolling) {
        while (vm.diceRolling) {
            flick = Pair(kotlin.random.Random.nextInt(1, 7), kotlin.random.Random.nextInt(1, 7))
            delay(70)
        }
    }
    val dice = if (vm.diceRolling) listOf(flick.first, flick.second) else g.dice
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (if (dice.isEmpty()) listOf(0, 0) else dice).forEach { d ->
                Box(
                    Modifier.size(38.dp).background(Terminal, RoundedCornerShape(8.dp))
                        .border(1.5.dp, if (vm.diceRolling) Magenta else Glow, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (d == 0) "·" else "$d", color = Glow, fontSize = 19.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (info != null) {
            Text(BOARD[info].name, color = TextMain, fontSize = 11.sp, textAlign = TextAlign.Center)
            Text(cellInfo(info, g), color = TextDim, fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center)
        }
        val mine = !g.over && g.current.who == "user"
        val turnLine = when {
            g.over -> g.log.lastOrNull() ?: "Партия окончена"
            g.phase == "order" -> "Бросок решит, кто ходит первым"
            g.phase == "pick" -> "Выбор фишки: ${playerName(g.current.who)}"
            mine -> "Твой ход"
            vm.gameThinking -> "Аврора думает…"
            else -> "Ходит Аврора"
        }
        Text(turnLine, color = if (mine) Violet else Glow, fontSize = 12.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
        when {
            g.over -> {}
            g.phase == "order" -> Button(onClick = { vm.gameOrder() }, enabled = !vm.diceRolling) { Text("Бросить за очерёдность", fontSize = 12.sp) }
            mine && g.phase == "roll" -> Button(onClick = { vm.gameRoll() }, enabled = !vm.diceRolling) { Text("Бросить кубики") }
            mine && g.phase == "buy" && vm.landing == null -> {
                val c = BOARD[g.current.pos]
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { vm.gameBuy(true) }) { Text("Купить ${c.price}", fontSize = 11.sp) }
                    OutlinedButton(onClick = { vm.gameBuy(false) }) { Text("Мимо", fontSize = 11.sp) }
                }
            }
            mine && g.phase == "jail" -> {
                Text("Карантин: выйти за $QUAR_FEE ₵?", color = TextMain, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { vm.gameJail(true) }) { Text("Выйти", fontSize = 11.sp) }
                    OutlinedButton(onClick = { vm.gameJail(false) }) { Text("Ждать", fontSize = 11.sp) }
                }
            }
        }
    }
}

/** Карточка клетки: что это за поле и что сейчас произошло. Висит, пока Адонис не нажмёт куда-нибудь. */
@Composable
private fun LandingCard(g: GameState, l: Landing, vm: ChatViewModel) {
    val c = BOARD[l.cell]
    val accent = cellAccent(c)
    val decide = g.phase == "buy" && g.current.who == "user" && l.who == "user" && !g.over
    val art = when (l.mood) {
        "buff" -> R.drawable.card_buff
        "debuff" -> R.drawable.card_debuff
        else -> null
    }
    val shape = RoundedCornerShape(12.dp)
    if (art == null) {
        Column(
            Modifier
                .width(290.dp)
                .background(Panel, shape)
                .border(1.5.dp, accent, shape)
                .cornerBrackets(accent, 10.dp, 2.dp)
                .clickable { vm.dismissLanding() }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (c.type == CellType.NODE) Box(Modifier.fillMaxWidth().height(10.dp).background(accent, RoundedCornerShape(3.dp)))
            Text(cellKind(c), color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(c.name, color = TextMain, fontSize = 20.sp)
            Text(cellInfo(l.cell, g), color = TextDim, fontSize = 13.sp, lineHeight = 18.sp)
            LandingLines(l, playerColor(l.who), TextMain)
            LandingFooter(decide, c, vm, TextDim)
        }
    } else {
        // бонус и дебаф: бумажная карточка с акварельным фоном
        val ink = Color(0xFF1A1020)
        val tone = if (l.mood == "buff") Color(0xFFDB2777) else Color(0xFFDC2626)
        Box(
            Modifier
                .width(290.dp)
                .height(420.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.5.dp, tone, shape)
                .clickable { vm.dismissLanding() }
        ) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(if (l.mood == "buff") "БОНУС" else "ДЕБАФ", color = tone, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(c.name, color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .background(Color.White.copy(alpha = 0.86f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(cellInfo(l.cell, g), color = ink.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
                LandingLines(l, tone, ink)
                LandingFooter(decide, c, vm, ink.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun LandingLines(l: Landing, head: Color, body: Color) {
    if (l.lines.isEmpty()) return
    Text((if (l.who == "user") "АДОНИС" else "АВРОРА") + " · ЧТО ПРОИЗОШЛО", color = head, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    l.lines.takeLast(6).forEach { Text("› $it", color = body, fontSize = 12.sp, lineHeight = 16.sp) }
}

@Composable
private fun LandingFooter(decide: Boolean, c: Cell, vm: ChatViewModel, hint: Color) {
    if (decide) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.dismissLanding(); vm.gameBuy(true) }) { Text("Купить за ${c.price}") }
            OutlinedButton(onClick = { vm.dismissLanding(); vm.gameBuy(false) }) { Text("Мимо") }
        }
    } else {
        Text("нажми куда угодно, чтобы закрыть", color = hint, fontSize = 10.sp)
    }
}

/** Выбор фишки: выбирает тот, чей ход; второй берёт из оставшихся. */
@Composable
private fun PickCard(g: GameState, vm: ChatViewModel) {
    val mine = g.current.who == "user"
    val free = freeTokens(g)
    Column(
        Modifier
            .width(300.dp)
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.5.dp, Violet, RoundedCornerShape(10.dp))
            .cornerBrackets(Magenta, 10.dp, 2.dp)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("ВЫБОР ФИШКИ", color = Magenta, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(if (mine) "Твой выбор" else if (vm.gameThinking) "Аврора выбирает…" else "Выбирает Аврора", color = TextMain, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GAME_TOKENS.indices.forEach { k ->
                val taken = k !in free
                val holder = g.players.firstOrNull { it.token == k }
                Column(
                    Modifier
                        .alpha(if (taken) 0.45f else 1f)
                        .clickable(enabled = mine && !taken) { vm.gamePick(k) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TokenImage(k, 72.dp, holder?.let { playerColor(it.who) } ?: Edge)
                    Text(GAME_TOKENS[k], color = TextMain, fontSize = 11.sp)
                    if (holder != null) Text(playerName(holder.who), color = playerColor(holder.who), fontSize = 10.sp)
                }
            }
        }
    }
}

/** Чат по ходу игры: та же переписка, что и в главном чате, просто под рукой. */
@Composable
private fun GameChat(vm: ChatViewModel, modifier: Modifier, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val list = vm.messages.takeLast(30)
    val state = rememberLazyListState()
    LaunchedEffect(list.size, vm.sending) { if (list.isNotEmpty()) state.animateScrollToItem(list.size - 1) }
    Column(
        modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(Panel)
            .border(1.dp, Edge)
    ) {
        GradientLine()
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ЧАТ", color = Glow, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextDim) }
        }
        LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(list) { m ->
                val mine = m.role == "user"
                val body = quoteSnippet(m.content, 400).ifBlank {
                    parseParts(m.content).filterIsInstance<Part.Game>().firstOrNull()?.let { if (it.kind == "итог") "итог партии: ${it.arg}" else "игра: ${it.kind}" } ?: "…"
                }
                Text(
                    (if (mine) "Адонис: " else "Аврора: ") + body,
                    color = if (mine) Violet else TextMain,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }
            if (vm.sending) item { Text("Аврора печатает…", color = TextDim, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сказать по ходу…") },
                maxLines = 3,
                shape = RoundedCornerShape(20.dp),
                colors = auroraFieldColors()
            )
            IconButton(onClick = {
                if (text.isNotBlank() && !vm.sending) {
                    vm.send(text)
                    text = ""
                }
            }) { Icon(Icons.Default.Send, contentDescription = "Отправить", tint = if (text.isNotBlank()) Glow else TextDim) }
        }
    }
}
