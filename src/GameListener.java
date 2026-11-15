// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.util.List;
// AI-GENERATED-END

/**
 * Server -> Client Schnittstelle (Callbacks, Richtung 2).
 * Design Pattern: Observer.
 * 
 * Dieses Interface ist technologieunabhaengig (kein RMI, kein RemoteException).
 * Es wird sowohl von der lokalen Variante, dem RMI-Proxy als auch vom JMS-Client verwendet.
 */
public interface GameListener {
    void onRoundStarted(int round);

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Frames des positionsbasierten Kampfes – der Client kann sie als Animation abspielen. */
    // AI-GENERATED-END
    void onCombatFrames(int round, List<CombatFrameDto> frames);

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Drei gleiche Einheiten wurden zur naechsten Sternstufe vereinigt. */
    // AI-GENERATED-END
    void onUnitCombined(int playerId, String unitName, int star);

    void onCombatFinished(CombatResultDto result);

    /** Der Zug ist an einem anderen Spieler. */
    void onTurnChanged(int turn);

    void onGameOver(String winnerName);
}
