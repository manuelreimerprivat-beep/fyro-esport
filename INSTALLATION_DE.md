# FYRO: RISE – Installation bei MC-Host24

## Voraussetzungen

- Minecraft Java Edition, Paper 26.2 und Java 25
- 2 CPU-Kerne, 6 GB RAM, mindestens 10 GB Speicher
- Zunächst 20 Spieler; erst nach Lasttests auf 35 und 50 erhöhen

## Installation

1. Server stoppen und im MC-Host24-Panel ein Backup erstellen.
2. Im Panel Paper 26.2 sowie Java 25 auswählen.
3. `FYRO-RISE-1.0.0.jar` in den Ordner `plugins` hochladen.
4. Server vollständig neu starten; niemals `/reload` verwenden.
5. In der Konsole muss `FYRO-RISE v1.0.0 aktiviert` stehen.
6. Im Spiel `/rise help` ausführen.

## Erster Funktionstest

```text
/rise choose faction aurora
/rise choose class krieger
/rise profile
/rise quest list
/rise quest accept erste_schatten
/rise skill 1
/rise pvp on
```

Operator-Test:

```text
/rise admin setlevel DEINNAME 10
/rise admin givegear DEINNAME 2
/rise admin spawnboss
/rise dungeon crypt
```

## Hauptbefehle

| Bereich | Befehl |
|---|---|
| Hilfe/Profil | `/rise help`, `/rise profile` |
| Fraktion | `/rise choose faction aurora\|obsidian` |
| Klasse | `/rise choose class krieger\|magier\|waldlaeufer\|priester` |
| Fähigkeit | `/rise skill 1\|2\|3` |
| Quests | `/rise quest list\|accept <id>` |
| Gruppe | `/rise party create\|invite\|accept\|leave\|info` |
| Gilde | `/rise guild create\|invite\|accept\|leave\|info\|deposit` |
| Wirtschaft | `/rise balance`, `/rise pay <Spieler> <Betrag>` |
| Dungeon/PvP | `/rise dungeon crypt`, `/rise pvp on\|off` |

## Leistung

Falls einstellbar: `-Xms2G -Xmx5G`. In `server.properties`: `view-distance=8`, `simulation-distance=6`. Teste mit 20, dann 35, dann 50 gleichzeitigen Spielern jeweils mindestens 60 Minuten. Prüfe TPS, MSPT, CPU und RAM. 50 Spieler sind ein Zielwert und keine Garantie.

## Daten und Backups

Alle Daten liegen unter `plugins/FYRO-RISE/`. Sichere diesen Ordner täglich und bearbeite YAML-Dateien nur bei gestopptem Server. `fyrorise.use` gilt für Spieler, `fyrorise.admin` nur für Operatoren/Administratoren.
