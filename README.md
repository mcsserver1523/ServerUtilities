# ServerUtilities

ServerUtilities ist ein Minecraft-Plugin für Paper und Purpur `1.21.11`. Es fügt ein Scoreboard, ein Economy-System, einen dynamischen Markt, eine Serverkasse, Spenden und Admin-Werkzeuge hinzu.

## Features

- Sidebar-Scoreboard mit Spielername, Geld, Todeszahl und Serverkasse
- Fast alle Plugin-Bereiche sind über `config.yml` einzeln steuerbar
- Economy-System mit Startguthaben
- Dynamischer Markt mit Kategorien, Kauf/Verkauf, Preisentwicklung und Preisgrafik
- Marktpreise steigen beim Kaufen und fallen beim Verkaufen
- Leichte Preis-Stabilisierung zurück zum Basispreis
- Konfigurierbarer Verkaufsabschlag, der in die Serverkasse geht
- Serverkasse mit wöchentlicher fairer Auszahlung
- Spenden-System für Spieler und Serverkasse mit klickbarer Bestätigung
- Admin-GUIs für Markt und Einstellungen
- Spieler-History mit konfigurierbarer Aufbewahrungszeit
- Serverkassen-History für Ein- und Auszahlungen

## Anforderungen

- Paper oder Purpur `1.21.11`
- Java `21`
- Gradle Wrapper ist im Projekt enthalten

## Build

```bash
./gradlew build
```

Die fertige Jar liegt danach hier:

```text
build/libs/ServerUtilities-1.0.0.jar
```

Diese Datei in den `plugins`-Ordner des Servers kopieren und den Server neu starten.

## Dateien

Im Serverordner werden diese Dateien genutzt:

```text
plugins/ServerUtilities/config.yml
plugins/ServerUtilities/players.yml
plugins/ServerUtilities/history.yml
plugins/ServerUtilities/prices.yml
```

Die Standard-Config im Repository liegt hier:

```text
src/main/resources/config.yml
```

Wenn auf dem Server bereits eine `plugins/ServerUtilities/config.yml` existiert, wird sie nicht automatisch überschrieben. Zum kompletten Zurücksetzen kann die Server-Config gelöscht werden, damit sie beim nächsten Start neu erstellt wird.

## Config

Wichtige Bereiche in `config.yml`:

```yaml
features:
  scoreboard: true
  market: true
  deaths: true
  history: true
  donations: true
  server-bank: true
  settings-gui: true
  market-graphs: true
  balance-graphs: true
  sell-gui: true
  sellall: true
  direct-market-commands: true
  market-search: true

scoreboard:
  title: "&6ServerUtilities"
  show-player: true
  show-stats: true
  show-money: true
  show-deaths: true
  show-community: true
  show-server-bank: true

economy:
  starting-balance: 1000.0
  allow-negative-admin-withdraw: false

history:
  trade-retention-hours: 48
  max-trade-entries: 100
  default-command-amount: 25
  balance-history-max-points: 500

server-bank:
  enabled: true
  first-join-deposit: 1000.0
  weekly:
    enabled: true
    day: MONDAY
    hour: 0
    minute: 0
    timezone: Europe/Berlin
    deposit-per-player-after-payout: 1000.0
    clear-history-after-refill: true
  history:
    max-entries: 500
    default-command-amount: 25

donations:
  enabled: true
  player-tax-percent: 10.0
  direct-server-bank-tax-percent: 0.0
  broadcast-server-bank-donations: true
  require-click-confirmation: true

commands:
  balance-enabled: true
  checkbalance-enabled: true
  checkhistory-enabled: true
  tode-enabled: true
  checktode-enabled: true
  resetmarket-enabled: true
  setmarket-enabled: true
  settings-enabled: true
  serverbank-enabled: true

market:
  baseprice_high: 10000.0
  baseprice_low: 0.05
  price-change-percent-per-stack: 0.035
  min-price: 0.01
  sell-tax-percent: 10.0
  price-history-max-points: 500
  stabilization:
    enabled: true
    interval-hours: 168
    factor-percent: 10.0
  gui:
    show-sell-tax-lore: true
```

