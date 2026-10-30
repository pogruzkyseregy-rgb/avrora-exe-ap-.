package ru.avrora.exe

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- папки на машине руки ----------
// Аврора работает в ~/work, Геспера в ~/hespera. Сервер смотрит туда по ssh и отдаёт список и файлы;
// выйти за пределы этих папок нельзя. Это только просмотр: ничего не меняется и в их лимит команд не входит.

data class RemoteFile(val name: String, val dir: Boolean, val size: Long, val mtime: Double)

object FilesApi {
    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun list(ctx: Context, who: String, path: String): Pair<List<RemoteFile>, String> {
        val p = AppPrefs(ctx)
        val r = AuroraApi.get(p.serverUrl, "/files/list?who=$who&path=${q(path)}", p.token)
        val a = r.optJSONArray("items")
        val items = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }.map {
            RemoteFile(it.optString("name"), it.optBoolean("dir"), it.optLong("size"), it.optDouble("mtime"))
        }
        return Pair(items, r.optString("note"))
    }

    /** Файл к себе в кэш (повторно не скачивается, пока не изменился размер). */
    suspend fun fetch(ctx: Context, who: String, path: String, f: RemoteFile): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "vps/$who").apply { mkdirs() }
        val out = File(dir, path.replace('/', '_'))
        if (out.exists() && out.length() == f.size) return@withContext out
        val p = AppPrefs(ctx)
        out.writeBytes(AuroraApi.getBytes(p.serverUrl, "/files/get?who=$who&path=${q(path)}", p.token))
        out
    }

    fun mimeOf(name: String): String =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /** В «Загрузки/AVRORA/<чья папка>». */
    fun saveToDownloads(ctx: Context, src: File, name: String, who: String): Boolean = runCatching {
        val sub = when (who) { WHO_HESPERA -> "Hespera"; WHO_EST -> "Est"; else -> "Aurora" }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(name))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AVRORA/" + sub)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return false
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AVRORA/$sub").apply { mkdirs() }
            src.copyTo(File(dir, name), overwrite = true)
        }
        true
    }.getOrDefault(false)
}

private val fileFmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))
private val TEXT_EXT = setOf("txt", "md", "py", "sh", "json", "js", "ts", "kt", "html", "css", "xml", "yml", "yaml", "toml",
    "ini", "cfg", "conf", "log", "csv", "abc", "c", "h", "cpp", "java", "go", "rs", "sql", "env", "service", "gitignore")
private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif")
private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "m4a", "flac")

private fun human(size: Long): String = when {
    size >= 1_000_000 -> "%.1f МБ".format(size / 1_000_000.0)
    size >= 1_000 -> "%.0f КБ".format(size / 1_000.0)
    else -> "$size Б"
}

