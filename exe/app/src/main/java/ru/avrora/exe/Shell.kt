package ru.avrora.exe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Четыре вкладки нижней полоски. Приложение открывается на ленте. */
enum class Tab(val label: String, val glyph: Glyph) {
    Feed("ЛЕНТА", Glyph.Feed),
    Chat("ЧАТ", Glyph.Chat),
    Gallery("ГАЛЕРЕЯ", Glyph.Gallery),
    Profile("ПРОФИЛЬ", Glyph.Profile)
}

@Composable
fun GradientLine() {
    val h = when (AppTheme.current.frame) { "mecha" -> 5.dp; "hud" -> 3.dp; else -> 1.dp }
    Canvas(Modifier.fillMaxWidth().height(h)) { themeRail(size.width) }
}

/** Верхняя строка вкладки: название слева, шестерёнка справа открывает виджеты. */
@Composable
fun TabTopBar(title: String, onGear: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (AppTheme.current.frame == "mecha") {
                Box(Modifier.size(width = 4.dp, height = 18.dp).background(Glow))
                Spacer(Modifier.width(8.dp))
            }
            Text(title, color = if (AppTheme.current.light) TextMain else Glow, fontSize = 17.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            IconButton(onClick = onGear) {
                Icon(Icons.Default.Settings, contentDescription = "Виджеты и настройки", tint = TextDim)
            }
        }
        GradientLine()
    }
}

@Composable
fun TabPage(title: String, onGear: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TabTopBar(title, onGear)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel)) {
        GradientLine()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.values().forEach { t ->
                val on = t == tab
                val color = if (on) Glow else TextDim
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(t) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.width(26.dp).height(2.dp)
                            .background(if (on) Magenta else Color.Transparent)
                    )
                    Canvas(Modifier.padding(top = 4.dp).size(24.dp)) { drawGlyph(t.glyph, color) }
                    Text(t.label, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// ---------- глитч ----------

/** Дёргает содержимое по горизонтали, пока level > 0 (шагов 6..1). */
fun Modifier.glitchShift(level: Int, seed: Int): Modifier = graphicsLayer {
    if (level > 0) {
        translationX = Random(seed * 31 + level).nextInt(-14, 15) * (level / 6f) * density
    }
}

@Composable
fun GlitchOverlay(level: Int, seed: Int) {
    if (level <= 0) return
    Canvas(Modifier.fillMaxSize()) {
        val r = Random(seed * 131 + level)
        val k = level / 6f
        repeat(4 + level * 2) {
            val y = r.nextFloat() * size.height
            val h = 2f + r.nextFloat() * size.height * 0.045f
            val x = r.nextInt(0, 60).toFloat()
            val c = if (r.nextBoolean()) Glow else Magenta
            drawRect(c.copy(alpha = 0.08f + 0.22f * k), Offset(x, y), Size(size.width * (0.35f + r.nextFloat() * 0.65f), h))
        }
    }
}

// ---------- виджеты ----------

@Composable
private fun WidgetCard(title: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .tile(accent)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Text(title, color = accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Панель, которая вылетает сверху по шестерёнке: живые виджеты и быстрые переходы. */
@Composable
fun WidgetsDrawer(vm: ChatViewModel, open: Boolean, onClose: () -> Unit, onOpen: (Screen) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color(0xAA000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
            )
        }
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            var now by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1000)
                }
            }
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
            val date = SimpleDateFormat("EEE, d MMM", Locale("ru")).format(Date(now))
            val state = vm.exeStates.lastOrNull()
            val mem = vm.memory
            val plans = vm.exePlans.count { it.status == "pending" }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp)
                    .background(Panel)
                    .border(1.dp, Edge)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetCard("ВРЕМЯ", Violet, Modifier.weight(1f), onClick = { onOpen(Screen.Clock) }) {
                        Text(
                            time, color = Glow, fontSize = 24.sp, fontFamily = FontFamily.Monospace,
                            style = TextStyle(shadow = Shadow(Glow, blurRadius = 18f), fontFamily = FontFamily.Monospace, fontSize = 24.sp, color = Glow)
                        )
                        Text(date, color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    WidgetCard("СОСТОЯНИЕ", Glow, Modifier.weight(1f), onClick = { onOpen(Screen.States) }) {
                        Text(state?.name ?: "не отмечено", color = TextMain, fontSize = 15.sp, maxLines = 1)
                        Text(state?.why ?: "", color = TextDim, fontSize = 11.sp, maxLines = 2)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetCard("ПАМЯТЬ", Magenta, Modifier.weight(1f), onClick = { onOpen(Screen.Memory) }) {
                        Text("ядро ${mem.core.size}", color = TextMain, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                        Text("эпизодов ${mem.episodes.size}", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    WidgetCard("ПЛАНЫ", Violet, Modifier.weight(1f), onClick = { onOpen(Screen.Schedule) }) {
                        Text("назначено $plans", color = TextMain, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                        Text("следов ${vm.exeNotes.size}", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HudIconButton(Glyph.Clock, "ЧАСЫ", accent = Violet) { onOpen(Screen.Clock) }
                    HudIconButton(Glyph.Memory, "ПАМЯТЬ", accent = Magenta) { onOpen(Screen.Memory) }
                    HudIconButton(Glyph.Diary, "ДНЕВНИК", accent = Glow) { onOpen(Screen.Diary) }
                    HudIconButton(Glyph.Dice, "ИГРА", accent = Glow) { onOpen(Screen.Game) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HudIconButton(Glyph.Alarm, "БУДИЛ.", accent = Violet) { onOpen(Screen.Alarms) }
                    HudIconButton(Glyph.Quote, "ЦИТАТЫ", accent = Magenta) { onOpen(Screen.Quotes) }
                    HudIconButton(Glyph.Hand, "ПРАВА", accent = Violet) { onOpen(Screen.Rights) }
                    HudIconButton(Glyph.Settings, "ВСЕ", accent = Magenta) { onOpen(Screen.Menu) }
                }
                GradientLine()
            }
        }
    }
}
