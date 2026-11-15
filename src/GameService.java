/**
 * Client -> Server Schnittstelle (der spätere RPC/RMI-Contract, Richtung 1).
 * Alle Parameter/Rückgabewerte sind primitive Typen oder DTOs.
 * Indizes sind immer 1-basiert (wie in der Konsole/UART gewohnt).
 * 
 * Dieses Interface ist technologieunabhaengig (kein RMI, kein RemoteException).
 * Es wird sowohl von der lokalen Variante, dem RMI-Stub als auch vom JMS-Client verwendet.
 */
public interface GameService {
    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Kauft aus dem Shop auf die Bench. */
    // AI-GENERATED-END
    PlayerDto buy(int playerId, int slot) throws GameRuleException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Verkauft eine Feld-Einheit. */
    // AI-GENERATED-END
    PlayerDto sell(int playerId, int index) throws GameRuleException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Verkauft eine Bench-Einheit. */
    // AI-GENERATED-END
    PlayerDto sellBench(int playerId, int slot) throws GameRuleException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Verschiebt eine Feld-Einheit auf eine Zelle der eigenen Halbseite (Tausch mit eigener Einheit moeglich). */
    // AI-GENERATED-END
    PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Zieht eine Bench-Einheit aufs Feld (tauscht ggf. mit der Einheit auf der Zielzelle). */
    // AI-GENERATED-END
    PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Zieht eine Feld-Einheit zurueck auf die Bench. */
    // AI-GENERATED-END
    PlayerDto toBench(int playerId, int index) throws GameRuleException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Erhoeht das Level -> mehr Platz auf dem Feld. */
    // AI-GENERATED-END
    PlayerDto levelUp(int playerId) throws GameRuleException;

    PlayerDto reroll(int playerId) throws GameRuleException;

    PlayerDto ready(int playerId) throws GameRuleException;

    GameStateDto getState(int playerId) throws GameRuleException;

    // AI-GENERATED-START: Aufgabe 6 - Minimalaufruf fuer den Lokal-vs-RPC-Benchmark - 2026-11-02 14:40 CET
    /**
     * Minimaler "Ping" ohne Nutzlast: dient nur dem Benchmark (Aufgabe 6),
     * um die reine RPC-Grundkosten ohne Serialisierung groesserer Objekte zu messen.
     * Gibt die Server-Zeit (Nanosekunden) zurueck.
     */
    long ping();
    // AI-GENERATED-END

    // AI-GENERATED-START: JSON-Varianten fuer das menschenlesbare Nachrichtenformat (JSON-P) - 2026-11-02 14:10 CET
    /**
     * Spielstand als menschenlesbaren JSON-String (Marshalling auf dem Server,
     * Unmarshalling auf dem Client). Richtung Server -> Client.
     */
    String getStateJson(int playerId) throws GameRuleException;

    /**
     * Fuehrt einen als JSON uebertragenen Befehl aus (Richtung Client -> Server):
     * die Anfrage wird mit dem JsonParser unmarshalled, das Ergebnis-DTO
     * wieder als JSON marshalled zurueckgeschickt.
     */
    String executeJson(String commandJson) throws GameRuleException;
    // AI-GENERATED-END

    /** Der Client meldet sich für Callbacks an (Richtung 2). */
    void addListener(GameListener listener);
}
