package ru.avrora.exe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- профиль-страница ----------
// Как в соцсети: обложка, аватар, имя, статус, «о себе», счётчики и лента постов.
// Ядро с графиками — отдельно, по кнопке «Ядро».

data class SocialAction(val label: String, val glyph: String, val onClick: () -> Unit)

@Composable
fun SocialHeader(
    accent: Color,
    avatar: @Composable (Dp) -> Unit,
    name: String,
    handle: String,
    status: String,
    about: String,
    counters: List<Pair<String, String>>,
    actions: List<SocialAction>
) {
    Column(Modifier.fillMaxWidth()) {
        // обложка: свет акцента, тонкие орбиты и сетка точек — в духе ядра, но спокойно
        Box(Modifier.fillMaxWidth().height(150.dp).clip(panelShape())) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Violet.copy(alpha = 0.25f), Night), Offset.Zero, Offset(size.width, size.height)))
                val c = Offset(size.width * 0.82f, size.height * 0.35f)
                for (k in 1..5) drawCircle(Color.White.copy(alpha = 0.07f + 0.02f * (5 - k)), size.height * 0.18f * k, c, style = Stroke(1.dp.toPx()))
                val step = 14.dp.toPx()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width * 0.55f) { drawCircle(Color.White.copy(alpha = 0.08f), 1.dp.toPx(), Offset(x, y)); x += step }
                    y += step
                }
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.55f)), size.height * 0.5f, size.height),
                    Offset(0f, size.height * 0.5f), Size(size.width, size.height * 0.5f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).pullUp(46.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(100.dp).clip(avatarShape()).background(Night).border(3.dp, accent, avatarShape()), contentAlignment = Alignment.Center) {
                avatar(94.dp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(handle, color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            if (status.isNotBlank()) Text("«$status»", color = accent, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
            if (about.isNotBlank()) Text(about, color = TextMain, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                counters.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = TextDim, fontSize = 13.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { a ->
                    Column(
                        Modifier.weight(1f).clip(panelShape()).background(Panel).border(1.dp, accent.copy(alpha = 0.35f), panelShape())
                            .clickable { a.onClick() }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(a.glyph, color = accent, fontSize = 18.sp)
                        Text(a.label, color = TextMain, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Подпись над лентой постов. */
@Composable
fun FeedTitle(text: String) {
    Text(text, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** Кнопка «‹ Профиль» над ядром. */
@Composable
fun BackToProfile(accent: Color, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onBack() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹  ПРОФИЛЬ", color = accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
    }
}

/** Сдвинуть вверх и не оставлять под собой пустого места (аватар наезжает на обложку). */
fun Modifier.pullUp(by: Dp): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(p.width, (p.height - shift).coerceAtLeast(0)) { p.place(0, -shift) }
}

@Suppress("unused")
private val keep = CircleShape
