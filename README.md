# CityBuild (Paper 26.2, Java 25)

## Bauen
Am einfachsten ueber GitHub: Die Datei `.github/workflows/build.yml` baut die Jar automatisch
(Reiter "Actions" -> Eintrag oeffnen -> unten bei "Artifacts" herunterladen).

Lokal: JDK 25 und Maven, dann `mvn clean package`. Die Jar liegt danach in `target/CityBuild.jar`.
Falls Maven die `paper-api`-Version `[26.2.build,26.3.build)` nicht aufloest, trage in der `pom.xml`
die genaue Version von https://docs.papermc.io/paper/dev/project-setup/ ein.

## Befehle
| Befehl | Funktion |
|---|---|
| /money [Spieler], /pay, /baltop | Geld, Ueberweisen, Rangliste |
| /eco give/take/set | Admin-Economy |
| /plot auto, claim, home, visit, list, info | Grundstuecke in eigener Plot-Welt (Gitter mit Strassen, wie PlotSquared) |
| /plot add, remove, deny, undeny, setowner | Mitglieder, Aussperren, Besitzer wechseln |
| /plot clear, delete | Leeren / abgeben |
| /plot merge, unmerge [Richtung] | Grundstuecke verbinden / trennen |
| /plot flag set/remove/list | Einstellungen: pvp, mob-spawning, fire, explosion, visitor-use, greeting, farewell |
| /plot biome <Biom> | Biom aendern |
| /sethome, /home, /delhome, /homes | Homes |
| /spawn, /setspawn | Spawn |
| /warp, /warps, /setwarp, /delwarp | Warps |
| /shop | Adminshop als Menue (Links = kaufen, Rechts = verkaufen) |
| /sell hand, all, list | Items schnell verkaufen |
| /daily | Taegliche Belohnung mit Serie |
| /farmwelt, /nether, /end | Zufalls-Teleport in Farmwelt und Nether, Teleport ins End |
| /clan create, invite, accept, leave, kick, info, list, disband | Clans |
| /cc <Nachricht> | Clan-Chat |
| /rang <Spieler> <Rang> | Rang vergeben (Admin) |
| /citybuild reload | Config neu laden |

## Wichtige Permissions
- `citybuild.admin` - Spawn/Warps setzen, Raenge vergeben, Reload
- `citybuild.eco` - /eco
- `citybuild.bypass` - Schutz ignorieren (Teammitglieder)
- `citybuild.plots.unlimited` - unbegrenzt Grundstuecke
- `citybuild.homes.<Zahl>` - mehr Homes (z.B. citybuild.homes.10)
- `citybuild.tpdelay.bypass` - kein Teleport-Delay

## Anpassen
Alles steht in `plugins/CityBuild/config.yml`: Preise, Limits, Raenge, Scoreboard, Shop-Kategorien, Welten.
Aenderungen uebernimmst du mit `/citybuild reload`. Hast du eine alte config.yml, loesche sie vor dem
ersten Start der neuen Version, damit die neuen Eintraege angelegt werden.
Daten liegen in `balances.yml`, `plots.yml`, `homes.yml`, `warps.yml`, `ranks.yml`, `clans.yml`, `daily.yml`.

## Plot-Welt
Beim ersten Start erstellt das Plugin die Welt `plotwelt` (Name in der config.yml). Groesse, Strassenbreite und Hoehe
nach der Erstellung nicht mehr aendern. Mit `/plot welt` kommen Spieler dorthin, mit `/plot auto` bekommen sie ein Grundstueck.
