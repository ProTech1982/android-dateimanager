package de.protech.dateimanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapierkorbScreen(vm: DateiViewModel, onZurueck: () -> Unit) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    var leerenOffen by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    LaunchedEffect(Unit) { vm.ladePapierkorb() }

    if (leerenOffen) {
        AlertDialog(
            onDismissRequest = { leerenOffen = false },
            title = { Text("Papierkorb leeren?") },
            text = {
                Text(
                    "Alle ${z.papierkorb.size} Einträge werden endgültig gelöscht. " +
                        "Danach gibt es keinen Weg zurück."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.leerePapierkorb(); leerenOffen = false }) {
                    Text("Endgültig löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { leerenOffen = false }) { Text("Abbrechen") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Papierkorb")
                        Text(
                            "${z.papierkorb.size} Einträge · ${groesseLesbar(z.papierkorb.sumOf { it.groesse })}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    if (z.papierkorb.isNotEmpty()) {
                        IconButton(onClick = { leerenOffen = true }) {
                            Icon(Icons.Filled.DeleteForever, "Papierkorb leeren")
                        }
                    }
                }
            )
        }
    ) { innen ->
        Box(modifier = Modifier.padding(innen).fillMaxSize()) {
            if (z.papierkorb.isEmpty()) {
                Text(
                    "Der Papierkorb ist leer.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(z.papierkorb, key = { it.id }) { eintrag ->
                        ListItem(
                            leadingContent = {
                                Icon(
                                    if (eintrag.istOrdner) Icons.Filled.Folder
                                    else Icons.Filled.InsertDriveFile,
                                    null
                                )
                            },
                            headlineContent = { Text(eintrag.name) },
                            supportingContent = {
                                Text(
                                    "${groesseLesbar(eintrag.groesse)} · gelöscht am " +
                                        format.format(Date(eintrag.geloeschtAm)) + "\n" +
                                        eintrag.urspruenglicherPfad,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(onClick = { vm.stelleWiederHer(eintrag) }) {
                                        Icon(Icons.Filled.Restore, "Wiederherstellen")
                                    }
                                    IconButton(onClick = { vm.endgueltigLoeschen(eintrag) }) {
                                        Icon(Icons.Filled.DeleteForever, "Endgültig löschen")
                                    }
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}
