package com.ontk.musicstats

import android.app.Activity
import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

data class AppFont(val id: Int, val label: String, val family: FontFamily)

val APP_FONTS = listOf(
    AppFont(0, "Системный", FontFamily.Default),
    AppFont(1, "Рубленый", FontFamily.SansSerif),
    AppFont(2, "С засечками", FontFamily.Serif),
    AppFont(3, "Узкий", FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))),
    AppFont(4, "Тонкий", FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))),
    AppFont(5, "Моноширинный", FontFamily.Monospace),
    AppFont(6, "Рукописный", FontFamily.Cursive)
)

data class Palette(
    val id: Int,
    val name: String,
    val accent: Color,
    val darkBg: Color,
    val lightBg: Color
)

/** Готовые палитры. id = 4 зарезервирован под «свой цвет». */
val PALETTES = listOf(
    Palette(0, "Красный", Color(0xFFE5262B), Color(0xFF0A0A0A), Color(0xFFF7F5F2)),
    Palette(1, "Кислота", Color(0xFFC6F000), Color(0xFF0A0A0A), Color(0xFFF5F6EE)),
    Palette(2, "Лёд", Color(0xFF3AA8FF), Color(0xFF080B10), Color(0xFFF1F5F9)),
    Palette(3, "Сахар", Color(0xFFFF4F8B), Color(0xFF100A0C), Color(0xFFFFF4F7))
)

private val CUSTOM_DARK_BG = Color(0xFF0A0A0A)
private val CUSTOM_LIGHT_BG = Color(0xFFF7F6F4)


private fun buildColors(dark: Boolean, accent: Color, bg: Color): ColorScheme {
    val ink = if (dark) Color(0xFFF5F5F5) else Color(0xFF0A0A0A)
    val onAccent = if (accent.luminance() > 0.22f) Color(0xFF0A0A0A) else Color.White
    val surface = if (dark) lerp(bg, Color.White, 0.06f) else Color.White
    val surfaceVariant =
        if (dark) lerp(bg, Color.White, 0.12f) else lerp(bg, Color.Black, 0.07f)
    val muted = ink.copy(alpha = 0.62f)
    val outline = ink.copy(alpha = 0.2f)
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = bg,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = muted,
            outline = outline,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            error = Color(0xFFFF6B6B)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = bg,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = muted,
            outline = outline,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            error = Color(0xFFC62828)
        )
    }
}

private fun Typography.withFont(f: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (AppSettings.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val palette = PALETTES.firstOrNull { it.id == AppSettings.paletteId }
    val accent = palette?.accent ?: Color(AppSettings.customColor)
    val bg = if (dark) {
        palette?.darkBg ?: CUSTOM_DARK_BG
    } else {
        palette?.lightBg ?: CUSTOM_LIGHT_BG
    }
    val font = APP_FONTS.firstOrNull { it.id == AppSettings.fontId } ?: APP_FONTS[0]
    val typography = remember(font.id) { Typography().withFont(font.family) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = buildColors(dark, accent, bg),
        typography = typography,
        content = content
    )
}