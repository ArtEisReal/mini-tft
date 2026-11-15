// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.util.List;
import java.util.Random;

/** Design Pattern: Factory – erzeugt zufällige Einheiten für den Shop. */
public class UnitFactory {
    private record Template(String name, int cost, int hp, int attack, int range) {}

    private static final List<Template> TEMPLATES = List.of(
            new Template("Ritter", 1, 60, 6, 1),
            new Template("Bogenschuetze", 1, 35, 9, 3),
            new Template("Magier", 2, 40, 13, 3),
            new Template("Assassine", 2, 45, 12, 1),
            new Template("Golem", 3, 120, 8, 1),
            new Template("Drache", 4, 90, 20, 3));

    /**
     * Deterministischer Zufallsgenerator (xorshift64*).
     *
     * Anders als java.util.Random laesst sich der interne Zustand auslesen und
     * wiederherstellen. Das ist fuer die Verzoegerungskompensation noetig:
     * beim Replay muessen Shop-Neuziehungen exakt reproduzierbar sein.
     */
    private long rngState;

    /** Naechste freie Einheiten-ID (instanzlokal, damit Snapshots sie enthalten). */
    private int nextId = 1;

    public UnitFactory(Random random) {
        this(random.nextLong());
    }

    public UnitFactory(long seed) {
        this.rngState = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
    }

    public Unit randomUnit() {
        Template t = TEMPLATES.get(nextInt(TEMPLATES.size()));
        return new Unit(nextId++, t.name(), t.cost(), t.hp(), t.attack(), t.range());
    }

    /** Liefert den aktuellen RNG-Zustand fuer einen Spielzustands-Snapshot. */
    public long rngState() {
        return rngState;
    }

    /** Stellt den RNG-Zustand aus einem Snapshot wieder her. */
    public void restoreRng(long state) {
        this.rngState = state == 0 ? 0x9E3779B97F4A7C15L : state;
    }

    /** Liefert die naechste freie Einheiten-ID fuer einen Snapshot. */
    public int nextId() {
        return nextId;
    }

    /** Stellt die naechste freie Einheiten-ID aus einem Snapshot wieder her. */
    public void restoreNextId(int id) {
        this.nextId = Math.max(1, id);
    }

    private int nextInt(int bound) {
        // xorshift64*
        rngState ^= rngState >>> 12;
        rngState ^= rngState << 25;
        rngState ^= rngState >>> 27;
        long value = rngState * 2685821657736338717L;
        return (int) Math.floorMod(value, (long) bound);
    }
}
// AI-GENERATED-END
