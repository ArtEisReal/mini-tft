/**
 * Serverseitiges Skeleton der JMS-Kommunikationsschicht.
 *
 * Das Skeleton implementiert denselben {@link GameService}-Vertrag wie die
 * Anwendung ({@link GameEngine}). Der JMS-Server ruft nur noch diese Klasse
 * auf; die eigentliche Spiellogik bleibt vollstaendig in der GameEngine.
 *
 * Damit ist die Kommunikationsschicht (Stub/Skeleton) sauber von der
 * Anwendung getrennt:
 *
 *   Client-GUI -- GameService --> JmsGameServiceStub -- JmsClient --> Broker
 *   Broker --> JmsServer --> JmsGameSkeleton -- GameService --> GameEngine
 */
public final class JmsGameSkeleton implements GameService {

    private final GameEngine engine;

    public JmsGameSkeleton(GameEngine engine) {
        this.engine = engine;
    }

    /** Zugriff auf die Engine fuer Snapshots/Replay (nur serverseitig). */
    public GameEngine engine() {
        return engine;
    }

    /**
     * Uebersetzt einen Befehl aus einer JMS-Nachricht in einen GameService-Aufruf.
     */
    public PlayerDto dispatch(String action, int playerId, int[] args) throws GameRuleException {
        return switch (action) {
            case "buy" -> buy(playerId, args[0]);
            case "sell" -> sell(playerId, args[0]);
            case "sellbench" -> sellBench(playerId, args[0]);
            case "tofield" -> toField(playerId, args[0], args[1], args[2]);
            case "tobench" -> toBench(playerId, args[0]);
            case "move" -> move(playerId, args[0], args[1], args[2]);
            case "levelup" -> levelUp(playerId);
            case "reroll" -> reroll(playerId);
            case "ready" -> ready(playerId);
            default -> throw new GameRuleException("Unbekannter Befehl: \"" + action + "\"");
        };
    }

    @Override
    public PlayerDto buy(int playerId, int slot) throws GameRuleException {
        return engine.buy(playerId, slot);
    }

    @Override
    public PlayerDto sell(int playerId, int index) throws GameRuleException {
        return engine.sell(playerId, index);
    }

    @Override
    public PlayerDto sellBench(int playerId, int slot) throws GameRuleException {
        return engine.sellBench(playerId, slot);
    }

    @Override
    public PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException {
        return engine.move(playerId, index, row, col);
    }

    @Override
    public PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException {
        return engine.toField(playerId, benchSlot, row, col);
    }

    @Override
    public PlayerDto toBench(int playerId, int index) throws GameRuleException {
        return engine.toBench(playerId, index);
    }

    @Override
    public PlayerDto levelUp(int playerId) throws GameRuleException {
        return engine.levelUp(playerId);
    }

    @Override
    public PlayerDto reroll(int playerId) throws GameRuleException {
        return engine.reroll(playerId);
    }

    @Override
    public PlayerDto ready(int playerId) throws GameRuleException {
        return engine.ready(playerId);
    }

    @Override
    public GameStateDto getState(int playerId) throws GameRuleException {
        return engine.getState(playerId);
    }

    @Override
    public long ping() {
        return engine.ping();
    }

    @Override
    public String getStateJson(int playerId) throws GameRuleException {
        return engine.getStateJson(playerId);
    }

    @Override
    public String executeJson(String commandJson) throws GameRuleException {
        return engine.executeJson(commandJson);
    }

    @Override
    public void addListener(GameListener listener) {
        engine.addListener(listener);
    }
}
