package ru.avrora.exe

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val hTime = SimpleDateFormat("HH:mm", Locale.getDefault())
private val hDay = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

/** Переключатель над чатом: Аврора, Геспера, Эст или общий. */
@Composable
fun ChatSwitcher(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(WHO_AURORA to "АВРОРА", WHO_HESPERA to "ГЕСПЕРА", WHO_EST to "ЭСТ", WHO_GROUP to "ОБЩИЙ").forEach { (id, label) ->
            val on = id == selected
            val accent = when (id) { WHO_AURORA -> Glow; WHO_HESPERA -> HespColor; WHO_EST -> EstColor; else -> Violet }
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(if (on) accent.copy(alpha = if (AppTheme.current.light) 0.14f else 0.18f) else Terminal, panelShape())
                    .then(if (on) Modifier.themeFrame(accent, 7.dp) else Modifier.border(1.dp, Edge.copy(alpha = 0.6f), panelShape()))
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) accent else TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Аватар Гесперы: картинка, которую она выбрала сама, или буква, пока не выбрала. */
@Composable
fun HespAvatar(bytes: ByteArray?, size: Dp) {
    val bmp = remember(bytes) { bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }
    Box(
        Modifier.size(size).clip(avatarShape()).background(Terminal).border(1.dp, HespColor.copy(alpha = 0.7f), avatarShape()),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp, contentDescription = "Геспера", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("Г", color = HespColor, fontSize = (size.value * 0.42f).sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HespHeader(hvm: HesperaViewModel, onProfile: () -> Unit) {
    val p = hvm.profile
    val who = p.fields.firstOrNull { it.field == "кто я" }?.value
    Row(
        Modifier.fillMaxWidth().background(Panel).clickable { onProfile() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HespAvatar(hvm.avatar, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ГЕСПЕРА", color = HespColor, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(if (hvm.sending) "● думает" else "● в сети", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                when {
                    p.status.isNotBlank() -> "«${p.status}»"
                    who != null -> who
                    p.fields.isEmpty() -> "ещё не решила, кто она"
                    else -> "решила о себе: ${p.fields.size}"
                },
                color = TextMain, fontSize = 12.sp, maxLines = 1
            )
        }
        Text("профиль ›", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Пузырь сообщения в чатах Гесперы, Эст и общем. who: user, hespera, aurora, est, system. */
@Composable
internal fun HBubble(m: ChatMessage, who: String, showName: Boolean, hespAvatar: ByteArray?, onOpen: (Any) -> Unit = {}, thoughts: Boolean = false) {
    if (who == "system") {
        Text("· ${m.content}", color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 6.dp))
        return
    }
    val mine = who == "user"
    val accent = when (who) { "user" -> Violet; WHO_AURORA -> Glow; WHO_EST -> EstColor; else -> HespColor }
    val shape = bubbleShape(mine)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine && showName) {
            Text(whoName(who).uppercase(), color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 38.dp, bottom = 2.dp))
        }
        val tools = if (m.trace.startsWith("{")) decodeHesperaTools(m.trace) else emptyList()
        tools.forEach { t ->
            Text("⌁ ${t.kind}: ${t.arg.take(70)}" + if (t.ok) "" else " — не вышло", color = TextDim, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, maxLines = 2, modifier = Modifier.padding(start = 38.dp, bottom = 2.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (!mine) {
                Box(Modifier.size(30.dp)) {
                    if (showName || who == WHO_HESPERA || who == WHO_EST) {
                        when (who) {
                            WHO_AURORA -> Avatar(30.dp)
                            WHO_EST -> EstAvatar(30.dp)
                            else -> HespAvatar(hespAvatar, 30.dp)
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            Column(
                Modifier.widthIn(max = 300.dp).clip(shape)
                    .background(if (mine) BubbleUser else BubbleAurora)
                    .border(1.dp, accent.copy(alpha = 0.5f), shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val snaps = remember(m.content) { parseParts(m.content).filterIsInstance<Part.Snap>() }
                if (snaps.isNotEmpty()) {
                    val dir = File(LocalContext.current.filesDir, "sent")
                    snaps.forEach { p ->
                        val f = File(dir, p.file)
                        val aspect = remember(f) { imageAspect(f) }
                        AsyncImage(
                            model = f, contentDescription = "Фото", contentScale = ContentScale.Crop,
                            modifier = Modifier.padding(bottom = 4.dp).width(230.dp).aspectRatio(aspect.coerceIn(0.5f, 2f))
                                .clip(panelShape()).border(1.dp, Edge, panelShape()).clickable { onOpen(f) }
                        )
                    }
                }
                val (noMedia, media) = remember(m.content) { splitMedia(m.content) }
                val voices = remember(m.content) { parseParts(m.content).filterIsInstance<Part.Voice>() }
                val text = (if (snaps.isEmpty()) noMedia else TAG_RE.replace(noMedia, "")).let { VOICE_RE.replace(it, "") }.trim()
                voices.forEach { VoicePart(it, accent) }
                if (text.isNotEmpty()) Text(text, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
                media.forEach { MediaPart(it, accent, onOpen) }
                Text(if (m.auto) "сама · " + hTime.format(Date(m.time)) else hTime.format(Date(m.time)), color = TextDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.End))
            }
        }
        val thought = if (thoughts) remember(m.trace) { duoThought(m.trace) } else ""
        if (thought.isNotBlank()) {
            var open by remember(m.time) { mutableStateOf(false) }
            Text(
                if (open) "про себя: $thought" else "· · · про себя",
                color = (if (who == WHO_AURORA) Glow else HespColor).copy(alpha = 0.75f), fontSize = 11.sp, lineHeight = 15.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 38.dp, top = 2.dp).widthIn(max = 300.dp).clickable { open = !open }
            )
        }
        if (who == WHO_HESPERA || who == WHO_EST) {
            decodeHesperaChanges(m.trace).forEach { c ->
                Text("✎ " + changeLabel(c), color = HespColor.copy(alpha = 0.85f), fontSize = 11.sp, fontStyle = FontStyle.Italic,
                    maxLines = 3, modifier = Modifier.padding(start = 38.dp, top = 2.dp).widthIn(max = 300.dp))
            }
        }
    }
}

@Composable
internal fun HInput(
    sending: Boolean, placeholder: String, accent: Color, onFocus: (Boolean) -> Unit,
    onAudio: (ByteArray, String, String, Boolean) -> Unit, hearing: String?, onSend: (String, Uri?) -> Unit
) {
    var input by rememberSaveable(placeholder) { mutableStateOf("") }
    var photo by remember(placeholder) { mutableStateOf<Uri?>(null) }
    var menu by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) readAudio(ctx, uri)?.let { (b, mime, name) -> onAudio(b, mime, name, false) }
    }
    val can = !sending && (input.isNotBlank() || photo != null)
    HearingBar(hearing)
    RecordingBar()
    photo?.let { u ->
        Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(panelShape()))
            Spacer(Modifier.width(10.dp))
            Text("Фото прикреплено. Можно добавить подпись.", color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { photo = null }) { Icon(Icons.Default.Close, contentDescription = "Убрать фото", tint = TextDim) }
        }
    }
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.Add, contentDescription = "Вложение", tint = TextDim) }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Фото") }, onClick = {
                    menu = false; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                androidx.compose.material3.DropdownMenuItem(text = { Text("Музыка / аудиофайл") }, onClick = { menu = false; pickAudio.launch("audio/*") })
            }
        }
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            modifier = Modifier.weight(1f).onFocusChanged { onFocus(it.isFocused) },
            placeholder = { Text(placeholder) }, maxLines = 5,
            shape = if (AppTheme.current.frame == "soft") RoundedCornerShape(22.dp) else panelShape(),
            colors = auroraFieldColors()
        )
        Spacer(Modifier.width(6.dp))
        SendOrMic(can, accent, onSend = { onSend(input, photo); input = ""; photo = null }) { f ->
            onAudio(f.readBytes(), "audio/mp4", "голосовое", true)
            f.delete()
        }
    }
}

