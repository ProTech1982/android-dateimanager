package de.protech.dateimanager

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun DateimanagerTheme(
    einstellungen: EinstellungsZustand = EinstellungsZustand(),
    content: @Composable () -> Unit
) {
    val systemDunkel = isSystemInDarkTheme()
    val context = LocalContext.current

    val dunkel = when (einstellungen.farbModus) {
        FarbModus.SYSTEM -> systemDunkel
        FarbModus.HELL -> false
        FarbModus.DUNKEL -> true
    }

    val materialYouMoeglich = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val schema = when {
        einstellungen.farbQuelle == FarbQuelle.MATERIAL_YOU && materialYouMoeglich ->
            if (dunkel) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        einstellungen.farbQuelle == FarbQuelle.VOREINSTELLUNG -> schemaAus(
            farbton = GRUNDFARBEN.getOrElse(einstellungen.voreinstellung) { GRUNDFARBEN[0] }.farbton,
            intensitaet = einstellungen.intensitaet,
            dunkel = dunkel
        )

        einstellungen.farbQuelle == FarbQuelle.EIGEN -> schemaAus(
            farbton = einstellungen.eigenerFarbton,
            intensitaet = einstellungen.intensitaet,
            dunkel = dunkel
        )

        // Material You gewuenscht, aber zu altes Android: Blau als Ersatz.
        else -> schemaAus(GRUNDFARBEN[0].farbton, einstellungen.intensitaet, dunkel)
    }

    MaterialTheme(colorScheme = schema, content = content)
}
