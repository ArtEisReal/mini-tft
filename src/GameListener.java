import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Server -> Client Schnittstelle (Callbacks, Richtung 2).
 * Design Pattern: Observer.
 * 
 * RMI-Anpassung: Erweitert java.rmi.Remote, alle Callback-Methoden werfen RemoteException.
 * Keine default-Methoden, da RMI diese nicht unterstützt.
 */
public interface GameListener extends Remote {
    void onRoundStarted(int round) throws RemoteException;

    /** Frames des positionsbasierten Kampfes – der Client kann sie als Animation abspielen. */
    void onCombatFrames(int round, List<CombatFrameDto> frames) throws RemoteException;

    /** Drei gleiche Einheiten wurden zur naechsten Sternstufe vereinigt. */
    void onUnitCombined(int playerId, String unitName, int star) throws RemoteException;

    void onCombatFinished(CombatResultDto result) throws RemoteException;

    void onGameOver(String winnerName) throws RemoteException;
}
