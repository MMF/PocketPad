package com.mfouad.mobiletrackpad.ui.theme

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

// Fallback palette for Android 9-11, which have no wallpaper-based colours.
private val Indigo = Color(0xFF3949AB)
private val IndigoLight = Color(0xFFB4C1FF)

private val LightColors = lightColorScheme(primary = Indigo)
private val DarkColors = darkColorScheme(primary = IndigoLight)

/**
 * Material 3 theme. Uses the wallpaper-based dynamic colours on Android 12+ and follows the
 * system dark setting. The trackpad screen draws its own true-black surface either way.
 */
@Composable
fun TrackPadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }
    MaterialTheme(colorScheme = scheme, content = content)
}