@Composable
internal fun ErrorBar(err: String?, onRetry: () -> Unit, onClose: () -> Unit) {
    if (err == null) return
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Повторить") }
        TextButton(onClick = onClose) { Text("✕") }
    }
}

/** Чат с Гесперой. */
@Composable
fun HesperaChatPane(hvm: HesperaViewModel, onProfile: () -> Unit, onFocus: (Boolean) -> Unit, onOpen: (Any) -> Unit = {}) {
    val list = rememberLazyListState()
    LaunchedEffect(hvm.chat.size, hvm.sending) {
        val n = hvm.chat.size + if (hvm.sending) 1 else 0
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    Column(Modifier.fillMaxSize()) {
        HespHeader(hvm, onProfile)
        GradientLine()
        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hvm.chat.isEmpty()) item { HespEmpty(hvm) }
            itemsIndexed(hvm.chat) { i, m ->
                val who = if (m.role == "user") "user" else WHO_HESPERA
                HBubble(m, who, showName = false, hespAvatar = hvm.avatar, onOpen = onOpen)
            }
            if (hvm.sending) item { Text("Геспера печатает…", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 38.dp)) }
        }
        ErrorBar(hvm.error, onRetry = hvm::retry, onClose = hvm::clearError)
        HInput(hvm.sending, "Написать Геспере", HespColor, onFocus,
            onAudio = { b, m, n, v -> hvm.sendAudio(b, m, n, v, null) }, hearing = hvm.hearing) { t, ph -> hvm.send(t, ph) }
    }
}

