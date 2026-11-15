import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;

/**
 * Startet den RMI-Server für Mini-TFT.
 * 
 * Erstellt eine RMI-Registry auf Port 1099, instanziert einen GameEngine
 * und registriert ihn unter dem Namen "mini-tft".
 * 
 * Verwendung (lokal, gleicher Rechner):
 *   java RmiServer [Spieler0] [Spieler1]
 *   Standard: "Spieler 0" und "Spieler 1"
 * 
 * Verwendung (Server auf anderem Rechner, z.B. im VPN):
 *   java -Dminitft.host=<eigene-VPN-IP> RmiServer [Spieler0] [Spieler1]
 */
public class RmiServer {
    private static final String DEFAULT_SERVER_HOST = "10.8.3.2";
    static final int PORT = RmiConfig.REGISTRY_PORT;
    static final String BIND_NAME = RmiConfig.BIND_NAME;

    public static void main(String[] args) {
        try {
            if (System.getProperty("minitft.host") == null) {
                System.setProperty("minitft.host", DEFAULT_SERVER_HOST);
            }
            RmiConfig.applyLocalHost();
            String localHost = RmiConfig.localHost();
            int enginePort = RmiConfig.port("minitft.enginePort", RmiConfig.ENGINE_PORT);

            String name0 = args.length > 0 ? args[0] : "Spieler 0";
            String name1 = args.length > 1 ? args[1] : "Spieler 1";

            try {
                LocateRegistry.createRegistry(PORT);
                System.out.println("RMI-Registry auf Port " + PORT + " gestartet.");
            } catch (Exception e) {
                System.out.println("RMI-Registry auf Port " + PORT + " wird verwendet (bereits vorhanden).");
            }

            // GameEngine erstellen und in RmiGameServiceImpl verpacken
            GameEngine engine = new GameEngine(name0, name1);
            System.out.println("GameEngine für " + name0 + " und " + name1 + " erstellt.");

            RmiGameService rmiService;
            try {
                rmiService = new RmiGameServiceImpl(engine, enginePort);
                System.out.println("RmiGameService auf Port " + enginePort + " exportiert.");
            } catch (ExportException e) {
                System.err.println("Port " + enginePort + " ist belegt - RmiGameService auf anonymem Port.");
                try {
                    rmiService = new RmiGameServiceImpl(engine, 0);
                } catch (java.rmi.RemoteException e2) {
                    throw new RuntimeException("Konnte RmiGameService nicht exportieren", e2);
                }
            }

            String url = "rmi://localhost:" + PORT + "/" + BIND_NAME;
            Naming.rebind(url, rmiService);
            System.out.println("Server läuft unter: " + url);
            System.out.println("Erreichbar für Clients: rmi://"
                    + (localHost != null ? localHost : "<diese-IP>") + ":" + PORT + "/" + BIND_NAME);
            System.out.println("Firewall (eingehend): TCP " + PORT + " (Registry), TCP " + enginePort + " (GameEngine)");
            System.out.println("Warte auf Client-Verbindungen...");

            engine.start();

        } catch (Exception e) {
            System.err.println("Fehler beim Starten des Servers:");
            e.printStackTrace();
        }
    }
}
