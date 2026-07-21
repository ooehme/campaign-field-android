# Referenzanalyse und API-Mapping

Analysiert wurde `ooehme/campaign-field` auf Commit
`01dec625e5116753fbc76ab206b39f4862358b6c` vom 19.06.2026. Es werden keine
React-Komponenten übernommen; maßgeblich sind Fachlogik, API-Verträge und UX-Anforderungen.

## Funktionsumfang und Screens

| Referenzroute | Funktion | Native Zielroute/Phase |
|---|---|---|
| `/login` | E-Mail/Passwort, Sanctum-CSRF, Sessionstart | Login, Phasen 2–3 |
| `/assignments` | teambezogene Liste, Sortierung, Statusaktion, Offlineindikator | Aufträge, Phase 4 |
| `/assignments/:id` | Briefing, Kampagne, Team, Gebiet, Fälligkeit, Gebäude/Poster/Stand | Detail, Phasen 4/6 |
| `/assignments/:id/map` | Zielgebiet, Gebäude, Poster, Stand, Nutzer/Team, Kartenaktionen | Karte, Phasen 5–6 |
| `/scanner` | aktive Assignments, Live-Position, Kompass, Gebäude-/Poster-Schnellaktion | Scanner/Karte, Phasen 5–6 |
| `/assignments/:id/proof` | nur Platzhalter „lokal geplant“ | Nachweise, Phase 8 |
| `/sync` | Queue-Zähler, manuelles Sync, Fehlerdetails, Retry | Sync, Phase 7 |
| `/profile` | Benutzer, Rollenanzeige, Teams/Mitglieder, Einladungen, Logout | Profil, Phase 3; Einladungen später |
| `/invitations` | offene Einladungen annehmen/ablehnen | nach MVP prüfen |

Die Referenz unterstützt Assignment-Typen `standard`, `letterbox_distribution`,
`poster_free`, `poster_guided`, `campaign_booth` und Status `draft`, `ready`, `active`,
`paused`, `completed`, `cancelled`. Entwürfe werden in der operativen Übersicht ausgeblendet.
Sortierung: aktiv, pausiert, bereit, erledigt, abgebrochen, Entwurf; innerhalb eines
Status nach Fälligkeit.

Eine zusätzliche Fachregel blockiert den Start eines Assignments, wenn bereits ein
Assignment in einem anderen Zielgebiet aktiv ist. Diese Regel muss mit dem Backend
bestätigt werden; sie darf nicht nur clientseitige Autorisierung ersetzen.

## Endpunkte

Die Basis-URL enthält in der Referenz bereits `/api`; Pfade dürfen daher kein doppeltes
`/api/api` erzeugen. Der CSRF-Endpunkt wird relativ zur Origin-Wurzel gebildet.

| Methode | Pfad | Verwendung/Response |
|---|---|---|
| `GET` | `/health` | Erreichbarkeit; fachlich nicht gleich Authstatus |
| `GET` | `/sanctum/csrf-cookie` | CSRF-Cookie vor Login; außerhalb API-Präfix verifizieren |
| `POST` | `/login` | `{email,password}`; Session-Cookies |
| `GET` | `/user` | aktueller Benutzer, direkt oder `{data: ...}` |
| `POST` | `/logout` | serverseitige Session beenden |
| `GET` | `/users/{id}` | Profildetails |
| `GET` | `/assignments?per_page=&page=&status=` | Assignment-Liste |
| `GET` | `/assignments/{id}` | Assignment-Detail |
| `PATCH` | `/assignments/{id}` | `status`, optional `description`, `type_config` |
| `GET` | `/users/{id}/assignments` | Fallback-Liste |
| `GET` | `/teams/{id}/assignments` | teambezogene Liste |
| `GET` | `/areas/{id}` | Zielgebiet |
| `GET` | `/campaigns/{id}/areas?per_page=` | Kampagnengebiete |
| `GET` | `/assignments/{id}/buildings?page=&per_page=&updated_since=` | paginierte Gebäude, max. 100/Seite |
| `PATCH` | `/assignment-buildings/{id}` | `status`, `notes`, optional `client_event_key` |
| `GET` | `/assignments/{id}/poster-locations` | Poster-Liste |
| `POST` | `/assignments/{id}/poster-locations` | Poster anlegen |
| `PATCH` | `/poster-locations/{id}` | Poster ändern |
| `DELETE` | `/poster-locations/{id}` | Poster löschen |
| `GET` | `/assignments/{id}/campaign-booth-location` | Standort oder 404 = nicht vorhanden |
| `POST` | `/assignments/{id}/campaign-booth-location` | Aktionsstand anlegen |
| `PATCH` | `/campaign-booth-locations/{id}` | Aktionsstand ändern |
| `DELETE` | `/campaign-booth-locations/{id}` | Aktionsstand löschen |
| `GET` | `/teams/{id}` | Team und Mitglieder |
| `GET` | `/teams/{id}/locations` | aktuelle Teamstandorte |
| `PUT` | `/teams/{id}/location` | `lat`, `lng`, optional Genauigkeit/Heading/Speed/Zeit |
| `GET` | `/user/invitations` | offene Einladungen |
| `POST` | `/team-invitations/{id}/accept` | Einladung annehmen |
| `POST` | `/team-invitations/{id}/decline` | Einladung ablehnen |

