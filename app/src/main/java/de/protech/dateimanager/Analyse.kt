package de.protech.dateimanager

import java.io.File

enum class DoppelModus(val bezeichnung: String, val erklaerung: String) {
    INHALT(
        "Gleicher Inhalt",
        "Vergleicht erst die Größe und dann eine Prüfsumme über den ganzen " +
            "Inhalt. Der Name spielt keine Rolle — findet also auch Kopien, " +
            "die anders heißen. Gründlich, aber am langsamsten."
    ),
    NAME(
        "Gleicher Name",
        "Vergleicht nur die Dateinamen. Findet dieselbe Datei in mehreren " +
            "Ordnern, auch wenn der Inhalt sich inzwischen unterscheidet."
    ),
    NAME_UND_GROESSE(
        "Name und Größe",
        "Gleicher Name und gleiche Größe. Schnell und meist treffsicher."
    )
}

data class DoppelGruppe(val beschreibung: String, val dateien: List<File>) {
    private fun groesseVon(datei: File) = runCatching { datei.length() }.getOrDefault(0L)

    val groesste: Long get() = dateien.maxOfOrNull { groesseVon(it) } ?: 0L
    val gesamt: Long get() = dateien.sumOf { groesseVon(it) }

    /** Was frei wuerde, wenn nur die groesste Kopie bliebe. */
    val verschwendet: Long get() = gesamt - groesste
}

data class SpeicherPosten(val datei: File, val groesse: Long, val istOrdner: Boolean)

data class SpeicherBericht(
    val gesamt: Long,
    val ordner: List<SpeicherPosten>,
    val groessteDateien: List<SpeicherPosten>
)

data class MusterFilter(
    val endung: String = "",
    val namensanfang: String = "",
    val aelterAlsTage: Int = 0,
    val groesserAlsMb: Int = 0,
    val rekursiv: Boolean = false
) {
    val istLeer: Boolean
        get() = endung.isBlank() && namensanfang.isBlank() &&
            aelterAlsTage <= 0 && groesserAlsMb <= 0
}

/**
 * Auswertungen ueber den Speicher. Alles blockierend, gehoert auf einen
 * Hintergrund-Thread.
 */
object Analyse {

    /** Sammelt Dateien, die auf den Filter passen. */
    fun passendeDateien(start: File, filter: MusterFilter, versteckteZeigen: Boolean): List<File> {
        if (filter.istLeer) return emptyList()

        val grenzzeit = if (filter.aelterAlsTage > 0) {
            System.currentTimeMillis() - filter.aelterAlsTage * 24L * 60L * 60L * 1000L
        } else Long.MAX_VALUE
        val mindestGroesse = filter.groesserAlsMb.toLong() * 1024L * 1024L
        val endung = filter.endung.trim().removePrefix(".").lowercase()
        val anfang = filter.namensanfang.trim().lowercase()

        val treffer = mutableListOf<File>()
        val warteschlange = ArrayDeque<File>()
        warteschlange.add(start)

        while (warteschlange.isNotEmpty() && treffer.size < 20000) {
            val ordner = warteschlange.removeFirst()
            val kinder = runCatching { ordner.listFiles() }.getOrNull() ?: continue

            for (kind in kinder) {
                if (Papierkorb.gehoertZumPapierkorb(kind)) continue
                if (!versteckteZeigen && kind.name.startsWith(".")) continue

                if (kind.isDirectory) {
                    if (filter.rekursiv) warteschlange.add(kind)
                    continue
                }
                if (endung.isNotBlank() && !kind.extension.equals(endung, true)) continue
                if (anfang.isNotBlank() && !kind.name.lowercase().startsWith(anfang)) continue
                if (filter.aelterAlsTage > 0 && kind.lastModified() > grenzzeit) continue
                if (mindestGroesse > 0 && kind.length() < mindestGroesse) continue
                treffer.add(kind)
            }
            if (!filter.rekursiv) break
        }
        return treffer
    }

