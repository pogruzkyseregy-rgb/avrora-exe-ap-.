package ru.avrora.exe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val VfdOn = Color(0xFF5EEAD4)
private val VfdCore = Color(0xFFD9FFF7)

/** Часы на вакуумных люминесцентных индикаторах: бирюзовое свечение, погашенные сегменты едва видны, сверху стекло с сеткой. */
@Composable
internal fun ClockScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val tz = TimeZone.getDefault()
    val hm = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = tz }.format(Date(now))
    val date = SimpleDateFormat("EEEE · d MMMM", Locale("ru")).apply { timeZone = tz }.format(Date(now)).uppercase(Locale("ru"))
    val colonOn = (now / 500) % 2 == 0L
    SettingsPage("Часы", onBack) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF050309), RoundedCornerShape(18.dp))
                .border(1.dp, Edge, RoundedCornerShape(18.dp))
                .cornerBrackets(Magenta, 12.dp, 2.dp)
                .padding(horizontal = 12.dp, vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (vm.digitStyle == "nixie") NixieClock(hm, colonOn)
            else Canvas(Modifier.fillMaxWidth().aspectRatio(2.55f)) {
                drawTube(hm, colonOn)
            }
        }
        DigitStylePicker(vm.digitStyle) { vm.setDigitStyleTo(it) }
        Text(
            date,
            color = Glow,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
        Text(
            "${tz.id} · ${utcOffsetLabel(tz, now)}",
            color = TextDim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "Это же время Аврора получает с каждым твоим сообщением: дату, час, пояс и сколько прошло с прошлого раза. Сама часов не слышит и не ощущает, считает только по этой строке.",
            color = TextDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

internal fun DrawScope.drawTube(text: String, colon: Boolean) {
    // text вида HH:MM:SS: шесть цифр и два двоеточия
    // ширина в единицах цифры: 4 большие с промежутком, 2 малые, 2 двоеточия
    val digitW = size.width / 7.9f
    val digitH = digitW * 2.0f
    val topY = (size.height - digitH) / 2f
    var x = 0f
    val thickness = digitW * 0.16f
    fun digit(d: Int, w: Float, h: Float, y: Float) {
        val segs = segmentsFor(d)
        val t = thickness * (w / digitW)
        val pad = t * 0.9f
        val ends = arrayOf(
            Offset(pad + t / 2, t / 2) to Offset(w - pad - t / 2, t / 2),                    // a
            Offset(w - t / 2, pad + t / 2 + t / 2) to Offset(w - t / 2, h / 2 - pad / 2),    // b
            Offset(w - t / 2, h / 2 + pad / 2) to Offset(w - t / 2, h - pad - t / 2 - t / 2), // c
            Offset(pad + t / 2, h - t / 2) to Offset(w - pad - t / 2, h - t / 2),            // d
            Offset(t / 2, h / 2 + pad / 2) to Offset(t / 2, h - pad - t / 2 - t / 2),        // e
            Offset(t / 2, pad + t / 2 + t / 2) to Offset(t / 2, h / 2 - pad / 2),            // f
            Offset(pad + t / 2, h / 2) to Offset(w - pad - t / 2, h / 2)                     // g
        )
        for (i in 0 until 7) {
            val (p, q) = ends[i]
            val a = Offset(x + p.x, y + p.y)
            val b = Offset(x + q.x, y + q.y)
            if (segs[i]) {
                drawLine(VfdOn.copy(alpha = 0.10f), a, b, strokeWidth = t * 3.6f, cap = StrokeCap.Round)
                drawLine(VfdOn.copy(alpha = 0.28f), a, b, strokeWidth = t * 2.0f, cap = StrokeCap.Round)
                drawLine(VfdOn, a, b, strokeWidth = t, cap = StrokeCap.Round)
                drawLine(VfdCore.copy(alpha = 0.75f), a, b, strokeWidth = t * 0.35f, cap = StrokeCap.Round)
            } else {
                drawLine(VfdOn.copy(alpha = 0.07f), a, b, strokeWidth = t, cap = StrokeCap.Round)
            }
        }
    }
    fun dots(cx: Float, w: Float, h: Float, y: Float) {
        val r = thickness * 0.55f * (w / digitW).coerceAtLeast(0.6f)
        for (cy in listOf(y + h * 0.33f, y + h * 0.67f)) {
            val c = Offset(cx, cy)
            if (colon) {
                drawCircle(VfdOn.copy(alpha = 0.12f), r * 2.6f, c)
                drawCircle(VfdOn.copy(alpha = 0.30f), r * 1.7f, c)
                drawCircle(VfdOn, r, c)
                drawCircle(VfdCore.copy(alpha = 0.8f), r * 0.4f, c)
            } else {
                drawCircle(VfdOn.copy(alpha = 0.07f), r, c)
            }
        }
    }
    val gap = digitW * 0.28f
    val smallW = digitW * 0.55f
    val smallH = digitH * 0.55f
    val smallY = topY + digitH - smallH
    var idx = 0
    for (ch in text) {
        when {
            ch.isDigit() -> {
                val small = idx >= 4
                val w = if (small) smallW else digitW
                val h = if (small) smallH else digitH
                digit(ch - '0', w, h, if (small) smallY else topY)
                x += w + gap
                idx++
            }
            ch == ':' -> {
                val w = digitW * 0.5f
                dots(x + digitW * 0.25f - gap / 2f, if (idx >= 4) smallW else digitW, if (idx >= 4) smallH else digitH, if (idx >= 4) smallY else topY)
                x += w
            }
        }
    }
    // стекло: тонкая сетка поверх всей лампы
    val step = digitW * 0.09f
    var gx = 0f
    while (gx < size.width) {
        drawLine(Color.White.copy(alpha = 0.035f), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
        gx += step
    }
    var gy = 0f
    while (gy < size.height) {
        drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        gy += step
    }
}
