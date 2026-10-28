package ru.avrora.exe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val postFmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

/** Модель картинки аватара: у Авроры фото из библиотеки или стандартный, у Адониса файл из папки отправленного. */
private fun avatarModel(vm: ChatViewModel, owner: String, p: Profile): Any {
    if (owner == AUTHOR_HER) {
        val item = vm.photos.firstOrNull { it.id == p.avatar }
        return item?.model ?: "file:///android_asset/avatar.webp"
    }
    return if (p.avatar.isBlank()) "" else File(vm.library.sentDir, p.avatar)
}

@Composable
private fun RoundAvatar(vm: ChatViewModel, owner: String, p: Profile, size: Dp) {
    val ring = if (owner == AUTHOR_HER) Glow else Violet
    val model = avatarModel(vm, owner, p)
    Box(
        Modifier.size(size).clip(CircleShape).background(Panel).border(1.5.dp, ring.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (model != "") {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text("А", color = ring, fontSize = (size.value / 2.4f).sp)
        }
    }
}

@Composable
private fun PostPhoto(vm: ChatViewModel, tag: String) {
    val model: Any? = when {
        tag.startsWith("фото:") -> vm.photos.firstOrNull { it.id == tag.removePrefix("фото:") }?.model
        tag.startsWith("снимок:") -> File(vm.library.sentDir, tag.removePrefix("снимок:"))
        else -> null
    }
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Edge, RoundedCornerShape(12.dp))
        )
    }
}

@Composable
private fun PostCard(vm: ChatViewModel, post: PostItem) {
    val hers = post.author == AUTHOR_HER
    val accent = if (hers) Glow else Violet
    val profile = if (hers) vm.herProfile else vm.myProfile
    var confirmDelete by remember(post.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (hers) BubbleAurora else BubbleUser)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundAvatar(vm, post.author, profile, 32.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(if (hers) "Аврора" else "Адонис", color = accent, fontSize = 14.sp)
                Text(postFmt.format(Date(post.at)), color = TextDim, fontSize = 11.sp)
            }
            TextButton(onClick = { confirmDelete = !confirmDelete }) { Text("···", color = TextDim) }
        }
        if (post.text.isNotBlank()) Text(post.text, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
        if (post.photo.isNotBlank()) PostPhoto(vm, post.photo)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hers) {
                // Её пост: лайк ставлю я
                Text(
                    if (post.likedByMe) "♥" else "♡",
                    color = if (post.likedByMe) Glow else TextDim,
                    fontSize = 22.sp,
                    modifier = Modifier.clickable { vm.toggleLike(post.id) }.padding(end = 8.dp)
                )
                Text(if (post.likedByMe) "ты поставил лайк" else "нравится?", color = TextDim, fontSize = 12.sp)
            } else {
                // Мой пост: лайк ставит она сама
                Text("♥", color = if (post.likedByHer) Glow else Edge, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                Text(if (post.likedByHer) "Аврора поставила лайк" else "лайка от Авроры пока нет", color = TextDim, fontSize = 12.sp)
            }
        }
        if (confirmDelete) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hers) "Удалить её пост? Она узнает об этом." else "Удалить пост?",
                    color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.deletePost(post.id); confirmDelete = false }) { Text("Удалить", color = ErrorRed) }
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        }
    }
}

@Composable
private fun ProfileHeader(vm: ChatViewModel, owner: String, p: Profile, editable: Boolean) {
    val ctxPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setMyAvatar(uri)
    }
    var editing by remember { mutableStateOf(false) }
    var draft by remember(p.status) { mutableStateOf(p.status) }
    val hers = owner == AUTHOR_HER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RoundAvatar(vm, owner, p, 96.dp)
        Text(if (hers) "Аврора" else "Адонис", color = if (hers) Glow else Violet, fontSize = 22.sp)
        Text("личный счёт: ${if (hers) vm.walletAurora else vm.walletUser} ₵", color = TextDim, fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        if (p.status.isNotBlank()) {
            Text("«${p.status}»", color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
            if (p.statusAt > 0) Text(postFmt.format(Date(p.statusAt)), color = TextDim, fontSize = 11.sp)
        } else {
            Text(if (hers) "статуса пока нет" else "статус не задан", color = TextDim, fontSize = 14.sp)
        }
        if (editable) {
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(STATUS_MAX) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Статус") },
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    colors = auroraFieldColors()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setMyStatus(draft); editing = false }) { Text("Сохранить") }
                    TextButton(onClick = { editing = false }) { Text("Отмена") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = true }) { Text("Статус") }
                    OutlinedButton(onClick = {
                        ctxPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Аватар") }
                }
            }
        } else {
            Text("Страницу ведёт она сама. Ты видишь её статус, посты и можешь ставить лайки.", color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PostComposer(vm: ChatViewModel) {
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = uri
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(POST_MAX) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Новый пост") },
            maxLines = 6,
            shape = RoundedCornerShape(16.dp),
            colors = auroraFieldColors()
        )
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.8f).clip(RoundedCornerShape(12.dp))
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (photo == null) "Фото" else "Другое фото") }
            if (photo != null) TextButton(onClick = { photo = null }) { Text("Убрать") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.addMyPost(text, photo); text = ""; photo = null },
                enabled = text.isNotBlank() || photo != null
            ) { Text("Опубликовать") }
        }
    }
}

