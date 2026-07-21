# Roadmap

Status: Bootstrap abgeschlossen; produktive Fachfunktionen sind bewusst noch nicht implementiert.
Jede Phase endet mit einem kleinen, testbaren Inkrement. Backend-Fragen müssen vor
Implementierung der davon abhängigen Mutation verbindlich beantwortet werden.

## 1. Projektgrundlage und Architektur

**Ziel:** Reproduzierbares natives Android-Fundament ohne proprietäre Laufzeitdienste.

**Arbeitspakete**

- Gradle Kotlin DSL, Wrapper, Version Catalog, `app`-Modul und Package stabilisieren.
- Compose/Material 3, Navigation, Coroutines/Flow sowie Paketgrenzen etablieren.
- Room, WorkManager, OkHttp, Serialization und MapLibre als fixierte Dependencies prüfen.
- BuildConfig-URL, Buildtypen, Lint, Unit-Test und CI aufsetzen.
- Dependency-Policy mit automatischem Verbot von Play Services/Firebase vorbereiten.

**Abhängigkeiten:** Android SDK/JDK; freigegebene Maven-Repositories; Ziel-Repo.

**Risiken:** AGP-/Kotlin-/Compose-Kompatibilität; MapLibre-native ABI-Größe; unbeabsichtigte
transitive proprietäre Abhängigkeiten.

**Akzeptanzkriterien**

- `./gradlew test`, `./gradlew lint` und `./gradlew assembleDebug` laufen sauber.
- App startet mit Theme, Shell, Navigation und fünf Platzhalter-Screens.
- Kein Secret und keine produktive URL ist versioniert.
- Runtime-Dependency-Graph enthält keine verbotenen Google-Dienste.

**Offene Backend-Fragen:** Unterstützte Android-/API-Version hat keinen Backendbezug;
benötigt die API besondere User-Agent- oder Versionsheader?

## 2. Sanctum-Cookie-/CSRF-Spike

**Ziel:** Native Machbarkeit und Sicherheitsparameter der bestehenden Cookie-Session belegen.

**Arbeitspakete**

- OkHttp-Client mit Origin-Trennung, JSON-Defaults und strikt ohne Bearer-Header bauen.
- persistenten verschlüsselten Cookie-Jar plus Android-Keystore-Schlüssel implementieren.
- CSRF-Cookie laden, `XSRF-TOKEN` dekodieren und `X-XSRF-TOKEN` nur sicher senden.
- Login, `/user`, Rotation, Prozessneustart, Ablauf, 401 und Logout als Integrationstest prüfen.
- Redirect-, Cookie-Domain-, Path-, Secure-, HttpOnly- und SameSite-Verhalten dokumentieren.

**Abhängigkeiten:** Phase 1; sichere Testinstanz; Testkonto ohne Produktivdaten.

**Risiken:** Sanctum SPA-Stateful-Domain-Modell passt nicht ohne Backendanpassung zum nativen
Client; Cookie-Header oder CSRF-Origin sind serverseitig zu eng konfiguriert.

**Akzeptanzkriterien**

- Session überlebt App-Prozessneustart und wird durch `/user` bestätigt.
- Schreibrequest funktioniert mit Cookie + CSRF, ohne `Authorization`.
- Cookies liegen nicht im Klartext und erscheinen nicht in Logs.
- 401/Logout löschen alle Spike-Daten deterministisch.

**Offene Backend-Fragen:** Exakte CSRF-URL; erlaubte Origin/Referer-Policy; Cookie-Domain,
SameSite und `Secure`; Session-/CSRF-Laufzeit; erwartete native Clientkennung; CORS-Relevanz.

## 3. Login, Session und Benutzerprofil

**Ziel:** Produktiver Session-Lifecycle mit verständlichem Login und vollständigem Logout.

**Arbeitspakete**

- Login-Formular, Validierung, Lade-/Fehlerzustände und Tastatur-/Accessibility-Verhalten.
- SessionRepository und `StateFlow`-basierter Auth-State; App-Start über `GET /user`.
- Profil, Teammitgliedschaften und reine Rollenanzeige modellieren.
- zentralen 401-Handler und atomare Datenbereinigung implementieren.
- 403/422/5xx/Netzwerkfehler deutsch und ohne sensible Rohdaten normalisieren.

