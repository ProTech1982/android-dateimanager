# Dateimanager

Ein schlanker Dateimanager für Android – ohne Werbung, ohne Tracker, ohne Konto.

Keine Werbe- oder Analyse-Bibliotheken, kein Konto, kein Tracking. Die einzige
Netzverbindung der App ist die Update-Prüfung bei GitHub – eine Abfrage der
Releases-API, sonst nichts.

## Was die App kann

- **Alles anzeigen**: Ordner, Dokumente, Bilder, Videos, Musik, Archive, APKs und
  alles Übrige im internen Speicher.
- **Dateitypen an- und abwählen**: In der Chip-Leiste über der Liste lässt sich
  jede Kategorie einzeln ein- oder ausblenden. „Alle“ schaltet auf einen Schlag
  alles an oder aus.
- **Im ganzen Speicher suchen**: Über das Globus-Symbol oben rechts wird nicht
  nur der aktuelle Ordner durchsucht, sondern der gesamte Speicher. In
  Kombination mit den Filtern zeigt das z. B. alle Bilder des Handys auf einmal.
- **Suchen nach Namen** über das Lupen-Symbol.
- **Sortieren** nach Name, Datum, Größe oder Typ – zweites Antippen dreht die
  Reihenfolge um.
- **Versteckte Dateien** ein- und ausblenden.
- **Schnellzugriff** auf Downloads, DCIM, Bilder, Videos, Musik, Dokumente.
- **Dateien öffnen** in der passenden App des Geräts.
- Vorschaubilder für Fotos und Videos, Material-You-Farben ab Android 12,
  heller und dunkler Modus.
- **Update-Prüfung**: Menü → „Nach Updates suchen". Die App fragt den neuesten
  Release ab und öffnet bei einer neueren Version den Download im Browser.
  Installiert wird von Hand – die App installiert bewusst nichts selbst.

## Technik

| | |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Mindestens | Android 8.0 (API 26) |
| Ziel | Android 15 (API 35) |
| Paketname | `de.protech.dateimanager` |

## Berechtigungen

Für die Anzeige *aller* Dateien nutzt die App ab Android 11
`MANAGE_EXTERNAL_STORAGE` („Zugriff auf alle Dateien“). Beim ersten Start führt
ein Knopf direkt in die passende Systemeinstellung.

> Hinweis: Diese Berechtigung muss im Google Play Store gesondert begründet
> werden. Für eine selbst gebaute App, die per APK installiert wird, ist das
> kein Problem.

## APK bauen

Bei jedem Push nach `main` baut GitHub Actions eine **signierte Release-APK**
und veröffentlicht sie als Release `build-<Nummer>` mit der Datei
`Dateimanager.apk`. Genau diesen Release findet die Update-Prüfung in der App.

`versionCode` ist die Lauf-Nummer von GitHub Actions und zählt automatisch
hoch. Die sichtbare Versionsnummer steht in `gradle.properties`
(`appVersionName`) und wird von Hand gepflegt.

### Benötigte Secrets

Unter *Settings → Secrets and variables → Actions*:

| Secret | Inhalt |
|---|---|
| `KEYSTORE_B64` | Der Signaturschlüssel (`.jks`) als Base64 |
| `KEYSTORE_PASSWORD` | Das Passwort des Schlüssels |
| `KEY_ALIAS` | Optional, Standard ist `dateimanager` |

Ohne festen Schlüssel lässt sich ein neues Build **nicht** über eine bestehende
Installation legen – Android verweigert dann die Aktualisierung. Der Schlüssel
gehört nicht ins Repository, `keystore/` und `*.jks` sind ignoriert.

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