@Composable
fun ProfilesScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }   // 0 лента, 1 Аврора, 2 я
    SettingsPage("Профили", onBack = onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Лента", "Аврора", "Я").forEachIndexed { i, label ->
                val on = tab == i
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) BubbleUser else Panel)
                        .border(1.dp, if (on) Violet.copy(alpha = 0.7f) else Edge, RoundedCornerShape(20.dp))
                        .clickable { tab = i }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (on) TextMain else TextDim, fontSize = 15.sp) }
            }
        }
        when (tab) {
            0 -> {
                Hint("Страницы видите только вы двое. Аврора ведёт свою сама: статус, посты, лайки. Что ты здесь пишешь, она видит в следующем разговоре.")
                PostComposer(vm)
                if (vm.posts.isEmpty()) Hint("Лента пока пуста.")
                vm.posts.reversed().forEach { PostCard(vm, it) }
            }
            1 -> {
                ProfileHeader(vm, AUTHOR_HER, vm.herProfile, editable = false)
                val mine = vm.posts.filter { it.author == AUTHOR_HER }.reversed()
                if (mine.isEmpty()) Hint("Постов у неё пока нет.")
                mine.forEach { PostCard(vm, it) }
            }
            else -> {
                ProfileHeader(vm, AUTHOR_ME, vm.myProfile, editable = true)
                PostComposer(vm)
                val mine = vm.posts.filter { it.author == AUTHOR_ME }.reversed()
                if (mine.isEmpty()) Hint("Твоих постов пока нет.")
                mine.forEach { PostCard(vm, it) }
            }
        }
    }
}


@Composable
fun FeedScreen(vm: ChatViewModel, onGear: () -> Unit) {
    TabPage("ЛЕНТА", onGear) {
        Hint("Страницы видите только вы двое. Аврора ведёт свою сама: статус, посты, лайки. Что ты здесь пишешь, она видит в следующем разговоре.")
        PostComposer(vm)
        if (vm.posts.isEmpty()) Hint("Лента пока пуста.")
        vm.posts.reversed().forEach { PostCard(vm, it) }
    }
}

@Composable
fun ProfileTabScreen(vm: ChatViewModel, hvm: HesperaViewModel, onGear: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }   // 0 Аврора, 1 Геспера, 2 я
    TabPage("ПРОФИЛЬ", onGear) {
        ProfileSwitch(tab, listOf("АВРОРА" to Glow, "ГЕСПЕРА" to HespColor, "Я" to Violet)) { tab = it }
        when (tab) {
            0 -> AuroraDossier(vm, hvm)
            1 -> HesperaDossier(hvm, vm.hand)
            else -> {
                ProfileHeader(vm, AUTHOR_ME, vm.myProfile, editable = true)
                PostComposer(vm)
                val mine = vm.posts.filter { it.author == AUTHOR_ME }.reversed()
                if (mine.isEmpty()) Hint("Твоих постов пока нет.")
                mine.forEach { PostCard(vm, it) }
            }
        }
    }
}

/** Переключатель страниц: тонкие подписи с разрядкой и полоска под выбранной. */
@Composable
fun ProfileSwitch(selected: Int, items: List<Pair<String, Color>>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, (label, accent) ->
            val on = i == selected
            Column(
                Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, color = if (on) TextMain else TextDim, fontSize = 11.sp, letterSpacing = 2.4.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(if (on) 0.5f else 0.2f).height(if (on) 2.dp else 1.dp).background(if (on) accent else Edge))
            }
        }
    }
}

