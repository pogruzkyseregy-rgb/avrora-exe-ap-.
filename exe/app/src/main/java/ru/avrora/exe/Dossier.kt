package ru.avrora.exe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Досье: профиль как приборная панель. Наверху живое ядро, ниже ровная сетка блоков:
// у каждого номер, название, маленькая схема и одна цифра. Текст открывается только по нажатию.
// Правила «дорогого» вида: тонкие полупрозрачные линии, один акцент на профиль, моноширинные подписи
// с разрядкой, цифры одной ширины, медленное движение и короткий отклик на нажатие.

private val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** Характер ядра: у Авроры собранное и ровное, у Гесперы — живее, с частицами и неровными орбитами. */
data class CoreStyle(
    val accent: Color,
    val second: Color,
    val orbits: Int,
    val wobble: Float,      // насколько «дышат» орбиты
    val speed: Float,       // общая скорость
    val particles: Int,
    val facets: Int         // грани внутренней решётки
)

fun auroraCore() = CoreStyle(Glow, Violet, orbits = 3, wobble = 0.08f, speed = 1f, particles = 14, facets = 6)
fun hesperaCore() = CoreStyle(HespColor, Glow, orbits = 4, wobble = 0.35f, speed = 1.35f, particles = 36, facets = 5)
/** У Эст ядро как клинок: ровное, медленное, мало частиц, острые грани. */
fun estCore() = CoreStyle(EstColor, Violet, orbits = 2, wobble = 0.02f, speed = 0.7f, particles = 8, facets = 4)

/** Живое ядро: шкала, кольца, орбиты со спутниками, луч развёртки и ядро в центре. */
@Composable
fun CoreReactor(style: CoreStyle, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "core")
    val slow by t.animateFloat(0f, 360f, infiniteRepeatable(tween((60000 / style.speed).toInt(), easing = LinearEasing)), label = "slow")
    val mid by t.animateFloat(360f, 0f, infiniteRepeatable(tween((26000 / style.speed).toInt(), easing = LinearEasing)), label = "mid")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween((7000 / style.speed).toInt(), easing = LinearEasing)), label = "sweep")
    val orbit by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween((11000 / style.speed).toInt(), easing = LinearEasing)), label = "orbit")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val light = AppTheme.current.light
    Canvas(modifier) { drawCore(style, slow, mid, sweep, orbit, breathe, light) }
}

