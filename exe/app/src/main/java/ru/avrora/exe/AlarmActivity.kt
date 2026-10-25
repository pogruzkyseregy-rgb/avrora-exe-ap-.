package ru.avrora.exe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Экран звонка поверх блокировки: ламповые часы, кто поставил и зачем, «Выключить» и «Отложить». */
class AlarmActivity : ComponentActivity() {
    companion object { const val ACTION_CLOSE = "ru.avrora.exe.ALARM_CLOSE" }

    private val closer = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(closer, IntentFilter(ACTION_CLOSE), Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(closer, IntentFilter(ACTION_CLOSE))
        val a = intent.getStringExtra(AlarmScheduler.EXTRA_ID)?.let { AlarmStore(this).get(it) }
        val nixie = AppPrefs(this).digitStyle == "nixie"
        AppTheme.set(AppPrefs(this).theme)
        setContent {
            MaterialTheme(colorScheme = auroraColors(), typography = ConsoleTypography, shapes = ConsoleShapes) {
                var now by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
                val hm = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
                val hers = a?.by == "aurora"
                Column(
                    Modifier.fillMaxSize().background(Night).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(if (hers) "АВРОРА БУДИТ ТЕБЯ" else "БУДИЛЬНИК", color = if (hers) Glow else Violet, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(18.dp))
                    if (nixie) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFF050309), RoundedCornerShape(18.dp))
                            .border(1.dp, Edge, RoundedCornerShape(18.dp)).padding(12.dp)) {
                            NixieClock(hm, (now / 500) % 2 == 0L)
                        }
                    } else Canvas(Modifier.fillMaxWidth().aspectRatio(2.55f).background(androidx.compose.ui.graphics.Color(0xFF050309), RoundedCornerShape(18.dp))
                        .border(1.dp, Edge, RoundedCornerShape(18.dp)).cornerBrackets(Magenta, 12.dp, 2.dp).padding(12.dp)) {
                        drawTube(hm, (now / 500) % 2 == 0L)
                    }
                    Spacer(Modifier.height(18.dp))
                    if (a != null && a.label.isNotBlank()) {
                        Text("«${a.label}»", color = TextMain, fontSize = 20.sp, textAlign = TextAlign.Center, lineHeight = 28.sp)
                        Spacer(Modifier.height(24.dp))
                    }
                    Button(onClick = { stop(); if (hers) openChat() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (hers) "Выключить и ответить ей" else "Выключить", fontSize = 17.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { snooze() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Отложить на $ALARM_SNOOZE_MIN минут")
                    }
                }
            }
        }
    }

    private fun stop() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
        finish()
    }

    private fun snooze() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_SNOOZE))
        finish()
    }

    private fun openChat() {
        startActivity(Intent(this, MainActivity::class.java).putExtra("open", "chat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    override fun onDestroy() {
        try { unregisterReceiver(closer) } catch (e: Exception) { }
        super.onDestroy()
    }
}
