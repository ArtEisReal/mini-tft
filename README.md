<!-- AI-GENERATED-START: Neue Datei - Anleitung für Server/Client-Betrieb über VPN inkl. Ports und Konfigurationstabelle - 2026-11-02 12:41 CET -->
# Mini-TFT

Auto-Battler (TFT-Style) in Java. Ein Server (`RmiServer`) hält den Spielzustand,
der Client (`SwingMain`, Swing-GUI) verbindet sich per RMI.

## Bauen

```bash
javac -cp lib/javax.json-1.1.4.jar -d out src/*.java
```

Der Klassenpfad (`-cp`) wird an allen Stellen weiter unten gebraucht – unter
Windows ist statt `:` ein `;` zu verwenden (`out;lib/javax.json-1.1.4.jar`).

## Lokal starten (ein Rechner, Hot-Seat)

```bash
java -cp out:lib/javax.json-1.1.4.jar RmiServer                 # Terminal 1
java -cp out:lib/javax.json-1.1.4.jar SwingMain                 # Terminal 2 – beide spielen abwechselnd
```

## Server auf einem anderen Rechner (z.B. VPN)

Voraussetzung: **beide Rechner brauchen denselben Code-Stand** (gleicher
Git-Commit / gleiche Klassen), weil RMI die Interfaces und DTOs auf beiden
Seiten braucht.

**1. Server-Rechner** – eigene IP setzen und starten:

```bash
javac -cp lib/javax.json-1.1.4.jar -d out src/*.java
java -cp out:lib/javax.json-1.1.4.jar -Dminitft.host=<SERVER-VPN-IP> RmiServer
```

`minitft.host` schreibt `java.rmi.server.hostname`. Ohne diesen Schritt steckt
ein lokaler Name (z.B. `MacBook-Pro.local`) in den RMI-Stubs – der andere
Rechner kann ihn nicht auflösen, `Naming.lookup` schlägt fehl.
Env-Alternative: `MINITFT_HOST=10.8.0.2 java -cp out:lib/javax.json-1.1.4.jar RmiServer`.

**2. Firewall auf dem Server** – eingehend erlauben:

| Port | Zweck | Variable |
|------|-------|----------|
| TCP 1099 | RMI-Registry (Lookup) | – |
| TCP 1100 | GameEngine (Spiel-Aufrufe) | `-Dminitft.enginePort=<port>` |

**3. Client-Rechner** – Server-IP als Argument, eigene IP für die Callbacks:

```bash
javac -cp lib/javax.json-1.1.4.jar -d out src/*.java
java -cp out:lib/javax.json-1.1.4.jar -Dminitft.host=<CLIENT-VPN-IP> SwingMain <SERVER-VPN-IP>
```

Ohne `minitft.host` auf dem Client kann der Server die **Callbacks**
(Runden-Start, Kampf-Animationen) nicht zurückschicken, d.h. das Spiel läuft,
aber die Animationen/Events fehlen.

Statt Argument geht auch `-Dminitft.server=<SERVER-VPN-IP>` bzw. `MINITFT_SERVER`.

**4. Firewall auf dem Client** – eingehend erlauben:

| Port | Zweck | Variable |
|------|-------|----------|
| TCP 1098 | GameListener-Callback (Server → Client) | `-Dminitft.listenerPort=<port>` |

Beim Export meldet der Client seinen tatsächlichen Port in der Konsole
(`Listener beim Server registriert (eigener Port …)`). Ist der Port belegt,
fällt RMI auf einen anonymen Port zurück – dann steht eine Warnung mit dem
tatsächlichen Port da.

## Prüfen

```bash
ping <SERVER-VPN-IP>
nc -vz <SERVER-VPN-IP> 1099          # Registry erreichbar?
lsof -nP -iTCP -sTCP:LISTEN | grep java   # Ports auf der Gegenseite
```