Die alten `settings.*`-Keys bleiben als Fallback erhalten. Neue Server sollten aber `features.*` nutzen.

### Konfigurierbare Bereiche

| Bereich | Config |
| --- | --- |
| Feature-Toggles | `features.*` |
| Scoreboard-Titel und Zeilen | `scoreboard.*` |
| Startgeld und Admin-Abbuchungen | `economy.*` |
| Spieler-History | `history.*` |
| Serverkasse, Wochenzeit, Wochenbetrag, Logs | `server-bank.*` |
| Spendensteuer, Broadcasts, Klickbestätigung | `donations.*` |
| Admin-Befehle einzeln aktivieren/deaktivieren | `commands.*` |
| Marktpreise, Verkaufsteuer, Preisstabilisierung | `market.*` |
| Kategorienamen, Icons und Items | `market.categories.*` |

Market-Items werden pro Kategorie eingetragen. Der Wert hinter dem Item ist die Seltenheit beziehungsweise die virtuelle Marktmenge für die Basispreis-Berechnung:

```yaml
market:
  categories:
    nether_end:
      items:
        minecraft:netherrack: 500000
        minecraft:ancient_debris: 20
```

Je kleiner der Wert, desto teurer wird das Item.

## Markt

Der Markt wird mit `/market` geöffnet. Er besteht aus Kategorien:

- Steinbruch und Erze
- Holzfäller
- Nether und End
- Farmer und Natur
- Wasser und Fischen
- Farben und Deko
- Verschiedenes

Items können gekauft und verkauft werden. Die Verkaufssteuer ist konfigurierbar:

```yaml
market:
  sell-tax-percent: 10.0
```

Direktes Handeln ist auch möglich:

```text
/market buy <item> <anzahl>
/market sell <item> <anzahl>
/market search <item>
```

`/market search <item>` öffnet direkt das Handels-GUI für das Item, wenn es im Markt verfügbar ist.

## Serverkasse

Die Serverkasse sammelt Community-Geld:

- Die konfigurierte Verkaufssteuer geht in die Serverkasse
- Die konfigurierte Spendensteuer geht in die Serverkasse
- Spenden direkt an die Serverkasse gehen vollständig in die Serverkasse
- Beim ersten Join eines Spielers wird der konfigurierte Betrag in die Serverkasse eingezahlt

Jeden Montag um `00:00`:

1. Die aktuelle Serverkasse wird fair an alle gespeicherten Spieler verteilt.
2. Danach wird die Serverkasse auf `deposit-per-player-after-payout * Anzahl gespeicherter Spieler` gesetzt.
3. Danach werden die Serverkassen-Logs für Ein- und Auszahlungen gelöscht.

Damit liegt jede Woche ein konfigurierbarer Betrag pro Spieler in der Serverkasse für die nächste Auszahlung bereit.

## Spenden

Spieler können Geld spenden. Vor der Ausführung muss die Spende per klickbarer Chat-Nachricht bestätigt werden.

```text
/donate <spieler> <amount>
/donate player <spieler> <amount>
/donate serverkasse <amount>
```

Beim Hover über die Bestätigung sieht der Spieler:

- wie viel Geld beim Zielspieler ankommt
- wie viel Geld in die Serverkasse geht
- wie hoch der Gesamtbetrag ist

Bei Spieler-Spenden wird `donations.player-tax-percent` an die Serverkasse abgeführt.

## Spieler-Befehle

| Befehl | Beschreibung |
| --- | --- |
| `/market` | Öffnet den Markt |
| `/market buy <item> <anzahl>` | Kauft ein Market-Item direkt |
| `/market sell <item> <anzahl>` | Verkauft ein Market-Item direkt |
| `/market search <item>` | Öffnet das Handels-GUI für ein Item |
| `/sell` | Öffnet ein Verkaufs-GUI |
| `/sellall` | Legt alle verkaufbaren Items automatisch in das Verkaufs-GUI |
| `/donate <spieler> <amount>` | Spendet Geld an einen Spieler |
| `/donate player <spieler> <amount>` | Alternative Spieler-Spende |
| `/donate serverkasse <amount>` | Spendet Geld an die Serverkasse |

