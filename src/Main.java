import java.util.List;
import java.util.Scanner;

/**
 * Lokale Konsolen-Anwendung (Hotseat, beide Spieler an einer Konsole).
 * Spaeter: Client = dieser Teil, Server = GameEngine.
 */
public class Main implements GameListener {

    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        GameEngine engine = new GameEngine("Spieler1", "Spieler2");
        engine.addListener(new Main());
        engine.start();

        System.out.println("Mini-TFT. Befehle: buy <shop> | levelup | reroll | ready | quit");
        System.out.println("  tofield <bench> <zeile> <spalte> | tobench <feld-pos> | move <feld-pos> <zeile> <spalte>");
        System.out.println("  sell <feld-pos> | sellbench <bench>");

        while (true) {
            for (int pid = 0; pid < 2; pid++) {
                GameStateDto st = engine.getState(pid);
                while (!st.gameOver() && !st.self().ready()) {
                    print(st);
                    System.out.print("[" + st.self().name() + "] > ");
                    if (!in.hasNextLine()) return;
                    String line = in.nextLine().trim();
                    if (line.equals("quit")) return;
                    try {
                        Command.parse(line).execute(engine, pid);
                    } catch (GameRuleException | IllegalArgumentException e) {
                        System.out.println("Fehler: " + e.getMessage());
                    }
                    st = engine.getState(pid);
                }
            }
            if (engine.getState(0).gameOver()) break;
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
    public void onCombatFrames(int round, List<CombatFrameDto> frames) {
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
    public void onUnitCombined(int playerId, String unitName, int star) {
        System.out.println("*** Spieler" + (playerId + 1) + ": 3x " + unitName + " vereinigt -> "
                + UnitDto.starLabel(unitName, star) + " ***");
    }

    @Override
    public void onGameOver(String winnerName) {
        System.out.println("\n*** SPIEL VORBEI - Gewinner: " + winnerName + " ***");
    }
}
