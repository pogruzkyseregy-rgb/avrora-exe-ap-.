package ru.avrora.exe

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Виджет: крупно текущее время (при смене минуты по цифрам пробегает глитч), ниже ближайший будильник.
 * Фон: своя картинка (из галереи или из её альбома) с затемнением, либо тёмная рамка по умолчанию.
 */
class AlarmWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx, mgr, ids)
        scheduleTick(ctx)
    }

    override fun onEnabled(ctx: Context) = scheduleTick(ctx)
    override fun onDisabled(ctx: Context) = cancelTick(ctx)

    companion object {
        private const val REQ_TICK = 7201
        private const val GLITCH_CHARS = "▓▒░#%&@$01"

        fun ids(ctx: Context): IntArray {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return IntArray(0)
            return mgr.getAppWidgetIds(ComponentName(ctx, AlarmWidget::class.java))
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = ids(ctx)
            if (ids.isNotEmpty()) {
                render(ctx, mgr, ids)
                scheduleTick(ctx)
            }
        }

        private fun bgFile(ctx: Context) = File(ctx.filesDir, "widget_bg.png")
        private fun srcFile(ctx: Context) = File(ctx.filesDir, "widget_bg_src.jpg")
        fun previewFile(ctx: Context): File? = bgFile(ctx).takeIf { it.exists() }

        fun hasBackground(ctx: Context) = bgFile(ctx).exists()

        private fun cropFile(ctx: Context) = File(ctx.filesDir, "widget_crop.txt")

        /** Пропорции виджета на рабочем столе (ширина к высоте), если он уже стоит; иначе 1.6. */
        fun widgetAspect(ctx: Context): Float {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return 1.6f
            val id = ids(ctx).firstOrNull() ?: return 1.6f
            val o = mgr.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            return if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.6f, 4f) else 1.6f
        }

        fun sourceBitmap(ctx: Context): Bitmap? = srcFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

        /** Последняя подгонка: доли картинки (лево, верх, право, низ). */
        fun savedCrop(ctx: Context): RectF? = runCatching {
            val p = cropFile(ctx).readText().split(",").map { it.trim().toFloat() }
            RectF(p[0], p[1], p[2], p[3])
        }.getOrNull()

        /**
         * Готовит фон: вырезает выбранную часть (crop в долях картинки; null значит по центру),
         * затемняет, скругляет углы. Исходник и подгонка хранятся, чтобы потом менять затемнение.
         */
        fun setBackground(ctx: Context, src: Bitmap, dimPercent: Int, crop: RectF? = null, keepSource: Boolean = true): Boolean {
            if (keepSource) {
                try {
                    val s = maxOf(src.width, src.height).let { if (it > 1400) 1400f / it else 1f }
                    val small = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
                    srcFile(ctx).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                } catch (e: Exception) { }
            }
            val aspect = widgetAspect(ctx)
            val w = 800
            val h = (800 / aspect).toInt().coerceIn(200, 1300)
            val srcRect = if (crop != null) {
                Rect((crop.left * src.width).toInt(), (crop.top * src.height).toInt(), (crop.right * src.width).toInt(), (crop.bottom * src.height).toInt())
            } else {
                val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
                val sw = (w / scale).toInt().coerceAtMost(src.width)
                val sh = (h / scale).toInt().coerceAtMost(src.height)
                val sx = (src.width - sw) / 2
                val sy = (src.height - sh) / 2
                Rect(sx, sy, sx + sw, sy + sh)
            }
            try {
                val c0 = crop ?: RectF(srcRect.left.toFloat() / src.width, srcRect.top.toFloat() / src.height, srcRect.right.toFloat() / src.width, srcRect.bottom.toFloat() / src.height)
                cropFile(ctx).writeText("${c0.left},${c0.top},${c0.right},${c0.bottom}")
            } catch (e: Exception) { }
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val r = 46f
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(src, srcRect, Rect(0, 0, w, h), paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            paint.color = Color.argb((dimPercent.coerceIn(0, 85) * 255 / 100), 9, 6, 15)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            return try {
                bgFile(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                updateAll(ctx)
                true
            } catch (e: Exception) { false }
        }

        fun loadUri(ctx: Context, uri: Uri): Bitmap? = try {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                val bytes = s.readBytes()
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var sample = 1
                while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 1400) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) { null }

        fun reapplyDim(ctx: Context, dimPercent: Int): Boolean {
            val src = sourceBitmap(ctx) ?: return false
            return setBackground(ctx, src, dimPercent, savedCrop(ctx), keepSource = false)
        }

        fun clearBackground(ctx: Context) {
            bgFile(ctx).delete()
            srcFile(ctx).delete()
            cropFile(ctx).delete()
            updateAll(ctx)
        }

        private fun baseViews(ctx: Context): RemoteViews {
            val now = System.currentTimeMillis()
            val v = RemoteViews(ctx.packageName, R.layout.widget_alarm)
            val open = PendingIntent.getActivity(
                ctx, 7101,
                Intent(ctx, MainActivity::class.java).putExtra("open", "alarms").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            val bg = bgFile(ctx)
            val bmp = if (bg.exists()) BitmapFactory.decodeFile(bg.absolutePath) else null
            if (bmp != null) {
                v.setImageViewBitmap(R.id.widget_bg_img, bmp)
                v.setViewVisibility(R.id.widget_bg_img, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.widget_bg_img, View.GONE)
            }
            v.setViewVisibility(R.id.widget_glitch, View.GONE)
            val hm = SimpleDateFormat("HH:mm", Locale.US).format(Date(now))
            v.setTextViewText(R.id.widget_time, hm)
            if (AppPrefs(ctx).digitStyle == "nixie") {
                v.setImageViewBitmap(R.id.widget_time_img, NixieBitmap.render(ctx, hm))
                v.setViewVisibility(R.id.widget_time_img, View.VISIBLE)
                v.setViewVisibility(R.id.widget_time, View.GONE)
            } else {
                v.setViewVisibility(R.id.widget_time_img, View.GONE)
                v.setViewVisibility(R.id.widget_time, View.VISIBLE)
            }
            val pal = palette(ctx)
            v.setTextColor(R.id.widget_time, pal.glow.toArgb())
            v.setTextColor(R.id.widget_title, pal.violet.toArgb())
            v.setTextColor(R.id.widget_by, pal.magenta.toArgb())
            v.setViewPadding(R.id.widget_time, 0, 0, 0, 0)
            val next = nextAlarm(AlarmStore(ctx).list(), now)
            if (next == null) {
                v.setTextViewText(R.id.widget_alarm, "")
                v.setTextViewText(R.id.widget_sub, "будильников нет · нажми, чтобы поставить")
                v.setTextViewText(R.id.widget_by, "")
            } else {
                val (a, at) = next
                v.setTextViewText(R.id.widget_alarm, "будильник " + SimpleDateFormat("HH:mm", Locale.US).format(Date(at)) + " · " + dayWord(at, now))
                v.setTextViewText(R.id.widget_sub, alarmDaysText(a) + if (a.label.isNotBlank()) " · ${a.label}" else "")
                v.setTextViewText(R.id.widget_by, if (a.by == "aurora") "поставила Аврора" else "")
            }
            return v
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val v = baseViews(ctx)
            for (id in ids) mgr.updateAppWidget(id, v)
        }

        /** Полосы глитча: маленькая картинка, растягивается на весь виджет и выглядит рвано. */
        private fun palette(ctx: Context): Palette = THEMES.firstOrNull { it.id == AppPrefs(ctx).theme } ?: THEMES[0]

        private fun glitchBars(rnd: Random, pal: Palette): Bitmap {
            val b = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint()
            repeat(7 + rnd.nextInt(6)) {
                val y = rnd.nextInt(60)
                val h = 1 + rnd.nextInt(4)
                val x = rnd.nextInt(40)
                p.color = (if (rnd.nextBoolean()) pal.glow else pal.magenta).copy(alpha = 0.6f).toArgb()
                c.drawRect(x.toFloat(), y.toFloat(), (x + 30 + rnd.nextInt(90)).toFloat(), (y + h).toFloat(), p)
            }
            return b
        }

        private fun glitchText(text: String, rnd: Random): String =
            text.map { ch -> if (ch.isDigit() && rnd.nextInt(3) == 0) GLITCH_CHARS[rnd.nextInt(GLITCH_CHARS.length)] else ch }.joinToString("")

        /** Смена минуты: несколько кадров глитча, потом чистые цифры. */
        fun glitchTick(ctx: Context, done: () -> Unit) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = ids(ctx)
            if (mgr == null || ids.isEmpty()) { done(); return }
            if (!AppPrefs(ctx).widgetGlitch) { render(ctx, mgr, ids); done(); return }
            val now = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            val rnd = Random(System.nanoTime())
            val pal = palette(ctx)
            val nixie = AppPrefs(ctx).digitStyle == "nixie"
            val handler = Handler(Looper.getMainLooper())
            val frames = 4
            for (k in 0 until frames) {
                handler.postDelayed({
                    val v = RemoteViews(ctx.packageName, R.layout.widget_alarm)
                    v.setTextViewText(R.id.widget_time, glitchText(now, rnd))
                    if (nixie) {
                        // у газоразрядных ламп глитч это мерцание катодов: на миг загораются случайные цифры, одна лампа гаснет
                        val flicker = now.map { ch -> if (ch.isDigit() && rnd.nextInt(2) == 0) (if (rnd.nextInt(4) == 0) ' ' else ('0' + rnd.nextInt(10))) else ch }.joinToString("")
                        v.setImageViewBitmap(R.id.widget_time_img, NixieBitmap.render(ctx, flicker, colon = k % 2 == 1))
                    }
                    v.setTextColor(R.id.widget_time, if (k % 2 == 0) pal.magenta.toArgb() else pal.glow.toArgb())
                    val dx = rnd.nextInt(-10, 11)
                    v.setViewPadding(R.id.widget_time, maxOf(0, dx), 0, maxOf(0, -dx), 0)
                    v.setImageViewBitmap(R.id.widget_glitch, glitchBars(rnd, pal))
                    v.setViewVisibility(R.id.widget_glitch, View.VISIBLE)
                    for (id in ids) mgr.partiallyUpdateAppWidget(id, v)
                }, k * 75L)
            }
            handler.postDelayed({
                render(ctx, mgr, ids)
                done()
            }, frames * 75L + 40L)
        }

        /** Будильник на начало следующей минуты. RTC без пробуждения: при выключенном экране телефон не будится. */
        fun scheduleTick(ctx: Context) {
            if (ids(ctx).isEmpty()) return
            val am = ctx.getSystemService(AlarmManager::class.java)
            val cal = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val pi = tickIntent(ctx)
            try {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, cal.timeInMillis, pi)
                else am.set(AlarmManager.RTC, cal.timeInMillis, pi)
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC, cal.timeInMillis, pi)
            }
        }

        fun cancelTick(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java).cancel(tickIntent(ctx))
        }

        private fun tickIntent(ctx: Context) = PendingIntent.getBroadcast(
            ctx, REQ_TICK, Intent(ctx, WidgetTickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun dayWord(at: Long, now: Long): String {
            val a = Calendar.getInstance().apply { timeInMillis = at }
            val n = Calendar.getInstance().apply { timeInMillis = now }
            val diff = (a.get(Calendar.YEAR) * 400 + a.get(Calendar.DAY_OF_YEAR)) - (n.get(Calendar.YEAR) * 400 + n.get(Calendar.DAY_OF_YEAR))
            return when (diff) {
                0 -> "сегодня"
                1 -> "завтра"
                else -> SimpleDateFormat("EEE d MMM", Locale("ru")).format(Date(at))
            }
        }
    }
}

/** Каждая минута: глитч на цифрах и следующая минута в очередь. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        AlarmWidget.scheduleTick(ctx)
        AlarmWidget.glitchTick(ctx) { pending.finish() }
    }
}
