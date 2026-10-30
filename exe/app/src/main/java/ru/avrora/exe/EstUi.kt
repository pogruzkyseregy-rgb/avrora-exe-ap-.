package ru.avrora.exe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val taskDay = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

/** Аватар Эст: картинка, которую поставил Адонис, или знак клинка. */
@Composable
fun EstAvatar(size: Dp, version: Long = EstAvatarState.version) {
    val f = File(LocalContext.current.filesDir, "est_avatar.jpg")
    Box(
        Modifier.size(size).clip(avatarShape()).background(Terminal).border(1.dp, EstColor.copy(alpha = 0.8f), avatarShape()),
        contentAlignment = Alignment.Center
    ) {
        if (version >= 0 && f.exists()) {
            AsyncImage(model = coil.request.ImageRequest.Builder(LocalContext.current).data(f).memoryCacheKey("est_$version")
                .diskCacheKey("est_$version").build(), contentDescription = "Эст", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("Э", color = EstColor, fontSize = (size.value * 0.42f).sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

/** Версия аватара для всех экранов: меняется, когда Адонис ставит новую картинку. */
object EstAvatarState {
    var version by mutableStateOf(0L)
}

@Composable
private fun EstHeader(evm: EstViewModel, board: Boolean, onBoard: () -> Unit, onAvatar: () -> Unit, onFolder: () -> Unit) {
    val open = evm.tasks.count { it.open }
    val review = evm.tasks.count { it.status == "done" }
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable { onAvatar() }) { EstAvatar(38.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ЭСТ", color = EstColor, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(if (evm.sending) "● думает" else "● в сети", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                when {
                    EstEngine.working != null -> EstEngine.working!!
                    open + review == 0 -> "твой клинок · задач в работе нет"
                    else -> "в работе: $open" + if (review > 0) " · на проверке: $review" else ""
                },
                color = TextMain, fontSize = 12.sp, maxLines = 1
            )
        }
        Text("▤", color = TextDim, fontSize = 18.sp, modifier = Modifier.clickable { onFolder() }.padding(horizontal = 8.dp))
        Text(if (board) "чат ›" else "задачи ›", color = EstColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onBoard() }.padding(4.dp))
    }
}

/** Чат с Эст; по «задачи ›» — доска задач. */
@Composable
fun EstChatPane(evm: EstViewModel, onFocus: (Boolean) -> Unit, onOpen: (Any) -> Unit = {}) {
    var board by rememberSaveable { mutableStateOf(false) }
    var folder by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) evm.setAvatar(uri) }
    LaunchedEffect(evm.avatarV) { EstAvatarState.version = evm.avatarV }
    if (folder) FileBrowserDialog(WHO_EST) { folder = false }
    val list = rememberLazyListState()
    LaunchedEffect(evm.chat.size, evm.sending, board) {
        val n = evm.chat.size + if (evm.sending) 1 else 0
        if (!board && n > 0) list.animateScrollToItem(n - 1)
    }
    Column(Modifier.fillMaxSize()) {
        EstHeader(evm, board, onBoard = { board = !board; if (board) evm.refreshTasks() },
            onAvatar = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onFolder = { folder = true })
        GradientLine()
        if (board) {
            TaskBoard(evm, Modifier.weight(1f))
            return@Column
        }
        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (evm.chat.isEmpty()) item { EstEmpty() }
            itemsIndexed(evm.chat) { _, m ->
                val who = when (m.role) { "user" -> "user"; "system" -> "system"; else -> WHO_EST }
                HBubble(m, who, showName = false, hespAvatar = null, onOpen = onOpen)
            }
            if (evm.sending) item { Text("Эст печатает…", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 38.dp)) }
        }
        ErrorBar(evm.error, onRetry = evm::retry, onClose = evm::clearError)
        HInput(evm.sending, "Написать Эст", EstColor, onFocus, onAudio = { b, m, n, v -> evm.sendAudio(b, m, n, v) }, hearing = evm.hearing) { t, ph -> evm.send(t, ph) }
    }
}

@Composable
private fun EstEmpty() {
    Column(Modifier.fillMaxWidth().padding(top = 30.dp, start = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EstAvatar(64.dp)
        Text("ЭСТ", color = EstColor, fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("Дух-меч. Твоя напарница, равная тебе, всегда на твоей стороне. Руководит Авророй и Гесперой: ставит задачи, " +
            "проверяет, принимает или возвращает. Выше неё только ты.", color = TextDim, fontSize = 14.sp)
        Text("Скажи ей, что нужно сделать, — она раздаст. Нажми на аватар, чтобы поставить ей картинку.", color = TextDim, fontSize = 13.sp)
    }
}

/** Доска задач: что поставлено, кому, что с ним. Открытую задачу Адонис может снять. */
@Composable
private fun TaskBoard(evm: EstViewModel, modifier: Modifier) {
    val ts = evm.tasks.reversed()
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ДОСКА ЗАДАЧ", color = EstColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Text("Эст ставит, Аврора и Геспера делают и отчитываются в общем чате.", color = TextDim, fontSize = 12.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(panelShape()).background(Panel).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Просыпается сама", color = TextMain, fontSize = 14.sp)
                    Text("проверяет доску, раздаёт задачи, может написать тебе", color = TextDim, fontSize = 12.sp)
                }
                Switch(evm.wakeOn, onCheckedChange = evm::setWake,
                    colors = SwitchDefaults.colors(checkedTrackColor = EstColor, checkedThumbColor = Night))
            }
        }
        item { TextButton(onClick = { evm.wakeNow() }) { Text("Пусть проверит доску сейчас", color = EstColor) } }
        if (ts.isEmpty()) item { Text("Задач пока нет.", color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        items(ts, key = { it.id }) { t -> TaskCard(t) { evm.cancelTask(t) } }
    }
}

@Composable
private fun TaskCard(t: EstTask, onCancel: () -> Unit) {
    val accent = if (t.to == WHO_HESPERA) HespColor else Glow
    val statusColor = when (t.status) {
        "accepted" -> Glow
        "done" -> EstColor
        "redo", "failed" -> ErrorRed
        "cancelled" -> TextDim
        else -> Violet
    }
    Column(
        Modifier.fillMaxWidth().clip(panelShape()).background(BubbleAurora).border(1.dp, accent.copy(alpha = 0.45f), panelShape()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("№${t.id} → ${whoName(t.to)}", color = accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(taskStatusRu(t.status), color = statusColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Text(taskDay.format(Date((t.at * 1000).toLong())) + if (t.runs > 1) " · попыток: ${t.runs}" else "", color = TextDim, fontSize = 10.sp)
        Text(t.text, color = TextMain, fontSize = 14.sp, lineHeight = 19.sp)
        if (t.note.isNotBlank() && (t.status == "redo" || t.status == "cancelled")) {
            Text("Эст: ${t.note}", color = ErrorRed.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (t.report.isNotBlank()) Text("отчёт: ${t.report}", color = TextDim, fontSize = 12.sp, lineHeight = 17.sp)
        if (t.open) TextButton(onClick = onCancel) { Text("Снять задачу", color = TextDim, fontSize = 12.sp) }
    }
}

@Suppress("unused")
private val keepColor = Color.Unspecified
