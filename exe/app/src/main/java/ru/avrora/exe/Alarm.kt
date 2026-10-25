package ru.avrora.exe

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AlarmScheduler {
    const val EXTRA_ID = "alarm_id"
    private const val REQ_FIRE = 7001
    private const val REQ_SHOW = 7002

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** Ставит в систему ближайший будильник (один на всё приложение) и обновляет виджет. */
    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = nextAlarm(AlarmStore(ctx).list(), System.currentTimeMillis())
        val fire = PendingIntent.getBroadcast(
            ctx, REQ_FIRE,
            Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, next?.first?.id ?: ""),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.cancel(fire)
        if (next != null) {
            val show = PendingIntent.getActivity(
                ctx, REQ_SHOW,
                Intent(ctx, MainActivity::class.java).putExtra("open", "alarms").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            try {
                if (canExact(ctx)) am.setAlarmClock(AlarmManager.AlarmClockInfo(next.second, show), fire)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.second, fire)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.second, fire)
            }
        }
        AlarmWidget.updateAll(ctx)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
        val store = AlarmStore(ctx)
        val a = store.get(id) ?: run { AlarmScheduler.reschedule(ctx); return }
        val now = System.currentTimeMillis()
        store.update(id) { afterFire(it, now) }
        val svc = Intent(ctx, AlarmService::class.java).putExtra(AlarmScheduler.EXTRA_ID, a.id)
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc) else ctx.startService(svc)
        } catch (e: Exception) {
            // запустить звонок не дали: хотя бы уведомление
            AuroraNotifications.show(ctx, "Будильник ${alarmTime(a)}" + if (a.label.isNotBlank()) ": ${a.label}" else "")
        }
        AlarmScheduler.reschedule(ctx)
    }
}

/** После перезагрузки, обновления приложения и смены времени будильники ставятся заново. */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = AlarmScheduler.reschedule(ctx)
}

/** Звонок: громкость будильника (звучит и в беззвучном режиме), вибрация, экран поверх блокировки. */
class AlarmService : Service() {
    companion object {
        const val ACTION_STOP = "ru.avrora.exe.ALARM_STOP"
        const val ACTION_SNOOZE = "ru.avrora.exe.ALARM_SNOOZE"
        private const val CHANNEL = "aurora_alarm"
        private const val NOTIF_ID = 1101
    }

    private var player: MediaPlayer? = null
    private var currentId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finish("missed") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { finish("dismissed"); return START_NOT_STICKY }
            ACTION_SNOOZE -> {
                currentId?.let { id -> AlarmStore(this).update(id) { snoozed(it, System.currentTimeMillis()) } }
                stopRinging()
                AlarmScheduler.reschedule(this)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val id = intent?.getStringExtra(AlarmScheduler.EXTRA_ID) ?: run { stopSelf(); return START_NOT_STICKY }
        val a = AlarmStore(this).get(id)
        currentId = id
        val notif = buildNotification(a)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, notif)
        startRinging()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, ALARM_RING_MAX_MS)
        // экран звонка (если система разрешит открыть его поверх)
        try {
            startActivity(Intent(this, AlarmActivity::class.java).putExtra(AlarmScheduler.EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
        return START_NOT_STICKY
    }

    private fun buildNotification(a: AlarmItem?): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Будильник AVRORA.EXE", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)   // звук играет сам сервис, громкостью будильника
                enableVibration(false)
            })
        }
        val full = PendingIntent.getActivity(this, 1, Intent(this, AlarmActivity::class.java).putExtra(AlarmScheduler.EXTRA_ID, a?.id ?: ""),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, AlarmService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getService(this, 3, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE), PendingIntent.FLAG_IMMUTABLE)
        val title = if (a?.by == "aurora") "Аврора будит тебя" else "Будильник"
        val text = (a?.let { alarmTime(it) } ?: "") + (a?.label?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Отложить $ALARM_SNOOZE_MIN мин", snooze)
            .addAction(0, "Выключить", stop)
            .build()
    }

    private fun startRinging() {
        stopRinging()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            player = null
        }
        val pattern = longArrayOf(0, 700, 500)
        vibrator()?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else getSystemService(Vibrator::class.java)

    private fun stopRinging() {
        try { player?.stop() } catch (e: Exception) { }
        player?.release()
        player = null
        vibrator()?.cancel()
    }

    private fun finish(action: String) {
        handler.removeCallbacks(timeout)
        val now = System.currentTimeMillis()
        currentId?.let { id -> AlarmStore(this).update(id) { it.copy(lastAction = action, lastActionAt = now) } }
        stopRinging()
        sendBroadcast(Intent(AlarmActivity.ACTION_CLOSE).setPackage(packageName))
        AlarmScheduler.reschedule(this)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        stopRinging()
        super.onDestroy()
    }
}
