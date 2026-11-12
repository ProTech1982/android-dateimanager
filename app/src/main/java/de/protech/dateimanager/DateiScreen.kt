package de.protech.dateimanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val snackbar = remember { SnackbarHostState() }

    var umbenennen by remember { mutableStateOf<File?>(null) }
    var loeschenOffen by remember { mutableStateOf(false) }
    var neuerOrdnerOffen by remember { mutableStateOf(false) }

    val imAuswahlmodus = z.auswahl.isNotEmpty()

    LaunchedEffect(z.meldung) {
        z.meldung?.let {
            snackbar.showSnackbar(it)
            vm.meldungGelesen()
        }
    }

    BackHandler(enabled = true) {
        when {
            imAuswahlmodus -> vm.auswahlLeeren()
            !vm.nachOben() -> (context as? android.app.Activity)?.finish()
        }
    }

    // --- Dialoge ---

    umbenennen?.let { datei ->
        NameDialog(
            titel = "Umbenennen",
            vorgabe = datei.name,
            bestaetigung = "Umbenennen",
            onAbbruch = { umbenennen = null },
            onBestaetigt = { name -> vm.benenneUm(datei, name); umbenennen = null }
        )
    }

    if (neuerOrdnerOffen) {
        NameDialog(
            titel = "Neuer Ordner",
            vorgabe = "",
            bestaetigung = "Anlegen",
            onAbbruch = { neuerOrdnerOffen = false },
            onBestaetigt = { name -> vm.neuerOrdner(name); neuerOrdnerOffen = false }
        )
    }

    if (loeschenOffen) {
        val anzahl = z.auswahl.size
        AlertDialog(
            onDismissRequest = { loeschenOffen = false },
            title = { Text(if (anzahl == 1) "Löschen?" else "$anzahl Elemente löschen?") },
            text = {
                Text(
                    "Das lässt sich nicht rückgängig machen. Ordner werden mit " +
                        "ihrem gesamten Inhalt gelöscht."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.loescheAuswahl(); loeschenOffen = false }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { loeschenOffen = false }) { Text("Abbrechen") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (imAuswahlmodus) {
                AuswahlLeiste(
                    anzahl = z.auswahl.size,
                    onSchliessen = vm::auswahlLeeren,
                    onAlle = vm::allesAuswaehlen,
                    onUmbenennen = { umbenennen = File(z.auswahl.first()) },
                    onTeilen = { teile(context, vm.ausgewaehlteDateien()); vm.auswahlLeeren() },
                    onKopieren = vm::zumKopierenMerken,
                    onAusschneiden = vm::zumVerschiebenMerken,
                    onLoeschen = { loeschenOffen = true }
                )
            } else {
                HauptLeiste(vm, z, eintraege.size)
            }
        },
        floatingActionButton = {
            if (!imAuswahlmodus && !z.ueberallSuchen) {
                FloatingActionButton(onClick = { neuerOrdnerOffen = true }) {
                    Icon(Icons.Filled.CreateNewFolder, "Neuer Ordner")
                }
            }
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

            if (z.ablage.isNotEmpty() && !z.ueberallSuchen) {
                EinfuegeLeiste(
                    anzahl = z.ablage.size,
                    modus = z.ablageModus,
                    onEinfuegen = vm::fuegeEin,
                    onAbbrechen = vm::ablageLeeren
                )
            }

            HorizontalDivider()

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    z.laedt || z.arbeitet ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    eintraege.isEmpty() -> Text(
                        text = if (z.aktiveTypen.isEmpty())
                            "Es ist kein Dateityp ausgewählt.\nTippe oben auf „Alle anzeigen“."
                        else "Hier ist nichts zu sehen.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(eintraege, key = { it.datei.absolutePath }) { eintrag ->
                            EintragZeile(
                                eintrag = eintrag,
                                ausgewaehlt = eintrag.datei.absolutePath in z.auswahl,
                                imAuswahlmodus = imAuswahlmodus,
                                pfadZeigen = z.ueberallSuchen,
                                wurzel = vm.wurzel,
                                onKlick = {
                                    when {
                                        imAuswahlmodus -> vm.schalteAuswahl(eintrag)
                                        eintrag.istOrdner -> vm.oeffneOrdner(eintrag.datei)
                                        else -> oeffneDatei(context, eintrag.datei)
                                    }
                                },
                                onLangerKlick = { vm.schalteAuswahl(eintrag) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// --- Leisten -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HauptLeiste(vm: DateiViewModel, z: DateiZustand, anzahl: Int) {
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
                    Text("$anzahl Einträge", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        navigationIcon = {
            if (z.ordner.absolutePath != vm.wurzel.absolutePath || z.ueberallSuchen) {
                IconButton(onClick = { vm.nachOben() }) {
                    Icon(Icons.Filled.ArrowBack, "Zurück")
                }
            }
        },
        actions = {
            IconButton(onClick = vm::schalteSuchfeld) {
                Icon(if (z.sucheOffen) Icons.Filled.Close else Icons.Filled.Search, "Suchen")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuswahlLeiste(
    anzahl: Int,
    onSchliessen: () -> Unit,
    onAlle: () -> Unit,
    onUmbenennen: () -> Unit,
    onTeilen: () -> Unit,
    onKopieren: () -> Unit,
    onAusschneiden: () -> Unit,
    onLoeschen: () -> Unit
) {
    var mehr by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        title = { Text("$anzahl ausgewählt") },
        navigationIcon = {
            IconButton(onClick = onSchliessen) { Icon(Icons.Filled.Close, "Auswahl aufheben") }
        },
        actions = {
            IconButton(onClick = onKopieren) { Icon(Icons.Filled.ContentCopy, "Kopieren") }
            IconButton(onClick = onAusschneiden) { Icon(Icons.Filled.ContentCut, "Ausschneiden") }
            IconButton(onClick = onLoeschen) { Icon(Icons.Filled.Delete, "Löschen") }
            IconButton(onClick = { mehr = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }

            DropdownMenu(expanded = mehr, onDismissRequest = { mehr = false }) {
                if (anzahl == 1) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = { mehr = false; onUmbenennen() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Teilen") },
                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = { mehr = false; onTeilen() }
                )
                DropdownMenuItem(
                    text = { Text("Alle auswählen") },
                    leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                    onClick = { mehr = false; onAlle() }
                )
            }
        }
    )
}

@Composable
private fun EinfuegeLeiste(
    anzahl: Int,
    modus: AblageModus,
    onEinfuegen: () -> Unit,
    onAbbrechen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$anzahl zum " +
                (if (modus == AblageModus.KOPIEREN) "Kopieren" else "Verschieben") +
                " gemerkt",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        TextButton(onClick = onEinfuegen) { Text("Hier einfügen") }
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
private fun NameDialog(
    titel: String,
    vorgabe: String,
    bestaetigung: String,
    onAbbruch: () -> Unit,
    onBestaetigt: (String) -> Unit
) {
    var text by remember { mutableStateOf(vorgabe) }

    AlertDialog(
        onDismissRequest = onAbbruch,
        title = { Text(titel) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onBestaetigt(text) },
                enabled = text.isNotBlank()
            ) { Text(bestaetigung) }
        },
        dismissButton = {
            TextButton(onClick = onAbbruch) { Text("Abbrechen") }
        }
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
        Icon(Icons.Filled.Sort, "Sortieren")
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
            if (!letzter) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun EintragZeile(
    eintrag: DateiEintrag,
    ausgewaehlt: Boolean,
    imAuswahlmodus: Boolean,
    pfadZeigen: Boolean,
    wurzel: File,
    onKlick: () -> Unit,
    onLangerKlick: () -> Unit
) {
    val datumsFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    ListItem(
        colors = if (ausgewaehlt) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.combinedClickable(
            onClick = onKlick,
            onLongClick = onLangerKlick
        ),
        leadingContent = {
            if (imAuswahlmodus) {
                Checkbox(checked = ausgewaehlt, onCheckedChange = { onKlick() })
            } else if (eintrag.typ == DateiTyp.BILD || eintrag.typ == DateiTyp.VIDEO) {
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
                if (eintrag.istOrdner) append("${eintrag.anzahlKinder} Einträge")
                else append(groesseLesbar(eintrag.groesse))
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
    DateiTyp.DOKUMENT -> Icons.Filled.Description
    DateiTyp.ARCHIV -> Icons.Filled.Archive
    DateiTyp.APP -> Icons.Filled.Android
    DateiTyp.SONSTIGES -> Icons.Filled.InsertDriveFile
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

private fun uriFuer(context: Context, datei: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)

/** Oeffnet eine Datei in der passenden App des Geraets. */
private fun oeffneDatei(context: Context, datei: File) {
    val ergebnis = runCatching {
        val absicht = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFuer(context, datei), datei.mimeTyp())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(absicht, "Öffnen mit"))
    }
    if (ergebnis.isFailure) {
        Toast.makeText(context, "Keine App zum Öffnen gefunden.", Toast.LENGTH_SHORT).show()
    }
}

/** Teilt die ausgewaehlten Dateien ueber andere Apps. */
private fun teile(context: Context, dateien: List<File>) {
    val echteDateien = dateien.filter { it.isFile }
    if (echteDateien.isEmpty()) {
        Toast.makeText(context, "Ordner lassen sich nicht teilen.", Toast.LENGTH_SHORT).show()
        return
    }

    val ergebnis = runCatching {
        val uris = ArrayList(echteDateien.map { uriFuer(context, it) })
        val absicht = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = echteDateien.first().mimeTyp()
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        absicht.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(absicht, "Teilen"))
    }
    if (ergebnis.isFailure) {
        Toast.makeText(context, "Teilen fehlgeschlagen.", Toast.LENGTH_SHORT).show()
    }
}
