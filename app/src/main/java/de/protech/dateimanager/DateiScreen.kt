package de.protech.dateimanager

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Description
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateiScreen(vm: DateiViewModel) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val eintraege = remember(z) { vm.sichtbareEintraege(z) }
    val context = LocalContext.current

    BackHandler(enabled = true) {
        if (!vm.nachOben()) {
            (context as? android.app.Activity)?.finish()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (z.sucheOffen) {
                        Suchfeld(z.suchtext, vm::setzeSuchtext)
                    } else {
                        Column {
                            Text(
                                text = if (z.ueberallSuchen) "Alle Dateien"
                                else z.ordner.name.ifBlank { "Interner Speicher" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${eintraege.size} Einträge",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (z.ordner.absolutePath != vm.wurzel.absolutePath || z.ueberallSuchen) {
                        IconButton(onClick = { vm.nachOben() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::schalteSuchfeld) {
                        Icon(
                            if (z.sucheOffen) Icons.Filled.Close else Icons.Filled.Search,
                            "Suchen"
                        )
                    }
                    IconButton(onClick = { vm.setzeUeberallSuchen(!z.ueberallSuchen) }) {
                        Icon(
                            Icons.Filled.TravelExplore,
                            "Im ganzen Speicher suchen",
                            tint = if (z.ueberallSuchen) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Menue(vm, z)
                }
            )
        }
    ) { innen ->
        Column(modifier = Modifier.padding(innen).fillMaxSize()) {

            if (!z.ueberallSuchen) {
                Brotkrumen(wurzel = vm.wurzel, aktuell = z.ordner, onSprung = vm::oeffneOrdner)
            }

            TypFilter(
                aktive = z.aktiveTypen,
                ueberall = z.ueberallSuchen,
                onSchalten = vm::schalteTyp,
                onAlle = vm::alleTypen
            )

            HorizontalDivider()

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    z.laedt -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    eintraege.isEmpty() -> Text(
                        text = if (z.aktiveTypen.isEmpty())
                            "Es ist kein Dateityp ausgewählt.\nTippe oben auf „Alle“."
                        else "Hier ist nichts zu sehen.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(eintraege, key = { it.datei.absolutePath }) { eintrag ->
                            EintragZeile(
                                eintrag = eintrag,
                                pfadZeigen = z.ueberallSuchen,
                                wurzel = vm.wurzel,
                                onKlick = {
                                    if (eintrag.istOrdner) vm.oeffneOrdner(eintrag.datei)
                                    else oeffneDatei(context, eintrag.datei)
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// --- Bausteine ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Suchfeld(text: String, onAendern: (String) -> Unit) {
    TextField(
        value = text,
        onValueChange = onAendern,
        placeholder = { Text("Name suchen …") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Menue(vm: DateiViewModel, z: DateiZustand) {
    var offen by remember { mutableStateOf(false) }
    var sortOffen by remember { mutableStateOf(false) }
    var updateOffen by remember { mutableStateOf(false) }

    if (updateOffen) {
        UpdateDialog(onSchliessen = { updateOffen = false })
    }

    IconButton(onClick = { sortOffen = true }) {
        Icon(Icons.AutoMirrored.Filled.Sort, "Sortieren")
    }
    DropdownMenu(expanded = sortOffen, onDismissRequest = { sortOffen = false }) {
        Sortierung.entries.forEach { s ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = s.bezeichnung + if (z.sortierung == s) {
                            if (z.absteigend) "  ↓" else "  ↑"
                        } else "",
                        fontWeight = if (z.sortierung == s) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { vm.setzeSortierung(s); sortOffen = false }
            )
        }
    }

    IconButton(onClick = { offen = true }) {
        Icon(Icons.Filled.MoreVert, "Mehr")
    }
    DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
        DropdownMenuItem(
            text = { Text(if (z.versteckteZeigen) "Versteckte ausblenden" else "Versteckte anzeigen") },
            leadingIcon = {
                Icon(
                    if (z.versteckteZeigen) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    null
                )
            },
            onClick = { vm.schalteVersteckte(); offen = false }
        )
        DropdownMenuItem(
            text = { Text("Interner Speicher") },
            leadingIcon = { Icon(Icons.Filled.Home, null) },
            onClick = { vm.zurWurzel(); offen = false }
        )
        DropdownMenuItem(
            text = { Text("Nach Updates suchen") },
            leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) },
            onClick = { offen = false; updateOffen = true }
        )
        HorizontalDivider()
        schnellZugriffe().forEach { (bezeichnung, ordner) ->
            DropdownMenuItem(
                text = { Text(bezeichnung) },
                leadingIcon = { Icon(Icons.Filled.Folder, null) },
                onClick = { vm.oeffneOrdner(ordner); offen = false }
            )
        }
    }
}

@Composable
private fun Brotkrumen(wurzel: File, aktuell: File, onSprung: (File) -> Unit) {
    val teile = remember(aktuell) {
        val relativ = aktuell.absolutePath.removePrefix(wurzel.absolutePath).trim('/')
        val namen = if (relativ.isBlank()) emptyList() else relativ.split("/")
        buildList {
            add("Speicher" to wurzel)
            var lauf = wurzel
            namen.forEach { name ->
                lauf = File(lauf, name)
                add(name to lauf)
            }
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(teile) { (name, ordner) ->
            val letzter = ordner.absolutePath == aktuell.absolutePath
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (letzter) FontWeight.Bold else FontWeight.Normal,
                color = if (letzter) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onSprung(ordner) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (!letzter) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypFilter(
    aktive: Set<DateiTyp>,
    ueberall: Boolean,
    onSchalten: (DateiTyp) -> Unit,
    onAlle: (Boolean) -> Unit
) {
    val typen = remember(ueberall) {
        if (ueberall) DateiTyp.entries.filter { it != DateiTyp.ORDNER } else DateiTyp.entries
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            val alleAn = typen.all { it in aktive }
            FilterChip(
                selected = alleAn,
                onClick = { onAlle(!alleAn) },
                label = { Text(if (alleAn) "Alle" else "Alle anzeigen") }
            )
        }
        items(typen) { typ ->
            FilterChip(
                selected = typ in aktive,
                onClick = { onSchalten(typ) },
                label = { Text(typ.bezeichnung) },
                leadingIcon = { Icon(typ.symbol(), null, Modifier.size(18.dp)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EintragZeile(
    eintrag: DateiEintrag,
    pfadZeigen: Boolean,
    wurzel: File,
    onKlick: () -> Unit
) {
    val datumsFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    ListItem(
        modifier = Modifier.clickable(onClick = onKlick),
        leadingContent = {
            if (eintrag.typ == DateiTyp.BILD || eintrag.typ == DateiTyp.VIDEO) {
                AsyncImage(
                    model = eintrag.datei,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = eintrag.typ.symbol(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        headlineContent = {
            Text(eintrag.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val unterzeile = buildString {
                if (eintrag.istOrdner) {
                    append("${eintrag.anzahlKinder} Einträge")
                } else {
                    append(groesseLesbar(eintrag.groesse))
                }
                append("  ·  ")
                append(datumsFormat.format(Date(eintrag.geaendert)))
                if (pfadZeigen) {
                    val ordner = eintrag.datei.parentFile?.absolutePath
                        ?.removePrefix(wurzel.absolutePath)?.trim('/')
                    if (!ordner.isNullOrBlank()) append("\n$ordner")
                }
            }
            Text(
                text = unterzeile,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

// --- Hilfsfunktionen ------------------------------------------------------

private fun DateiTyp.symbol(): ImageVector = when (this) {
    DateiTyp.ORDNER -> Icons.Filled.Folder
    DateiTyp.BILD -> Icons.Filled.Image
    DateiTyp.VIDEO -> Icons.Filled.Videocam
    DateiTyp.AUDIO -> Icons.Filled.MusicNote
    DateiTyp.DOKUMENT -> Icons.AutoMirrored.Filled.Description
    DateiTyp.ARCHIV -> Icons.Filled.Archive
    DateiTyp.APP -> Icons.Filled.Android
    DateiTyp.SONSTIGES -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun schnellZugriffe(): List<Pair<String, File>> {
    fun ordner(typ: String) = Environment.getExternalStoragePublicDirectory(typ)
    return listOf(
        "Downloads" to ordner(Environment.DIRECTORY_DOWNLOADS),
        "Kamera (DCIM)" to ordner(Environment.DIRECTORY_DCIM),
        "Bilder" to ordner(Environment.DIRECTORY_PICTURES),
        "Videos" to ordner(Environment.DIRECTORY_MOVIES),
        "Musik" to ordner(Environment.DIRECTORY_MUSIC),
        "Dokumente" to ordner(Environment.DIRECTORY_DOCUMENTS)
    ).filter { it.second.exists() }
}

/** Oeffnet eine Datei in der passenden App des Geraets. */
private fun oeffneDatei(context: Context, datei: File) {
    val ergebnis = runCatching {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", datei
        )
        val absicht = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, datei.mimeTyp())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(absicht, "Öffnen mit"))
    }
    if (ergebnis.isFailure) {
        Toast.makeText(context, "Keine App zum Öffnen gefunden.", Toast.LENGTH_SHORT).show()
    }
}
