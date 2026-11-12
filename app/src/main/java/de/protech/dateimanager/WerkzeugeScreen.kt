package de.protech.dateimanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WerkzeugeScreen(
    vm: DateiViewModel,
    onZurueck: () -> Unit,
    onOrdner: (File) -> Unit,
    onDatei: (File) -> Unit
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    var reiter by remember { mutableIntStateOf(0) }
    var auswahl by remember { mutableStateOf(setOf<String>()) }
    var loeschenOffen by remember { mutableStateOf(false) }

    if (loeschenOffen) {
        AlertDialog(
            onDismissRequest = { loeschenOffen = false },
            title = { Text("${auswahl.size} Dateien löschen?") },
            text = {
                Text(
                    "Die Dateien wandern in den Papierkorb und lassen sich von dort " +
                        "wiederherstellen. Von jeder Gruppe bleibt mindestens eine " +
                        "Datei übrig, solange du nicht alle Häkchen setzt."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.loescheDateien(auswahl.map { File(it) })
                    auswahl = emptySet()
                    loeschenOffen = false
                }) { Text("In den Papierkorb") }
            },
            dismissButton = {
                TextButton(onClick = { loeschenOffen = false }) { Text("Abbrechen") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (auswahl.isEmpty()) "Aufräumen" else "${auswahl.size} ausgewählt")
                },
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    if (reiter == 1 && z.doppelte.isNotEmpty()) {
                        IconButton(onClick = {
                            // Pro Gruppe die erste Datei behalten, den Rest markieren.
                            auswahl = z.doppelte
                                .flatMap { gruppe -> gruppe.dateien.drop(1) }
                                .map { it.absolutePath }
                                .toSet()
                        }) { Icon(Icons.Filled.DoneAll, "Duplikate auswählen") }
                    }
                    if (auswahl.isNotEmpty()) {
                        IconButton(onClick = { loeschenOffen = true }) {
                            Icon(Icons.Filled.Delete, "In den Papierkorb")
                        }
                    }
                }
            )
        }
    ) { innen ->
        Column(modifier = Modifier.padding(innen).fillMaxSize()) {
            TabRow(selectedTabIndex = reiter) {
                Tab(
                    selected = reiter == 0,
                    onClick = { reiter = 0; auswahl = emptySet() },
                    text = { Text("Speicherplatz") }
                )
                Tab(
                    selected = reiter == 1,
                    onClick = { reiter = 1 },
                    text = { Text("Doppelte") }
                )
            }

            if (z.arbeitet) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            "Der Speicher wird durchsucht. Das kann einen Moment dauern.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else if (reiter == 0) {
                SpeicherTeil(z.bericht, vm::erstelleBericht, onOrdner, onDatei)
            } else {
                DoppelteTeil(
                    z = z,
                    auswahl = auswahl,
                    onStart = { auswahl = emptySet(); vm.findeDoppelte() },
                    onModus = vm::setzeDoppelModus,
                    onTyp = vm::schalteDoppelTyp,
                    onAlleTypen = vm::doppelTypenZuruecksetzen,
                    onSchalten = { pfad ->
                        auswahl = if (pfad in auswahl) auswahl - pfad else auswahl + pfad
                    },
                    onOeffnen = onDatei
                )
            }
        }
    }
}

// --- Speicherplatz --------------------------------------------------------