    /** Sammelt alle Dateien unterhalb von [start], gefiltert nach Typ. */
    private fun alleDateien(
        start: File,
        versteckteZeigen: Boolean,
        typen: Set<DateiTyp>,
        maximal: Int = 60000
    ): List<File> {
        val gefunden = mutableListOf<File>()
        val warteschlange = ArrayDeque<File>()
        warteschlange.add(start)

        while (warteschlange.isNotEmpty() && gefunden.size < maximal) {
            val ordner = warteschlange.removeFirst()
            val kinder = runCatching { ordner.listFiles() }.getOrNull() ?: continue
            for (kind in kinder) {
                if (Papierkorb.gehoertZumPapierkorb(kind)) continue
                if (!versteckteZeigen && kind.name.startsWith(".")) continue
                if (kind.isDirectory) {
                    warteschlange.add(kind)
                } else {
                    if (typen.isNotEmpty() && kind.dateiTyp() !in typen) continue
                    if (kind.length() <= 0L) continue
                    gefunden.add(kind)
                }
            }
        }
        return gefunden
    }

    /**
     * Sucht doppelte Dateien. [modus] entscheidet, was als doppelt gilt;
     * [typen] schraenkt auf bestimmte Dateiarten ein (leer = alle).
     */
    fun doppelteDateien(
        start: File,
        versteckteZeigen: Boolean,
        modus: DoppelModus = DoppelModus.INHALT,
        typen: Set<DateiTyp> = emptySet()
    ): List<DoppelGruppe> {
        val dateien = alleDateien(start, versteckteZeigen, typen)

        return when (modus) {
            DoppelModus.NAME -> dateien
                .groupBy { it.name.lowercase() }
                .filterValues { it.size > 1 }
                .map { (_, gruppe) -> DoppelGruppe(gruppe.first().name, gruppe) }
                .sortedByDescending { it.verschwendet }

            DoppelModus.NAME_UND_GROESSE -> dateien
                .groupBy { it.name.lowercase() to it.length() }
                .filterValues { it.size > 1 }
                .map { (_, gruppe) -> DoppelGruppe(gruppe.first().name, gruppe) }
                .sortedByDescending { it.verschwendet }

            DoppelModus.INHALT -> {
                val gruppen = mutableListOf<DoppelGruppe>()
                dateien.groupBy { it.length() }
                    .filterValues { it.size > 1 }
                    .forEach { (_, gleichGross) ->
                        val nachSumme = mutableMapOf<String, MutableList<File>>()
                        gleichGross.forEach { datei ->
                            val summe = DateiAktionen.pruefsumme(datei) ?: return@forEach
                            nachSumme.getOrPut(summe) { mutableListOf() }.add(datei)
                        }
                        nachSumme.values.filter { it.size > 1 }.forEach {
                            gruppen.add(DoppelGruppe(it.first().name, it))
                        }
                    }
                gruppen.sortedByDescending { it.verschwendet }
            }
        }
    }

    /** Welche Ordner und Dateien belegen den meisten Platz? */
    fun speicherBericht(start: File, versteckteZeigen: Boolean): SpeicherBericht {
        val kinder = runCatching { start.listFiles() }.getOrNull() ?: return SpeicherBericht(0, emptyList(), emptyList())

        val sichtbar = kinder.filter {
            !Papierkorb.gehoertZumPapierkorb(it) && (versteckteZeigen || !it.name.startsWith("."))
        }

        val posten = sichtbar.map {
            SpeicherPosten(it, DateiAktionen.groesseVon(it), it.isDirectory)
        }.sortedByDescending { it.groesse }

        val groesste = mutableListOf<SpeicherPosten>()
        val warteschlange = ArrayDeque<File>()
        warteschlange.add(start)
        var gezaehlt = 0
        while (warteschlange.isNotEmpty() && gezaehlt < 60000) {
            val ordner = warteschlange.removeFirst()
            val inhalt = runCatching { ordner.listFiles() }.getOrNull() ?: continue
            for (kind in inhalt) {
                if (Papierkorb.gehoertZumPapierkorb(kind)) continue
                if (!versteckteZeigen && kind.name.startsWith(".")) continue
                if (kind.isDirectory) warteschlange.add(kind)
                else {
                    groesste.add(SpeicherPosten(kind, kind.length(), false))
                    gezaehlt++
                }
            }
        }

        return SpeicherBericht(
            gesamt = posten.sumOf { it.groesse },
            ordner = posten.take(15),
            groessteDateien = groesste.sortedByDescending { it.groesse }.take(25)
        )
    }
}
