# FYRO: RISE

Eigenständiges, deutschsprachiges Minecraft-RPG mit einer Vanilla-Intro-Cutscene und verpflichtendem magischem Buch-GUI zur ersten Charakterwahl für **Paper 26.2** mit Gilden-Tags über Spielern und im Chat, nativer Level-/EP-Leiste, zwei originalen Fraktionen, vier Klassen, drei Fähigkeiten je Klasse, Level 1–60, Quests, Gruppen, Gilden, eigener Wirtschaft, Ausrüstung, Grabkrypta, Bossen und Fraktions-PvP.

## Installation

Die fertige `FYRO-RISE-1.2.0.jar` in den Ordner `plugins` laden und Paper 26.2 mit Java 25 neu starten. Beim ersten Betreten läuft ohne Client-Download eine kurze FYRO-Kamerafahrt mit Einblendungen, Sounds und Partikeln. Danach öffnet sich automatisch das Buch des Aufstiegs: Orden wählen, Klasse wählen und auf „AUSWAHL ABSCHLIESSEN“ klicken. Details stehen in `INSTALLATION_DE.md`.

## Build und Test

```bash
mvn clean verify
```

GitHub Actions führt Unit-Tests und zusätzlich einen echten Starttest mit Paper 26.2 durch.