private fun DrawScope.drawCore(s: CoreStyle, slow: Float, mid: Float, sweep: Float, orbit: Float, breathe: Float, light: Boolean) {
    val c = center
    val r = min(size.width, size.height) / 2f
    val a = s.accent
    val hair = 1.dp.toPx()

    // ореол: мягкий, без «неоновой каши»
    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = if (light) 0.10f else 0.20f), Color.Transparent), c, r * 0.95f), r * 0.95f, c)

    // внешняя шкала: 120 рисок, каждая десятая длиннее
    rotate(slow * 0.5f, c) {
        for (i in 0 until 120) {
            val long = i % 10 == 0
            val ang = (i * 3f) * (PI / 180f).toFloat()
            val r1 = r * 0.97f
            val r0 = r1 - if (long) r * 0.07f else r * 0.03f
            drawLine(
                a.copy(alpha = if (long) 0.75f else 0.28f),
                Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0),
                Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1),
                strokeWidth = if (long) hair * 1.2f else hair * 0.7f
            )
        }
    }
    // визирные метки по сторонам света — неподвижные
    for (k in 0 until 4) {
        rotate(k * 90f, c) {
            drawLine(a.copy(alpha = 0.9f), Offset(c.x, c.y - r), Offset(c.x, c.y - r * 0.9f), strokeWidth = hair * 1.6f, cap = StrokeCap.Square)
        }
    }

    // сегментное кольцо, вращается навстречу
    val rs = r * 0.82f
    val segTopLeft = Offset(c.x - rs, c.y - rs)
    val segSize = Size(rs * 2, rs * 2)
    rotate(mid, c) {
        for (k in 0 until 3) {
            drawArc(a.copy(alpha = 0.45f), k * 120f + 8f, 92f, false, segTopLeft, segSize, style = Stroke(hair * 1.3f))
        }
        drawArc(s.second.copy(alpha = 0.95f), 300f, 16f, false, segTopLeft, segSize, style = Stroke(hair * 3f, cap = StrokeCap.Butt))
    }
    // тонкое внутреннее кольцо с пунктиром
    val rd = r * 0.74f
    rotate(-slow, c) {
        for (k in 0 until 48) if (k % 4 != 0) {
            drawArc(a.copy(alpha = 0.22f), k * 7.5f, 4f, false, Offset(c.x - rd, c.y - rd), Size(rd * 2, rd * 2), style = Stroke(hair))
        }
    }

    // луч развёртки
    rotate(sweep, c) {
        drawArc(
            Brush.sweepGradient(0f to Color.Transparent, 0.86f to Color.Transparent, 1f to a.copy(alpha = if (light) 0.18f else 0.26f), center = c),
            310f, 50f, true, Offset(c.x - rd, c.y - rd), Size(rd * 2, rd * 2)
        )
    }

    // орбиты со спутниками: дальняя половина пути тусклее, так видно объём
    val ra = r * 0.6f
    for (k in 0 until s.orbits) {
        val tilt = k * (180f / s.orbits) + slow * 0.15f * (if (k % 2 == 0) 1 else -1)
        val rb = ra * (0.26f + 0.1f * k) * (1f + s.wobble * sin(orbit * 2 + k))
        rotate(tilt, c) {
            drawOval(a.copy(alpha = 0.3f), Offset(c.x - ra, c.y - rb), Size(ra * 2, rb * 2), style = Stroke(hair * 0.8f))
            val th = orbit * (1f + 0.35f * k) + k * 1.7f
            val p = Offset(c.x + cos(th) * ra, c.y + sin(th) * rb)
            val front = sin(th) > 0
            if (!light) drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.5f), Color.Transparent), p, hair * 7), hair * 7, p)
            drawCircle(if (front) lerp(a, Color.White, if (light) 0f else 0.5f) else a.copy(alpha = 0.45f), hair * (if (front) 2.6f else 1.8f), p)
        }
    }

    // частицы вокруг ядра
    for (i in 0 until s.particles) {
        val ang = i * 2.39996f + orbit * 0.25f * ((i % 3) + 1) * (if (i % 2 == 0) 1 else -1)
        val rr = r * (0.3f + 0.4f * (((i * 37) % 100) / 100f))
        val tw = 0.25f + 0.75f * abs(sin(orbit * 2f + i))
        drawCircle(a.copy(alpha = 0.55f * tw), hair * (0.6f + (i % 3) * 0.35f), Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr))
    }

    // ядро: свет, грани и чистое кольцо
    val r0 = r * 0.2f * (1f + 0.06f * breathe)
    val hot = if (light) a else lerp(a, Color.White, 0.7f)
    drawCircle(Brush.radialGradient(listOf(hot, a.copy(alpha = 0.85f), a.copy(alpha = 0f)), c, r0 * 1.9f), r0 * 1.9f, c)
    rotate(slow * 3f, c) {
        val poly = Path()
        for (i in 0..s.facets) {
            val ang = (i * 2 * PI / s.facets).toFloat()
            val x = c.x + cos(ang) * r0 * 1.25f
            val y = c.y + sin(ang) * r0 * 1.25f
            if (i == 0) poly.moveTo(x, y) else poly.lineTo(x, y)
        }
        drawPath(poly, s.second.copy(alpha = 0.7f), style = Stroke(hair))
        for (i in 0 until s.facets) {
            val ang = (i * 2 * PI / s.facets).toFloat()
            drawLine(s.second.copy(alpha = 0.25f), c, Offset(c.x + cos(ang) * r0 * 1.25f, c.y + sin(ang) * r0 * 1.25f), strokeWidth = hair * 0.7f)
        }
    }
    drawCircle(a.copy(alpha = 0.9f), r0 * 1.55f, c, style = Stroke(hair))
}

