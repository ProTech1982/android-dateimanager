package de.protech.dateimanager

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Seite { DATEIEN, PAPIERKORB, WERKZEUGE, EINSTELLUNGEN }

@Composable
fun DateiScreen(vm: DateiViewModel, einstellungen: Einstellungen) {
    var seite by remember { mutableStateOf(Seite.DATEIEN) }
    var betrachtet by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    val z by vm.zustand.collectAsStateWithLifecycle()

    // Die Zurueck-Taste schliesst erst das Bild bzw. die Unterseite und
    // verlaesst die App nicht sofort.
    BackHandler(enabled = betrachtet != null || seite != Seite.DATEIEN) {
        if (betrachtet != null) betrachtet = null else seite = Seite.DATEIEN
    }

    val betrachtetDatei = betrachtet
    when {
        betrachtetDatei != null -> BetrachterScreen(
            datei = betrachtetDatei,
            nachbarn = vm.sichtbareEintraege(z).map { it.datei },
            onSchliessen = { betrachtet = null },
            onExtern = { oeffneDatei(context, it) }
        )

        seite == Seite.PAPIERKORB -> PapierkorbScreen(vm) { seite = Seite.DATEIEN }

        seite == Seite.WERKZEUGE -> WerkzeugeScreen(
            vm = vm,
            onZurueck = { seite = Seite.DATEIEN },
            onOrdner = { vm.oeffneOrdner(it); seite = Seite.DATEIEN },
            onDatei = { datei ->
                if (datei.internBetrachtbar()) betrachtet = datei
                else oeffneDatei(context, datei)
            }
        )

        seite == Seite.EINSTELLUNGEN -> EinstellungenScreen(einstellungen) { seite = Seite.DATEIEN }

        else -> DateiListeScreen(
            vm = vm,
            einstellungen = einstellungen,
            onSeite = { seite = it },
            onBetrachten = { betrachtet = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateiListeScreen(
    vm: DateiViewModel,
    einstellungen: Einstellungen,
    onSeite: (Seite) -> Unit,
    onBetrachten: (File) -> Unit
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val e by einstellungen.zustand.collectAsStateWithLifecycle()
    val eintraege = remember(z) { vm.sichtbareEintraege(z) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val konfiguration = LocalConfiguration.current

    var umbenennen by remember { mutableStateOf<File?>(null) }
    var info by remember { mutableStateOf<File?>(null) }
    var loeschenOffen by remember { mutableStateOf(false) }
    var neuerOrdnerOffen by remember { mutableStateOf(false) }
    var musterOffen by remember { mutableStateOf(false) }
    var stapelOffen by remember { mutableStateOf(false) }
    var packenOffen by remember { mutableStateOf(false) }

    val imAuswahlmodus = z.auswahl.isNotEmpty()
    val querformat = konfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val zweiSpalten = querformat && e.zweiSpalten && !z.ueberallSuchen

    LaunchedEffect(z.meldung) {
        val meldung = z.meldung ?: return@LaunchedEffect
        val rueck = z.rueckgaengig
        val ergebnis = snackbar.showSnackbar(
            message = meldung,
            actionLabel = if (rueck != null) "Rückgängig" else null,
            withDismissAction = true
        )
        if (ergebnis == SnackbarResult.ActionPerformed) vm.macheRueckgaengig()
        vm.meldungGelesen()
    }

    BackHandler(enabled = true) {
        when {
            imAuswahlmodus -> vm.auswahlLeeren()
            !vm.nachOben() -> (context as? android.app.Activity)?.finish()
        }
    }

    // --- Dialoge ---

    umbenennen?.let { datei ->
        NameDialog("Umbenennen", datei.name, "Umbenennen", { umbenennen = null }) { name ->
            vm.benenneUm(datei, name); umbenennen = null
        }
    }

    if (neuerOrdnerOffen) {
        NameDialog("Neuer Ordner", "", "Anlegen", { neuerOrdnerOffen = false }) { name ->
            vm.neuerOrdner(name); neuerOrdnerOffen = false
        }
    }

    if (packenOffen) {
        NameDialog("Als ZIP packen", "Archiv", "Packen", { packenOffen = false }) { name ->
            vm.packeAuswahl(name); packenOffen = false
        }
    }

    if (stapelOffen) {
        StapelDialog(
            dateien = vm.ausgewaehlteDateien().filter { it.isFile },
            onAbbruch = { stapelOffen = false },
            onBestaetigt = { vorlage, start -> vm.stapelUmbenennen(vorlage, start); stapelOffen = false }
        )
    }

    if (musterOffen) {
        MusterDialog(
            treffer = z.musterTreffer,
            groesse = z.musterGroesse,
            onPruefen = vm::pruefeMuster,
            onAbbruch = { musterOffen = false },
            onAuswaehlen = { filter -> vm.waehleNachMuster(filter); musterOffen = false }
        )
    }

    info?.let { datei ->
        InfoDialog(
            datei = datei,
            onExtern = { oeffneDatei(context, datei); info = null },
            onSchliessen = { info = null }
        )
    }

    if (loeschenOffen) {
        val anzahl = z.auswahl.size
        AlertDialog(
            onDismissRequest = { loeschenOffen = false },
            title = { Text(if (anzahl == 1) "In den Papierkorb?" else "$anzahl in den Papierkorb?") },
            text = {
                Text(
                    "Die Einträge wandern in den Papierkorb und lassen sich dort " +
                        "wiederherstellen. Endgültig weg sind sie erst, wenn du den " +
                        "Papierkorb leerst."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.inDenPapierkorb(); loeschenOffen = false }) {
                    Text("In den Papierkorb")
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
                    onStapel = { stapelOffen = true },
                    onInfo = { info = File(z.auswahl.first()) },
                    onPacken = { packenOffen = true },
                    onEntpacken = { vm.entpacke(File(z.auswahl.first())) },
                    nurEinArchiv = z.auswahl.size == 1 &&
                        File(z.auswahl.first()).extension.equals("zip", true),
                    onTeilen = { teile(context, vm.ausgewaehlteDateien()); vm.auswahlLeeren() },
                    onKopieren = vm::zumKopierenMerken,
                    onAusschneiden = vm::zumVerschiebenMerken,
                    onLoeschen = { loeschenOffen = true }
                )
            } else {
                HauptLeiste(
                    vm = vm,
                    z = z,
                    einstellungen = einstellungen,
                    anzahl = eintraege.size,
                    onSeite = onSeite,
                    onMuster = { musterOffen = true }
                )
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
                Brotkrumen(vm.wurzel, z.ordner, vm::oeffneOrdner)
            }

            TypFilter(z.aktiveTypen, z.ueberallSuchen, vm::schalteTyp, vm::alleTypen)

            if (z.ablage.isNotEmpty() && !z.ueberallSuchen) {
                EinfuegeLeiste(z.ablage.size, z.ablageModus, vm::fuegeEin, vm::ablageLeeren)
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

                    zweiSpalten -> Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.width(260.dp).fillMaxHeight()) {
                            BaumSpalte(vm.wurzel, z.ordner, vm::oeffneOrdner)
                        }
                        VerticalDivider()
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Inhalt(vm, z, e, eintraege, imAuswahlmodus, context, onBetrachten) {
                                info = it
                            }
                        }
                    }

                    else -> Inhalt(vm, z, e, eintraege, imAuswahlmodus, context, onBetrachten) {
                        info = it
                    }
                }
            }
        }
    }
}

