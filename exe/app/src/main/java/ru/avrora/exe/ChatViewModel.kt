package ru.avrora.exe

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ChatStore(app)
    private val prefs = AppPrefs(app)
    private val inbox = Inbox(app)
    private val memoryStore = MemoryStore(app)
    private val appCtx: Application = app
    private val diaryStore = DiaryStore(app)
    private val exeStore = ExeStore(app)
    private val profileStore = ProfileStore(app)
    val library = Library(app)

    val messages = mutableStateListOf<ChatMessage>().apply { addAll(store.load()) }
    val stickers = mutableStateListOf<CatalogItem>()
    val photos = mutableStateListOf<CatalogItem>()
    val diary = mutableStateListOf<DiaryEntry>().apply { addAll(diaryStore.load()) }
    val exeStates = mutableStateListOf<StateEntry>().apply { addAll(exeStore.states()) }
    val exePlans = mutableStateListOf<PlanItem>().apply { addAll(exeStore.plans()) }
    val exeNotes = mutableStateListOf<NoteEntry>().apply { addAll(exeStore.notes()) }
    private val gameStore = GameStore(app)
    private var gameSaved = gameStore.load()
    var game by mutableStateOf(gameSaved.game)
    var gameInvites by mutableStateOf(gameSaved.invites)
    var walletUser by mutableStateOf(prefs.walletUser)
    var walletAurora by mutableStateOf(prefs.walletAurora)
    var gameThinking by mutableStateOf(false)
    var diceRolling by mutableStateOf(false)
    /** Попросить экран открыть игру (после принятия приглашения). */
    var openGameRequest by mutableStateOf(false)
    /** Карточка клетки, на которую только что встали: вылетает на экране игры. */
    var landing by mutableStateOf<Landing?>(null)
    private var gameJob: Job? = null
    private val gameRnd = kotlin.random.Random.Default
    private val alarmStore = AlarmStore(app)
    val alarms = mutableStateListOf<AlarmItem>().apply { addAll(alarmStore.list()) }
    var alarmByAurora by mutableStateOf(prefs.alarmByAurora)
    /** Что открыть по запросу извне (виджет, звонок будильника): alarms / chat. */
    var openRequest by mutableStateOf<String?>(null)
    private val quoteStore = QuoteStore(app)
    val quotes = mutableStateListOf<QuoteItem>().apply { addAll(quoteStore.load()) }
    /** Что вставить в поле ввода чата (ссылка на цитату из цитатника). */
    var draftInsert by mutableStateOf<String?>(null)
    var bootScreenOn by mutableStateOf(prefs.bootScreen)
    fun setBootScreen(on: Boolean) { prefs.bootScreen = on; bootScreenOn = on }
    val posts = mutableStateListOf<PostItem>().apply { addAll(profileStore.posts()) }
    var herProfile by mutableStateOf(profileStore.profile(AUTHOR_HER))
        private set
    var myProfile by mutableStateOf(profileStore.profile(AUTHOR_ME))
        private set

    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var serverUrl by mutableStateOf(prefs.serverUrl)
        private set
    var token by mutableStateOf(prefs.token)
        private set
    var persona by mutableStateOf(prefs.loadPersona())
        private set
    var initSettings by mutableStateOf(prefs.loadInit())
        private set
    var memory by mutableStateOf(memoryStore.load())
        private set
    var compressing by mutableStateOf(false)
        private set
    var compressStatus by mutableStateOf("")
        private set
    var hasMemoryBackup by mutableStateOf(memoryStore.hasBackup())
        private set
    var consenting by mutableStateOf(false)
        private set
    var diaryOn by mutableStateOf(prefs.diaryEnabled)
        private set
    var writingDiary by mutableStateOf(false)
        private set
    var revealTime by mutableStateOf(0L)
        private set
    var probing by mutableStateOf(false)
        private set
    var probeResult by mutableStateOf<String?>(null)
        private set
    var testing by mutableStateOf(false)
        private set
    var toolSearch by mutableStateOf(prefs.toolSearch)
        private set
    var glitchOn by mutableStateOf(prefs.glitch)
    fun setGlitch(on: Boolean) { prefs.glitch = on; glitchOn = on }
    var toolRead by mutableStateOf(prefs.toolRead)
        private set
    var toolHand by mutableStateOf(prefs.toolHand)
        private set
    var toolDraw by mutableStateOf(prefs.toolDraw)
        private set
    var toolMusic by mutableStateOf(prefs.toolMusic)
        private set
    var toolHear by mutableStateOf(prefs.toolHear)
        private set
    var hand by mutableStateOf(HandStatus(prefs.handConfigured, prefs.handOff, 0, prefs.handCap))
        private set
    var handBusy by mutableStateOf(false)
        private set
    var handLog by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        val (bs, bp) = library.builtIn()
        val (us, up) = library.user()
        stickers.addAll(bs + us)
        photos.addAll(bp + up)

        drainInbox()
        resolvePending()
        InitiativeScheduler.apply(app, initSettings.enabled, initSettings.free)
        DiaryScheduler.apply(app, diaryOn)
        maybeWriteDiary()
        PlanScheduler.resync(app)
        runDuePlans()
        refreshHand()
        viewModelScope.launch { InboxBus.flow.collect { drainInbox() } }
    }

    // ---------- консоль: состояние, время, следы, права ----------

    /** Файлы могла изменить фоновая задача: перечитываем. */
    fun reloadAlarms() {
        val fresh = alarmStore.list()
        if (fresh != alarms.toList()) { alarms.clear(); alarms.addAll(fresh) }
    }

    private fun saveAlarms(list: List<AlarmItem>) {
        alarmStore.save(list)
        alarms.clear()
        alarms.addAll(list)
        AlarmScheduler.reschedule(appCtx)
    }

    fun addAlarm(hour: Int, minute: Int, days: Set<Int>, label: String) {
        val now = System.currentTimeMillis()
        val a = AlarmItem(newId("al"), hour, minute, days, label.trim().take(200), "adonis", true,
            onceAt = if (days.isEmpty()) onceAtFor(hour, minute, now) else 0L, created = now)
        saveAlarms(alarmStore.list() + a)
        nextAlarm(listOf(a), now)?.let { notice = "Будильник: " + humanGap(it.second - now) + " до звонка" }
    }

    fun updateAlarm(a: AlarmItem) {
        val now = System.currentTimeMillis()
        val fixed = if (a.days.isEmpty() && a.enabled && a.onceAt <= now) a.copy(onceAt = onceAtFor(a.hour, a.minute, now)) else a
        saveAlarms(alarmStore.list().map { if (it.id == a.id) fixed else it })
    }

    fun toggleAlarm(a: AlarmItem, on: Boolean) = updateAlarm(a.copy(enabled = on, snoozeAt = 0L))

    fun deleteAlarm(a: AlarmItem) {
        saveAlarms(alarmStore.list().filter { it.id != a.id })
        if (a.by == "aurora") {
            commitMemory(memory.copy(log = (memory.log + note("Адонис удалил твой будильник на ${alarmTime(a)}" + if (a.label.isNotBlank()) ": «${a.label.take(80)}»" else "")).takeLast(100)))
        }
    }

    fun setAlarmRight(on: Boolean) { prefs.alarmByAurora = on; alarmByAurora = on }

    // ---------- виджет ----------
    var widgetGlitch by mutableStateOf(prefs.widgetGlitch)
    var widgetDim by mutableStateOf(prefs.widgetDim)
    /** Меняется при каждой смене фона, чтобы превью перерисовалось. */
    var widgetBgVersion by mutableStateOf(0)

    fun setWidgetGlitchOn(on: Boolean) { prefs.widgetGlitch = on; widgetGlitch = on }

    var digitStyle by mutableStateOf(prefs.digitStyle)
    fun setDigitStyleTo(v: String) { prefs.digitStyle = v; digitStyle = v; AlarmWidget.updateAll(appCtx) }

    var themeId by mutableStateOf(prefs.theme)
    fun setTheme(id: String) { prefs.theme = id; themeId = id; AppTheme.set(id); AlarmWidget.updateAll(appCtx) }

    /** Картинка в редакторе подгонки фона; null значит редактор закрыт. */
    var cropSource by mutableStateOf<android.graphics.Bitmap?>(null)
    var cropAspect by mutableStateOf(1.6f)
    var cropInitial by mutableStateOf<android.graphics.RectF?>(null)

    private fun openCrop(bmp: android.graphics.Bitmap?, initial: android.graphics.RectF? = null) {
        if (bmp == null) { notice = "Не получилось открыть картинку"; return }
        cropAspect = AlarmWidget.widgetAspect(appCtx)
        cropInitial = initial
        cropSource = bmp
    }

    fun setWidgetBgUri(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val bmp = AlarmWidget.loadUri(appCtx, uri)
        withContext(Dispatchers.Main) { openCrop(bmp) }
    }

    fun setWidgetBgPhoto(item: CatalogItem) = viewModelScope.launch(Dispatchers.IO) {
        val bmp = (item.model as? File)?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
        withContext(Dispatchers.Main) { openCrop(bmp) }
    }

    /** Подогнать уже поставленный фон заново. */
    fun refitWidgetBg() = viewModelScope.launch(Dispatchers.IO) {
        val bmp = AlarmWidget.sourceBitmap(appCtx)
        val crop = AlarmWidget.savedCrop(appCtx)
        withContext(Dispatchers.Main) { openCrop(bmp, crop) }
    }

    fun applyWidgetCrop(crop: android.graphics.RectF) {
        val src = cropSource ?: return
        cropSource = null
        viewModelScope.launch(Dispatchers.IO) {
            val ok = AlarmWidget.setBackground(appCtx, src, widgetDim, crop)
            withContext(Dispatchers.Main) { widgetBgVersion++; notice = if (ok) "Фон виджета поставлен" else "Не получилось сохранить фон" }
        }
    }

    fun cancelCrop() { cropSource = null }

    fun setWidgetDimLevel(v: Int) {
        prefs.widgetDim = v
        widgetDim = v
        viewModelScope.launch(Dispatchers.IO) {
            AlarmWidget.reapplyDim(appCtx, v)
            withContext(Dispatchers.Main) { widgetBgVersion++ }
        }
    }

    fun clearWidgetBg() = viewModelScope.launch(Dispatchers.IO) {
        AlarmWidget.clearBackground(appCtx)
        withContext(Dispatchers.Main) { widgetBgVersion++ }
    }

    fun reloadExe() {
        reloadAlarms()
        exeStates.clear(); exeStates.addAll(exeStore.states())
        exePlans.clear(); exePlans.addAll(exeStore.plans())
        exeNotes.clear(); exeNotes.addAll(exeStore.notes())
        posts.clear(); posts.addAll(profileStore.posts())
        herProfile = profileStore.profile(AUTHOR_HER)
        myProfile = profileStore.profile(AUTHOR_ME)
    }

    // ---------- профили и лента ----------

    fun setMyStatus(text: String) {
        profileStore.setStatus(AUTHOR_ME, text, System.currentTimeMillis())
        reloadExe()
    }

    /** Мой пост: текст и, если выбрано, фото из галереи. Аврора увидит его в ленте в следующем разговоре. */
    fun addMyPost(text: String, photoUri: Uri?) {
        val t = text.trim()
        if (t.isEmpty() && photoUri == null) return
        viewModelScope.launch {
            var photo = ""
            if (photoUri != null) {
                try {
                    photo = "снимок:" + withContext(Dispatchers.IO) { library.saveSnap(photoUri) }
                } catch (e: Exception) {
                    notice = "Не удалось открыть фото"
                    return@launch
                }
            }
            profileStore.addPost(PostItem(newId("p"), AUTHOR_ME, t.take(POST_MAX), photo, System.currentTimeMillis()))
            reloadExe()
        }
    }

    fun setMyAvatar(uri: Uri) {
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) { library.saveSnap(uri) }
                profileStore.setAvatar(AUTHOR_ME, name)
                reloadExe()
            } catch (e: Exception) {
                notice = "Не удалось открыть фото"
            }
        }
    }

    /** Мой лайк на её пост (повторное нажатие снимает). */
    fun toggleLike(id: String) {
        val p = posts.firstOrNull { it.id == id } ?: return
        profileStore.setLike(id, AUTHOR_ME, !p.likedByMe)
        reloadExe()
    }

    /** Удаление поста. Если удалён её пост, она узнает об этом из журнала. */
    fun deletePost(id: String) {
        val p = posts.firstOrNull { it.id == id } ?: return
        profileStore.removePost(id)
        if (p.author == AUTHOR_HER) {
            commitMemory(memory.copy(log = (memory.log + note("Адонис удалил твой пост: «${p.text.take(80)}»")).takeLast(100)))
        }
        reloadExe()
    }

    /** Если телефон проспал назначенное время, делаем это при открытии приложения. */
    private fun runDuePlans() {
        val now = System.currentTimeMillis()
        val due = exeStore.plans().filter { it.status == "pending" && it.at <= now }
        if (due.isEmpty()) return
        viewModelScope.launch {
            for (p in due) {
                try { InitiativeEngine.tickSelf(appCtx, p.id) } catch (e: Exception) { /* повторит фоновая задача */ }
            }
            drainInbox()
            reloadExe()
        }
    }

    fun setTool(kind: String, on: Boolean) {
        when (kind) {
            "поиск" -> { prefs.toolSearch = on; toolSearch = on }
            "чтение" -> { prefs.toolRead = on; toolRead = on }
            "рука" -> { prefs.toolHand = on; toolHand = on }
            "рисование" -> { prefs.toolDraw = on; toolDraw = on }
            "музыка" -> { prefs.toolMusic = on; toolMusic = on }
            "слух" -> { prefs.toolHear = on; toolHear = on }
        }
    }

    /** Узнать, подключена ли рука на сервере. Результат запоминается для списка ограничений. */
    fun refreshHand() {
        if (token.isBlank()) return
        viewModelScope.launch {
            try {
                applyHand(AuroraApi.handStatus(serverUrl, token))
            } catch (e: Exception) {
                // нет связи: остаётся последнее известное
            }
        }
    }

    private fun applyHand(h: HandStatus) {
        hand = h
        prefs.handConfigured = h.configured
        prefs.handOff = h.off
        prefs.handCap = h.cap
    }

    /** Рубильник руки на сервере. Нажатие попадает в журнал, и Аврора видит это в списке ограничений. */
    fun setHandKill(off: Boolean) {
        if (handBusy) return
        handBusy = true
        viewModelScope.launch {
            try {
                applyHand(AuroraApi.handSwitch(serverUrl, token, off))
                notice = if (off) "Рука отключена" else "Рука снова включена"
            } catch (e: Exception) {
                notice = "Не получилось: ${e.message ?: "нет связи"}"
            } finally {
                handBusy = false
            }
        }
    }

    fun loadHandLog() {
        viewModelScope.launch {
            handLog = try { AuroraApi.handLog(serverUrl, token) } catch (e: Exception) { listOf("не удалось получить журнал: ${e.message ?: "нет связи"}") }
        }
    }

    /** Отмена её назначения. Она узнает об этом из списка ограничений (строка пропадёт) и из журнала. */
    fun cancelPlan(id: String) {
        val p = exePlans.firstOrNull { it.id == id } ?: return
        PlanScheduler.cancel(appCtx, id)
        exeStore.updatePlan(id) { it.copy(status = "dropped", outcome = "отменено Адонисом") }
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))
        commitMemory(memory.copy(log = (memory.log + note("Адонис отменил твоё назначение на ${fmt.format(Date(p.at))}: «${p.topic.take(80)}»")).takeLast(100)))
        reloadExe()
    }

    fun deleteNote(id: String) {
        val n = exeNotes.firstOrNull { it.id == id } ?: return
        exeStore.removeNote(id)
        commitMemory(memory.copy(log = (memory.log + note("Адонис удалил твой след [${n.kind}]: «${n.text.take(80)}»")).takeLast(100)))
        reloadExe()
    }

    // ---------- настройки ----------

    fun saveSettings(url: String, tok: String) {
        serverUrl = url.trim()
        token = tok.trim()
        prefs.serverUrl = serverUrl
        prefs.token = token
    }

    fun clearNotice() { notice = null }

    fun updatePersona(p: PersonaSettings) {
        persona = p
        prefs.savePersona(p)
    }

    fun updateInit(s: InitSettings) {
        initSettings = s
        prefs.saveInit(s)
        InitiativeScheduler.apply(getApplication(), s.enabled, s.free)
    }

    /** Проверка кнопкой: Аврора пишет сразу, в обход пауз и лимитов. */
    fun testInitiative() {
        if (testing) return
        testing = true
        viewModelScope.launch {
            val r = try {
                InitiativeEngine.tick(getApplication(), true)
            } catch (e: Exception) {
                InitiativeOutcome.FAILED
            }
            notice = when (r) {
                InitiativeOutcome.SENT -> "Аврора написала"
                InitiativeOutcome.SILENT -> "Аврора решила промолчать"
                InitiativeOutcome.NO_CHAT -> "Сначала поговори с Авророй"
                InitiativeOutcome.NO_TOKEN -> "Сначала укажи токен"
                else -> "Не получилось: нет связи с сервером"
            }
            testing = false
        }
    }

    /** Забрать сообщения, которые Аврора написала, пока приложение было закрыто. */
    fun drainInbox() {
        val items = inbox.drain()
        if (items.isNotEmpty()) {
            items.forEach { m ->
                val before = messages.toList()
                messages.add(m)
                if (m.role == "assistant") {
                    applyQuoteSaves(m.content, before)
                    applyGameMarkers(m.content, before)
                }
            }
            persist()
        }
        reloadExe()
    }

    // ---------- дневник ----------

    /** Записи могла дописать фоновая проверка: перечитываем файл. */
    fun reloadDiary() {
        val fresh = diaryStore.load()
        if (fresh.size != diary.size || fresh.lastOrNull()?.id != diary.lastOrNull()?.id) {
            diary.clear()
            diary.addAll(fresh)
        }
    }

    fun enableDiary(on: Boolean) {
        prefs.diaryEnabled = on
        diaryOn = on
        DiaryScheduler.apply(appCtx, on)
    }

    /** Фоновая попытка по правилам: раз в сутки вечером и если был разговор. Ничего не показывает. */
    fun maybeWriteDiary() {
        viewModelScope.launch {
            try {
                DiaryEngine.tick(appCtx, false)
                reloadDiary()
            } catch (e: Exception) {
                // нет связи: попробуем позже
            }
        }
    }

    /** Кнопка «Написать сейчас»: правила времени не действуют, но писать ли, решает она. */
    fun writeDiaryNow() {
        if (writingDiary) return
        writingDiary = true
        viewModelScope.launch {
            val outcome = try {
                DiaryEngine.tick(appCtx, true)
            } catch (e: Exception) {
                DiaryOutcome.FAILED
            }
            reloadDiary()
            notice = when (outcome) {
                DiaryOutcome.WRITTEN -> "Аврора написала в дневник"
                DiaryOutcome.SKIPPED -> "Аврора решила, что писать пока нечего"
                DiaryOutcome.NO_NEW -> "С последней записи ты ей ничего не писал"
                DiaryOutcome.NO_TOKEN -> "Укажи токен в настройках"
                DiaryOutcome.FAILED -> "Не получилось: нет связи с сервером"
                DiaryOutcome.NOT_NOW -> "Сейчас нельзя"
            }
            writingDiary = false
        }
    }

    /** Удаление записи разрешено, но молча не бывает: Аврора узнаёт об этом из журнала. */
    fun deleteDiaryEntry(id: String) {
        val e = diary.firstOrNull { it.id == id } ?: return
        val day = SimpleDateFormat("d MMMM", Locale("ru")).format(Date(e.at))
        val list = diary.filter { it.id != id }
        diaryStore.save(list)
        diary.clear()
        diary.addAll(list)
        commitMemory(memory.copy(log = (memory.log + note("Адонис удалил твою запись в дневнике от $day")).takeLast(100)))
    }

    // ---------- отправка ----------

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        addUserMessage(t)
    }

    /** Текст и прикреплённый стикер или фото из альбома уходят одним сообщением: сначала текст, потом вложение. */
    fun sendWithTag(text: String, tag: String) {
        if (sending) return
        val t = text.trim()
        addUserMessage(if (t.isEmpty()) tag else "$t\n$tag")
    }

    /** Фото из галереи прямо в чат, с необязательной подписью. */
    fun sendSnap(uri: Uri, caption: String) {
        if (sending) return
        sending = true
        error = null
        viewModelScope.launch {
            val name = try {
                withContext(Dispatchers.IO) { library.saveSnap(uri) }
            } catch (e: Exception) {
                error = "Не удалось открыть фото"
                sending = false
                return@launch
            }
            val c = caption.trim()
            messages.add(ChatMessage("user", if (c.isEmpty()) "[снимок:$name]" else "[снимок:$name]\n$c"))
            noteUserActivity()
            persist()
            sending = false
            requestReply()
        }
    }

    /** «Слушаю…» / «расшифровываю…», пока сервер работает с голосовым или треком. */
    var hearing by mutableStateOf<String?>(null)
        private set

    /** Голосовое или трек Адониса: сервер расшифровывает или слушает, в чат уходит метка с текстом. */
    fun sendAudio(bytes: ByteArray, mime: String, name: String, voice: Boolean) {
        if (sending || hearing != null) return
        hearing = if (voice) "● расшифровываю голосовое…" else "● слушаю трек…"
        error = null
        viewModelScope.launch {
            try {
                val h = AudioApi.upload(appCtx, bytes, mime, name, voice)
                hearing = null
                addUserMessage(audioTag(voice, h, name))
            } catch (e: Exception) {
                hearing = null
                error = "Звук не дошёл: ${e.message ?: "нет связи"}"
            }
        }
    }

    private fun addUserMessage(content: String) {
        revealTime = 0L
        messages.add(ChatMessage("user", content))
        noteUserActivity()
        persist()
        requestReply()
    }

    /** Я написал: счётчик «сообщений Авроры без ответа» и паузы инициативы обнуляются. */
    private fun noteUserActivity() {
        prefs.initUnanswered = 0
        prefs.initNextCheck = 0L
    }

    /** Повторить запрос, если последний ответ не пришёл. */
    fun retry() {
        if (!sending && messages.lastOrNull()?.role == "user") requestReply()
    }

    fun clearChat() {
        messages.clear()
        error = null
        persist()
    }

    // ---------- библиотека ----------

    fun addSticker(uri: Uri, desc: String, cutBlack: Boolean) {
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { library.addSticker(uri, desc, cutBlack) }
                stickers.add(item)
                notice = "Стикер добавлен"
            } catch (e: Exception) {
                notice = "Не получилось добавить: ${e.message ?: "ошибка"}"
            }
        }
    }

    fun addPhoto(uri: Uri, desc: String) {
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { library.addPhoto(uri, desc) }
                photos.add(item)
                notice = "Фото добавлено в альбом"
            } catch (e: Exception) {
                notice = "Не получилось добавить: ${e.message ?: "ошибка"}"
            }
        }
    }

    /** Картинки из сети, которые она сохранила в этом ответе. Лимит в сутки, остальное отбрасывается. */
    private suspend fun saveFromNet(list: List<SavedImage>) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.saveDay != today) { prefs.saveDay = today; prefs.saveCount = 0 }
        for (img in list) {
            if (prefs.saveCount >= IMG_SAVE_DAILY_CAP) break
            val item = withContext(Dispatchers.IO) { library.addPhotoBytes(img.bytes, "из сети: " + img.desc) } ?: continue
            prefs.saveCount = prefs.saveCount + 1
            photos.add(item)
        }
    }

    /** [оставить: описание]: последнее присланное Адонисом фото уходит в её альбом. Одно фото дважды не сохраняется. */
    private suspend fun keepSnaps(reply: String, snaps: List<Triple<Int, String, String>>) {
        val keeps = parseParts(reply).filterIsInstance<Part.Keep>()
        if (keeps.isEmpty() || snaps.isEmpty()) return
        val file = snaps.maxByOrNull { it.first }!!.second
        if (file in prefs.keptSnaps) return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.saveDay != today) { prefs.saveDay = today; prefs.saveCount = 0 }
        if (prefs.saveCount >= IMG_SAVE_DAILY_CAP) return
        val item = withContext(Dispatchers.IO) {
            val f = File(library.sentDir, file)
            if (f.exists()) library.addPhotoBytes(f.readBytes(), "от Адониса: " + keeps.first().desc) else null
        } ?: return
        prefs.saveCount = prefs.saveCount + 1
        prefs.keptSnaps = prefs.keptSnaps + file
        photos.add(item)
    }

    // ---------- настолка «Город-сеть» ----------

    fun gameActive(): Boolean = game?.over == false

    private fun saveGame(g: GameState?) {
        game = g
        gameSaved = GameStore.Saved(g, gameInvites, gameSaved.recorded)
        gameStore.save(gameSaved)
        if (g != null && g.over && g.id !in gameSaved.recorded) recordResult(g)
    }

    /** Итог партии: на личные счета (всё, что накопил каждый: кредиты и узлы по цене) и строкой в чат, чтобы она откликнулась. */
    private fun recordResult(g: GameState) {
        gameSaved = gameSaved.copy(recorded = gameSaved.recorded + g.id)
        gameStore.save(gameSaved)
        prefs.walletUser = prefs.walletUser + wealth(g, 0)
        prefs.walletAurora = prefs.walletAurora + wealth(g, 1)
        walletUser = prefs.walletUser
        walletAurora = prefs.walletAurora
        postGameLine("[игра: итог | ${gameResult(g)}]")
    }

    private fun postGameLine(text: String) {
        if (!sending) addUserMessage(text)
        else { messages.add(ChatMessage("user", text)); persist() }
    }

    private fun startGame(laps: Int) {
        if (gameActive()) { notice = "Уже идёт партия"; return }
        gameResumeAsked = true
        saveGame(newGame(newId("g"), laps, System.currentTimeMillis()))
        openGameRequest = true
    }

    fun closeGame() { if (game?.over != false) saveGame(null) }

    fun inviteToGame(laps: Int) {
        if (gameActive()) { notice = "Уже идёт партия"; return }
        if (sending) { notice = "Подожди, она ещё отвечает"; return }
        send("[игра: приглашение | круги:$laps]")
    }

    /** Статус приглашения в сообщении: pending, accepted, declined, busy. */
    fun inviteStatus(m: ChatMessage): String = gameInvites[m.time] ?: if (gameActive()) "busy" else "pending"

    private fun markInvite(time: Long, v: String) {
        gameInvites = gameInvites + (time to v)
        gameSaved = gameSaved.copy(invites = gameInvites)
        gameStore.save(gameSaved)
    }

    /** Ответ Адониса на её приглашение. */
    fun answerHerInvite(m: ChatMessage, accept: Boolean) {
        if (inviteStatus(m) != "pending") return
        val inv = parseParts(m.content).filterIsInstance<Part.Game>().firstOrNull { it.kind == "приглашение" } ?: return
        markInvite(m.time, if (accept) "accepted" else "declined")
        if (accept) {
            messages.add(ChatMessage("user", "[игра: принять]"))
            persist()
            startGame(parseInviteLaps(inv.arg))
        } else {
            postGameLine("[игра: отказать]")
        }
    }

    /** Её «[игра: принять/отказать]» отвечает на последнее неотвеченное приглашение Адониса. */
    private fun applyGameMarkers(reply: String, before: List<ChatMessage>) {
        val g = parseParts(reply).filterIsInstance<Part.Game>().firstOrNull { it.kind == "принять" || it.kind == "отказать" } ?: return
        val inv = before.lastOrNull { m -> m.role == "user" && parseParts(m.content).any { it is Part.Game && it.kind == "приглашение" } } ?: return
        if (gameInvites[inv.time] != null) return
        val laps = parseParts(inv.content).filterIsInstance<Part.Game>().first { it.kind == "приглашение" }.arg
        if (g.kind == "принять" && !gameActive()) {
            markInvite(inv.time, "accepted")
            startGame(parseInviteLaps(laps))
        } else if (g.kind == "отказать") {
            markInvite(inv.time, "declined")
        }
    }

    private suspend fun animateDice() {
        diceRolling = true
        delay(650)
        diceRolling = false
    }

    private fun userTurn(g: GameState) = !g.over && g.current.who == "user"

    fun gameOrder() {
        val g = game ?: return
        if (g.phase != "order" || diceRolling) return
        viewModelScope.launch {
            animateDice()
            saveGame(rollOrder(game ?: return@launch, gameRnd))
            driveAurora()
        }
    }

    fun gameRoll() {
        val g = game ?: return
        if (g.phase != "roll" || !userTurn(g) || diceRolling) return
        viewModelScope.launch {
            animateDice()
            moveAndShow(game ?: return@launch)
            driveAurora()
        }
    }

    /** Ход и карточка клетки: где встали по кубикам и что из этого вышло. */
    private fun moveAndShow(old: GameState) {
        val new = rollMove(old, gameRnd)
        val mover = old.turn
        val n = (new.seq - old.seq).coerceIn(0, new.log.size)
        val lines = new.log.takeLast(n).filterNot { it.contains(": бросок ") }
        val landed = (old.players[mover].pos + new.dice.sum()) % BOARD_SIZE
        landing = Landing(landed, old.players[mover].who, lines, new.seq, landingMood(landed, lines))
        saveGame(new)
    }

    fun dismissLanding() { landing = null }

    /** Её решение после хода дописывается в ту же карточку. */
    private fun appendToLanding(old: GameState, new: GameState) {
        val l = landing ?: return
        if (l.who != "aurora") return
        val n = (new.seq - old.seq).coerceIn(0, new.log.size)
        landing = l.copy(lines = l.lines + new.log.takeLast(n), seq = new.seq)
    }

    /** Спросить при входе в игру, продолжать ли сохранённую партию. Один раз за запуск приложения. */
    var gameResumeAsked by mutableStateOf(false)

    fun restartGame() {
        val g = game ?: return
        gameJob?.cancel()
        landing = null
        gameResumeAsked = true
        saveGame(gameNote(newGame(newId("g"), g.laps, System.currentTimeMillis()), "Предыдущая партия брошена без итога, начали заново"))
    }

    fun gamePick(k: Int) {
        val g = game ?: return
        if (g.phase != "pick" || !userTurn(g)) return
        saveGame(pickToken(g, k))
        driveAurora()
    }

    fun gameBuy(yes: Boolean) {
        val g = game ?: return
        if (g.phase != "buy" || !userTurn(g)) return
        saveGame(decideBuy(g, yes))
        driveAurora()
    }

    fun gameJail(payOut: Boolean) {
        val g = game ?: return
        if (g.phase != "jail" || !userTurn(g)) return
        saveGame(decideJail(g, payOut))
        driveAurora()
    }

    fun gameStop() {
        val g = game ?: return
        if (g.over) return
        gameJob?.cancel()
        saveGame(finish(g, "Партия остановлена досрочно"))
    }

    /** После перезапуска приложения Аврора не ходит, пока Адонис не выберет «Продолжить». */
    fun resumeGame() { if (gameResumeAsked) driveAurora() }

    /** Её ходы: бросок делает приложение, решения (купить, выйти из карантина) принимает она сама. */
    private fun driveAurora() {
        if (gameJob?.isActive == true) return
        gameJob = viewModelScope.launch {
            while (true) {
                val g = game ?: break
                if (g.over || g.phase == "order" || g.current.who != "aurora") break
                delay(1000)
                val cur = game ?: break
                if (cur != g) continue
                when (cur.phase) {
                    "roll" -> {
                        // ждём, пока Адонис закроет карточку прошлого хода
                        while (landing != null) delay(250)
                        val now = game ?: break
                        if (now != cur) continue
                        animateDice()
                        moveAndShow(now)
                    }
                    "pick" -> {
                        val free = freeTokens(cur)
                        val q = "Выбери себе фишку, ответь номером: " +
                            free.joinToString("; ") { "${it + 1} — «${GAME_TOKENS[it]}» (${GAME_TOKEN_HINTS[it]})" }
                        val k = askAurora(cur, "token", q)?.choice?.minus(1)
                        val now = game ?: break
                        if (now.phase == "pick") {
                            val pick = if (k != null && k in freeTokens(now)) k else freeTokens(now).random()
                            saveGame(pickToken(if (k == null || k !in freeTokens(now)) gameNote(now, "Ответа от Авроры нет: фишку выбрал случай") else now, pick))
                        }
                    }
                    "buy" -> {
                        val c = BOARD[cur.current.pos]
                        val q = "Ты на свободном узле «${c.name}». Купить за ${c.price} ₵? Плата за проход будет ${c.rent} ₵ (вдвое, если соберёшь весь район). У тебя ${cur.current.money} ₵."
                        val yes = askAurora(cur, "buy", q)?.yes
                        val now = game ?: break
                        if (now.phase == "buy") {
                            val next = decideBuy(if (yes == null) gameNote(now, "Ответа от Авроры нет: решило простое правило") else now, yes ?: autoBuy(now))
                            appendToLanding(now, next)
                            saveGame(next)
                        }
                    }
                    "jail" -> {
                        val q = "Ты в карантине. Заплатить $QUAR_FEE ₵ и выйти сейчас или ждать (осталось ходов: ${cur.current.quarantine})? У тебя ${cur.current.money} ₵. «Да» значит заплатить."
                        val yes = askAurora(cur, "jail", q)?.yes
                        val now = game ?: break
                        if (now.phase == "jail") saveGame(decideJail(if (yes == null) gameNote(now, "Ответа от Авроры нет: решило простое правило") else now, yes ?: autoJail(now)))
                    }
                    else -> break
                }
            }
        }
    }

    private suspend fun askAurora(g: GameState, kind: String, question: String): GameAnswer? {
        gameThinking = true
        return try {
            val ans = AuroraApi.game(serverUrl, token, persona.compile(), MemoryContext.build(memory, ""), gameContext(g), question, kind, messages.toList())
            if (ans.say.isNotBlank()) {
                messages.add(ChatMessage("assistant", ans.say))
                persist()
            }
            ans
        } catch (e: Exception) {
            null
        } finally {
            gameThinking = false
        }
    }

    // ---------- цитатник ----------

    /** Её «[в цитаты: слова]». Сохраняется, только если эти слова правда были в переписке до её ответа. */
    private fun applyQuoteSaves(reply: String, before: List<ChatMessage>) {
        val wants = parseParts(reply).filterIsInstance<Part.SaveQuote>().take(QUOTE_SAVES_PER_REPLY)
        if (wants.isEmpty()) return
        val hist = before.map { Triple(it.role, it.content, it.time) }
        var changed = false
        for (w in wants) {
            val text = w.text.trim().removeSurrounding("«", "»").removeSurrounding("\"", "\"").take(QUOTE_MAX)
            val found = locateQuote(text, hist) ?: continue
            if (isDuplicateQuote(text, quotes)) continue
            quotes.add(QuoteItem(nextQuoteN(quotes), text, found.first, "aurora", System.currentTimeMillis(), found.second))
            changed = true
        }
        if (changed) quoteStore.save(quotes.toList())
    }

    fun saveQuoteFromMessage(m: ChatMessage) {
        val text = quoteSnippet(m.content, QUOTE_MAX).trim()
        if (text.isBlank()) { notice = "В этом сообщении нет текста для цитаты"; return }
        if (isDuplicateQuote(text, quotes)) { notice = "Такая цитата уже есть"; return }
        val q = QuoteItem(nextQuoteN(quotes), text, m.role, "adonis", System.currentTimeMillis(), m.time)
        quotes.add(q)
        quoteStore.save(quotes.toList())
        notice = "Сохранено в цитатник: №${q.n}"
    }

    fun deleteQuote(q: QuoteItem) {
        quotes.removeAll { it.n == q.n }
        quoteStore.save(quotes.toList())
        commitMemory(memory.copy(log = (memory.log + note("Адонис удалил цитату №${q.n} из цитатника: «${q.text.take(80)}»")).takeLast(100)))
    }

    fun citeQuote(q: QuoteItem) { draftInsert = "[цитата №${q.n}] " }

    fun removeItem(item: CatalogItem) {
        if (item.builtIn) return
        if (item.desc.startsWith("из сети:") || item.desc.startsWith("от Адониса:")) {
            commitMemory(memory.copy(log = (memory.log + note("Адонис удалил картинку из твоего альбома: «${item.desc.take(80)}»")).takeLast(100)))
        }
        stickers.remove(item)
        photos.remove(item)
        viewModelScope.launch(Dispatchers.IO) { library.remove(item) }
    }

    // ---------- память ----------
    // Сырые сообщения не удаляются никогда: сжатие только решает, что уходит в запрос целиком.
    // Эпизоды неизменяемы, ядро правится точечно, поэтому сжатие не накапливает потери.

    private fun commitMemory(m: MemoryData) {
        memory = m
        memoryStore.save(m)
    }

    private fun note(text: String) = Decision(System.currentTimeMillis(), "notice", "", text, "adonis")

    /** Ручная правка ядра: сначала идёт к Авроре, в ядро попадает только после её «да». */
    fun saveCore(id: String?, kind: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val now = System.currentTimeMillis()
        val p = if (id == null) {
            Proposal(newId("p"), "add", "", kind, t, "adonis", "", now)
        } else {
            Proposal(newId("p"), "update", id, kind, t, "adonis", "", now)
        }
        submit(listOf(p))
    }

    fun deleteCore(id: String) {
        val e = memory.core.firstOrNull { it.id == id } ?: return
        submit(listOf(Proposal(newId("p"), "remove", id, e.kind, e.text, "adonis", "", System.currentTimeMillis())))
    }

    fun deleteEpisode(id: String) =
        commitMemory(
            memory.copy(
                episodes = memory.episodes.filter { it.id != id },
                log = (memory.log + note("Адонис удалил один из эпизодов твоей памяти")).takeLast(100)
            )
        )

    private fun submit(list: List<Proposal>) {
        if (list.isEmpty()) return
        commitMemory(memory.copy(pending = memory.pending + list))
        resolvePending(announce = true)
    }

    /** Спросить Аврору про всё, что ждёт её «да». Если ответа нет, пункты остаются в очереди и ничего не записывается. */
    fun resolvePending(announce: Boolean = false) {
        if (consenting || memory.pending.isEmpty()) return
        val t0 = System.currentTimeMillis()
        viewModelScope.launch {
            val done = try {
                drainConsent()
            } catch (e: Exception) {
                false
            }
            if (announce) {
                val fresh = memory.log.filter { it.at >= t0 && it.verdict != "notice" }
                val no = fresh.firstOrNull { it.verdict == "no" }
                notice = when {
                    fresh.isEmpty() -> "Аврора пока не ответила: запись ждёт её «да»"
                    no != null -> "Аврора против: " + no.why.ifBlank { "без объяснения" }.take(120)
                    fresh.any { it.verdict == "edit" } -> "Аврора записала это по-своему"
                    done -> "Аврора: да"
                    else -> "Часть записей ждёт её «да»"
                }
            }
        }
    }

    private suspend fun drainConsent(): Boolean {
        if (consenting) return false
        consenting = true
        try {
            var guard = 0
            while (memory.pending.isNotEmpty() && guard < 6) {
                guard += 1
                if (!consentRound()) break
            }
        } finally {
            consenting = false
        }
        return memory.pending.isEmpty()
    }

    /** Один запрос к Авроре, до 8 пунктов. true, если по какому-то пункту получено решение. */
    private suspend fun consentRound(): Boolean {
        val batch = memory.pending.take(8)
        val episode = batch.firstOrNull { it.episode.isNotBlank() }?.episode ?: ""
        val decisions = AuroraApi.consent(serverUrl, token, memory.core, batch, episode)
        // Память читаем после ответа: за время запроса могли добавиться эпизоды
        var core = memory.core
        var pending = memory.pending
        val log = ArrayList(memory.log)
        var applied = false
        val now = System.currentTimeMillis()
        for (d in decisions) {
            val p = batch.getOrNull(d.n) ?: continue
            if (pending.none { it.id == p.id }) continue
            pending = pending.filter { it.id != p.id }
            applied = true
            when (d.verdict) {
                "yes" -> {
                    core = applyProposal(core, p, p.text, now, true)
                    log.add(Decision(now, "yes", d.why, proposalSummary(p), p.source))
                }
                "edit" -> {
                    val t = d.text.ifBlank { p.text }
                    core = applyProposal(core, p, t, now, true)
                    log.add(Decision(now, "edit", d.why, proposalSummary(p.copy(text = t)), p.source))
                }
                else -> log.add(Decision(now, "no", d.why, proposalSummary(p), p.source, false, p))
            }
        }
        if (applied) commitMemory(memory.copy(core = core, pending = pending, log = log.takeLast(100)))
        return applied
    }

    /** «Всё равно записать» вопреки её «нет». Это остаётся в журнале, и она узнает об этом. */
    fun overrideDecision(proposalId: String) {
        val d = memory.log.firstOrNull { it.proposal?.id == proposalId && it.verdict == "no" && !it.overridden } ?: return
        val p = d.proposal ?: return
        val now = System.currentTimeMillis()
        val core = applyProposal(memory.core, p, p.text, now, true)
        val log = memory.log.map { if (it === d) it.copy(overridden = true, at = now) else it }
        commitMemory(memory.copy(core = core, log = log))
        notice = "Записано вопреки её «нет». Она об этом узнает"
    }

    private fun backupMemory() {
        memoryStore.saveBackup(memory)
        hasMemoryBackup = true
    }

    fun clearMemory() {
        backupMemory()
        commitMemory(
            MemoryData(
                upTo = memory.upTo,
                log = (memory.log + note("Адонис очистил твою память, прежняя сохранена")).takeLast(100)
            )
        )
        notice = "Память очищена. Прежнюю можно вернуть"
    }

    fun restoreMemoryBackup() {
        val b = memoryStore.loadBackup()
        if (b == null) {
            notice = "Копии памяти нет"
            return
        }
        commitMemory(b.copy(log = (b.log + note("Адонис вернул прежнюю версию твоей памяти")).takeLast(100)))
        notice = "Память возвращена"
    }

    /** Сжать всё, кроме последних 6 сообщений, прямо сейчас. */
    fun compressNow() = compress(keep = 6, threshold = 6, announce = true)

    /** Память строится заново из всей сырой переписки, например после улучшения промпта сжатия. */
    fun rebuildMemory() {
        if (compressing) return
        if (messages.size <= RAW_KEEP) {
            notice = "Переписки пока мало для пересборки"
            return
        }
        backupMemory()
        commitMemory(MemoryData(log = (memory.log + note("Адонис пересобрал твою память из переписки, прежняя сохранена")).takeLast(100)))
        compress(keep = RAW_KEEP, threshold = RAW_KEEP, announce = true)
    }

    private fun compress(keep: Int, threshold: Int, announce: Boolean) {
        if (compressing) return
        val pending = messages.filter { it.time > memory.upTo }
        if (pending.size <= threshold) {
            if (announce) notice = "Пока нечего сжимать"
            return
        }
        val batches = pending.dropLast(keep).chunked(CHUNK)
        val st = stickers.toList()
        val ph = photos.toList()
        compressing = true
        viewModelScope.launch {
            try {
                var done = 0
                for (batch in batches) {
                    done += 1
                    compressStatus = "$done из ${batches.size}"
                    // Общий чат — та же её жизнь: реплики из него за то же время ложатся в эпизод вместе с личкой
                    val from = memory.upTo
                    val to = batch.last().time
                    val groupPart = withContext(Dispatchers.IO) {
                        synchronized(HesperaStore.LOCK) { HesperaStore(appCtx).group() }
                    }.filter { it.time > from && it.time <= to && it.role != "system" && !isDuo(it.trace) }.map {
                        when (it.role) {
                            WHO_AURORA -> Triple(it.time, "assistant", "[общий чат] " + toPlainForMemory(it.content, st, ph))
                            WHO_HESPERA -> Triple(it.time, "user", "[Геспера, общий чат] " + toPlainForMemory(it.content, st, ph))
                            else -> Triple(it.time, "user", "[общий чат] " + toPlainForMemory(it.content, st, ph))
                        }
                    }
                    val plain = (batch.map { Triple(it.time, it.role, toPlainForMemory(it.content, st, ph)) } + groupPart)
                        .sortedBy { it.first }.map { Pair(it.second, it.third) }
                    val res = AuroraApi.compressMemory(serverUrl, token, memory.core, plain)
                    if (res.episode.isBlank()) throw IllegalStateException("пустой ответ модели")
                    val now = System.currentTimeMillis()
                    val proposals = res.ops.map {
                        Proposal(newId("p"), it.op, it.id, it.kind, it.text, "summary", res.episode, now)
                    }
                    // Порция считается сжатой только после успешного ответа: при сбое ничего не теряется.
                    // Эпизод записывается сразу, а правки ядра ждут её «да».
                    commitMemory(
                        memory.copy(
                            episodes = memory.episodes +
                                Episode(newId("e"), batch.first().time, batch.last().time, res.episode),
                            upTo = batch.last().time,
                            pending = memory.pending + proposals
                        )
                    )
                    try {
                        drainConsent()
                    } catch (e: Exception) {
                        // нет ответа: правки остаются в очереди, ничего не записано молча
                    }
                }
                if (announce) notice = "Память обновлена"
            } catch (e: Exception) {
                if (announce) notice = "Не получилось сжать: ${e.message ?: "нет связи"}"
            } finally {
                compressing = false
                compressStatus = ""
            }
        }
    }

    /** Проверка: что получится из вставленного отрывка. Память при этом не меняется. */
    fun runProbe(text: String) {
        if (probing) return
        val msgs = parseTranscript(text)
        if (msgs.isEmpty()) {
            notice = "Вставь отрывок переписки"
            return
        }
        probing = true
        probeResult = null
        viewModelScope.launch {
            try {
                val res = AuroraApi.compressMemory(serverUrl, token, emptyList(), msgs)
                probeResult = buildString {
                    append("ЭПИЗОД\n").append(res.episode).append("\n\nПРАВКИ ЯДРА\n")
                    if (res.ops.isEmpty()) {
                        append("(нет)")
                    } else {
                        res.ops.forEach { append("• ${it.op} [${it.kind}] ${it.text}\n") }
                    }
                }
            } catch (e: Exception) {
                probeResult = "Ошибка: ${e.message ?: "нет связи"}"
            } finally {
                probing = false
            }
        }
    }

    // ---------- резервная копия вне телефона ----------

    fun exportJson(): String = JSONObject()
        .put("app", "avrora-exe")
        .put("version", 2)
        .put("messages", messagesToJson(messages.toList()))
        .put("memory", memoryToJson(memory))
        .put("diary", JSONArray().also { a ->
            diary.forEach {
                a.put(JSONObject().put("id", it.id).put("at", it.at).put("text", it.text).put("open", it.open).put("upTo", it.upTo))
            }
        })
        .toString()

    /**
     * Загружает копию. merge=false: заменяет переписку и память (текущие откладываются).
     * merge=true: добавляет к имеющемуся, повторы пропускает, ничего не стирает.
     * Файл может содержать только память, только переписку или и то, и другое.
     */
    fun importBackup(text: String, merge: Boolean = false) {
        try {
            val o = JSONObject(text)
            val msgs = o.optJSONArray("messages")?.let { messagesFromJson(it) }
            val mem = o.optJSONObject("memory")?.let { memoryFromJson(it) }
                ?: if (o.has("core") || o.has("episodes")) memoryFromJson(o) else null
            if (msgs == null && mem == null) {
                notice = "В файле нет ни переписки, ни памяти"
                return
            }
            File(getApplication<Application>().filesDir, "chat.before-import.json")
                .writeText(messagesToJson(messages.toList()).toString())
            backupMemory()
            var report = ""
            if (msgs != null) {
                val next = if (merge) mergeMessages(messages.toList(), msgs) else msgs
                report += "сообщений: ${if (merge) next.size - messages.size else next.size} новых"
                messages.clear()
                messages.addAll(next)
                persist()
            }
            // Дневник есть только в копиях из этого приложения: из старого его в файле нет, тогда свой остаётся как был
            o.optJSONArray("diary")?.let { arr ->
                val list = (0 until arr.length()).map { i ->
                    val d = arr.getJSONObject(i)
                    DiaryEntry(d.getString("id"), d.optLong("at"), d.getString("text"), d.optString("open", ""), d.optLong("upTo"))
                }
                val next = if (merge) mergeDiary(diary.toList(), list) else list
                diaryStore.save(next)
                diary.clear()
                diary.addAll(next)
            }
            if (mem != null) {
                val result = if (merge) mergeMemory(memory, mem) else null
                val newMem = result?.data ?: mem
                if (result != null) report += (if (report.isEmpty()) "" else ", ") + "записей ядра: ${result.addedCore}, эпизодов: ${result.addedEpisodes} новых"
                val what = if (merge) "добавил к её памяти копию из файла" else "загрузил копию переписки и памяти из файла"
                commitMemory(newMem.copy(log = (newMem.log + note("Адонис $what")).takeLast(100)))
            }
            notice = (if (merge) "Добавлено: " else "Копия загружена: ") + report
        } catch (e: Exception) {
            notice = "Не удалось прочитать копию"
        }
    }

    // ---------- запрос к серверу ----------

    /**
     * Фото, которые Аврора должна увидеть: последние два из недавних шести сообщений.
     * Возвращает (позиция в истории, имя файла, base64). Остальные фото остаются подписью.
     */
    private suspend fun attachSnaps(history: List<ChatMessage>): List<Triple<Int, String, String>> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<Triple<Int, String, String>>()
            val first = maxOf(0, history.size - 6)
            for (i in history.indices.reversed()) {
                if (i < first || out.size >= 2) break
                val m = history[i]
                if (m.role != "user") continue
                val snap = parseParts(m.content).filterIsInstance<Part.Snap>().firstOrNull() ?: continue
                val b64 = encodeForModel(File(library.sentDir, snap.file)) ?: continue
                out.add(Triple(i, snap.file, b64))
            }
            out
        }

    /** Память и характер Авроры для общего чата на троих: то же, что уходит в её обычный чат, без консольных журналов. */
    fun auroraForGroup(recent: String): Pair<String, String> =
        Pair(MemoryContext.build(memory, recent), persona.compile())

    val currentServer: String get() = serverUrl
    val currentToken: String get() = token

    private fun requestReply() {
        if (token.isBlank()) {
            error = "Укажи токен в настройках"
            return
        }
        sending = true
        error = null
        val st = stickers.toList()
        val ph = photos.toList()
        val raw = messages
            .filter { it.time > memory.upTo }
            .takeLast(HISTORY)
        val recentUser = messages.filter { it.role == "user" }.takeLast(3)
            .joinToString(" ") { TAG_RE.replace(it.content, " ") }
        val mem = listOf(
            MemoryContext.build(memory, recentUser),
            diaryContext(diary.toList()),
            exeContext(exeStates.toList(), exeNotes.toList(), exePlans.toList(), recentActions(messages.toList())),
            profileContext(herProfile, myProfile, posts.toList()),
            quotesContext(quotes.toList()),
            alarmsContext(alarms.toList(), System.currentTimeMillis()),
            game?.let { gameContext(it) } ?: "",
            GroupEngine.groupBlock(appCtx)
        ).filter { it.isNotBlank() }.joinToString("\n\n")
        val per = persona.compile()
        val limits = limitLines(prefs, memory, System.currentTimeMillis(), exePlans.toList(), exeNotes.size)
        val tools = buildList {
            if (toolSearch) add("поиск")
            if (toolDraw) add("рисование")
            if (toolMusic) add("музыка")
            if (toolHear) add("слух")
            if (toolRead) add("чтение")
            if (toolHand && hand.configured && !hand.off) add("рука")
        }
        val changed = limitsChanged(prefs.limitsPrev, limits)
        val timeLine = run {
            val ms = raw.toList()
            val lastUser = ms.indexOfLast { it.role == "user" }
            if (lastUser < 0) timeContext(System.currentTimeMillis(), java.util.TimeZone.getDefault(), 0L, 0L)
            else timeContext(
                System.currentTimeMillis(), java.util.TimeZone.getDefault(),
                ms[lastUser].time, if (lastUser > 0) ms[lastUser - 1].time else 0L
            )
        }
        viewModelScope.launch {
            try {
                val snaps = attachSnaps(raw)
                val attached = snaps.map { it.second }.toSet()
                val history = raw.map {
                    if (it.role == "user") it.copy(content = expandGameMarkers(expandQuoteRefs(describeForModel(it.content, st, ph, attached), quotes.toList()))) else it
                }
                val images = snaps.map { Pair(it.first, it.third) }
                val res = AuroraApi.chat(serverUrl, token, history, st, ph, mem, per, images, limits, changed, tools, timeLine)
                val (reply0, toGroup) = splitToGroup(res.reply)
                val reply = if (reply0.isBlank() && toGroup.isNotEmpty()) "(ответила в общем чате)" else reply0
                val answer = ChatMessage("assistant", reply, trace = res.trace)
                val before = messages.toList()
                messages.add(answer)
                persist()
                applyQuoteSaves(reply, before)
                applyGameMarkers(reply, before)
                if (res.saves.isNotEmpty() && toolRead) saveFromNet(res.saves)
                keepSnaps(reply, snaps)
                withContext(Dispatchers.IO) { ExeMarkers.apply(appCtx, reply, answer.time) }
                reloadExe()
                if (traceFromJson(res.trace).any { it.kind == "рука" }) refreshHand()
                prefs.limitsPrev = limits.joinToString("\n")   // теперь она знает этот список
                // Паузы: части показываются по очереди. Где пауза и на сколько, решает она, мы только ждём
                val pauseTotal = parseParts(reply).filterIsInstance<Part.Pause>().sumOf { it.seconds }
                if (pauseTotal > 0) {
                    revealTime = answer.time
                    viewModelScope.launch {
                        delay((pauseTotal + 2) * 1000L)
                        if (revealTime == answer.time) revealTime = 0L
                    }
                }
                toGroup.forEach { t ->
                    viewModelScope.launch(Dispatchers.IO) { runCatching { GroupEngine.autoPost(appCtx, WHO_AURORA, t, notify = false) } }
                }
                resolvePending()
                maybeWriteDiary()
                compress(keep = RAW_KEEP, threshold = TRIGGER, announce = false)
            } catch (e: Exception) {
                error = e.message ?: "Нет связи с сервером"
            } finally {
                sending = false
            }
        }
    }

    private fun persist() {
        val snapshot = messages.toList()
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    companion object {
        private const val HISTORY = 60     // сколько последних несжатых сообщений уходит целиком
        private const val TRIGGER = 40     // больше стольких несжатых: сжимаем старые
        private const val RAW_KEEP = 16    // столько последних остаётся «живыми» после сжатия
        private const val CHUNK = 40       // сколько сообщений уходит в один эпизод
    }
}