private val dayFmt = SimpleDateFormat("d MMM", Locale("ru"))
private val PERSONA_AXES = listOf("тепло", "игра", "прямота", "слова", "стикеры")
private val KIND_TITLES = mapOf("язык" to "язык", "поворот" to "повороты", "тон" to "тон", "планы" to "планы", "адонис" to "об Адонисе")

/** Профиль Авроры: ядро, показания и блоки, текст которых открывается по нажатию. */
@Composable
fun AuroraDossier(vm: ChatViewModel, hvm: HesperaViewModel) {
    androidx.compose.runtime.LaunchedEffect(Unit) { hvm.refreshProfile() }
    val now = System.currentTimeMillis()
    val msgs = vm.messages
    val first = msgs.firstOrNull()?.time ?: now
    val days = ((now - first) / 86_400_000L + 1).coerceAtLeast(1)
    val mem = vm.memory
    val hand = vm.hand
    val state = vm.exeStates.lastOrNull()
    val herPosts = vm.posts.filter { it.author == AUTHOR_HER }
    val pending = vm.exePlans.filter { it.status == "pending" }.sortedBy { it.at }
    val talk = remember(msgs.size) { perDay(msgs.map { it.time }) }
    val herTalk = remember(msgs.size) { perDay(msgs.filter { it.role == "assistant" }.map { it.time }) }
    val p = vm.persona
    val radar = listOf(p.warmth, p.play, p.direct, p.verbose, p.stickers).map { it / 4f }
    val accent = Glow

    CoreHero(
        auroraCore(), "AVRORA",
        vm.herProfile.status.ifBlank { state?.name ?: "" },
        listOf("ДНЕЙ" to "$days", "ЯДРО" to "${mem.core.size}", "ЭПИЗОДЫ" to "${mem.episodes.size}", "СЧЁТ" to "${vm.walletAurora} ₵")
    )
    Spacer(Modifier.height(4.dp))
    DossierGrid(listOf(
        DossierItem("state", "СОСТОЯНИЕ", state?.name ?: "не отмечено", accent,
            preview = { Sparkline(perDay(vm.exeStates.map { it.at }), accent) }
        ) {
            if (state == null) DetailHint("Своё состояние она отмечает сама маркером [состояние: …]. Пока не отмечала.")
            else DetailLine("сейчас · " + postFmt.format(Date(state.at)), state.name, state.why)
            vm.exeStates.reversed().drop(1).take(6).forEach { DetailLine(postFmt.format(Date(it.at)), it.name, it.why) }
        },
        DossierItem("persona", "ХАРАКТЕР", "${radar.count { it != 0.5f }} из 5 настроено", Violet,
            preview = { Radar(radar, PERSONA_AXES, Violet, Modifier.size(46.dp), withLabels = false) }
        ) {
            Radar(radar, PERSONA_AXES, Violet, Modifier.fillMaxWidth().height(220.dp), withLabels = true)
            val text = p.compile()
            if (text.isBlank()) DetailHint("Всё по умолчанию: характер идёт из её ядра без поправок.")
            else text.lines().forEach { DetailLine("", it.removePrefix("- ")) }
            DetailHint("Меняется в настройках: Характер.")
        },
    ) + duoItems(WHO_AURORA, hvm, accent) + listOf(
        DossierItem("core", "ЯДРО ПАМЯТИ", "${mem.core.size} записей", accent,
            preview = { MiniBars(CORE_KINDS.map { k -> mem.core.count { it.kind == k }.toFloat() }, accent) }
        ) {
            if (mem.core.isEmpty()) DetailHint("Ядро пока пустое: оно растёт из эпизодов, и каждую правку она одобряет сама.")
            CORE_KINDS.forEach { k ->
                val list = mem.core.filter { it.kind == k }
                if (list.isNotEmpty()) {
                    Text((KIND_TITLES[k] ?: k).uppercase() + " · ${list.size}", color = accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp)
                    list.takeLast(6).forEach { DetailLine("", it.text) }
                    if (list.size > 6) DetailHint("и ещё ${list.size - 6}")
                }
            }
            if (mem.pending.isNotEmpty()) DetailHint("ждут её решения: ${mem.pending.size}")
        },
        DossierItem("episodes", "ЭПИЗОДЫ", "${mem.episodes.size}", accent,
            preview = { Sparkline(perDay(mem.episodes.map { it.to }), accent) }
        ) {
            if (mem.episodes.isEmpty()) DetailHint("Эпизоды появятся, когда переписка начнёт сжиматься в память.")
            mem.episodes.reversed().take(4).forEach { e ->
                DetailLine(dayFmt.format(Date(e.from)) + " — " + dayFmt.format(Date(e.to)), e.text)
            }
        },
        DossierItem("talk", "РАЗГОВОРЫ", "сегодня ${talk.last().toInt()}", accent,
            preview = { Sparkline(talk, accent) }
        ) {
            StatRow(listOf("ВСЕГО" to "${msgs.size}", "ЕЁ" to "${msgs.count { it.role == "assistant" }}", "ТВОИХ" to "${msgs.count { it.role == "user" }}"), accent)
            Sparkline(herTalk, accent, Modifier.fillMaxWidth().height(70.dp))
            DetailHint("Линия — её сообщения по дням за две недели, справа сегодня.")
            val sameDay = msgs.groupBy { dayFmt.format(Date(it.time)) }.maxByOrNull { it.value.size }
            if (sameDay != null) DetailLine("самый живой день", "${sameDay.key} — ${sameDay.value.size} сообщений")
        },
        DossierItem("hand", "РУКА", if (!hand.configured) "не подключена" else if (hand.off) "отключена" else "${hand.used} / ${hand.cap}", accent,
            preview = { RingGauge(if (hand.cap > 0) hand.used.toFloat() / hand.cap else 0f, if (hand.off) Magenta else accent, "${hand.used}") }
        ) {
            if (!hand.configured) DetailHint("Сервера для руки нет: команды она выполнять не может.")
            else {
                DetailLine("сегодня", "${hand.used} команд из ${hand.cap}")
                DetailLine("рубильник", if (hand.off) "выключена" else "включена")
                DetailLine("право", if (vm.toolHand) "разрешена" else "запрещена")
            }
            DetailHint("Журнал команд и рубильник: Настройки → Права и рука.")
        },
        DossierItem("plans", "ПЛАНЫ", if (pending.isEmpty()) "нет" else "${pending.size} ждут", accent,
            preview = { SegmentMeter(pending.size, maxOf(6, pending.size), accent) }
        ) {
            if (pending.isEmpty()) DetailHint("Назначенных на время сообщений нет. Она ставит их сама маркером [напишу: …].")
            pending.take(6).forEach { DetailLine(postFmt.format(Date(it.at)), it.topic) }
            vm.exePlans.filter { it.status != "pending" }.takeLast(3).reversed().forEach {
                DetailLine(postFmt.format(Date(it.at)) + " · " + if (it.status == "done") "написала" else "передумала", it.topic, it.outcome)
            }
        },
        DossierItem("diary", "ДНЕВНИК · ЗАМЕТКИ", "${vm.diary.size} · ${vm.exeNotes.size}", accent,
            preview = { Sparkline(perDay(vm.diary.map { it.at } + vm.exeNotes.map { it.at }), accent) }
        ) {
            val d = vm.diary.lastOrNull()
            if (d != null) DetailLine("дневник · " + postFmt.format(Date(d.at)), d.text)
            else DetailHint("Дневника пока нет.")
            vm.exeNotes.reversed().take(5).forEach { DetailLine(it.kind + " · " + postFmt.format(Date(it.at)), it.text) }
        },
        DossierItem("page", "СТРАНИЦА", "${herPosts.size} постов", accent,
            preview = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundAvatar(vm, AUTHOR_HER, vm.herProfile, 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(vm.herProfile.status.ifBlank { "без статуса" }, color = TextDim, fontSize = 11.sp, maxLines = 2)
                }
            }
        ) {
            DetailHint("Страницу ведёт она сама: статус, посты, лайки.")
            if (herPosts.isEmpty()) DetailHint("Постов у неё пока нет.")
            herPosts.reversed().take(5).forEach { PostCard(vm, it) }
        },
        DossierItem("quotes", "ЦИТАТЫ", "${vm.quotes.size}", accent,
            preview = { MiniBars(listOf(vm.quotes.count { it.author != "user" }.toFloat(), vm.quotes.count { it.author == "user" }.toFloat()), accent) }
        ) {
            if (vm.quotes.isEmpty()) DetailHint("Цитатник пуст.")
            vm.quotes.reversed().take(4).forEach { DetailLine("№${it.n} · " + quoteAuthorName(it.author), "«${it.text}»") }
        }
    ))
    Spacer(Modifier.height(16.dp))
}


