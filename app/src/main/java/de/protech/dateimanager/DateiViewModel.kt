package de.protech.dateimanager

import android.os.Environment
import androidx.lifecycle.ViewModel
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
    NAME("Name"),
    DATUM("Datum"),
    GROESSE("Größe"),
    TYP("Typ")
}

enum class AblageModus { KOPIEREN, VERSCHIEBEN }

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
    /** Absolute Pfade der ausgewaehlten Eintraege. */
    val auswahl: Set<String> = emptySet(),
    /** Was beim naechsten Einfuegen passiert. */
    val ablage: List<File> = emptyList(),
    val ablageModus: AblageModus = AblageModus.KOPIEREN,
    /** Laeuft gerade eine Schreiboperation? */
    val arbeitet: Boolean = false,
    /** Einmalige Rueckmeldung fuer die Snackbar. */
    val meldung: String? = null
)

class DateiViewModel : ViewModel() {

    val wurzel: File = Environment.getExternalStorageDirectory()

    private val _zustand = MutableStateFlow(DateiZustand())
    val zustand: StateFlow<DateiZustand> = _zustand.asStateFlow()

    private var ladeJob: Job? = null

    init {
        laden()
    }

    // --- Navigation -------------------------------------------------------

    fun oeffneOrdner(ordner: File) {
        _zustand.update { it.copy(ordner = ordner, ueberallSuchen = false) }
        laden()
    }