/** Верх профиля: ядро и четыре показания по углам, под ним имя с разрядкой и строка-подпись. */
@Composable
fun CoreHero(style: CoreStyle, name: String, line: String, readouts: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(268.dp)) {
            CoreReactor(style, Modifier.size(256.dp).align(Alignment.Center))
            val spots = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
            readouts.take(4).forEachIndexed { i, (label, value) ->
                val end = i % 2 == 1
                Column(Modifier.align(spots[i]), horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
                    Text(label, color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.6.sp)
                    Text(value, color = TextMain, fontSize = 16.sp, fontFamily = FontFamily.Monospace, style = Tnum)
                    Box(Modifier.padding(top = 3.dp).width(26.dp).height(1.dp).background(style.accent.copy(alpha = 0.6f)))
                }
            }
        }
        Text(name, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = 9.sp, fontFamily = FontFamily.Monospace)
        if (line.isNotBlank()) {
            Text(line, color = style.accent, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp))
        }
    }
}

// ---------- блоки ----------

class DossierItem(
    val key: String,
    val title: String,
    val metric: String,
    val accent: Color,
    val preview: @Composable () -> Unit,
    val detail: @Composable ColumnScope.() -> Unit
)

/** Сетка по два блока в ряд. Открытый блок разворачивается под своим рядом, второй нажатый закрывает первый. */
@Composable
fun DossierGrid(items: List<DossierItem>) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { i, item ->
                    DossierTile(item, rowIndex * 2 + i + 1, open == item.key, Modifier.weight(1f).fillMaxHeight()) {
                        open = if (open == item.key) null else item.key
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            val inRow = row.firstOrNull { it.key == open }?.key
            AnimatedContent(
                targetState = inRow,
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(140)))
                        .using(SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow) })
                },
                label = "dossier-$rowIndex"
            ) { key ->
                val item = row.firstOrNull { it.key == key }
                if (item == null) Spacer(Modifier.fillMaxWidth().height(0.dp)) else DossierDetail(item)
            }
        }
    }
}

/** Тонкая рамка с уголками в стиле темы и бликом по верхней кромке. */
private fun Modifier.hairline(accent: Color, on: Boolean): Modifier = drawBehind {
    val light = AppTheme.current.light
    val w = size.width
    val h = size.height
    val px = 1.dp.toPx()
    drawRect(accent.copy(alpha = if (on) 0.6f else if (light) 0.28f else 0.2f), Offset.Zero, size, style = Stroke(px))
    drawLine(Color.White.copy(alpha = if (light) 0.6f else 0.07f), Offset(px, px * 1.5f), Offset(w - px, px * 1.5f), strokeWidth = px)
    val l = 9.dp.toPx()
    val st = px * if (on) 2f else 1.4f
    drawLine(accent, Offset(0f, 0f), Offset(l, 0f), st); drawLine(accent, Offset(0f, 0f), Offset(0f, l), st)
    drawLine(accent, Offset(w, h), Offset(w - l, h), st); drawLine(accent, Offset(w, h), Offset(w, h - l), st)
}

@Composable
private fun DossierTile(item: DossierItem, n: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "press")
    val turn by animateFloatAsState(if (on) 45f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "plus")
    val bg = if (AppTheme.current.light) Panel else lerp(Panel, Night, 0.25f)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Brush.verticalGradient(listOf(lerp(bg, item.accent, if (on) 0.08f else 0.03f), bg)))
            .hairline(item.accent, on)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(n), color = item.accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace, style = Tnum)
            Spacer(Modifier.width(6.dp))
            Text(item.title, color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.4.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(10.dp).graphicsLayer { rotationZ = turn }) {
                val m = size.width / 2
                drawLine(item.accent, Offset(m, 0f), Offset(m, size.height), 1.2.dp.toPx())
                drawLine(item.accent, Offset(0f, m), Offset(size.width, m), 1.2.dp.toPx())
            }
        }
        Box(Modifier.fillMaxWidth().height(46.dp).padding(vertical = 6.dp), contentAlignment = Alignment.CenterStart) { item.preview() }
        Text(item.metric, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Tnum)
    }
}

