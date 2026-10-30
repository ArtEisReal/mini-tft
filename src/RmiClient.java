import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-Seite für Mini-TFT RMI-Kommunikation.
 * 
 * Verbindet sich zum RMI-Server und liefert einen GameService-Stub.
 * Zusätzlich exportiert er einen GameListener-Stub für Callbacks vom Server.
 * 
 * Verwendung:
 *   RmiClient client = new RmiClient("localhost");
 *   GameService service = client.getService();
 *   client.registerListener();  // Listener beim Server registrieren
 *   
 *   // Später:
 *   client.close();
 */
public class RmiClient {
    static final int PORT = 1099;
    static final String BIND_NAME = "mini-tft";

    private final String host;
    private GameService service;
    private GameListenerProxy listenerProxy;

    /**
     * Erzeugt einen Client, der sich an den angegebenen Host verbindet.
     * @param host Hostname oder IP-Adresse des Servers (z.B. "localhost" oder "192.168.1.100")
     */
    public RmiClient(String host) {
        this.host = host;
    }

    /**
     * Verbindet sich zum RMI-Server und erhält den GameService-Stub.
     */
    public void connect() throws Exception {
        String url = "rmi://" + host + ":" + PORT + "/" + BIND_NAME;
        service = (GameService) Naming.lookup(url);
        System.out.println("Verbunden mit Server: " + url);
    }

    /**
     * Liefert den GameService-Stub für Remote-Aufrufe.
     * Muss nach connect() aufgerufen werden.
     */
    public GameService getService() {
        if (service == null) {
            throw new IllegalStateException("Noch nicht verbunden. Rufe zuerst connect() auf.");
        }
        return service;
    }

    /**
     * Registriert einen GameListener beim Server.
     * Der Listener wird als Remote-Objekt exportiert, sodass der Server
     * Callbacks an diesen Client senden kann.
     * 
     * @param listener Lokale Implementierung des GameListener, die Callbacks empfängt
     */
    public void registerListener(GameListener listener) throws Exception {
        listenerProxy = new GameListenerProxy(listener);
        service.addListener(listenerProxy);
        System.out.println("Listener beim Server registriert.");
    }

    /**
     * Schließt die Verbindung und entfernt den Listener vom Server.
     */
    public void close() {
        if (listenerProxy != null) {
            try {
                listenerProxy.unexport();
            } catch (RemoteException e) {
                System.err.println("Fehler beim Deexportieren des Listeners: " + e.getMessage());
            }
        }
        service = null;
    }

    /**
     * Proxy-Klasse, die einen lokalen GameListener als Remote-Objekt exportiert.
     * Damit kann der Server Callbacks an den Client senden (bidirektionale Kommunikation).
     */
    private static class GameListenerProxy extends UnicastRemoteObject implements GameListener {
        private final GameListener delegate;

        protected GameListenerProxy(GameListener delegate) throws RemoteException {
            super(0); // Port 0 = zufälliger freier Port
            this.delegate = delegate;
        }

        @Override
        public void onRoundStarted(int round) throws RemoteException {
            delegate.onRoundStarted(round);
        }

        @Override
        public void onCombatFrames(int round, java.util.List<CombatFrameDto> frames) throws RemoteException {
            delegate.onCombatFrames(round, frames);
        }

        @Override
        public void onUnitCombined(int playerId, String unitName, int star) throws RemoteException {
            delegate.onUnitCombined(playerId, unitName, star);
        }

        @Override
        public void onCombatFinished(CombatResultDto result) throws RemoteException {
            delegate.onCombatFinished(result);
        }

        @Override
        public void onGameOver(String winnerName) throws RemoteException {
            delegate.onGameOver(winnerName);
        }

        void unexport() throws RemoteException {
            UnicastRemoteObject.unexportObject(this, true);
        }
    }
}
