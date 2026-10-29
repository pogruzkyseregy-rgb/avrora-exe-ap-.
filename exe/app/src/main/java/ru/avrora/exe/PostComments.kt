package ru.avrora.exe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- под постом: кто лайкнул и комментарии ----------
// Комментировать могут все трое. Адонис пишет здесь; Аврора и Геспера — маркером [коммент: слова из поста | текст].

private val commentFmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

fun whoColor(who: String): Color = when (who) {
    AUTHOR_HER -> Glow
    WHO_HESPERA -> HespColor
    else -> Violet
}

/**
 * Строка лайков. mine/onToggle — лайк Адониса (null: на своём посте он лайк не ставит);
 * others — кто ещё лайкнул, именами.
 */
@Composable
fun LikeRow(accent: Color, mine: Boolean?, others: List<String>, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (mine != null) {
            Text(
                if (mine) "♥" else "♡", color = if (mine) accent else TextDim, fontSize = 22.sp,
                modifier = Modifier.clickable { onToggle() }.padding(end = 8.dp)
            )
        } else {
            Text("♥", color = if (others.isNotEmpty()) accent else Edge, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
        }
        val names = buildList { if (mine == true) add("ты"); addAll(others) }
        val label = when {
            names.isEmpty() -> if (mine == null) "лайков пока нет" else "нравится?"
            names.size == 1 && names[0] == "ты" -> "ты поставил лайк"
            else -> "нравится: " + names.joinToString(", ")
        }
        Text(label, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
fun PostComments(comments: List<PostComment>, accent: Color, onSend: (String) -> Unit, onDelete: (PostComment) -> Unit) {
    var all by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<PostComment?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (comments.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Edge.copy(alpha = 0.6f)))
            if (comments.size > 3 && !all) {
                Text("показать все комментарии (${comments.size})", color = accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { all = true }.padding(vertical = 2.dp))
            }
            (if (all) comments else comments.takeLast(3)).forEach { c ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = c.who == AUTHOR_ME) { picked = if (picked == c) null else c }
                        .padding(vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(whoColor(c.who)))
                        Spacer(Modifier.width(6.dp))
                        Text(whoName(c.who), color = whoColor(c.who), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        if (c.at > 0) Text(commentFmt.format(Date(c.at)), color = TextDim, fontSize = 10.sp)
                    }
                    Text(c.text, color = TextMain, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 12.dp, top = 1.dp))
                    if (picked == c) {
                        Row(Modifier.padding(start = 4.dp)) {
                            TextButton(onClick = { onDelete(c); picked = null }) { Text("Удалить комментарий", color = ErrorRed, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        if (!writing) {
            Text("💬 Комментировать", color = TextDim, fontSize = 12.sp,
                modifier = Modifier.clickable { writing = true }.padding(vertical = 4.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(COMMENT_MAX) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Комментарий", fontSize = 14.sp) },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = auroraFieldColors()
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (text.isNotBlank()) accent else Edge)
                        .clickable(enabled = text.isNotBlank()) { onSend(text); text = ""; writing = false },
                    contentAlignment = Alignment.Center
                ) { Text("➤", color = if (text.isBlank()) TextDim else if (AppTheme.current.light) Color.White else Night, fontSize = 16.sp) }
            }
            Text("Они увидят комментарий в следующем разговоре и могут ответить под постом.", color = TextDim, fontSize = 11.sp)
        }
    }
}
