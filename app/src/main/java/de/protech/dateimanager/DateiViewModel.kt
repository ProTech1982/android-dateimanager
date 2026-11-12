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
    val laedt: Boolean = false
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
}
