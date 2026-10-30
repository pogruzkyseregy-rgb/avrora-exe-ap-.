package ru.avrora.exe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- профиль Эст: страница как у девчонок, ядро — по кнопке ----------

private val eDay = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

private fun estMoodLine(p: EstProfile, now: Long): String? =
    p.mood.takeIf { it.word.isNotBlank() && now / 1000.0 - it.at < 12 * 3600 }?.let { "настроение: ${it.word}" + if (it.why.isNotBlank()) " — ${it.why}" else "" }

/** Страница Эст: обложка, аватар, статус, счётчики и её лента. */
@Composable
fun EstSocial(evm: EstViewModel, onCore: () -> Unit, onWrite: () -> Unit) {
    LaunchedEffect(Unit) { evm.refreshProfile(); evm.refreshTasks() }
    var showFiles by remember { mutableStateOf(false) }
    if (showFiles) FileBrowserDialog(WHO_EST) { showFiles = false }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) evm.setAvatar(uri) }
    val p = evm.profile
    val now = System.currentTimeMillis()
    val accepted = evm.tasks.count { it.status == "accepted" }
    SocialHeader(
        accent = EstColor,
        avatar = { size -> EstAvatar(size) },
        name = "Эст",
        handle = "@est · дух-меч · твой клинок",
        status = p.status,
        about = estMoodLine(p, now) ?: "Равная тебе. Руководит Авророй и Гесперой: ставит задачи, проверяет, принимает.",
        counters = listOf("постов" to "${p.posts.size}", "♥" to "${p.posts.sumOf { likesOf(it) }}", "задач" to "$accepted"),
        actions = listOf(
            SocialAction("Ядро", "◎", onCore),
            SocialAction("Папка", "▤") { showFiles = true },
            SocialAction("Аватар", "◐") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            SocialAction("Написать", "✉", onWrite)
        )
    )
    FeedTitle("ЕЁ ЛЕНТА")
    if (p.posts.isEmpty()) Hint("Постов у неё пока нет. Она пишет их сама — коротко и по делу.")
    p.posts.reversed().forEach { EstPostCard(evm, it) }
    Spacer(Modifier.height(12.dp))
}

private fun likesOf(p: HesperaPost): Int = listOf(p.liked, p.likedAurora, p.likedHespera).count { it }

/** Пост Эст: лайк Адониса, кто ещё лайкнул, комментарии. */
@Composable
fun EstPostCard(evm: EstViewModel, post: HesperaPost) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(BubbleAurora).border(1.dp, EstColor.copy(alpha = 0.5f), shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EstAvatar(32.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Эст", color = EstColor, fontSize = 14.sp)
                Text(eDay.format(Date((post.at * 1000).toLong())), color = TextDim, fontSize = 11.sp)
            }
        }
        if (post.text.isNotBlank()) Text(post.text, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
        if (post.media.isNotBlank()) MediaPart(Part.Media(post.media, post.text.take(40)), EstColor) {}
        LikeRow(EstColor, post.liked, listOfNotNull("Аврора".takeIf { post.likedAurora }, "Геспера".takeIf { post.likedHespera })) { evm.likePost(post) }
        PostComments(post.comments, EstColor, onSend = { evm.commentPost(post, it) }, onDelete = { evm.deleteComment(post, it) })
    }
}

