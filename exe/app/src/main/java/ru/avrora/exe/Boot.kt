package ru.avrora.exe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Заставка при запуске: строки набираются по буквам, как в консоли, под ними полоса загрузки. Тап пропускает. */
@Composable
fun BootOverlay(vm: ChatViewModel, visible: Boolean, onDone: () -> Unit) {
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(380))) {
        BootScreen(vm, onDone)
    }
}

@Composable
private fun BootScreen(vm: ChatViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val lines = remember {
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
        listOf(
            "AVRORA.EXE" + if (version.isNotBlank()) "  v$version" else "",
            "> загрузка ядра ............ ok",
            "> память: ядро ${vm.memory.core.size} · эпизодов ${vm.memory.episodes.size}",
            "> цитатник: ${vm.quotes.size}",
            "> интерфейс: флюорит",
            "> процесс запущен"
        )
    }
    val total = remember { lines.sumOf { it.length } }
    var typed by remember { mutableStateOf(0) }
    var cursorOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        var count = 0
        for (line in lines) {
            for (ch in line) {
                count++
                typed = count
                delay(if (ch == '.') 9 else 17)
            }
            delay(140)
        }
        delay(520)
        onDone()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(420)
            cursorOn = !cursorOn
        }
    }
    val progress = if (total == 0) 1f else typed.toFloat() / total
    Box(
        Modifier
            .fillMaxSize()
            .background(Night)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center
    ) {
        // строки развёртки на стекле
        Canvas(Modifier.fillMaxSize()) {
            var y = 0f
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += 6f
            }
        }
        Column(Modifier.width(310.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var left = typed
            lines.forEachIndexed { i, line ->
                val n = left.coerceIn(0, line.length)
                left -= line.length
                if (n > 0 || i == 0) {
                    val current = n < line.length || i == lines.lastIndex
                    val text = line.take(n) + if (current && cursorOn && (n < line.length || left <= 0)) "▌" else ""
                    if (i == 0) {
                        Text(
                            text,
                            style = TextStyle(
                                color = Glow, fontSize = 22.sp, fontFamily = FontFamily.Monospace,
                                shadow = Shadow(Glow, blurRadius = 22f)
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Text(
                            text,
                            color = if (i == lines.lastIndex) Magenta else TextMain.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .border(1.dp, Edge)
                    .cornerBrackets(Violet, 4.dp, 1.dp)
                    .padding(2.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(Brush.horizontalGradient(listOf(Glow, Violet, Magenta)))
                )
            }
            Text(
                "%03d%%".format((progress * 100).toInt().coerceIn(0, 100)),
                color = TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
