package com.victorfalcon.dose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand palette: the amber/terracotta scheme of the redesign. Dynamic color (Material You)
// wins when it's on; this is what the app looks like with it off.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF9A4B23),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF5F2A00),
    secondary = Color(0xFF77574A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBDDCB),
    onSecondaryContainer = Color(0xFF35200F),
    tertiary = Color(0xFF63612F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9E6A7),
    onTertiaryContainer = Color(0xFF1E1D00),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF5E0D6),
    onSurfaceVariant = Color(0xFF56443B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFBEAE1),
    surfaceContainerHigh = Color(0xFFF5E4DB),
    surfaceContainerHighest = Color(0xFFEFDED5),
    surfaceDim = Color(0xFFE8D7CE),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFDCC7BC),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF601410),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB68E),
    onPrimary = Color(0xFF542100),
    primaryContainer = Color(0xFF78310B),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE7BFA8),
    onSecondary = Color(0xFF44291B),
    secondaryContainer = Color(0xFF56412F),
    onSecondaryContainer = Color(0xFFFBDDCB),
    tertiary = Color(0xFFCDCA8D),
    onTertiary = Color(0xFF343205),
    tertiaryContainer = Color(0xFF4B4919),
    onTertiaryContainer = Color(0xFFE9E6A7),
    background = Color(0xFF1A120D),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF1A120D),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF54443C),
    onSurfaceVariant = Color(0xFFD8C2B8),
    surfaceContainerLowest = Color(0xFF140C08),
    surfaceContainerLow = Color(0xFF221A15),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF322822),
    surfaceContainerHighest = Color(0xFF3D332D),
    surfaceDim = Color(0xFF1A120D),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF54443C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * Dose-state colors, deliberately outside the color scheme: "taken" is green whatever the
 * wallpaper says, so a filled dose shape always reads as a good outcome. Missed/skipped reuse
 * the scheme's error role.
 */
@Immutable
data class DoseStateColors(
    val taken: Color,
    val onTaken: Color,
    val takenContainer: Color,
    val onTakenContainer: Color,
)

private val LightDoseState = DoseStateColors(
    taken = Color(0xFF2E7D32),
    onTaken = Color.White,
    takenContainer = Color(0xFFD9EBD6),
    onTakenContainer = Color(0xFF12401A),
)

private val DarkDoseState = DoseStateColors(
    taken = Color(0xFF4E9A54),
    onTaken = Color.White,
    takenContainer = Color(0xFF21351F),
    onTakenContainer = Color(0xFFB6E5B4),
)

private val LocalDoseStateColors = staticCompositionLocalOf { LightDoseState }

/** The dose-state palette for the current theme. `MaterialTheme.colorScheme` for everything else. */
val doseStateColors: DoseStateColors
    @Composable @ReadOnlyComposable get() = LocalDoseStateColors.current

// The widget can't read composition locals, so it takes the same green from
// R.color.dose_taken (values / values-night) — keep the two in step.

/**
 * Material 3 Expressive theme with dynamic color (Material You). The expressive motion scheme is
 * what gives components their spring-based press and morph feedback, so it's set once here rather
 * than per component.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DoseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(
        LocalDoseStateColors provides if (darkTheme) DarkDoseState else LightDoseState,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
