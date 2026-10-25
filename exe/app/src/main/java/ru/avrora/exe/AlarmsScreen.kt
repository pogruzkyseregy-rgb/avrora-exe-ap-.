package ru.avrora.exe

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlarmsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<AlarmItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<AlarmItem?>(null) }
    val now = System.currentTimeMillis()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun needNotif() = Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    SettingsPage("Будильник", onBack) {
        if (needNotif()) {
            Hint("Без разрешения на уведомления звонок не сможет показать экран и кнопки.")
            OutlinedButton(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Разрешить уведомления") }
        }
        val exactOk = remember { AlarmScheduler.canExact(ctx) }
        if (!exactOk) {
            Hint("Системе не разрешено звонить точно по времени: будильник может опоздать. Разреши «Будильники и напоминания».")
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
            }) { Text("Открыть настройку") }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                Hint("Чтобы звонок открывался во весь экран поверх блокировки, разреши приложению полноэкранные уведомления.")
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)))
                }) { Text("Разрешить во весь экран") }
            }
        }
        val next = nextAlarm(vm.alarms.toList(), now)
        Box(
            Modifier.fillMaxWidth().background(Terminal, RoundedCornerShape(6.dp)).border(1.dp, Edge, RoundedCornerShape(6.dp))
                .cornerBrackets(Glow).padding(14.dp)
        ) {
            Column {
                Text("БЛИЖАЙШИЙ", color = Violet, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(
                    next?.let { SimpleDateFormat("HH:mm", Locale.US).format(Date(it.second)) } ?: "--:--",
                    style = TextStyle(color = Glow, fontSize = 44.sp, fontFamily = FontFamily.Monospace, shadow = Shadow(Glow, blurRadius = 20f))
                )
                Text(
                    next?.let { "через " + humanGap(it.second - now) + (if (it.first.by == "aurora") " · поставила Аврора" else "") + (if (it.first.label.isNotBlank()) " · ${it.first.label}" else "") }
                        ?: "будильников нет",
                    color = TextDim, fontSize = 12.sp
                )
            }
        }
        Button(onClick = {
            if (needNotif()) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            adding = true
        }, modifier = Modifier.fillMaxWidth()) { Text("Добавить будильник") }
        vm.alarms.sortedWith(compareBy({ it.hour }, { it.minute })).forEach { a ->
            AlarmRow(a, now, onToggle = { vm.toggleAlarm(a, it) }, onEdit = { editing = a }, onDelete = { toDelete = a })
        }
        WidgetSection(vm)
        SectionTitle("АВРОРА")
        SwitchRow(
            "Аврора может ставить будильник",
            "чтобы позвать тебя звонком: не больше $AURORA_ALARM_DAILY_CAP в сутки, не дальше двух суток",
            vm.alarmByAurora
        ) { vm.setAlarmRight(it) }
        Hint("Её будильник звонит так же громко, на экране её подпись и кнопка «Выключить и ответить ей». Если выключишь, отложишь или пропустишь, она это узнает.")
    }
    if (adding) AlarmEditor(null, onDismiss = { adding = false }) { h, m, d, l -> vm.addAlarm(h, m, d, l); adding = false }
    editing?.let { e ->
        AlarmEditor(e, onDismiss = { editing = null }) { h, m, d, l ->
            vm.updateAlarm(e.copy(hour = h, minute = m, days = d, label = l, enabled = true, snoozeAt = 0L, onceAt = 0L)); editing = null
        }
    }
    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Panel,
            title = { Text("Удалить будильник ${alarmTime(a)}?") },
            text = { Text(if (a.by == "aurora") "Его поставила Аврора. Она узнает, что ты удалил." else alarmDaysText(a)) },
            confirmButton = { TextButton(onClick = { vm.deleteAlarm(a); toDelete = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun AlarmRow(a: AlarmItem, now: Long, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val hers = a.by == "aurora"
    val accent = if (hers) Glow else Violet
    val active = nextFire(a, now) != null
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(6.dp)).border(1.dp, if (active) accent.copy(alpha = 0.6f) else Edge, RoundedCornerShape(6.dp))
            .clickable(enabled = !hers) { onEdit() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(alarmTime(a), color = if (active) TextMain else TextDim, fontSize = 30.sp, fontFamily = FontFamily.Monospace)
            Text(alarmDaysText(a) + (if (a.snoozeAt > now) " · отложен" else ""), color = TextDim, fontSize = 12.sp)
            if (a.label.isNotBlank()) Text("«${a.label}»", color = TextMain, fontSize = 13.sp)
            if (hers) Text("поставила Аврора", color = Glow, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Column(horizontalAlignment = Alignment.End) {
            Switch(checked = a.enabled && active, onCheckedChange = onToggle)
            TextButton(onClick = onDelete) { Text("удалить", color = TextDim, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun AlarmEditor(a: AlarmItem?, onDismiss: () -> Unit, onSave: (Int, Int, Set<Int>, String) -> Unit) {
    val ctx = LocalContext.current
    var hour by remember { mutableStateOf(a?.hour ?: 7) }
    var minute by remember { mutableStateOf(a?.minute ?: 30) }
    var days by remember { mutableStateOf(a?.days ?: emptySet()) }
    var label by remember { mutableStateOf(a?.label ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(if (a == null) "Новый будильник" else "Будильник") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "%02d:%02d".format(hour, minute),
                    style = TextStyle(color = Glow, fontSize = 42.sp, fontFamily = FontFamily.Monospace, shadow = Shadow(Glow, blurRadius = 16f)),
                    modifier = Modifier.clickable {
                        TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    }
                )
                Text("нажми на время, чтобы изменить", color = TextDim, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ALARM_DAY_NAMES.forEachIndexed { i, name ->
                        val d = i + 1
                        val on = d in days
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(if (on) BubbleUser else Terminal)
                                .border(1.dp, if (on) Violet else Edge, CircleShape)
                                .clickable { days = if (on) days - d else days + d },
                            contentAlignment = Alignment.Center
                        ) { Text(name, color = if (on) TextMain else TextDim, fontSize = 11.sp) }
                    }
                }
                Text(if (days.isEmpty()) "дни не выбраны: прозвонит один раз" else alarmDaysText(AlarmItem("", 0, 0, days)), color = TextDim, fontSize = 11.sp)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(200) },
                    placeholder = { Text("Подпись (необязательно)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = auroraFieldColors()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(hour, minute, days, label) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun WidgetSection(vm: ChatViewModel) {
    val ctx = LocalContext.current
    var pickAlbum by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setWidgetBgUri(uri) }
    SectionTitle("ВИДЖЕТ")
    Hint("Виджет «AVRORA.EXE · часы и будильник»: крупно время (при смене минуты по цифрам пробегает глитч) и ближайший будильник. Добавить: долгое нажатие на рабочем столе → Виджеты.")
    val preview = remember(vm.widgetBgVersion) { AlarmWidget.previewFile(ctx) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(18.dp)).background(Night)
            .border(1.dp, Glow.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
    ) {
        if (preview != null) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(ctx).data(preview).memoryCacheKey("wbg${vm.widgetBgVersion}").diskCacheKey("wbg${vm.widgetBgVersion}").build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(Modifier.padding(14.dp).align(Alignment.CenterStart)) {
            Text("AVRORA.EXE", color = Violet, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(
                SimpleDateFormat("HH:mm", Locale.US).format(Date()),
                style = TextStyle(color = Glow, fontSize = 40.sp, fontFamily = FontFamily.Monospace, shadow = Shadow(Glow, blurRadius = 18f))
            )
            val next = nextAlarm(vm.alarms.toList(), System.currentTimeMillis())
            Text(next?.let { "будильник " + alarmTime(it.first) } ?: "будильников нет", color = TextMain, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Из галереи") }
        OutlinedButton(onClick = { pickAlbum = true }) { Text("Из альбома Авроры") }
    }
    if (preview != null) {
        OutlinedButton(onClick = { vm.refitWidgetBg() }) { Text("Подогнать картинку") }
        Text("Затемнение: ${vm.widgetDim}%", color = TextDim, fontSize = 12.sp)
        var dim by remember(vm.widgetDim) { mutableStateOf(vm.widgetDim.toFloat()) }
        androidx.compose.material3.Slider(value = dim, onValueChange = { dim = it }, onValueChangeFinished = { vm.setWidgetDimLevel(dim.toInt()) }, valueRange = 0f..85f)
        TextButton(onClick = { vm.clearWidgetBg() }) { Text("Убрать фон", color = TextDim) }
    }
    SwitchRow("Глитч при смене цифр", "каждую минуту на долю секунды", vm.widgetGlitch) { vm.setWidgetGlitchOn(it) }
    vm.cropSource?.let { bmp ->
        CropEditor(bmp, vm.cropAspect, vm.cropInitial, vm.widgetDim, onCancel = { vm.cancelCrop() }) { vm.applyWidgetCrop(it) }
    }
    if (pickAlbum) {
        val photos = vm.photos.filter { it.model is java.io.File }
        AlertDialog(
            onDismissRequest = { pickAlbum = false },
            containerColor = Panel,
            title = { Text("Альбом Авроры") },
            text = {
                if (photos.isEmpty()) Text("В её альбоме пока нет своих фото.", color = TextDim)
                else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier.height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(photos.size) { i ->
                        val p = photos[i]
                        coil.compose.AsyncImage(
                            model = p.model,
                            contentDescription = p.desc,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { vm.setWidgetBgPhoto(p); pickAlbum = false }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickAlbum = false }) { Text("Закрыть") } }
        )
    }
}
