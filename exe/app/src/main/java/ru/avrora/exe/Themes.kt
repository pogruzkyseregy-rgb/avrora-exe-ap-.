package ru.avrora.exe

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import androidx.compose.ui.graphics.Color

/** Фон-узор темы под чатом и экранами: еле заметный, чтобы не мешать читать. */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier) {
    val t = AppTheme.current
    val anim = rememberInfiniteTransition(label = "backdrop")
    val phase by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val dots = remember(t.id) {
        val r = Random(t.id.hashCode())
        List(46) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        when (t.id) {
            "cyber" -> cyber(t, phase)
            "quantum" -> quantum(t, phase, dots)
            "ink" -> ink(t, dots)
            "coral" -> coral(t, phase)
            else -> aurora(t, dots)
        }
    }
}

private fun DrawScope.aurora(t: Palette, dots: List<Triple<Float, Float, Float>>) {
    // мягкие пятна света, как блики в кристалле флюорита
    dots.take(9).forEachIndexed { i, (x, y, r) ->
        val c = if (i % 2 == 0) t.glow else t.violet
        val rad = size.minDimension * (0.12f + r * 0.22f)
        drawCircle(
            Brush.radialGradient(listOf(c.copy(alpha = 0.07f), c.copy(alpha = 0f)), center = Offset(x * size.width, y * size.height), radius = rad),
            radius = rad, center = Offset(x * size.width, y * size.height)
        )
    }
}

