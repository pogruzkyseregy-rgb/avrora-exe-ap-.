package ru.avrora.exe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// Газоразрядные индикаторы (никси): цифры вырезаны из фотографии настоящих ламп.

val NIXIE_RES = listOf(
    R.drawable.nixie_0, R.drawable.nixie_1, R.drawable.nixie_2, R.drawable.nixie_3, R.drawable.nixie_4,
    R.drawable.nixie_5, R.drawable.nixie_6, R.drawable.nixie_7, R.drawable.nixie_8, R.drawable.nixie_9
)
const val NIXIE_ASPECT = 116f / 304f
private val NixieGlow = Color(0xFFFF8A2A)

fun nixieRes(ch: Char): Int = if (ch.isDigit()) NIXIE_RES[ch - '0'] else R.drawable.nixie_blank

/** Ряд ламп: «HH:mm» или «HH:mm:ss». Секунды в лампах поменьше, двоеточие это две неоновые точки. */
@Composable
fun NixieClock(text: String, colon: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val big = text.take(5).count { it.isDigit() }
        val small = text.drop(5).count { it.isDigit() }
        val colons = text.count { it == ':' }
        // ширина: большие лампы + маленькие (0.62) + точки (0.28 ширины лампы)
        val units = big + small * 0.62f + colons * 0.28f
        val tubeW = maxWidth / units
        val tubeH = tubeW / NIXIE_ASPECT
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            var digitIndex = 0
            text.forEach { ch ->
                if (ch == ':') {
                    val h = if (digitIndex >= 4) tubeH * 0.62f else tubeH
                    Box(Modifier.width(tubeW * 0.28f).height(h)) {
                        Canvas(Modifier.fillMaxWidth().height(h)) {
                            val r = size.width * 0.2f
                            for (fy in listOf(0.38f, 0.62f)) {
                                val c = Offset(size.width / 2, size.height * fy)
                                if (colon) {
                                    drawCircle(Brush.radialGradient(listOf(NixieGlow.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r * 3.2f), radius = r * 3.2f, center = c)
                                    drawCircle(NixieGlow, radius = r, center = c)
                                    drawCircle(Color(0xFFFFD9A8), radius = r * 0.45f, center = c)
                                } else {
                                    drawCircle(NixieGlow.copy(alpha = 0.12f), radius = r, center = c)
                                }
                            }
                        }
                    }
                } else {
                    val scale = if (digitIndex >= 4) 0.62f else 1f
                    Image(
                        painter = painterResource(nixieRes(ch)),
                        contentDescription = ch.toString(),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.width(tubeW * scale).height(tubeH * scale)
                    )
                    digitIndex++
                }
            }
        }
    }
}

/** Картинка «HH:mm» из ламп для виджета. digits: 4 символа (цифры или пробел для пустой лампы). */
object NixieBitmap {
    private var cache: List<Bitmap>? = null
    private var blank: Bitmap? = null

    private fun tiles(ctx: Context): List<Bitmap> {
        cache?.let { return it }
        val list = NIXIE_RES.map { BitmapFactory.decodeResource(ctx.resources, it) }
        cache = list
        blank = BitmapFactory.decodeResource(ctx.resources, R.drawable.nixie_blank)
        return list
    }

    fun render(ctx: Context, text: String, colon: Boolean = true): Bitmap {
        val t = tiles(ctx)
        val h = 200
        val tw = (h * NIXIE_ASPECT).toInt()
        val cw = (tw * 0.34f).toInt()
        val digits = text.filter { it.isDigit() || it == ' ' || it == '?' }
        val w = digits.length * tw + cw
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        var x = 0
        digits.forEachIndexed { i, ch ->
            if (i == 2) {
                if (colon) drawDots(c, x, cw, h)
                x += cw
            }
            val src = if (ch.isDigit()) t[ch - '0'] else blank!!
            c.drawBitmap(src, null, Rect(x, 0, x + tw, h), p)
            x += tw
        }
        return out
    }

    private fun drawDots(c: Canvas, x: Int, w: Int, h: Int) {
        val r = w * 0.18f
        for (fy in listOf(0.4f, 0.6f)) {
            val cx = x + w / 2f
            val cy = h * fy
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, r * 3.2f, intArrayOf(0x8CFF8A2A.toInt(), 0x00FF8A2A), null, Shader.TileMode.CLAMP)
            }
            c.drawCircle(cx, cy, r * 3.2f, glow)
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF8A2A.toInt() })
            c.drawCircle(cx, cy, r * 0.45f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD9A8.toInt() })
        }
    }
}

/** Выбор цифр для часов, будильника и виджета. */
@Composable
fun DigitStylePicker(selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("ЦИФРЫ ЧАСОВ", color = Violet, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("vfd" to "Вакуумные (ВЛИ)", "nixie" to "Газоразрядные").forEach { (id, title) ->
                val on = selected == id
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Terminal)
                        .border(if (on) 2.dp else 1.dp, if (on) Glow else Edge, RoundedCornerShape(10.dp))
                        .clickable { onPick(id) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (id == "nixie") NixieClock("12:34", true, Modifier.width(110.dp))
                    else Canvas(Modifier.width(110.dp).height(44.dp)) { drawTube("12:34", true) }
                    Text(title, color = if (on) TextMain else TextDim, fontSize = 12.sp)
                }
            }
        }
    }
}
