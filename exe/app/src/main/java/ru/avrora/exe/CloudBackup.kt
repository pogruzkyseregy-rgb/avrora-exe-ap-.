package ru.avrora.exe

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Копия приложения на сервере. Всё, что лежит только в телефоне — переписки всех четырёх чатов, память Авроры,
 * дневник, состояния, профили, цитаты, стикеры и фото, настройки, — архивом уходит на сервер кусками по 4 МБ.
 * После переустановки или на новом телефоне: ввёл адрес и токен — приложение предлагает всё вернуть.
 */
object CloudBackup {
    private const val PART = 4 * 1024 * 1024
    private const val FILE_MAX = 40L * 1024 * 1024        // один файл больше — пропускаем (огромное видео и т.п.)
    private val lock = Mutex()
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cloud_backup", Context.MODE_PRIVATE)

    fun lastAt(ctx: Context): Long = prefs(ctx).getLong("at", 0L)

    /** Собрать архив и отправить на сервер. Возвращает размер отправленного. */
    suspend fun upload(ctx: Context): Long = lock.withLock {
        val app = ctx.applicationContext
        val p = AppPrefs(app)
        if (p.token.isBlank()) throw IllegalStateException("Нет токена")
        val zip = File(app.cacheDir, "cloud-backup.zip")
        withContext(Dispatchers.IO) { pack(app, zip) }
        try {
            val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val total = zip.length()
            val parts = ((total + PART - 1) / PART).toInt().coerceAtLeast(1)
            withContext(Dispatchers.IO) {
                FileInputStream(zip).use { inp ->
                    val buf = ByteArray(PART)
                    for (n in 0 until parts) {
                        var got = 0
                        while (got < PART) { val r = inp.read(buf, got, PART - got); if (r < 0) break; got += r }
                        val body = JSONObject().put("id", id).put("n", n).put("last", n == parts - 1)
                            .put("data", Base64.encodeToString(buf, 0, got, Base64.NO_WRAP))
                        AuroraApi.post(p.serverUrl, "/backup/part", p.token, body)
                    }
                }
            }
            prefs(app).edit().putLong("at", System.currentTimeMillis()).apply()
            total
        } finally {
            zip.delete()
        }
    }

    /** Что лежит на сервере: null — копии нет. */
    suspend fun info(ctx: Context): JSONObject? {
        val p = AppPrefs(ctx)
        if (p.token.isBlank()) return null
        val o = AuroraApi.get(p.serverUrl, "/backup/info", p.token)
        return if (o.optBoolean("exists")) o else null
    }

    /** Скачать последнюю копию и разложить по местам. После этого приложение перезапускается. */
    suspend fun restore(ctx: Context) = lock.withLock {
        val app = ctx.applicationContext
        val p = AppPrefs(app)
        val info = info(app) ?: throw IllegalStateException("На сервере нет копии")
        val name = info.getString("name")
        val parts = info.getInt("parts")
        val zip = File(app.cacheDir, "cloud-restore.zip")
        withContext(Dispatchers.IO) {
            FileOutputStream(zip).use { out ->
                for (n in 0 until parts) {
                    val o = AuroraApi.get(p.serverUrl, "/backup/get?name=$name&n=$n", p.token)
                    out.write(Base64.decode(o.getString("data"), Base64.DEFAULT))
                }
            }
            val url = p.serverUrl
            val token = p.token
            unpack(app, zip)
            zip.delete()
            // адрес и токен — те, что введены сейчас (в копии могли остаться старые). Правим сам файл:
            // настройки в памяти процесса старые, и после перезапуска приложение прочтёт файл заново.
            patchPrefs(app, "settings", mapOf("server_url" to url, "token" to token))
        }
        prefs(app).edit().putBoolean("offered", true).putLong("at", System.currentTimeMillis()).commit()
    }

