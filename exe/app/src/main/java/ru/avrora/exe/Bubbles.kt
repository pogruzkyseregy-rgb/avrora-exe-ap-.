package ru.avrora.exe

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

@Composable
fun Avatar(size: Dp) {
    AsyncImage(
        model = "file:///android_asset/avatar.webp",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(avatarShape())
            .border(1.dp, Glow.copy(alpha = 0.55f), avatarShape())
    )
}

@Composable
private fun Cursor(color: androidx.compose.ui.graphics.Color = Glow) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val a by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(520), repeatMode = RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Text("█", color = color.copy(alpha = a), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Avatar(64.dp)
        Spacer(Modifier.height(10.dp))
        Text("AVRORA.EXE", color = Glow, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("Напиши ей первым. Или подожди: она может написать сама.", color = TextDim, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("на связи ", color = TextDim, fontSize = 13.sp)
            Cursor()
        }
    }
}

@Composable
fun TypingRow() {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
        Avatar(30.dp)
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier
                .clip(bubbleShape(false))
                .background(BubbleAurora)
                .border(1.dp, Glow.copy(alpha = 0.4f), bubbleShape(false))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("печатает ", color = TextDim, fontSize = 13.sp)
            Cursor()
        }
    }
}

/** Строка, которую пишет система, а не Аврора и не Адонис: состояние, назначение, след. */
@Composable
private fun SystemLine(text: String) {
    Text(
        "· $text",
        color = TextDim,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFamily = FontFamily.Monospace
    )
}

/** Один вызов инструмента: что она запросила и что вернулось. Нажатие раскрывает вывод целиком. */
@Composable
private fun TraceBlock(t: ToolTrace) {
    var open by remember(t) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text("$ ", color = Violet, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Text(
                    "${t.kind}: ${t.arg}",
                    color = Violet,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (open) Int.MAX_VALUE else 4
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Terminal)
                .border(1.dp, if (t.ok) Edge else ErrorRed, RoundedCornerShape(2.dp))
                .clickable { open = !open }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                (if (t.ok) "" else "не вышло: ") + t.out.trim().ifEmpty { "(пусто)" },
                color = if (t.ok) TextDim else ErrorRed,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = if (open) Int.MAX_VALUE else 6
            )
        }
    }
}

/** Цитата внутри пузыря: кому отвечаю или что пересылаю. */
@Composable
private fun QuoteBlock(q: Quote, accent: androidx.compose.ui.graphics.Color) {
    val who = if (q.who == "адонис") "Адонис" else "Аврора"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Night.copy(alpha = 0.35f))
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(
                (if (q.forward) "Пересланное · " else "") + who,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(q.text, color = TextDim, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3)
        }
    }
}

/** Приглашение в настолку или итог партии. */
@Composable
private fun GameCard(p: Part.Game, mine: Boolean, status: String, onAction: (String) -> Unit) {
    val accent = if (p.kind == "итог") Glow else Magenta
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Night.copy(alpha = 0.5f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .cornerBrackets(accent)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (p.kind == "итог") {
            Text("ПАРТИЯ ОКОНЧЕНА · ГОРОД-СЕТЬ", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(p.arg, color = TextMain, fontSize = 14.sp)
        } else {
        Text("ПРИГЛАШЕНИЕ · ГОРОД-СЕТЬ", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        val laps = parseInviteLaps(p.arg)
        Text(if (mine) "Ты зовёшь Аврору сыграть, кругов: $laps" else "Аврора зовёт тебя сыграть, кругов: $laps", color = TextMain, fontSize = 14.sp)
        when {
            !mine && status == "pending" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = { onAction("accept") }) { Text("Принять") }
                androidx.compose.material3.OutlinedButton(onClick = { onAction("decline") }) { Text("Отказать") }
            }
            status == "accepted" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("принято", color = Glow, fontSize = 12.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { onAction("open") }) { Text("Открыть игру") }
            }
            status == "declined" -> Text(if (mine) "Аврора отказалась" else "ты отказался", color = TextDim, fontSize = 12.sp)
            status == "busy" -> Text("сейчас идёт другая партия", color = TextDim, fontSize = 12.sp)
            else -> Text("ждёт её ответа", color = TextDim, fontSize = 12.sp)
        }
        }
    }
}