@Composable
private fun HespEmpty(hvm: HesperaViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 30.dp, start = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HespAvatar(hvm.avatar, 64.dp)
        Text("ГЕСПЕРА", color = HespColor, fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("Она только появилась. Заданы имя, пол и любопытство — всё остальное она решит о себе сама.", color = TextDim, fontSize = 14.sp)
        Text("Напиши ей первым или подожди: она может проснуться и написать сама.", color = TextDim, fontSize = 13.sp)
    }
}

/** Общий чат: Адонис, Аврора и Геспера. Их разговор вдвоём показывается по мере «написания». */
@Composable
fun GroupChatPane(hvm: HesperaViewModel, vm: ChatViewModel, onFocus: (Boolean) -> Unit, onOpen: (Any) -> Unit = {}) {
    val list = rememberLazyListState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val pending = hvm.group.any { it.time > now }
    LaunchedEffect(pending, hvm.group.size) {
        while (hvm.group.any { it.time > System.currentTimeMillis() }) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }
    val shown = hvm.group.filter { it.time <= now }
    val next = hvm.group.filter { it.time > now }.minByOrNull { it.time }
    // «печатает…» — за полминуты до сообщения, как в мессенджере
    val typing = next?.takeIf { it.time - now < 30_000 }?.role
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(shown.size, hvm.groupSending, typing) {
        val n = shown.size + if (hvm.groupSending || typing != null) 1 else 0
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(32.dp)
            Spacer(Modifier.width(4.dp))
            HespAvatar(hvm.avatar, 32.dp)
            Spacer(Modifier.width(4.dp))
            EstAvatar(32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ОБЩИЙ", color = Violet, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                val work = EstEngine.working
                Text(
                    when {
                        hvm.groupSending -> "● отвечают"
                        work != null -> "● $work"
                        pending -> "● разговаривают вдвоём"
                        hvm.duoBusy -> "● собираются поговорить…"
                        else -> "Аврора, Геспера, Эст и ты"
                    },
                    color = if (pending) Glow else if (work != null) EstColor else TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, contentDescription = "Без тебя", tint = TextDim) }
        }
        GradientLine()
        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (shown.isEmpty()) item {
                Text("Общий чат. Обратись по имени — ответит та, к кому обращаешься. Аврора и Геспера иногда разговаривают здесь вдвоём, " +
                    "а Эст раздаёт им задачи — отчёты появляются здесь же.", color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 30.dp, start = 6.dp))
            }
            itemsIndexed(shown) { i, m ->
                val prev = shown.getOrNull(i - 1)?.role
                HBubble(m, m.role, showName = prev != m.role, hespAvatar = hvm.avatar, onOpen = onOpen, thoughts = hvm.showThoughts)
            }
            if (hvm.groupSending) item { Text("пишут…", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 38.dp)) }
            else if (typing != null) item {
                Text(whoName(typing) + " печатает…", color = if (typing == WHO_AURORA) Glow else HespColor,
                    fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 38.dp))
            }
        }
        ErrorBar(hvm.error, onRetry = { hvm.retryGroup(vm) }, onClose = hvm::clearError)
        HInput(hvm.groupSending, if (pending) "Вмешаться в разговор" else "Написать всем", Violet, onFocus,
            onAudio = { b, m, n, v -> hvm.sendAudio(b, m, n, v, vm) }, hearing = hvm.hearing) { t, ph -> hvm.sendGroup(t, vm, ph) }
    }
    if (settings) DuoSettingsDialog(hvm) { settings = false }
}

