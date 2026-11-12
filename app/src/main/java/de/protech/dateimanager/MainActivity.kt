package de.protech.dateimanager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DateimanagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Einstieg()
                }
            }
        }
    }
}

/** Prueft, ob die App den Speicher lesen darf. */
fun hatSpeicherRecht(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun Einstieg() {
    val context = LocalContext.current
    var darfLesen by remember { mutableStateOf(hatSpeicherRecht(context)) }

    // Nach der Rueckkehr aus den Systemeinstellungen neu pruefen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val beobachter = LifecycleEventObserver { _, ereignis ->
            if (ereignis == Lifecycle.Event.ON_RESUME) {
                darfLesen = hatSpeicherRecht(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(beobachter)
        onDispose { lifecycleOwner.lifecycle.removeObserver(beobachter) }
    }

    if (darfLesen) {
        val viewModel: DateiViewModel = viewModel()
        DateiScreen(viewModel)
    } else {
        RechteScreen(onGewaehrt = { darfLesen = hatSpeicherRecht(context) })
    }
}

@Composable
private fun RechteScreen(onGewaehrt: () -> Unit) {
    val context = LocalContext.current

    val starter = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onGewaehrt() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Zugriff auf den Speicher",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Damit dir alle Ordner, Bilder und Dokumente angezeigt werden " +
                "können, braucht die App die Berechtigung zum Lesen des Speichers. " +
                "Es werden keine Daten verschickt – die App hat keinen Internetzugriff.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val gezielt = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                val allgemein = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                runCatching { context.startActivity(gezielt) }
                    .onFailure { runCatching { context.startActivity(allgemein) } }
            } else {
                starter.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }) {
            Text("Berechtigung erteilen")
        }
    }
}
