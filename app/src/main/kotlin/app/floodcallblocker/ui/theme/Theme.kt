package app.floodcallblocker.ui.theme

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
import app.floodcallblocker.data.ThemeMode

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF1F5F66),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFBCEBF1),
        onPrimaryContainer = Color(0xFF001F23),
        secondary = Color(0xFF4A6366),
        secondaryContainer = Color(0xFFCCE8EB),
        tertiary = Color(0xFF7A5900),
        tertiaryContainer = Color(0xFFFFDEA1),
        onTertiaryContainer = Color(0xFF261900),
        errorContainer = Color(0xFFFFDAD6),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFA0CFD5),
        onPrimary = Color(0xFF00363B),
        primaryContainer = Color(0xFF004F55),
        onPrimaryContainer = Color(0xFFBCEBF1),
        secondary = Color(0xFFB1CBCF),
        secondaryContainer = Color(0xFF324B4F),
        tertiary = Color(0xFFF2BF48),
        tertiaryContainer = Color(0xFF5C4300),
        onTertiaryContainer = Color(0xFFFFDEA1),
        errorContainer = Color(0xFF93000A),
    )

@Composable
fun FloodTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val context = LocalContext.current
    val colors =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            dark -> DarkColors
            else -> LightColors
        }
    MaterialTheme(colorScheme = colors, content = content)
}