**Abhängigkeiten:** erfolgreicher Phase-2-Spike; finaler User-/Team-Response.

**Risiken:** variable `{data: ...}`-Hüllen; unvollständige Teamdaten; Logout-Request schlägt
offline fehl; sensible Daten bleiben in Caches oder temporären Dateien.

**Akzeptanzkriterien**

- Login, Session-Wiederherstellung, Ablauf und Logout sind getestet.
- Profil zeigt Name/E-Mail/Teams; Rollen werden nie zur Berechtigung genutzt.
- Passwort, Cookies und Response-Payloads erscheinen nicht in Logcat.
- Logout räumt Cookies, Datenbank, Queue, Fotos und Standortzustand auf.

**Offene Backend-Fragen:** kanonisches `/user`-Schema; Profil-/Team-Einbettung; serverseitige
Sessioninvalidierung bei Offline-Logout; Einladungsscope für das erste native Release.

## 4. Assignment-Liste und Assignment-Details

**Ziel:** Relevante Assignments und Briefings read-only anzeigen; Statusänderungen nur nach Vertrag.

**Arbeitspakete**

- serialisierbare DTOs/Domainmodelle für Assignment, Kampagne, Team, Area, Pagination.
- Repository für `/assignments`, Detail und dokumentierte User-/Team-Fallbacks.
- Liste mit Referenzsortierung, Teamfilter, Status/Fälligkeit und Fehler-/Leerzuständen.
- Detail mit Briefing, `type_config`-Anweisungen und Navigationszielen.
- `can`-Mapper mit fehlend = `false`; Status-Use-Case nach bestätigtem Übergangsmodell.

**Abhängigkeiten:** Phase 3; Assignment- und Permission-Schema.

**Risiken:** inkonsistente Response-Hüllen; `ready` fehlt in älterer Doku; clientseitige
„anderes Zielgebiet aktiv“-Regel weicht von Backendpolicy ab.

**Akzeptanzkriterien**

- Liste und Detail laden, rotieren und zeigen Fehler ohne Absturz.
- Typen/Status/Fälligkeit entsprechen dem Referenzvertrag.
- Jede Mutation wird durch passendes `can`-Flag und Backendvalidierung geschützt.
- Keine Erstellung oder Löschung von Assignments in der Field-App.

**Offene Backend-Fragen:** primärer Listenendpunkt und Filter; vollständige Statusmatrix;
Semantik `draft`/`ready`; Zielgebiets-Exklusivität; ETag/`updated_at`/Pagination.

## 5. MapLibre-Karte und Standort

**Ziel:** Native operative Karte mit optionalem, nutzerkontrolliertem Standort.

**Arbeitspakete**

- MapLibre-MapView lifecycle-sicher in Compose integrieren; genau eine Kartenengine.
- Zielgebiet/GeoJSON und robuste Kamera (Zoom 16,5, Pitch 20°, Bearing-Fallback) darstellen.
- `LocationManager`-Quelle als callbackFlow mit Providerwahl, Genauigkeit und sauberem Stop.
- Runtime-Permissions kontextbezogen erklären; App ohne Freigabe nutzbar halten.
- optionalen Kompass mit SensorManager, Glättung und Lifecycle-Steuerung umsetzen.
- Basemapfehler, Attribution, kein Netz und fehlende Geometrie sichtbar behandeln.

**Abhängigkeiten:** Phase 4; Area-Geometrie; Basemap-/Tile-Entscheidung.

**Risiken:** MapLibre-Lifecycle/Rendering; Akkuverbrauch; ungenaue Positionen; Tile-Lizenz,
Rate Limits und fehlender echter Offline-Style.

**Akzeptanzkriterien**

- Karte zeigt Zielgebiet und bleibt ohne Standort/Sensor nutzbar.
- Standort startet erst nach Nutzeraktion/aktiver Scanneransicht und stoppt beim Verlassen.
- Keine Fused Location Provider-/Google-Maps-Abhängigkeit im Runtime-Graph.
- Koordinaten werden weder geloggt noch ohne konkrete Aktion persistiert.

**Offene Backend-Fragen:** kanonisches GeoJSON/CRS; Style-/Tile-Hosting; Teamstandort-
Frequenz, TTL und Zustimmung; maximal erlaubte Genauigkeit und `reported_at`-Semantik.

## 6. Assignment Buildings und Poster-Standorte

