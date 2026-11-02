// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.io.Serializable;

/** DTO: Einheit (wird in beide Richtungen übertragen). row/col = Position auf dem Brett, -1 = keine. */
public record UnitDto(int id, String name, int cost, int hp, int attack, int range,
                      int star, int row, int col) implements Serializable {

    /** Verkaufswert: 2-Sterne zahlt 3x, 3-Sterne 9x die Kosten zurueck. */
    public int sellValue() {
        return cost * Unit.starMultiplier(star);
    }

    /** Anzeigename mit Sternen, z.B. "★ Ritter" fuer einen 2-Sterne-Ritter. */
    public static String starLabel(String name, int star) {
        return star > 1 ? "★".repeat(star - 1) + " " + name : name;
    }
}
// AI-GENERATED-END
