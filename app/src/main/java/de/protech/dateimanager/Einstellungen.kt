package de.protech.dateimanager

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class FarbModus(val bezeichnung: String) {
    SYSTEM("Wie das System"), HELL("Immer hell"), DUNKEL("Immer dunkel")
}

enum class FarbQuelle { MATERIAL_YOU, VOREINSTELLUNG, EIGEN }

enum class Ansicht(val bezeichnung: String) {
    LISTE("Liste"), RASTER("Raster"), BAUM("Baum")
}

data class EinstellungsZustand(
    val farbModus: FarbModus = FarbModus.SYSTEM,
    val farbQuelle: FarbQuelle = FarbQuelle.MATERIAL_YOU,
    val voreinstellung: Int = 0,
    val eigenerFarbton: Float = 210f,
    val intensitaet: Float = 0.6f,
    val ansicht: Ansicht = Ansicht.LISTE,
    val zweiSpalten: Boolean = true,
    val lesezeichen: List<String> = emptyList(),
    val papierkorbTage: Int = 30,
    /** Versteckte Dateien farblich hervorheben? */
    val versteckteHervorheben: Boolean = true,
    /** Farbton der Hervorhebung auf dem Farbkreis (0-360). */
    val versteckteFarbton: Float = 32f
)

/**
 * Haelt die Einstellungen der App. Gespeichert wird in SharedPreferences,
 * gelesen wird ueber einen StateFlow, damit die Oberflaeche sofort reagiert.
 */
class Einstellungen(context: Context) {

    private val speicher: SharedPreferences =
        context.applicationContext.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    private val _zustand = MutableStateFlow(lade())
    val zustand: StateFlow<EinstellungsZustand> = _zustand.asStateFlow()

    private fun lade() = EinstellungsZustand(
        farbModus = FarbModus.entries.getOrElse(speicher.getInt("farbModus", 0)) { FarbModus.SYSTEM },
        farbQuelle = FarbQuelle.entries.getOrElse(speicher.getInt("farbQuelle", 0)) { FarbQuelle.MATERIAL_YOU },
        voreinstellung = speicher.getInt("voreinstellung", 0),
        eigenerFarbton = speicher.getFloat("eigenerFarbton", 210f),
        intensitaet = speicher.getFloat("intensitaet", 0.6f),
        ansicht = Ansicht.entries.getOrElse(speicher.getInt("ansicht", 0)) { Ansicht.LISTE },
        zweiSpalten = speicher.getBoolean("zweiSpalten", true),
        lesezeichen = speicher.getStringSet("lesezeichen", emptySet())!!.toList().sorted(),
        papierkorbTage = speicher.getInt("papierkorbTage", 30),
        versteckteHervorheben = speicher.getBoolean("versteckteHervorheben", true),
        versteckteFarbton = speicher.getFloat("versteckteFarbton", 32f)
    )

    private fun schreibe(aenderung: SharedPreferences.Editor.() -> Unit) {
        speicher.edit().apply(aenderung).apply()
        _zustand.value = lade()
    }

    fun setzeFarbModus(m: FarbModus) = schreibe { putInt("farbModus", m.ordinal) }

    fun setzeMaterialYou() = schreibe { putInt("farbQuelle", FarbQuelle.MATERIAL_YOU.ordinal) }

    fun setzeVoreinstellung(index: Int) = schreibe {
        putInt("farbQuelle", FarbQuelle.VOREINSTELLUNG.ordinal)
        putInt("voreinstellung", index)
    }

    fun setzeEigeneFarbe(farbton: Float) = schreibe {
        putInt("farbQuelle", FarbQuelle.EIGEN.ordinal)
        putFloat("eigenerFarbton", farbton)
    }

    fun setzeIntensitaet(wert: Float) = schreibe { putFloat("intensitaet", wert) }

    fun setzeAnsicht(a: Ansicht) = schreibe { putInt("ansicht", a.ordinal) }

    fun setzeZweiSpalten(an: Boolean) = schreibe { putBoolean("zweiSpalten", an) }

    fun setzePapierkorbTage(tage: Int) = schreibe { putInt("papierkorbTage", tage) }

