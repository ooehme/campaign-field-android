# Campaign Field Android

Native Android-Neuimplementierung der operativen Field-App für `campaign-core`.
Das Repository enthält ein verifizierbares Projektgerüst, den abgeschlossenen
Sanctum-Cookie-/CSRF-Spike, den produktiven Login-/Session-Lifecycle sowie Assignment-
Liste, Details, Statusaktionen, Offline-Synchronisierung und die operative MapLibre-Karte.

## Leitplanken

- Kotlin, Jetpack Compose und Material 3; keine WebView und kein React-Native-Port.
- MapLibre Native Android, Android `LocationManager`, OkHttp, `kotlinx.serialization`.
- Room für Offline-Daten/Queue, WorkManager für Hintergrund-Sync, Coroutines und Flow.
- Keine Google Play Services, Firebase, Google Maps, FCM, Play Integrity oder Fused Location Provider.
- Laravel Sanctum ausschließlich mit Session-Cookies, CSRF-Cookie und `X-XSRF-TOKEN`; keine Bearer-Tokens.
- Keine Secrets, Cookies, Passwörter, Nachweise oder Standortdaten in Logs oder im Repository.

## Stand

Enthalten sind Login, Session-Wiederherstellung, Profil/Teams, Logout, zentraler
401-Cleanup, die App-Shell mit echtem API-/Standortstatus sowie Assignment-Liste und
Assignment-Details mit Pagination, Referenzsortierung und Teamabgleich. Assignment-Daten
werden in Room gespeichert; Statusänderungen bleiben bei Netzausfall in einer persistenten,
sichtbaren Queue und werden per WorkManager nach Wiederverbindung synchronisiert. Die
Assignment-Karte zeigt Zielgebiete, optionalen Live-Standort und Geräte-Bearing; bei
Basemapfehlern bleibt sie mit lokalem Leer-Stil nutzbar. Der technische Sanctum-Nachweis
steht in [SANCTUM-SPIKE.md](docs/SANCTUM-SPIKE.md); als Nächstes folgt Phase 6 der
[Roadmap](docs/ROADMAP.md).

Die Analyse der PWA-Referenz ist in [API-MAPPING.md](docs/API-MAPPING.md) dokumentiert.
Architektur und Sicherheitskonzept stehen in [ARCHITECTURE.md](docs/ARCHITECTURE.md)
und [SECURITY.md](docs/SECURITY.md).

## Lokaler Build

Voraussetzungen: JDK 17 oder neuer sowie Android SDK Platform 36 und Build Tools 36.0.0.
Der Wrapper verwendet Gradle 9.6.1.

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Unter Windows kann alternativ `gradlew.bat` verwendet werden.

## API-Konfiguration

Der sichere Default ist `https://example.invalid/api`. Die echte Basis-URL wird lokal
oder in CI als Gradle-Property gesetzt:

```properties
CAMPAIGN_FIELD_API_BASE_URL=https://example.invalid/api
CAMPAIGN_FIELD_SANCTUM_CLIENT_ORIGIN=https://example.invalid
CAMPAIGN_FIELD_MAP_STYLE_URL=https://tiles.openfreemap.org/styles/dark
```

Beispiel: [gradle.properties.example](gradle.properties.example). Produktive URLs,
Zugangsdaten und Session-Werte gehören nicht in Versionskontrolle. Die Properties werden
als `BuildConfig`-Werte kompiliert und enthalten ausdrücklich kein Auth-Material. Die
Kartenstil-URL ist optional und verwendet standardmäßig OpenFreeMap Dark. Die
Sanctum-Client-Origin ist die serverseitig freigegebene Stateful-Origin; Requests selbst
bleiben strikt auf die Origin der API-Basis-URL begrenzt.

Für „Run“ aus Android Studio können beide Properties auch in der ignorierten
`local.properties` stehen. Explizite Gradle-Properties (`-P…` oder `~/.gradle`) haben
weiterhin Vorrang.

## Struktur

```text
app/src/main/java/de/oliveroehme/campaignfield/
  data/       Repository-Grenzen
  domain/     Fachmodelle und Use Cases
  ui/         Compose, Theme und Navigation
  network/    OkHttp, Sanctum und API-Verträge
  database/   Room-Cache und Sync-Queue
  location/   LocationManager-basierte Standortquelle
  map/        MapLibre-Integration
  sync/       WorkManager-Orchestrierung
docs/         Analyse, Roadmap, Security und ADRs
```

## Distribution

Das Ziel ist eine reproduzierbare, F-Droid-taugliche Release-Pipeline ohne proprietäre
Laufzeitdienste. Signing-Material wird nie eingecheckt. Reproducible-Build- und
Dependency-Audit-Anforderungen werden in Roadmap-Phase 10 umgesetzt.