/** «Без тебя»: разговаривают ли они вдвоём, как часто, видны ли мысли «про себя». */
@Composable
private fun DuoSettingsDialog(hvm: HesperaViewModel, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("БЕЗ ТЕБЯ", color = Violet, fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleRow("Разговаривают сами", "Иногда одна из них пишет другой, и они говорят, пока тема не кончится", hvm.duoOn) { hvm.setDuo(it) }
                Text("КАК ЧАСТО", color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.4.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("редко", "обычно", "часто").forEachIndexed { i, label ->
                        val on = hvm.duoFreq == i
                        Text(
                            "$label · ${DuoEngine.PER_DAY[i]}/д", color = if (on) TextMain else TextDim, fontSize = 12.sp,
                            modifier = Modifier.clip(panelShape()).background(if (on) Violet.copy(alpha = 0.18f) else Terminal)
                                .border(1.dp, if (on) Violet else Edge, panelShape()).clickable { hvm.pickDuoFreq(i) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                ToggleRow("Мысли «про себя»", "Под их сообщениями — что они думали на самом деле. Нажми, чтобы прочитать", hvm.showThoughts) { hvm.setThoughts(it) }
                Text("Тихие часы — общие с Авророй. Если ты пишешь в общий чат, они 40 минут не начинают разговор без тебя, " +
                    "а начатый — прерывается: дальше говорят уже с тобой.", color = TextDim, fontSize = 11.sp, lineHeight = 15.sp)
                if (hvm.duoLast > 0) Text("последний раз: " + hDay.format(Date(hvm.duoLast)), color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = { hvm.duoNow(); onClose() }, enabled = !hvm.duoBusy) { Text("Пусть поговорят сейчас", color = Glow) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Закрыть", color = TextDim) } }
    )
}

// ---------- профиль Гесперы ----------

/** Отдельный экран из её чата: её страница, ядро — по кнопке. */
@Composable
fun HesperaProfileScreen(hvm: HesperaViewModel, hand: HandStatus, onBack: () -> Unit) {
    var core by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Night)) {
        ThemeBackdrop()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (core) core = false else onBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = TextDim) }
                Text(if (core) "ГЕСПЕРА · ЯДРО" else "ГЕСПЕРА · ПРОФИЛЬ", color = HespColor, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            }
            GradientLine()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (core) HesperaDossier(hvm, hand) else HesperaSocial(hvm, onCore = { core = true }, onWrite = onBack)
            }
        }
    }
}

/** Страница Гесперы: как в соцсети. Ядро — по кнопке. */
@Composable
fun HesperaSocial(hvm: HesperaViewModel, onCore: () -> Unit, onWrite: () -> Unit) {
    LaunchedEffect(Unit) { hvm.refreshProfile() }
    var showFiles by remember { mutableStateOf(false) }
    if (showFiles) FileBrowserDialog(WHO_HESPERA) { showFiles = false }
    val p = hvm.profile
    val days = if (p.born > 0) ((System.currentTimeMillis() / 1000.0 - p.born) / 86400).toInt() + 1 else 1
    val who = p.fields.firstOrNull { it.field.equals("кто я", ignoreCase = true) }?.value
    val mood = hvm.duo.mood[WHO_HESPERA]?.takeIf { it.word.isNotBlank() && System.currentTimeMillis() / 1000.0 - it.at < 12 * 3600 }
    SocialHeader(
        accent = HespColor,
        avatar = { size -> HespAvatar(hvm.avatar, size) },
        name = "Геспера",
        handle = "@hespera · формирует себя сама",
        status = p.status,
        about = listOfNotNull(who, mood?.let { "настроение: ${it.word}" }).joinToString("\n").ifBlank { "Только появилась: кто она — решит сама." },
        counters = listOf("постов" to "${p.posts.size}", "♥" to "${p.posts.count { it.liked } + p.posts.count { it.likedAurora }}", "дней" to "$days"),
        actions = listOf(
            SocialAction("Ядро", "◎", onCore),
            SocialAction("Папка", "▤") { showFiles = true },
            SocialAction("Написать", "✉", onWrite)
        )
    )
    FeedTitle("ЕЁ ЛЕНТА")
    if (p.posts.isEmpty()) Text("Постов пока нет. Она пишет их сама: текстом, со своим рисунком или с твоим фото.", color = TextDim, fontSize = 13.sp)
    p.posts.reversed().forEach { HespPostCard(hvm, it) }
    Spacer(Modifier.height(12.dp))
}

