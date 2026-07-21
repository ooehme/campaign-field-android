# ADR 0001: Native Android mit Kotlin und Jetpack Compose

- Status: Akzeptiert
- Datum: 2026-07-21

## Kontext

`campaign-field` ist eine React/PWA. Die Neuimplementierung soll Android-Lifecycle,
Offline-Arbeit, Kamera, Standort und Hintergrund-Sync nativ abbilden. React-Komponenten
sollen nicht übertragen werden.

## Entscheidung

Die App wird nativ in Kotlin mit Jetpack Compose, Material 3, Navigation Compose,
Coroutines und Flow implementiert. Fachlogik und API-Verträge werden aus der Referenz
neu modelliert. Es gibt keine WebView und keinen React-Native-/Capacitor-Port.

## Konsequenzen

- Plattformfunktionen und Lifecycle sind direkt kontrollierbar.
- UI und Datenhaltung werden neu implementiert; dadurch höherer Anfangsaufwand.
- AndroidX/Jetpack aus `google()` sind als Build-/Framework-Abhängigkeiten zulässig.
- Ein `app`-Modul genügt zunächst; Paketgrenzen erlauben spätere Modularisierung.
