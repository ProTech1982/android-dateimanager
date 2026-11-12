package de.protech.dateimanager

import java.io.File

/**
 * Die Kategorien, nach denen im Dateimanager gefiltert werden kann.
 * Jede Kategorie kann in der Chip-Leiste einzeln an- und abgewaehlt werden.
 */
enum class DateiTyp(val bezeichnung: String) {
    ORDNER("Ordner"),
    BILD("Bilder"),
    VIDEO("Videos"),
    AUDIO("Musik"),
    DOKUMENT("Dokumente"),
    ARCHIV("Archive"),
    APP("Apps"),
    SONSTIGES("Sonstiges")
}

private val bildEndungen = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif",
    "tif", "tiff", "svg", "dng", "raw", "cr2", "nef", "avif"
)

private val videoEndungen = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp",
    "m4v", "mpg", "mpeg", "ts", "mts", "m2ts"
)

private val audioEndungen = setOf(
    "mp3", "wav", "flac", "aac", "ogg", "oga", "m4a", "wma",
    "opus", "amr", "mid", "midi", "aiff"
)

private val dokumentEndungen = setOf(
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md",
    "rtf", "odt", "ods", "odp", "csv", "epub", "mobi", "html", "htm",
    "xml", "json", "log", "ini", "cfg", "yml", "yaml"
)

private val archivEndungen = setOf(
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab", "tgz"
)

private val appEndungen = setOf("apk", "apks", "xapk", "aab")

/** Ordnet einer Datei anhand ihrer Endung eine Kategorie zu. */
fun File.dateiTyp(): DateiTyp {
    if (isDirectory) return DateiTyp.ORDNER
    return when (extension.lowercase()) {
        in bildEndungen -> DateiTyp.BILD
        in videoEndungen -> DateiTyp.VIDEO
        in audioEndungen -> DateiTyp.AUDIO
        in dokumentEndungen -> DateiTyp.DOKUMENT
        in archivEndungen -> DateiTyp.ARCHIV
        in appEndungen -> DateiTyp.APP
        else -> DateiTyp.SONSTIGES
    }
}

/** Ein Eintrag in der Dateiliste. */
data class DateiEintrag(
    val datei: File,
    val name: String,
    val istOrdner: Boolean,
    val groesse: Long,
    val geaendert: Long,
    val typ: DateiTyp,
    val anzahlKinder: Int
)

/** Formatiert eine Groesse in Bytes als lesbaren Text, z. B. "3,4 MB". */
fun groesseLesbar(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val einheiten = listOf("kB", "MB", "GB", "TB")
    var wert = bytes.toDouble() / 1024.0
    var index = 0
    while (wert >= 1024.0 && index < einheiten.lastIndex) {
        wert /= 1024.0
        index++
    }
    return String.format(java.util.Locale.GERMANY, "%.1f %s", wert, einheiten[index])
}

/** MIME-Typ fuer das Oeffnen einer Datei in einer anderen App. */
fun File.mimeTyp(): String {
    val ext = extension.lowercase()
    val ausAndroid = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    if (ausAndroid != null) return ausAndroid
    return when (dateiTyp()) {
        DateiTyp.BILD -> "image/*"
        DateiTyp.VIDEO -> "video/*"
        DateiTyp.AUDIO -> "audio/*"
        DateiTyp.DOKUMENT -> "text/*"
        else -> "*/*"
    }
}
