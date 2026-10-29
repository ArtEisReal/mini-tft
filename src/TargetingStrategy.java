import java.util.Comparator;
import java.util.List;

/** Design Pattern: Strategy – wie eine Einheit ihr Ziel wählt. */
public interface TargetingStrategy {
    Unit pick(List<Unit> enemies);

    static TargetingStrategy lowestHp() {
        return enemies -> enemies.stream().min(Comparator.comparingInt(u -> u.hp)).orElse(null);
    }

    static TargetingStrategy first() {
        return enemies -> enemies.isEmpty() ? null : enemies.get(0);
    }
}
