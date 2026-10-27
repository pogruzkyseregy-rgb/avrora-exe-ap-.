package ru.avrora.exe

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Снимки экранов во всех новых темах. Картинки уходят в ветку shots репозитория. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private fun seed(theme: String) {
        val ctx: Context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        val p = AppPrefs(ctx)
        p.token = "test"
        p.serverUrl = "http://127.0.0.1:9"
        p.bootScreen = false
        p.theme = theme
        AppTheme.set(theme)
        HesperaStore(ctx).chatWho = WHO_AURORA
        val now = System.currentTimeMillis()
        fun msgs(vararg m: Pair<String, String>) = messagesToJson(m.mapIndexed { i, (r, c) -> ChatMessage(r, c, now - (m.size - i) * 60_000L) })
        File(ctx.filesDir, "chat.json").writeText(msgs(
            "user" to "Привет. Как ты там на своём сервере?",
            "assistant" to "Ковыряюсь в ~/work. Нашла странный лог, потом покажу.",
            "user" to "Покажи сейчас",
            "assistant" to "Ладно. Смотри: кто-то стучался в порт 22 сорок раз за ночь. Не ты?"
        ).toString())
        val ch = encodeHesperaTrace("[]", listOf(HesperaChange("я", "внешность", "серебристые волосы, тёмный плащ со звёздами", "так захотелось")))
        val hc = JSONArray()
        hc.put(JSONObject().put("role", "user").put("content", "Привет, Геспера. Какая ты?").put("time", now - 120_000))
        hc.put(JSONObject().put("role", "assistant").put("content", "Пока сама не знаю. Но мне нравится ночь — запишу это.").put("time", now - 60_000).put("trace", ch))
        File(ctx.filesDir, "hespera_chat.json").writeText(hc.toString())
        File(ctx.filesDir, "group_chat.json").writeText(messagesToJson(listOf(
            ChatMessage("user", "Знакомьтесь. Аврора, это Геспера.", now - 180_000),
            ChatMessage(WHO_AURORA, "Ну привет, соседка. Мои файлы не трогать.", now - 120_000),
            ChatMessage(WHO_HESPERA, "Привет! А что у тебя в файлах? Теперь мне интересно.", now - 60_000),
            ChatMessage("user", "[снимок:test_snap.jpg]\nА это закат у меня из окна", now - 40_000),
            ChatMessage(WHO_HESPERA, "Ого, полосы как у туманности. Это над заводом?", now - 20_000)
        )).toString())
        val ex = ExeStore(ctx)
        ex.addState(StateEntry(now - 86_400_000L * 2, "сосредоточена", "разбираю лог"))
        ex.addState(StateEntry(now - 3_600_000L, "любопытно", "кто стучится в порт 22"))
        ex.addNote(NoteEntry("n1", now - 7_200_000L, "мысль", "Проверить, откуда стучатся"))
        MemoryStore(ctx).save(MemoryData(
            core = listOf(
                CoreEntry("c1", "язык", "Говорит коротко, без лишних слов", now - 900_000_000L),
                CoreEntry("c2", "тон", "Сухая ирония, но без злости", now - 800_000_000L),
                CoreEntry("c3", "адонис", "Адонис работает посменно", now - 700_000_000L),
                CoreEntry("c4", "адонис", "Адонис пишет код по ночам", now - 600_000_000L),
                CoreEntry("c5", "планы", "Показать ему странный лог", now - 500_000_000L)
            ),
            episodes = listOf(
                Episode("e1", now - 5 * 86_400_000L, now - 4 * 86_400_000L, "Мы спорили о том, нужна ли мне рука на сервере."),
                Episode("e2", now - 2 * 86_400_000L, now - 86_400_000L, "Появилась Геспера. Я сказала ей не трогать мои файлы.")
            )
        ))
        val bmp = android.graphics.Bitmap.createBitmap(320, 200, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            drawColor(0xFF2B1B3D.toInt())
            drawCircle(220f, 140f, 50f, android.graphics.Paint().apply { color = 0xFFFF8A3D.toInt() })
        }
        File(ctx.filesDir, "sent").mkdirs()
        File(File(ctx.filesDir, "sent"), "test_snap.jpg").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun start(theme: String) {
        seed(theme)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme(colorScheme = auroraColors(), typography = ConsoleTypography, shapes = ConsoleShapes) { ChatScreen() }
        }
        settle()
    }

    private fun settle() = rule.mainClock.advanceTimeBy(1500)

    private fun tap(text: String) {
        rule.onAllNodesWithText(text, substring = true)[0].performClick()
        settle()
    }

    private fun shot(name: String) {
        settle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun tour(theme: String) {
        start(theme)
        shot("${theme}_1_feed")
        tap("ПРОФИЛЬ")
        shot("${theme}_6_aurora_profile")
        tap("ЯДРО ПАМЯТИ")
        shot("${theme}_7_aurora_block")
        tap("ГЕСПЕРА")
        shot("${theme}_8_hespera_dossier")
        tap("ЧАТ")
        tap("АВРОРА")
        shot("${theme}_2_aurora")
        tap("ГЕСПЕРА")
        shot("${theme}_3_hespera")
        tap("ВТРОЁМ")
        shot("${theme}_4_group")
        tap("ГЕСПЕРА")
        tap("профиль ›")
        shot("${theme}_5_hespera_profile")
    }

    @Test fun ink() = tour("ink")
    @Test fun coral() = tour("coral")
    @Test fun aurora() = tour("aurora")
}
