import java.util.List;

/**
 * Server -> Client Schnittstelle (Callbacks, Richtung 2).
 * Design Pattern: Observer.
 */
public interface GameListener {
    void onRoundStarted(int round);

    /** Frames des positionsbasierten Kampfes – der Client kann sie als Animation abspielen. */
    default void onCombatFrames(int round, List<CombatFrameDto> frames) {}

    /** Drei gleiche Einheiten wurden zur naechsten Sternstufe vereinigt. */
    default void onUnitCombined(int playerId, String unitName, int star) {}

    void onCombatFinished(CombatResultDto result);

    void onGameOver(String winnerName);
}
