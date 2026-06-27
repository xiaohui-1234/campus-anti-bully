package com.campus.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = CampusBlue,
    secondary = CampusTeal,
    background = CampusBackground,
    surface = CampusCard,
    onPrimary = CampusCard,
    onSecondary = CampusCard,
    onBackground = CampusText,
    onSurface = CampusText,
    error = CampusDanger
)

@Composable
fun CampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