/** Ссылка на цитату «[цитата №N]»: карточка с её текстом и автором. */
@Composable
private fun QuoteRefCard(n: Int, q: QuoteItem?) {
    val accent = if (q?.author == "user") Violet else Magenta
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Night.copy(alpha = 0.45f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(
                "ЦИТАТА №$n" + (q?.let { " · " + quoteAuthorName(it.author) } ?: ""),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                q?.let { "«${it.text}»" } ?: "такой цитаты в цитатнике нет",
                color = if (q == null) TextDim else TextMain,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    m: ChatMessage,
    showAvatar: Boolean,
    stickers: List<CatalogItem>,
    photos: List<CatalogItem>,
    sentDir: File,
    revealing: Boolean,
    onOpen: (Any) -> Unit,
    onReply: (ChatMessage) -> Unit = {},
    onForward: (ChatMessage) -> Unit = {},
    lookupQuoted: (String) -> Pair<String, String>? = { null },
    lookupQuoteN: (Int) -> QuoteItem? = { null },
    onSaveQuote: (ChatMessage) -> Unit = {},
    inviteStatus: (ChatMessage) -> String = { "" },
    onGame: (ChatMessage, String) -> Unit = { _, _ -> }
) {
    val mine = m.role == "user"
    val split = remember(m.content) { splitQuote(m.content) }
    val parts = remember(m.content) { parseParts(split.second) }
    // Её цитата-ответ: [ответ: слова]. Находим то сообщение; не нашли, показываем её слова как есть
    val herReply = parts.filterIsInstance<Part.Reply>().firstOrNull()
    val herQuote = remember(m.content) {
        herReply?.let { r ->
            val f = lookupQuoted(r.snippet)
            Quote("reply", if (f?.first == "user" || f == null) "адонис" else "аврора", f?.second ?: r.snippet)
        }
    }
    val quote = split.first ?: herQuote
    val traces = remember(m.trace) { traceFromJson(m.trace) }
    // Её паузы: части идут по очереди, только пока сообщение свежее. Где пауза и на сколько, решила она
    val steps = remember(parts) { splitSteps(parts) }
    var visible by remember(m.time) { mutableIntStateOf(if (revealing) 1 else steps.size) }
    LaunchedEffect(m.time, revealing) {
        if (revealing) {
            while (visible < steps.size) {
                delay(steps[visible].first)
                visible += 1
            }
        } else {
            visible = steps.size
        }
    }
    val shown = steps.take(visible).flatMap { it.second }
    val bubbleParts = shown.filter { it is Part.Text || it is Part.Sticker || it is Part.Photo || it is Part.Snap || it is Part.QuoteRef || it is Part.Media || it is Part.Voice ||
        (it is Part.Game && (it.kind == "приглашение" || it.kind == "итог")) }
    val sideParts = shown.filter {
        it is Part.Silence || it is Part.State || it is Part.Plan || it is Part.Note ||
            it is Part.Post || it is Part.Status || it is Part.Like || it is Part.Comment || it is Part.Avatar || it is Part.Keep || it is Part.SaveQuote ||
            (it is Part.Game && (it.kind == "принять" || it.kind == "отказать"))
    }
    // (Part.Reply в пузырь не входит: он показан цитатой наверху)
    val frameless = quote == null && bubbleParts.isNotEmpty() && bubbleParts.all { it is Part.Sticker }
    val accent = if (mine) Violet else Glow
    val shape = bubbleShape(mine)
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val copyText = remember(m.content) {
        parseParts(splitQuote(m.content).second).filterIsInstance<Part.Text>().joinToString("\n") { it.s }
    }

    Column(
        Modifier.fillMaxWidth().padding(top = if (showAvatar || mine) 6.dp else 0.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (!mine) traces.forEach {
            Box(Modifier.padding(start = 36.dp).widthIn(max = 320.dp)) { TraceBlock(it) }
        }
        if (bubbleParts.isNotEmpty() || quote != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (!mine) {
                    if (showAvatar) Avatar(30.dp) else Spacer(Modifier.width(30.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Box {
                    Column(
                        Modifier
                            .widthIn(max = 300.dp)
                            .then(
                                if (frameless) Modifier
                                else Modifier
                                    .clip(shape)
                                    .background(if (mine) BubbleUser else BubbleAurora)
                                    .border(1.dp, accent.copy(alpha = 0.5f), shape)
                            )
                            .combinedClickable(onClick = {}, onLongClick = { menu = true })
                            .padding(horizontal = if (frameless) 0.dp else 12.dp, vertical = if (frameless) 0.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (quote != null) QuoteBlock(quote, accent)
                        bubbleParts.forEach { p ->
                            when (p) {
                                is Part.Text -> Text(
                                    p.s,
                                    color = TextMain,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp
                                )
                                is Part.Sticker -> {
                                    val item = stickers.firstOrNull { s -> s.id == p.id }
                                    if (item != null) {
                                        AsyncImage(
                                            model = item.model,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .height(150.dp)
                                                .aspectRatio(item.aspect.coerceIn(0.3f, 3f))
                                        )
                                    }
                                }
                                is Part.Photo -> {
                                    val item = photos.firstOrNull { s -> s.id == p.id }
                                    if (item != null) PhotoImage(item.model, item.aspect, onOpen)
                                }
                                is Part.QuoteRef -> QuoteRefCard(p.n, lookupQuoteN(p.n))
                                is Part.Game -> GameCard(p, mine, inviteStatus(m)) { action -> onGame(m, action) }
                                is Part.Snap -> {
                                    val file = File(sentDir, p.file)
                                    val aspect = remember(file) { imageAspect(file) }
                                    PhotoImage(file, aspect, onOpen)
                                }
                                is Part.Media -> MediaPart(p, accent, onOpen)
                                is Part.Voice -> VoicePart(p, accent)
                                else -> {}
                            }
                        }
                        Text(
                            (if (m.auto) "сама · " else "") + dayOrTime(m.time),
                            color = TextDim,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ответить") }, onClick = { menu = false; onReply(m) })
                        DropdownMenuItem(text = { Text("Переслать") }, onClick = { menu = false; onForward(m) })
                        DropdownMenuItem(text = { Text("В цитатник") }, onClick = { menu = false; onSaveQuote(m) })
                        if (copyText.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Копировать") },
                                onClick = { menu = false; clipboard.setText(AnnotatedString(copyText)) }
                            )
                        }
                    }
                }
            }
        }
        sideParts.forEach { p ->
            val pad = Modifier.padding(start = if (mine) 0.dp else 36.dp)
            when (p) {
                is Part.Silence -> Text(
                    "прочитано, ответа нет",
                    color = TextDim,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = pad
                )
                is Part.State -> Box(pad) {
                    SystemLine("состояние → ${p.name}" + if (p.why.isNotBlank()) " — ${p.why}" else "")
                }
                is Part.Plan -> Box(pad) { SystemLine("назначено: ${p.whenText} — ${p.topic}") }
                is Part.Note -> Box(pad) { SystemLine("след [${p.kind}]: ${p.text}") }
                is Part.Post -> Box(pad) { SystemLine("пост в ленту: ${p.text.take(120)}" + if (p.text.length > 120) "…" else "") }
                is Part.Status -> Box(pad) { SystemLine("статус → ${p.text}") }
                is Part.Like -> Box(pad) { SystemLine("лайк: ${p.snippet.take(80)}") }
                is Part.Comment -> Box(pad) { SystemLine("комментарий к «${p.snippet.take(40)}»: ${p.text.take(120)}") }
                is Part.Avatar -> Box(pad) { SystemLine("сменила аватар") }
                is Part.Keep -> Box(pad) { SystemLine("оставила себе фото: ${p.desc.take(80)}") }
                is Part.SaveQuote -> Box(pad) { SystemLine("в цитатник: «${p.text.take(100)}»") }
                is Part.Game -> Box(pad) {
                    SystemLine(
                        (if (mine) "Адонис " else "Аврора ") +
                            if (p.kind == "принять") (if (mine) "принял приглашение в игру" else "приняла приглашение в игру")
                            else (if (mine) "отказался от игры" else "отказалась от игры")
                    )
                }
                else -> {}
            }
        }
    }
}

private fun dayOrTime(t: Long): String {
    val now = System.currentTimeMillis()
    return if (now - t < 20 * 3_600_000L) timeFormat.format(Date(t)) else dayFormat.format(Date(t))
}

/** Делит части сообщения по её паузам: (задержка перед группой в мс, части группы). */
private fun splitSteps(parts: List<Part>): List<Pair<Long, List<Part>>> {
    val steps = ArrayList<Pair<Long, List<Part>>>()
    var cur = ArrayList<Part>()
    var delayMs = 0L
    for (p in parts) {
        if (p is Part.Pause) {
            if (cur.isNotEmpty()) {
                steps.add(Pair(delayMs, cur))
                cur = ArrayList()
            }
            delayMs = p.seconds * 1000L
        } else {
            cur.add(p)
        }
    }
    if (cur.isNotEmpty()) steps.add(Pair(delayMs, cur))
    return steps
}

@Composable
private fun PhotoImage(model: Any, aspect: Float, onOpen: (Any) -> Unit) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(240.dp)
            .aspectRatio(aspect.coerceIn(0.5f, 2f))
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Edge, RoundedCornerShape(10.dp))
            .clickable { onOpen(model) }
    )
}
