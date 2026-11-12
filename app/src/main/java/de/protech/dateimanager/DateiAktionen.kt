package de.protech.dateimanager

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Die schreibenden Operationen auf dem Dateisystem. Alle Methoden sind
 * blockierend und gehoeren auf einen Hintergrund-Thread.
 */
object DateiAktionen {

    /** Loescht eine Datei oder einen Ordner samt Inhalt. */
    fun loesche(datei: File): Boolean = runCatching {
        if (datei.isDirectory) datei.deleteRecursively() else datei.delete()
    }.getOrDefault(false)

    /** Benennt um. Der neue Name darf keinen Pfadtrenner enthalten. */
    fun benenneUm(datei: File, neuerName: String): Result<File> {
        val sauber = neuerName.trim()
        if (sauber.isBlank()) return Result.failure(IllegalArgumentException("Der Name ist leer."))
        if (sauber.contains('/')) {
            return Result.failure(IllegalArgumentException("Der Name darf kein / enthalten."))
        }
        val ziel = File(datei.parentFile, sauber)
        if (ziel.exists()) {
            return Result.failure(IllegalArgumentException("„$sauber“ gibt es hier schon."))
        }
        return runCatching {
            if (datei.renameTo(ziel)) ziel
            else throw IllegalStateException("Umbenennen fehlgeschlagen.")
        }
    }

    /** Legt einen neuen Ordner an. */
    fun neuerOrdner(eltern: File, name: String): Result<File> {
        val sauber = name.trim()
        if (sauber.isBlank()) return Result.failure(IllegalArgumentException("Der Name ist leer."))
        if (sauber.contains('/')) {
            return Result.failure(IllegalArgumentException("Der Name darf kein / enthalten."))
        }
        val ziel = File(eltern, sauber)
        if (ziel.exists()) {
            return Result.failure(IllegalArgumentException("„$sauber“ gibt es hier schon."))
        }
        return runCatching {
            if (ziel.mkdirs()) ziel else throw IllegalStateException("Ordner anlegen fehlgeschlagen.")
        }
    }

    /**
     * Kopiert in den Zielordner. Ein vorhandener Name wird nicht ueberschrieben,
     * sondern um „(2)“, „(3)“ … ergaenzt.
     */
    fun kopiere(quelle: File, zielOrdner: File): Result<File> {
        if (istInSichSelbst(quelle, zielOrdner)) {
            return Result.failure(IllegalArgumentException("Ein Ordner kann nicht in sich selbst kopiert werden."))
        }
        val ziel = freierName(zielOrdner, quelle.name)
        return runCatching {
            quelle.copyRecursively(ziel, overwrite = false)
            ziel
        }
    }

    /** Verschiebt in den Zielordner; faellt auf Kopieren und Loeschen zurueck. */
    fun verschiebe(quelle: File, zielOrdner: File): Result<File> {
        if (istInSichSelbst(quelle, zielOrdner)) {
            return Result.failure(IllegalArgumentException("Ein Ordner kann nicht in sich selbst verschoben werden."))
        }
        if (quelle.parentFile?.absolutePath == zielOrdner.absolutePath) {
            return Result.success(quelle)
        }
        val ziel = freierName(zielOrdner, quelle.name)
        return runCatching {
            if (quelle.renameTo(ziel)) {
                ziel
            } else {
                // Ueber Dateisystemgrenzen hinweg scheitert renameTo.
                quelle.copyRecursively(ziel, overwrite = false)
                if (!loesche(quelle)) {
                    throw IllegalStateException("Kopiert, aber das Original liess sich nicht loeschen.")
                }
                ziel
            }
        }
    }

    /** Sucht einen freien Namen im Zielordner: „Bild.jpg“ → „Bild (2).jpg“. */
    fun freierName(zielOrdner: File, name: String): File {
        var ziel = File(zielOrdner, name)
        if (!ziel.exists()) return ziel

        val punkt = name.lastIndexOf('.')
        val basis = if (punkt > 0) name.substring(0, punkt) else name
        val endung = if (punkt > 0) name.substring(punkt) else ""

        var zaehler = 2
        while (ziel.exists() && zaehler < 1000) {
            ziel = File(zielOrdner, "$basis ($zaehler)$endung")
            zaehler++
        }
        return ziel
    }

    /** Verhindert, dass ein Ordner in einen eigenen Unterordner wandert. */
    private fun istInSichSelbst(quelle: File, zielOrdner: File): Boolean {
        if (!quelle.isDirectory) return false
        val q = quelle.absolutePath.trimEnd('/') + "/"
        val z = zielOrdner.absolutePath.trimEnd('/') + "/"
        return z.startsWith(q)
    }

    // --- Stapel-Umbenennen -----------------------------------------------