@Composable
private fun SpeicherTeil(
    bericht: SpeicherBericht?,
    onStart: () -> Unit,
    onOrdner: (File) -> Unit,
    onDatei: (File) -> Unit
) {
    if (bericht == null) {
        Starter(
            "Zeigt, welche Ordner und Dateien den meisten Platz belegen.",
            "Speicher auswerten",
            onStart
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Belegt: ${groesseLesbar(bericht.gesamt)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        item { Ueberschrift("Größte Ordner") }
        items(bericht.ordner) { posten -> BalkenZeile(posten, bericht.gesamt, onOrdner) }

        item { Ueberschrift("Größte einzelne Dateien") }
        items(bericht.groessteDateien) { posten ->
            ListItem(
                modifier = Modifier.clickable { onDatei(posten.datei) },
                headlineContent = {
                    Text(posten.datei.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        groesseLesbar(posten.groesse) + "\n" +
                            (posten.datei.parent ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    TextButton(onClick = { posten.datei.parentFile?.let(onOrdner) }) {
                        Text("Ordner")
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun BalkenZeile(posten: SpeicherPosten, gesamt: Long, onOrdner: (File) -> Unit) {
    val anteil = if (gesamt > 0) (posten.groesse.toFloat() / gesamt).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (posten.istOrdner) onOrdner(posten.datei) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row {
            Text(posten.datei.name, maxLines = 1, modifier = Modifier.weight(1f))
            Text(groesseLesbar(posten.groesse), style = MaterialTheme.typography.labelMedium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .padding(top = 3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(anteil)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// --- Doppelte -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoppelteTeil(
    z: DateiZustand,
    auswahl: Set<String>,
    onStart: () -> Unit,
    onModus: (DoppelModus) -> Unit,
    onTyp: (DateiTyp) -> Unit,
    onAlleTypen: () -> Unit,
    onSchalten: (String) -> Unit,
    onOeffnen: (File) -> Unit
) {
    val gruppen = z.doppelte
    val verschwendet = gruppen.sumOf { it.verschwendet }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // --- Einstellungen der Suche ---
        item {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Wonach suchen?", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DoppelModus.entries.forEach { modus ->
                        FilterChip(
                            selected = z.doppelModus == modus,
                            onClick = { onModus(modus) },
                            label = { Text(modus.bezeichnung) }
                        )
                    }
                }
                Text(
                    z.doppelModus.erklaerung,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text("Welche Dateiarten?", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = z.doppelTypen.isEmpty(),
                            onClick = onAlleTypen,
                            label = { Text("Alle") }
                        )
                    }
                    items(DateiTyp.entries.filter { it != DateiTyp.ORDNER }) { typ ->
                        FilterChip(
                            selected = typ in z.doppelTypen,
                            onClick = { onTyp(typ) },
                            label = { Text(typ.bezeichnung) }
                        )
                    }
                }

                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(if (z.doppelGesucht) "Neu suchen" else "Doppelte suchen")
                }
            }
            HorizontalDivider()
        }

        // --- Ergebnis ---
        if (gruppen.isEmpty()) {
            item {
                Text(
                    if (z.doppelGesucht) "Keine doppelten Dateien gefunden."
                    else "Wähle oben aus, wonach gesucht werden soll.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            }
            return@LazyColumn
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("${gruppen.size} Gruppen gefunden", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${groesseLesbar(verschwendet)} wären frei, wenn je eine Kopie bliebe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Das Häkchen oben rechts markiert in jeder Gruppe alle Kopien " +
                        "außer der ersten. Tippe auf einen Namen, um die Datei anzusehen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        gruppen.forEachIndexed { index, gruppe ->
            item(key = "kopf_$index") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        gruppe.beschreibung,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${gruppe.dateien.size} Stück · größte ${groesseLesbar(gruppe.groesste)} · " +
                            "${groesseLesbar(gruppe.verschwendet)} einsparbar",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            items(gruppe.dateien, key = { it.absolutePath }) { datei ->
                val gewaehlt = datei.absolutePath in auswahl
                ListItem(
                    leadingContent = {
                        Checkbox(
                            checked = gewaehlt,
                            onCheckedChange = { onSchalten(datei.absolutePath) }
                        )
                    },
                    headlineContent = {
                        Text(
                            datei.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            groesseLesbar(runCatching { datei.length() }.getOrDefault(0L)) +
                                "\n" + (datei.parent ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3
                        )
                    },
                    modifier = Modifier.clickable { onOeffnen(datei) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

// --- Gemeinsames ----------------------------------------------------------

@Composable
private fun Starter(text: String, knopf: String, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onStart) { Text(knopf) }
    }
}

@Composable
private fun Ueberschrift(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
