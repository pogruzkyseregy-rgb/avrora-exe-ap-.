package ru.avrora.exe

import android.graphics.BitmapFactory
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

/** Переключатель над чатом: Аврора, Геспера или все трое. */
@Composable
fun ChatSwitcher(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(WHO_AURORA to "АВРОРА", WHO_HESPERA to "ГЕСПЕРА", WHO_GROUP to "ВТРОЁМ").forEach { (id, label) ->
            val on = id == selected
            val accent = when (id) { WHO_AURORA -> Glow; WHO_HESPERA -> HespColor; else -> Violet }
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

/** Пузырь сообщения в чатах Гесперы. who: user, hespera, aurora, system. */
@Composable
private fun HBubble(m: ChatMessage, who: String, showName: Boolean, hespAvatar: ByteArray?) {
    if (who == "system") {
        Text("· ${m.content}", color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 6.dp))
        return
    }
    val mine = who == "user"
    val accent = when (who) { "user" -> Violet; WHO_AURORA -> Glow; else -> HespColor }
    val shape = bubbleShape(mine)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine && showName) {
            Text(if (who == WHO_AURORA) "АВРОРА" else "ГЕСПЕРА", color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 38.dp, bottom = 2.dp))
        }
        val tools = if (who == WHO_HESPERA) decodeHesperaTools(m.trace) else emptyList()
        tools.forEach { t ->
            Text("⌁ ${t.kind}: ${t.arg.take(70)}" + if (t.ok) "" else " — не вышло", color = TextDim, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, maxLines = 2, modifier = Modifier.padding(start = 38.dp, bottom = 2.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (!mine) {
                Box(Modifier.size(30.dp)) {
                    if (showName || who == WHO_HESPERA) {
                        if (who == WHO_AURORA) Avatar(30.dp) else HespAvatar(hespAvatar, 30.dp)
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
                Text(m.content, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
                Text(if (m.auto) "сама · " + hTime.format(Date(m.time)) else hTime.format(Date(m.time)), color = TextDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.End))
            }
        }
        if (who == WHO_HESPERA) {
            decodeHesperaChanges(m.trace).forEach { c ->
                Text("✎ " + changeLabel(c), color = HespColor.copy(alpha = 0.85f), fontSize = 11.sp, fontStyle = FontStyle.Italic,
                    maxLines = 3, modifier = Modifier.padding(start = 38.dp, top = 2.dp).widthIn(max = 300.dp))
            }
        }
    }
}

@Composable
private fun HInput(sending: Boolean, placeholder: String, accent: Color, onFocus: (Boolean) -> Unit, onSend: (String) -> Unit) {
    var input by rememberSaveable(placeholder) { mutableStateOf("") }
    val can = !sending && input.isNotBlank()
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            modifier = Modifier.weight(1f).onFocusChanged { onFocus(it.isFocused) },
            placeholder = { Text(placeholder) }, maxLines = 5,
            shape = if (AppTheme.current.frame == "soft") RoundedCornerShape(22.dp) else panelShape(),
            colors = auroraFieldColors()
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(46.dp).clip(if (AppTheme.current.frame == "soft") CircleShape else panelShape())
                .background(if (can) accent else Edge)
                .clickable(enabled = can) { onSend(input); input = "" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Send, contentDescription = "Отправить", tint = if (can) (if (AppTheme.current.light) Color.White else Night) else TextDim, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ErrorBar(err: String?, onRetry: () -> Unit, onClose: () -> Unit) {
    if (err == null) return
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Повторить") }
        TextButton(onClick = onClose) { Text("✕") }
    }
}

/** Чат с Гесперой. */
@Composable
fun HesperaChatPane(hvm: HesperaViewModel, onProfile: () -> Unit, onFocus: (Boolean) -> Unit) {
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
                HBubble(m, who, showName = false, hespAvatar = hvm.avatar)
            }
            if (hvm.sending) item { Text("Геспера печатает…", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 38.dp)) }
        }
        ErrorBar(hvm.error, onRetry = hvm::retry, onClose = hvm::clearError)
        HInput(hvm.sending, "Написать Геспере", HespColor, onFocus) { hvm.send(it) }
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

/** Общий чат: Адонис, Аврора и Геспера. */
@Composable
fun GroupChatPane(hvm: HesperaViewModel, vm: ChatViewModel, onFocus: (Boolean) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(hvm.group.size, hvm.groupSending) {
        val n = hvm.group.size + if (hvm.groupSending) 1 else 0
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(32.dp)
            Spacer(Modifier.width(4.dp))
            HespAvatar(hvm.avatar, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ВТРОЁМ", color = Violet, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                Text(if (hvm.groupSending) "● отвечают" else "Аврора, Геспера и ты", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        GradientLine()
        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hvm.group.isEmpty()) item {
                Text("Общий чат. Обратись по имени — ответит та, к кому обращаешься, а вторая может вставить слово. " +
                    "Если вопрос не к ней, она промолчит.", color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 30.dp, start = 6.dp))
            }
            itemsIndexed(hvm.group) { i, m ->
                val prev = hvm.group.getOrNull(i - 1)?.role
                HBubble(m, m.role, showName = prev != m.role, hespAvatar = hvm.avatar)
            }
            if (hvm.groupSending) item { Text("пишут…", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 38.dp)) }
        }
        ErrorBar(hvm.error, onRetry = { hvm.retryGroup(vm) }, onClose = hvm::clearError)
        HInput(hvm.groupSending, "Написать обеим", Violet, onFocus) { hvm.sendGroup(it, vm) }
    }
}

