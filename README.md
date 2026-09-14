# FYRO: RISE

Eigenständiges, deutschsprachiges Minecraft-RPG mit einer Vanilla-Intro-Cutscene und verpflichtendem magischem Buch-GUI zur ersten Charakterwahl für **Paper 26.2** mit Gilden-Tags über Spielern und im Chat, nativer Level-/EP-Leiste, sichtbaren Gegnerleveln, zwei originalen Fraktionen, vier Klassen, drei Fähigkeiten je Klasse, Level 1–60, Quests, Gruppen, Gilden, eigener Wirtschaft, Ausrüstung, Grabkrypta, Bossen und Fraktions-PvP.

## Installation

Die fertige `FYRO-RISE-1.3.0.jar` in den Ordner `plugins` laden und Paper 26.2 mit Java 25 neu starten. Beim ersten Betreten läuft ohne Client-Download eine kurze FYRO-Kamerafahrt mit Einblendungen, Sounds und Partikeln. Danach öffnet sich automatisch das Buch des Aufstiegs: Orden wählen, Klasse wählen und auf „AUSWAHL ABSCHLIESSEN“ klicken. Details stehen in `INSTALLATION_DE.md`.

Feindliche Mobs tragen dauerhaft Name und Level über dem Kopf. Ihre Lebenspunkte, ihr Schaden und ihre Belohnungen steigen mit dem Level. Ist ein Spieler mindestens drei Level höher als sein Gegner, erhält er für diesen Gegner keine EP mehr.

Nach der Klassenwahl liegen die drei Fähigkeiten als gebundene Runen auf den Hotbarplätzen 7, 8 und 9 und lösen beim Anwählen sofort aus. Für Y/X/B können diese drei Hotbarplätze ohne Mod einmal in den Minecraft-Steuerungen entsprechend belegt werden. Jede Klasse besitzt eigene mehrstufige Kampfanimationen mit Partikelstrahlen, Flächenringen und Sounds.

Ein großer violetter FYRO-EP-Balken zeigt oben im Bild das aktuelle Level sowie die exakten EP bis zum nächsten Aufstieg. Die originale Minecraft-EP-Leiste unten bleibt zusätzlich synchron.

## Build und Test

```bash
mvn clean verify
```

GitHub Actions führt Unit-Tests und zusätzlich einen echten Starttest mit Paper 26.2 durch.
