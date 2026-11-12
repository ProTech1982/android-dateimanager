package de.protech.dateimanager

import android.os.Environment
import java.io.File

data class PapierkorbEintrag(
    val id: String,
    val datei: File,
    val urspruenglicherPfad: String,
    val geloeschtAm: Long,
    val groesse: Long,
    val istOrdner: Boolean
) {
    val name: String get() = datei.name
}

/**
 * Ein eigener Papierkorb im internen Speicher. Geloeschtes wird nur
 * verschoben; endgueltig weg ist es erst, wenn der Papierkorb geleert wird.
 */
object Papierkorb {

    private const val ORDNER = ".Dateimanager/Papierkorb"

    fun wurzel(): File =
        File(Environment.getExternalStorageDirectory(), ORDNER).apply { mkdirs() }

    /** Liegt dieser Pfad im Papierkorb? Dann taucht er in Listen nicht auf. */
    fun gehoertZumPapierkorb(datei: File): Boolean =
        datei.absolutePath.startsWith(
            File(Environment.getExternalStorageDirectory(), ".Dateimanager").absolutePath
        )

    /** Verschiebt eine Datei oder einen Ordner in den Papierkorb. */
    fun hinein(datei: File): Result<PapierkorbEintrag> = runCatching {
        val id = System.currentTimeMillis().toString() + "_" + (1000..9999).random()
        val fach = File(wurzel(), id)
        if (!fach.mkdirs()) throw IllegalStateException("Papierkorb nicht beschreibbar.")

        val istOrdner = datei.isDirectory
        val groesse = if (istOrdner) groesseVon(datei) else datei.length()
        val ziel = File(fach, datei.name)

        if (!datei.renameTo(ziel)) {
            datei.copyRecursively(ziel, overwrite = true)
            if (!DateiAktionen.loesche(datei)) {
                throw IllegalStateException("Original liess sich nicht entfernen.")
            }
        }

        File(fach, "info.txt").writeText(
            listOf(datei.absolutePath, System.currentTimeMillis().toString()).joinToString("\n")
        )

        PapierkorbEintrag(id, ziel, datei.absolutePath, System.currentTimeMillis(), groesse, istOrdner)
    }

    fun liste(): List<PapierkorbEintrag> {
        val faecher = runCatching { wurzel().listFiles() }.getOrNull() ?: return emptyList()
        return faecher.filter { it.isDirectory }.mapNotNull { fach ->
            val info = File(fach, "info.txt")
            if (!info.exists()) return@mapNotNull null
            val zeilen = runCatching { info.readLines() }.getOrNull() ?: return@mapNotNull null
            val pfad = zeilen.getOrNull(0) ?: return@mapNotNull null
            val zeit = zeilen.getOrNull(1)?.toLongOrNull() ?: fach.lastModified()
            val inhalt = fach.listFiles()?.firstOrNull { it.name != "info.txt" }
                ?: return@mapNotNull null
            PapierkorbEintrag(
                id = fach.name,
                datei = inhalt,
                urspruenglicherPfad = pfad,
                geloeschtAm = zeit,
                groesse = if (inhalt.isDirectory) groesseVon(inhalt) else inhalt.length(),
                istOrdner = inhalt.isDirectory
            )
        }.sortedByDescending { it.geloeschtAm }
    }

    /** Legt einen Eintrag wieder an seinen urspruenglichen Platz. */
    fun wiederherstellen(eintrag: PapierkorbEintrag): Result<File> = runCatching {
        val original = File(eintrag.urspruenglicherPfad)
        val elternteil = original.parentFile ?: throw IllegalStateException("Zielordner unbekannt.")
        if (!elternteil.exists() && !elternteil.mkdirs()) {
            throw IllegalStateException("Zielordner lässt sich nicht anlegen.")
        }
        val ziel = DateiAktionen.freierName(elternteil, original.name)

        if (!eintrag.datei.renameTo(ziel)) {
            eintrag.datei.copyRecursively(ziel, overwrite = false)
            DateiAktionen.loesche(eintrag.datei)
        }
        File(wurzel(), eintrag.id).deleteRecursively()
        ziel
    }

    fun endgueltigLoeschen(eintrag: PapierkorbEintrag): Boolean =
        runCatching { File(wurzel(), eintrag.id).deleteRecursively() }.getOrDefault(false)

    fun leeren(): Int {
        val faecher = runCatching { wurzel().listFiles() }.getOrNull() ?: return 0
        return faecher.count { runCatching { it.deleteRecursively() }.getOrDefault(false) }
    }

    fun gesamtGroesse(): Long = liste().sumOf { it.groesse }

    /** Raeumt Eintraege weg, die aelter als [tage] Tage sind. */
    fun aufraeumen(tage: Int): Int {
        if (tage <= 0) return 0
        val grenze = System.currentTimeMillis() - tage * 24L * 60L * 60L * 1000L
        return liste().filter { it.geloeschtAm < grenze }.count { endgueltigLoeschen(it) }
    }

    private fun groesseVon(datei: File): Long = runCatching {
        if (datei.isFile) datei.length()
        else datei.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)
}
