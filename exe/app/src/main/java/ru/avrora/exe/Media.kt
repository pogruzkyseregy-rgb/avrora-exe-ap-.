package ru.avrora.exe

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// ---------- картинки и треки, которые они сделали ----------
// В тексте сообщения стоит метка [медиа: id | название]. Файл лежит на сервере; при первом показе
// приложение скачивает его к себе и дальше показывает из своей папки, без сети.

object MediaCache {
    fun file(ctx: Context, id: String): File = File(File(ctx.filesDir, "media").apply { mkdirs() }, id)

    fun cached(ctx: Context, id: String): File? = file(ctx, id).takeIf { it.exists() && it.length() > 0 }

    suspend fun fetch(ctx: Context, id: String): File = withContext(Dispatchers.IO) {
        cached(ctx, id) ?: run {
            val p = AppPrefs(ctx)
            val bytes = AuroraApi.getBytes(p.serverUrl, "/media/$id", p.token)
            if (bytes.isEmpty()) throw java.io.IOException("пустой файл")
            val f = file(ctx, id)
            val tmp = File(f.path + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(f)
            f
        }
    }

    /** Байты картинки из чего угодно, что показывает чат: файл, картинка из приложения, адрес в галерее. */
    private fun readModel(ctx: Context, model: Any): ByteArray? = runCatching {
        when (model) {
            is File -> model.readBytes()
            is android.net.Uri -> ctx.contentResolver.openInputStream(model)?.use { it.readBytes() }
            is String -> when {
                model.startsWith("file:///android_asset/") -> ctx.assets.open(model.removePrefix("file:///android_asset/")).use { it.readBytes() }
                model.startsWith("content://") -> ctx.contentResolver.openInputStream(android.net.Uri.parse(model))?.use { it.readBytes() }
                else -> File(model.removePrefix("file://")).takeIf { it.exists() }?.readBytes()
            }
            else -> null
        }
    }.getOrNull()

    private fun imageType(b: ByteArray): Pair<String, String> = when {
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "png" to "image/png"
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg" to "image/jpeg"
        b.size > 12 && String(b, 8, 4) == "WEBP" -> "webp" to "image/webp"
        b.size > 3 && String(b, 0, 3) == "GIF" -> "gif" to "image/gif"
        else -> "png" to "image/png"
    }

    /** Картинка — в галерею, в альбом AVRORA. */
    fun saveImage(ctx: Context, model: Any, title: String = ""): Boolean = runCatching {
        val bytes = readModel(ctx, model) ?: return false
        val (ext, mime) = imageType(bytes)
        val base = title.replace(Regex("[\\/:*?\"<>|\n]"), " ").trim().take(40).ifBlank { "avrora" }
        val name = base + "_" + System.currentTimeMillis() + "." + ext
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AVRORA")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AVRORA").apply { mkdirs() }
            File(dir, name).writeBytes(bytes)
        }
        true
    }.getOrDefault(false)

    /** Трек в «Загрузки», чтобы он остался и вне приложения. */
    fun saveToDownloads(ctx: Context, src: File, title: String): Boolean = runCatching {
        val name = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "трек" }.take(60) + ".mp3"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AVRORA")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AVRORA").apply { mkdirs() }
            src.copyTo(File(dir, name), overwrite = true)
        }
        true
    }.getOrDefault(false)
}

/** Один плеер на всё приложение: запускаешь новый трек — прежний останавливается. */
object TrackPlayer {
    var playingId by mutableStateOf<String?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    private var mp: MediaPlayer? = null

    fun toggle(id: String, file: File) {
        val cur = mp
        if (playingId == id && cur != null) {
            if (cur.isPlaying) { cur.pause(); paused = true } else { cur.start(); paused = false }
            return
        }
        stop()
        runCatching {
            mp = MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playingId = id
            paused = false
        }.onFailure { stop() }
    }

    fun stop() {
        runCatching { mp?.release() }
        mp = null
        playingId = null
        paused = false
    }

    fun position(): Int = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)
    fun seek(fraction: Float) {
        val m = mp ?: return
        runCatching { m.seekTo((m.duration * fraction.coerceIn(0f, 1f)).toInt()) }
    }
}

