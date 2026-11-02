import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

// AI-GENERATED-START: Javadoc für Netzwerkbetrieb ergänzt (Host-Property, fester Listener-Port) - 2026-11-02 12:41 CET
/**
 * Client-Seite für Mini-TFT RMI-Kommunikation.
 * 
 * Verbindet sich zum RMI-Server und liefert einen GameService-Stub.
 * Zusätzlich exportiert er einen GameListener-Stub für Callbacks vom Server.
 * 
 * Verwendung:
 *   RmiClient client = new RmiClient("10.8.0.2");   // Server-Host im VPN
 *   GameService service = client.getService();
 *   client.registerListener();  // Listener beim Server registrieren
 *   
 *   // Später:
 *   client.close();
 * 
 * Für den Betrieb auf verschiedenen Rechnern:
 *   - Der eigene Host (für die RMI-Stubs) kommt aus minitft.host / MINITFT_HOST
 *     und wird im Konstruktor nach java.rmi.server.hostname übertragen.
 *   - Der Listener-Objekt wird auf festem Port exportiert (Standard 1098,
 *     per -Dminitft.listenerPort änderbar), damit der Server nur eine
 *     Firewall-Regel braucht: TCP 1098 -> Client.
 */
// AI-GENERATED-END
public class RmiClient {
    // AI-GENERATED-START: Konstanten auf RmiConfig umgestellt - 2026-11-02 12:41 CET
    static final int PORT = RmiConfig.REGISTRY_PORT;
    static final String BIND_NAME = RmiConfig.BIND_NAME;
    // AI-GENERATED-END

    private final String host;
    private GameService service;
    private GameListenerProxy listenerProxy;

    /**
     * Erzeugt einen Client, der sich an den angegebenen Host verbindet.
     * @param host Hostname oder IP-Adresse des Servers (z.B. "localhost" oder "192.168.1.100")
     */
    public RmiClient(String host) {
        this.host = host;
        // AI-GENERATED-START: eigene Host-Adresse für den Listener-Stub setzen - 2026-11-02 12:41 CET
        // Eigene Adresse für den Listener-Stub (Server ruft zurück!) - vor jedem Export
        RmiConfig.applyLocalHost();
        // AI-GENERATED-END
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
        // AI-GENERATED-START: Ausgabe des tatsächlich belegten Listener-Ports - 2026-11-02 12:41 CET
        System.out.println("Listener beim Server registriert (eigener Port " + listenerProxy.port() + ").");
        System.out.println("Hinweis: Der Server muss diesen Port erreichen können (Firewall, TCP "
                + listenerProxy.port() + ").");
        // AI-GENERATED-END
    }

    /**
     * Schließt die Verbindung und entfernt den Listener vom Server.
     */
    public void close() {
        GameListenerProxy proxy = listenerProxy;
        listenerProxy = null;
        if (proxy != null) {
            try {
                proxy.unexport();
            } catch (java.rmi.NoSuchObjectException e) {
                // Bereits deexportiert (z.B. close() wurde mehrfach aufgerufen) - ignorieren
            } catch (RemoteException e) {
                System.err.println("Fehler beim Deexportieren des Listeners: " + e.getMessage());
            }
        }
        service = null;
    }

    // AI-GENERATED-START: Proxy ohne UnicastRemoteObject-Erbe, Export auf festem Port mit Fallback - 2026-11-02 12:41 CET
    /**
     * Proxy-Klasse, die einen lokalen GameListener als Remote-Objekt exportiert.
     * Damit kann der Server Callbacks an den Client senden (bidirektionale Kommunikation).
     *
     * Export auf festem Port (minitft.listenerPort, Standard 1098 -> siehe RmiConfig),
     * damit nur eine Firewall-Regel noetig ist. Ist der Port belegt, wird auf einen
     * anonymen Port zurückgefallen (Warnung mit dem tatsächlichen Port).
     */
    private static class GameListenerProxy implements GameListener {
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

        /** Tatsächlich belegter Port (0 = anonymer Port, siehe Ausgabe beim Start). */
        int port() {
            return port;
        }
        // AI-GENERATED-END

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
