package ru.avrora.exe

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Будильник: чистая логика без Android. Дни недели: 1 = Пн … 7 = Вс; пустой набор значит «один раз».

data class AlarmItem(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = emptySet(),
    val label: String = "",
    val by: String = "adonis",          // adonis / aurora
    val enabled: Boolean = true,
    val onceAt: Long = 0L,              // для разового: точное время
    val snoozeAt: Long = 0L,            // отложен до
    val created: Long = 0L,
    val lastFired: Long = 0L,
    val lastAction: String = "",        // dismissed / snoozed / missed
    val lastActionAt: Long = 0L
)

const val ALARM_SNOOZE_MIN = 10
const val ALARM_RING_MAX_MS = 5 * 60_000L
const val AURORA_ALARM_DAILY_CAP = 3
const val AURORA_ALARM_PENDING_CAP = 3
val ALARM_DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private fun isoDay(cal: Calendar): Int = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1   // Пн=1 … Вс=7

/** Ближайшее срабатывание после now; null, если выключен или разовый уже прошёл. */
fun nextFire(a: AlarmItem, now: Long, tz: TimeZone = TimeZone.getDefault()): Long? {
    if (!a.enabled) return null
    if (a.snoozeAt > now) return a.snoozeAt
    if (a.days.isEmpty()) return a.onceAt.takeIf { it > now }
    val cal = Calendar.getInstance(tz)
    cal.timeInMillis = now
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.HOUR_OF_DAY, a.hour)
    cal.set(Calendar.MINUTE, a.minute)
    repeat(8) {
        if (cal.timeInMillis > now && isoDay(cal) in a.days) return cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, a.hour)
        cal.set(Calendar.MINUTE, a.minute)
    }
    return null
}

/** Разовый будильник на ближайшие hour:minute. */
fun onceAtFor(hour: Int, minute: Int, now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
    val cal = Calendar.getInstance(tz)
    cal.timeInMillis = now
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis
}

fun nextAlarm(list: List<AlarmItem>, now: Long, tz: TimeZone = TimeZone.getDefault()): Pair<AlarmItem, Long>? =
    list.mapNotNull { a -> nextFire(a, now, tz)?.let { Pair(a, it) } }.minByOrNull { it.second }

/** После звонка: разовый выключается, отложенный сбрасывается. */
fun afterFire(a: AlarmItem, now: Long): AlarmItem =
    a.copy(lastFired = now, snoozeAt = 0L, enabled = if (a.days.isEmpty()) false else a.enabled)

fun snoozed(a: AlarmItem, now: Long): AlarmItem =
    a.copy(snoozeAt = now + ALARM_SNOOZE_MIN * 60_000L, enabled = true, lastAction = "snoozed", lastActionAt = now)

data class AlarmDecision(val alarm: AlarmItem?, val why: String)

/** Будильник от неё: время как у «напишу» (не дальше двух суток), не больше трёх в сутки и трёх ждущих. */
fun acceptAuroraAlarm(existing: List<AlarmItem>, whenText: String, label: String, now: Long, id: String,
                      tz: TimeZone = TimeZone.getDefault()): AlarmDecision {
    val mine = existing.filter { it.by == "aurora" }
    if (mine.count { it.created > now - 24 * 3_600_000L } >= AURORA_ALARM_DAILY_CAP)
        return AlarmDecision(null, "за сутки уже поставлено $AURORA_ALARM_DAILY_CAP")
    if (mine.count { nextFire(it, now, tz) != null } >= AURORA_ALARM_PENDING_CAP)
        return AlarmDecision(null, "уже ждут $AURORA_ALARM_PENDING_CAP твоих будильника")
    val w = parseWhen(whenText, now, tz)
    val at = w.at ?: return AlarmDecision(null, w.why)
    val cal = Calendar.getInstance(tz)
    cal.timeInMillis = at
    return AlarmDecision(
        AlarmItem(id, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), emptySet(), label.trim().take(200), "aurora",
            true, onceAt = at, created = now), ""
    )
}

fun alarmDaysText(a: AlarmItem): String = when {
    a.days.isEmpty() -> "один раз"
    a.days.size == 7 -> "каждый день"
    a.days == setOf(1, 2, 3, 4, 5) -> "по будням"
    a.days == setOf(6, 7) -> "по выходным"
    else -> a.days.sorted().joinToString(" ") { ALARM_DAY_NAMES[it - 1] }
}

fun alarmTime(a: AlarmItem): String = "%02d:%02d".format(a.hour, a.minute)

/** Что она видит о будильниках: ближайшие и что Адонис сделал с её звонками. */
fun alarmsContext(list: List<AlarmItem>, now: Long, tz: TimeZone = TimeZone.getDefault()): String {
    val f = SimpleDateFormat("EEE d MMM, HH:mm", Locale("ru")).apply { timeZone = tz }
    val upcoming = list.mapNotNull { a -> nextFire(a, now, tz)?.let { Pair(a, it) } }.sortedBy { it.second }.take(6)
    val recent = list.filter { it.by == "aurora" && it.lastActionAt > now - 48 * 3_600_000L && it.lastAction.isNotEmpty() }
    if (upcoming.isEmpty() && recent.isEmpty()) return ""
    val sb = StringBuilder("БУДИЛЬНИКИ:")
    upcoming.forEach { (a, at) ->
        sb.append("\n- ${f.format(Date(at))}, ${alarmDaysText(a)}, ставил${if (a.by == "aurora") "а ты" else " Адонис"}")
        if (a.label.isNotBlank()) sb.append(": «${a.label}»")
    }
    recent.forEach { a ->
        val what = when (a.lastAction) {
            "dismissed" -> "выключил"
            "snoozed" -> "отложил на $ALARM_SNOOZE_MIN минут"
            else -> "не ответил (звонок прозвучал и смолк сам)"
        }
        sb.append("\n- твой будильник «${a.label.ifBlank { alarmTime(a) }}»: Адонис $what, ${f.format(Date(a.lastActionAt))}")
    }
    return sb.toString()
}
