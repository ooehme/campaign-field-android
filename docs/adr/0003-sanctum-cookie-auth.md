# ADR 0003: Laravel Sanctum Cookie-/CSRF-Authentifizierung

- Status: Akzeptiert und durch Phase-2-Spike bestätigt
- Datum: 2026-07-21

## Kontext

Die bestehende API authentifiziert `campaign-field` über Laravel-Sanctum-Session-Cookies.
Ein mobiles Bearer-Token würde einen neuen Auth-Vertrag, zusätzliche Secrets und andere
Widerrufs-/Speicherregeln einführen.

## Entscheidung

Die Android-App behält Session-Cookies, CSRF-Cookie und `X-XSRF-TOKEN` bei. Ein zentraler
OkHttp-Client verwaltet Cookies persistent und verschlüsselt; der Schlüssel liegt im
Android Keystore. Ein `Authorization`-Header wird weder erzeugt noch toleriert.

Die serverseitig freigegebene Stateful-Origin wird getrennt von der API-Basis-URL als
`CAMPAIGN_FIELD_SANCTUM_CLIENT_ORIGIN` konfiguriert und als `Origin`/`Referer` gesendet.
Netzwerkziele bleiben trotzdem strikt auf die HTTPS-Origin der API begrenzt.

Login: CSRF-Cookie laden, `/login` senden, Session über `/user` bestätigen. 401 und
Logout löschen Cookies und alle lokalen sensiblen Fachdaten.

## Konsequenzen

- Cookie-Origin, SameSite/CORS, Rotation und Prozessneustart müssen früh gegen das Backend getestet werden.
- Der Cookie-Jar ist sicherheitskritisch und braucht Integrations- sowie Migrationstests.
- Offline-Mutationen können nur mit gültiger Session synchronisiert werden; 401 stoppt die Queue.
- Eine spätere Tokenauthentifizierung erfordert ein neues ADR und Backendfreigabe.

Die konkreten Testergebnisse und verbliebenen Backend-Hardening-Punkte stehen in
[`SANCTUM-SPIKE.md`](../SANCTUM-SPIKE.md).
