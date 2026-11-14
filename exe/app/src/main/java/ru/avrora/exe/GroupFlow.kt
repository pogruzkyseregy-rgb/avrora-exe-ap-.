package ru.avrora.exe

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Живой общий чат. После круга ответов на сообщение Адониса, если тема или игра в разгаре, девчонки продолжают сами:
 * через 15–40 секунд — следующий круг, и так, пока кто-то из них отвечает. Затихает, когда все промолчали,
 * после MAX_ROUNDS кругов или как только Адонис написал сам (его сообщение важнее — круг отменяется).
 */
object GroupFlow {
    private const val MAX_ROUNDS = 8
    private val GIRLS = setOf(WHO_AURORA, WHO_HESPERA, WHO_EST)

    /** Идёт круг без Адониса: на экране «пишут…». */
    var busy by mutableStateOf(false)
        private set
    private var job: Job? = null

    private fun lastAdonis(ctx: Context): Long =
        synchronized(HesperaStore.LOCK) { HesperaStore(ctx).group() }.lastOrNull { it.role == "user" }?.time ?: 0L

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (!HesperaStore(app).flowOn || AppPrefs(app).token.isBlank()) return
        job?.cancel()
        val since = lastAdonis(app)
        job = AppScope.launch {
            try {
                repeat(MAX_ROUNDS) {
                    delay(Random.nextLong(15_000, 40_000))
                    if (lastAdonis(app) != since) return@launch        // Адонис написал — дальше его круг
                    busy = true
                    val replies = runCatching { GroupEngine.converse(app, flow = true) }.getOrNull()
                    busy = false
                    if (lastAdonis(app) != since) return@launch
                    if (replies == null || replies.none { it.who in GIRLS && it.text.isNotBlank() }) return@launch   // тема затихла
                }
            } finally {
                busy = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        busy = false
    }
}