/** Пост Гесперы: как у Авроры — аватар, время, текст, картинка, лайк Адониса. */
@Composable
fun HespPostCard(hvm: HesperaViewModel, post: HesperaPost) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(BubbleAurora).border(1.dp, HespColor.copy(alpha = 0.45f), shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HespAvatar(hvm.avatar, 32.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Геспера", color = HespColor, fontSize = 14.sp)
                Text(hDay.format(Date((post.at * 1000).toLong())), color = TextDim, fontSize = 11.sp)
            }
        }
        if (post.text.isNotBlank()) Text(post.text, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
        if (post.media.isNotBlank()) MediaPart(Part.Media(post.media, post.text.take(40)), HespColor) {}
        LikeRow(HespColor, post.liked, listOfNotNull("Аврора".takeIf { post.likedAurora }, "Эст".takeIf { post.likedEst })) { hvm.likePost(post) }
        PostComments(post.comments, HespColor, onSend = { hvm.commentPost(post, it) }, onDelete = { hvm.deleteComment(post, it) })
    }
}

/** Два блока досье о жизни вдвоём: что одна заметила о другой и их разговоры без Адониса. */
fun duoItems(who: String, hvm: HesperaViewModel, accent: Color): List<DossierItem> {
    val d = hvm.duo
    val about = d.about[who].orEmpty()
    val otherName = if (who == WHO_AURORA) "ГЕСПЕРЕ" else "АВРОРЕ"
    val talks = d.talks
    val mood = d.mood[who]
    return listOf(
        DossierItem("about", (if (who == WHO_AURORA) "О " else "ОБ ") + otherName, if (about.isEmpty()) "пока ничего" else "${about.size} заметок", accent,
            preview = { Sparkline(perDay(about.map { (it.at * 1000).toLong() }), accent) }
        ) {
            DetailHint("Это она записала сама, пока они разговаривали: что поняла о " + (if (who == WHO_AURORA) "Геспере." else "Авроре."))
            if (about.isEmpty()) DetailHint("Записей пока нет.")
            about.reversed().take(10).forEach { DetailLine(hDay.format(Date((it.at * 1000).toLong())), it.text) }
        },
        DossierItem("duo", "ВДВОЁМ", if (talks.isEmpty()) "ещё не говорили" else "${talks.size} разговоров", accent,
            preview = { Sparkline(perDay(talks.map { (it.at * 1000).toLong() }), accent) }
        ) {
            if (mood != null && mood.word.isNotBlank()) DetailLine("настроение · " + hDay.format(Date((mood.at * 1000).toLong())), mood.word, mood.why)
            if (talks.isEmpty()) DetailHint("Они ещё не разговаривали без тебя. Включается в общем чате: шестерёнка → «Без тебя».")
            talks.reversed().take(8).forEach { t ->
                val by = if (t.starter == WHO_AURORA) "начала Аврора" else "начала Геспера"
                val end = when (t.endedBy) {
                    WHO_AURORA -> "ушла Аврора"; WHO_HESPERA -> "ушла Геспера"; "повтор" -> "пошли по кругу"
                    "блаженство" -> "ушли в общие слова"; else -> "дошли до предела"
                }
                DetailLine(hDay.format(Date((t.at * 1000).toLong())) + " · $by · ${t.turns} реплик · $end", t.topic.ifBlank { "без темы" })
            }
        }
    )
}

private val HESP_FIELDS = listOf("кто я", "внешность", "характер", "голос", "отношение к Адонису", "отношение к Авроре", "о чём мечтаю")