Die Referenz-README nennt zusätzlich Create/Bulk/Delete für Assignment Buildings und
Bulk-Create für Poster. Der aktuelle PWA-Client implementiert diese Aufrufe nicht.
`GET /campaigns/{campaign}/assignments` ist dokumentiert, aber nicht im Client verwendet.

## Datenmodelle

| Modell | Relevante Felder |
|---|---|
| `User` | `id`, Name/E-Mail, Anzeige-/App-Rolle, Teams/Memberships, `can` |
| `Assignment` | IDs, `type`, Titel/Beschreibung, `status`, Start/Fälligkeit, `type_config`, Gebiet, Team, Kampagne, Gebäude, Poster, Aktionsstand, `can` |
| `Area` | ID/Label, GeoJSON-Varianten, Mittelpunkt |
| `AssignmentBuilding` | Assignment-/Gebäude-ID, `status`, `notes` (Legacy `note`), Abschlusszeit, Adresse/Geometrie, `can` |
| `PosterLocation` | Label/Notiz/Status, Koordinate/Geometrie, `photo_url`, `can` |
| `CampaignBoothLocation` | Label/Notiz/Status, Zeitfenster, Koordinate/Geometrie, `can` |
| `TeamLocation` | Team/User, `lat/lng`, Genauigkeit, Heading, Speed, Meldezeit |
| `FieldQueuedEvent` | UUID, Assignment, Art, Endpoint/Methode/Payload, Zeiten, Status, Versuche, Fehler |

API-Antworten variieren zwischen direktem Objekt/Array, `{data: ...}` und Laravel-
Pagination. Native DTOs müssen diese Varianten nur so lange unterstützen, wie das
Backend sie nicht vereinheitlicht.

Gebäudestatus in der Referenz: `open`, `done`, `blocked`, `unreachable`, `skipped`,
`problem`. Ein früher dokumentierter Core-Konflikt (`blocked`/`unknown`, `note`/`notes`)
ist laut Praxistest-Datei teilweise behoben, muss aber serverseitig erneut verifiziert werden.

## Authentifizierungsablauf

1. Lokale Auth-/Fachdaten vor neuem Login bereinigen.
2. `GET /sanctum/csrf-cookie` mit Cookie-Unterstützung.
3. `POST /login` als JSON.
4. `GET /user`; nur diese Antwort bestätigt die Session.
5. Benutzer lokal für UI-Hydration speichern, aber beim App-Start stets `/user` prüfen.
6. Schreibende Requests senden Cookies und `X-XSRF-TOKEN`; `Authorization` wird entfernt.
7. 401 löscht Auth, Query-/Assignment-Cache, Queue und Standortzustand und führt zu Login.
8. Logout ruft `/logout` auf und löscht lokale Daten auch bei einem nicht-401-Fehler.

Native Abweichung: Cookies dürfen nicht im Klartext gespeichert werden. Der Cookie-Jar
wird persistent verschlüsselt, sein Schlüssel liegt im Android Keystore.

## `can`-Berechtigungslogik

Fehlende Flags gelten strikt als `false`; Rollen werden nie zur Autorisierung verwendet.

| Container | bekannte Flags |
|---|---|
| `user.can` | `view_assignments` |
| `assignment.can` | `update`, `start`, `pause`, `complete`, `cancel`, `report_issue`, `create_proof`, `manage_poster_locations`, `manage_campaign_booth_location`, `view_team_locations`, `report_team_location` |
| `assignment_building.can` | `update`, `delete` |
| `poster_location.can` | `update`, `delete` |
| `campaign_booth_location.can` | `update`, `delete` |
| `team.can` | `view`, `view_team_locations`, `report_team_location` |

