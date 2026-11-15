import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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

                engine.addListener(ui);
                engine.start();

                ui.setVisible(true);

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
