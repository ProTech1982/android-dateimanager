package de.protech.dateimanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Sucht auf GitHub nach einer neueren Version und oeffnet deren Seite im
 * Browser. Die App laedt und installiert absichtlich nichts selbst: die
 * dafuer noetige Berechtigung ist dasselbe Muster, das Schadsoftware
 * benutzt, und laesst Play Protect warnen.
 */
object Updater {

    const val REPO = "ProTech1982/android-dateimanager"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    sealed class Ergebnis {
        /** Eine neuere Version liegt bereit. */
        data class Verfuegbar(
            val build: Int,
            val name: String,
            val seite: String,
            val apkUrl: String,
            val groesse: Long,
            val notizen: String
        ) : Ergebnis()

        /** Die installierte Version ist die neueste. */
        data object Aktuell : Ergebnis()

        /** Das Repository ist privat oder nicht erreichbar. */
        data class BrauchtBrowser(val grund: String) : Ergebnis()

        data class Fehlgeschlagen(val grund: String) : Ergebnis()
    }

    /** Die sichtbare Versionsnummer, z. B. "1.0". */
    fun installierterName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    fun installierterBuild(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(0)

    fun pruefe(context: Context, onErgebnis: (Ergebnis) -> Unit) {
        val installiert = installierterBuild(context)
        worker.execute {
            val ergebnis = runCatching { holeNeuestes(installiert) }
                .getOrElse { Ergebnis.Fehlgeschlagen("Keine Verbindung zu GitHub") }
            main.post { onErgebnis(ergebnis) }
        }
    }

    private fun holeNeuestes(installiert: Int): Ergebnis {
        val verbindung = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Dateimanager-App")
        }

        try {
            when (verbindung.responseCode) {
                200 -> Unit
                401, 403, 404 -> return Ergebnis.BrauchtBrowser(
                    "Das Repository ist privat. Die Seite lässt sich im Browser öffnen."
                )
                else -> return Ergebnis.Fehlgeschlagen(
                    "GitHub antwortete mit ${verbindung.responseCode}"
                )
            }

            val text = verbindung.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val tag = json.optString("tag_name")
            val build = tag.substringAfterLast('-').toIntOrNull()
                ?: return Ergebnis.Fehlgeschlagen("Versionsnummer nicht erkannt")

            if (build <= installiert) return Ergebnis.Aktuell

            val seite = json.optString("html_url").ifBlank { RELEASES_PAGE }
            val name = json.optString("name").ifBlank { tag }

            // Direktlink auf die APK und ihre Groesse, damit im Fenster steht,
            // was heruntergeladen wird.
            var apkUrl = seite
            var groesse = 0L
            json.optJSONArray("assets")?.let { anhaenge ->
                for (i in 0 until anhaenge.length()) {
                    val anhang = anhaenge.optJSONObject(i) ?: continue
                    if (anhang.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = anhang.optString("browser_download_url").ifBlank { seite }
                        groesse = anhang.optLong("size")
                        break
                    }
                }
            }

            return Ergebnis.Verfuegbar(
                build = build,
                name = name,
                seite = seite,
                apkUrl = apkUrl,
                groesse = groesse,
                notizen = json.optString("body").trim()
            )
        } finally {
            verbindung.disconnect()
        }
    }

    /** Oeffnet die Release-Seite im Browser; dort laedt und installiert der Nutzer. */
    fun oeffneReleaseSeite(context: Context, url: String? = null) {
        val absicht = Intent(Intent.ACTION_VIEW, Uri.parse(url ?: RELEASES_PAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(absicht) }
    }
}
