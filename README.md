<!-- AI-GENERATED-START: Neue Datei - Anleitung für Server/Client-Betrieb über VPN inkl. Ports und Konfigurationstabelle - 2026-11-02 12:41 CET -->
# Mini-TFT

Auto-Battler (TFT-Style) in Java. Ein Server (`RmiServer`) hält den Spielzustand,
Clients (`SwingMain` für die GUI, `Main` für die Konsole) verbinden sich per RMI.

## Bauen

```bash
javac -d out src/*.java
```

## Lokal starten (ein Rechner, Hot-Seat)

```bash
java -cp out RmiServer                 # Terminal 1
java -cp out SwingMain                 # Terminal 2 – beide spielen abwechselnd
# oder Konsole:
java -cp out Main
```

## Server auf einem anderen Rechner (z.B. VPN)

Voraussetzung: **beide Rechner brauchen denselben Code-Stand** (gleicher
Git-Commit / gleiche Klassen), weil RMI die Interfaces und DTOs auf beiden
Seiten braucht.

**1. Server-Rechner** – eigene IP setzen und starten:

```bash
javac -d out src/*.java
java -cp out -Dminitft.host=<SERVER-VPN-IP> RmiServer
```

`minitft.host` schreibt `java.rmi.server.hostname`. Ohne diesen Schritt steckt
ein lokaler Name (z.B. `MacBook-Pro.local`) in den RMI-Stubs – der andere
Rechner kann ihn nicht auflösen, `Naming.lookup` schlägt fehl.
Env-Alternative: `MINITFT_HOST=10.8.0.2 java -cp out RmiServer`.

**2. Firewall auf dem Server** – eingehend erlauben:

| Port | Zweck | Variable |
|------|-------|----------|
| TCP 1099 | RMI-Registry (Lookup) | – |
| TCP 1100 | GameEngine (Spiel-Aufrufe) | `-Dminitft.enginePort=<port>` |

**3. Client-Rechner** – Server-IP als Argument, eigene IP für die Callbacks:

```bash
javac -d out src/*.java
java -cp out -Dminitft.host=<CLIENT-VPN-IP> SwingMain <SERVER-VPN-IP>
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

## Spiel (Konsolen-Client)

```
buy <shop> | levelup | reroll | ready | quit
tofield <bench> <zeile> <spalte> | tobench <feld-pos> | move <feld-pos> <zeile> <spalte>
sell <feld-pos> | sellbench <bench>
```
<!-- AI-GENERATED-END -->
