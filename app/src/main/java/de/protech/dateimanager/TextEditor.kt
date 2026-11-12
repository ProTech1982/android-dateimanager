package de.protech.dateimanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Groesste Datei, die sich bearbeiten laesst. Darueber nur Lesen. */
const val EDITOR_GRENZE = 1L * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(datei: File, onSchliessen: () -> Unit) {
    var wert by remember { mutableStateOf<TextFieldValue?>(null) }
    var urspruenglich by remember { mutableStateOf("") }
    var verlauf by remember { mutableStateOf(listOf<String>()) }
    var suchenOffen by remember { mutableStateOf(false) }
    var umbruch by remember { mutableStateOf(true) }
    var zeilennummern by remember { mutableStateOf(true) }
    var verwerfenOffen by remember { mutableStateOf(false) }
    var speichert by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val senkrecht = rememberScrollState()
    val waagerecht = rememberScrollState()

    LaunchedEffect(datei) {
        val text = withContext(Dispatchers.IO) {
            runCatching { datei.readText() }.getOrElse { "" }
        }
        urspruenglich = text
        wert = TextFieldValue(text)
    }

    val aktuell = wert
    val geaendert = aktuell != null && aktuell.text != urspruenglich

    fun speichern() {
        val text = aktuell?.text ?: return
        speichert = true
        // Sicherungskopie vor dem Schreiben, damit nichts verloren geht.
        runCatching { datei.copyTo(File(datei.parentFile, datei.name + ".bak"), overwrite = true) }
        val ergebnis = runCatching { datei.writeText(text) }
        speichert = false
        if (ergebnis.isSuccess) urspruenglich = text
    }

    BackHandler(enabled = true) {
        if (geaendert) verwerfenOffen = true else onSchliessen()
    }

    if (verwerfenOffen) {
        AlertDialog(
            onDismissRequest = { verwerfenOffen = false },
            title = { Text("Änderungen verwerfen?") },
            text = { Text("Die Datei wurde geändert, aber noch nicht gespeichert.") },
            confirmButton = {
                TextButton(onClick = { verwerfenOffen = false; onSchliessen() }) {
                    Text("Verwerfen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    speichern(); verwerfenOffen = false; onSchliessen()
                }) { Text("Speichern") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(datei.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (geaendert) "geändert" else "gespeichert",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (geaendert) verwerfenOffen = true else onSchliessen()
                    }) { Icon(Icons.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(
                        enabled = verlauf.isNotEmpty(),
                        onClick = {
                            val letzter = verlauf.lastOrNull() ?: return@IconButton
                            verlauf = verlauf.dropLast(1)
                            wert = TextFieldValue(letzter, TextRange(letzter.length))
                        }
                    ) { Icon(Icons.Filled.Undo, "Rückgängig") }

                    IconButton(onClick = { suchenOffen = !suchenOffen }) {
                        Icon(
                            if (suchenOffen) Icons.Filled.Close else Icons.Filled.Search,
                            "Suchen und Ersetzen"
                        )
                    }
                    IconButton(onClick = { zeilennummern = !zeilennummern }) {
                        Icon(
                            Icons.Filled.Numbers,
                            "Zeilennummern",
                            tint = if (zeilennummern) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { umbruch = !umbruch }) {
                        Icon(
                            Icons.Filled.FormatAlignLeft,
                            "Zeilenumbruch",
                            tint = if (umbruch) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(enabled = geaendert && !speichert, onClick = { speichern() }) {
                        Icon(Icons.Filled.Save, "Speichern")
                    }
                }
            )
        }
    ) { innen ->
        if (aktuell == null) {
            Box(Modifier.padding(innen).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(innen).fillMaxSize()) {

            if (suchenOffen) {
                SuchLeiste(
                    text = aktuell.text,
                    onSpringe = { stelle, laenge ->
                        wert = aktuell.copy(selection = TextRange(stelle, stelle + laenge))
                    },
                    onErsetzeAlle = { suchen, ersetzen ->
                        if (suchen.isNotEmpty()) {
                            verlauf = (verlauf + aktuell.text).takeLast(50)
                            val neu = aktuell.text.replace(suchen, ersetzen)
                            wert = TextFieldValue(neu, TextRange(0))
                        }
                    }
                )
            }

            val zeilen = remember(aktuell.text) { aktuell.text.count { it == '\n' } + 1 }
            val schrift = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            val inhalt = @Composable {
                Row(modifier = Modifier.padding(8.dp)) {
                    if (zeilennummern) {
                        Column(modifier = Modifier.width(40.dp)) {
                            for (nummer in 1..zeilen) {
                                Text(
                                    text = nummer.toString(),
                                    style = schrift.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    BasicTextField(
                        value = aktuell,
                        onValueChange = { neu ->
                            if (neu.text != aktuell.text) {
                                verlauf = (verlauf + aktuell.text).takeLast(50)
                            }
                            wert = neu
                        },
                        textStyle = schrift,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.primary
                        ),
                        // Ohne Umbruch bekommt das Feld eine sehr grosse Breite,
                        // damit lange Zeilen stehen bleiben und seitlich
                        // gescrollt werden kann.
                        modifier = if (umbruch) Modifier.fillMaxWidth()
                        else Modifier.width(2400.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(senkrecht)
            ) {
                if (umbruch) {
                    inhalt()
                } else {
                    Box(modifier = Modifier.horizontalScroll(waagerecht)) { inhalt() }
                }
            }
        }
    }
}

@Composable
private fun SuchLeiste(
    text: String,
    onSpringe: (Int, Int) -> Unit,
    onErsetzeAlle: (String, String) -> Unit
) {
    var suchen by remember { mutableStateOf("") }
    var ersetzen by remember { mutableStateOf("") }
    var letzteStelle by remember { mutableStateOf(-1) }

    val treffer = remember(text, suchen) {
        if (suchen.isBlank()) 0 else text.windowedCount(suchen)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = suchen,
                onValueChange = { suchen = it; letzteStelle = -1 },
                singleLine = true,
                label = { Text("Suchen") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ersetzen,
                onValueChange = { ersetzen = it },
                singleLine = true,
                label = { Text("Ersetzen") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (suchen.isBlank()) "" else "$treffer Treffer",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = suchen.isNotBlank() && treffer > 0,
                onClick = {
                    val start = text.indexOf(suchen, letzteStelle + 1)
                        .let { if (it < 0) text.indexOf(suchen) else it }
                    if (start >= 0) {
                        letzteStelle = start
                        onSpringe(start, suchen.length)
                    }
                }
            ) { Text("Weiter") }
            TextButton(
                enabled = suchen.isNotBlank() && treffer > 0,
                onClick = { onErsetzeAlle(suchen, ersetzen) }
            ) { Text("Alle ersetzen") }
        }
    }
}

/** Zaehlt, wie oft [teil] im Text vorkommt. */
private fun String.windowedCount(teil: String): Int {
    if (teil.isEmpty()) return 0
    var anzahl = 0
    var stelle = indexOf(teil)
    while (stelle >= 0) {
        anzahl++
        stelle = indexOf(teil, stelle + teil.length)
    }
    return anzahl
}
