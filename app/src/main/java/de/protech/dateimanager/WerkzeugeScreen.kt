package de.protech.dateimanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WerkzeugeScreen(vm: DateiViewModel, onZurueck: () -> Unit, onOrdner: (File) -> Unit) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    var reiter by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aufräumen") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { innen ->
        Column(modifier = Modifier.padding(innen).fillMaxSize()) {
            TabRow(selectedTabIndex = reiter) {
                Tab(
                    selected = reiter == 0,
                    onClick = { reiter = 0 },
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
                SpeicherTeil(z.bericht, onStart = vm::erstelleBericht, onOrdner = onOrdner)
            } else {
                DoppelteTeil(z.doppelte, onStart = vm::findeDoppelte, onOrdner = onOrdner)
            }
        }
    }
}

@Composable
private fun SpeicherTeil(
    bericht: SpeicherBericht?,
    onStart: () -> Unit,
    onOrdner: (File) -> Unit
) {
    if (bericht == null) {
        Starter(
            text = "Zeigt, welche Ordner und Dateien den meisten Platz belegen.",
            knopf = "Speicher auswerten",
            onStart = onStart
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
        items(bericht.ordner) { posten ->
            BalkenZeile(posten, bericht.gesamt, onOrdner)
        }
        item { Ueberschrift("Größte einzelne Dateien") }
        items(bericht.groessteDateien) { posten ->
            ListItem(
                modifier = Modifier.clickable {
                    posten.datei.parentFile?.let(onOrdner)
                },
                headlineContent = { Text(posten.datei.name, maxLines = 1) },
                supportingContent = {
                    Text(groesseLesbar(posten.groesse), style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun DoppelteTeil(
    gruppen: List<DoppelGruppe>,
    onStart: () -> Unit,
    onOrdner: (File) -> Unit
) {
    if (gruppen.isEmpty()) {
        Starter(
            text = "Sucht Dateien mit gleichem Inhalt — auch wenn sie anders heißen.",
            knopf = "Doppelte suchen",
            onStart = onStart
        )
        return
    }

    val verschwendet = gruppen.sumOf { it.verschwendet }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "${gruppen.size} Gruppen · ${groesseLesbar(verschwendet)} wären frei, " +
                    "wenn je eine Kopie bliebe",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp)
            )
        }
        items(gruppen) { gruppe ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "${gruppe.dateien.size}× ${groesseLesbar(gruppe.groesse)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                gruppe.dateien.forEach { datei ->
                    Text(
                        text = datei.absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datei.parentFile?.let(onOrdner) }
                            .padding(vertical = 3.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        item {
            Text(
                "Zum Löschen tippe einen Pfad an, um in den Ordner zu springen. " +
                    "Gelöschtes landet im Papierkorb.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

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
