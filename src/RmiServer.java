import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

/**
 * Startet den RMI-Server für Mini-TFT.
 * 
 * Erstellt eine RMI-Registry auf Port 1099, instanziert einen GameEngine
 * und registriert ihn unter dem Namen "mini-tft".
 * 
 * Verwendung:
 *   1. RmiServer.main() starten (z.B. in einer Konsole)
 *   2. Clients können sich dann mit "rmi://<host>:1099/mini-tft" verbinden
 * 
 * Die Spielername können als Argumente übergeben werden:
 *   java RmiServer [Spieler0] [Spieler1]
 *   Standard: "Spieler 0" und "Spieler 1"
 */
public class RmiServer {
    static final int PORT = 1099;
    static final String BIND_NAME = "mini-tft";

    public static void main(String[] args) {
        try {
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

            // GameEngine als Remote-Objekt erstellen
            GameEngine engine = new GameEngine(name0, name1);
            System.out.println("GameEngine für " + name0 + " und " + name1 + " erstellt.");

            // In der Registry registrieren
            String url = "rmi://localhost:" + PORT + "/" + BIND_NAME;
            Naming.rebind(url, engine);
            System.out.println("Server läuft unter: " + url);
            System.out.println("Warte auf Client-Verbindungen...");

            // Spiel starten
            engine.start();

        } catch (Exception e) {
            System.err.println("Fehler beim Starten des Servers:");
            e.printStackTrace();
        }
    }
}
