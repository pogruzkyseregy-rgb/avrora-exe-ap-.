package ru.avrora.exe

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* Общие мелочи для всех четырёх чатов: разделители по дням, кнопка «вниз», черновики. */

private val dayFmt = SimpleDateFormat("d MMMM", Locale("ru"))
private val dayYearFmt = SimpleDateFormat("d MMMM yyyy", Locale("ru"))

private fun dayKey(t: Long): Int = Calendar.getInstance().run { timeInMillis = t; get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR) }

fun sameDay(a: Long, b: Long): Boolean = dayKey(a) == dayKey(b)

fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String {
    val today = dayKey(now)
    val yesterday = dayKey(now - 86_400_000L)
    val k = dayKey(t)
    return when {
        k == today -> "Сегодня"
        k == yesterday -> "Вчера"
        k / 1000 == today / 1000 -> dayFmt.format(Date(t))
        else -> dayYearFmt.format(Date(t))
    }
}

/** Нужен ли разделитель перед сообщением: первое в списке или первое за новый день. */
fun needsDay(prev: Long?, t: Long): Boolean = prev == null || !sameDay(prev, t)

@Composable
fun DayDivider(t: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Edge))
        Text(dayLabel(t), color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Edge))
    }
}

/**
 * Список сообщений с кнопкой «вниз»: появляется, когда пролистал вверх, и одним нажатием
 * возвращает к последнему сообщению.
 */
@Composable
fun ChatList(
    state: LazyListState,
    modifier: Modifier,
    accent: Color,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val away by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last < info.totalItemsCount - 2
        }
    }
    Box(modifier) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        AnimatedVisibility(away, Modifier.align(Alignment.BottomEnd).padding(12.dp), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Panel).border(1.dp, accent.copy(alpha = 0.7f), CircleShape)
                    .clickable { scope.launch { val n = state.layoutInfo.totalItemsCount; if (n > 0) state.animateScrollToItem(n - 1) } },
                contentAlignment = Alignment.Center
            ) { Text("↓", color = accent, fontSize = 18.sp) }
        }
    }
}

/** Черновики: недописанное сообщение переживает закрытие приложения. Ключ — какой это чат. */
object Drafts {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("drafts", Context.MODE_PRIVATE)
    fun get(ctx: Context, key: String): String = prefs(ctx).getString(key, "") ?: ""
    fun put(ctx: Context, key: String, text: String) {
        prefs(ctx).edit().apply { if (text.isBlank()) remove(key) else putString(key, text.take(8000)) }.apply()
    }
}
