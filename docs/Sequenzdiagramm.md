<!-- AI-GENERATED-START: Aufgabe 5 - Sequenzdiagramme fuer entfernte Aufrufe (C->S Befehl und S->C Callback) - 2026-11-02 15:20 CET -->
# Sequenzdiagramme (Aufgabe 5)

Ablauf **eines einzelnen entfernten Aufrufs** – einmal in jede Richtung unseres
bidirektionalen Contracts.

- **Diagramm 1:** Client → Server, `GameService.buy(...)` (Befehl, Rückgabe DTO)
- **Diagramm 2:** Server → Client, `GameListener.onRoundStarted(...)` (Push-Event)

Beide zeigen: Stub-Aufruf → Marshalling → Netzwerk → Dispatch auf dem echten
Objekt → Rückweg (bzw. Exception) → Ergebnis im Aufrufer.

---

## 0. Verbindungsaufbau (Voraussetzung für beide Diagramme)

```mermaid
sequenceDiagram
    autonumber
    participant C as "Client (JVM A)"
    participant REG as "RMI-Registry (JVM B, Port 1099)"
    participant E as "GameEngine (echtes Objekt, JVM B)"

    C->>REG: Naming.lookup("rmi://10.8.3.3:1099/mini-tft")
    REG-->>C: Referenz auf den Remote-Stub (dynamic Proxy)
    Note over C: RmiClient.service = GameService-Stub
    C->>E: service.addListener(listenerProxy)
    E-->>C: ok - der Server speichert den Gegner-Stub fuer Callbacks
```

---

## 1. Client → Server: `buy(int playerId, int slot)`

```mermaid
sequenceDiagram
    autonumber
    participant U as "Nutzer / SwingMain (Client, JVM A)"
    participant S as "GameService-Stub (dynamic Proxy, JVM A)"
    participant RA as "RMI-Laufzeit Client (Marshalling)"
    participant N as "Netzwerk TCP/IP (Port 1100, JRMP)"
    participant RB as "RMI-Laufzeit Server (Dispatch)"
    participant E as "GameEngine (echtes Objekt, JVM B)"

    U->>S: service.buy(0, 1)
    S->>RA: Objekt-Methoden-ID + Argumente (0, 1)
    RA->>N: JRMP-Request, serialisiert via ObjectOutputStream
    N->>RB: Request am Server-Port 1100
    RB->>E: Upcall: engine.buy(0, 1)
    E->>E: Regelpruefung (Gold, Bench-Platz), Zustand aendern
    alt Erfolg
        E-->>RB: PlayerDto (Record, Serializable)
        RB-->>N: JRMP-Reply (DTO serialisiert)
        N-->>RA: Rueckweg ueber TCP
        RA-->>S: PlayerDto deserialisiert (ObjectInputStream)
        S-->>U: return PlayerDto
        U->>U: GUI / Konsole mit neuem Zustand aktualisieren
    else Regelverletzung
        E-->>RB: throw new GameRuleException("Zu wenig Gold: ...")
        RB-->>N: JRMP-Reply mit Exception-Objekt
        N-->>RA: Rueckweg ueber TCP
        RA-->>S: Exception deserialisiert
        S-->>U: throw GameRuleException (checked, in der Signatur deklariert)
        U->>U: Fehlermeldung anzeigen
    end
```

**Zu beachten:**
- Der Aufruf ist **synchron**: der Client blockiert bis zur Rückkehr (Aufgabe 6
  misst genau diese Dauer).
- `GameRuleException` wird **mit übertragen** (Signatur
  `throws GameRuleException, RemoteException`) → Aufgabe 7.
- Marshalling hier = `java.io.Serializable` der DTO-Records; das JSON-Format aus
  `JsonMapper` liegt als String **im** Argument/Rückgabewert
  (`executeJson`, `getStateJson`).

---

## 2. Server → Client: `onRoundStarted(int round)` (Push-Callback)

