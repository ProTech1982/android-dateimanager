package de.protech.dateimanager

import java.io.File

/**
 * Liest Ordnerinhalte vom Geraet. Alle Methoden sind blockierend und
 * gehoeren auf einen Hintergrund-Thread (Dispatchers.IO).
 */
object DateiRepository {

    /** Inhalt eines einzelnen Ordners. */
    fun ladeOrdner(ordner: File, versteckteZeigen: Boolean): List<DateiEintrag> {
        val kinder = runCatching { ordner.listFiles() }.getOrNull() ?: return emptyList()
        return kinder
            .filter { versteckteZeigen || !it.name.startsWith(".") }
            .map { it.alsEintrag() }
    }

    /**
     * Durchsucht den gesamten Speicher ab [start] und sammelt alle Dateien,
     * die zu einem der [typen] passen. Breitensuche, damit nahe Treffer zuerst
     * kommen; [maxTreffer] verhindert, dass sehr volle Speicher die App ausbremsen.
     */
    fun sucheUeberall(
        start: File,
        versteckteZeigen: Boolean,
        typen: Set<DateiTyp>,
        suchtext: String,
        maxTreffer: Int = 4000
    ): List<DateiEintrag> {
        val treffer = mutableListOf<DateiEintrag>()
        val warteschlange = ArrayDeque<File>()
        warteschlange.add(start)

        while (warteschlange.isNotEmpty() && treffer.size < maxTreffer) {
            val aktuell = warteschlange.removeFirst()
            val kinder = runCatching { aktuell.listFiles() }.getOrNull() ?: continue

            for (kind in kinder) {
                if (!versteckteZeigen && kind.name.startsWith(".")) continue

                if (kind.isDirectory) {
                    warteschlange.add(kind)
                    continue
                }

                val typ = kind.dateiTyp()
                if (typ !in typen) continue
                if (suchtext.isNotBlank() &&
                    !kind.name.contains(suchtext, ignoreCase = true)
                ) continue

                treffer.add(kind.alsEintrag())
                if (treffer.size >= maxTreffer) break
            }
        }
        return treffer
    }

    private fun File.alsEintrag(): DateiEintrag {
        val ordner = isDirectory
        return DateiEintrag(
            datei = this,
            name = name,
            istOrdner = ordner,
            groesse = if (ordner) 0L else runCatching { length() }.getOrDefault(0L),
            geaendert = runCatching { lastModified() }.getOrDefault(0L),
            typ = dateiTyp(),
            anzahlKinder = if (ordner) runCatching { list()?.size ?: 0 }.getOrDefault(0) else 0
        )
    }
}
