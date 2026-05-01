package cn.verlu.lulu.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LuluGreen,
    onPrimary = LuluInk,
    primaryContainer = LuluBlue,
    onPrimaryContainer = LuluText,
    secondary = LuluSky,
    onSecondary = LuluInk,
    secondaryContainer = LuluInkCard,
    onSecondaryContainer = LuluText,
    tertiary = LuluGreen,
    background = LuluInk,
    onBackground = LuluText,
    surface = LuluInkHigh,
    onSurface = LuluText,
    surfaceVariant = LuluInkCard,
    onSurfaceVariant = LuluTextMuted,
    surfaceContainerLowest = LuluInk,
    surfaceContainerLow = LuluInkHigh,
    surfaceContainer = LuluInkCard,
    surfaceContainerHigh = ColorSchemeDefaults.darkSurfaceHigh,
    surfaceContainerHighest = ColorSchemeDefaults.darkSurfaceHighest,
    outline = ColorSchemeDefaults.darkOutline,
    outlineVariant = ColorSchemeDefaults.darkOutlineVariant,
    error = ColorSchemeDefaults.darkError,
    onError = LuluInk,
)

private val LightColorScheme = lightColorScheme(
    primary = LuluGreenDark,
    onPrimary = LuluPaper,
    primaryContainer = ColorSchemeDefaults.lightPrimaryContainer,
    onPrimaryContainer = LuluPaperText,
    secondary = ColorSchemeDefaults.lightSecondary,
    onSecondary = LuluPaper,
    secondaryContainer = ColorSchemeDefaults.lightSecondaryContainer,
    onSecondaryContainer = LuluPaperText,
    tertiary = LuluBlue,
    background = LuluPaper,
    onBackground = LuluPaperText,
    surface = LuluPaperCard,
    onSurface = LuluPaperText,
    surfaceVariant = LuluPaperCardLow,
    onSurfaceVariant = LuluPaperTextMuted,
    surfaceContainerLowest = LuluPaper,
    surfaceContainerLow = LuluPaperCard,
    surfaceContainer = LuluPaperCardLow,
    surfaceContainerHigh = ColorSchemeDefaults.lightSurfaceHigh,
    surfaceContainerHighest = ColorSchemeDefaults.lightSurfaceHighest,
    outline = ColorSchemeDefaults.lightOutline,
    outlineVariant = ColorSchemeDefaults.lightOutlineVariant,
    error = ColorSchemeDefaults.lightError,
    onError = LuluPaper,
)

@Composable
@Suppress("DEPRECATION")
fun SyncTheme(
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            val lightSystemBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            view.systemUiVisibility =
                if (darkTheme) {
                    view.systemUiVisibility and lightSystemBars.inv()
                } else {
                    view.systemUiVisibility or lightSystemBars
                }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private object ColorSchemeDefaults {
    val darkSurfaceHigh = androidx.compose.ui.graphics.Color(0xFF303A4C)
    val darkSurfaceHighest = androidx.compose.ui.graphics.Color(0xFF394458)
    val darkOutline = androidx.compose.ui.graphics.Color(0xFF758095)
    val darkOutlineVariant = androidx.compose.ui.graphics.Color(0xFF3D485C)
    val darkError = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
    val lightPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDCEFA5)
    val lightSecondary = androidx.compose.ui.graphics.Color(0xFF4C647D)
    val lightSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD8E6F4)
    val lightSurfaceHigh = androidx.compose.ui.graphics.Color(0xFFDDE3D8)
    val lightSurfaceHighest = androidx.compose.ui.graphics.Color(0xFFD3DACF)
    val lightOutline = androidx.compose.ui.graphics.Color(0xFF74796F)
    val lightOutlineVariant = androidx.compose.ui.graphics.Color(0xFFC4C9BE)
    val lightError = androidx.compose.ui.graphics.Color(0xFFBA1A1A)
}
