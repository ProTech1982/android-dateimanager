package de.protech.dateimanager

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

private val HellSchema = lightColorScheme(
    primary = Color(0xFF1B5E9B),
    secondary = Color(0xFF4A6B8A),
    tertiary = Color(0xFF7A5BA8)
)

private val DunkelSchema = darkColorScheme(
    primary = Color(0xFF9BC8F5),
    secondary = Color(0xFFB4C9DE),
    tertiary = Color(0xFFD3BBF5)
)

@Composable
fun DateimanagerTheme(content: @Composable () -> Unit) {
    val dunkel = isSystemInDarkTheme()
    val context = LocalContext.current

    val schema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dunkel) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dunkel -> DunkelSchema
        else -> HellSchema
    }

    MaterialTheme(colorScheme = schema, content = content)
}