@Composable
private fun DossierDetail(item: DossierItem) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (AppTheme.current.light) Panel else Terminal)
            .drawBehind {
                drawRect(item.accent, Offset.Zero, Size(2.dp.toPx(), size.height))
                drawRect(item.accent.copy(alpha = 0.18f), Offset.Zero, size, style = Stroke(1.dp.toPx()))
            }
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("▸ " + item.title, color = item.accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.6.sp)
        item.detail(this)
    }
}

/** Строка подробностей: подпись мелко, значение обычным текстом, причина — курсивом. */
@Composable
fun DetailLine(label: String, value: String, note: String = "") {
    Column(Modifier.fillMaxWidth()) {
        if (label.isNotBlank()) Text(label.uppercase(), color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp)
        Text(value, color = TextMain, fontSize = 14.sp, lineHeight = 20.sp)
        if (note.isNotBlank()) Text(note, color = TextDim, fontSize = 12.sp, lineHeight = 17.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

@Composable
fun DetailHint(text: String) = Text(text, color = TextDim, fontSize = 12.sp, lineHeight = 17.sp)

/** Числа в ряд: крупная цифра и подпись под ней. */
@Composable
fun StatRow(stats: List<Pair<String, String>>, accent: Color) {
    Row(Modifier.fillMaxWidth()) {
        stats.forEachIndexed { i, (label, value) ->
            if (i > 0) Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(34.dp).background(accent.copy(alpha = 0.25f)))
            Column(Modifier.weight(1f)) {
                Text(value, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace, style = Tnum, maxLines = 1)
                Text(label, color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp, maxLines = 1)
            }
        }
    }
}

// ---------- схемы и шкалы ----------

/** Тонкие столбики: доли по категориям. */
@Composable
fun MiniBars(values: List<Float>, accent: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        val n = max(1, values.size)
        val top = max(1f, values.maxOrNull() ?: 1f)
        val gap = 7.dp.toPx()
        val bw = min(14.dp.toPx(), (size.width - gap * (n - 1)) / n)
        drawLine(accent.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = max(1.5.dp.toPx(), (v / top) * (size.height - 2.dp.toPx()))
            val x = i * (bw + gap)
            // тонкая вертикальная направляющая вместо тяжёлой подложки
            drawLine(accent.copy(alpha = 0.14f), Offset(x + bw / 2, 0f), Offset(x + bw / 2, size.height), 1.dp.toPx())
            drawRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.45f)), size.height - h, size.height), Offset(x, size.height - h), Size(bw, h))
        }
    }
}

/** Линия по дням с мягкой заливкой и точкой «сегодня». */
@Composable
fun Sparkline(values: List<Float>, accent: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val top = max(1f, values.max())
        val step = size.width / (values.size - 1)
        val pad = 3.dp.toPx()
        fun y(v: Float) = size.height - pad - (v / top) * (size.height - pad * 2)
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * step, y(v)) }
        val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, accent, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
        for (i in values.indices step 7) drawLine(accent.copy(alpha = 0.18f), Offset(i * step, 0f), Offset(i * step, size.height), 1.dp.toPx())
        val last = Offset(size.width, y(values.last()))
        drawCircle(accent.copy(alpha = 0.25f), 5.dp.toPx(), last)
        drawCircle(accent, 2.4.dp.toPx(), last)
    }
}

/** Кольцевая шкала на 270° с рисками. */
@Composable
fun RingGauge(fraction: Float, accent: Color, center: String = "", size: Dp = 40.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = 2.dp.toPx()
            val inset = st * 2
            val tl = Offset(inset, inset)
            val sz = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(accent.copy(alpha = 0.18f), 135f, 270f, false, tl, sz, style = Stroke(st))
            drawArc(accent, 135f, 270f * fraction.coerceIn(0f, 1f), false, tl, sz, style = Stroke(st, cap = StrokeCap.Butt))
            val c = this.center
            val r = this.size.minDimension / 2
            for (i in 0..9) {
                val ang = ((135f + i * 30f) * PI / 180f).toFloat()
                drawLine(accent.copy(alpha = 0.5f), Offset(c.x + cos(ang) * r * 0.98f, c.y + sin(ang) * r * 0.98f),
                    Offset(c.x + cos(ang) * r * 0.86f, c.y + sin(ang) * r * 0.86f), 0.8.dp.toPx())
            }
        }
        if (center.isNotEmpty()) Text(center, color = TextMain, fontSize = 9.sp, fontFamily = FontFamily.Monospace, style = Tnum)
    }
}

