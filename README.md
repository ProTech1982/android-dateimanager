# Dateimanager

Ein schlanker Dateimanager für Android – ohne Werbung, ohne Tracker, ohne Konto.

Die App hat **keine `INTERNET`-Berechtigung** im Manifest. Sie kann technisch
gar keine Werbung nachladen und keine Daten verschicken.

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

Bei jedem Push nach `main` baut GitHub Actions automatisch eine Debug-APK.
Zu finden unter **Actions → letzter Lauf → Artifacts → `dateimanager-debug-apk`**.

Ein Tag wie `v1.0` erzeugt zusätzlich ein Release mit der APK als Anhang:

```bash
git tag v1.0
git push origin v1.0
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
