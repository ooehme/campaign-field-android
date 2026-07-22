# Architektur

## Zielbild

Die App wird als einzelne Android-Anwendung mit klaren Schichtgrenzen aufgebaut. Das
erste Release bleibt ein `app`-Modul; eine spätere Modularisierung erfolgt nur bei
messbarem Nutzen. UI und Plattformadapter hängen von Domain-Verträgen ab, nicht
umgekehrt.

```text
Compose UI / Navigation
        |
ViewModels + Use Cases (StateFlow)
        |
Repository-Verträge (domain/data)
     /                         \
OkHttp + Serialization      Room + Sync-Queue
     |                         |
campaign-core              WorkManager

MapLibre <--- Map-State --- LocationManager / Sensors
```

## Schichten und Pakete

| Paket | Verantwortung |
|---|---|
| `ui` | Compose-Screens, Navigation, UI-State; keine HTTP- oder SQL-Details |
| `domain` | Fachmodelle, Statusübergänge, Berechtigungsentscheidungen, Use Cases |
| `data` | Repository-Implementierungen und Abgleich von Remote-/Local-State |
| `network` | OkHttp, Cookie-/CSRF-Handling, DTOs, Fehlernormalisierung |
| `database` | Room-Entities, DAOs, Cache-Metadaten und persistente Queue |
| `location` | explizit gestartete `LocationManager`-Updates als `Flow` |
| `map` | MapLibre-Lifecycle, GeoJSON, Layer und Kamera |
| `sync` | WorkManager, Retry-Klassen, Idempotenz und Queue-Zustände |

## Zustandsmodell

- ViewModels stellen unveränderlichen UI-State über `StateFlow` bereit.
- Ein Repository ist die fachliche Quelle; Room ist offline die lokale Quelle der Wahrheit.
- Remote-Daten werden transaktional gespeichert und anschließend als Flow beobachtet.
- Ausstehende lokale Mutationen bleiben getrennt vom Server-Snapshot und werden als
  Overlay dargestellt. Dadurch überschreibt ein Refetch keine noch nicht synchronisierte Aktion.
- Navigation transportiert nur stabile IDs, keine vollständigen oder sensiblen Objekte.

## Netzwerk und Sanctum

`BuildConfig.API_BASE_URL` enthält die Basis inklusive `/api`. Ein zentraler URL-Builder
verhindert doppelte `/api`-Segmente. OkHttp sendet JSON mit `Accept: application/json`.

Der Auth-Ablauf ist:

1. CSRF-Cookie am Origin-Endpunkt `GET /sanctum/csrf-cookie` laden.
2. `POST /login` mit E-Mail und Passwort; Cookies im persistenten Cookie-Jar übernehmen.
3. Für schreibende Requests den URL-dekodierten Wert von `XSRF-TOKEN` als
   `X-XSRF-TOKEN` senden.
4. Sitzung mit `GET /user` prüfen; niemals `Authorization: Bearer` erzeugen.
5. Bei 401 Auth-, Cache-, Queue-, Foto- und Standortzustand löschen; bei 403 nur die
   Aktion ablehnen; 422 fachlich anzeigen; 5xx/netzwerkabhängig erneut versuchen.

Der CSRF-Endpunkt liegt außerhalb `/api`. Client-Origin, Cookie-Domain und SameSite-
Verhalten sind im Phase-2-Spike bestätigt. Phase 3 führt alle 401-Antworten über einen
zentralen Handler, der lokale Session-, Cache-, Queue-, Foto- und Standortdaten bereinigt.

`SessionRepository` veröffentlicht den Auth-Zustand als `StateFlow`. Beim App-Start wird
ein lokal gespeichertes Profil nur zur kurzen UI-Hydration verwendet; erst ein erfolgreiches
`GET /user` schaltet die App-Shell frei. Rollen bleiben reine Anzeige und ersetzen nie `can`.

## Offline und Sync

Im aktuellen Ausbaustand speichert Room Assignment-Snapshots/-Details samt Zielgebieten,
Gebäuden, Poster-/Aktionsstandorten und typisierten Mutations-Events. Nachweismetadaten
folgen mit Phase 8. Queue-Zustände:
`pending`, `syncing`, `synced`, `failed`; Payloads werden nicht als beliebige Requests,
sondern als fachlich typisierte Felder persistiert.

WorkManager verarbeitet nur Requests mit Netzwerk-Constraint. 401 stoppt den Lauf,
403/409/422 werden dauerhaft sichtbar `failed`, 5xx und Transportfehler bleiben `pending`
und verwenden Backoff. Erfolgreiche Events werden erst nach lokalem Merge als `synced` markiert.
Gebäude-Retries sind mit stabiler Queue-ID (`client_event_key`) idempotent; `updated_at`
liefert Optimistic Locking mit 409. Für Poster- und Aktionsstand-POSTs fehlt diese
Servergarantie noch.

## Karten, Standort und Sensoren

- MapLibre Native Android ist die einzige Kartenengine.
- Standort kommt ausschließlich vom Android `LocationManager`.
- Standortabfragen starten erst nach einer Nutzeraktion beziehungsweise auf der aktiv
  sichtbaren Scanner-Seite und enden beim Verlassen.
- Es gibt keine permanente Hintergrundortung.
- Kompassdaten kommen optional aus dem Android Sensor Framework; ohne Sensor bleibt
  `bearing = 0` beziehungsweise GPS-Heading.
- Basemap-Style und Tile-Nutzung müssen ohne geheimen API-Key und mit geklärter
  Offline-/Lizenzstrategie funktionieren.

## Berechtigungen

Jede Fachaktion prüft das passende serverseitige `can`-Flag. Fehlende oder unbekannte
Flags sind `false`. Rollen dienen nur zur Anzeige. Plattformberechtigungen (Standort,
Kamera/Medien) werden zusätzlich kontextbezogen und möglichst spät angefragt.

## Dependency-Regel

`google()` ist nur für AndroidX/Jetpack und Build-Artefakte vorgesehen. Vor jeder
neuen Laufzeitabhängigkeit wird geprüft, ob sie Google-Dienste, proprietäre SDKs,
Telemetrie oder dynamische Downloads einführt. MapLibre und OkHttp kommen aus
`mavenCentral()`.
