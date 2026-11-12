package de.protech.dateimanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Fragt beim Oeffnen den neuesten Release bei GitHub ab und zeigt das
 * Ergebnis. Heruntergeladen und installiert wird im Browser.
 */
@Composable
fun UpdateDialog(onSchliessen: () -> Unit) {
    val context = LocalContext.current
    var ergebnis by remember { mutableStateOf<Updater.Ergebnis?>(null) }

    LaunchedEffect(Unit) {
        Updater.pruefe(context) { ergebnis = it }
    }

    val installiert = remember {
        "Version ${Updater.installierterName(context)} " +
            "(Build ${Updater.installierterBuild(context)})"
    }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Aktualisierung") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(installiert, style = MaterialTheme.typography.bodySmall)

                when (val e = ergebnis) {
                    null -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text("Suche nach einer neueren Version …")
                    }

                    is Updater.Ergebnis.Aktuell ->
                        Text("Du hast bereits die neueste Version.")

                    is Updater.Ergebnis.Verfuegbar -> {
                        Text(
                            text = e.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (e.groesse > 0) {
                            Text(
                                text = "Download: ${groesseLesbar(e.groesse)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (e.notizen.isNotBlank()) {
                            Text(
                                text = e.notizen,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    is Updater.Ergebnis.BrauchtBrowser -> Text(e.grund)

                    is Updater.Ergebnis.Fehlgeschlagen -> Text(e.grund)
                }
            }
        },
        confirmButton = {
            when (val e = ergebnis) {
                is Updater.Ergebnis.Verfuegbar -> TextButton(onClick = {
                    Updater.oeffneReleaseSeite(context, e.apkUrl)
                    onSchliessen()
                }) { Text("Herunterladen") }

                is Updater.Ergebnis.BrauchtBrowser,
                is Updater.Ergebnis.Fehlgeschlagen -> TextButton(onClick = {
                    Updater.oeffneReleaseSeite(context)
                    onSchliessen()
                }) { Text("Im Browser öffnen") }

                else -> TextButton(onClick = onSchliessen) { Text("Schließen") }
            }
        },
        dismissButton = {
            if (ergebnis is Updater.Ergebnis.Verfuegbar ||
                ergebnis is Updater.Ergebnis.BrauchtBrowser ||
                ergebnis is Updater.Ergebnis.Fehlgeschlagen
            ) {
                TextButton(onClick = onSchliessen) { Text("Später") }
            }
        }
    )
}
