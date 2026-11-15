import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * RMI-Adapter fuer GameService.
 *
 * Implementiert das Remote-Interface und delegiert alle Aufrufe an eine
 * lokale GameEngine-Instanz. So bleibt die Spiellogik technologieunabhaengig.
 */
public interface RmiGameService extends Remote {
    PlayerDto buy(int playerId, int slot) throws GameRuleException, RemoteException;
    PlayerDto sell(int playerId, int index) throws GameRuleException, RemoteException;
    PlayerDto sellBench(int playerId, int slot) throws GameRuleException, RemoteException;
    PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException, RemoteException;
    PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException, RemoteException;
    PlayerDto toBench(int playerId, int index) throws GameRuleException, RemoteException;
    PlayerDto levelUp(int playerId) throws GameRuleException, RemoteException;
    PlayerDto reroll(int playerId) throws GameRuleException, RemoteException;
    PlayerDto ready(int playerId) throws GameRuleException, RemoteException;
    GameStateDto getState(int playerId) throws GameRuleException, RemoteException;
    long ping() throws RemoteException;
    String getStateJson(int playerId) throws GameRuleException, RemoteException;
    String executeJson(String commandJson) throws GameRuleException, RemoteException;
    void addListener(RmiGameListener listener) throws RemoteException;
}

/**
 * RMI-Adapter fuer GameListener.
 *
 * Erweitert Remote, damit der Client einen Listener als Remote-Objekt exportieren kann.
 */
interface RmiGameListener extends Remote {
    void onRoundStarted(int round) throws RemoteException;
    void onCombatFrames(int round, List<CombatFrameDto> frames) throws RemoteException;
    void onUnitCombined(int playerId, String unitName, int star) throws RemoteException;
    void onCombatFinished(CombatResultDto result) throws RemoteException;
    void onTurnChanged(int turn) throws RemoteException;
    void onGameOver(String winnerName) throws RemoteException;
}

/**
 * Implementiert RmiGameService und delegiert an eine GameEngine.
 * Wird von RmiServer exportiert.
 */
class RmiGameServiceImpl extends UnicastRemoteObject implements RmiGameService {

    private final GameEngine engine;

    protected RmiGameServiceImpl(GameEngine engine, int port) throws RemoteException {
        super(port);
        this.engine = engine;
    }

    @Override
    public PlayerDto buy(int playerId, int slot) throws GameRuleException, RemoteException {
        return engine.buy(playerId, slot);
    }

    @Override
    public PlayerDto sell(int playerId, int index) throws GameRuleException, RemoteException {
        return engine.sell(playerId, index);
    }

    @Override
    public PlayerDto sellBench(int playerId, int slot) throws GameRuleException, RemoteException {
        return engine.sellBench(playerId, slot);
    }

    @Override
    public PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException, RemoteException {
        return engine.move(playerId, index, row, col);
    }

    @Override
    public PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException, RemoteException {
        return engine.toField(playerId, benchSlot, row, col);
    }

    @Override
    public PlayerDto toBench(int playerId, int index) throws GameRuleException, RemoteException {
        return engine.toBench(playerId, index);
    }

    @Override
    public PlayerDto levelUp(int playerId) throws GameRuleException, RemoteException {
        return engine.levelUp(playerId);
    }

    @Override
    public PlayerDto reroll(int playerId) throws GameRuleException, RemoteException {
        return engine.reroll(playerId);
    }

    @Override
    public PlayerDto ready(int playerId) throws GameRuleException, RemoteException {
        return engine.ready(playerId);
    }

    @Override
    public GameStateDto getState(int playerId) throws GameRuleException, RemoteException {
        return engine.getState(playerId);
    }

    @Override
    public long ping() throws RemoteException {
        return engine.ping();
    }

    @Override
    public String getStateJson(int playerId) throws GameRuleException, RemoteException {
        return engine.getStateJson(playerId);
    }

    @Override
    public String executeJson(String commandJson) throws GameRuleException, RemoteException {
        return engine.executeJson(commandJson);
    }

    @Override
    public void addListener(RmiGameListener listener) throws RemoteException {
        // RmiGameListener in GameListener verpacken und an Engine registrieren
        engine.addListener(new GameListenerAdapter(listener));
    }

    /**
     * Verpackt einen RmiGameListener als GameListener.
     */
    private static class GameListenerAdapter implements GameListener {
        private final RmiGameListener delegate;

        GameListenerAdapter(RmiGameListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onRoundStarted(int round) {
            try { delegate.onRoundStarted(round); }
            catch (RemoteException e) {
                System.err.println("RMI-Callback onRoundStarted fehlgeschlagen: " + e.getMessage());
            }
        }

        @Override
        public void onCombatFrames(int round, List<CombatFrameDto> frames) {
            try { delegate.onCombatFrames(round, frames); }
            catch (RemoteException e) {
                System.err.println("RMI-Callback onCombatFrames fehlgeschlagen: " + e.getMessage());
            }
        }

        @Override
        public void onUnitCombined(int playerId, String unitName, int star) {
            try { delegate.onUnitCombined(playerId, unitName, star); }
            catch (RemoteException e) {
                System.err.println("RMI-Callback onUnitCombined fehlgeschlagen: " + e.getMessage());
            }
        }

        @Override
        public void onCombatFinished(CombatResultDto result) {
            try { delegate.onCombatFinished(result); }
            catch (RemoteException e) {
                System.err.println("RMI-Callback onCombatFinished fehlgeschlagen: " + e.getMessage());
            }
        }

        @Override
        public void onTurnChanged(int turn) {
            try { delegate.onTurnChanged(turn); }
            catch (RemoteException e) {
                System.err.println("RMI-Callback onTurnChanged fehlgeschlagen: " + e.getMessage());
            }
        }

        @Override
        public void onGameOver(String winnerName) {
            try { delegate.onGameOver(winnerName); }
            catch (RemoteException e) {
                System.err.println("RMI-Callback onGameOver fehlgeschlagen: " + e.getMessage());
            }
        }
    }
}