macOS: Systemeinstellungen → Netzwerk → Firewall darf Java nicht komplett
blockieren („alle eingehenden Verbindungen blockieren" ausschalten).
Windows: `netsh advfirewall firewall add rule name="RMI" dir=in action=allow protocol=TCP localport=1099`

## Konfiguration (System-Properties)

| Property | Env | Bedeutung | Standard |
|----------|-----|-----------|----------|
| `minitft.host` | `MINITFT_HOST` | eigene IP für RMI-Stubs (= `java.rmi.server.hostname`) | lokaler Hostname |
| `minitft.server` | `MINITFT_SERVER` | Server-Host, falls kein Argument | `localhost` |
| `minitft.enginePort` | – | Port des GameEngine | `1100` |
| `minitft.listenerPort` | – | Port des GameListener (`0` = anonym) | `1098` |

## Menschenlesbares Nachrichtenformat (JSON-P)

Bibliothek: **JSON-P / JSR 374** (`javax.json`), Referenz-Implementierung
`lib/javax.json-1.1.4.jar` (aus Maven Central, `org.glassfish:javax.json:1.1.4`).
In IntelliJ ist die JAR unter *Project Structure → Modules → Dependencies*
eingebunden (steht in `mini-tft.iml`).

| Richtung | Methode (in `GameService`) | Marshalling | Unmarshalling |
|----------|----------------------------|-------------|---------------|
| Client → Server | `String executeJson(String commandJson)` | `JsonMapper.commandJson()` (Objekt → JSON) | `JsonMapper.parseCommand()` via **JsonParser** (ereignisbasiert) |
| Server → Client | `String getStateJson(int playerId)` | `JsonMapper.toJson(GameStateDto)` | `JsonMapper.toGameState()` / `toPlayer()` / `toUnit()` |
| `CombatFrameDto` (Animation) | `GameListener.onCombatFrames(...)` | RMI-Objekt | `JsonMapper.toCombatFrame()` / `toUnitState()` / `toCombatEvent()` |

Alle drei Ansichten liegen im Menü der GUI:

| Menü | Eintrag | Zeigt |
|---|---|---|
| *Ansicht* | **Spielstand als JSON ...** | `getStateJson(current)` – Server marshallt den `GameStateDto` |
| *Ansicht* | **Befehl als JSON senden ...** | `executeJson(...)` – Request **und** Response als JSON |
| *Ansicht* | **Kampf-Frames als JSON ...** | `CombatFrameDto`/`UnitStateDto`/`CombatEventDto` des letzten Kampfes |
| *Extras* | **Benchmark (lokal vs. RPC) ...** | Messung aus Aufgabe 6 |

Beispiel „Befehl als JSON senden" mit `buy 1`:

```json
--> Request  (Marshalling auf dem Client)
{ "cmd": "buy", "playerId": 0, "args": [ 1 ] }

<-- Response (Marshalling auf dem Server)
{ "id": 0, "name": "Alice", "hp": 30, "gold": 8, ... }

Unmarshalling auf dem Client: Alice | HP 30 | Gold 8 | Level 2
```

Bei fehlendem Argument (`json` = `buy`) erscheint die Server-Meldung
`JSON-Befehl "buy" braucht Argument 1` – die `GameRuleException` wird über RMI
übertragen (Aufgabe 7).

Der Transport bleibt RMI/JRMP; das Nutzformat der beiden JSON-Methoden ist
String-JSON, d.h. auf dem Draht liegt ein menschenlesbarer Text. Fehlt ein
Argument oder ist das JSON kaputt, entsteht eine `GameRuleException`, die RMI
vom Server zum Client überträgt (Aufgabe 7 – Exception-Übertragung).

Klassen mit Stubs (nur diese sind `Remote`/werden verteiltem):
`GameService` + `GameEngine` (Client → Server), `GameListener` +
`RmiClient.GameListenerProxy` (Server → Client); Registry `RmiServer`.

## Aufgabe 6: lokaler Aufruf vs. entfernter RPC

`RpcBenchmark` misst **dieselbe Methode auf derselben Implementierung** zweimal:
einmal als direkten Aufruf im selben JVM (Objekt-Referenz, kein Stub), einmal
über den RMI-Stub (Marshalling → TCP → Server → Unmarshalling).

```bash
# eigenständig gegen einen Rechner:
java -cp out:lib/javax.json-1.1.4.jar RpcBenchmark <SERVER-IP> 500

# oder in der GUI: Menü "Extras" -> "Benchmark (lokal vs. RPC) ..." (Anzahl eingeben)
```

Messreihen mit steigender Nutzlast:

| Methode | Was gemessen wird |
|---------|-------------------|
| `ping()` | reine RPC-Grundkosten (kein Payload, Methode gibt `long` zurück) |
| `getState(0)` | + Serialisierung der DTOs (Listen mit `UnitDto`) |
| `getStateJson(0)` | + JSON-Marshalling/Unmarshalling (JSON-P) des gleichen DTO |

Beispielzahlen (gleicher Rechner, d.h. **ohne** Netzwerklatenz):

```
ping()          lokal Ø 0,12 µs | remote Ø 56 µs   Faktor ~460
getState(0)     lokal Ø 5,5  µs | remote Ø 83 µs   Faktor ~15
getStateJson(0) lokal Ø 98   µs | remote Ø 132 µs  Faktor ~1,4
```

Für die Abnahme zusätzlich gegen die **IP des zweiten Rechners** messen
(`RpcBenchmark 10.8.3.3`) – dann kommt die echte Netzwerklatenz zum
Protokoll-/Serialisierungsaufwand hinzu. Die Zahlen in der Konsole als
Tabelle ausgeben und Werte vor dem Vorführen protokollieren.

## Spiel (GUI)

Bedienung mit der Maus im `SwingMain`-Fenster:

- **Shop-Klick** = kaufen (Einheit landet auf der Bench)
- **Bench anklicken, dann Brettzelle** = aufs Feld ziehen
- **Feld-Einheit anklicken, dann Zielzelle** = verschieben/tauschen
- **Rechtsklick** auf eigene Einheit = verkaufen
- **Reroll** / **Level up** / **Fertig** als Buttons; zwischen den Spielern
  liegt ein Sichtschutz („Weiter")

Zusätzlich im Menü: JSON-Ansichten und Benchmark (siehe oben).
<!-- AI-GENERATED-END -->