Die UI darf eine verbotene Aktion ausblenden oder deaktiviert erklären. Das Backend
bleibt autoritativ; 403 wird als „Keine Berechtigung für diese Aktion.“ angezeigt.

## Karte, Standort und Sensoren

- MapLibre GL JS ist in der Referenz die einzige Engine; nativ wird MapLibre Native verwendet.
- Basemap bevorzugt OpenFreeMap Dark; Lizenz, Verfügbarkeit und Offline-Caching sind offen.
- Defaultkamera: Zoom 16,5, Pitch 20°, Bearing 0°.
- Darstellung: Zielgebiet, Gebäude/status, Poster, Aktionsstand, eigener Standort,
  Genauigkeit, Teamstandorte und Distanz/Inside-Outside-Prüfung.
- Standort wird per Nutzeraktion oder auf aktiver Scanneransicht beobachtet und beim
  Verlassen gestoppt. Kein permanentes Hintergrundtracking.
- Referenz meldet Teamstandorte aktiv etwa alle 5 Sekunden, sonst etwa alle 30 Sekunden,
  und lädt Teampositionen alle 5 Sekunden. Native Intervalle benötigen Datenschutz-,
  Akku- und Backend-Abnahme.
- Geräteausrichtung ist optional; große Sprünge werden geglättet. Native Umsetzung nutzt
  Android Sensor APIs und darf fehlende Sensorfreigabe nicht blockieren.

## Offline-Cache und Sync-Queue

Die PWA nutzt IndexedDB für Assignment-, Area- und Gebäude-Caches sowie eine Queue.
Lokale Gebäudeänderungen werden als Overlay über Serverdaten gelegt. Queue-Arten sind:

`assignment_status_update`, `assignment_building_update`, `poster_location_create`,
`poster_location_update`, `campaign_booth_location_update`, `proof_create`, `issue_report`.

Verarbeitet werden `pending -> syncing -> synced|failed`; stale `syncing` wird nach fünf
Minuten zurückgesetzt, erfolgreiche Einträge nach 24 Stunden entfernt. Sync startet bei
App-Start, Online-Rückkehr, manuell und nach Retry. Gebäude-Events erhalten die Queue-ID
als `client_event_key`. 401 muss den gesamten Sync stoppen; 403/422 bleiben sichtbar;
5xx/Netzwerkfehler sind erneut versuchbar. Native ersetzt IndexedDB/TanStack Query durch
Room, Repositories, Flow und WorkManager.

## Fotos und Nachweise

Die Referenz hat nur eine Proof-Platzhalterseite. Es gibt kein lokales Foto-/Proof-Store,
keine Aufnahme/Auswahl, keinen Upload-Endpunkt und kein Issue-Report-UI. `photo_url` ist
lediglich am Poster-Modell vorhanden; `proof_create` und `issue_report` existieren nur als
Queue-Arten. Deshalb darf die native App bis zur Backend-Klärung nur „lokal gespeichert“
anzeigen und niemals einen Servererfolg vortäuschen.

## Noch nicht oder nur teilweise implementierte PWA-Funktionen

- Foto-, Notiz- und GPS-Nachweise samt lokalem Blob-Store und Upload.
- Problembericht/`issue_report`.
- Karten-Tap zum Anlegen eines Poster-Standorts (derzeit nur GPS).
- vom Client genutzte Building-Create/Bulk/Delete- und Poster-Bulk-Flows.
- robuste Offline-Basemap; Service Worker cached nur App-Shell/Navigationsfallback.
- Install-Prompt/Update-UX und vollständige Service-Worker-Assetstrategie.
- expliziter Proof-Daten-Cleanup (noch kein Proof-Store vorhanden).
- automatisierte Praxis-/End-to-End-Tests für Kamera, GPS, Reconnect und Multi-User-Sync.

## Offene Backend-Verträge mit höchster Priorität

1. Exakte Sanctum-Origin-/Cookie-/CORS-/SameSite-Konfiguration für einen nativen Client.
2. Finales Schema aller `can`-Flags und Statusübergänge.
3. Idempotenz und Konfliktstrategie für sämtliche Queue-Arten, nicht nur Gebäude.
4. Proof-/Foto-/Issue-Endpunkte, Multipart-Schema, Limits, EXIF- und Löschregeln.
5. Teamstandort-Aufbewahrung, Frequenz, Sichtbarkeit und Löschsemantik.
6. Delta-/Versionsfelder für Assignments, Areas, Gebäude und Standortdaten.