private fun mmss(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun MediaPart(p: Part.Media, accent: Color, onOpen: (Any) -> Unit) {
    val ctx = LocalContext.current
    var file by remember(p.id) { mutableStateOf(MediaCache.cached(ctx, p.id)) }
    var failed by remember(p.id) { mutableStateOf<String?>(null) }
    var attempt by remember(p.id) { mutableStateOf(0) }
    LaunchedEffect(p.id, attempt) {
        if (file == null) {
            runCatching { MediaCache.fetch(ctx, p.id) }
                .onSuccess { file = it; failed = null }
                .onFailure { failed = it.message ?: "нет связи" }
        }
    }
    if (p.audio) TrackCard(p, file, failed, accent) { attempt++ }
    else ImageCard(p, file, failed, accent, onOpen) { attempt++ }
}

@Composable
private fun ImageCard(p: Part.Media, file: File?, failed: String?, accent: Color, onOpen: (Any) -> Unit, retry: () -> Unit) {
    Box(
        Modifier.padding(vertical = 4.dp).width(240.dp).aspectRatio(1f).clip(panelShape())
            .background(Terminal).border(1.dp, accent.copy(alpha = 0.5f), panelShape())
            .clickable { if (file != null) onOpen(file) else retry() },
        contentAlignment = Alignment.Center
    ) {
        when {
            file != null -> {
                AsyncImage(model = file, contentDescription = p.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                SaveBadge(Modifier.align(Alignment.BottomEnd)) { ctx -> MediaCache.saveImage(ctx, file, p.title) }
            }
            failed != null -> Text("картинка не загрузилась\nнажми, чтобы повторить", color = TextDim, fontSize = 12.sp)
            else -> Text("рисунок загружается…", color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun TrackCard(p: Part.Media, file: File?, failed: String?, accent: Color, retry: () -> Unit) {
    val ctx = LocalContext.current
    val playing = TrackPlayer.playingId == p.id
    val total = remember(file) {
        file?.let { f ->
            runCatching {
                MediaMetadataRetriever().run {
                    setDataSource(f.path)
                    val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                    release(); d
                }
            }.getOrDefault(0)
        } ?: 0
    }
    var pos by remember(p.id) { mutableStateOf(0) }
    LaunchedEffect(playing, TrackPlayer.paused) {
        while (playing && !TrackPlayer.paused) {
            pos = TrackPlayer.position()
            delay(250)
        }
        if (!playing) pos = 0
    }
    Column(
        Modifier.padding(vertical = 4.dp).width(260.dp).clip(panelShape()).background(Terminal)
            .border(1.dp, accent.copy(alpha = 0.5f), panelShape()).padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (file != null) accent else Edge)
                    .clickable {
                        if (file != null) TrackPlayer.toggle(p.id, file) else retry()
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val c = if (AppTheme.current.light) Color.White else Night
                    if (playing && !TrackPlayer.paused) {
                        drawRect(c, Offset(size.width * 0.12f, 0f), Size(size.width * 0.28f, size.height))
                        drawRect(c, Offset(size.width * 0.6f, 0f), Size(size.width * 0.28f, size.height))
                    } else {
                        val t = Path().apply { moveTo(size.width * 0.18f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.18f, size.height); close() }
                        drawPath(t, c)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title.ifBlank { "трек" }, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        failed != null && file == null -> "не загрузился — нажми ▶"
                        file == null -> "загружается…"
                        else -> mmss(if (playing) pos else 0) + " / " + mmss(total)
                    },
                    color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }
            if (file != null) {
                Text("↓", color = accent, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp).clickable {
                    val ok = MediaCache.saveToDownloads(ctx, file, p.title)
                    Toast.makeText(ctx, if (ok) "Сохранено в «Загрузки/AVRORA»" else "Не получилось сохранить", Toast.LENGTH_SHORT).show()
                })
            }
        }
        // полоса: можно нажать, чтобы перемотать
        val frac = if (total > 0 && playing) pos.toFloat() / total else 0f
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(14.dp)
                .pointerInput(playing) { detectTapGestures { o -> if (playing) TrackPlayer.seek(o.x / size.width) } },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                drawRect(accent.copy(alpha = 0.2f), Offset.Zero, size)
                drawRect(accent, Offset.Zero, Size(size.width * frac, size.height))
            }
        }
    }
}

/** Кружок «↓» поверх картинки: сохранить в галерею. */
@Composable
fun SaveBadge(modifier: Modifier = Modifier, save: (Context) -> Boolean) {
    val ctx = LocalContext.current
    Box(
        modifier.padding(6.dp).size(34.dp).clip(CircleShape).background(Color(0xAA000000))
            .clickable {
                val ok = save(ctx)
                Toast.makeText(ctx, if (ok) "Сохранено в галерею: Pictures/AVRORA" else "Не получилось сохранить", Toast.LENGTH_SHORT).show()
            },
        contentAlignment = Alignment.Center
    ) { Text("↓", color = Color.White, fontSize = 18.sp) }
}

/** Текст без меток медиа и сами метки — для пузырей Гесперы и общего чата. */
fun splitMedia(content: String): Pair<String, List<Part.Media>> {
    val media = MEDIA_RE.findAll(content).map { Part.Media(it.groupValues[1].trim(), it.groupValues[2].trim()) }.toList()
    return Pair(MEDIA_RE.replace(content, "").trim(), media)
}