/** Ядро Эст: кто она, доска задач в цифрах, как работают Аврора и Геспера, её память. */
@Composable
fun EstDossier(evm: EstViewModel, hand: HandStatus) {
    LaunchedEffect(Unit) { evm.refreshProfile(); evm.refreshTasks() }
    var showFiles by remember { mutableStateOf(false) }
    if (showFiles) FileBrowserDialog(WHO_EST) { showFiles = false }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now = System.currentTimeMillis() } }
    val p = evm.profile
    val accent = EstColor
    val ts = evm.tasks
    val days = if (p.born > 0) ((now / 1000.0 - p.born) / 86400).toInt() + 1 else 1
    val open = ts.count { it.open }
    val review = ts.count { it.status == "done" }
    val accepted = ts.count { it.status == "accepted" }
    val talk = remember(evm.chat.size) { perDay(evm.chat.filter { it.role != "system" }.map { it.time }) }
    val rights = listOf(evm.wakeOn, true, true, hand.configured && !hand.off, true, true, true)

    CoreHero(
        estCore(), "EST",
        estMoodLine(p, now) ?: p.status.ifBlank { "на страже" },
        listOf("ДНЕЙ" to "$days", "ЗАДАЧ" to "${ts.size}", "ПРИНЯТО" to "$accepted", "ЭПИЗОДОВ" to "${p.episodesN}")
    )
    Spacer(Modifier.height(4.dp))

    fun girlItem(who: String, color: androidx.compose.ui.graphics.Color): DossierItem {
        val mine = ts.filter { it.to == who }
        val ok = mine.count { it.status == "accepted" }
        val bad = mine.count { it.status == "failed" } + mine.sumOf { (it.runs - 1).coerceAtLeast(0) }
        val frac = if (ok + bad > 0) ok.toFloat() / (ok + bad) else 0f
        return DossierItem(who, whoName(who).uppercase(), "${mine.size} задач · принято $ok", color,
            preview = { RingGauge(frac, color, "${(frac * 100).toInt()}") }
        ) {
            DetailHint("Шкала — доля принятых с первого раза: принятые против переделок и проваленных.")
            StatRow(listOf("ВСЕГО" to "${mine.size}", "ПРИНЯТО" to "$ok", "В РАБОТЕ" to "${mine.count { it.open }}",
                "ПЕРЕДЕЛОК" to "${mine.sumOf { (it.runs - 1).coerceAtLeast(0) }}"), color)
            mine.reversed().take(6).forEach { DetailLine("№${it.id} · ${taskStatusRu(it.status)}", it.text, it.report.take(200)) }
            if (mine.isEmpty()) DetailHint("Задач ей пока не было.")
        }
    }

    DossierGrid(listOf(
        DossierItem("who", "КТО ОНА", "дух-меч", accent,
            preview = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EstAvatar(32.dp)
                    Spacer(Modifier.width(8.dp))
                    SegmentMeter(p.fields.size.coerceAtLeast(1), p.fields.size.coerceAtLeast(1), accent, Modifier.weight(1f).height(8.dp))
                }
            }
        ) {
            p.fields.forEach { (k, v) -> DetailLine(k, v) }
            if (p.fields.isEmpty()) DetailHint("Сервер ещё не рассказал о ней — обнови сервер до exe-33.")
            DetailHint("Характер Эст задан тобой и не меняется. Меняются её память, настроение, статус и страница.")
        },
        DossierItem("board", "ДОСКА ЗАДАЧ", "в работе $open · проверка $review", accent,
            preview = { MiniBars(listOf(open, review, accepted, ts.count { it.status == "failed" || it.status == "cancelled" }).map { it.toFloat() }, accent) }
        ) {
            StatRow(listOf("В РАБОТЕ" to "$open", "НА ПРОВЕРКЕ" to "$review", "ПРИНЯТО" to "$accepted",
                "СНЯТО/НЕ ВЫШЛО" to "${ts.count { it.status == "failed" || it.status == "cancelled" }}"), accent)
            DetailLine("по дням", "задач за последние две недели — на графике")
            Sparkline(perDay(ts.map { (it.at * 1000).toLong() }), accent, Modifier.fillMaxWidth().height(40.dp))
            ts.reversed().take(8).forEach { DetailLine("№${it.id} → ${whoName(it.to)} · ${taskStatusRu(it.status)}", it.text) }
            if (ts.isEmpty()) DetailHint("Задач пока не было. Скажи Эст, что нужно сделать, — она раздаст.")
        },
        girlItem(WHO_AURORA, Glow),
        girlItem(WHO_HESPERA, HespColor),
        DossierItem("memory", "ПАМЯТЬ", "${p.notesN} заметок", accent,
            preview = { SegmentMeter(p.notesN.coerceAtMost(20), 20, accent, Modifier.fillMaxWidth().height(8.dp)) }
        ) {
            if (p.notes.isEmpty()) DetailHint("Заметок пока нет. Она пишет их сама: [запомню: …]")
            p.notes.reversed().take(20).forEach { DetailLine("", it) }
        },
        DossierItem("episodes", "ЭПИЗОДЫ", "${p.episodesN}", accent,
            preview = { Sparkline(perDay(p.episodes.map { (it.at * 1000).toLong() }), accent) }
        ) {
            DetailHint("Раз в 30 сообщений она пересказывает разговор себе в память.")
            p.episodes.reversed().forEach { DetailLine(eDay.format(Date((it.at * 1000).toLong())), it.text) }
            if (p.episodes.isEmpty()) DetailHint("Эпизодов пока нет.")
        },
        DossierItem("talk", "РАЗГОВОРЫ", "сегодня ${talk.last().toInt()}", accent,
            preview = { Sparkline(talk, accent) }
        ) {
            StatRow(listOf("ЛИЧКА" to "${evm.chat.count { it.role != "system" }}", "САМА" to "${evm.chat.count { it.auto }}"), accent)
        },
        DossierItem("page", "СТРАНИЦА", "${p.posts.size} постов · ♥ ${p.posts.sumOf { likesOf(it) }}", accent,
            preview = { Sparkline(perDay(p.posts.map { (it.at * 1000).toLong() }), accent) }
        ) {
            if (p.status.isNotBlank()) DetailLine("статус", "«${p.status}»")
            if (p.posts.isEmpty()) DetailHint("Постов пока нет.")
            p.posts.reversed().take(10).forEach { EstPostCard(evm, it) }
        },
        filesItem(WHO_EST, accent) { showFiles = true },
        DossierItem("rights", "ЧТО ЕЙ МОЖНО", "все права", accent,
            preview = { DotRow(rights, accent) }
        ) {
            DetailLine("пишет первой", if (evm.wakeOn) "да — просыпается сама, проверяет доску" else "выключено")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Просыпается сама", color = TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(evm.wakeOn, onCheckedChange = evm::setWake, colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Night))
            }
            DetailLine("поиск, чтение, рисование, музыка, слух", "всегда включены")
            DetailLine("рука", if (hand.configured) "своя папка ~/est, папки девчонок — только смотреть" else "рука не подключена на сервере")
            DetailLine("над девчонками", "ставит задачи, проверяет, принимает или возвращает. Выше неё только ты")
        },
        DossierItem("wake", "ПРОБУЖДЕНИЕ", if (!evm.wakeOn) "выключено" else inTime(evm.nextWakeAt - now), accent,
            preview = {
                val left = (evm.nextWakeAt - now).coerceAtLeast(0)
                RingGauge(if (evm.wakeOn) 1f - (left / (12 * 3_600_000f)).coerceIn(0f, 1f) else 0f, accent, if (evm.wakeOn) "${left / 60_000}" else "—")
            }
        ) {
            DetailHint("Когда проснуться, она решает сама: от 30 минут до 12 часов. Просыпаясь, проверяет доску и ленту.")
            TextButton(onClick = { evm.wakeNow() }, enabled = !evm.sending) { Text(if (evm.sending) "Будим…" else "Разбудить сейчас", color = accent) }
            evm.error?.let { DetailHint(it) }
        }
    ))
    Text("Характер Эст задан тобой. Её задачи девчонки выполняют — выше неё только ты.",
        color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(16.dp))
}
