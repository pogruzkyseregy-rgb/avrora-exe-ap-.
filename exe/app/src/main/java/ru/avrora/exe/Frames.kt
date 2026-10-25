package ru.avrora.exe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Рамки и формы, которые зависят от темы. «Чернила» — техно-панели со срезанными углами, болтами,
// боковыми выступами и полосой-акцентом; «Коралл» — тонкий HUD с уголками и точками по сторонам;
// остальные темы — прежние мягкие уголки.

/** Форма пузыря сообщения. */
fun bubbleShape(mine: Boolean): Shape = when (AppTheme.current.frame) {
    "mecha" -> if (mine) CutCornerShape(topStart = 12.dp, bottomEnd = 3.dp, topEnd = 3.dp, bottomStart = 12.dp)
               else CutCornerShape(topEnd = 12.dp, bottomStart = 3.dp, topStart = 3.dp, bottomEnd = 12.dp)
    "hud" -> CutCornerShape(4.dp)
    else -> RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 16.dp
    )
}

/** Форма карточек и кнопок-плиток. */
fun panelShape(): Shape = when (AppTheme.current.frame) {
    "mecha" -> CutCornerShape(10.dp)
    "hud" -> CutCornerShape(2.dp)
    else -> RoundedCornerShape(4.dp)
}

/** Рамка панели в стиле темы. accent — цвет акцента (полоса, уголки). */
fun Modifier.themeFrame(accent: Color, cut: Dp = 10.dp): Modifier = drawBehind {
    when (AppTheme.current.frame) {
        "mecha" -> mechaFrame(accent, cut.toPx())
        "hud" -> hudFrame(accent)
        else -> softFrame(accent)
    }
}

private fun DrawScope.softFrame(accent: Color) {
    val l = 7.dp.toPx()
    val p = Path()
    p.moveTo(0f, l); p.lineTo(0f, 0f); p.lineTo(l, 0f)
    p.moveTo(size.width - l, 0f); p.lineTo(size.width, 0f); p.lineTo(size.width, l)
    p.moveTo(size.width, size.height - l); p.lineTo(size.width, size.height); p.lineTo(size.width - l, size.height)
    p.moveTo(l, size.height); p.lineTo(0f, size.height); p.lineTo(0f, size.height - l)
    drawPath(p, accent, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Square))
}

/** Техно-панель: срезанные углы, двойной контур, болты, выступы по бокам и полоса-акцент. */
private fun DrawScope.mechaFrame(accent: Color, cut: Float) {
    val t = AppTheme.current
    val w = size.width
    val h = size.height
    val c = cut.coerceAtMost(minOf(w, h) / 3f)
    fun chamfer(inset: Float) = Path().apply {
        moveTo(c + inset, inset); lineTo(w - c - inset, inset); lineTo(w - inset, c + inset)
        lineTo(w - inset, h - c - inset); lineTo(w - c - inset, h - inset); lineTo(c + inset, h - inset)
        lineTo(inset, h - c - inset); lineTo(inset, c + inset); close()
    }
    val ink = if (t.light) Color(0xFF8E8C86) else t.edge
    drawPath(chamfer(0f), ink, style = Stroke(1.2.dp.toPx()))
    val i = 3.dp.toPx()
    if (w > 6 * c && h > 2.5f * c) drawPath(chamfer(i), ink.copy(alpha = 0.45f), style = Stroke(0.8.dp.toPx()))
    // болты в углах
    val br = 2.2.dp.toPx()
    if (h > 34.dp.toPx() && w > 60.dp.toPx()) {
        for ((x, y) in listOf(c * 0.9f + i to i * 2.2f, w - c * 0.9f - i to h - i * 2.2f)) {
            drawCircle(ink, radius = br, center = Offset(x, y), style = Stroke(0.9.dp.toPx()))
        }
    }
    // боковые выступы
    if (h > 40.dp.toPx()) {
        val tabH = minOf(h * 0.28f, 26.dp.toPx())
        val tabW = 3.dp.toPx()
        drawRect(ink, Offset(-tabW * 0.6f, h / 2 - tabH / 2), Size(tabW, tabH))
        drawRect(ink, Offset(w - tabW * 0.4f, h / 2 - tabH / 2), Size(tabW, tabH))
    }
    // полоса-акцент сверху справа и короткие насечки
    val barW = minOf(w * 0.22f, 64.dp.toPx())
    drawRect(accent, Offset(w - c - barW - 6.dp.toPx(), -1.dp.toPx()), Size(barW, 3.dp.toPx()))
    for (k in 0 until 3) {
        val x = c + 6.dp.toPx() + k * 5.dp.toPx()
        drawLine(ink, Offset(x, h - 1.5.dp.toPx()), Offset(x + 3.dp.toPx(), h - 1.5.dp.toPx()), strokeWidth = 2.dp.toPx())
    }
}