// --- Darstellung der Eintraege -------------------------------------------

@Composable
private fun Inhalt(
    vm: DateiViewModel,
    z: DateiZustand,
    e: EinstellungsZustand,
    eintraege: List<DateiEintrag>,
    imAuswahlmodus: Boolean,
    context: Context,
    onBetrachten: (File) -> Unit,
    onInfo: (File) -> Unit
) {
    val oeffne: (DateiEintrag) -> Unit = { eintrag ->
        when {
            imAuswahlmodus -> vm.schalteAuswahl(eintrag)
            eintrag.istOrdner -> vm.oeffneOrdner(eintrag.datei)
            eintrag.datei.internBetrachtbar() -> onBetrachten(eintrag.datei)
            // Unbekanntes Format: lieber die Infos zeigen als den Nutzer in
            // einem leeren Android-Dialog stranden zu lassen.
            else -> onInfo(eintrag.datei)
        }
    }

    when (if (z.ueberallSuchen) Ansicht.LISTE else e.ansicht) {
        Ansicht.RASTER -> LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
        ) {
            items(eintraege, key = { it.datei.absolutePath }) { eintrag ->
                RasterKachel(
                    eintrag = eintrag,
                    ausgewaehlt = eintrag.datei.absolutePath in z.auswahl,
                    onKlick = { oeffne(eintrag) },
                    onLangerKlick = { vm.schalteAuswahl(eintrag) }
                )
            }
        }

        Ansicht.BAUM -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(eintraege, key = { it.datei.absolutePath }) { eintrag ->
                BaumZeile(
                    eintrag = eintrag,
                    ausgewaehlt = eintrag.datei.absolutePath in z.auswahl,
                    onKlick = { oeffne(eintrag) },
                    onLangerKlick = { vm.schalteAuswahl(eintrag) }
                )
            }
        }

        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(eintraege, key = { it.datei.absolutePath }) { eintrag ->
                EintragZeile(
                    eintrag = eintrag,
                    ausgewaehlt = eintrag.datei.absolutePath in z.auswahl,
                    imAuswahlmodus = imAuswahlmodus,
                    pfadZeigen = z.ueberallSuchen,
                    wurzel = vm.wurzel,
                    onKlick = { oeffne(eintrag) },
                    onLangerKlick = { vm.schalteAuswahl(eintrag) },
                    onInfo = { onInfo(eintrag.datei) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

/** Die linke Spalte im Querformat: die Ordner der aktuellen Ebene. */
@Composable
private fun BaumSpalte(wurzel: File, aktuell: File, onOrdner: (File) -> Unit) {
    val kette = remember(aktuell) {
        val relativ = aktuell.absolutePath.removePrefix(wurzel.absolutePath).trim('/')
        val namen = if (relativ.isBlank()) emptyList() else relativ.split("/")
        buildList {
            add(0 to wurzel)
            var lauf = wurzel
            namen.forEachIndexed { tiefe, name ->
                lauf = File(lauf, name)
                add((tiefe + 1) to lauf)
            }
        }
    }
    val geschwister = remember(aktuell) {
        runCatching { aktuell.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } }
            .getOrNull().orEmpty().sortedBy { it.name.lowercase() }
    }
    val tiefe = kette.size

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(kette) { (ebene, ordner) ->
            OrdnerZeile(
                name = if (ebene == 0) "Interner Speicher" else ordner.name,
                ebene = ebene,
                hervorgehoben = ordner.absolutePath == aktuell.absolutePath,
                onKlick = { onOrdner(ordner) }
            )
        }
        items(geschwister) { ordner ->
            OrdnerZeile(
                name = ordner.name,
                ebene = tiefe,
                hervorgehoben = false,
                onKlick = { onOrdner(ordner) }
            )
        }
    }
}

@Composable
private fun OrdnerZeile(name: String, ebene: Int, hervorgehoben: Boolean, onKlick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onKlick)
            .background(
                if (hervorgehoben) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .padding(start = (8 + ebene * 14).dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Folder, null, Modifier.size(18.dp))
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (hervorgehoben) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BaumZeile(
    eintrag: DateiEintrag,
    ausgewaehlt: Boolean,
    onKlick: () -> Unit,
    onLangerKlick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onKlick, onLongClick = onLangerKlick)
            .background(
                if (ausgewaehlt) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (eintrag.istOrdner) "▸" else "·",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp)
        )
        Icon(eintrag.typ.symbol(), null, Modifier.size(18.dp))
        Text(
            text = eintrag.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f)
        )
        Text(
            text = if (eintrag.istOrdner) "${eintrag.anzahlKinder}" else groesseLesbar(eintrag.groesse),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RasterKachel(
    eintrag: DateiEintrag,
    ausgewaehlt: Boolean,
    onKlick: () -> Unit,
    onLangerKlick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (ausgewaehlt) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .combinedClickable(onClick = onKlick, onLongClick = onLangerKlick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (eintrag.typ == DateiTyp.BILD || eintrag.typ == DateiTyp.VIDEO) {
                AsyncImage(
                    model = eintrag.datei,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(eintrag.typ.symbol(), null, Modifier.size(34.dp))
            }
        }
        Text(
            text = eintrag.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
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
    onLangerKlick: () -> Unit,
    onInfo: () -> Unit
) {
    val datumsFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    ListItem(
        colors = if (ausgewaehlt) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else ListItemDefaults.colors(),
        modifier = Modifier.combinedClickable(onClick = onKlick, onLongClick = onLangerKlick),
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
                            eintrag.typ.symbol(),
                            null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        headlineContent = { Text(eintrag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                unterzeile,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            if (!imAuswahlmodus) {
                IconButton(onClick = onInfo) {
                    Icon(
                        Icons.Filled.Info,
                        "Infos",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

// --- Leisten -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HauptLeiste(
    vm: DateiViewModel,
    z: DateiZustand,
    einstellungen: Einstellungen,
    anzahl: Int,
    onSeite: (Seite) -> Unit,
    onMuster: () -> Unit
) {
    TopAppBar(
        title = {
            if (z.sucheOffen) {
                Suchfeld(z.suchtext, vm::setzeSuchtext)
            } else {
                Column {
                    Text(
                        if (z.ueberallSuchen) "Alle Dateien"
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
            Menue(vm, z, einstellungen, onSeite, onMuster)
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
    onStapel: () -> Unit,
    onInfo: () -> Unit,
    onPacken: () -> Unit,
    onEntpacken: () -> Unit,
    nurEinArchiv: Boolean,
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
            IconButton(onClick = onLoeschen) { Icon(Icons.Filled.Delete, "In den Papierkorb") }
            IconButton(onClick = { mehr = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }

            DropdownMenu(expanded = mehr, onDismissRequest = { mehr = false }) {
                if (anzahl == 1) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = { mehr = false; onUmbenennen() }
                    )
                    DropdownMenuItem(
                        text = { Text("Infos") },
                        leadingIcon = { Icon(Icons.Filled.Info, null) },
                        onClick = { mehr = false; onInfo() }
                    )
                }
                if (anzahl > 1) {
                    DropdownMenuItem(
                        text = { Text("Stapel-Umbenennen") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = { mehr = false; onStapel() }
                    )
                }
                if (nurEinArchiv) {
                    DropdownMenuItem(
                        text = { Text("Entpacken") },
                        leadingIcon = { Icon(Icons.Filled.FolderZip, null) },
                        onClick = { mehr = false; onEntpacken() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Als ZIP packen") },
                    leadingIcon = { Icon(Icons.Filled.FolderZip, null) },
                    onClick = { mehr = false; onPacken() }
                )
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
            "$anzahl zum " + (if (modus == AblageModus.KOPIEREN) "Kopieren" else "Verschieben") + " gemerkt",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        TextButton(onClick = onEinfuegen) { Text("Hier einfügen") }
    }
}

@Composable
private fun Menue(
    vm: DateiViewModel,
    z: DateiZustand,
    einstellungen: Einstellungen,
    onSeite: (Seite) -> Unit,
    onMuster: () -> Unit
) {
    var offen by remember { mutableStateOf(false) }
    var sortOffen by remember { mutableStateOf(false) }
    var updateOffen by remember { mutableStateOf(false) }
    val e by einstellungen.zustand.collectAsStateWithLifecycle()

    if (updateOffen) UpdateDialog(onSchliessen = { updateOffen = false })

    IconButton(onClick = { sortOffen = true }) { Icon(Icons.Filled.Sort, "Sortieren") }
    DropdownMenu(expanded = sortOffen, onDismissRequest = { sortOffen = false }) {
        Sortierung.entries.forEach { s ->
            DropdownMenuItem(
                text = {
                    Text(
                        s.bezeichnung + if (z.sortierung == s) {
                            if (z.absteigend) "  ↓" else "  ↑"
                        } else "",
                        fontWeight = if (z.sortierung == s) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { vm.setzeSortierung(s); sortOffen = false }
            )
        }
        HorizontalDivider()
        Ansicht.entries.forEach { a ->
            DropdownMenuItem(
                text = {
                    Text(
                        "Ansicht: ${a.bezeichnung}",
                        fontWeight = if (e.ansicht == a) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { einstellungen.setzeAnsicht(a); sortOffen = false }
            )
        }
    }

    IconButton(onClick = { offen = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
    DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
        val rueck = z.rueckgaengig
        if (rueck != null) {
            DropdownMenuItem(
                text = { Text(rueck.beschreibung) },
                leadingIcon = { Icon(Icons.Filled.Undo, null) },
                onClick = { vm.macheRueckgaengig(); offen = false }
            )
            HorizontalDivider()
        }
        DropdownMenuItem(
            text = { Text("Auswählen nach Muster") },
            leadingIcon = { Icon(Icons.Filled.FilterAlt, null) },
            onClick = { offen = false; onMuster() }
        )
        DropdownMenuItem(
            text = { Text("Papierkorb") },
            leadingIcon = { Icon(Icons.Filled.Delete, null) },
            onClick = { offen = false; onSeite(Seite.PAPIERKORB) }
        )
        DropdownMenuItem(
            text = { Text("Aufräumen") },
            leadingIcon = { Icon(Icons.Filled.CleaningServices, null) },
            onClick = { offen = false; onSeite(Seite.WERKZEUGE) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(if (einstellungen.istLesezeichen(z.ordner.absolutePath))
                    "Lesezeichen entfernen" else "Als Lesezeichen merken")
            },
            leadingIcon = {
                Icon(
                    if (einstellungen.istLesezeichen(z.ordner.absolutePath))
                        Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    null
                )
            },
            onClick = { einstellungen.schalteLesezeichen(z.ordner.absolutePath); offen = false }
        )
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
            text = { Text("Einstellungen") },
            leadingIcon = { Icon(Icons.Filled.Settings, null) },
            onClick = { offen = false; onSeite(Seite.EINSTELLUNGEN) }
        )
        DropdownMenuItem(
            text = { Text("Nach Updates suchen") },
            leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) },
            onClick = { offen = false; updateOffen = true }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Interner Speicher") },
            leadingIcon = { Icon(Icons.Filled.Home, null) },
            onClick = { vm.zurWurzel(); offen = false }
        )
        e.lesezeichen.forEach { pfad ->
            DropdownMenuItem(
                text = { Text(File(pfad).name) },
                leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                onClick = { vm.oeffneOrdner(File(pfad)); offen = false }
            )
        }
        schnellZugriffe().forEach { (bezeichnung, ordner) ->
            DropdownMenuItem(
                text = { Text(bezeichnung) },
                leadingIcon = { Icon(Icons.Filled.Folder, null) },
                onClick = { vm.oeffneOrdner(ordner); offen = false }
            )
        }
    }
}

// --- Dialoge und kleine Bausteine ----------------------------------------

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
            TextButton(onClick = { onBestaetigt(text) }, enabled = text.isNotBlank()) {
                Text(bestaetigung)
            }
        },
        dismissButton = { TextButton(onClick = onAbbruch) { Text("Abbrechen") } }
    )
}

@Composable
private fun StapelDialog(
    dateien: List<File>,
    onAbbruch: () -> Unit,
    onBestaetigt: (String, Int) -> Unit
) {
    var vorlage by remember { mutableStateOf("{name}_{n}") }
    var start by remember { mutableStateOf("1") }
    val startZahl = start.toIntOrNull() ?: 1
    val vorschau = remember(vorlage, startZahl, dateien) {
        DateiAktionen.vorschauNamen(dateien, vorlage, startZahl)
    }

    AlertDialog(
        onDismissRequest = onAbbruch,
        title = { Text("${dateien.size} Dateien umbenennen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vorlage,
                    onValueChange = { vorlage = it },
                    singleLine = true,
                    label = { Text("Vorlage") }
                )
                Text(
                    "{n} ist die laufende Nummer, {name} der bisherige Name. " +
                        "Die Endung bleibt erhalten.",
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.filter { zeichen -> zeichen.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Erste Nummer") }
                )
                Text("Vorschau:", style = MaterialTheme.typography.labelMedium)
                vorschau.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onBestaetigt(vorlage, startZahl) },
                enabled = vorlage.isNotBlank() && dateien.isNotEmpty()
            ) { Text("Umbenennen") }
        },
        dismissButton = { TextButton(onClick = onAbbruch) { Text("Abbrechen") } }
    )
}

@Composable
private fun MusterDialog(
    treffer: Int,
    groesse: Long,
    onPruefen: (MusterFilter) -> Unit,
    onAbbruch: () -> Unit,
    onAuswaehlen: (MusterFilter) -> Unit
) {
    var endung by remember { mutableStateOf("") }
    var anfang by remember { mutableStateOf("") }
    var tage by remember { mutableStateOf("") }
    var mb by remember { mutableStateOf("") }
    var rekursiv by remember { mutableStateOf(false) }

    val filter = MusterFilter(
        endung = endung,
        namensanfang = anfang,
        aelterAlsTage = tage.toIntOrNull() ?: 0,
        groesserAlsMb = mb.toIntOrNull() ?: 0,
        rekursiv = rekursiv
    )

    LaunchedEffect(filter) { if (!filter.istLeer) onPruefen(filter) }

    AlertDialog(
        onDismissRequest = onAbbruch,
        title = { Text("Auswählen nach Muster") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = endung,
                    onValueChange = { endung = it },
                    singleLine = true,
                    label = { Text("Endung, z. B. log") }
                )
                OutlinedTextField(
                    value = anfang,
                    onValueChange = { anfang = it },
                    singleLine = true,
                    label = { Text("Name beginnt mit") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tage,
                        onValueChange = { tage = it.filter { z -> z.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Älter als (Tage)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mb,
                        onValueChange = { mb = it.filter { z -> z.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Größer als (MB)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auch Unterordner", modifier = Modifier.weight(1f))
                    Switch(checked = rekursiv, onCheckedChange = { rekursiv = it })
                }
                HorizontalDivider()
                Text(
                    if (filter.istLeer) "Trage mindestens eine Bedingung ein."
                    else "$treffer Dateien · ${groesseLesbar(groesse)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Es wird nur ausgewählt, nichts gelöscht. Du siehst die Treffer " +
                        "danach in der Liste.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAuswaehlen(filter) }, enabled = !filter.istLeer && treffer > 0) {
                Text("$treffer auswählen")
            }
        },
        dismissButton = { TextButton(onClick = onAbbruch) { Text("Abbrechen") } }
    )
}

@Composable
private fun InfoDialog(datei: File, onExtern: () -> Unit, onSchliessen: () -> Unit) {
    val format = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    var summe by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text(datei.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoZeile("Art", if (datei.isDirectory) "Ordner" else datei.dateiTyp().bezeichnung)
                InfoZeile(
                    "Größe",
                    if (datei.isDirectory) "${datei.list()?.size ?: 0} Einträge"
                    else groesseLesbar(datei.length())
                )
                InfoZeile("Geändert", format.format(Date(datei.lastModified())))
                InfoZeile("Pfad", datei.absolutePath)
                if (datei.isFile) {
                    InfoZeile("Lesbar", if (datei.canRead()) "ja" else "nein")
                    InfoZeile("Schreibbar", if (datei.canWrite()) "ja" else "nein")
                    val aktuell = summe
                    if (aktuell == null) {
                        TextButton(onClick = { summe = DateiAktionen.pruefsumme(datei) ?: "—" }) {
                            Text("Prüfsumme berechnen")
                        }
                    } else {
                        InfoZeile("MD5", aktuell)
                    }
                }
            }
        },
        confirmButton = {
            if (datei.isFile) {
                TextButton(onClick = onExtern) { Text("Mit anderer App öffnen") }
            } else {
                TextButton(onClick = onSchliessen) { Text("Schließen") }
            }
        },
        dismissButton = {
            if (datei.isFile) TextButton(onClick = onSchliessen) { Text("Schließen") }
        }
    )
}

@Composable
private fun InfoZeile(bezeichnung: String, wert: String) {
    Column {
        Text(bezeichnung, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.bodySmall)
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
                modifier = Modifier.clickable { onSprung(ordner) }
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
