package com.winlator.cmod.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb

object WinlatorLegacyTheme {
    private fun colors(context: Context) = winlatorColorScheme(WinlatorThemeManager.currentTheme(context))

    @JvmStatic fun background(context: Context): Int = colors(context).background.toArgb()
    @JvmStatic fun surface(context: Context): Int = colors(context).surface.toArgb()
    @JvmStatic fun surfaceVariant(context: Context): Int = colors(context).surfaceVariant.toArgb()
    @JvmStatic fun onBackground(context: Context): Int = colors(context).onBackground.toArgb()
    @JvmStatic fun onSurface(context: Context): Int = colors(context).onSurface.toArgb()
    @JvmStatic fun onSurfaceVariant(context: Context): Int = colors(context).onSurfaceVariant.toArgb()
    @JvmStatic fun primary(context: Context): Int = colors(context).primary.toArgb()
    @JvmStatic fun onPrimary(context: Context): Int = colors(context).onPrimary.toArgb()
    @JvmStatic fun outlineVariant(context: Context): Int = colors(context).outlineVariant.toArgb()
    @JvmStatic fun isLight(context: Context): Boolean = WinlatorThemeManager.currentTheme(context) == WinlatorThemeType.WHITE
}
