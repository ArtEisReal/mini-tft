/**
 * Clientseitiger Stub der JMS-Kommunikationsschicht.
 *
 * Der Stub implementiert denselben {@link GameService}-Vertrag wie der
 * Server ({@link JmsGameSkeleton}) und uebersetzt Methodenaufrufe der GUI in
 * JMS-Nachrichten. Die GUI kennt damit weder Broker noch Queue-Namen.
 *
 * Von-Neumann-Stil:
 *   GUI -- GameService --> Stub -- JmsClient --> Broker
 *   Broker --> JmsServer --> Skeleton -- GameService --> GameEngine
 *
 * Die GameService-Methoden sind asynchron: sie senden den Befehl und liefern
 * {@code null}; das Ergebnis kommt als Callback ueber den {@link GameListener}.
 * {@link #getState(int)} ist synchron (Request/Reply mit Correlation-ID).
 */
public final class JmsGameServiceStub implements GameService {

    private final JmsClient client;

    public JmsGameServiceStub(JmsClient client) {
        this.client = client;
    }

    public JmsClient client() {
        return client;
    }

    /**
     * Demo-Funktion: kauft eine Einheit mit Broker-Verzoegerung.
     * Die Vektoruhr wird beim Senden eingefroren.
     */
    public void buyDelayed(int playerId, int slot, long delayMs) throws GameRuleException {
        try {
            client.buyDelayed(playerId, slot, delayMs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Demo-Funktion mit Standard-Verzoegerung aus {@link JmsConfig#DEMO_DELAY_MS}. */
    public void buyDelayed(int playerId, int slot) throws GameRuleException {
        buyDelayed(playerId, slot, JmsConfig.DEMO_DELAY_MS);
    }

    @Override
    public PlayerDto buy(int playerId, int slot) throws GameRuleException {
        try { client.buy(playerId, slot); } catch (Exception e) { throw new RuntimeException(e); }
        return null; // asynchron, Ergebnis kommt per Callback
    }

    @Override
    public PlayerDto sell(int playerId, int index) throws GameRuleException {
        try { client.sell(playerId, index); } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    @Override
    public PlayerDto sellBench(int playerId, int slot) throws GameRuleException {
        try { client.sellBench(playerId, slot); } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    @Override
    public PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException {
        try { client.move(playerId, index, row, col); } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    @Override
    public PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException {
        try { client.toField(playerId, benchSlot, row, col); } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    @Override
    public PlayerDto toBench(int playerId, int index) throws GameRuleException {
        try { client.toBench(playerId, index); } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    @Override
    public PlayerDto levelUp(int playerId) throws GameRuleException {
        try { client.levelUp(playerId); } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    @Override
    public PlayerDto reroll(int playerId) throws GameRuleException {
        try { client.reroll(playerId); } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    @Override
    public PlayerDto ready(int playerId) throws GameRuleException {
        try { client.ready(playerId); } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    @Override
    public GameStateDto getState(int playerId) throws GameRuleException {
        try {
            return client.getState(playerId);
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Abrufen des Spielstands: " + e.getMessage(), e);
        }
    }

    @Override
    public long ping() {
        // Ping ist im JMS-Modus nicht als synchroner Benchmark vorgesehen.
        return 0;
    }

    @Override
    public String getStateJson(int playerId) throws GameRuleException {
        throw new UnsupportedOperationException("getStateJson wird im JMS-Stub nicht unterstuetzt");
    }

    @Override
    public String executeJson(String commandJson) throws GameRuleException {
        throw new UnsupportedOperationException("executeJson wird im JMS-Stub nicht unterstuetzt");
    }

    @Override
    public void addListener(GameListener listener) {
        client.registerListener(listener);
    }
}
