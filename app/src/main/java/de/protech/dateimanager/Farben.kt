package de.protech.dateimanager

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Die sieben Grundfarben zur Auswahl, als Farbton auf dem Farbkreis. */
data class Grundfarbe(val bezeichnung: String, val farbton: Float)

val GRUNDFARBEN = listOf(
    Grundfarbe("Blau", 212f),
    Grundfarbe("Grün", 135f),
    Grundfarbe("Gelb", 48f),
    Grundfarbe("Rosa", 332f),
    Grundfarbe("Braun", 24f),
    Grundfarbe("Violett", 272f),
    Grundfarbe("Türkis", 180f)
)

/** Farbe aus Farbton (0-360), Saettigung und Helligkeit (je 0-1). */
fun ausHsl(farbton: Float, saettigung: Float, helligkeit: Float): Color {
    val h = ((farbton % 360f) + 360f) % 360f
    val s = saettigung.coerceIn(0f, 1f)
    val l = helligkeit.coerceIn(0f, 1f)

    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f

    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/**
 * Baut ein vollstaendiges Material-Farbschema aus einem Farbton.
 * [intensitaet] steuert die Saettigung: 0 fast grau, 1 kraeftig.
 */
fun schemaAus(farbton: Float, intensitaet: Float, dunkel: Boolean): ColorScheme {
    val s = (0.15f + intensitaet * 0.65f).coerceIn(0.05f, 0.95f)
    val zweit = farbton + 32f
    val dritt = farbton + 150f

    return if (dunkel) {
        darkColorScheme(
            primary = ausHsl(farbton, s * 0.75f, 0.76f),
            onPrimary = ausHsl(farbton, s, 0.16f),
            primaryContainer = ausHsl(farbton, s * 0.8f, 0.28f),
            onPrimaryContainer = ausHsl(farbton, s * 0.5f, 0.90f),
            secondary = ausHsl(zweit, s * 0.45f, 0.74f),
            onSecondary = ausHsl(zweit, s * 0.6f, 0.16f),
            secondaryContainer = ausHsl(zweit, s * 0.5f, 0.26f),
            onSecondaryContainer = ausHsl(zweit, s * 0.35f, 0.89f),
            tertiary = ausHsl(dritt, s * 0.5f, 0.75f),
            onTertiary = ausHsl(dritt, s * 0.6f, 0.17f),
            tertiaryContainer = ausHsl(dritt, s * 0.5f, 0.27f),
            onTertiaryContainer = ausHsl(dritt, s * 0.35f, 0.90f),
            background = ausHsl(farbton, s * 0.12f, 0.08f),
            onBackground = ausHsl(farbton, s * 0.10f, 0.90f),
            surface = ausHsl(farbton, s * 0.12f, 0.08f),
            onSurface = ausHsl(farbton, s * 0.10f, 0.90f),
            surfaceVariant = ausHsl(farbton, s * 0.18f, 0.22f),
            onSurfaceVariant = ausHsl(farbton, s * 0.14f, 0.78f),
            outline = ausHsl(farbton, s * 0.16f, 0.55f)
        )
    } else {
        lightColorScheme(
            primary = ausHsl(farbton, s, 0.38f),
            onPrimary = Color.White,
            primaryContainer = ausHsl(farbton, s * 0.7f, 0.88f),
            onPrimaryContainer = ausHsl(farbton, s, 0.16f),
            secondary = ausHsl(zweit, s * 0.55f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = ausHsl(zweit, s * 0.45f, 0.89f),
            onSecondaryContainer = ausHsl(zweit, s * 0.7f, 0.17f),
            tertiary = ausHsl(dritt, s * 0.6f, 0.38f),
            onTertiary = Color.White,
            tertiaryContainer = ausHsl(dritt, s * 0.5f, 0.89f),
            onTertiaryContainer = ausHsl(dritt, s * 0.7f, 0.16f),
            background = ausHsl(farbton, s * 0.10f, 0.985f),
            onBackground = ausHsl(farbton, s * 0.25f, 0.11f),
            surface = ausHsl(farbton, s * 0.10f, 0.985f),
            onSurface = ausHsl(farbton, s * 0.25f, 0.11f),
            surfaceVariant = ausHsl(farbton, s * 0.22f, 0.90f),
            onSurfaceVariant = ausHsl(farbton, s * 0.30f, 0.32f),
            outline = ausHsl(farbton, s * 0.20f, 0.52f)
        )
    }
}
