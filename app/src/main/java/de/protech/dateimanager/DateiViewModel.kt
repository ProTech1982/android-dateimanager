package de.protech.dateimanager

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Sortierung(val bezeichnung: String) {
    NAME("Name"), DATUM("Datum"), GROESSE("Größe"), TYP("Typ")
}

enum class AblageModus { KOPIEREN, VERSCHIEBEN }

/** Eine rueckgaengig machbare Aktion. */
data class RueckgaengigAktion(val beschreibung: String, val ausfuehren: () -> String)

data class DateiZustand(
    val ordner: File = Environment.getExternalStorageDirectory(),
    val alleEintraege: List<DateiEintrag> = emptyList(),
    val aktiveTypen: Set<DateiTyp> = DateiTyp.entries.toSet(),
    val suchtext: String = "",
    val sucheOffen: Boolean = false,
    val sortierung: Sortierung = Sortierung.NAME,
    val absteigend: Boolean = false,
    val versteckteZeigen: Boolean = false,
    val ueberallSuchen: Boolean = false,
    val laedt: Boolean = false,
    val auswahl: Set<String> = emptySet(),
    val ablage: List<File> = emptyList(),
    val ablageModus: AblageModus = AblageModus.KOPIEREN,
    val arbeitet: Boolean = false,
    val meldung: String? = null,
    val rueckgaengig: RueckgaengigAktion? = null,
    val papierkorb: List<PapierkorbEintrag> = emptyList(),
    val doppelte: List<DoppelGruppe> = emptyList(),
    val bericht: SpeicherBericht? = null,
    val musterTreffer: Int = 0,
    val musterGroesse: Long = 0L
)

class DateiViewModel(private val einstellungen: Einstellungen) : ViewModel() {

