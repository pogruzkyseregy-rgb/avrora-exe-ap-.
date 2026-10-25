package ru.avrora.exe

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Подгонка фона виджета: картинку двигают пальцем и увеличивают двумя пальцами или ползунком. Рамка повторяет пропорции виджета. */
@Composable
fun CropEditor(bmp: Bitmap, aspect: Float, initial: RectF?, dimPercent: Int, onCancel: () -> Unit, onDone: (RectF) -> Unit) {
    val img = remember(bmp) { bmp.asImageBitmap() }
    val bw = bmp.width.toFloat()
    val bh = bmp.height.toFloat()
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var ready by remember { mutableStateOf(false) }

    fun base(): Float = if (frame.width == 0) 1f else maxOf(frame.width / bw, frame.height / bh)
    fun clamp(o: Offset, s: Float) = Offset(
        o.x.coerceIn(minOf(0f, frame.width - bw * s), 0f),
        o.y.coerceIn(minOf(0f, frame.height - bh * s), 0f)
    )
    fun center() {
        zoom = 1f
        val s = base()
        offset = clamp(Offset((frame.width - bw * s) / 2f, (frame.height - bh * s) / 2f), s)
    }
    fun zoomAround(p: Offset, newZoom: Float, pan: Offset = Offset.Zero) {
        val old = base() * zoom
        val nz = newZoom.coerceIn(1f, 6f)
        val ns = base() * nz
        val ip = (p - offset) / old
        offset = clamp(p - ip * ns + pan, ns)
        zoom = nz
    }

    LaunchedEffect(frame) {
        if (!ready && frame.width > 0) {
            if (initial != null && initial.width() > 0f) {
                val s = frame.width / (initial.width() * bw)
                zoom = (s / base()).coerceIn(1f, 6f)
                offset = clamp(Offset(-initial.left * bw * base() * zoom, -initial.top * bh * base() * zoom), base() * zoom)
            } else center()
            ready = true
        }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Night).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Text("ПОДГОНКА ФОНА", color = Violet, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("Двигай картинку пальцем, увеличивай двумя пальцами или ползунком. Рамка такой же формы, как виджет.", color = TextDim, fontSize = 13.sp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, Glow, RoundedCornerShape(18.dp))
                    .onSizeChanged { frame = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, gz, _ -> zoomAround(centroid, zoom * gz, pan) }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val s = base() * zoom
                    drawImage(
                        img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(offset.x.toInt(), offset.y.toInt()),
                        dstSize = IntSize((bw * s).toInt(), (bh * s).toInt())
                    )
                    drawRect(Night.copy(alpha = dimPercent.coerceIn(0, 85) / 100f))
                }
                Column(Modifier.padding(14.dp).align(Alignment.CenterStart)) {
                    Text("AVRORA.EXE", color = Violet, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        SimpleDateFormat("HH:mm", Locale.US).format(Date()),
                        style = TextStyle(color = Glow, fontSize = 40.sp, fontFamily = FontFamily.Monospace, shadow = Shadow(Glow, blurRadius = 18f))
                    )
                    Text("будильник", color = TextMain, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Text("Увеличение", color = TextDim, fontSize = 12.sp)
            Slider(
                value = zoom,
                onValueChange = { zoomAround(Offset(frame.width / 2f, frame.height / 2f), it) },
                valueRange = 1f..6f
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { center() }) { Text("По центру") }
                OutlinedButton(onClick = onCancel) { Text("Отмена") }
                Button(onClick = {
                    val s = base() * zoom
                    onDone(
                        RectF(
                            (-offset.x / s / bw).coerceIn(0f, 1f),
                            (-offset.y / s / bh).coerceIn(0f, 1f),
                            ((frame.width - offset.x) / s / bw).coerceIn(0f, 1f),
                            ((frame.height - offset.y) / s / bh).coerceIn(0f, 1f)
                        )
                    )
                }) { Text("Готово") }
            }
        }
    }
}