    /** Перезапуск после восстановления: настройки в памяти старые, их нельзя дописывать поверх копии. */
    fun restart(ctx: Context) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        ctx.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }

    private fun patchPrefs(ctx: Context, name: String, values: Map<String, String>) {
        val f = File(File(ctx.applicationInfo.dataDir, "shared_prefs"), "$name.xml")
        if (!f.exists()) return
        var xml = f.readText()
        fun esc(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        values.forEach { (k, v) ->
            val tag = "<string name=\"$k\">${esc(v)}</string>"
            val re = Regex("<string name=\"${Regex.escape(k)}\">.*?</string>|<string name=\"${Regex.escape(k)}\" />", RegexOption.DOT_MATCHES_ALL)
            xml = if (re.containsMatchIn(xml)) re.replace(xml) { tag } else xml.replace("</map>", "    $tag\n</map>")
        }
        f.writeText(xml)
    }

    private fun pack(ctx: Context, dest: File) {
        val files = ctx.filesDir
        val prefsDir = File(ctx.applicationInfo.dataDir, "shared_prefs")
        ZipOutputStream(FileOutputStream(dest)).use { z ->
            fun add(root: File, f: File, prefix: String) {
                if (f.isDirectory) { f.listFiles()?.forEach { add(root, it, prefix) }; return }
                if (f.length() > FILE_MAX) return
                val rel = f.relativeTo(root).path.replace('\\', '/')
                z.putNextEntry(ZipEntry(prefix + rel))
                FileInputStream(f).use { it.copyTo(z) }
                z.closeEntry()
            }
            files.listFiles()?.forEach { add(files, it, "files/") }
            // настройки, кроме служебных этой копии и черновиков
            prefsDir.listFiles()?.filter { it.name.endsWith(".xml") && it.name != "cloud_backup.xml" && !it.name.startsWith("WorkManager") }
                ?.forEach { add(prefsDir, it, "prefs/") }
        }
    }

    private fun unpack(ctx: Context, src: File) {
        val files = ctx.filesDir.canonicalFile
        val prefsDir = File(ctx.applicationInfo.dataDir, "shared_prefs").canonicalFile.also { it.mkdirs() }
        ZipFile(src).use { z ->
            for (e in z.entries()) {
                if (e.isDirectory) continue
                val target = when {
                    e.name.startsWith("files/") -> File(files, e.name.removePrefix("files/"))
                    e.name.startsWith("prefs/") -> File(prefsDir, e.name.removePrefix("prefs/"))
                    else -> continue
                }.canonicalFile
                // защита от «../» в именах внутри архива
                if (!target.path.startsWith(files.path + File.separator) && !target.path.startsWith(prefsDir.path + File.separator)) continue
                target.parentFile?.mkdirs()
                z.getInputStream(e).use { inp -> FileOutputStream(target).use { inp.copyTo(it) } }
            }
        }
    }

    /** Предлагать ли восстановление: свежая установка (все чаты пустые), токен есть, ещё не предлагали. */
    fun shouldOffer(ctx: Context): Boolean {
        if (prefs(ctx).getBoolean("offered", false)) return false
        if (AppPrefs(ctx).token.isBlank()) return false
        val chats = listOf("chat.json", "hespera_chat.json", "est_chat.json", "group_chat.json")
        return chats.all { val f = File(ctx.filesDir, it); !f.exists() || f.length() < 8 }
    }

    fun markOffered(ctx: Context) { prefs(ctx).edit().putBoolean("offered", true).apply() }

    // ---------- автоматически ----------

    /** Раз в 6 часов при сети и после того, как приложение свернули (не чаще раза в 30 минут). */
    fun schedule(ctx: Context) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "cloud_backup_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CloudBackupWorker>(6, TimeUnit.HOURS).setConstraints(net).build()
        )
    }

    fun soon(ctx: Context) {
        if (System.currentTimeMillis() - lastAt(ctx) < 30 * 60_000L) return
        if (AppPrefs(ctx).token.isBlank() || shouldOffer(ctx)) return     // пустое свежее приложение не затирает хорошую копию
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "cloud_backup_soon", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudBackupWorker>().setConstraints(net).setInitialDelay(1, TimeUnit.MINUTES).build()
        )
    }
}

class CloudBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (AppPrefs(ctx).token.isBlank() || CloudBackup.shouldOffer(ctx)) return Result.success()
        return try {
            CloudBackup.upload(ctx)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

private val backupDate = SimpleDateFormat("d MMMM, HH:mm", Locale("ru"))

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f МБ", bytes / 1024.0 / 1024.0)

/** Раздел в настройках: копия на сервере — когда была, сохранить сейчас, восстановить. */
@Composable
fun CloudBackupSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf<JSONObject?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { server = runCatching { CloudBackup.info(ctx) }.getOrNull() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Копия на сервере: все четыре чата, память, дневник, профили, стикеры и фото, настройки. " +
            "Сохраняется сама раз в несколько часов и когда сворачиваешь приложение. После переустановки — вернуть одной кнопкой.",
            color = TextDim, fontSize = 13.sp)
        Text(server?.let { "Последняя на сервере: ${backupDate.format(Date(it.optLong("at") * 1000))}, ${mb(it.optLong("size"))}" }
            ?: "На сервере копии пока нет", color = TextMain, fontSize = 14.sp)
        OutlinedButton(enabled = !busy, onClick = {
            busy = true; status = "Сохраняю…"
            scope.launch {
                status = runCatching { CloudBackup.upload(ctx) }.fold({ "Сохранено: ${mb(it)}" }, { "Не вышло: ${it.message}" })
                server = runCatching { CloudBackup.info(ctx) }.getOrNull()
                busy = false
            }
        }) { Text("Сохранить на сервер сейчас") }
        OutlinedButton(enabled = !busy && server != null, onClick = { confirm = true }) { Text("Восстановить с сервера") }
        if (status.isNotBlank()) Text(status, color = TextDim, fontSize = 13.sp)
    }
    if (confirm) RestoreDialog(server, onDismiss = { confirm = false }) {
        confirm = false; busy = true; status = "Скачиваю копию…"
        scope.launch {
            runCatching { CloudBackup.restore(ctx) }
                .onSuccess { CloudBackup.restart(ctx) }
                .onFailure { status = "Не вышло: ${it.message}"; busy = false }
        }
    }
}

@Composable
private fun RestoreDialog(info: JSONObject?, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вернуть всё с сервера?") },
        text = {
            Text("Копия от ${info?.let { backupDate.format(Date(it.optLong("at") * 1000)) } ?: "?"}" +
                (info?.let { ", ${mb(it.optLong("size"))}" } ?: "") +
                ". Переписки, память и настройки в телефоне заменятся тем, что в копии. Приложение перезапустится.")
        },
        confirmButton = { TextButton(onClick = onOk) { Text("Вернуть") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** После переустановки: чаты пустые, токен введён, на сервере есть копия — предложить вернуть всё. */
@Composable
fun RestoreOffer(token: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(token) {
        if (CloudBackup.shouldOffer(ctx)) info = runCatching { CloudBackup.info(ctx) }.getOrNull()
    }
    val i = info ?: return
    if (working) {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Возвращаю…") },
            text = { Text(error.ifBlank { "Скачиваю копию с сервера. Не закрывай приложение." }) })
        return
    }
    RestoreDialog(i, onDismiss = { CloudBackup.markOffered(ctx); info = null }) {
        working = true
        scope.launch {
            runCatching { CloudBackup.restore(ctx) }
                .onSuccess { CloudBackup.restart(ctx) }
                .onFailure { error = "Не вышло: ${it.message}. Попробуй ещё раз в настройках → Резервная копия."; CloudBackup.markOffered(ctx); working = false; info = null }
        }
    }
}