**Ziel:** Operative Objekte listen, auf der Karte darstellen und berechtigt bearbeiten.

**Arbeitspakete**

- paginierte Gebäudeabfrage inklusive `updated_since` und `notes`-Normalisierung.
- Gebäude-Liste/Layer, Statusaktionen, Pending-Sperre und adressbezogene UX.
- Poster-Liste/Layer; GPS- und Karten-Tap-Anlage für `poster_free`.
- geführte Posterstandorte und Aktionsstandort anzeigen/bearbeiten.
- alle Aktionen über `assignment.can` beziehungsweise Ressourcen-`can` absichern.

**Abhängigkeiten:** Phasen 4–5; stabile Objekt-/Status-/Permission-Verträge.

**Risiken:** Statusvokabular driftet; Geometrien fehlen; doppelte Anlage bei Retry;
große Gebäudemengen; Karten-Tap außerhalb Zielgebiet.

**Akzeptanzkriterien**

- Alle Gebäudeseiten werden ohne Duplikate geladen; `note` wird höchstens als Legacy-Alias gelesen.
- `can.update/delete/manage_*` steuert exakt die zugehörige UI-Aktion.
- Poster können nach Bestätigung per GPS oder Karten-Tap angelegt werden.
- 403/422 sind sichtbar; kein Fake-Erfolg.

**Offene Backend-Fragen:** finales Gebäudestatusset; `notes`-Feld; Bulk-Scope; Koordinaten-
vs.-GeoJSON-Pflicht; erlaubte Poster-/Booth-Status; serverseitige Gebietskontrolle.

## 7. Room-Cache und Offline-Sync-Queue

**Ziel:** Lesen und Arbeiten bei instabilem Netz mit nachvollziehbarer, idempotenter Queue.

**Arbeitspakete**

- Room-Schema, DAOs, Migrationstests und Cache-Versionen für alle benötigten Lesemodelle.
- typisierte Queue-Entities mit Zuständen, Versuchen, Fehlerklasse und Zeitstempeln.
- Remote-Snapshot plus Pending-Overlay transaktional zusammenführen.
- WorkManager mit Network-Constraint, Backoff, Einzelinstanz und manuellem Sync.
- 401-Stopp, 403/422 dauerhaft failed, 5xx/Transport-Retry, Cleanup und Retry-UI.
- Reconnect-/Foreground-Refresh und Teamänderungs-Merge testen.

**Abhängigkeiten:** Phasen 3–6; Idempotenz-/Konfliktvertrag.

**Risiken:** Duplikate, Reihenfolgekonflikte, Clock-Skew, Datenverlust bei Migration,
WorkManager-Verzögerung, Sessionablauf während Queue-Lauf.

**Akzeptanzkriterien**

- zuvor geladene Assignments/Geometrien sind nach Prozessneustart offline lesbar.
- Queue überlebt Neustart; kein Eintrag verschwindet still.
- erfolgreicher Sync merged Serverzustand vor `synced`; Fehler bleiben verständlich sichtbar.
- Retry ist idempotent; lokale Pending-Daten werden durch Refetch nicht verdeckt.

**Offene Backend-Fragen:** Idempotency-Key für jede Mutationsart; 409/Versionierung;
Deltaendpunkte; server authoritative timestamp; maximale Offline-Dauer und Löschregeln.

## 8. Fotos, Notizen und Nachweise

**Ziel:** Transparente lokale Erfassung und bestätigter Upload von Nachweisen.

**Arbeitspakete**

- Kamera-/Photo-Picker-Flow ohne proprietäre Dienste; temporären internen Dateispeicher bauen.
- Proof-Modell für Foto, Notiz, Status, Zeit und optional explizit bestätigte GPS-Koordinate.
- Bildgröße/Format/Rotation/EXIF datenschutzgerecht normalisieren.
- Upload/Multipart und Queue-Anbindung erst nach finalem Backendvertrag implementieren.
- Fortschritt, lokaler Status, Fehler, Retry und Löschung klar unterscheiden.

**Abhängigkeiten:** Phase 7; Proof-/Upload-Endpunkte; Datenschutzfreigabe.

**Risiken:** große Dateien/Speicherdruck; EXIF-Leak; doppelte Uploads; unklare Rechtsgrundlage;
OS-bedingter Verlust temporärer URIs.

