# Sanctum-Cookie-/CSRF-Spike

Status: abgeschlossen am 22.07.2026 gegen die sichere Testinstanz. URLs, Zugangsdaten und
Cookie-Werte sind nicht versioniert.

## Bestätigter Ablauf

1. `GET /sanctum/csrf-cookie` wird an der Origin-Wurzel aufgerufen.
2. `POST /api/login` sendet JSON, Cookies und den URL-dekodierten `XSRF-TOKEN` als
   `X-XSRF-TOKEN`; ein `Authorization`-Header wird aktiv entfernt.
3. Erst ein erfolgreiches `GET /api/user` bestätigt die Session.
4. Ein neu erzeugter Cookie-Jar liest die persistierte Session und bestätigt sie erneut
   über `/user` (simulierter Prozessneustart).
5. `POST /api/logout` beendet die Session. Anschließend sind lokale Cookies gelöscht und
   `/user` antwortet mit 401.

Der Live-Test liest Basis-URL, Stateful-Origin und Testkonto ausschließlich aus
`CAMPAIGN_FIELD_TEST_API_BASE_URL`,
`CAMPAIGN_FIELD_TEST_SANCTUM_CLIENT_ORIGIN`, `CAMPAIGN_FIELD_TEST_EMAIL` und
`CAMPAIGN_FIELD_TEST_PASSWORD`. Ohne diese Variablen wird er übersprungen.

## Client-Sicherheitsparameter

- API-Basis-URL und Stateful-Origin müssen HTTPS verwenden.
- Nur Requests an exakt dieselbe API-Origin (Schema, Host und Port) sind erlaubt.
- Redirects werden nicht automatisch verfolgt, damit Cookie- und CSRF-Header nicht an
  fremde Ziele gelangen.
- `Accept: application/json`, `Origin` und `Referer` werden zentral gesetzt; vom Aufrufer
  gesetzte Bearer- und CSRF-Header werden entfernt beziehungsweise sicher ersetzt.
- Cookies werden mit Domain-, Path-, Ablauf-, Secure-, HttpOnly- und SameSite-Attributen
  serialisiert. SameSite wird erhalten; da OkHttp kein Browser ist, bilden die strikte
  Origin-Sperre und die konfigurierte Stateful-Origin die entscheidende Senderegel.
- Der komplette Cookie-Datensatz wird mit AES-256-GCM verschlüsselt. Der nicht
  exportierbare Schlüssel liegt im Android Keystore, Nonce und Ciphertext im internen
  `SharedPreferences`-Speicher.
- 401 und Logout entfernen synchron sowohl den verschlüsselten Blob als auch den
  Keystore-Alias. Es gibt keinen Logger für Header, Body, Passwort oder Cookies.

## Beobachtete Backend-Policy

- Die konfigurierte Web-Stateful-Origin muss als `Origin` und `Referer` gesendet werden.
  Ohne oder mit einer anderen Origin schlägt der Login derzeit serverseitig mit 500 fehl.
- CSRF- und Session-Cookie gelten zwei Stunden, verwenden den gemeinsamen Parent-Domain-
  Scope, Path `/` und `SameSite=Lax`; das Session-Cookie ist `HttpOnly`.
- Das Backend setzt derzeit bei beiden Cookies kein `Secure`-Attribut. Der Client sendet
  wegen HTTPS-Pflicht und exakter Origin-Sperre trotzdem nie an Cleartext-Ziele. Vor einem
  produktiven Release sollte das Backend `Secure` setzen.
- CORS ist für OkHttp nicht browserseitig wirksam. Die Stateful-Origin-Prüfung des
  Backends bleibt für die Auswahl der Session-Authentifizierung dennoch erforderlich.

## Automatisierte Nachweise

- MockWebServer: Login, CSRF-Dekodierung und -Rotation, Session-Cookie, 401, Logout,
  Redirects, Fremd-Origin und Entfernen von `Authorization`.
- JVM: Cookie-Domain/Path/Secure/Ablauf, Prozessneustart, AES-GCM-Roundtrip und
  Manipulationserkennung.
- Android-API-36-Emulator: echter Android-Keystore, verschlüsselter interner Speicher,
  Jar-Neuaufbau sowie vollständiges Löschen von Blob und Keystore-Alias.
- Live-Testinstanz: CSRF, Login, `/user`, Jar-Neuaufbau, erneutes `/user`, Logout und 401.

## Verbleibende Backend-Aufgaben vor Produktion

1. Cookies mit `Secure` ausliefern.
2. Fehlende oder falsche Stateful-Origin als sicheren 4xx-Fehler statt 500 behandeln.
3. Eine eigene native Client-Origin festlegen oder die bestehende Freigabe verbindlich
   als gemeinsamen Clientvertrag dokumentieren.
