/** Domänenklasse: eine Kampfeinheit. Wird bei der Verteilung über UnitDto übertragen. */
public class Unit {
    /**
     * Werte-Skala je Sternstufe – bewusst ueberproportional:
     * linear waeren 3-Sterne = 3x (oder 1.5^2 = 2.25 auf der 2-Sterne-Kurve).
     * stattdessen 4x, damit sich ein 3-Sterner wie ein Raid-Boss anfuehlt.
     */
    private static final double[] STAR_SCALE = {1.0, 1.0, 1.5, 4.0};

    /** Eindeutige Instanz-ID – jede gekaufte Einheit ist so identifizierbar. */
    public final int id;
    public final String name;
    public final int cost;
    /** Basis-Werte bei 1 Stern; die Stern-Skala wird darauf angewendet. */
    public final int baseAttack, baseHp;
    /** Reichweite in Feldern (1 = Nahkampf). */
    public final int range;
    /** Effektive Werte (= Basis x Stern-Skala). */
    public int hp, attack;
    /** 1 = normal, 2 = 2-Sterne (aus 3 gleichen), 3 = 3-Sterne (aus 3x 2-Sterne). */
    public int star = 1;
    /** Position auf dem 9x9-Brett; -1 = nicht platziert. */
    public int row = -1, col = -1;

    public Unit(int id, String name, int cost, int hp, int attack, int range) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.baseHp = hp;
        this.baseAttack = attack;
        this.hp = hp;
        this.attack = attack;
        this.range = range;
    }

    /** Wendet die Stern-Skala auf Basis-Werte an (wird beim Vereinigen aufgerufen). */
    public void applyStar() {
        hp = scaled(baseHp);
        attack = scaled(baseAttack);
    }

    private int scaled(int base) {
        return (int) Math.round(base * STAR_SCALE[star]);
    }

    public Unit copy() {
        Unit c = new Unit(id, name, cost, baseHp, baseAttack, range);
        c.star = star;
        c.hp = hp;
        c.attack = attack;
        c.row = row;
        c.col = col;
        return c;
    }

    public UnitDto toDto() {
        return new UnitDto(id, name, cost, hp, attack, range, star, row, col);
    }

    /** Verkaufswert: 2-Sterne zahlt 3x, 3-Sterne 9x die Kosten zurueck. */
    public int sellValue() {
        return cost * starMultiplier(star);
    }

    static int starMultiplier(int star) {
        return star <= 1 ? 1 : star == 2 ? 3 : 9;
    }
}