/** Сегментная шкала: N из total. */
@Composable
fun SegmentMeter(filled: Int, total: Int, accent: Color, modifier: Modifier = Modifier.fillMaxWidth().height(10.dp)) {
    Canvas(modifier) {
        val n = max(1, total)
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val on = i < filled
            drawRect(if (on) accent else accent.copy(alpha = 0.16f), Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}

/** Точки-индикаторы: что включено, что нет. */
@Composable
fun DotRow(states: List<Boolean>, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        states.forEach { on ->
            Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (on) accent else Color.Transparent)
                .border(1.dp, accent.copy(alpha = if (on) 1f else 0.4f), androidx.compose.foundation.shape.CircleShape))
        }
    }
}

/** Лепестковая схема: значения 0..1 по осям. С подписями — в развёрнутом блоке, без — в превью. */
@Composable
fun Radar(values: List<Float>, labels: List<String>, accent: Color, modifier: Modifier, withLabels: Boolean) {
    val textColor = TextDim
    Canvas(modifier) {
        val n = values.size
        if (n < 3) return@Canvas
        val c = center
        val r = size.minDimension / 2 * if (withLabels) 0.7f else 0.95f
        fun pt(i: Int, k: Float): Offset {
            val ang = (-PI / 2 + i * 2 * PI / n).toFloat()
            return Offset(c.x + cos(ang) * r * k, c.y + sin(ang) * r * k)
        }
        for (ring in 1..4) {
            val p = Path()
            for (i in 0..n) { val q = pt(i % n, ring / 4f); if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }
            drawPath(p, accent.copy(alpha = 0.16f), style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(accent.copy(alpha = 0.16f), c, pt(i, 1f), 1.dp.toPx())
        val shape = Path()
        for (i in 0..n) { val q = pt(i % n, values[i % n].coerceIn(0.06f, 1f)); if (i == 0) shape.moveTo(q.x, q.y) else shape.lineTo(q.x, q.y) }
        drawPath(shape, accent.copy(alpha = 0.22f))
        drawPath(shape, accent, style = Stroke(1.5.dp.toPx()))
        for (i in 0 until n) drawCircle(accent, 2.2.dp.toPx(), pt(i, values[i].coerceIn(0.06f, 1f)))
        if (withLabels) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true; color = textColor.toArgb(); textSize = 10.sp.toPx(); typeface = android.graphics.Typeface.MONOSPACE
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (i in 0 until n) {
                val q = pt(i, 1.22f)
                drawContext.canvas.nativeCanvas.drawText(labels.getOrElse(i) { "" }.uppercase(), q.x, q.y + paint.textSize / 3, paint)
            }
        }
    }
}

/** Сколько событий пришлось на каждый из последних days дней (последний — сегодня). */
fun perDay(times: List<Long>, days: Int = 14, now: Long = System.currentTimeMillis()): List<Float> {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = now; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val today = cal.timeInMillis
    val out = FloatArray(days)
    times.forEach { t ->
        val d = if (t >= today) 0 else ((today - t) / 86_400_000L + 1).toInt()
        if (d in 0 until days) out[days - 1 - d] += 1f
    }
    return out.toList()
}

/** «через 1 ч 20 мин», «через 5 мин». */
fun inTime(ms: Long): String {
    if (ms <= 0) return "скоро"
    val m = ms / 60_000
    return if (m >= 60) "через ${m / 60} ч ${m % 60} мин" else "через $m мин"
}

/** Растягивает содержимое на ширину блока, оставляя квадрат для круглых схем. */
@Composable
fun PreviewBox(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { content() }
}
