/**
 * Client -> Server Schnittstelle (der spätere RPC/RMI-Contract, Richtung 1).
 * Alle Parameter/Rückgabewerte sind primitive Typen oder DTOs.
 * Indizes sind immer 1-basiert (wie in der Konsole/UART gewohnt).
 */
public interface GameService {
    /** Kauft aus dem Shop auf die Bench. */
    PlayerDto buy(int playerId, int slot) throws GameRuleException;

    /** Verkauft eine Feld-Einheit. */
    PlayerDto sell(int playerId, int index) throws GameRuleException;

    /** Verkauft eine Bench-Einheit. */
    PlayerDto sellBench(int playerId, int slot) throws GameRuleException;

    /** Verschiebt eine Feld-Einheit auf eine Zelle der eigenen Halbseite (Tausch mit eigener Einheit moeglich). */
    PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException;

    /** Zieht eine Bench-Einheit aufs Feld (tauscht ggf. mit der Einheit auf der Zielzelle). */
    PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException;

    /** Zieht eine Feld-Einheit zurueck auf die Bench. */
    PlayerDto toBench(int playerId, int index) throws GameRuleException;

    /** Erhoeht das Level -> mehr Platz auf dem Feld. */
    PlayerDto levelUp(int playerId) throws GameRuleException;

    PlayerDto reroll(int playerId) throws GameRuleException;

    PlayerDto ready(int playerId) throws GameRuleException;

    GameStateDto getState(int playerId) throws GameRuleException;

    /** Der Client meldet sich für Callbacks an (Richtung 2). */
    void addListener(GameListener listener);
}
