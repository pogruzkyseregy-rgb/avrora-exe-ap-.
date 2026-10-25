package ru.avrora.exe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()
    private val hvm: HesperaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuroraNotifications.ensureChannel(this)
        intent?.getStringExtra("open")?.let { vm.openRequest = it }
        AlarmScheduler.reschedule(this)
        AppTheme.set(AppPrefs(this).theme)
        setContent {
            MaterialTheme(colorScheme = auroraColors(), typography = ConsoleTypography, shapes = ConsoleShapes) { ChatScreen() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open")?.let { vm.openRequest = it }
    }

    override fun onStart() {
        super.onStart()
        // Забираем сообщения, которые Аврора написала в фоне, и убираем уведомление
        vm.drainInbox()
        vm.reloadDiary()
        vm.reloadExe()
        vm.refreshHand()
        vm.resumeGame()
        AuroraNotifications.clear(this)
        hvm.reload()
        hvm.refreshProfile()
        HesperaNotifications.clear(this)
    }
}
