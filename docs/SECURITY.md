# Sicherheit und Datenschutz

## Schutzbedarf

Session-Cookies, personenbezogene Assignment-Daten, Teamstandorte, Notizen, Fotos und
Nachweise sind sensibel. Das öffentliche Repository und Build-Artefakte dürfen keine
produktiven Zugangsdaten oder internen Entwicklungsdaten enthalten.

## Authentifizierung und Cookies

- Ausschließlich Sanctum-Session-Cookies; kein Bearer-Token und kein Token-Fallback.
- Persistentes OkHttp-`CookieJar` mit Host-, Path-, Secure-, HttpOnly-, SameSite- und
  Ablaufsemantik. Es werden nur Cookies der konfigurierten API-/Sanctum-Origin akzeptiert.
- Cookie-Werte werden verschlüsselt gespeichert. Ein zufälliger AES-GCM-Schlüssel wird
  im Android Keystore erzeugt (hardware-backed, wenn verfügbar); Nonce und Ciphertext
  liegen in app-internem Storage.
- `XSRF-TOKEN` wird nur für zulässige schreibende Requests URL-dekodiert in
  `X-XSRF-TOKEN` kopiert. `Authorization` wird aktiv entfernt.
- Kein Cookie, Passwort, CSRF-Wert oder Response-Body mit sensiblen Daten wird geloggt.

Der Phase-2-Spike bestätigt Domain, SameSite-Verhalten, Cookie-Persistenz nach
Prozessneustart, CSRF-Rotation und Logout gegen eine sichere Testumgebung. `Secure`
bleibt vor einem Produktivbetrieb eine verbindliche Backend-Voraussetzung.

## Lokale Daten

- Room-Datenbank und Foto-Dateien liegen ausschließlich im internen App-Speicher.
- Backups sind im Manifest deaktiviert; sensible Daten werden nicht in externe/shared
  Ablagen geschrieben.
- Logout und 401 löschen Cookies, Benutzer, fachlichen Cache, Queue, Nachweise,
  temporäre Bilder, Fehlermeldungen mit Payloadbezug und Standortzustand.
- Für verbleibende Offline-Daten werden Aufbewahrungsfristen und eine manuelle
  Löschfunktion festgelegt.
- Room bleibt vorerst ohne zusätzliche Anwendungsschicht-Verschlüsselung: App-Sandbox,
  Gerätespeicherverschlüsselung und deaktivierte Backups bilden den Basisschutz; Cookies
  bleiben separat Keystore-verschlüsselt. Phase 9 prüft diese Entscheidung erneut gegen
  das Threat Model für entsperrte oder kompromittierte Geräte.

## Netzwerk

- Nur HTTPS; Cleartext ist im Manifest deaktiviert.
- Keine vertrauensaufweichende `TrustManager`-/HostnameVerifier-Konfiguration.
- Timeouts, Response-Größen und Redirects werden begrenzt; Redirects dürfen Cookies
  oder CSRF-Header nicht an fremde Origins weiterreichen.
- Fehlertexte werden für Nutzer normalisiert; Rohantworten und Request-Payloads werden
  nicht protokolliert.
- Certificate Pinning ist vorerst nicht vorgesehen, damit Schlüsselrotation und
  F-Droid-Builds nicht unnötig fragil werden; eine spätere Einführung benötigt ein ADR.

## Standort, Sensor und Fotos

- Standort nur nach informierter Nutzeraktion und nur für konkrete Field-Aktionen.
- Keine Hintergrundortung, kein stilles Teamtracking, keine Koordinaten in Logs/Crashreports.
- Teamstandorte nur bei aktivem Assignment oder explizitem `can`-Flag.
- Kamera/Fotozugriff wird erst im Nachweisflow angefragt. Uploads sind sichtbar und
  gelten erst nach Serverbestätigung als erfolgreich.
- EXIF-Metadaten werden vor Upload geprüft beziehungsweise entfernt, sofern sie nicht
  Teil des bestätigten Backend-Vertrags sind.

## Logging und Telemetrie

Keine Analytics-, Crash- oder Push-SDKs im Bootstrap. Release-Logging verwendet eine
kleine Allowlist technischer Ereignisse ohne IDs, Payloads, URLs mit Query-Parametern,
Cookies, Koordinaten oder Dateiinhalte. Debug-Logging folgt denselben Datengrenzen.

## Lieferkette und Release

- Versionen sind im Catalog fixiert; keine dynamischen Versionen.
- Gradle Wrapper Validation, Tests, Lint und Debug-Build laufen in CI.
- Vor Release folgen SBOM/Dependency-Audit, Lizenzprüfung, reproduzierbarer Build,
  getrenntes Signing und F-Droid-Metadaten.
- Google-Play-Service- und Firebase-Artefakte werden automatisiert aus dem Runtime-
  Dependency-Graph ausgeschlossen beziehungsweise als CI-Verstoß behandelt.

## Security-Stand nach Phase 3

- Cookie- und CSRF-Integrationstests gegen Testbackend sind erfolgreich.
- Prozessneustart, Sessionablauf, 401, Logout und Cookie-Rotation sind getestet;
  403/422/5xx/Transportfehler werden ohne Response-Rohdaten normalisiert.
- Produktiver Code enthält kein Logging für Passwort, Cookies oder Response-Payloads.
- 401 und Logout bereinigen Cookies, Profil, App-Datenbank, Queue-Arbeit, Fotos,
  temporären Cache und Standortzustand über einen gemeinsamen lokalen Cleaner.
