package ru.avrora.exe

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Палитра темы. Все экраны берут цвета отсюда, поэтому смена темы перекрашивает приложение целиком. */
data class Palette(
    val id: String,
    val title: String,
    val hint: String,
    val night: Color,
    val panel: Color,
    val bubbleHer: Color,
    val bubbleUser: Color,
    val glow: Color,        // цвет Авроры
    val violet: Color,      // цвет Адониса и акценты
    val magenta: Color,     // HUD-акцент
    val text: Color,
    val dim: Color,
    val edge: Color,
    val terminal: Color,
    /** Цвет Гесперы. */
    val hesp: Color = Color(0xFFF7A8D8),
    /** Светлая тема: тёмный текст на светлом. */
    val light: Boolean = false,
    /** Рисунок рамок: soft — мягкие уголки, mecha — техно-панели со срезами и болтами, hud — тонкий HUD с точками. */
    val frame: String = "soft",
)

val THEMES = listOf(
    Palette(
        "aurora", "Аврора", "флюорит: чёрный, бирюза, фиолет",
        Color(0xFF09060F), Color(0xFF120D1E), Color(0xFF141026), Color(0xFF2A1B52),
        Color(0xFF5EEAD4), Color(0xFFA78BFA), Color(0xFFD946EF),
        Color(0xFFE6E1F2), Color(0xFF9088AB), Color(0xFF2B2145), Color(0xFF07040C)
    ),
    Palette(
        "cyber", "Киберпанк", "неоновый жёлтый, розовый и циан, сетка горизонта",
        Color(0xFF07070C), Color(0xFF111119), Color(0xFF12161D), Color(0xFF2B0E2C),
        Color(0xFFFCEE0A), Color(0xFFFF2A6D), Color(0xFF05D9E8),
        Color(0xFFF2F2F2), Color(0xFF8A8FA3), Color(0xFF2A2A3A), Color(0xFF050508)
    ),
    Palette(
        "quantum", "Квантовая", "глубокий синий, электрический циан, орбиты и частицы",
        Color(0xFF040716), Color(0xFF0A1128), Color(0xFF0C1733), Color(0xFF1A2453),
        Color(0xFF7DF9FF), Color(0xFF8C9EFF), Color(0xFFE0AAFF),
        Color(0xFFE8F0FF), Color(0xFF7F8FB3), Color(0xFF1C2A4D), Color(0xFF030512)
    ),
    Palette(
        "ink", "Чернила", "светлая: бумага, тушь и красный, техно-панели со срезами",
        Color(0xFFE8E6E1), Color(0xFFFBFAF8), Color(0xFFFFFFFF), Color(0xFFDDDAD3),
        Color(0xFFD7263D), Color(0xFF1C1C1F), Color(0xFFE4404F),
        Color(0xFF141416), Color(0xFF6F6E73), Color(0xFFB9B7B1), Color(0xFFDFDDD8),
        hesp = Color(0xFF2F5D8C), light = true, frame = "mecha"
    ),
    Palette(
        "coral", "Коралл", "тёмная: чёрный и коралловый HUD, тонкие рамки и точки",
        Color(0xFF070606), Color(0xFF110C0B), Color(0xFF170F0D), Color(0xFF2A1410),
        Color(0xFFFF6B57), Color(0xFFFFC2B4), Color(0xFFFF3D3D),
        Color(0xFFF6EAE6), Color(0xFF8E726B), Color(0xFF3B2421), Color(0xFF0A0605),
        hesp = Color(0xFFFFD27A), frame = "hud"
    )
)

object AppTheme {
    var current by mutableStateOf(THEMES[0])
    fun set(id: String) { current = THEMES.firstOrNull { it.id == id } ?: THEMES[0] }
}

// Имена остались прежними, чтобы экраны не менять: это не константы, а текущая тема.
val Night: Color get() = AppTheme.current.night
val Panel: Color get() = AppTheme.current.panel
val BubbleAurora: Color get() = AppTheme.current.bubbleHer
val BubbleUser: Color get() = AppTheme.current.bubbleUser
val Glow: Color get() = AppTheme.current.glow
val Violet: Color get() = AppTheme.current.violet
val TextMain: Color get() = AppTheme.current.text
val TextDim: Color get() = AppTheme.current.dim
val Edge: Color get() = AppTheme.current.edge
val Terminal: Color get() = AppTheme.current.terminal
val Magenta: Color get() = AppTheme.current.magenta
val HespColor: Color get() = AppTheme.current.hesp
val ErrorRed = Color(0xFFFF8F8F)

fun auroraColors() = if (AppTheme.current.light) androidx.compose.material3.lightColorScheme(
    primary = Glow,
    onPrimary = Color.White,
    secondary = Violet,
    background = Night,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    onSurfaceVariant = TextDim,
    outline = Edge,
    primaryContainer = BubbleUser,
    onPrimaryContainer = TextMain,
    error = Color(0xFFB3261E)
) else darkColorScheme(
    primary = Glow,
    onPrimary = Night,
    secondary = Violet,
    background = Night,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    onSurfaceVariant = TextDim,
    outline = Edge,
    primaryContainer = BubbleUser,
    onPrimaryContainer = TextMain,
    error = ErrorRed
)

/** Обычный шрифт мессенджера. Моноширинным остаются только служебные строки и имя системы. */
val ConsoleTypography: Typography = Typography()

/** Скруглённые углы мессенджера. */
val ConsoleShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun auroraFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    cursorColor = Glow,
    focusedBorderColor = Violet.copy(alpha = 0.8f),
    unfocusedBorderColor = Edge,
    focusedPlaceholderColor = TextDim,
    unfocusedPlaceholderColor = TextDim,
    focusedLabelColor = Glow,
    unfocusedLabelColor = TextDim
)
