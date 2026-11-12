package de.protech.dateimanager

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinstellungenScreen(einstellungen: Einstellungen, onZurueck: () -> Unit) {
    val e by einstellungen.zustand.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { innen ->
        Column(
            modifier = Modifier
                .padding(innen)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Abschnitt("Helligkeit") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarbModus.entries.forEach { modus ->
                        FilterChip(
                            selected = e.farbModus == modus,
                            onClick = { einstellungen.setzeFarbModus(modus) },
                            label = { Text(modus.bezeichnung) }
                        )
                    }
                }
            }

            Abschnitt("Farbe") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        FilterChip(
                            selected = e.farbQuelle == FarbQuelle.MATERIAL_YOU,
                            onClick = { einstellungen.setzeMaterialYou() },
                            label = { Text("Vom Hintergrundbild (Material You)") }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GRUNDFARBEN.forEachIndexed { index, farbe ->
                            val gewaehlt = e.farbQuelle == FarbQuelle.VOREINSTELLUNG &&
                                e.voreinstellung == index
                            Box(
                                modifier = Modifier
                                    .size(if (gewaehlt) 40.dp else 34.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(ausHsl(farbe.farbton, 0.75f, 0.45f))
                                    .pointerInput(index) {
                                        detectTapGestures {
                                            einstellungen.setzeVoreinstellung(index)
                                        }
                                    }
                            )
                        }
                    }
                    Text(
                        GRUNDFARBEN.joinToString("  ·  ") { it.bezeichnung },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Eigene Farbe", style = MaterialTheme.typography.titleSmall)
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Farbkreisel(
                            farbton = e.eigenerFarbton,
                            aktiv = e.farbQuelle == FarbQuelle.EIGEN,
                            onFarbton = { einstellungen.setzeEigeneFarbe(it) }
                        )
                    }

                    Text(
                        "Intensität: ${(e.intensitaet * 100).toInt()} %",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = e.intensitaet,
                        onValueChange = { einstellungen.setzeIntensitaet(it) },
                        valueRange = 0f..1f
                    )
                    if (e.farbQuelle == FarbQuelle.MATERIAL_YOU) {
                        Text(
                            "Die Intensität wirkt auf die eigenen Farben, nicht auf Material You.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Abschnitt("Ansicht") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ansicht.entries.forEach { a ->
                            FilterChip(
                                selected = e.ansicht == a,
                                onClick = { einstellungen.setzeAnsicht(a) },
                                label = { Text(a.bezeichnung) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Zwei Spalten im Querformat")
                            Text(
                                "Links der Ordnerbaum, rechts der Inhalt — wie im Explorer.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = e.zweiSpalten,
                            onCheckedChange = { einstellungen.setzeZweiSpalten(it) }
                        )
                    }
                }
            }

            Abschnitt("Versteckte Dateien") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Farblich hervorheben")
                            Text(
                                "Dateien und Ordner, die mit einem Punkt beginnen, " +
                                    "bekommen eine eigene Farbe und Umrandung.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = e.versteckteHervorheben,
                            onCheckedChange = { einstellungen.setzeVersteckteHervorheben(it) }
                        )
                    }

                    if (e.versteckteHervorheben) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GRUNDFARBEN.forEach { farbe ->
                                val gewaehlt =
                                    kotlin.math.abs(e.versteckteFarbton - farbe.farbton) < 1f
                                Box(
                                    modifier = Modifier
                                        .size(if (gewaehlt) 40.dp else 34.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(ausHsl(farbe.farbton, 0.75f, 0.45f))
                                        .pointerInput(farbe.farbton) {
                                            detectTapGestures {
                                                einstellungen.setzeVersteckteFarbton(farbe.farbton)
                                            }
                                        }
                                )
                            }
                        }

                        Text("Eigene Farbe", style = MaterialTheme.typography.titleSmall)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Farbkreisel(
                                farbton = e.versteckteFarbton,
                                aktiv = true,
                                onFarbton = { einstellungen.setzeVersteckteFarbton(it) }
                            )
                        }

                        // Vorschau, damit die Wirkung sofort sichtbar ist
                        val dunkel = isSystemInDarkTheme()
                        val istDunkel = when (e.farbModus) {
                            FarbModus.HELL -> false
                            FarbModus.DUNKEL -> true
                            FarbModus.SYSTEM -> dunkel
                        }
                        val hervor = hervorhebung(e.versteckteFarbton, istDunkel)
                        Text("Vorschau", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, hervor.rand, RoundedCornerShape(8.dp))
                                .background(hervor.hintergrund)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(".nomedia", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Abschnitt("Papierkorb") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Automatisch leeren nach ${e.papierkorbTage} Tagen",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = e.papierkorbTage.toFloat(),
                        onValueChange = { einstellungen.setzePapierkorbTage(it.toInt()) },
                        valueRange = 0f..90f,
                        steps = 17
                    )
                    Text(
                        if (e.papierkorbTage == 0) "0 Tage bedeutet: nie automatisch leeren."
                        else "Gelöschtes verschwindet nach ${e.papierkorbTage} Tagen endgültig.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Abschnitt(titel: String, inhalt: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            titel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(14.dp)) { inhalt() }
        }
    }
}

/** Ein Farbring: antippen oder ziehen waehlt den Farbton. */
@Composable
private fun Farbkreisel(farbton: Float, aktiv: Boolean, onFarbton: (Float) -> Unit) {
    val regenbogen = regenbogenFarben()

    Canvas(
        modifier = Modifier
            .size(190.dp)
            .pointerInput(Unit) {
                detectTapGestures { punkt -> onFarbton(winkelAus(punkt, size.width, size.height)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { aenderung, _ ->
                    onFarbton(winkelAus(aenderung.position, size.width, size.height))
                }
            }
    ) {
        val dicke = size.minDimension * 0.18f
        val radius = (size.minDimension - dicke) / 2f

        drawCircle(
            brush = Brush.sweepGradient(regenbogen),
            radius = radius,
            style = Stroke(width = dicke)
        )

        // Markierung auf dem gewaehlten Farbton
        val bogen = Math.toRadians(farbton.toDouble())
        val mx = center.x + radius * cos(bogen).toFloat()
        val my = center.y + radius * sin(bogen).toFloat()
        drawCircle(color = Color.White, radius = dicke * 0.42f, center = Offset(mx, my))
        drawCircle(
            color = ausHsl(farbton, 0.8f, 0.45f),
            radius = dicke * 0.30f,
            center = Offset(mx, my)
        )
        if (aktiv) {
            drawCircle(
                color = ausHsl(farbton, 0.8f, 0.45f),
                radius = radius - dicke
            )
        }
    }
}

@Composable
private fun regenbogenFarben(): List<Color> = androidx.compose.runtime.remember {
    (0..360 step 30).map { ausHsl(it.toFloat(), 0.8f, 0.5f) }
}

private fun winkelAus(punkt: Offset, breite: Int, hoehe: Int): Float {
    val dx = punkt.x - breite / 2f
    val dy = punkt.y - hoehe / 2f
    val grad = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return ((grad % 360f) + 360f) % 360f
}
