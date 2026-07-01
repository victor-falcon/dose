package com.victorfalcon.dose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Material 3 with dynamic color (Material You). Dynamic color is guaranteed at
// minSdk 31, so no version guard — it falls back to a static scheme only if the
// user disables it later.
// ponytail: MaterialExpressiveTheme is still `internal` in the stable Compose
// Material3 (BOM 2026.06); swap it in here once it graduates to public API.
// Individual expressive components can still be used where they're public.
@Composable
fun DoseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