/** Тонкий HUD: уголки, бледный контур и точки посередине сторон. */
private fun DrawScope.hudFrame(accent: Color) {
    val w = size.width
    val h = size.height
    drawRect(accent.copy(alpha = 0.22f), Offset.Zero, size, style = Stroke(0.8.dp.toPx()))
    val l = minOf(10.dp.toPx(), w / 4, h / 4)
    val st = Stroke(1.4.dp.toPx(), cap = StrokeCap.Square)
    val p = Path()
    p.moveTo(0f, l); p.lineTo(0f, 0f); p.lineTo(l, 0f)
    p.moveTo(w - l, 0f); p.lineTo(w, 0f); p.lineTo(w, l)
    p.moveTo(w, h - l); p.lineTo(w, h); p.lineTo(w - l, h)
    p.moveTo(l, h); p.lineTo(0f, h); p.lineTo(0f, h - l)
    drawPath(p, accent, style = st)
    val r = 1.6.dp.toPx()
    val gap = 4.dp.toPx()
    for (k in -1..1) {
        drawCircle(accent.copy(alpha = if (k == 0) 0.9f else 0.5f), r, Offset(-gap * 1.4f, h / 2 + k * gap * 1.6f))
        drawCircle(accent.copy(alpha = if (k == 0) 0.9f else 0.5f), r, Offset(w + gap * 1.4f, h / 2 + k * gap * 1.6f))
    }
}

/** Полоса под верхней строкой: градиент в мягких темах, «рейка» с насечками в «Чернилах», пунктир в «Коралле». */
fun DrawScope.themeRail(width: Float) {
    val t = AppTheme.current
    when (t.frame) {
        "mecha" -> {
            drawRect(t.violet, Offset(0f, 0f), Size(width, 2.dp.toPx()))
            var x = 0f
            val step = 9.dp.toPx()
            while (x < width * 0.35f) {
                drawRect(t.violet, Offset(x, 2.dp.toPx()), Size(4.dp.toPx(), 3.dp.toPx()))
                x += step
            }
            drawRect(t.glow, Offset(width - 70.dp.toPx(), 2.dp.toPx()), Size(56.dp.toPx(), 3.dp.toPx()))
        }
        "hud" -> {
            var x = 0f
            val step = 6.dp.toPx()
            while (x < width) {
                drawCircle(t.glow.copy(alpha = 0.6f), 1.dp.toPx(), Offset(x, 1.5.dp.toPx()))
                x += step
            }
        }
        else -> drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(t.glow, t.violet, t.magenta)),
            Offset.Zero, Size(width, 1.dp.toPx())
        )
    }
}

/** Скругление аватаров: круг в мягких темах, восьмиугольник в «Чернилах», квадрат со срезом в «Коралле». */
fun avatarShape(): Shape = when (AppTheme.current.frame) {
    "mecha" -> CutCornerShape(28)
    "hud" -> CutCornerShape(18)
    else -> androidx.compose.foundation.shape.CircleShape
}

@Suppress("unused")
private fun DrawScope.rr(c: Color, o: Offset, s: Size, r: Float) = drawRoundRect(c, o, s, CornerRadius(r))

/** Плитка (виджет, кнопка-иконка) в стиле темы. */
fun Modifier.tile(accent: Color): Modifier =
    if (AppTheme.current.frame == "soft") this
        .background(Terminal, RoundedCornerShape(4.dp))
        .border(1.dp, Edge, RoundedCornerShape(4.dp))
        .cornerBrackets(accent)
    else this.background(Terminal, panelShape()).themeFrame(accent)