    /** true, wenn wirklich nach oben navigiert wurde. */
    fun nachOben(): Boolean {
        val z = _zustand.value
        if (z.ueberallSuchen) {
            setzeUeberallSuchen(false)
            return true
        }
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
            val neu = if (typ in z.aktiveTypen) z.aktiveTypen - typ else z.aktiveTypen + typ
            z.copy(aktiveTypen = neu)
        }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun alleTypen(an: Boolean) {
        _zustand.update {
            it.copy(aktiveTypen = if (an) DateiTyp.entries.toSet() else emptySet())
        }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun nurTyp(typ: DateiTyp) {
        _zustand.update { it.copy(aktiveTypen = setOf(typ)) }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun setzeSuchtext(text: String) {
        _zustand.update { it.copy(suchtext = text) }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun schalteSuchfeld() {
        _zustand.update {
            if (it.sucheOffen) it.copy(sucheOffen = false, suchtext = "")
            else it.copy(sucheOffen = true)
        }
        if (_zustand.value.ueberallSuchen) laden()
    }

    fun setzeSortierung(s: Sortierung) {
        _zustand.update {
            if (it.sortierung == s) it.copy(absteigend = !it.absteigend)
            else it.copy(sortierung = s, absteigend = false)
        }
    }

    fun schalteVersteckte() {
        _zustand.update { it.copy(versteckteZeigen = !it.versteckteZeigen) }
        laden()
    }

    fun setzeUeberallSuchen(an: Boolean) {
        _zustand.update { it.copy(ueberallSuchen = an) }
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

    /** Die tatsaechlich angezeigte Liste: gefiltert und sortiert. */
    fun sichtbareEintraege(z: DateiZustand): List<DateiEintrag> {
        val gefiltert = z.alleEintraege.filter { eintrag ->
            eintrag.typ in z.aktiveTypen &&
                (z.suchtext.isBlank() || eintrag.name.contains(z.suchtext, ignoreCase = true))
        }

        val vergleich = when (z.sortierung) {
            Sortierung.NAME -> compareBy<DateiEintrag> { it.name.lowercase() }
            Sortierung.DATUM -> compareBy { it.geaendert }
            Sortierung.GROESSE -> compareBy { it.groesse }
            Sortierung.TYP -> compareBy<DateiEintrag> { it.typ.ordinal }
                .thenBy { it.name.lowercase() }
        }

        val sortiert = gefiltert.sortedWith(if (z.absteigend) vergleich.reversed() else vergleich)

        // Ordner stehen beim Blaettern immer oben.
        return if (z.ueberallSuchen) sortiert
        else sortiert.sortedByDescending { it.istOrdner }
    }

    // --- Auswahl ----------------------------------------------------------

    fun schalteAuswahl(eintrag: DateiEintrag) {
        val pfad = eintrag.datei.absolutePath
        _zustand.update { z ->
            z.copy(auswahl = if (pfad in z.auswahl) z.auswahl - pfad else z.auswahl + pfad)
        }
    }

    fun auswahlLeeren() {
        _zustand.update { it.copy(auswahl = emptySet()) }
    }

    fun allesAuswaehlen() {
        val z = _zustand.value
        val alle = sichtbareEintraege(z).map { it.datei.absolutePath }.toSet()
        _zustand.update {
            it.copy(auswahl = if (it.auswahl.size == alle.size) emptySet() else alle)
        }
    }

    fun ausgewaehlteDateien(): List<File> =
        _zustand.value.auswahl.map { File(it) }

    fun meldungGelesen() {
        _zustand.update { it.copy(meldung = null) }
    }

    // --- Zwischenablage ---------------------------------------------------

    fun zumKopierenMerken() = merke(AblageModus.KOPIEREN)

    fun zumVerschiebenMerken() = merke(AblageModus.VERSCHIEBEN)

    private fun merke(modus: AblageModus) {
        val dateien = ausgewaehlteDateien()
        if (dateien.isEmpty()) return
        val wort = if (modus == AblageModus.KOPIEREN) "kopieren" else "verschieben"
        _zustand.update {
            it.copy(
                ablage = dateien,
                ablageModus = modus,
                auswahl = emptySet(),
                meldung = "${dateien.size} zum $wort gemerkt"
            )
        }
    }

    fun ablageLeeren() {
        _zustand.update { it.copy(ablage = emptyList()) }
    }

    fun fuegeEin() {
        val z = _zustand.value
        if (z.ablage.isEmpty()) return
        val ziel = z.ordner

        fuehreAus(
            arbeit = {
                var erfolg = 0
                var fehler: String? = null
                z.ablage.forEach { quelle ->
                    val ergebnis = if (z.ablageModus == AblageModus.KOPIEREN) {
                        DateiAktionen.kopiere(quelle, ziel)
                    } else {
                        DateiAktionen.verschiebe(quelle, ziel)
                    }
                    ergebnis.onSuccess { erfolg++ }
                        .onFailure { if (fehler == null) fehler = it.message }
                }
                val wort = if (z.ablageModus == AblageModus.KOPIEREN) "kopiert" else "verschoben"
                fehler ?: "$erfolg $wort"
            },
            danach = { _zustand.update { it.copy(ablage = emptyList()) } }
        )
    }

    // --- Schreiboperationen ----------------------------------------------

    fun loescheAuswahl() {
        val dateien = ausgewaehlteDateien()
        if (dateien.isEmpty()) return
        fuehreAus(
            arbeit = {
                val geloescht = dateien.count { DateiAktionen.loesche(it) }
                if (geloescht == dateien.size) "$geloescht geloescht"
                else "$geloescht von ${dateien.size} geloescht"
            },
            danach = { _zustand.update { it.copy(auswahl = emptySet()) } }
        )
    }

    fun benenneUm(datei: File, neuerName: String) {
        fuehreAus(
            arbeit = {
                DateiAktionen.benenneUm(datei, neuerName)
                    .fold({ "Umbenannt in ${it.name}" }, { it.message ?: "Umbenennen fehlgeschlagen" })
            },
            danach = { _zustand.update { it.copy(auswahl = emptySet()) } }
        )
    }

    fun neuerOrdner(name: String) {
        val ziel = _zustand.value.ordner
        fuehreAus(
            arbeit = {
                DateiAktionen.neuerOrdner(ziel, name)
                    .fold({ "Ordner ${it.name} angelegt" }, { it.message ?: "Anlegen fehlgeschlagen" })
            }
        )
    }

    /** Fuehrt eine Schreiboperation im Hintergrund aus und laedt danach neu. */
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
