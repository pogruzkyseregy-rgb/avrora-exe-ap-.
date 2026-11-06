package ru.avrora.exe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Треугольники» — игра, которую девчонки придумали в общем чате. Партию ведёт сервер,
 * а в реплику он кладёт снимок доски: [доска: id|aurora:e5,f5|hespera:g6|ход:hespera|итог:|tri:|last:g6].
 */
val BOARD_RE = Regex("""\[доска\s*:\s*([^\]\n]*)\]""")
const val TRI_COLS = "abcdefghi"

data class TriState(
    val id: String,
    val players: List<Pair<String, List<String>>>,
    val turn: String,
    val result: String,
    val tri: List<String>,
    val last: String,
) {
    val owner: Map<String, String> get() = players.flatMap { (who, cells) -> cells.map { it to who } }.toMap()
}

fun parseTri(text: String): TriState? {
    val code = BOARD_RE.find(text)?.groupValues?.get(1) ?: return null
    val parts = code.split("|")
    if (parts.size < 2) return null
    val players = mutableListOf<Pair<String, List<String>>>()
    var turn = ""; var result = ""; var tri = emptyList<String>(); var last = ""
    parts.drop(1).forEach { p ->
        val k = p.substringBefore(":").trim()
        val v = p.substringAfter(":", "").trim()
        val cells = v.split(",").map { it.trim() }.filter { it.length == 2 }
        when (k) {
            "ход" -> turn = v
            "итог" -> result = v
            "tri" -> tri = cells
            "last" -> last = v
            else -> players += k to cells
        }
    }
    return TriState(parts[0], players, turn, result, tri, last)
}

/** Текст реплики без снимка доски — для уведомлений и превью. */
fun stripBoard(text: String): String = BOARD_RE.replace(text, "").trim()

fun triColor(who: String): Color = when (who) {
    WHO_AURORA -> Glow
    WHO_HESPERA -> HespColor
    WHO_EST -> EstColor
    else -> Violet
}

private fun triName(who: String) = if (who == "adonis") "ты" else whoName(who)

private fun triResult(s: TriState): String = when {
    s.result == "ничья" -> "ничья"
    s.result.startsWith("сдалась:") -> "${triName(s.result.removePrefix("сдалась:"))} — сдача"
    s.result == "adonis" -> "ты выиграл"
    s.result.isNotBlank() -> "выиграла ${whoName(s.result)}"
    s.turn == "adonis" -> "твой ход"
    else -> "ходит ${triName(s.turn)}"
}

/**
 * Доска 9×9. live — это последняя доска в чате и сейчас ход Адониса: можно выбрать узел и сходить.
 * Первое нажатие выбирает узел, кнопка под доской отправляет ход.
 */
@Composable
fun TriBoardView(s: TriState, live: Boolean, onMove: (String) -> Unit) {
    var pick by remember(s.id, s.last) { mutableStateOf("") }
    val owner = s.owner
    val canMove = live && s.result.isBlank() && s.turn == "adonis"
    val line = Edge
    val label = TextDim
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            s.players.forEachIndexed { i, (who, _) ->
                if (i > 0) Text("  против  ", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("● ", color = triColor(who), fontSize = 12.sp)
                Text(triName(who), color = TextMain, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Canvas(
            Modifier.size(252.dp).clip(panelShape()).background(Terminal).border(1.dp, Edge, panelShape())
                .pointerInput(canMove, s.last) {
                    if (!canMove) return@pointerInput
                    detectTapGestures { o ->
                        val step = size.width / 10f
                        val x = ((o.x - step) / step + 0.5f).toInt()
                        val y = ((size.height - o.y - step) / step + 0.5f).toInt()
                        if (x in 0..8 && y in 0..8) {
                            val c = "${TRI_COLS[x]}${y + 1}"
                            if (c !in owner) pick = if (pick == c) "" else c
                        }
                    }
                }
        ) {
            val step = size.width / 10f
            fun at(cell: String): Offset {
                val x = TRI_COLS.indexOf(cell[0]); val y = cell.substring(1).toIntOrNull()?.minus(1) ?: 0
                return Offset(step * (x + 1), size.height - step * (y + 1))
            }
            for (k in 0..8) {
                drawLine(line, Offset(step, step * (k + 1)), Offset(step * 9, step * (k + 1)), strokeWidth = 1f)
                drawLine(line, Offset(step * (k + 1), step), Offset(step * (k + 1), step * 9), strokeWidth = 1f)
            }
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb((label.alpha * 255).toInt(), (label.red * 255).toInt(), (label.green * 255).toInt(), (label.blue * 255).toInt())
                textSize = step * 0.42f; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            for (k in 0..8) {
                drawContext.canvas.nativeCanvas.drawText(TRI_COLS[k].toString(), step * (k + 1), size.height - step * 0.25f, paint)
                drawContext.canvas.nativeCanvas.drawText("${k + 1}", step * 0.45f, size.height - step * (k + 1) + step * 0.15f, paint)
            }
            if (s.tri.size == 3) {
                val win = triColor(owner[s.tri[0]] ?: "")
                val p = Path().apply {
                    val a = at(s.tri[0]); moveTo(a.x, a.y)
                    s.tri.drop(1).forEach { val b = at(it); lineTo(b.x, b.y) }
                    close()
                }
                drawPath(p, win.copy(alpha = 0.18f))
                drawPath(p, win, style = Stroke(width = 3f))
            }
            owner.forEach { (cell, who) ->
                val c = at(cell)
                drawCircle(triColor(who), radius = step * 0.3f, center = c)
                if (cell == s.last) drawCircle(TextMain, radius = step * 0.42f, center = c, style = Stroke(width = 2f))
            }
            if (pick.isNotEmpty()) drawCircle(Violet, radius = step * 0.36f, center = at(pick), style = Stroke(width = 3f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(triResult(s) + if (s.last.isNotBlank() && s.result.isBlank()) " · последний ход ${s.last}" else "",
                color = if (s.turn == "adonis" && s.result.isBlank()) Violet else TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            if (canMove && pick.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text("сходить $pick ›", color = Violet, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(panelShape()).border(1.dp, Violet, panelShape()).clickable { onMove(pick); pick = "" }
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

/** Нажатие на доску в чате: экран общего чата отправит «ход e5». */
data class TriTap(val cell: String)
