import java.rmi.RemoteException;

/**
 * Design Pattern: Command – jede Konsoleneingabe wird zu einem Command-Objekt,
 * das gegen den GameService ausgefuehrt wird (lokal direkt, spaeter ueber den Stub).
 * 
 * RMI-Anpassung: execute wirft auch RemoteException fuer Remote-Aufrufe.
 */
// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
public interface Command {
// AI-GENERATED-END
    PlayerDto execute(GameService service, int playerId) throws GameRuleException, RemoteException;

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Wandelt eine Eingabezeile in ein Command um. */
    static Command parse(String line) {
        String[] parts = line.trim().toLowerCase().split("\\s+");
        try {
            switch (parts[0]) {
                case "buy":
                    int slot = Integer.parseInt(parts[1]);
                    return (s, id) -> s.buy(id, slot);
                case "sell":
                    int idx = Integer.parseInt(parts[1]);
                    return (s, id) -> s.sell(id, idx);
                case "sellbench":
                    int bslot = Integer.parseInt(parts[1]);
                    return (s, id) -> s.sellBench(id, bslot);
                case "tofield":
                    int bench = Integer.parseInt(parts[1]);
                    int row = Integer.parseInt(parts[2]);
                    int col = Integer.parseInt(parts[3]);
                    return (s, id) -> s.toField(id, bench, row, col);
                case "tobench":
                    int pos = Integer.parseInt(parts[1]);
                    return (s, id) -> s.toBench(id, pos);
                case "move":
                    int fpos = Integer.parseInt(parts[1]);
                    int fr = Integer.parseInt(parts[2]);
                    int fc = Integer.parseInt(parts[3]);
                    return (s, id) -> s.move(id, fpos, fr, fc);
                case "levelup":
                    return (s, id) -> s.levelUp(id);
                case "reroll":
                    return (s, id) -> s.reroll(id);
                case "ready":
                    return (s, id) -> s.ready(id);
                default:
                    throw new IllegalArgumentException("Unbekannter Befehl: " + parts[0]);
            }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("Falsche Parameter. Beispiel: buy 1");
        }
    }
}
// AI-GENERATED-END
