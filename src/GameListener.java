import java.rmi.Remote;
import java.rmi.RemoteException;
// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.util.List;
// AI-GENERATED-END

/**
 * Server -> Client Schnittstelle (Callbacks, Richtung 2).
 * Design Pattern: Observer.
 * 
 * RMI-Anpassung: Erweitert java.rmi.Remote, alle Callback-Methoden werfen RemoteException.
 * Keine default-Methoden, da RMI diese nicht unterstützt.
 */
public interface GameListener extends Remote {
    void onRoundStarted(int round) throws RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Frames des positionsbasierten Kampfes – der Client kann sie als Animation abspielen. */
    // AI-GENERATED-END
    void onCombatFrames(int round, List<CombatFrameDto> frames) throws RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Drei gleiche Einheiten wurden zur naechsten Sternstufe vereinigt. */
    // AI-GENERATED-END
    void onUnitCombined(int playerId, String unitName, int star) throws RemoteException;

    void onCombatFinished(CombatResultDto result) throws RemoteException;

    void onGameOver(String winnerName) throws RemoteException;
}
