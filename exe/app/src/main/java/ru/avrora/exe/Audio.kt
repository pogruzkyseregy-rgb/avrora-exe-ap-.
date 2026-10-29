package ru.avrora.exe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

// ---------- уши: голосовые и треки от Адониса ----------
// Голосовое записывается здесь, сервер его расшифровывает; трек или любой аудиофайл с телефона сервер
// даёт «послушать» модели со слухом. В сообщении остаётся метка с расшифровкой или пересказом:
//   [голос: id | расшифровка]     [аудио: id | название | что в нём слышно]

object VoiceRecorder {
    var recording by mutableStateOf(false)
        private set
    var startedAt = 0L
        private set
    private var rec: MediaRecorder? = null
    private var file: File? = null

    fun start(ctx: Context): Boolean = runCatching {
        cancel()
        val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.path)
        r.prepare()
        r.start()
        rec = r
        file = f
        startedAt = System.currentTimeMillis()
        recording = true
        true
    }.getOrElse { cancel(); false }

    /** Остановить и отдать файл; слишком короткая запись (меньше секунды) не считается. */
    fun stop(): File? {
        val r = rec ?: return null
        val long = System.currentTimeMillis() - startedAt >= 1000
        runCatching { r.stop() }
        runCatching { r.release() }
        rec = null
        recording = false
        val f = file
        file = null
        return if (long && f != null && f.length() > 1000) f else { f?.delete(); null }
    }

    fun cancel() {
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        rec = null
        file?.delete()
        file = null
        recording = false
    }
}

object AudioApi {
    data class Heard(val id: String, val text: String, val ok: Boolean)

    suspend fun upload(ctx: Context, bytes: ByteArray, mime: String, name: String, voice: Boolean): Heard {
        val p = AppPrefs(ctx)
        val body = JSONObject().put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            .put("mime", mime).put("name", name).put("kind", if (voice) "voice" else "music")
        val r = AuroraApi.post(p.serverUrl, "/audio/upload", p.token, body)
        return Heard(r.optString("id"), r.optString("text"), r.optBoolean("ok"))
    }
}

private fun clean(s: String) = s.replace(Regex("[\\[\\]|\\n\\r]+"), " ").trim()

/** Метка голосового или трека для текста сообщения. */
fun audioTag(voice: Boolean, h: AudioApi.Heard, name: String): String =
    if (voice) "[голос: ${h.id} | ${clean(if (h.ok) h.text else "(не расшифровалось)")}]"
    else "[аудио: ${h.id} | ${clean(name).ifBlank { "трек" }.take(80)} | ${clean(if (h.ok) h.text else "(послушать не получилось)")}]"

/** Аудиофайл с телефона: байты, тип и имя. */
fun readAudio(ctx: Context, uri: Uri): Triple<ByteArray, String, String>? = runCatching {
    val mime = ctx.contentResolver.getType(uri) ?: "audio/mpeg"
    var name = "трек"
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) name = c.getString(0)?.substringBeforeLast('.') ?: name
    }
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    Triple(bytes, mime, name)
}.getOrNull()

/** Кнопка справа от поля: есть что отправить — «отправить», иначе микрофон; во время записи — красная точка, нажать ещё раз — отправить. */
@Composable
fun SendOrMic(canSend: Boolean, accent: Color, onSend: () -> Unit, onVoice: (File) -> Unit) {
    val ctx = LocalContext.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!VoiceRecorder.start(ctx)) Toast.makeText(ctx, "Микрофон не включился", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(ctx, "Без микрофона голосовые не записать", Toast.LENGTH_SHORT).show()
    }
    val rec = VoiceRecorder.recording
    Box(
        Modifier.size(46.dp).clip(CircleShape)
            .background(if (rec) Color(0xFFE5484D) else if (canSend) accent else Edge)
            .clickable {
                when {
                    rec -> VoiceRecorder.stop()?.let(onVoice)
                    canSend -> onSend()
                    ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                        if (!VoiceRecorder.start(ctx)) Toast.makeText(ctx, "Микрофон не включился", Toast.LENGTH_SHORT).show()
                    else -> perm.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val tint = if (rec || canSend) (if (AppTheme.current.light) Color.White else Night) else TextDim
        if (rec || canSend) Icon(Icons.Default.Send, contentDescription = if (rec) "Отправить голосовое" else "Отправить", tint = if (rec) Color.White else tint, modifier = Modifier.size(20.dp))
        else MicGlyph(tint)
    }
}

@Composable
private fun MicGlyph(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val st = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(tint, Offset(w * 0.34f, h * 0.05f), Size(w * 0.32f, h * 0.55f), CornerRadius(w * 0.16f))
        drawArc(tint, 0f, 180f, false, Offset(w * 0.2f, h * 0.25f), Size(w * 0.6f, h * 0.5f), style = st)
        drawLine(tint, Offset(w / 2, h * 0.75f), Offset(w / 2, h * 0.95f), st.width, StrokeCap.Round)
    }
}

/** Полоса над полем во время записи: время и «отменить». */
@Composable
fun RecordingBar() {
    if (!VoiceRecorder.recording) return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(VoiceRecorder.recording) {
        while (VoiceRecorder.recording) { delay(250); now = System.currentTimeMillis() }
    }
    val s = ((now - VoiceRecorder.startedAt) / 1000).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE5484D)))
        Spacer(Modifier.width(8.dp))
        Text("запись %d:%02d · нажми ➤, чтобы отправить".format(s / 60, s % 60), color = TextMain, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        TextButton(onClick = { VoiceRecorder.cancel() }) { Text("отменить", color = TextDim) }
    }
}

/** «Слушаю…» пока сервер расшифровывает или слушает. */
@Composable
fun HearingBar(text: String?) {
    if (text == null) return
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Голосовое: плеер и расшифровка под ним; трек — плеер с названием. */
@Composable
fun VoicePart(p: Part.Voice, accent: Color) {
    androidx.compose.foundation.layout.Column {
        MediaPart(Part.Media(p.id, if (p.voice) "голосовое" else p.first), accent) {}
        if (p.voice && p.first.isNotBlank()) {
            Text("«${p.first}»", color = TextDim, fontSize = 13.sp, lineHeight = 18.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(top = 2.dp).width(260.dp))
        }
    }
}
