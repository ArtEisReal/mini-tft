import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
// AI-GENERATED-START: neuer Import für den Port-Fallback - 2026-11-02 12:41 CET
import java.rmi.server.ExportException;
// AI-GENERATED-END

// AI-GENERATED-START: Javadoc um Netzwerk-/VPN-Betrieb ergänzt (Host-Property, Ports) - 2026-11-02 12:41 CET
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
 * 
 *   minitft.host setzt java.rmi.server.hostname. OHNE diese Angabe landet ein
 *   lokaler Name (z.B. "MacBook-Pro.local") in den RMI-Stubs, den der andere
 *   Rechner nicht aufloesen kann -> Lookup schlaegt fehl.
 * 
 *   Firewall, eingehend erlauben:
 *     TCP 1099  Registry (RmiConfig.REGISTRY_PORT)
 *     TCP 1100  GameEngine (RmiConfig.ENGINE_PORT, per -Dminitft.enginePort aenderbar)
 * 
 *   Clients verbinden sich mit:
 *     java SwingMain <diese-IP>
 */
// AI-GENERATED-END
public class RmiServer {
    /** Eigene IP-Adresse des Rechners, auf dem der Server läuft. */
    private static final String DEFAULT_SERVER_HOST = "10.8.3.3";
    // AI-GENERATED-START: Konstanten auf RmiConfig umgestellt - 2026-11-02 12:41 CET
    static final int PORT = RmiConfig.REGISTRY_PORT;
    static final String BIND_NAME = RmiConfig.BIND_NAME;
    // AI-GENERATED-END

    public static void main(String[] args) {
        try {
            // Für den IntelliJ-Start ohne -D-Argumente. Bei Bedarf hier ändern.
            if (System.getProperty("minitft.host") == null) {
                System.setProperty("minitft.host", DEFAULT_SERVER_HOST);
            }
            // AI-GENERATED-START: eigene Host-Adresse + fester Engine-Port (muss VOR dem Export passieren) - 2026-11-02 12:41 CET
            // Eigene Adresse in die RMI-Stubs schreiben - muss VOR dem Export passieren
            RmiConfig.applyLocalHost();
            String localHost = RmiConfig.localHost();
            int enginePort = RmiConfig.port("minitft.enginePort", RmiConfig.ENGINE_PORT);
            // AI-GENERATED-END

            String name0 = args.length > 0 ? args[0] : "Spieler 0";
            String name1 = args.length > 1 ? args[1] : "Spieler 1";

            // RMI Registry erstellen (falls noch nicht vorhanden)
            try {
                LocateRegistry.createRegistry(PORT);
                System.out.println("RMI-Registry auf Port " + PORT + " gestartet.");
            } catch (Exception e) {
                // Registry läuft bereits – das ist in Ordnung
                System.out.println("RMI-Registry auf Port " + PORT + " wird verwendet (bereits vorhanden).");
            }

            // AI-GENERATED-START: GameEngine auf festem Port, Fallback auf anonymen Port - 2026-11-02 12:41 CET
            // GameEngine als Remote-Objekt erstellen (fester Port -> eine Firewall-Regel)
            GameEngine engine;
            try {
                engine = new GameEngine(name0, name1, enginePort);
                System.out.println("GameEngine auf Port " + enginePort + " exportiert.");
            } catch (ExportException e) {
                System.err.println("Port " + enginePort + " ist belegt - GameEngine auf anonymem Port.");
                System.err.println("(Firewall dann für den tatsächlichen Port öffnen: lsof -nP -iTCP -sTCP:LISTEN | grep java)");
                engine = new GameEngine(name0, name1);
            }
            // AI-GENERATED-END
            System.out.println("GameEngine für " + name0 + " und " + name1 + " erstellt.");

            // In der Registry registrieren
            String url = "rmi://localhost:" + PORT + "/" + BIND_NAME;
            Naming.rebind(url, engine);
            System.out.println("Server läuft unter: " + url);
            // AI-GENERATED-START: Hinweise für Clients und Firewall - 2026-11-02 12:41 CET
            System.out.println("Erreichbar für Clients: rmi://"
                    + (localHost != null ? localHost : "<diese-IP>") + ":" + PORT + "/" + BIND_NAME);
            System.out.println("Firewall (eingehend): TCP " + PORT + " (Registry), TCP " + enginePort + " (GameEngine)");
            // AI-GENERATED-END
            System.out.println("Warte auf Client-Verbindungen...");

            // Spiel starten
            engine.start();

        } catch (Exception e) {
            System.err.println("Fehler beim Starten des Servers:");
            e.printStackTrace();
        }
    }
}
