package ru.avrora.exe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

/*
 * Отправка, которая не зависит от экрана. Сообщение ушло — дальше можно листать другие чаты, сворачивать
 * и закрывать приложение: запрос живёт в общем для приложения потоке (не в экране), а пока ждём ответ,
 * работает служба «жду ответ» — Android не убивает приложение, пока она висит в шторке.
 * Ответ сохраняется в чат и, если приложение не на экране, приходит уведомлением.
 */

/** Поток приложения: живёт, пока жив процесс, а не пока открыт экран. */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Приложение сейчас на экране? Ставит MainActivity в onStart/onStop. */
object AppForeground {
    @Volatile var visible = false
}

/** Ответ пришёл: экран, открытый уже после отправки, перечитывает чат с диска. Значение — какой чат. */
object ReplyBus {
    val flow = MutableSharedFlow<String>(extraBufferCapacity = 16)
}

/** Какие чаты сейчас ждут ответа. На экране — «печатает…», даже если экран открыт заново. */
object InFlight {
    val chats = mutableStateMapOf<String, Int>()

    fun busy(chat: String): Boolean = (chats[chat] ?: 0) > 0

    /** Обернуть ожидание ответа: служба держит приложение живым, пока хоть один чат ждёт. */
    suspend fun <T> run(ctx: Context, chat: String, block: suspend () -> T): T {
        val app = ctx.applicationContext
        chats[chat] = (chats[chat] ?: 0) + 1
        SendService.update(app)
        try {
            return block()
        } finally {
            val n = (chats[chat] ?: 1) - 1
            if (n <= 0) chats.remove(chat) else chats[chat] = n
            SendService.update(app)
            ReplyBus.flow.tryEmit(chat)
        }
    }
}

/** Ответ, пришедший, пока приложение свёрнуто или закрыто, — уведомлением. */
object ReplyNotify {
    fun show(ctx: Context, chat: String, text: String) {
        if (AppForeground.visible) return
        val plain = stripBoard(TAG_RE.replace(text, "")).replace(Regex("\\[[^\\]\\n]{1,60}\\]"), "").trim()
        if (plain.isBlank()) return
        when (chat) {
            "aurora" -> AuroraNotifications.show(ctx, plain)
            else -> HesperaNotifications.show(ctx, plain, chat)
        }
    }
}

/** Служба «жду ответ»: пока висит, Android не выгружает приложение посреди запроса. */
class SendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val waiting = InFlight.chats.keys.toList()
        // startForeground обязателен при каждом запуске, даже если ответ уже пришёл, — иначе Android роняет приложение
        val n = notification(this, waiting)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIF_ID, n)
        } catch (e: Exception) {
            // запустить службу не дали (редкие прошивки) — запрос всё равно идёт, пока приложение живо
        }
        if (waiting.isEmpty()) {
            running = false
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "sending"
        private const val NOTIF_ID = 3003
        @Volatile private var running = false

        private fun notification(ctx: Context, waiting: List<String>): Notification {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Отправка сообщений", NotificationManager.IMPORTANCE_MIN))
            }
            val who = waiting.joinToString(", ") {
                when (it) { "aurora" -> "Аврора"; "hespera" -> "Геспера"; "est" -> "Эст"; "group" -> "общий чат"; else -> it }
            }
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("AVRORA.EXE")
                .setContentText(if (waiting.isEmpty()) "Ответ пришёл" else "Жду ответ: $who")
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }

        /** Запустить, обновить или остановить — по тому, ждёт ли кто-то ответа. */
        fun update(ctx: Context) {
            val i = Intent(ctx, SendService::class.java)
            try {
                if (InFlight.chats.isEmpty()) {
                    if (running) ctx.startService(i)        // служба сама уберёт себя из шторки и остановится
                } else {
                    running = true
                    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                }
            } catch (e: Exception) {
                // из фона Android может не дать запустить службу — не страшно, запрос идёт дальше
            }
        }
    }
}
