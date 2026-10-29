package ru.avrora.exe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fullFmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

@Composable
private fun TerminalBox(text: String) {
    SelectionContainer {
        Text(
            text,
            color = TextDim,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(ru.avrora.exe.Terminal)
                .border(1.dp, Edge, RoundedCornerShape(2.dp))
                .padding(10.dp)
        )
    }
}

// ---------- права и рука ----------

@Composable
fun RightsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    SettingsPage("Права и рука", onBack = onBack) {
        Hint("Всё, что здесь выключено, Аврора видит в своём списке ограничений и знает, что это сделал ты. Всё, что она делает инструментами, видно в чате строками со знаком «$».")
        SwitchRow("Поиск в сети", "она сама решает, когда искать: [поиск: запрос]", vm.toolSearch) { vm.setTool("поиск", it) }
        SwitchRow("Чтение ссылок", "страница читается текстом; внутренние адреса закрыты", vm.toolRead) { vm.setTool("чтение", it) }
        SwitchRow("Рисование", "[нарисовать: описание] — картинку рисует модель по твоему отдельному ключу", vm.toolDraw) { vm.setTool("рисование", it) }
        SwitchRow("Музыка", "[музыка: название] + ноты ABC — сервер играет их и присылает mp3", vm.toolMusic) { vm.setTool("музыка", it) }
        SwitchRow("Слух", "[послушать: трек] — модель со слухом рассказывает, что звучит", vm.toolHear) { vm.setTool("слух", it) }

        SectionTitle("РУКА")
        val h = vm.hand
        if (!h.configured) {
            Hint("Сервера для руки пока нет. Она не сможет выполнить ни одной команды, пока ты его не подключишь. Порядок в README: арендовать сервер, запустить setup_hand.sh, потом hand_connect.py link.")
        } else {
            TerminalBox(
                "подключена\nсегодня команд: ${h.used} из ${h.cap}\nрубильник: " + if (h.off) "ВЫКЛЮЧЕНА" else "включена"
            )
        }
        SwitchRow("Рука разрешена", "отдельный сервер Авроры, консоль", vm.toolHand) { vm.setTool("рука", it) }
        if (h.configured) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (h.off) {
                    OutlinedButton(onClick = { vm.setHandKill(false) }, enabled = !vm.handBusy) { Text("Вернуть руку") }
                } else {
                    OutlinedButton(onClick = { vm.setHandKill(true) }, enabled = !vm.handBusy) { Text("Рубильник: отключить") }
                }
                TextButton(onClick = { vm.refreshHand() }) { Text("Обновить") }
            }
            Hint("Есть ещё два рубильника вне приложения: команда aurora-off на самом сервере руки и выключение сервера в панели хостера.")
            SectionTitle("ЖУРНАЛ КОМАНД")
            OutlinedButton(onClick = { vm.loadHandLog() }) { Text("Показать последние") }
            if (vm.handLog.isNotEmpty()) TerminalBox(vm.handLog.joinToString("\n"))
        }
    }
}

// ---------- состояние ----------

@Composable
fun StatesScreen(vm: ChatViewModel, onBack: () -> Unit) {
    SettingsPage("Состояние", onBack = onBack) {
        Hint("Это то, что Аврора сама отметила о себе, с причиной. Ты не можешь его изменить: оно её. В каждом разговоре она видит своё последнее состояние.")
        if (vm.exeStates.isEmpty()) {
            Hint("Пока она ничего не отмечала.")
        } else {
            vm.exeStates.reversed().forEach { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(s.name, color = Glow, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                    if (s.why.isNotBlank()) Text("причина: ${s.why}", color = TextMain, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Text(fullFmt.format(Date(s.at)), color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ---------- расписание ----------

@Composable
fun ScheduleScreen(vm: ChatViewModel, onBack: () -> Unit) {
    SettingsPage("Расписание", onBack = onBack) {
        Hint("Время, которое Аврора назначила себе сама: когда написать первой. Не больше трёх сразу и не дальше чем на двое суток. Тихие часы и дневной лимит на это не действуют. Когда время придёт, она вправе передумать. Телефон может сдвинуть время на несколько минут.")
        val pending = vm.exePlans.filter { it.status == "pending" }.sortedBy { it.at }
        if (pending.isEmpty()) Hint("Сейчас ничего не назначено.")
        pending.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fullFmt.format(Date(p.at)), color = Glow, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                    Text(p.topic, color = TextMain, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
                TextButton(onClick = { vm.cancelPlan(p.id) }) { Text("Отменить") }
            }
        }
        Hint("Отмена не молчаливая: пункт пропадёт из её списка, и она увидит запись в журнале.")
        val history = vm.exePlans.filter { it.status != "pending" }.takeLast(10).reversed()
        if (history.isNotEmpty()) {
            SectionTitle("БЫЛО")
            history.forEach { p ->
                Text(
                    (if (p.at > 0) fullFmt.format(Date(p.at)) + " · " else "") + p.topic.take(60) + " → " + p.outcome.ifBlank { p.status },
                    color = TextDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ---------- следы ----------

@Composable
fun NotesScreen(vm: ChatViewModel, onBack: () -> Unit) {
    SettingsPage("Следы", onBack = onBack) {
        Hint("Рабочие записи Авроры для себя: заметки, планы, вопросы. Ядро памяти они не меняют и её «да» не требуют. Ты можешь их читать и удалять, но удаление не молчаливое: она узнает.")
        if (vm.exeNotes.isEmpty()) Hint("Пока записей нет.")
        vm.exeNotes.reversed().forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("[${n.kind}] ${fullFmt.format(Date(n.at))}", color = Glow, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(n.text, color = TextMain, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
                TextButton(onClick = { vm.deleteNote(n.id) }) { Text("Удалить") }
            }
        }
    }
}