    fun setzeVersteckteHervorheben(an: Boolean) = schreibe {
        putBoolean("versteckteHervorheben", an)
    }

    fun setzeVersteckteFarbton(farbton: Float) = schreibe {
        putFloat("versteckteFarbton", farbton)
    }

    fun schalteLesezeichen(pfad: String) {
        val vorhanden = _zustand.value.lesezeichen.toMutableSet()
        if (!vorhanden.remove(pfad)) vorhanden.add(pfad)
        schreibe { putStringSet("lesezeichen", vorhanden) }
    }

    fun istLesezeichen(pfad: String) = pfad in _zustand.value.lesezeichen

    // --- Sicherung und Wiederherstellung ---------------------------------

    /** Alle Einstellungen als JSON-Text, zum Ablegen in einer Datei. */
    fun alsSicherung(): String {
        val eintraege = JSONArray()
        for ((schluessel, wert) in speicher.all) {
            val (typ, json) = when (wert) {
                is Boolean -> "boolean" to wert
                is Int -> "int" to wert
                is Long -> "long" to wert
                is Float -> "float" to wert.toDouble()
                is String -> "string" to wert
                is Set<*> -> "set" to JSONArray(wert.map { it.toString() })
                else -> continue
            }
            eintraege.put(JSONObject().put("k", schluessel).put("t", typ).put("v", json))
        }
        return JSONObject()
            .put("format", SICHERUNG_FORMAT)
            .put("version", SICHERUNG_VERSION)
            .put("erstellt", java.time.LocalDateTime.now().toString())
            .put("eintraege", eintraege)
            .toString(2)
    }

    /**
     * Ersetzt alle Einstellungen durch die der Sicherung und gibt die Anzahl
     * der Werte zurueck. Bei einer kaputten Datei bleibt alles unveraendert.
     */
    fun ausSicherung(text: String): Int {
        val wurzel = JSONObject(text)
        require(wurzel.optString("format") == SICHERUNG_FORMAT) { "Das ist keine Sicherung des Dateimanagers." }
        require(wurzel.optInt("version", 0) <= SICHERUNG_VERSION) { "Die Sicherung stammt von einer neueren App-Version." }
        val eintraege = wurzel.getJSONArray("eintraege")

        val editor = speicher.edit()
        editor.clear()
        var anzahl = 0
        for (i in 0 until eintraege.length()) {
            val o = eintraege.getJSONObject(i)
            val schluessel = o.getString("k")
            when (o.getString("t")) {
                "boolean" -> editor.putBoolean(schluessel, o.getBoolean("v"))
                "int" -> editor.putInt(schluessel, o.getInt("v"))
                "long" -> editor.putLong(schluessel, o.getLong("v"))
                "float" -> editor.putFloat(schluessel, o.getDouble("v").toFloat())
                "string" -> editor.putString(schluessel, o.getString("v"))
                "set" -> {
                    val arr = o.getJSONArray("v")
                    editor.putStringSet(schluessel, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
                else -> continue
            }
            anzahl++
        }
        check(editor.commit()) { "Einstellungen konnten nicht gespeichert werden" }
        _zustand.value = lade()
        return anzahl
    }

    companion object {
        private const val SICHERUNG_FORMAT = "dateimanager-backup"
        private const val SICHERUNG_VERSION = 1

        fun sicherungsName(): String =
            "dateimanager-sicherung-${java.time.LocalDate.now()}.json"
    }

    // --- Ansicht und Sortierung je Ordner --------------------------------

    fun merkeSortierung(ordner: String, sortierung: Sortierung, absteigend: Boolean) = schreibe {
        putString("sort:$ordner", "${sortierung.ordinal}:$absteigend")
    }

    fun sortierungFuer(ordner: String): Pair<Sortierung, Boolean>? {
        val roh = speicher.getString("sort:$ordner", null) ?: return null
        val teile = roh.split(":")
        val s = Sortierung.entries.getOrNull(teile[0].toIntOrNull() ?: -1) ?: return null
        return s to (teile.getOrNull(1) == "true")
    }
}
