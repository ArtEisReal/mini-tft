/**
 * Ein Eintrag im serverseitigen Befehls-Journal.
 *
 * Vor jedem ausgeführten Befehl wird ein {@link GameEngine.Snapshot} des
 * Spielzustands gespeichert. Trifft eine verspaetete Nachricht ein, kann der
 * Server zu dem Snapshot zurueckspulen, der vor dem ersten kausal spaeteren
 * Befehl liegt, und anschliessend alle folgenden Befehle erneut ausfuehren.
 */
public final class CommandJournalEntry {

    /** Client, der den Befehl gesendet hat. */
    public final String clientId;

    /** Spieler-ID (0 oder 1). */
    public final int playerId;

    /** Befehlsname (buy, reroll, ready, ...). */
    public final String action;

    /** Befehlsargumente. */
    public final int[] args;

    /** Vektoruhr zum Sendezeitpunkt. */
    public final VectorClock clock;

    /** JMS-Correlation-ID der urspruenglichen Nachricht. */
    public final String correlationId;

    /** Spielzustand VOR der Ausfuehrung dieses Befehls. */
    public final GameEngine.Snapshot snapshotBefore;

    public CommandJournalEntry(String clientId, int playerId, String action, int[] args,
                               VectorClock clock, String correlationId,
                               GameEngine.Snapshot snapshotBefore) {
        this.clientId = clientId;
        this.playerId = playerId;
        this.action = action;
        this.args = args != null ? args.clone() : new int[0];
        this.clock = clock;
        this.correlationId = correlationId;
        this.snapshotBefore = snapshotBefore;
    }

    /** Kopiert einen alten Eintrag mit einem neuen Vorher-Snapshot (fuer Replay). */
    public CommandJournalEntry withSnapshotBefore(GameEngine.Snapshot snapshot) {
        return new CommandJournalEntry(clientId, playerId, action, args, clock, correlationId, snapshot);
    }

    @Override
    public String toString() {
        return action + " von " + clientId + " (Spieler " + playerId + ")"
                + (clock != null ? ", Uhr " + clock : "");
    }
}