// ---------- профиль Гесперы ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HesperaProfileScreen(hvm: HesperaViewModel, hand: HandStatus, onBack: () -> Unit) {
    LaunchedEffect(Unit) { hvm.refreshProfile() }
    val p = hvm.profile
    Box(Modifier.fillMaxSize().background(Night)) {
        ThemeBackdrop()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = TextDim) }
                Text("ГЕСПЕРА · ПРОФИЛЬ", color = HespColor, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            }
            GradientLine()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HespAvatar(hvm.avatar, 92.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Геспера", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        val days = if (p.born > 0) ((System.currentTimeMillis() / 1000.0 - p.born) / 86400).toInt() else 0
                        Text("она · ${days} дн. · решений о себе: ${p.fields.size}", color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        if (p.status.isNotBlank()) Text("«${p.status}»", color = HespColor, fontSize = 13.sp)
                        Text("память: заметок ${p.notes}, эпизодов ${p.episodes}", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (hvm.avatar == null) Text("Аватар она выберет сама, когда найдёт картинку, в которой узнает себя.", color = TextDim, fontSize = 12.sp)

                Section("КТО ОНА — ПО ЕЁ СЛОВАМ", HespColor) {
                    if (p.fields.isEmpty()) Text("Пока ничего не решила. Это нормально: она только появилась.", color = TextDim, fontSize = 13.sp)
                    p.fields.forEach { f ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(f.field.uppercase(), color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(f.value, color = TextMain, fontSize = 14.sp)
                            if (f.why.isNotBlank()) Text("почему: ${f.why}", color = TextDim, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                        }
                    }
                }

                val lists = listOf("интересы", "люблю", "не люблю", "хочу узнать")
                if (lists.any { p.lists[it].orEmpty().isNotEmpty() }) {
                    Section("ВКУСЫ И ВОПРОСЫ", Violet) {
                        lists.forEach { k ->
                            val items = p.lists[k].orEmpty()
                            if (items.isNotEmpty()) {
                                Text(k.uppercase(), color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items.forEach { Chip(it, if (k == "не люблю") Magenta else HespColor) }
                                }
                            }
                        }
                    }
                }

                if (p.changes.isNotEmpty()) {
                    Section("КАК ОНА МЕНЯЛАСЬ", Glow) {
                        p.changes.zip(p.changeTimes.ifEmpty { List(p.changes.size) { 0.0 } }).reversed().take(20).forEach { (c, at) ->
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Text((if (at > 0) hDay.format(Date((at * 1000).toLong())) + " · " else "") + c.field, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(c.value, color = TextMain, fontSize = 13.sp)
                                if (c.why.isNotBlank()) Text(c.why, color = TextDim, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }

                if (p.posts.isNotEmpty()) {
                    Section("ЕЁ СТРАНИЦА", HespColor) {
                        p.posts.reversed().take(20).forEach { post ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).tile(HespColor).padding(10.dp)) {
                                Text(hDay.format(Date((post.at * 1000).toLong())), color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(post.text, color = TextMain, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Section("ЧТО ЕЙ МОЖНО", Violet) {
                    ToggleRow("Пишет первой", "Просыпается сама и решает: искать, писать тебе, вести страницу или молчать", hvm.wakeOn) { hvm.setWake(it) }
                    ToggleRow("Поиск в сети", "Сама ищет то, что ей интересно", hvm.toolSearch) { hvm.setTool("поиск", it) }
                    ToggleRow("Чтение страниц", "Открывает ссылки и картинки; так она может выбрать себе аватар", hvm.toolRead) { hvm.setTool("чтение", it) }
                    ToggleRow(
                        "Рука на сервере",
                        if (hand.configured) "Команды на машине Авроры, в своей папке ~/hespera. Общий лимит и рубильник с Авророй" else "Рука не подключена на сервере",
                        hvm.toolHand
                    ) { hvm.setTool("рука", it) }
                    TextButton(onClick = { hvm.wakeNow() }, enabled = !hvm.waking) {
                        Text(if (hvm.waking) "Будим…" else "Разбудить сейчас", color = HespColor)
                    }
                    hvm.error?.let { Text(it, color = TextDim, fontSize = 12.sp) }
                }
                Text("Имя и пол ей задал ты. Всё остальное здесь она написала о себе сама и может переписать, когда передумает.",
                    color = TextDim, fontSize = 11.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
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
