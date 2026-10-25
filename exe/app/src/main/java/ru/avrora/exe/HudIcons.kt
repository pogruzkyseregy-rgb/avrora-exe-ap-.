package ru.avrora.exe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Glyph { Profile, Clock, Memory, Diary, Hand, Settings, Feed, Chat, Gallery, Quote, Dice, Alarm }

/** Уголки рамки в духе HUD: четыре «Г» по углам. */
fun Modifier.cornerBrackets(color: Color, len: Dp = 7.dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val l = len.toPx()
    val w = width.toPx()
    val s = Stroke(width = w, cap = StrokeCap.Square)
    val p = Path()
    p.moveTo(0f, l); p.lineTo(0f, 0f); p.lineTo(l, 0f)
    p.moveTo(size.width - l, 0f); p.lineTo(size.width, 0f); p.lineTo(size.width, l)
    p.moveTo(size.width, size.height - l); p.lineTo(size.width, size.height); p.lineTo(size.width - l, size.height)
    p.moveTo(l, size.height); p.lineTo(0f, size.height); p.lineTo(0f, size.height - l)
    drawPath(p, color, style = s)
}

internal fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val s = size.minDimension
    // «Чернила» — жирная тушь с рублеными концами, «Коралл» — тонкая линия HUD
    val frame = AppTheme.current.frame
    val w = s * when (frame) { "mecha" -> 0.11f; "hud" -> 0.065f; else -> 0.085f }
    val st = Stroke(width = w, cap = if (frame == "mecha") StrokeCap.Square else StrokeCap.Round)
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    when (g) {
        Glyph.Profile -> {
            drawCircle(c, radius = s * 0.17f, center = o(0.5f, 0.32f), style = st)
            drawArc(c, 180f, 180f, false, topLeft = o(0.17f, 0.58f), size = Size(s * 0.66f, s * 0.6f), style = st)
        }
        Glyph.Clock -> {
            drawCircle(c, radius = s * 0.4f, center = o(0.5f, 0.5f), style = st)
            drawLine(c, o(0.5f, 0.5f), o(0.5f, 0.24f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.5f, 0.5f), o(0.7f, 0.6f), strokeWidth = w, cap = StrokeCap.Round)
        }
        Glyph.Memory -> {
            for (k in 0..2) {
                val y = 0.28f + k * 0.2f
                val p = Path()
                p.moveTo(s * 0.5f, s * (y - 0.16f)); p.lineTo(s * 0.86f, s * y); p.lineTo(s * 0.5f, s * (y + 0.16f)); p.lineTo(s * 0.14f, s * y); p.close()
                drawPath(p, c.copy(alpha = if (k == 0) 1f else 0.6f), style = st)
            }
        }
        Glyph.Diary -> {
            drawRoundRect(c, o(0.2f, 0.12f), Size(s * 0.6f, s * 0.76f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = st)
            drawLine(c, o(0.34f, 0.12f), o(0.34f, 0.88f), strokeWidth = w)
            drawLine(c, o(0.47f, 0.34f), o(0.7f, 0.34f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.47f, 0.5f), o(0.7f, 0.5f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.47f, 0.66f), o(0.62f, 0.66f), strokeWidth = w, cap = StrokeCap.Round)
        }
        Glyph.Hand -> {
            val p = Path()
            p.moveTo(s * 0.18f, s * 0.26f); p.lineTo(s * 0.46f, s * 0.5f); p.lineTo(s * 0.18f, s * 0.74f)
            drawPath(p, c, style = st)
            drawLine(c, o(0.54f, 0.78f), o(0.84f, 0.78f), strokeWidth = w, cap = StrokeCap.Round)
        }
        Glyph.Feed -> {
            drawRoundRect(c, o(0.12f, 0.2f), Size(s * 0.2f, s * 0.2f), androidx.compose.ui.geometry.CornerRadius(s * 0.03f), style = st)
            drawLine(c, o(0.42f, 0.24f), o(0.88f, 0.24f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.42f, 0.38f), o(0.74f, 0.38f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.12f, 0.58f), o(0.88f, 0.58f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.12f, 0.74f), o(0.88f, 0.74f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.12f, 0.9f), o(0.6f, 0.9f), strokeWidth = w, cap = StrokeCap.Round)
        }
        Glyph.Chat -> {
            val p = Path()
            p.moveTo(s * 0.14f, s * 0.2f); p.lineTo(s * 0.86f, s * 0.2f); p.lineTo(s * 0.86f, s * 0.68f)
            p.lineTo(s * 0.46f, s * 0.68f); p.lineTo(s * 0.26f, s * 0.88f); p.lineTo(s * 0.26f, s * 0.68f)
            p.lineTo(s * 0.14f, s * 0.68f); p.close()
            drawPath(p, c, style = st)
            drawLine(c, o(0.3f, 0.38f), o(0.7f, 0.38f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.3f, 0.52f), o(0.56f, 0.52f), strokeWidth = w, cap = StrokeCap.Round)
        }
        Glyph.Gallery -> {
            drawRoundRect(c, o(0.12f, 0.18f), Size(s * 0.76f, s * 0.64f), androidx.compose.ui.geometry.CornerRadius(s * 0.05f), style = st)
            val p = Path()
            p.moveTo(s * 0.16f, s * 0.74f); p.lineTo(s * 0.4f, s * 0.46f); p.lineTo(s * 0.56f, s * 0.64f); p.lineTo(s * 0.68f, s * 0.52f); p.lineTo(s * 0.84f, s * 0.72f)
            drawPath(p, c, style = st)
            drawCircle(c, radius = s * 0.06f, center = o(0.7f, 0.34f))
        }
        Glyph.Alarm -> {
            drawCircle(c, radius = s * 0.32f, center = o(0.5f, 0.55f), style = st)
            drawLine(c, o(0.5f, 0.55f), o(0.5f, 0.38f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.5f, 0.55f), o(0.63f, 0.62f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.16f, 0.24f), o(0.3f, 0.12f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(c, o(0.84f, 0.24f), o(0.7f, 0.12f), strokeWidth = w, cap = StrokeCap.Round)
        }
        Glyph.Dice -> {
            drawRoundRect(c, o(0.16f, 0.16f), Size(s * 0.68f, s * 0.68f), androidx.compose.ui.geometry.CornerRadius(s * 0.12f), style = st)
            for ((x, y) in listOf(0.34f to 0.34f, 0.66f to 0.66f, 0.5f to 0.5f, 0.66f to 0.34f, 0.34f to 0.66f)) {
                drawCircle(c, radius = s * 0.055f, center = o(x, y))
            }
        }
        Glyph.Quote -> {
            for (dx in listOf(0f, 0.36f)) {
                drawCircle(c, radius = s * 0.11f, center = o(0.3f + dx, 0.38f))
                val p = Path()
                p.moveTo(s * (0.4f + dx), s * 0.38f)
                p.quadraticBezierTo(s * (0.4f + dx), s * 0.66f, s * (0.2f + dx), s * 0.74f)
                drawPath(p, c, style = st)
            }
        }
        Glyph.Settings -> {
            drawCircle(c, radius = s * 0.17f, center = o(0.5f, 0.5f), style = st)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val cx = 0.5f; val cy = 0.5f
                val x1 = cx + 0.30f * Math.cos(a).toFloat(); val y1 = cy + 0.30f * Math.sin(a).toFloat()
                val x2 = cx + 0.42f * Math.cos(a).toFloat(); val y2 = cy + 0.42f * Math.sin(a).toFloat()
                drawLine(c, o(x1, y1), o(x2, y2), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
fun HudIconButton(glyph: Glyph, label: String, modifier: Modifier = Modifier, accent: Color = Glow, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .tile(accent),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(22.dp)) { drawGlyph(glyph, accent) }
        }
        Text(label, color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 3.dp))
    }
}
