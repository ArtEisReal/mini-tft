import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.server.UnicastRemoteObject;

/**
 * Lokale, nichtverteilte Swing-Variante des Spiels.
 *
 * SwingMain verwendet hier direkt die GameEngine-Objektreferenz. Es gibt
 * keinen RMI-Lookup, keine Registry und keinen Client-Server-Netzwerkaufruf.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        String name0 = args.length > 0 ? args[0] : "Spieler 1";
        String name1 = args.length > 1 ? args[1] : "Spieler 2";

        SwingUtilities.invokeLater(() -> {
            try {
                GameEngine engine = new GameEngine(name0, name1);
                SwingMain ui = new SwingMain(engine, "lokal");

                // Der Listener wird vor dem Rundenstart registriert, damit
                // auch das erste Serverereignis lokal empfangen wird.
                engine.addListener(ui);
                engine.start();

                ui.setVisible(true);

                ui.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) {
                        try {
                            UnicastRemoteObject.unexportObject(engine, true);
                        } catch (java.rmi.NoSuchObjectException ignored) {
                            // Die Engine wurde bereits beendet.
                        }
                    }
                });
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Lokales Spiel konnte nicht gestartet werden:\n" + e.getMessage(),
                        "Startfehler",
                        JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        });
    }
}