## Admin-Befehle

Admin-Befehle benötigen die Permission:

```text
serverutilities.admin
```

| Befehl | Beschreibung |
| --- | --- |
| `/balance <player> <set\|add\|withdraw\|remove> <amount>` | Ändert den Kontostand eines Spielers. `withdraw` zahlt an den Admin aus, `remove` löscht Geld |
| `/balance <player> <amount>` | Kurzform für `set` |
| `/checkbalance <player>` | Zeigt den Kontoverlauf als Grafik |
| `/checkhistory <player> [amount]` | Zeigt Trade-History für den konfigurierten Zeitraum |
| `/tode <spieler> <value>` | Setzt die Todeszahl |
| `/checktode <spieler>` | Zeigt die Todeszahl |
| `/resetmarket [item]` | Setzt Marktpreise für ein Item oder alle Items zurück |
| `/setmarket` | Öffnet den Markt im Admin-Modus |
| `/settings` | Öffnet das Einstellungs-GUI |
| `/serverbank <set\|add\|withdraw\|remove> <amount>` | Ändert die Serverkasse. `withdraw` zahlt an den Admin aus, `remove` löscht Geld |
| `/serverbank in [amount]` | Zeigt Einzahlungen in die Serverkasse |
| `/serverbank out [amount]` | Zeigt Auszahlungen aus der Serverkasse |

## Admin-Markt

Mit `/setmarket` wird der Markt im Admin-Modus geöffnet.

- Item mit dem Cursor in eine Kategorie legen: Item wird dieser Kategorie hinzugefügt
- Linksklick auf Market-Item: Item wird aus dem Markt entfernt und in den Cursor gelegt
- Rechtsklick auf Market-Item: Item wird nur entfernt

Items werden in der Reihenfolge angezeigt, in der sie zur Kategorie hinzugefügt wurden. Neue Items landen hinten. Kategorienamen und Icons kommen aus `market.categories.<category>.name` und `market.categories.<category>.icon`.

## Preis-System

Der Basispreis wird aus den globalen Preiswerten und der Item-Seltenheit berechnet:

- `baseprice_high`: Preis für sehr seltene Items
- `baseprice_low`: Mindest-Basispreis für sehr häufige Items
- Item-Wert in der Kategorie: virtuelle Marktmenge/Seltenheit

Beim Kaufen steigt der Preis leicht. Beim Verkaufen sinkt er leicht. Zusätzlich stabilisiert sich der Preis langsam zurück zum Basispreis:

```text
Intervall und Faktor werden über `market.stabilization.interval-hours` und `market.stabilization.factor-percent` konfiguriert.
```

## Daten-History

- Spieler-Trade-History wird nach `history.trade-retention-hours` bereinigt.
- Serverkassen-History wird je nach `server-bank.weekly.clear-history-after-refill` nach der wöchentlichen Auszahlung gelöscht.
- Marktpreis-History wird in `prices.yml` gespeichert.
- Kontostand-History wird in `history.yml` gespeichert.

## Permissions

```yaml
serverutilities.admin:
  description: Zugriff auf alle ServerUtilities Admin-Befehle.
  default: op
```

## Entwicklung

Projektstruktur:

```text
src/main/java/de/serverutilities/
src/main/resources/config.yml
src/main/resources/plugin.yml
build.gradle
settings.gradle
```

Build lokal:

```bash
./gradlew build
```

Bei Änderungen an `plugin.yml` oder `config.yml` den Server nach dem Deploy neu starten.

## Hinweise

- Das Plugin nutzt Bukkit/Paper Inventar-GUIs und läuft auch auf Purpur.
- Die Preisgrafik wird über Minecraft-Karten gerendert.
- Die Serverkasse liegt in `players.yml` unter `server-bank`.
- Market-Items werden nur aus der echten Server-Config gelesen, damit entfernte Default-Items nicht wieder erscheinen.