```mermaid
sequenceDiagram
    autonumber
    participant E as "GameEngine (JVM B, Server)"
    participant LS as "Listener-Stub im Server (Proxy-Referenz auf JVM A)"
    participant N as "Netzwerk TCP/IP (Port 1098, JRMP)"
    participant LR as "RMI-Laufzeit Client (JVM A)"
    participant P as "GameListenerProxy (Remote-Objekt, JVM A)"
    participant G as "SwingMain (delegierter Listener)"

    Note over E,LS: Voraussetzung: service.addListener(proxy) - der Server haelst den Stub des Clients
    E->>LS: onRoundStarted(3)
    LS->>N: JRMP-Request (int round), synchron
    N->>LR: Anfrage am Client-Port 1098
    LR->>P: Dispatch auf dem exportierten Objekt
    P->>G: delegate.onRoundStarted(3)
    G->>G: GUI aktualisieren (Rundentext, Shop auffuellen)
    G-->>LR: Return (void)
    LR-->>N: JRMP-Reply
    N-->>LS: Reply
    LS-->>E: Aufruf beendet
```

**Zu beachten:**
- Der Server **ruft beim Client auf** – nur möglich, weil der Client sein
  `GameListenerProxy`-Objekt exportiert hat (`UnicastRemoteObject.exportObject`,
  fester Port 1098) und den Stub bei `addListener` mitgeliefert hat.
- Erreicht der Server Port 1098 nicht (Firewall/NAT), erzeugt RMI pro Callback
  eine `ConnectException` mit Timeout → sichtbar als
  `Listener-Callback fehlgeschlagen: ...` auf dem Server.

---

## Skizze zum Abzeichnen (reicht laut Aufgabe als „saubere Skizze")

```
 Client (JVM A)                                          Server (JVM B)
+---------------------+                              +----------------------+
| SwingMain            |                              | GameEngine           |
|        |            |                              |   (echtes Objekt)    |
|        v            |                              |        ^             |
| GameService-Stub ---+-- Marshalling (DTO/long) ----+--> RMI-Dispatch      |
|  (dynamic Proxy)    |        Netz / TCP 1100       |        |             |
|                     |<-- Unmarshalling (Rueckgabe  | Regelpruefung+Logik  |
|        v            |        oder Exception)       |        v             |
| GUI aktualisiert    |                              | PlayerDto / Fehler   |
+---------------------+                              +----------------------+

 Rueckrichtung (Push):
 GameEngine -- onRoundStarted / onCombatFrames --> Listener-Stub (Serverseite)
            -- Netz / TCP 1098 --> GameListenerProxy (Client) --> GUI
```

## Stubs: konkret in unserem Code

| Remote-Interface | Echte Implementierung | Stub-Werter auf der Gegenseite | Wo entsteht der Stub |
|---|---|---|---|
| `GameService` | `GameEngine` (Server) | dynamic Proxy, gehalten von `RmiClient.service` | `Naming.lookup(...)` |
| `GameListener` | `RmiClient.GameListenerProxy` (Client) | dynamic Proxy, gehalten in `GameEngine.listeners` | `UnicastRemoteObject.exportObject(...)` + `addListener(...)` |
| – DTOs (`GameStateDto`, `PlayerDto`, `UnitDto`, `CombatFrameDto`, `CombatResultDto`) | – | **kein Stub**, nur `Serializable` | – |

**Hinweis für die mündliche Prüfung:** Skeleton-Klassen gibt es seit Java 5
nicht mehr – die „Stubs" sind dynamische Proxies (`java.lang.reflect.Proxy`),
die RMI beim `lookup` bzw. beim Export erzeugt; die Methode wird über die
Objekt-ID + Methodensignatur identifiziert und per Refection auf dem Server
ausgeführt (Dispatch), nicht über generierten Code.
<!-- AI-GENERATED-END -->
