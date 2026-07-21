# ADR 0002: Keine Google Play Services zur Laufzeit

- Status: Akzeptiert
- Datum: 2026-07-21

## Kontext

Die App soll auf Geräten ohne Google-Komponenten und perspektivisch über F-Droid
verteilbar sein. Standort, Karte, Push und Geräteprüfung dürfen daher nicht von
proprietären Google-Laufzeitdiensten abhängen.

## Entscheidung

Verboten sind Google Play Services, Firebase, Google Maps, FCM, Play Integrity und der
Fused Location Provider. Karten verwenden MapLibre Native Android; Standort verwendet
Android `LocationManager`. AndroidX, Jetpack und Build-Artefakte aus `google()` bleiben erlaubt.

## Konsequenzen

- Kein Google-basierter Push im aktuellen Zielbild; eine freie Alternative benötigt ein neues ADR.
- Providerwahl, Genauigkeit und Energieverbrauch des Standorts werden selbst gesteuert.
- CI muss den Runtime-Dependency-Graph auf verbotene Artefakte prüfen.
- Basemap-/Tile-Infrastruktur, Attribution und Offline-Nutzung müssen separat geklärt werden.