    /**
     * Benennt mehrere Dateien nach einer Vorlage um. In der Vorlage steht
     * {n} fuer die laufende Nummer und {name} fuer den bisherigen Namen
     * ohne Endung. Die Endung bleibt immer erhalten.
     */
    fun stapelUmbenennen(dateien: List<File>, vorlage: String, start: Int): Pair<Int, String?> {
        var nummer = start
        var erfolg = 0
        var fehler: String? = null

        dateien.sortedBy { it.name.lowercase() }.forEach { datei ->
            val punkt = datei.name.lastIndexOf('.')
            val basis = if (punkt > 0) datei.name.substring(0, punkt) else datei.name
            val endung = if (punkt > 0) datei.name.substring(punkt) else ""

            val neu = vorlage
                .replace("{n}", nummer.toString().padStart(3, '0'))
                .replace("{name}", basis)
                .let { if (it.contains('.')) it else it + endung }

            benenneUm(datei, neu)
                .onSuccess { erfolg++ }
                .onFailure { if (fehler == null) fehler = it.message }
            nummer++
        }
        return erfolg to fehler
    }

    /** Beispielnamen fuer die Vorschau im Dialog. */
    fun vorschauNamen(dateien: List<File>, vorlage: String, start: Int): List<String> =
        dateien.sortedBy { it.name.lowercase() }.take(3).mapIndexed { i, datei ->
            val punkt = datei.name.lastIndexOf('.')
            val basis = if (punkt > 0) datei.name.substring(0, punkt) else datei.name
            val endung = if (punkt > 0) datei.name.substring(punkt) else ""
            vorlage
                .replace("{n}", (start + i).toString().padStart(3, '0'))
                .replace("{name}", basis)
                .let { if (it.contains('.')) it else it + endung }
        }

    // --- ZIP ---------------------------------------------------------------

    /** Packt die uebergebenen Dateien in ein ZIP im Zielordner. */
    fun packe(dateien: List<File>, zielOrdner: File, name: String): Result<File> = runCatching {
        val ziel = freierName(zielOrdner, if (name.endsWith(".zip")) name else "$name.zip")
        ZipOutputStream(FileOutputStream(ziel).buffered()).use { aus ->
            dateien.forEach { quelle -> schreibeInZip(aus, quelle, quelle.name) }
        }
        ziel
    }

    private fun schreibeInZip(aus: ZipOutputStream, datei: File, pfad: String) {
        if (datei.isDirectory) {
            aus.putNextEntry(ZipEntry("$pfad/"))
            aus.closeEntry()
            datei.listFiles()?.forEach { schreibeInZip(aus, it, "$pfad/${it.name}") }
        } else {
            aus.putNextEntry(ZipEntry(pfad))
            datei.inputStream().buffered().use { it.copyTo(aus) }
            aus.closeEntry()
        }
    }

    /** Entpackt ein ZIP in einen gleichnamigen Ordner daneben. */
    fun entpacke(archiv: File, zielOrdner: File): Result<File> = runCatching {
        val ziel = freierName(zielOrdner, archiv.nameWithoutExtension)
        if (!ziel.mkdirs()) throw IllegalStateException("Zielordner liess sich nicht anlegen.")

        ZipInputStream(BufferedInputStream(archiv.inputStream())).use { ein ->
            var eintrag: ZipEntry? = ein.nextEntry
            while (eintrag != null) {
                val datei = File(ziel, eintrag.name)
                // Schutz vor praeparierten Archiven, die aus dem Ziel ausbrechen.
                if (!datei.canonicalPath.startsWith(ziel.canonicalPath)) {
                    throw SecurityException("Das Archiv enthält unzulässige Pfade.")
                }
                if (eintrag.isDirectory) {
                    datei.mkdirs()
                } else {
                    datei.parentFile?.mkdirs()
                    FileOutputStream(datei).buffered().use { aus -> ein.copyTo(aus) }
                }
                ein.closeEntry()
                eintrag = ein.nextEntry
            }
        }
        ziel
    }

    // --- Pruefsumme --------------------------------------------------------

    /** MD5 einer Datei, zum Erkennen doppelter Dateien. */
    fun pruefsumme(datei: File): String? = runCatching {
        val digest = MessageDigest.getInstance("MD5")
        datei.inputStream().buffered().use { ein ->
            val puffer = ByteArray(64 * 1024)
            while (true) {
                val gelesen = ein.read(puffer)
                if (gelesen <= 0) break
                digest.update(puffer, 0, gelesen)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Gesamtgroesse einer Datei oder eines Ordners. */
    fun groesseVon(datei: File): Long = runCatching {
        if (datei.isFile) datei.length()
        else datei.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)
}
