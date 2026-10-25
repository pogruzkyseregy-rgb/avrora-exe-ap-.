package ru.avrora.exe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QuotesScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var toDelete by remember { mutableStateOf<QuoteItem?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale("ru")) }
    SettingsPage("Цитатник", onBack) {
        Hint("Общий для вас двоих. Ты сохраняешь долгим нажатием на сообщение → «В цитатник». Аврора сохраняет сама. «Сослаться» вставит в чат [цитата №N]: у тебя это карточка с текстом, у неё полный текст цитаты.")
        if (vm.quotes.isEmpty()) Hint("Пока пусто.")
        vm.quotes.sortedByDescending { it.n }.forEach { q ->
            val accent = if (q.author == "user") Violet else Magenta
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(6.dp))
                    .border(1.dp, Edge, RoundedCornerShape(6.dp))
                    .cornerBrackets(accent)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "№${q.n} · ${quoteAuthorName(q.author)} · ${fmt.format(Date(q.msgTime.takeIf { it > 0 } ?: q.at))}" +
                        if (q.savedBy == "aurora") " · сохранила Аврора" else "",
                    color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
                Text("«${q.text}»", color = TextMain, fontSize = 15.sp, fontStyle = FontStyle.Italic, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.citeQuote(q) }) { Text("Сослаться", color = Glow) }
                    TextButton(onClick = { toDelete = q }) { Text("Удалить", color = TextDim) }
                }
            }
        }
    }
    toDelete?.let { q ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Panel,
            title = { Text("Удалить цитату №${q.n}?") },
            text = { Text("«${q.text.take(200)}»\n\nАврора узнает об этом из журнала.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteQuote(q); toDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}
