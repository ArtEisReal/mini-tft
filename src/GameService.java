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
    /** Kauft aus dem Shop auf die Bench. */
    PlayerDto buy(int playerId, int slot) throws GameRuleException, RemoteException;

    /** Verkauft eine Feld-Einheit. */
    PlayerDto sell(int playerId, int index) throws GameRuleException, RemoteException;

    /** Verkauft eine Bench-Einheit. */
    PlayerDto sellBench(int playerId, int slot) throws GameRuleException, RemoteException;

    /** Verschiebt eine Feld-Einheit auf eine Zelle der eigenen Halbseite (Tausch mit eigener Einheit moeglich). */
    PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException, RemoteException;

    /** Zieht eine Bench-Einheit aufs Feld (tauscht ggf. mit der Einheit auf der Zielzelle). */
    PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException, RemoteException;

    /** Zieht eine Feld-Einheit zurueck auf die Bench. */
    PlayerDto toBench(int playerId, int index) throws GameRuleException, RemoteException;

    /** Erhoeht das Level -> mehr Platz auf dem Feld. */
    PlayerDto levelUp(int playerId) throws GameRuleException, RemoteException;

    PlayerDto reroll(int playerId) throws GameRuleException, RemoteException;

    PlayerDto ready(int playerId) throws GameRuleException, RemoteException;

    GameStateDto getState(int playerId) throws GameRuleException, RemoteException;

    /** Der Client meldet sich für Callbacks an (Richtung 2). Der Listener ist ein Remote-Objekt auf dem Client. */
    void addListener(GameListener listener) throws RemoteException;
}
