import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Faellt jede Einheit eine eindeutige ID zu (damit "gleiche Einheit" identifizierbar ist). */
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private final Random random;

    public UnitFactory(Random random) {
        this.random = random;
    }

    public Unit randomUnit() {
        Template t = TEMPLATES.get(random.nextInt(TEMPLATES.size()));
        return new Unit(NEXT_ID.getAndIncrement(), t.name(), t.cost(), t.hp(), t.attack(), t.range());
    }
}
