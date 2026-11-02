import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Client -> Server Schnittstelle (der spätere RPC/RMI-Contract, Richtung 1).
 * Alle Parameter/Rückgabewerte sind primitive Typen oder DTOs.
 * Indizes sind immer 1-basiert (wie in der Konsole/UART gewohnt).
 * 
 * RMI-Anpassung: Erweitert java.rmi.Remote, alle Methoden werfen RemoteException.
 */
public interface GameService extends Remote {
    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Kauft aus dem Shop auf die Bench. */
    // AI-GENERATED-END
    PlayerDto buy(int playerId, int slot) throws GameRuleException, RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Verkauft eine Feld-Einheit. */
    // AI-GENERATED-END
    PlayerDto sell(int playerId, int index) throws GameRuleException, RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Verkauft eine Bench-Einheit. */
    // AI-GENERATED-END
    PlayerDto sellBench(int playerId, int slot) throws GameRuleException, RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Verschiebt eine Feld-Einheit auf eine Zelle der eigenen Halbseite (Tausch mit eigener Einheit moeglich). */
    // AI-GENERATED-END
    PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException, RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Zieht eine Bench-Einheit aufs Feld (tauscht ggf. mit der Einheit auf der Zielzelle). */
    // AI-GENERATED-END
    PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException, RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Zieht eine Feld-Einheit zurueck auf die Bench. */
    // AI-GENERATED-END
    PlayerDto toBench(int playerId, int index) throws GameRuleException, RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Erhoeht das Level -> mehr Platz auf dem Feld. */
    // AI-GENERATED-END
    PlayerDto levelUp(int playerId) throws GameRuleException, RemoteException;

    PlayerDto reroll(int playerId) throws GameRuleException, RemoteException;

    PlayerDto ready(int playerId) throws GameRuleException, RemoteException;

    GameStateDto getState(int playerId) throws GameRuleException, RemoteException;

    // AI-GENERATED-START: Aufgabe 6 - Minimalaufruf fuer den Lokal-vs-RPC-Benchmark - 2026-11-02 14:40 CET
    /**
     * Minimaler "Ping" ohne Nutzlast: dient nur dem Benchmark (Aufgabe 6),
     * um die reine RPC-Grundkosten ohne Serialisierung groesserer Objekte zu messen.
     * Gibt die Server-Zeit (Nanosekunden) zurueck.
     */
    long ping() throws RemoteException;
    // AI-GENERATED-END

    // AI-GENERATED-START: JSON-Varianten fuer das menschenlesbare Nachrichtenformat (JSON-P) - 2026-11-02 14:10 CET
    /**
     * Spielstand als menschenlesbaren JSON-String (Marshalling auf dem Server,
     * Unmarshalling auf dem Client). Richtung Server -> Client.
     */
    String getStateJson(int playerId) throws GameRuleException, RemoteException;

    /**
     * Fuehrt einen als JSON uebertragenen Befehl aus (Richtung Client -> Server):
     * die Anfrage wird mit dem JsonParser unmarshalled, das Ergebnis-DTO
     * wieder als JSON marshalled zurueckgeschickt.
     */
    String executeJson(String commandJson) throws GameRuleException, RemoteException;
    // AI-GENERATED-END

    /** Der Client meldet sich für Callbacks an (Richtung 2). Der Listener ist ein Remote-Objekt auf dem Client. */
    void addListener(GameListener listener) throws RemoteException;
}
