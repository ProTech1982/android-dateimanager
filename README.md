# Dateimanager

Ein schlanker Dateimanager für Android — ohne Werbung, ohne Tracker, ohne Konto.

Keine Werbe- oder Analyse-Bibliotheken sind eingebunden. Die einzige
Netzverbindung der App ist die Update-Prüfung bei GitHub.

## Anzeigen

- **Alles im Blick**: Ordner, Dokumente, Bilder, Videos, Musik, Archive, APKs
  und alles Übrige im internen Speicher.
- **Dateitypen an- und abwählen**: Die Chip-Leiste über der Liste blendet jede
  Kategorie einzeln ein oder aus. „Alle" schaltet auf einen Schlag um.
- **Im ganzen Speicher suchen**: Das Globus-Symbol oben rechts durchsucht nicht
  nur den aktuellen Ordner, sondern den gesamten Speicher. Zusammen mit den
  Filtern zeigt das etwa alle Bilder des Handys auf einmal, egal wo sie liegen.
- **Namenssuche** über das Lupen-Symbol.
- **Sortieren** nach Name, Datum, Größe oder Typ; zweites Antippen dreht die
  Reihenfolge um.
- **Versteckte Dateien** ein- und ausblenden.
- **Schnellzugriff** auf Downloads, DCIM, Bilder, Videos, Musik, Dokumente.
- **Brotkrumen-Pfad** zum Zurückspringen in jede Ebene.
- Vorschaubilder für Fotos und Videos, Material-You-Farben ab Android 12,
  heller und dunkler Modus.

## Bearbeiten

Ein **langer Druck** auf einen Eintrag startet die Auswahl. Danach wählt ein
Tippen weitere Einträge an und ab, und die obere Leiste zeigt die Aktionen:

| Aktion | Wo |
|---|---|
| Kopieren | Symbol in der Auswahlleiste |
| Ausschneiden (Verschieben) | Symbol in der Auswahlleiste |
| Löschen | Symbol in der Auswahlleiste, mit Rückfrage |
| Umbenennen | Menü der Auswahlleiste, bei genau einem Eintrag |
| Teilen | Menü der Auswahlleiste |
| Alle auswählen | Menü der Auswahlleiste |
| Neuer Ordner | Schaltfläche unten rechts |

Nach *Kopieren* oder *Ausschneiden* erscheint über der Liste eine Leiste mit
**„Hier einfügen"** — in den Ordner wechseln, einfügen, fertig. Bestehende
Dateien werden nie überschrieben: Gleichnamiges landet als `Name (2).jpg`
daneben. Ordner lassen sich nicht in sich selbst verschieben, und Löschen
fragt vorher nach, weil Ordner mitsamt Inhalt verschwinden.

## Aktualisieren

Menü → **„Nach Updates suchen"**. Die App fragt den neuesten Release ab,
zeigt Version, Größe und die Änderungen und öffnet den Download im Browser.
Installiert wird von Hand — die App installiert bewusst nichts selbst, denn
die dafür nötige Berechtigung ist dasselbe Muster, das Schadsoftware benutzt.

## Technik

| | |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Mindestens | Android 8.0 (API 26) |
| Ziel | Android 15 (API 35) |
| Paketname | `de.protech.dateimanager` |

| Datei | Aufgabe |
|---|---|
| `MainActivity.kt` | Einstieg, Berechtigungsabfrage |
| `DateiScreen.kt` | Gesamte Oberfläche |
| `DateiViewModel.kt` | Zustand, Filter, Auswahl, Zwischenablage |
| `DateiRepository.kt` | Ordner lesen, Speicher durchsuchen |
| `DateiAktionen.kt` | Kopieren, Verschieben, Löschen, Umbenennen |
| `DateiTyp.kt` | Kategorien, Endungen, MIME-Typen |
| `Updater.kt` / `UpdateDialog.kt` | Update-Prüfung |
| `Theme.kt` | Farben, heller und dunkler Modus |

## Berechtigungen

| Berechtigung | Wofür |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Alle Dateien sehen und bearbeiten (ab Android 11) |
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` | Dasselbe auf älteren Versionen |
| `WRITE_EXTERNAL_STORAGE` | Schreiben bis Android 10 |
| `INTERNET` | Nur die Update-Prüfung bei GitHub |

Beim ersten Start führt ein Knopf direkt in die Systemeinstellung für den
Dateizugriff.

> Hinweis: `MANAGE_EXTERNAL_STORAGE` muss im Google Play Store gesondert
> begründet werden. Für eine selbst gebaute App, die per APK installiert wird,
> ist das kein Problem.

## APK bauen

Jeder Push nach `main` baut eine **signierte Release-APK** und veröffentlicht
sie als Release `build-<Nummer>` mit der Datei `Dateimanager.apk`. Genau diesen
Release findet die Update-Prüfung in der App.

`versionCode` ist die Lauf-Nummer von GitHub Actions und zählt automatisch
hoch. Die sichtbare Versionsnummer steht in `gradle.properties`
(`appVersionName`) und wird von Hand gepflegt.

Schlägt ein Build fehl, erscheint das vollständige Protokoll als Release
`buildlog-<Nummer>` — so ist der Fehler ohne Anmeldung nachlesbar.

### Benötigte Secrets

Unter *Settings → Secrets and variables → Actions*:

| Secret | Inhalt |
|---|---|
| `KEYSTORE_B64` | Der Signaturschlüssel (`.jks`) als Base64 |
| `KEYSTORE_PASSWORD` | Das Passwort des Schlüssels |
| `KEY_ALIAS` | Optional, Standard ist `dateimanager` |

Ohne festen Schlüssel lässt sich ein neues Build **nicht** über eine bestehende
Installation legen — Android verweigert dann die Aktualisierung. Der Schlüssel
gehört nicht ins Repository; `keystore/` und `*.jks` sind ignoriert.

Einen neuen Schlüssel erzeugen und als Base64 ausgeben:

```bash
keytool -genkeypair -v -keystore dateimanager.jks -alias dateimanager \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 dateimanager.jks > dateimanager.b64
```

### Lokal bauen

```bash
gradle assembleDebug
```

Die fertige Datei liegt unter `app/build/outputs/apk/debug/app-debug.apk`.

> Im Repo liegt bewusst keine `gradle-wrapper.jar` (Binärdatei). Wer `./gradlew`
> nutzen möchte, erzeugt den Wrapper einmalig mit `gradle wrapper`. Android
> Studio und der GitHub-Actions-Workflow brauchen ihn nicht.

## Lizenz

MIT
