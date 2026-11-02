import java.rmi.RemoteException;
import java.util.List;
import java.util.Scanner;

/**
 * Konsolen-Client für Mini-TFT.
 * Verbindet sich per RMI zum Server und führt Befehle remote aus.
 * 
 * Verwendung:
 *   java Main [host]
 *   Standard: "localhost"
 * 
 * Befehle: buy <shop> | levelup | reroll | ready | quit
 *   tofield <bench> <zeile> <spalte> | tobench <feld-pos> | move <feld-pos> <zeile> <spalte>
 *   sell <feld-pos> | sellbench <bench>
 */
public class Main implements GameListener {

    public static void main(String[] args) throws Exception {
        // AI-GENERATED-START: Server-Host-Resolution (Argument / minitft.server / localhost) + Fehlerhinweis - 2026-11-02 12:41 CET
        // Server-Host: erst Argument, sonst minitft.server/MINITFT_SERVER, sonst "localhost"
        String host = RmiConfig.serverHost(args);

        // RMI-Client verbinden
        RmiClient client = new RmiClient(host);
        try {
            client.connect();
        } catch (java.rmi.ConnectException e) {
            System.err.println("Server nicht erreichbar unter " + host + ":" + RmiConfig.REGISTRY_PORT + ".");
            System.err.println("Starte zuerst den Server:  java -Dminitft.host=<eigene-IP> RmiServer");
            return;
        }
        // AI-GENERATED-END
        GameService service = client.getService();

        // Listener registrieren (bidirektionale Kommunikation)
        Main listener = new Main();
        client.registerListener(listener);

        Scanner in = new Scanner(System.in);
        System.out.println("Verbunden mit Server: " + host);
        System.out.println("Mini-TFT. Befehle: buy <shop> | levelup | reroll | ready | quit");
        System.out.println("  tofield <bench> <zeile> <spalte> | tobench <feld-pos> | move <feld-pos> <zeile> <spalte>");
        System.out.println("  sell <feld-pos> | sellbench <bench>");

        try {
            while (true) {
                for (int pid = 0; pid < 2; pid++) {
                    GameStateDto st = service.getState(pid);
                    while (!st.gameOver() && !st.self().ready()) {
                        print(st);
                        System.out.print("[" + st.self().name() + "] > ");
                        if (!in.hasNextLine()) {
                            client.close();
                            return;
                        }
                        String line = in.nextLine().trim();
                        if (line.equals("quit")) {
                            client.close();
                            return;
                        }
                        try {
                            Command.parse(line).execute(service, pid);
                        } catch (GameRuleException | IllegalArgumentException e) {
                            System.out.println("Fehler: " + e.getMessage());
                        } catch (RemoteException e) {
                            System.out.println("Verbindungsfehler: " + e.getMessage());
                            client.close();
                            return;
                        }
                        st = service.getState(pid);
                    }
                }
                if (service.getState(0).gameOver()) break;
            }
        } finally {
            client.close();
        }
    }

    private static void print(GameStateDto s) {
        PlayerDto me = s.self(), op = s.opponent();
        System.out.printf("%n=== Runde %d | %s: %d HP, %d Gold, Level %d | %s: %d HP ===%n",
                s.round(), me.name(), me.hp(), me.gold(), me.level(), op.name(), op.hp());
        System.out.printf("Feld (%d/%d):%n", me.board().size(), me.level());
        printUnits(me.board());
        System.out.printf("Bench (%d/%d):%n", me.bench().size(), GameEngine.BENCH_SIZE);
        printUnits(me.bench());
        System.out.println("Shop:");
        printUnits(me.shop());
    }

    private static void printUnits(List<UnitDto> units) {
        if (units.isEmpty()) System.out.println("  (leer)");
        for (int i = 0; i < units.size(); i++) {
            UnitDto u = units.get(i);
            System.out.printf("  [%d] %-16s Kosten %d | HP %d | ATK %d | Stern %d | %s%n",
                    i + 1, u.name(), u.cost(), u.hp(), u.attack(), u.star(),
                    u.row() < 0 ? "Bench" : "Feld " + u.row() + "," + u.col());
        }
    }

    // ---------- GameListener (Callbacks vom "Server") ----------

    @Override
    public void onRoundStarted(int round) {
        System.out.println("\n--- Runde " + round + " beginnt ---");
    }

    @Override
    public void onCombatFrames(int round, List<CombatFrameDto> frames) throws RemoteException {
        System.out.println("(Kampf mit " + frames.size() + " Ticks auf dem Brett)");
    }

    @Override
    public void onCombatFinished(CombatResultDto r) {
        System.out.println("\n##### KAMPF Runde " + r.round() + " #####");
        r.log().forEach(System.out::println);
        System.out.println(r.winnerId() < 0 ? "Unentschieden!" : "Sieger: Spieler" + (r.winnerId() + 1)
                + " (Gegner verliert " + r.damage() + " HP)");
    }

    @Override
    public void onUnitCombined(int playerId, String unitName, int star) throws RemoteException {
        System.out.println("*** Spieler" + (playerId + 1) + ": 3x " + unitName + " vereinigt -> "
                + UnitDto.starLabel(unitName, star) + " ***");
    }

    @Override
    public void onGameOver(String winnerName) {
        System.out.println("\n*** SPIEL VORBEI - Gewinner: " + winnerName + " ***");
    }
}
