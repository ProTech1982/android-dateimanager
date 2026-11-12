package de.protech.dateimanager

import java.io.File

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
}
