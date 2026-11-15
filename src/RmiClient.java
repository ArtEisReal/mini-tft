import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import javax.swing.SwingUtilities;

/**
 * Client-Seite für Mini-TFT RMI-Kommunikation.
 * 
 * Verbindet sich zum RMI-Server und liefert einen RmiGameService-Stub.
 * Zusätzlich exportiert er einen RmiGameListener-Stub für Callbacks vom Server.
 */
public class RmiClient {
    static final int PORT = RmiConfig.REGISTRY_PORT;
    static final String BIND_NAME = RmiConfig.BIND_NAME;

    private final String host;
    private RmiGameService service;
    private GameListenerProxy listenerProxy;

    public RmiClient(String host) {
        this.host = host;
        RmiConfig.applyLocalHost();
    }

    public void connect() throws Exception {
        String url = "rmi://" + host + ":" + PORT + "/" + BIND_NAME;
        service = (RmiGameService) Naming.lookup(url);
        System.out.println("Verbunden mit Server: " + url);
    }

    /**
     * Liefert den GameService-Stub (delegiert an RmiGameService).
     */
    public GameService getService() {
        if (service == null) {
            throw new IllegalStateException("Noch nicht verbunden. Rufe zuerst connect() auf.");
        }
        return new RmiServiceAdapter(service);
    }

    public void registerListener(GameListener listener) throws Exception {
        listenerProxy = new GameListenerProxy(listener);
        service.addListener(listenerProxy);
        System.out.println("Listener beim Server registriert (eigener Port " + listenerProxy.port() + ").");
        System.out.println("Hinweis: Der Server muss diesen Port erreichen können (Firewall, TCP "
                + listenerProxy.port() + ").");
    }

    public void close() {
        GameListenerProxy proxy = listenerProxy;
        listenerProxy = null;
        if (proxy != null) {
            try {
                proxy.unexport();
            } catch (java.rmi.NoSuchObjectException e) {
                // Bereits deexportiert
            } catch (RemoteException e) {
                System.err.println("Fehler beim Deexportieren des Listeners: " + e.getMessage());
            }
        }
        service = null;
    }

    /**
     * Adapter: RmiGameService -> GameService (technologieunabhängig).
     */
    private static class RmiServiceAdapter implements GameService {
        private final RmiGameService delegate;

        RmiServiceAdapter(RmiGameService delegate) {
            this.delegate = delegate;
        }

        @Override
        public PlayerDto buy(int playerId, int slot) throws GameRuleException {
            try { return delegate.buy(playerId, slot); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public PlayerDto sell(int playerId, int index) throws GameRuleException {
            try { return delegate.sell(playerId, index); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public PlayerDto sellBench(int playerId, int slot) throws GameRuleException {
            try { return delegate.sellBench(playerId, slot); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException {
            try { return delegate.move(playerId, index, row, col); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException {
            try { return delegate.toField(playerId, benchSlot, row, col); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public PlayerDto toBench(int playerId, int index) throws GameRuleException {
            try { return delegate.toBench(playerId, index); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public PlayerDto levelUp(int playerId) throws GameRuleException {
            try { return delegate.levelUp(playerId); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public PlayerDto reroll(int playerId) throws GameRuleException {
            try { return delegate.reroll(playerId); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public PlayerDto ready(int playerId) throws GameRuleException {
            try { return delegate.ready(playerId); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public GameStateDto getState(int playerId) throws GameRuleException {
            try { return delegate.getState(playerId); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public long ping() {
            try { return delegate.ping(); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public String getStateJson(int playerId) throws GameRuleException {
            try { return delegate.getStateJson(playerId); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public String executeJson(String commandJson) throws GameRuleException {
            try { return delegate.executeJson(commandJson); }
            catch (RemoteException e) { throw new RuntimeException("RMI-Fehler: " + e.getMessage(), e); }
        }

        @Override
        public void addListener(GameListener listener) {
            // Nicht verwendet — der Listener wird über registerListener() registriert
        }
    }

    /**
     * Proxy-Klasse, die einen lokalen GameListener als RmiGameListener exportiert.
     */
    private static class GameListenerProxy implements RmiGameListener {
        private final GameListener delegate;
        private int port;

        GameListenerProxy(GameListener delegate) throws RemoteException {
            this.delegate = delegate;
            int wanted = RmiConfig.port("minitft.listenerPort", RmiConfig.LISTENER_PORT);
            try {
                UnicastRemoteObject.exportObject(this, wanted);
                this.port = wanted;
            } catch (java.rmi.server.ExportException e) {
                if (wanted == 0) throw e;
                System.err.println("Port " + wanted + " für den Listener belegt - wechsle zu einem anonymen Port.");
                UnicastRemoteObject.exportObject(this, 0);
                this.port = 0;
            }
        }

        int port() { return port; }

        @Override
        public void onRoundStarted(int round) throws RemoteException {
            final int r = round;
            SwingUtilities.invokeLater(() -> {
                try { delegate.onRoundStarted(r); }
                catch (Exception e) { System.err.println("Callback onRoundStarted fehlgeschlagen: " + e.getMessage()); }
            });
        }

        @Override
        public void onCombatFrames(int round, java.util.List<CombatFrameDto> frames) throws RemoteException {
            final int r = round;
            final java.util.List<CombatFrameDto> f = frames;
            SwingUtilities.invokeLater(() -> {
                try { delegate.onCombatFrames(r, f); }
                catch (Exception e) { System.err.println("Callback onCombatFrames fehlgeschlagen: " + e.getMessage()); }
            });
        }

        @Override
        public void onUnitCombined(int playerId, String unitName, int star) throws RemoteException {
            final int pid = playerId;
            final String name = unitName;
            final int s = star;
            SwingUtilities.invokeLater(() -> {
                try { delegate.onUnitCombined(pid, name, s); }
                catch (Exception e) { System.err.println("Callback onUnitCombined fehlgeschlagen: " + e.getMessage()); }
            });
        }

        @Override
        public void onCombatFinished(CombatResultDto result) throws RemoteException {
            final CombatResultDto r = result;
            SwingUtilities.invokeLater(() -> {
                try { delegate.onCombatFinished(r); }
                catch (Exception e) { System.err.println("Callback onCombatFinished fehlgeschlagen: " + e.getMessage()); }
            });
        }

        @Override
        public void onTurnChanged(int turn) throws RemoteException {
            final int t = turn;
            SwingUtilities.invokeLater(() -> {
                try { delegate.onTurnChanged(t); }
                catch (Exception e) { System.err.println("Callback onTurnChanged fehlgeschlagen: " + e.getMessage()); }
            });
        }

        @Override
        public void onGameOver(String winnerName) throws RemoteException {
            final String name = winnerName;
            SwingUtilities.invokeLater(() -> {
                try { delegate.onGameOver(name); }
                catch (Exception e) { System.err.println("Callback onGameOver fehlgeschlagen: " + e.getMessage()); }
            });
        }

        void unexport() throws RemoteException {
            UnicastRemoteObject.unexportObject(this, true);
        }
    }
}
