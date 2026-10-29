package ru.avrora.exe

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

/** Стикер или фото из альбома, прикреплённые к черновику. Уходят вместе с текстом по кнопке отправки. */
private data class Pending(val tag: String, val model: Any, val isSticker: Boolean)

/** Ответ или пересылка в черновике: маркер уходит в начало сообщения, превью видно над полем ввода. */
private data class QuoteDraft(val forward: Boolean, val who: String, val preview: String, val marker: String)

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel(), hvm: HesperaViewModel = viewModel()) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var input by rememberSaveable { mutableStateOf("") }
    var screen by remember { mutableStateOf(if (vm.token.isBlank()) Screen.Connection else Screen.Chat) }
    var showPanel by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.Feed) }
    var booted by rememberSaveable { mutableStateOf(!vm.bootScreenOn) }
    var widgets by remember { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }
    var glitch by remember { mutableStateOf(0) }
    var glitchSeed by remember { mutableStateOf(0) }
    var viewer by remember { mutableStateOf<Any?>(null) }
    var pendingSnap by remember { mutableStateOf<Uri?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var quoteDraft by remember { mutableStateOf<QuoteDraft?>(null) }
    var addingSticker by remember { mutableStateOf(true) }
    var addUri by remember { mutableStateOf<Uri?>(null) }
    var toDelete by remember { mutableStateOf<CatalogItem?>(null) }
    var hespProfile by remember { mutableStateOf(false) }
    var hespFocused by remember { mutableStateOf(false) }

    val pickSnap = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingSnap = uri
            pending = null
        }
    }
    val pickForLibrary = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addUri = uri
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    LaunchedEffect(vm.messages.size, vm.sending) {
        val count = vm.messages.size + if (vm.sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(vm.notice) {
        val n = vm.notice
        if (n != null) {
            Toast.makeText(ctx, n, Toast.LENGTH_SHORT).show()
            vm.clearNotice()
        }
    }

    LaunchedEffect(tab, screen) {
        if (vm.glitchOn) {
            glitchSeed++
            for (i in 6 downTo 1) {
                glitch = i
                delay(45)
            }
            glitch = 0
        }
    }
    LaunchedEffect(vm.draftInsert) {
        val ins = vm.draftInsert
        if (ins != null) {
            input = if (input.isBlank()) ins else input.trimEnd() + " " + ins
            tab = Tab.Chat
            screen = Screen.Chat
            vm.draftInsert = null
        }
    }
    LaunchedEffect(vm.openRequest) {
        when (vm.openRequest) {
            "alarms" -> { booted = true; screen = Screen.Alarms }
            "chat" -> { booted = true; tab = Tab.Chat; screen = Screen.Chat; hvm.selectChat(WHO_AURORA) }
            "hespera" -> { booted = true; tab = Tab.Chat; screen = Screen.Chat; hvm.selectChat(WHO_HESPERA) }
            "group" -> { booted = true; tab = Tab.Chat; screen = Screen.Chat; hvm.selectChat(WHO_GROUP) }
        }
        vm.openRequest = null
    }
    LaunchedEffect(vm.openGameRequest) {
        if (vm.openGameRequest) {
            screen = Screen.Game
            vm.openGameRequest = false
        }
    }
    BackHandler(enabled = tab != Tab.Feed) { tab = Tab.Feed }
    BackHandler(enabled = showPanel) { showPanel = false }
    BackHandler(enabled = screen != Screen.Chat) {
        screen = if (screen == Screen.Menu || screen == Screen.Game) Screen.Chat else Screen.Menu
    }
    BackHandler(enabled = widgets) { widgets = false }
    BackHandler(enabled = hespProfile) { hespProfile = false }

    val canSend = !vm.sending && (input.isNotBlank() || pendingSnap != null || pending != null || quoteDraft != null)
    fun submit() {
        val snap = pendingSnap
        val att = pending
        val q = quoteDraft?.marker
        val out = when {
            q == null -> input
            input.isBlank() -> q
            else -> q + "\n" + input.trim()
        }
        when {
            snap != null -> vm.sendSnap(snap, out)
            att != null -> vm.sendWithTag(out, att.tag)
            else -> vm.send(out)
        }
        input = ""
        pendingSnap = null
        pending = null
        quoteDraft = null
    }
    fun startQuote(m: ChatMessage, forward: Boolean) {
        val who = if (m.role == "user") "адонис" else "аврора"
        quoteDraft = QuoteDraft(forward, who, quoteSnippet(m.content, 160), buildQuote(forward, who, m.content))
    }

    Box(Modifier.fillMaxSize().background(Night)) {
      ThemeBackdrop()
      Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().glitchShift(glitch, glitchSeed)) {
          when (tab) {
            Tab.Feed -> FeedScreen(vm) { widgets = true }
            Tab.Profile -> ProfileTabScreen(vm, hvm) { widgets = true }
            Tab.Gallery -> Column(Modifier.fillMaxSize()) {
                TabTopBar("ГАЛЕРЕЯ") { widgets = true }
                LibraryPanel(
                    stickers = vm.stickers,
                    photos = vm.photos,
                    onSticker = { viewer = it.model },
                    onPhoto = { viewer = it.model },
                    onAddSticker = {
                        addingSticker = true
                        pickForLibrary.launch(imageOnly)
                    },
                    onAddPhoto = {
                        addingSticker = false
                        pickForLibrary.launch(imageOnly)
                    },
                    onDelete = { toDelete = it },
                    fill = true
                )
            }
            Tab.Chat -> Column(Modifier.fillMaxSize()) {
            ChatSwitcher(hvm.chatWho) { hvm.selectChat(it) }
            if (hvm.chatWho == WHO_HESPERA) {
                HesperaChatPane(hvm, onProfile = { hespProfile = true }, onFocus = { hespFocused = it }, onOpen = { viewer = it })
                return@Column
            }
            if (hvm.chatWho == WHO_GROUP) {
                GroupChatPane(hvm, vm, onFocus = { hespFocused = it }, onOpen = { viewer = it })
                return@Column
            }
            Header(
                sending = vm.sending,
                state = vm.exeStates.lastOrNull(),
                pendingPlans = vm.exePlans.count { it.status == "pending" },
                status = vm.herProfile.status,
                onProfile = { tab = Tab.Profile },
                onSettings = { widgets = true }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (vm.messages.isEmpty() && !vm.sending) item { EmptyState() }
                itemsIndexed(vm.messages) { i, m ->
                    val showAvatar = m.role != "user" && (i == 0 || vm.messages[i - 1].role == "user")
                    MessageRow(
                        m = m,
                        showAvatar = showAvatar,
                        stickers = vm.stickers,
                        photos = vm.photos,
                        sentDir = vm.library.sentDir,
                        revealing = m.time == vm.revealTime,
                        onOpen = { viewer = it },
                        onReply = { startQuote(it, false) },
                        onForward = { startQuote(it, true) },
                        lookupQuoted = { snippet ->
                            findQuoted(snippet, vm.messages.take(i).map { Pair(it.role, it.content) })
                        },
                        lookupQuoteN = { n -> vm.quotes.firstOrNull { it.n == n } },
                        onSaveQuote = { vm.saveQuoteFromMessage(it) },
                        inviteStatus = { vm.inviteStatus(it) },
                        onGame = { msg, action ->
                            when (action) {
                                "accept" -> vm.answerHerInvite(msg, true)
                                "decline" -> vm.answerHerInvite(msg, false)
                                else -> screen = Screen.Game
                            }
                        }
                    )
                }
                if (vm.sending) item { TypingRow() }
            }

            vm.error?.let { err ->
                Row(
                    Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::retry) { Text("Повторить") }
                }
            }

            quoteDraft?.let { qd ->
                Row(
                    Modifier.fillMaxWidth().background(Panel).padding(start = 12.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).height(36.dp).background(if (qd.who == "адонис") Violet else Glow))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (qd.forward) "Пересылка · " else "Ответ · ") + (if (qd.who == "адонис") "Адонис" else "Аврора"),
                            color = if (qd.who == "адонис") Violet else Glow,
                            fontSize = 11.sp
                        )
                        Text(qd.preview, color = TextDim, fontSize = 13.sp, maxLines = 2)
                    }
                    IconButton(onClick = { quoteDraft = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Убрать цитату", tint = TextDim)
                    }
                }
            }

            val chipModel: Any? = pendingSnap ?: pending?.model
            if (chipModel != null) {
                val isSticker = pending?.isSticker == true
                Row(
                    Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = chipModel,
                        contentDescription = null,
                        contentScale = if (isSticker) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isSticker) "Стикер прикреплён. Напиши сообщение или сразу отправь."
                        else "Фото прикреплено. Можно добавить подпись.",
                        color = TextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { pendingSnap = null; pending = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Убрать вложение", tint = TextDim)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { pickSnap.launch(imageOnly) }) {
                    Icon(Icons.Default.Add, contentDescription = "Отправить фото", tint = TextDim)
                }
                IconButton(onClick = {
                    showPanel = !showPanel
                    if (showPanel) keyboard?.hide()
                }) {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = "Стикеры и альбом",
                        tint = if (showPanel) Glow else TextDim
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            inputFocused = it.isFocused
                            if (it.isFocused) showPanel = false
                        },
                    placeholder = { Text("Сообщение") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    colors = auroraFieldColors()
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Glow else Edge)
                        .clickable(enabled = canSend) { submit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (canSend) Night else TextDim,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (showPanel) {
                LibraryPanel(
                    stickers = vm.stickers,
                    photos = vm.photos,
                    onSticker = {
                        pending = Pending("[стикер:${it.id}]", it.model, true)
                        pendingSnap = null
                        showPanel = false
                    },
                    onPhoto = {
                        pending = Pending("[фото:${it.id}]", it.model, false)
                        pendingSnap = null
                        showPanel = false
                    },
                    onAddSticker = {
                        addingSticker = true
                        pickForLibrary.launch(imageOnly)
                    },
                    onAddPhoto = {
                        addingSticker = false
                        pickForLibrary.launch(imageOnly)
                    },
                    onDelete = { toDelete = it }
                )
            }
            }
          }
        }
        if (!(tab == Tab.Chat && (if (hvm.chatWho == WHO_AURORA) inputFocused else hespFocused))) BottomBar(tab) { tab = it }
      }

        WidgetsDrawer(vm, widgets, onClose = { widgets = false }) { widgets = false; screen = it }
        GlitchOverlay(glitch, glitchSeed)

        if (screen != Screen.Chat) {
            SettingsHost(vm = vm, screen = screen, onNavigate = { screen = it })
        }
        if (hespProfile) HesperaProfileScreen(hvm, vm.hand) { hespProfile = false }
        BootOverlay(vm, visible = !booted) { booted = true }
    }

    val newUri = addUri
    if (newUri != null) {
        AddItemDialog(
            isSticker = addingSticker,
            uri = newUri,
            onConfirm = { desc, cut ->
                if (addingSticker) vm.addSticker(newUri, desc, cut) else vm.addPhoto(newUri, desc)
                addUri = null
            },
            onDismiss = { addUri = null }
        )
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Panel,
            title = { Text("Удалить?") },
            text = { Text(item.desc) },
            confirmButton = {
                TextButton(onClick = { vm.removeItem(item); toDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }

    viewer?.let { model ->
        Dialog(
            onDismissRequest = { viewer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xF2000000))
                    .clickable { viewer = null }
            ) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
                // любую картинку из чата можно сохранить себе: нарисованную, присланную, из её альбома
                val vctx = LocalContext.current
                TextButton(
                    onClick = {
                        val ok = MediaCache.saveImage(vctx, model)
                        android.widget.Toast.makeText(vctx, if (ok) "Сохранено в галерею: Pictures/AVRORA" else "Не получилось сохранить", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                        .background(Color(0x99000000), RoundedCornerShape(20.dp))
                ) { Text("↓  Скачать", color = Color.White, fontSize = 15.sp) }
            }
        }
    }
}

/** Шапка-консоль: имя системы и её собственное состояние с причиной, если она его отметила. */
@Composable
private fun Header(sending: Boolean, state: StateEntry?, pendingPlans: Int, status: String, onProfile: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clickable { onProfile() }.cornerBrackets(Glow, 6.dp, 1.2.dp).padding(3.dp)) { Avatar(36.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { onProfile() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AVRORA.EXE", color = Glow, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(if (sending) "● обработка" else "● в сети", color = if (sending) Violet else TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            if (status.isNotBlank()) {
                Text("«$status»", color = TextMain, fontSize = 12.sp, maxLines = 1)
            }
            Text(
                if (state != null) "состояние: ${state.name}" + (if (state.why.isNotBlank()) " — ${state.why}" else "")
                else "состояние: не отмечено",
                color = TextDim,
                fontSize = 11.sp,
                maxLines = 2,
                fontFamily = FontFamily.Monospace
            )
            if (pendingPlans > 0) {
                Text("назначено сообщений: $pendingPlans", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = TextDim)
        }
    }
}
