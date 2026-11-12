package de.protech.dateimanager

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Welche Betrachter die App selbst mitbringt. */
fun File.internBetrachtbar(): Boolean = when (dateiTyp()) {
    DateiTyp.BILD, DateiTyp.VIDEO, DateiTyp.AUDIO -> true
    DateiTyp.DOKUMENT -> extension.lowercase() in setOf(
        "pdf", "txt", "md", "csv", "json", "xml", "log", "html", "htm",
        "ini", "cfg", "yml", "yaml", "rtf"
    )
    else -> false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetrachterScreen(
    datei: File,
    nachbarn: List<File>,
    onSchliessen: () -> Unit,
    onExtern: (File) -> Unit
) {
    var titel by remember { mutableStateOf(datei.name) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onSchliessen) {
                        Icon(Icons.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { onExtern(datei) }) {
                        Icon(Icons.Filled.OpenInNew, "Mit anderer App öffnen")
                    }
                }
            )
        }
    ) { innen ->
        Box(modifier = Modifier.padding(innen).fillMaxSize()) {
            when {
                datei.dateiTyp() == DateiTyp.BILD -> BildBetrachter(
                    start = datei,
                    bilder = nachbarn.filter { it.dateiTyp() == DateiTyp.BILD }
                        .ifEmpty { listOf(datei) },
                    onTitel = { titel = it }
                )

                datei.extension.equals("pdf", true) -> PdfBetrachter(datei)

                datei.dateiTyp() == DateiTyp.VIDEO || datei.dateiTyp() == DateiTyp.AUDIO ->
                    MedienBetrachter(datei)

                datei.extension.equals("csv", true) -> CsvBetrachter(datei)

                else -> TextBetrachter(datei)
            }
        }
    }
}

// --- Bilder ---------------------------------------------------------------

@Composable
private fun BildBetrachter(start: File, bilder: List<File>, onTitel: (String) -> Unit) {
    val startIndex = bilder.indexOfFirst { it.absolutePath == start.absolutePath }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startIndex) { bilder.size }

    LaunchedTitel(pager.currentPage, bilder, onTitel)

    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { seite ->
        var massstab by remember(seite) { mutableFloatStateOf(1f) }
        var xVerschiebung by remember(seite) { mutableFloatStateOf(0f) }
        var yVerschiebung by remember(seite) { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(seite) {
                    detectTransformGestures { _, verschiebung, zoom, _ ->
                        massstab = (massstab * zoom).coerceIn(1f, 6f)
                        if (massstab > 1f) {
                            xVerschiebung += verschiebung.x
                            yVerschiebung += verschiebung.y
                        } else {
                            xVerschiebung = 0f
                            yVerschiebung = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = bilder[seite],
                contentDescription = bilder[seite].name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = massstab,
                        scaleY = massstab,
                        translationX = xVerschiebung,
                        translationY = yVerschiebung
                    )
            )
        }
    }
}

@Composable
private fun LaunchedTitel(seite: Int, bilder: List<File>, onTitel: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(seite) {
        bilder.getOrNull(seite)?.let { onTitel("${it.name}  (${seite + 1}/${bilder.size})") }
    }
}

// --- PDF ------------------------------------------------------------------

@Composable
private fun PdfBetrachter(datei: File) {
    val oeffner = remember(datei) {
        runCatching {
            val beschreibung = ParcelFileDescriptor.open(
                datei, ParcelFileDescriptor.MODE_READ_ONLY
            )
            beschreibung to PdfRenderer(beschreibung)
        }.getOrNull()
    }

    DisposableEffect(oeffner) {
        onDispose {
            runCatching { oeffner?.second?.close() }
            runCatching { oeffner?.first?.close() }
        }
    }

    val renderer = oeffner?.second
    if (renderer == null) {
        Hinweis("Diese PDF lässt sich nicht öffnen. Vielleicht ist sie beschädigt oder mit einem Passwort geschützt.")
        return
    }

    var massstab by remember { mutableFloatStateOf(1f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2B2B))
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    massstab = (massstab * zoom).coerceIn(1f, 4f)
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
    ) {
        items((0 until renderer.pageCount).toList()) { nummer ->
            val bild by produceState<Bitmap?>(initialValue = null, nummer) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        synchronized(renderer) {
                            val seite = renderer.openPage(nummer)
                            val breite = 1400
                            val hoehe = (breite.toFloat() / seite.width * seite.height).toInt()
                            val bitmap = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            seite.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            seite.close()
                            bitmap
                        }
                    }.getOrNull()
                }
            }

            val aktuell = bild
            if (aktuell == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                androidx.compose.foundation.Image(
                    bitmap = aktuell.asImageBitmap(),
                    contentDescription = "Seite ${nummer + 1}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = massstab, scaleY = massstab)
                )
            }
        }
    }
}

// --- Text und CSV ---------------------------------------------------------

@Composable
private fun TextBetrachter(datei: File) {
    val inhalt by produceState(initialValue = null as String?, datei) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (datei.length() > 2L * 1024 * 1024) {
                    datei.bufferedReader().use { leser ->
                        val puffer = CharArray(2 * 1024 * 1024)
                        val gelesen = leser.read(puffer)
                        String(puffer, 0, gelesen.coerceAtLeast(0)) +
                            "\n\n… die Datei ist länger und wurde hier gekürzt."
                    }
                } else {
                    datei.readText()
                }
            }.getOrElse { "Diese Datei lässt sich nicht als Text lesen." }
        }
    }

    val text = inhalt
    if (text == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    SelectionContainer {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        )
    }
}

@Composable
private fun CsvBetrachter(datei: File) {
    val zeilen by produceState(initialValue = null as List<List<String>>?, datei) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                datei.bufferedReader().useLines { folge ->
                    folge.take(2000).map { zeile ->
                        val trenner = if (zeile.count { it == ';' } > zeile.count { it == ',' }) ';' else ','
                        zeile.split(trenner).map { it.trim().trim('"') }
                    }.toList()
                }
            }.getOrDefault(emptyList())
        }
    }

    val daten = zeilen
    if (daten == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (daten.isEmpty()) {
        Hinweis("Diese Tabelle ist leer oder lässt sich nicht lesen.")
        return
    }

    val spalten = daten.maxOf { it.size }

    LazyColumn(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        items(daten.withIndex().toList()) { (index, zeile) ->
            Row(
                modifier = Modifier
                    .background(
                        if (index == 0) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent
                    )
                    .padding(vertical = 6.dp)
            ) {
                for (spalte in 0 until spalten) {
                    Text(
                        text = zeile.getOrElse(spalte) { "" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(140.dp).padding(horizontal = 10.dp)
                    )
                }
            }
        }
    }
}

// --- Video und Audio ------------------------------------------------------

@Composable
private fun MedienBetrachter(datei: File) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(datei.absolutePath)
                    val steuerung = MediaController(ctx)
                    steuerung.setAnchorView(this)
                    setMediaController(steuerung)
                    setOnPreparedListener { start() }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (datei.dateiTyp() == DateiTyp.AUDIO) {
            Text(
                text = datei.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun Hinweis(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
