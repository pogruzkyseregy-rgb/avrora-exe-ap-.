package ru.avrora.exe

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/*
 * ДОМ: общее пространство Авроры, Гесперы, Эст и Адониса. Живёт на сервере: распорядок, сцены, перестановки.
 * Здесь — чертёж сверху: комнаты, мебель, фигурки, которые плавно ходят по маршрутам, и короткие реплики в облачках.
 * Нажал на комнату — твоя фигурка идёт туда. Нажал на девчонку — погладить, обнять, угостить, позвать.
 */

data class HRoom(val id: String, val name: String, val x: Float, val y: Float, val w: Float, val h: Float, val doorX: Float, val doorY: Float)
data class HFurn(val id: String, val kind: String, val room: String, val x: Float, val y: Float, val w: Float, val h: Float, val label: String)
data class HPoint(val x: Float, val y: Float, val t: Double)
data class HChar(val who: String, val x: Float, val y: Float, val room: String, val act: String, val path: List<HPoint>) {
    /** Где сейчас: по маршруту, если идёт. */
    fun pos(now: Double): Pair<Float, Float> {
        if (path.isEmpty() || now >= path.last().t) return if (path.isEmpty()) x to y else path.last().x to path.last().y
        if (now <= path.first().t) return path.first().x to path.first().y
        for (i in 1 until path.size) {
            val p = path[i - 1]; val q = path[i]
            if (now < q.t) {
                val k = ((now - p.t) / (q.t - p.t).coerceAtLeast(0.01)).toFloat()
                return p.x + (q.x - p.x) * k to p.y + (q.y - p.y) * k
            }
        }
        return path.last().x to path.last().y
    }
    fun walking(now: Double) = path.isNotEmpty() && now < path.last().t
}
data class HLine(val at: Double, val who: String, val text: String, val room: String)
data class HouseState(
    val now: Double, val w: Int, val h: Int, val rooms: List<HRoom>, val furniture: List<HFurn>,
    val chars: Map<String, HChar>, val lines: List<HLine>, val events: List<String>
)

/** Для снимков экрана: готовое состояние дома вместо сервера. */
object HouseDebug {
    var fakeJson: String? = null
}

object HouseApi {
    fun parse(o: JSONObject): HouseState {
        fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
        val rooms = arr("rooms").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map {
            val r = it.getJSONArray("rect"); val d = it.getJSONArray("door")
            HRoom(it.getString("id"), it.getString("name"), r.getDouble(0).toFloat(), r.getDouble(1).toFloat(), r.getDouble(2).toFloat(),
                r.getDouble(3).toFloat(), d.getDouble(0).toFloat(), d.getDouble(1).toFloat())
        } }
        val furn = arr("furniture").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map {
            HFurn(it.getString("id"), it.getString("kind"), it.getString("room"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat(),
                it.getDouble("w").toFloat(), it.getDouble("h").toFloat(), it.optString("label"))
        } }
        val chars = o.optJSONObject("chars")?.let { c ->
            c.keys().asSequence().associateWith { k ->
                val j = c.getJSONObject(k)
                val p = j.optJSONArray("path") ?: JSONArray()
                HChar(k, j.getDouble("x").toFloat(), j.getDouble("y").toFloat(), j.optString("room"), j.optString("act"),
                    (0 until p.length()).map { p.getJSONArray(it) }.map { HPoint(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2)) })
            }
        } ?: emptyMap()
        val lines = arr("lines").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map {
            HLine(it.getDouble("at"), it.getString("who"), it.getString("text"), it.optString("room"))
        } }
        val events = arr("events").let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("text") } }
        return HouseState(o.getDouble("now"), o.optInt("w", 16), o.optInt("h", 20), rooms, furn, chars, lines, events)
    }

    suspend fun state(ctx: Context, since: Double): HouseState {
        val p = AppPrefs(ctx)
        return parse(AuroraApi.get(p.serverUrl, "/house/state?since=$since", p.token))
    }

    suspend fun act(ctx: Context, action: String, room: String = "", who: String = "", text: String = ""): HouseState {
        val p = AppPrefs(ctx)
        val body = JSONObject().put("action", action).put("room", room).put("who", who).put("text", text)
        return parse(AuroraApi.post(p.serverUrl, "/house/act", p.token, body))
    }
}