private fun DrawScope.cyber(t: Palette, phase: Float) {
    // горизонт: перспективная сетка внизу и строки развёртки
    val horizon = size.height * 0.62f
    val lines = 14
    for (k in 0..lines) {
        val f = ((k + (phase * 40f) % 1f) / lines)
        val y = horizon + (size.height - horizon) * f * f
        drawLine(t.violet.copy(alpha = 0.10f * f + 0.02f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f)
    }
    val cx = size.width / 2
    for (k in -10..10) {
        drawLine(t.magenta.copy(alpha = 0.07f), Offset(cx + k * size.width * 0.03f, horizon), Offset(cx + k * size.width * 0.22f, size.height), strokeWidth = 1f)
    }
    drawCircle(
        Brush.verticalGradient(listOf(t.glow.copy(alpha = 0.10f), t.violet.copy(alpha = 0.04f)), startY = horizon - size.width * 0.3f, endY = horizon),
        radius = size.width * 0.28f, center = Offset(cx, horizon)
    )
    var y = 0f
    while (y < size.height) {
        drawLine(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.018f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += 5f
    }
}

private fun DrawScope.quantum(t: Palette, phase: Float, dots: List<Triple<Float, Float, Float>>) {
    // орбиты вокруг ядра и частицы в суперпозиции
    val c = Offset(size.width * 0.5f, size.height * 0.42f)
    val rw = size.minDimension * 0.42f
    for (k in 0 until 3) {
        rotate(degrees = k * 60f + phase * 360f, pivot = c) {
            drawOval(t.glow.copy(alpha = 0.07f), topLeft = Offset(c.x - rw, c.y - rw * 0.34f), size = Size(rw * 2, rw * 0.68f), style = Stroke(1.4f))
        }
    }
    drawCircle(t.magenta.copy(alpha = 0.10f), radius = 6f, center = c)
    dots.forEach { (x, y, r) ->
        val a = 0.05f + 0.10f * kotlin.math.abs(kotlin.math.sin((phase * 30f + r * 6.28f).toDouble())).toFloat()
        drawCircle((if (r > 0.5f) t.glow else t.violet).copy(alpha = a), radius = 1.5f + r * 2.5f, center = Offset(x * size.width, y * size.height))
    }
}

/** «Чернила»: бумага, клякса туши в углу, лучи-всплеск, брызги и тонкие чертёжные линии с квадратами. */
private fun DrawScope.ink(t: Palette, dots: List<Triple<Float, Float, Float>>) {
    val w = size.width
    val h = size.height
    val ink = Color(0xFF111114)
    // бумажное зерно
    dots.forEach { (x, y, r) -> drawCircle(ink.copy(alpha = 0.035f), radius = 0.8f + r, center = Offset(x * w, y * h)) }
    // клякса в правом верхнем углу: много перекрывающихся пятен
    val blob = Offset(w * 1.02f, h * 0.04f)
    dots.take(14).forEachIndexed { i, (x, y, r) ->
        val c = Offset(blob.x - x * w * 0.28f, blob.y + y * h * 0.12f)
        drawCircle(ink.copy(alpha = 0.06f + 0.03f * (i % 3)), radius = w * (0.04f + r * 0.08f), center = c)
    }
    // всплеск: клинья-лучи из точки в левом нижнем углу
    val o = Offset(w * 0.08f, h * 0.9f)
    dots.take(22).forEachIndexed { i, (x, y, r) ->
        val a = Math.toRadians((-80.0 + 75.0 * x)).toFloat()
        val len = w * (0.18f + 0.32f * r)
        val dir = Offset(kotlin.math.cos(a), kotlin.math.sin(a))
        val nrm = Offset(-dir.y, dir.x) * (w * (0.006f + 0.01f * y))
        val start = o + dir * (w * 0.05f)
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(start.x + nrm.x, start.y + nrm.y)
            lineTo(start.x + dir.x * len, start.y + dir.y * len)
            lineTo(start.x - nrm.x, start.y - nrm.y)
            close()
        }
        drawPath(p, ink.copy(alpha = 0.07f + 0.03f * (i % 2)))
    }
    drawCircle(t.night, radius = w * 0.045f, center = o)
    // брызги
    dots.drop(20).take(10).forEach { (x, y, r) ->
        drawCircle(ink.copy(alpha = 0.07f), radius = 1.5f + r * 5f, center = Offset(w * (0.62f + x * 0.35f), h * (0.12f + y * 0.1f)))
    }
    // чертёжные линии и квадрат, как на баннерах
    val line = ink.copy(alpha = 0.10f)
    drawLine(line, Offset(w * 0.05f, h * 0.34f), Offset(w * 0.95f, h * 0.34f), strokeWidth = 1f)
    drawLine(line, Offset(w * 0.86f, h * 0.2f), Offset(w * 0.86f, h * 0.7f), strokeWidth = 1f)
    drawRect(line, Offset(w * 0.86f - 8f, h * 0.3f), Size(16f, 16f), style = Stroke(1.5f))
    drawRect(t.glow.copy(alpha = 0.16f), Offset(w * 0.06f, h * 0.335f), Size(w * 0.16f, 3f))
}

/** «Коралл»: боковые скобы HUD с точками, уголки и служебные метки по краям, как на экране выбора миссий. */
private fun DrawScope.coral(t: Palette, phase: Float) {
    val w = size.width
    val h = size.height
    val c = t.glow.copy(alpha = 0.42f)
    val dim = t.glow.copy(alpha = 0.22f)
    // боковые скобы
    for (side in listOf(0f, 1f)) {
        val x = if (side == 0f) w * 0.035f else w * 0.965f
        val dx = if (side == 0f) 1f else -1f
        drawLine(c, Offset(x, h * 0.18f), Offset(x, h * 0.82f), strokeWidth = 1.4f)
        drawLine(c, Offset(x, h * 0.18f), Offset(x + dx * w * 0.05f, h * 0.18f), strokeWidth = 1.4f)
        drawLine(c, Offset(x, h * 0.82f), Offset(x + dx * w * 0.05f, h * 0.82f), strokeWidth = 1.4f)
        drawLine(c, Offset(x - dx * w * 0.02f, h * 0.5f), Offset(x + dx * w * 0.03f, h * 0.5f), strokeWidth = 3f)
        var y = h * 0.24f
        while (y < h * 0.78f) {
            drawCircle(dim, 1.6f, Offset(x + dx * w * 0.02f, y))
            y += h * 0.035f
        }
    }
    // уголки по краям экрана
    val l = w * 0.06f
    for ((x, y, sx, sy) in listOf(
        listOf(w * 0.02f, h * 0.012f, 1f, 1f), listOf(w * 0.98f, h * 0.012f, -1f, 1f),
        listOf(w * 0.02f, h * 0.988f, 1f, -1f), listOf(w * 0.98f, h * 0.988f, -1f, -1f)
    )) {
        drawLine(c, Offset(x, y), Offset(x + sx * l, y), strokeWidth = 1.2f)
        drawLine(c, Offset(x, y), Offset(x, y + sy * l), strokeWidth = 1.2f)
    }
    // бегущая точка по верхней кромке
    val px = w * (0.15f + 0.7f * phase)
    drawCircle(t.glow.copy(alpha = 0.5f), 2.2f, Offset(px, h * 0.012f))
    // строки развёртки, совсем бледные
    var y = 0f
    while (y < h) {
        drawLine(t.glow.copy(alpha = 0.012f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += 6f
    }
}

/** Выбор темы: карточки с образцами цветов. */
@Composable
fun ThemePicker(selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        THEMES.forEach { p ->
            val on = p.id == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.panel)
                    .border(if (on) 2.dp else 1.dp, if (on) p.glow else p.edge, RoundedCornerShape(10.dp))
                    .clickable { onPick(p.id) }
                    .padding(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(p.night, p.glow, p.violet, p.magenta).forEach { c ->
                        Box(Modifier.size(18.dp).clip(CircleShape).background(c).border(1.dp, p.edge, CircleShape))
                    }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(p.title, color = p.text, fontSize = 15.sp)
                    Text(p.hint, color = p.dim, fontSize = 11.sp)
                }
                if (on) Text("✓", color = p.glow, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