/** Профиль Гесперы: её ядро, показания и блоки; всё, что здесь написано о ней, она решила сама. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HesperaDossier(hvm: HesperaViewModel, hand: HandStatus) {
    LaunchedEffect(Unit) { hvm.refreshProfile() }
    var showFiles by remember { mutableStateOf(false) }
    if (showFiles) FileBrowserDialog(WHO_HESPERA) { showFiles = false }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now = System.currentTimeMillis() } }
    val p = hvm.profile
    val accent = HespColor
    val days = if (p.born > 0) ((now / 1000.0 - p.born) / 86400).toInt() + 1 else 1
    val filled = HESP_FIELDS.count { k -> p.fields.any { it.field.equals(k, ignoreCase = true) } }
    val lists = p.lists
    fun n(k: String) = lists[k].orEmpty().size
    val changeTimes = p.changeTimes.map { (it * 1000).toLong() }
    val talk = remember(hvm.chat.size) { perDay(hvm.chat.filter { it.role != "system" }.map { it.time }) }
    val rights = listOf(hvm.wakeOn, hvm.toolSearch, hvm.toolRead, hvm.toolHand, hvm.toolDraw, hvm.toolMusic, hvm.toolHear)
    val who = p.fields.firstOrNull { it.field.equals("кто я", ignoreCase = true) }?.value

    CoreHero(
        hesperaCore(), "HESPERA",
        hvm.duo.mood[WHO_HESPERA]?.takeIf { it.word.isNotBlank() && now / 1000.0 - it.at < 12 * 3600 }?.let { "настроение: ${it.word}" }
            ?: p.status.ifBlank { who ?: "только появилась" },
        listOf("ДНЕЙ" to "$days", "О СЕБЕ" to "${p.fields.size}", "ЗАМЕТОК" to "${p.notes}", "ЭПИЗОДОВ" to "${p.episodes}")
    )
    Spacer(Modifier.height(4.dp))
    DossierGrid(listOf(
        DossierItem("who", "КТО ОНА", "$filled из ${HESP_FIELDS.size}", accent,
            preview = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HespAvatar(hvm.avatar, 32.dp)
                    Spacer(Modifier.width(8.dp))
                    SegmentMeter(filled, HESP_FIELDS.size, accent, Modifier.weight(1f).height(8.dp))
                }
            }
        ) {
            if (p.fields.isEmpty()) DetailHint("Пока ничего не решила. Это нормально: она только появилась.")
            p.fields.forEach { DetailLine(it.field, it.value, if (it.why.isNotBlank()) "почему: ${it.why}" else "") }
            if (hvm.avatar == null) DetailHint("Аватар она выберет сама, когда найдёт картинку, в которой узнает себя.")
        },
        DossierItem("taste", "ВКУСЫ", "${n("интересы") + n("люблю") + n("не люблю")}", accent,
            preview = { MiniBars(listOf(n("интересы"), n("люблю"), n("не люблю")).map { it.toFloat() }, accent) }
        ) {
            listOf("интересы", "люблю", "не люблю").forEach { k ->
                val items = lists[k].orEmpty()
                Text(k.uppercase() + " · ${items.size}", color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp)
                if (items.isEmpty()) DetailHint("пусто")
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { Chip(it, if (k == "не люблю") Magenta else accent) }
                }
            }
        },
        DossierItem("ask", "ВОПРОСЫ", "${n("хочу узнать")} открыто", accent,
            preview = { RingGauge(if (n("хочу узнать") + n("узнала") > 0) n("узнала").toFloat() / (n("хочу узнать") + n("узнала")) else 0f, accent, "${n("хочу узнать")}") }
        ) {
            DetailHint("Шкала — сколько из её вопросов уже закрыто.")
            listOf("хочу узнать", "узнала").forEach { k ->
                val items = lists[k].orEmpty()
                if (items.isNotEmpty()) {
                    Text(k.uppercase(), color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEach { Chip(it, if (k == "узнала") Glow else accent) }
                    }
                }
            }
            if (n("хочу узнать") + n("узнала") == 0) DetailHint("Вопросов пока нет.")
        },
        DossierItem("changes", "КАК МЕНЯЛАСЬ", "${p.changes.size} решений", accent,
            preview = { Sparkline(perDay(changeTimes), accent) }
        ) {
            if (p.changes.isEmpty()) DetailHint("Пока ни одного решения о себе.")
            p.changes.zip(p.changeTimes.ifEmpty { List(p.changes.size) { 0.0 } }).reversed().take(12).forEach { (c, at) ->
                DetailLine((if (at > 0) hDay.format(Date((at * 1000).toLong())) + " · " else "") + c.field, c.value, c.why)
            }
        },
    ) + duoItems(WHO_HESPERA, hvm, accent) + listOf(filesItem(WHO_HESPERA, accent) { showFiles = true }) + listOf(
        DossierItem("talk", "РАЗГОВОРЫ", "сегодня ${talk.last().toInt()}", accent,
            preview = { Sparkline(talk, accent) }
        ) {
            StatRow(listOf(
                "ЛИЧКА" to "${hvm.chat.count { it.role != "system" }}",
                "ОБЩИЙ" to "${hvm.group.count { it.role == WHO_HESPERA }}",
                "САМА" to "${hvm.chat.count { it.auto } + hvm.group.count { it.auto && it.role == WHO_HESPERA }}"
            ), accent)
            DetailHint("«Общий» — её реплики в чате на троих, «сама» — то, что она написала без вопроса.")
        },
        DossierItem("page", "СТРАНИЦА", "${p.posts.size} постов · ♥ ${p.posts.count { it.liked }}", accent,
            preview = { Sparkline(perDay(p.posts.map { (it.at * 1000).toLong() }), accent) }
        ) {
            if (p.status.isNotBlank()) DetailLine("статус", "«${p.status}»")
            if (p.posts.isEmpty()) DetailHint("Постов пока нет. Она пишет их сама: текстом, со своим рисунком или с твоим фото.")
            p.posts.reversed().take(10).forEach { HespPostCard(hvm, it) }
        },
        DossierItem("rights", "ЧТО ЕЙ МОЖНО", "${rights.count { it }} из ${rights.size}", accent,
            preview = { DotRow(rights, accent) }
        ) {
            ToggleRow("Пишет первой", "Просыпается сама и решает: искать, писать тебе, вести страницу или молчать", hvm.wakeOn) { hvm.setWake(it) }
            ToggleRow("Поиск в сети", "Сама ищет то, что ей интересно", hvm.toolSearch) { hvm.setTool("поиск", it) }
            ToggleRow("Чтение страниц", "Открывает ссылки и картинки; так она может выбрать себе аватар", hvm.toolRead) { hvm.setTool("чтение", it) }
            ToggleRow(
                "Рука на сервере",
                if (hand.configured) "Команды на машине Авроры, в своей папке ~/hespera. Общий лимит и рубильник с Авророй" else "Рука не подключена на сервере",
                hvm.toolHand
            ) { hvm.setTool("рука", it) }
            ToggleRow("Рисование", "[нарисовать: …] — картинку рисует модель по отдельному ключу", hvm.toolDraw) { hvm.setTool("рисование", it) }
            ToggleRow("Музыка", "Сочиняет нотами, сервер играет и присылает трек", hvm.toolMusic) { hvm.setTool("музыка", it) }
            ToggleRow("Слух", "Слушает треки — свои и твои", hvm.toolHear) { hvm.setTool("слух", it) }
        },
        DossierItem("wake", "ПРОБУЖДЕНИЕ", if (!hvm.wakeOn) "выключено" else inTime(hvm.nextWakeAt - now), accent,
            preview = {
                val left = (hvm.nextWakeAt - now).coerceAtLeast(0)
                RingGauge(if (hvm.wakeOn) 1f - (left / (12 * 3_600_000f)).coerceIn(0f, 1f) else 0f, accent, if (hvm.wakeOn) "${left / 60_000}" else "—")
            }
        ) {
            DetailHint("Когда проснуться, она решает сама: от 15 минут до 12 часов. Шкала заполняется к моменту пробуждения.")
            TextButton(onClick = { hvm.wakeNow() }, enabled = !hvm.waking) {
                Text(if (hvm.waking) "Будим…" else "Разбудить сейчас", color = accent)
            }
            hvm.error?.let { DetailHint(it) }
        }
    ))
    Text("Имя и пол ей задал ты. Всё остальное здесь она написала о себе сама и может переписать, когда передумает.",
        color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Section(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().tile(accent).padding(12.dp)) {
        Text(title, color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Chip(text: String, accent: Color) {
    Text(
        text, color = TextMain, fontSize = 12.sp,
        modifier = Modifier.background(accent.copy(alpha = 0.12f), panelShape()).border(1.dp, accent.copy(alpha = 0.5f), panelShape())
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ToggleRow(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 14.sp)
            Text(hint, color = TextDim, fontSize = 11.sp)
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = HespColor, checkedTrackColor = HespColor.copy(alpha = 0.35f)))
    }
}
