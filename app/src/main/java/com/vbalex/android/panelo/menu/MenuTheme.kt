// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Panelo teal palette (static colors, no dynamic color). */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF14B8A6),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFFD5F5F0),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFF14B8A6),
    onTertiary = Color(0xFF042F2E),
    background = Color(0xFFF7FBFA),
    onBackground = Color(0xFF14201F),
    surface = Color(0xFFF7FBFA),
    onSurface = Color(0xFF14201F),
    surfaceVariant = Color(0xFFDDE8E6),
    onSurfaceVariant = Color(0xFF3F4F4D),
    outline = Color(0xFF6F7F7D),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0F766E),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF14B8A6),
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFF14B8A6),
    onTertiary = Color(0xFF00201C),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE5E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE5E3),
    surfaceVariant = Color(0xFF2A3836),
    onSurfaceVariant = Color(0xFFBFCAC7),
    outline = Color(0xFF899491),
)

@Composable
fun MenuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
