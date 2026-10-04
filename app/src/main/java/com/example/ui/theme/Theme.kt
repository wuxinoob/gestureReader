package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 🍃 深色模式：柔夜墨竹护眼调 (深邃墨绿、柔光玉翠、低眩光)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6EBE92),              // 柔和薄荷玉翠
    onPrimary = Color(0xFF0F2B1D),
    primaryContainer = Color(0xFF1E432F),     // 沉稳墨林绿容器
    onPrimaryContainer = Color(0xFFC7EBD6),
    secondary = Color(0xFF90B59E),            // 水墨青灰
    onSecondary = Color(0xFF13281C),
    secondaryContainer = Color(0xFF253D2E),
    onSecondaryContainer = Color(0xFFD4E8DC),
    tertiary = Color(0xFFCFB97D),             // 柔茶琥珀金
    onTertiary = Color(0xFF352B06),
    tertiaryContainer = Color(0xFF4C3E14),
    onTertiaryContainer = Color(0xFFF4E5BE),
    background = HerbDarkBg,                  // 幽竹墨夜 (#0F1512)
    onBackground = HerbDarkTextPrimary,       // 柔白玉色 (#E0EAE3)
    surface = HerbDarkSurface,                // 微光深青卡片面 (#161E1A)
    onSurface = HerbDarkTextPrimary,
    surfaceVariant = HerbDarkSurfaceVariant,  // #1F2B24
    onSurfaceVariant = HerbDarkTextSecondary, // #8CA394
    outline = HerbDarkOutline,                // #2E3E34
    outlineVariant = Color(0xFF1C2820),
    error = Color(0xFFE57373),
    onError = Color(0xFF3E1212)
)

// 🍃 浅色模式 (默认)：竹青护眼纸本调 (低饱和雅致绿、护眼灰白纸张底、温和抗疲劳)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF285C3F),              // 典雅竹青绿 / Forest Sage Green
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EFE7),     // 柔雾青竹容器
    onPrimaryContainer = Color(0xFF143B25),
    secondary = Color(0xFF436B52),            // 水松青灰
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F2EC),
    onSecondaryContainer = Color(0xFF224430),
    tertiary = Color(0xFF8C7332),             // 暖竹茶褐色
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7F0DC),
    onTertiaryContainer = Color(0xFF524112),
    background = HerbPaperBg,                 // 护眼纸本灰白米绿 (#F4F7F4，杜绝刺眼纯白反光)
    onBackground = HerbTextPrimary,           // 深墨青灰 (#213026，温和低对比度)
    surface = HerbSurface,                    // 纯白轻卡片底 (#FFFFFF)
    onSurface = HerbTextPrimary,
    surfaceVariant = HerbSurfaceVariant,      // 柔青灰底 (#EAF1EB)
    onSurfaceVariant = HerbTextSecondary,     // 中灰青 (#55695B)
    outline = HerbOutline,                    // 浅竹灰柔和描边 (#D2DED4)
    outlineVariant = Color(0xFFE2EBE4),
    error = RoseError,                        // 柔和赭石红
    onError = Color.White
)

@Composable
fun GestureReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