    class Fabrik(private val einstellungen: Einstellungen) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DateiViewModel(einstellungen) as T
    }

    val wurzel: File = Environment.getExternalStorageDirectory()

    private val _zustand = MutableStateFlow(DateiZustand())
    val zustand: StateFlow<DateiZustand> = _zustand.asStateFlow()

    private var ladeJob: Job? = null

    init {
        laden()
        viewModelScope.launch(Dispatchers.IO) {
            Papierkorb.aufraeumen(einstellungen.zustand.value.papierkorbTage)
        }
    }

    // --- Navigation -------------------------------------------------------

    fun oeffneOrdner(ordner: File) {
        val gemerkt = einstellungen.sortierungFuer(ordner.absolutePath)
        _zustand.update {
            it.copy(
                ordner = ordner,
                ueberallSuchen = false,
                auswahl = emptySet(),
                sortierung = gemerkt?.first ?: it.sortierung,
                absteigend = gemerkt?.second ?: it.absteigend
            )
        }
        laden()
    }

    fun nachOben(): Boolean {
        val z = _zustand.value
        if (z.ueberallSuchen) { setzeUeberallSuchen(false); return true }
        if (z.ordner.absolutePath == wurzel.absolutePath) return false
        val eltern = z.ordner.parentFile ?: return false
        if (!eltern.absolutePath.startsWith(wurzel.absolutePath)) return false
        oeffneOrdner(eltern)
        return true
    }

    fun zurWurzel() = oeffneOrdner(wurzel)

    // --- Filter und Optionen ---------------------------------------------

    fun schalteTyp(typ: DateiTyp) {
        _zustand.update { z ->
            z.copy(aktiveTypen = if (typ in z.aktiveTypen) z.aktiveTypen - typ else z.aktiveTypen + typ)
        }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun alleTypen(an: Boolean) {
        _zustand.update { it.copy(aktiveTypen = if (an) DateiTyp.entries.toSet() else emptySet()) }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun setzeSuchtext(text: String) {
        _zustand.update { it.copy(suchtext = text) }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun schalteSuchfeld() {
        _zustand.update {
            if (it.sucheOffen) it.copy(sucheOffen = false, suchtext = "") else it.copy(sucheOffen = true)
        }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun setzeSortierung(s: Sortierung) {
        _zustand.update {
            if (it.sortierung == s) it.copy(absteigend = !it.absteigend)
            else it.copy(sortierung = s, absteigend = false)
        }
        val z = _zustand.value
        einstellungen.merkeSortierung(z.ordner.absolutePath, z.sortierung, z.absteigend)
    }

    fun schalteVersteckte() {
        _zustand.update { it.copy(versteckteZeigen = !it.versteckteZeigen) }
        laden()
    }

    fun setzeUeberallSuchen(an: Boolean) {
        _zustand.update { it.copy(ueberallSuchen = an, auswahl = emptySet()) }
        laden()
    }

    // --- Laden ------------------------------------------------------------

    fun laden() {
        ladeJob?.cancel()
        val z = _zustand.value
        _zustand.update { it.copy(laedt = true) }

        ladeJob = viewModelScope.launch {
            val eintraege = withContext(Dispatchers.IO) {
                if (z.ueberallSuchen) {
                    DateiRepository.sucheUeberall(
                        start = wurzel,
                        versteckteZeigen = z.versteckteZeigen,
                        typen = z.aktiveTypen - DateiTyp.ORDNER,
                        suchtext = z.suchtext
                    )
                } else {
                    DateiRepository.ladeOrdner(z.ordner, z.versteckteZeigen)
                }
            }
            _zustand.update { it.copy(alleEintraege = eintraege, laedt = false) }
        }
    }

    fun sichtbareEintraege(z: DateiZustand): List<DateiEintrag> {
        val gefiltert = z.alleEintraege.filter { eintrag ->
            eintrag.typ in z.aktiveTypen &&
                (z.suchtext.isBlank() || eintrag.name.contains(z.suchtext, ignoreCase = true))
        }

        val vergleich = when (z.sortierung) {
            Sortierung.NAME -> compareBy<DateiEintrag> { it.name.lowercase() }
            Sortierung.DATUM -> compareBy { it.geaendert }
            Sortierung.GROESSE -> compareBy { it.groesse }
            Sortierung.TYP -> compareBy<DateiEintrag> { it.typ.ordinal }.thenBy { it.name.lowercase() }
        }

        val sortiert = gefiltert.sortedWith(if (z.absteigend) vergleich.reversed() else vergleich)
        return if (z.ueberallSuchen) sortiert else sortiert.sortedByDescending { it.istOrdner }
    }

    // --- Auswahl ----------------------------------------------------------

    fun schalteAuswahl(eintrag: DateiEintrag) {
        val pfad = eintrag.datei.absolutePath
        _zustand.update { z ->
            z.copy(auswahl = if (pfad in z.auswahl) z.auswahl - pfad else z.auswahl + pfad)
        }
    }

    fun auswahlLeeren() = _zustand.update { it.copy(auswahl = emptySet()) }

    fun allesAuswaehlen() {
        val z = _zustand.value
        val alle = sichtbareEintraege(z).map { it.datei.absolutePath }.toSet()
        _zustand.update { it.copy(auswahl = if (it.auswahl.size == alle.size) emptySet() else alle) }
    }

    fun ausgewaehlteDateien(): List<File> = _zustand.value.auswahl.map { File(it) }

    fun meldungGelesen() = _zustand.update { it.copy(meldung = null) }

    // --- Zwischenablage ---------------------------------------------------

    fun zumKopierenMerken() = merke(AblageModus.KOPIEREN)
    fun zumVerschiebenMerken() = merke(AblageModus.VERSCHIEBEN)

    private fun merke(modus: AblageModus) {
        val dateien = ausgewaehlteDateien()
        if (dateien.isEmpty()) return
        val wort = if (modus == AblageModus.KOPIEREN) "kopieren" else "verschieben"
        _zustand.update {
            it.copy(
                ablage = dateien, ablageModus = modus, auswahl = emptySet(),
                meldung = "${dateien.size} zum $wort gemerkt"
            )
        }
    }

    fun ablageLeeren() = _zustand.update { it.copy(ablage = emptyList()) }

    fun fuegeEin() {
        val z = _zustand.value
        if (z.ablage.isEmpty()) return
        val ziel = z.ordner
        val quellen = z.ablage
        val modus = z.ablageModus

        fuehreAus(
            arbeit = {
                var erfolg = 0
                var fehler: String? = null
                val neue = mutableListOf<Pair<File, File>>()
                quellen.forEach { quelle ->
                    val ergebnis = if (modus == AblageModus.KOPIEREN) {
                        DateiAktionen.kopiere(quelle, ziel)
                    } else {
                        DateiAktionen.verschiebe(quelle, ziel)
                    }
                    ergebnis.onSuccess { neu -> erfolg++; neue.add(quelle to neu) }
                        .onFailure { if (fehler == null) fehler = it.message }
                }
                if (modus == AblageModus.VERSCHIEBEN && neue.isNotEmpty()) {
                    merkeRueckgaengig("Verschieben rückgängig") {
                        var zurueck = 0
                        neue.forEach { (urspruenglich, jetzt) ->
                            val zielOrdner = urspruenglich.parentFile
                            if (zielOrdner != null &&
                                DateiAktionen.verschiebe(jetzt, zielOrdner).isSuccess
                            ) zurueck++
                        }
                        "$zurueck zurückverschoben"
                    }
                }
                val wort = if (modus == AblageModus.KOPIEREN) "kopiert" else "verschoben"
                fehler ?: "$erfolg $wort"
            },
            danach = { _zustand.update { it.copy(ablage = emptyList()) } }
        )
    }

    // --- Schreiboperationen ----------------------------------------------

    /** Loeschen bedeutet: ab in den Papierkorb. */
    fun inDenPapierkorb() {
        val dateien = ausgewaehlteDateien()
        if (dateien.isEmpty()) return
        fuehreAus(
            arbeit = {
                val eintraege = mutableListOf<PapierkorbEintrag>()
                var fehler: String? = null
                dateien.forEach { datei ->
                    Papierkorb.hinein(datei)
                        .onSuccess { eintraege.add(it) }
                        .onFailure { if (fehler == null) fehler = it.message }
                }
                if (eintraege.isNotEmpty()) {
                    merkeRueckgaengig("Löschen rückgängig") {
                        val zurueck = eintraege.count { Papierkorb.wiederherstellen(it).isSuccess }
                        "$zurueck wiederhergestellt"
                    }
                }
                fehler ?: "${eintraege.size} in den Papierkorb"
            },
            danach = { _zustand.update { it.copy(auswahl = emptySet()) } }
        )
    }

    fun benenneUm(datei: File, neuerName: String) {
        fuehreAus(
            arbeit = {
                DateiAktionen.benenneUm(datei, neuerName).fold(
                    { neu ->
                        val alterName = datei.name
                        merkeRueckgaengig("Umbenennen rückgängig") {
                            DateiAktionen.benenneUm(neu, alterName)
                                .fold({ "Wieder $alterName" }, { it.message ?: "Fehlgeschlagen" })
                        }
                        "Umbenannt in ${neu.name}"
                    },
                    { it.message ?: "Umbenennen fehlgeschlagen" }
                )
            },
            danach = { _zustand.update { it.copy(auswahl = emptySet()) } }
        )
    }

    fun neuerOrdner(name: String) {
        val ziel = _zustand.value.ordner
        fuehreAus(arbeit = {
            DateiAktionen.neuerOrdner(ziel, name)
                .fold({ "Ordner ${it.name} angelegt" }, { it.message ?: "Anlegen fehlgeschlagen" })
        })
    }

    fun stapelUmbenennen(vorlage: String, start: Int) {
        val dateien = ausgewaehlteDateien().filter { it.isFile }
        if (dateien.isEmpty()) return
        fuehreAus(
            arbeit = {
                val (erfolg, fehler) = DateiAktionen.stapelUmbenennen(dateien, vorlage, start)
                fehler ?: "$erfolg umbenannt"
            },
            danach = { _zustand.update { it.copy(auswahl = emptySet()) } }
        )
    }

    fun packeAuswahl(name: String) {
        val dateien = ausgewaehlteDateien()
        val ziel = _zustand.value.ordner
        if (dateien.isEmpty()) return
        fuehreAus(
            arbeit = {
                DateiAktionen.packe(dateien, ziel, name)
                    .fold({ "Archiv ${it.name} erstellt" }, { it.message ?: "Packen fehlgeschlagen" })
            },
            danach = { _zustand.update { it.copy(auswahl = emptySet()) } }
        )
    }

    fun entpacke(archiv: File) {
        val ziel = _zustand.value.ordner
        fuehreAus(
            arbeit = {
                DateiAktionen.entpacke(archiv, ziel)
                    .fold({ "Nach ${it.name} entpackt" }, { it.message ?: "Entpacken fehlgeschlagen" })
            },
            danach = { _zustand.update { it.copy(auswahl = emptySet()) } }
        )
    }

    // --- Rueckgaengig -----------------------------------------------------

    private fun merkeRueckgaengig(beschreibung: String, aktion: () -> String) {
        _zustand.update { it.copy(rueckgaengig = RueckgaengigAktion(beschreibung, aktion)) }
    }

    fun macheRueckgaengig() {
        val aktion = _zustand.value.rueckgaengig ?: return
        _zustand.update { it.copy(rueckgaengig = null) }
        fuehreAus(arbeit = { aktion.ausfuehren() })
    }

    // --- Papierkorb -------------------------------------------------------

    fun ladePapierkorb() {
        viewModelScope.launch {
            val liste = withContext(Dispatchers.IO) { Papierkorb.liste() }
            _zustand.update { it.copy(papierkorb = liste) }
        }
    }

    fun stelleWiederHer(eintrag: PapierkorbEintrag) {
        fuehreAus(
            arbeit = {
                Papierkorb.wiederherstellen(eintrag)
                    .fold({ "${it.name} wiederhergestellt" }, { it.message ?: "Fehlgeschlagen" })
            },
            danach = { ladePapierkorb() }
        )
    }

    fun endgueltigLoeschen(eintrag: PapierkorbEintrag) {
        fuehreAus(
            arbeit = {
                if (Papierkorb.endgueltigLoeschen(eintrag)) "${eintrag.name} endgültig gelöscht"
                else "Löschen fehlgeschlagen"
            },
            danach = { ladePapierkorb() }
        )
    }

    fun leerePapierkorb() {
        fuehreAus(
            arbeit = { "${Papierkorb.leeren()} endgültig gelöscht" },
            danach = { ladePapierkorb() }
        )
    }

    // --- Werkzeuge --------------------------------------------------------

    fun pruefeMuster(filter: MusterFilter) {
        val z = _zustand.value
        viewModelScope.launch {
            val treffer = withContext(Dispatchers.IO) {
                Analyse.passendeDateien(z.ordner, filter, z.versteckteZeigen)
            }
            _zustand.update {
                it.copy(musterTreffer = treffer.size, musterGroesse = treffer.sumOf { d -> d.length() })
            }
        }
    }

    fun waehleNachMuster(filter: MusterFilter) {
        val z = _zustand.value
        fuehreAus(
            arbeit = {
                val treffer = Analyse.passendeDateien(z.ordner, filter, z.versteckteZeigen)
                _zustand.update { it.copy(auswahl = treffer.map { d -> d.absolutePath }.toSet()) }
                "${treffer.size} ausgewählt"
            }
        )
    }

    fun findeDoppelte() {
        val z = _zustand.value
        fuehreAus(
            arbeit = {
                val gruppen = Analyse.doppelteDateien(wurzel, z.versteckteZeigen)
                _zustand.update { it.copy(doppelte = gruppen) }
                if (gruppen.isEmpty()) "Keine doppelten Dateien gefunden"
                else "${gruppen.size} Gruppen gefunden"
            }
        )
    }

    fun erstelleBericht() {
        val z = _zustand.value
        fuehreAus(
            arbeit = {
                val bericht = Analyse.speicherBericht(wurzel, z.versteckteZeigen)
                _zustand.update { it.copy(bericht = bericht) }
                "Auswertung fertig"
            }
        )
    }

    private fun fuehreAus(arbeit: () -> String, danach: () -> Unit = {}) {
        _zustand.update { it.copy(arbeitet = true) }
        viewModelScope.launch {
            val meldung = withContext(Dispatchers.IO) { arbeit() }
            danach()
            _zustand.update { it.copy(arbeitet = false, meldung = meldung) }
            laden()
        }
    }
}
