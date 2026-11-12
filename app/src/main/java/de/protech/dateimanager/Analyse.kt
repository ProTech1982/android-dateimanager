package de.protech.dateimanager

import java.io.File

data class DoppelGruppe(val groesse: Long, val dateien: List<File>) {
    /** Was frei wuerde, wenn nur eine Kopie bliebe. */
    val verschwendet: Long get() = groesse * (dateien.size - 1)
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

    /**
     * Findet Dateien mit gleichem Inhalt: erst nach Groesse gruppieren,
     * dann nur bei gleicher Groesse die Pruefsumme berechnen.
     */
    fun doppelteDateien(start: File, versteckteZeigen: Boolean): List<DoppelGruppe> {
        val nachGroesse = mutableMapOf<Long, MutableList<File>>()
        val warteschlange = ArrayDeque<File>()
        warteschlange.add(start)
        var gezaehlt = 0

        while (warteschlange.isNotEmpty() && gezaehlt < 60000) {
            val ordner = warteschlange.removeFirst()
            val kinder = runCatching { ordner.listFiles() }.getOrNull() ?: continue
            for (kind in kinder) {
                if (Papierkorb.gehoertZumPapierkorb(kind)) continue
                if (!versteckteZeigen && kind.name.startsWith(".")) continue
                if (kind.isDirectory) {
                    warteschlange.add(kind)
                } else {
                    val groesse = kind.length()
                    if (groesse > 0) {
                        nachGroesse.getOrPut(groesse) { mutableListOf() }.add(kind)
                        gezaehlt++
                    }
                }
            }
        }

        val gruppen = mutableListOf<DoppelGruppe>()
        nachGroesse.filterValues { it.size > 1 }.forEach { (groesse, dateien) ->
            val nachSumme = mutableMapOf<String, MutableList<File>>()
            dateien.forEach { datei ->
                val summe = DateiAktionen.pruefsumme(datei) ?: return@forEach
                nachSumme.getOrPut(summe) { mutableListOf() }.add(datei)
            }
            nachSumme.values.filter { it.size > 1 }.forEach {
                gruppen.add(DoppelGruppe(groesse, it))
            }
        }
        return gruppen.sortedByDescending { it.verschwendet }
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
