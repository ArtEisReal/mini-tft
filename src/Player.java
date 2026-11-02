// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.util.ArrayList;
import java.util.List;

/** Domänenklasse: ein Spieler mit Leben, Gold, Level, Feld, Bench und Shop. */
public class Player {
    public final int id;
    public final String name;
    public int hp = 30;
    public int gold = 5;
    /** Wie viele Einheiten gleichzeitig auf dem Feld stehen duerfen. */
    public int level = GameEngine.START_LEVEL;
    public boolean ready;
    /** Feld-Einheiten (platziert, row/col gesetzt). */
    public final List<Unit> board = new ArrayList<>();
    /** Bench (gewartet; row/col = -1). */
    public final List<Unit> bench = new ArrayList<>();
    public final List<Unit> shop = new ArrayList<>();

    public Player(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Der Gegner sieht deinen Shop nicht (hideShop = true). */
    public PlayerDto toDto(boolean hideShop) {
        return new PlayerDto(id, name, hp, gold, level, ready,
                board.stream().map(Unit::toDto).toList(),
                bench.stream().map(Unit::toDto).toList(),
                hideShop ? List.of() : shop.stream().map(Unit::toDto).toList());
    }
}
// AI-GENERATED-END
