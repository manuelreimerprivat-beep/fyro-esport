# FYRO: RISE – Installation bei MC-Host24

## Voraussetzungen

- Minecraft Java Edition, Paper 26.2 und Java 25
- 2 CPU-Kerne, 6 GB RAM, mindestens 10 GB Speicher
- Zunächst 20 Spieler; erst nach Lasttests auf 35 und 50 erhöhen

## Installation

1. Server stoppen und im MC-Host24-Panel ein Backup erstellen.
2. Im Panel Paper 26.2 sowie Java 25 auswählen.
3. Eine vorhandene ältere FYRO-RISE-JAR löschen und `FYRO-RISE-1.3.0.jar` in den Ordner `plugins` hochladen.
4. Server vollständig neu starten; niemals `/reload` verwenden.
5. In der Konsole muss `FYRO-RISE v1.3.0 aktiviert` stehen.
6. Im Spiel `/rise help` ausführen.

## Erster Funktionstest

```text
Beim ersten Betreten:
1. Im Buch des Aufstiegs einen Orden anklicken.
2. Auf der nächsten Seite eine Klasse anklicken.
3. Auswahl kontrollieren und "AUSWAHL ABSCHLIESSEN" anklicken.
4. Danach im Chat ausführen:

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

## Intro-Cutscene

Beim ersten Betreten läuft eine ungefähr zehn Sekunden lange Vanilla-Cutscene. Sie benötigt weder Mod noch Resourcepack. Anschließend öffnet sich das Buch des Aufstiegs. In `config.yml` kann die Dauer über `intro.duration-seconds` zwischen 6 und 20 Sekunden eingestellt werden. Mit `intro.play-on-every-join: true` läuft sie bei jedem Beitritt.

## Anzeigen

Nach dem Beitritt zu einer Gilde steht `[Gildenname]` im Chat und über dem Charakter. Die Zahl an der grünen Erfahrungsleiste ist das FYRO-Level; die Leiste zeigt den Fortschritt bis zum nächsten Level. Zusätzlich zeigt ein deutlich dickerer violetter FYRO-EP-Balken oben am Bildschirm das Level und die exakten EP. Er kann mit `display.thick-experience-bar: false` deaktiviert werden.

## Gegnerlevel

Jeder feindliche Mob zeigt dauerhaft `[Level X] Name` über dem Kopf. Mit wachsender Entfernung vom Weltspawn steigen die Level. Im Nether beginnen Gegner standardmäßig bei Level 20 und im End bei Level 40. Dungeonwachen orientieren sich am Level des Gruppenleiters; der Boss liegt drei Level darüber. Lebenspunkte, Angriffsschaden, EP und Münzen skalieren mit dem Gegnerlevel.

Ist ein Spieler mindestens drei Level höher als der besiegte Gegner, gibt dieser Gegner keine EP. Münzen und Questfortschritt bleiben erhalten. Beispiel: Ein Spieler auf Level 10 erhält von Gegnern bis Level 7 keine EP, von Gegnern ab Level 8 dagegen schon.

Alle Werte können unter `mob-levels` in `plugins/FYRO-RISE/config.yml` angepasst werden. Nach Änderungen den Server vollständig neu starten.

## Fähigkeiten über die Tastatur

Die drei Klassenfähigkeiten erscheinen nach der Charakterwahl als gebundene Runen auf den Hotbarplätzen 7, 8 und 9. Das Anwählen eines Platzes löst die Fähigkeit sofort aus; die Rune kann nicht versehentlich verschoben oder weggeworfen werden. Belegte Gegenstände werden vorher sicher in einen freien Inventarplatz verschoben.

Ein Paper-Server kann die Tastenbelegung eines Minecraft-Clients technisch nicht selbst verändern. Damit die Fähigkeiten auf Y, X und B liegen, stellt jeder Spieler einmal unter `Optionen → Steuerung` Folgendes ein:

| Fähigkeit | Minecraft-Belegung |
|---|---|
| Y – Fähigkeit 1 | Hotbarplatz 7 auf Y |
| X – Fähigkeit 2 | Hotbarplatz 8 auf X |
| B – Fähigkeit 3 | Hotbarplatz 9 auf B |

Hierfür wird kein Mod und kein zusätzlicher Download benötigt. Alternativ funktionieren immer die normalen Tasten 7, 8 und 9 sowie weiterhin `/rise skill 1|2|3`.

## Hauptbefehle

| Bereich | Befehl |
|---|---|
| Hilfe/Profil | `/rise help`, `/rise profile` |
| Orden und Klasse | Auswahl ausschließlich über das Buch des Aufstiegs |
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