**Akzeptanzkriterien**

- Foto/Notiz kann offline lokal gespeichert und später nachvollziehbar synchronisiert werden.
- „Serverseitig gespeichert“ erscheint nur nach bestätigter API-Antwort.
- Logout/Retention entfernt lokale Dateien und Metadaten vollständig.
- Kamera-/Medien-/Standortberechtigungen werden nur kontextbezogen angefragt.

**Offene Backend-Fragen:** Proof-/Issue-Routen; Multipart/JSON-Schema; MIME/Größe; EXIF;
Idempotenz; Zuordnung; Löschung; Retention; Malware-/Bildprüfung; erlaubte GPS-Präzision.

## 9. Berechtigungen, Datenschutz und Hardening

**Ziel:** Autorisierung, Datensparsamkeit und technische Schutzmaßnahmen releasefähig machen.

**Arbeitspakete**

- vollständige `can`-Matrix mit Contract-Tests und Missing=false-Regel.
- Plattformpermission-UX, Datenschutztexte, Datenexport/-löschung und Retention prüfen.
- Logging-/Crash-/Screenshot-/Clipboard-Audit; sensible Daten aus Fehlern entfernen.
- Cookie-/Keystore-, Datenbank-, Backup-, TLS- und Redirect-Hardening testen.
- Dependency-/Lizenz-/SBOM- und verbotene-Google-Runtime-Prüfung in CI integrieren.

**Abhängigkeiten:** Phasen 2–8; Datenschutz-/Security-Review; Backendpolicy.

**Risiken:** serverseitige `can`-Lücken; Geräte ohne hardware-backed Keystore; gerootete Geräte;
transitive Tracking-/Google-Abhängigkeit; unvollständige Datenlöschung.

**Akzeptanzkriterien**

- Keine Aktion wird aus Rollen abgeleitet; 403 bleibt sicher und verständlich.
- Security-Testplan und Dependency-Audit sind grün.
- Keine Secrets/PII/Cookies/GPS/Fotos in Logs, Backups oder Repo-Historie.
- Threat Model und Datenschutzentscheidungen sind dokumentiert und abgenommen.

**Offene Backend-Fragen:** vollständige Policy-/`can`-Abdeckung; Audit-/Löschpflichten;
Account-/Session-Widerruf; Aufbewahrung und Zugriff auf Teamstandorte/Nachweise.

## 10. Tests, CI, Release-Build und F-Droid-taugliche Distribution

**Ziel:** Reproduzierbares, testbares Release ohne Google-Laufzeitdienste.

**Arbeitspakete**

- Unit-, Repository-, Room-Migrations-, MockWebServer-, Compose- und Instrumentationtests.
- Praxisfälle für Offline, Reconnect, 401/403/422, Prozessneustart, GPS und Kamera.
- CI um Release-Lint, Dependency Verification, SBOM, Lizenz- und Secret-Scan erweitern.
- deterministische Release-Konfiguration, R8-Regeln, Signing-Trennung und APK/AAB prüfen.
- F-Droid-Metadaten, reproduzierbare Source-Tarballs und scannerkompatible Versionierung erstellen.

**Abhängigkeiten:** alle vorherigen Phasen; F-Droid-Policy; Release-Signing außerhalb Repo.

**Risiken:** native MapLibre-Binaries/ABIs erschweren Reproduzierbarkeit; nicht-freie
transitive Dependencies; flaky Sensor-/UI-Tests; Signing- und Versionierungsfehler.

**Akzeptanzkriterien**

- Clean Build mit Tests/Lint/Release auf CI; Dependency Verification ist aktiv.
- Runtime-Graph ist frei von verbotenen/proprietären Diensten.
- Release enthält keine Secrets/Debugdaten und ist installierbar.
- F-Droid-Buildrezept ist aus öffentlichem Source reproduzierbar dokumentiert.

**Offene Backend-Fragen:** unterstützte Clientversionen/API-Kompatibilitätsfenster;
Staging für automatisierte Tests; Release-Health-Endpunkt und Wartungsmodus.

## Empfohlener nächster Schritt

Phase 2: ein eng begrenzter Sanctum-Cookie-/CSRF-Spike gegen eine sichere Testinstanz.
Ohne diesen Nachweis würden Login, Offline-Sync und Logout auf einer unbestätigten
Sicherheitsannahme aufbauen.