/** Браузер папки во весь экран. who: aurora — ~/work, hespera — ~/hespera. */
@Composable
fun FileBrowserDialog(who: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val accent = when (who) { WHO_HESPERA -> HespColor; WHO_EST -> EstColor; else -> Glow }
    var path by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Pair<RemoteFile, String>?>(null) }   // файл и его путь
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(path, reload) {
        loading = true
        error = null
        runCatching { FilesApi.list(ctx, who, path) }
            .onSuccess { (list, n) -> items = list; note = n }
            .onFailure { error = it.message ?: "нет связи" }
        loading = false
    }
    fun up() { path = path.substringBeforeLast('/', "") }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { if (open != null) open = null else if (path.isNotEmpty()) up() else onClose() }
        Box(Modifier.fillMaxSize().background(Night)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Panel).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (open != null) open = null else if (path.isNotEmpty()) up() else onClose() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = TextDim)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(when (who) { WHO_HESPERA -> "ГЕСПЕРА · ~/hespera"; WHO_EST -> "ЭСТ · ~/est"; else -> "АВРОРА · ~/work" }, color = accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            "/" + (open?.second ?: path), color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { reload++ }) { Text("↻", color = TextDim, fontSize = 18.sp) }
                }
                GradientLine()
                val o = open
                if (o != null) {
                    FileView(who, o.second, o.first, accent)
                } else {
                    when {
                        loading -> Text("загружаю…", color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
                        error != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Не получилось открыть: $error", color = TextMain, fontSize = 14.sp)
                            TextButton(onClick = { reload++ }) { Text("Повторить", color = accent) }
                        }
                        items.isEmpty() -> Text(note.ifBlank { "Папка пустая." }, color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(items) { f ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        val full = if (path.isEmpty()) f.name else "$path/${f.name}"
                                        if (f.dir) path = full else open = Pair(f, full)
                                    }.padding(horizontal = 16.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (f.dir) "▸" else "·", color = accent, fontSize = 16.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(22.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(f.name + if (f.dir) "/" else "", color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            (if (f.dir) "папка" else human(f.size)) + " · " + fileFmt.format(Date((f.mtime * 1000).toLong())),
                                            color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Box(Modifier.fillMaxWidth().padding(start = 38.dp).background(Edge.copy(alpha = 0.5f)).padding(top = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Один файл: текст, картинка, звук или просто «скачать». */
@Composable
private fun FileView(who: String, path: String, f: RemoteFile, accent: Color) {
    val ctx = LocalContext.current
    val ext = f.name.substringAfterLast('.', "").lowercase()
    var file by remember(path) { mutableStateOf<File?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var text by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        runCatching { FilesApi.fetch(ctx, who, path, f) }
            .onSuccess { got ->
                file = got
                val small = got.length() <= 400_000
                if (small && (ext in TEXT_EXT || ext.isEmpty() || f.name.startsWith("."))) {
                    text = withContext(Dispatchers.IO) { got.readText() }
                } else if (small && ext !in IMAGE_EXT && ext !in AUDIO_EXT) {
                    // неизвестное расширение: если это текст — покажем текстом
                    val bytes = withContext(Dispatchers.IO) { got.readBytes() }
                    if (bytes.none { it == 0.toByte() }) text = String(bytes)
                }
            }
            .onFailure { error = it.message ?: "нет связи" }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.name, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val got = file
            if (got != null) {
                TextButton(onClick = {
                    val ok = FilesApi.saveToDownloads(ctx, got, f.name, who)
                    Toast.makeText(ctx, if (ok) "Сохранено в «Загрузки/AVRORA»" else "Не получилось сохранить", Toast.LENGTH_SHORT).show()
                }) { Text("↓ Скачать", color = accent) }
            }
        }
        Text(human(f.size) + " · " + fileFmt.format(Date((f.mtime * 1000).toLong())), color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        val got = file
        when {
            error != null -> Text("Не получилось открыть: $error", color = TextMain, fontSize = 14.sp)
            got == null -> Text("загружаю…", color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            ext in IMAGE_EXT -> AsyncImage(model = got, contentDescription = f.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().weight(1f))
            ext in AUDIO_EXT -> {
                val playing = TrackPlayer.playingId == path
                TextButton(onClick = { TrackPlayer.toggle(path, got) }) {
                    Text(if (playing && !TrackPlayer.paused) "❚❚  Пауза" else "▶  Слушать", color = accent, fontSize = 16.sp)
                }
            }
            text != null -> SelectionContainer(Modifier.weight(1f)) {
                Text(
                    text ?: "", color = TextMain, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxSize().background(Terminal).border(1.dp, Edge).padding(10.dp)
                        .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                )
            }
            else -> Text("Этот файл здесь не открыть — его можно скачать.", color = TextDim, fontSize = 13.sp)
        }
    }
}

/** Блок досье «Папка на сервере». */
fun filesItem(who: String, accent: Color, onOpen: () -> Unit): DossierItem =
    DossierItem("files", "ПАПКА НА СЕРВЕРЕ", when (who) { WHO_HESPERA -> "~/hespera"; WHO_EST -> "~/est"; else -> "~/work" }, accent,
        preview = { Text("▸ ▸ ·", color = accent, fontSize = 18.sp, fontFamily = FontFamily.Monospace) }
    ) {
        DetailHint("Что она сделала на своей машине: файлы, заметки, код. Только просмотр — можно открыть и скачать к себе.")
        TextButton(onClick = onOpen) { Text("Открыть папку", color = accent) }
    }
