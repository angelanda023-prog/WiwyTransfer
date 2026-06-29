package com.wiwy.wiwytransfer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WiwyBlue = Color(0xFF2962FF)
private val WiwyBlueDark = Color(0xFF82B1FF)

@Composable
fun wiwyColorScheme(): ColorScheme =
    if (isSystemInDarkTheme()) {
        darkColorScheme(primary = WiwyBlueDark, secondary = WiwyBlueDark)
    } else {
        lightColorScheme(primary = WiwyBlue, secondary = WiwyBlue)
    }