private fun houseColor(who: String): Color = when (who) {
    WHO_AURORA -> Glow
    WHO_HESPERA -> HespColor
    WHO_EST -> EstColor
    else -> Violet
}

private fun houseName(who: String) = when (who) { "adonis" -> "Ты"; else -> whoName(who) }

@Composable
private fun HouseFace(who: String, size: androidx.compose.ui.unit.Dp, hesp: ByteArray?) {
    when (who) {
        WHO_AURORA -> Avatar(size)
        WHO_HESPERA -> HespAvatar(hesp, size)
        WHO_EST -> EstAvatar(size)
        else -> Box(Modifier.size(size).clip(CircleShape).background(Panel).border(1.5.dp, Violet, CircleShape), contentAlignment = Alignment.Center) {
            Text("Я", color = Violet, fontSize = (size.value * 0.45f).sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun HouseScreen(hvm: HesperaViewModel, onGear: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<HouseState?>(null) }
    val lines = remember { mutableStateListOf<HLine>() }
    var offset by remember { mutableStateOf(0.0) }          // часы сервера минус часы телефона, сек
    var error by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var say by remember { mutableStateOf("") }

    fun take(s: HouseState) {
        state = s
        offset = s.now - System.currentTimeMillis() / 1000.0
        val seen = lines.map { it.at to it.who }.toSet()
        s.lines.filter { (it.at to it.who) !in seen }.forEach { lines.add(it) }
        while (lines.size > 60) lines.removeAt(0)
        error = ""
    }

    fun act(action: String, room: String = "", who: String = "", text: String = "") {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { HouseApi.act(ctx, action, room, who, text) }.onSuccess { take(it) }.onFailure { error = it.message ?: "нет связи" }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        HouseDebug.fakeJson?.let { take(HouseApi.parse(JSONObject(it))); return@LaunchedEffect }
        while (true) {
            val since = lines.lastOrNull()?.at ?: 0.0
            runCatching { HouseApi.state(ctx, if (state == null) 0.0 else since) }
                .onSuccess { take(it) }.onFailure { if (state == null) error = it.message ?: "нет связи" }
            delay(3000)
        }
    }
    val frame by produceState(0L) { while (true) withFrameMillis { value = it } }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("ДОМ", onGear)
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (error.isBlank()) "Открываю дверь…" else "Дом не отвечает: $error", color = TextDim, fontSize = 14.sp)
            }
            return@Column
        }
        @Suppress("UNUSED_VARIABLE") val tick = frame
        val now = System.currentTimeMillis() / 1000.0 + offset
        val me = s.chars["adonis"]
        val myRoom = me?.let { m -> m.pos(now).let { (x, y) -> roomAt(s, x, y) } } ?: ""

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.TopCenter) {
            val density = LocalDensity.current
            val cellDp = minOf(maxWidth / s.w, maxHeight / s.h)
            val cell = with(density) { cellDp.toPx() }
            val planW = cellDp * s.w
            val planH = cellDp * s.h
            Box(Modifier.size(planW, planH)) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(s.rooms, s.chars.size) {
                        detectTapGestures { o ->
                            val gx = o.x / cell; val gy = o.y / cell
                            val tNow = System.currentTimeMillis() / 1000.0 + offset
                            val hit = s.chars.values.filter { it.who != "adonis" }.minByOrNull { c ->
                                val (cx, cy) = c.pos(tNow); (cx - gx) * (cx - gx) + (cy - gy) * (cy - gy)
                            }?.takeIf { c -> val (cx, cy) = c.pos(tNow); (cx - gx) * (cx - gx) + (cy - gy) * (cy - gy) < 1.2f }
                            if (hit != null) { picked = hit.who; return@detectTapGestures }
                            picked = null
                            val room = roomAt(s, gx, gy)
                            if (room.isNotBlank() && room != "hall") act("move", room = room)
                        }
                    }
                ) {
                    drawPlan(s, cell, myRoom)
                }
                // фигурки
                val face = cellDp * 1.25f
                s.chars.values.sortedBy { if (it.who == "adonis") 1 else 0 }.forEach { c ->
                    val (x, y) = c.pos(now)
                    val px = with(density) { (x * cell - (face / 2).toPx()).roundToInt() }
                    val py = with(density) { (y * cell - (face / 2).toPx()).roundToInt() }
                    Box(Modifier.offset { IntOffset(px, py) }.size(face).clickable { if (c.who != "adonis") picked = c.who }) {
                        HouseFace(c.who, face, hvm.avatar)
                        if (picked == c.who) Box(Modifier.matchParentSize().border(2.dp, houseColor(c.who), CircleShape))
                    }
                }
                // реплики в облачках: 7 секунд над головой
                lines.filter { now >= it.at && now - it.at < 7 }.groupBy { it.who }.forEach { (who, ls) ->
                    val c = s.chars[who] ?: return@forEach
                    val l = ls.last()
                    val (x, y) = c.pos(now)
                    val px = with(density) { (x * cell).roundToInt() - 70.dp.roundToPx() }
                    val py = with(density) { (y * cell).roundToInt() - (face).roundToPx() - 34.dp.roundToPx() }
                    Box(Modifier.offset { IntOffset(px.coerceIn(0, (planW - 140.dp).roundToPx().coerceAtLeast(0)), py.coerceAtLeast(0)) }.width(140.dp)
                        .wrapContentSize(Alignment.BottomCenter)) {
                        Text(l.text, color = TextMain, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 3,
                            modifier = Modifier.widthIn(max = 140.dp).clip(RoundedCornerShape(8.dp)).background(Panel.copy(alpha = 0.94f))
                                .border(1.dp, houseColor(who).copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            }
        }

        // карточка выбранной: чем занята и что с ней сделать
        picked?.let { who ->
            val c = s.chars[who]
            if (c != null) {
                val (x, y) = c.pos(now)
                val room = roomAt(s, x, y)
                val near = room == myRoom && !c.walking(now)
                Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(houseName(who), color = houseColor(who), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(8.dp))
                        Text((if (c.walking(now)) "идёт" else c.act).ifBlank { "здесь" } + " · " + (s.rooms.firstOrNull { it.id == room }?.name ?: ""),
                            color = TextDim, fontSize = 12.sp, maxLines = 1)
                    }
                    val acts = if (near) listOf("Погладить" to { act("pet", who = who) }, "Обнять" to { act("hug", who = who) },
                        "Угостить" to { act("treat", who = who) }, "Позвать на кухню" to { act("call", room = "kitchen", who = who) },
                        "Позвать в гостиную" to { act("call", room = "living", who = who) }, "Позвать к себе" to { act("call", room = "adonis", who = who) })
                    else listOf("Подойти к ней" to { if (room.isNotBlank() && room != "hall") act("move", room = room) })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(acts) { (label, run) ->
                            Text(label, color = if (busy) TextDim else houseColor(who), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, houseColor(who).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .clickable(enabled = !busy) { run() }.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        // маленькое окошко разговоров и строка «сказать»
        Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 6.dp)) {
            val recent = lines.filter { it.at <= now }.takeLast(4)
            if (recent.isEmpty()) Text("В доме тихо. Нажми на комнату — пойдёшь туда, на девчонку — подойдёшь.", color = TextDim, fontSize = 12.sp)
            recent.forEach { l ->
                Row {
                    Text(houseName(l.who) + ": ", color = houseColor(l.who), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(l.text, color = TextMain, fontSize = 12.sp, maxLines = 2)
                }
            }
            if (error.isNotBlank()) Text(error, color = ErrorRed, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val send = { val t = say.trim(); if (t.isNotEmpty()) { act("say", text = t); say = "" } }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, Violet.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (say.isEmpty()) Text(if (myRoom.isBlank() || myRoom == "hall") "Сказать…" else "Сказать тем, кто рядом…", color = TextDim, fontSize = 13.sp)
                    BasicTextField(say, { say = it.take(300) }, singleLine = true, textStyle = TextStyle(color = TextMain, fontSize = 13.sp),
                        cursorBrush = SolidColor(Violet), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "…" else "›", color = Violet, fontSize = 20.sp, modifier = Modifier.clickable(enabled = !busy) { send() }.padding(6.dp))
            }
        }
    }
}

private fun roomAt(s: HouseState, x: Float, y: Float): String =
    s.rooms.firstOrNull { it.id != "hall" && x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }?.id
        ?: if (s.rooms.any { it.id == "hall" && x >= it.x && x < it.x + it.w }) "hall" else ""

/** Чертёж: сетка, стены с проёмами дверей, подписи комнат, мебель контурами. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlan(s: HouseState, cell: Float, myRoom: String) {
    val grid = Glow.copy(alpha = 0.06f)
    for (i in 0..s.w) drawLine(grid, Offset(i * cell, 0f), Offset(i * cell, s.h * cell), 1f)
    for (j in 0..s.h) drawLine(grid, Offset(0f, j * cell), Offset(s.w * cell, j * cell), 1f)
    val wall = Glow.copy(alpha = 0.75f)
    val label = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(150, (TextDim.red * 255).toInt(), (TextDim.green * 255).toInt(), (TextDim.blue * 255).toInt())
        textSize = cell * 0.42f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE
    }
    s.rooms.filter { it.id != "hall" }.forEach { r ->
        if (r.id == myRoom) drawRect(Violet.copy(alpha = 0.07f), Offset(r.x * cell, r.y * cell), Size(r.w * cell, r.h * cell))
        drawRect(wall, Offset(r.x * cell, r.y * cell), Size(r.w * cell, r.h * cell), style = Stroke(2f))
        // проём двери: стена у коридора, на высоте двери
        val wx = if (r.doorX < r.x + r.w / 2) r.x else r.x + r.w
        drawLine(Night, Offset(wx * cell, (r.doorY - 0.6f) * cell), Offset(wx * cell, (r.doorY + 0.6f) * cell), 4f)
        drawContext.canvas.nativeCanvas.drawText(r.name.uppercase(), (r.x + 0.25f) * cell, (r.y + 0.6f) * cell, label)
    }
    val furnPaint = android.graphics.Paint(label).apply { textSize = cell * 0.32f; textAlign = android.graphics.Paint.Align.CENTER }
    s.furniture.forEach { f ->
        val tl = Offset(f.x * cell + 2, f.y * cell + 2)
        val sz = Size(f.w * cell - 4, f.h * cell - 4)
        val c = Violet.copy(alpha = 0.55f)
        when (f.kind) {
            "window" -> drawRect(Glow.copy(alpha = 0.5f), tl, sz)
            "rug" -> drawRoundRect(c.copy(alpha = 0.3f), tl, sz, CornerRadius(cell * 0.4f), style = Stroke(1.5f))
            "plant" -> drawCircle(Glow.copy(alpha = 0.4f), radius = cell * 0.35f, center = Offset((f.x + f.w / 2) * cell, (f.y + f.h / 2) * cell), style = Stroke(1.5f))
            "bed" -> {
                drawRoundRect(c, tl, sz, CornerRadius(cell * 0.15f), style = Stroke(1.5f))
                drawRoundRect(c, tl + Offset(cell * 0.2f, cell * 0.2f), Size(sz.width - cell * 0.4f, cell * 0.5f), CornerRadius(cell * 0.1f), style = Stroke(1.2f))
            }
            "table" -> {
                drawRoundRect(c, tl, sz, CornerRadius(cell * 0.2f), style = Stroke(1.5f))
                for (k in 0 until f.w.toInt()) {
                    drawCircle(c, cell * 0.12f, Offset((f.x + k + 0.5f) * cell, f.y * cell - cell * 0.15f), style = Stroke(1.2f))
                    drawCircle(c, cell * 0.12f, Offset((f.x + k + 0.5f) * cell, (f.y + f.h) * cell + cell * 0.15f), style = Stroke(1.2f))
                }
            }
            "tv" -> drawRect(c, tl, Size(sz.width, cell * 0.25f))
            else -> drawRoundRect(c, tl, sz, CornerRadius(cell * 0.12f), style = Stroke(1.5f))
        }
        if (f.kind !in setOf("window", "plant", "tv") && f.w * f.h >= 2f)
            drawContext.canvas.nativeCanvas.drawText(f.label, (f.x + f.w / 2) * cell, (f.y + f.h / 2) * cell + cell * 0.12f, furnPaint)
    }
}
