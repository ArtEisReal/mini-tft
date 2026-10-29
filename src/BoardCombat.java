import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Positionsbasierter Kampf auf dem 9x9-Brett.
 * pro Tick: jedes lebende Einheit handelt der Reihe nach –
 * Ziel = immer der naechste Gegner (Grid-Distanz, bei Gleichstand Tie-Break per TargetingStrategy).
 * Solange NIEMAND in der eigenen Reichweite ist, rueckt die Einheit ein Feld vor – auch Fernkaempfer.
 * Steht aber jemand in Reichweite, wird gestanden und geschossen (Fernkaempfer also nur dann
 * still, wenn sie gerade treffen koennen).
 * Liefert pro Tick einen Frame, den der Client animieren kann.
 */
public class BoardCombat {
    private static final int MAX_TICKS = 100;

    private static final class Fighter {
        final int team, attack, range, maxHp, star;
        final String name;
        int hp, row, col;

        Fighter(int team, Unit u) {
            this.team = team;
            this.name = u.name;
            this.attack = u.attack;
            this.range = u.range;
            this.star = u.star;
            this.maxHp = u.hp;
            this.hp = u.hp;
            this.row = u.row;
            this.col = u.col;
        }
    }

    public static List<CombatFrameDto> run(List<Unit> team0, List<Unit> team1,
                                           String name0, String name1) {
        List<Fighter> fighters = new ArrayList<>();
        for (Unit u : team0) if (u.row >= 0) fighters.add(new Fighter(0, u));
        for (Unit u : team1) if (u.row >= 0) fighters.add(new Fighter(1, u));

        List<CombatFrameDto> frames = new ArrayList<>();
        TargetingStrategy strategy = TargetingStrategy.lowestHp();
        int tick = 0;

        while (tick < MAX_TICKS && count(fighters, 0) > 0 && count(fighters, 1) > 0) {
            tick++;
            List<CombatEventDto> events = new ArrayList<>();
            for (int team = 0; team <= 1; team++) {
                for (Fighter f : fighters) {
                    if (f.team != team || f.hp <= 0) continue;
                    Fighter target = pick(f, fighters, strategy);
                    if (target == null) break;
                    if (distance(f, target) <= f.range) {
                        target.hp -= f.attack;
                        events.add(new CombatEventDto(f.team, f.row, f.col, target.row, target.col, f.attack,
                                label(f) + " (" + name(f.team, name0, name1) + ") trifft " + label(target)
                                        + " fuer " + f.attack + " -> Rest-HP " + Math.max(0, target.hp)));
                        if (target.hp <= 0) {
                            events.add(new CombatEventDto(target.team, target.row, target.col,
                                    target.row, target.col, 0,
                                    "  " + label(target) + " (" + name(target.team, name0, name1) + ") faellt!"));
                        }
                    } else {
                        // Niemand in Reichweite -> ein Feld Richtung Ziel ruecken (auch Fernkaempfer)
                        int[] step = stepToward(f, target, fighters);
                        if (step != null) {
                            events.add(new CombatEventDto(f.team, f.row, f.col, step[0], step[1], 0,
                                    label(f) + " (" + name(f.team, name0, name1) + ") rueckt vor"));
                            f.row = step[0];
                            f.col = step[1];
                        }
                    }
                }
            }
            // Stillstand (z.B. Fernkaempfer vs. Fernkaempfer ausserhalb der Reichweite) = Unentschieden
            if (events.isEmpty()) break;
            frames.add(new CombatFrameDto(tick, snapshot(fighters), events));
        }
        return frames;
    }

    /** Ziel: immer der naechste Gegner (Grid-Distanz); bei Gleichstand entscheidet die
     *  TargetingStrategy (wenigste HP) – Strategy-Pattern. */
    private static Fighter pick(Fighter f, List<Fighter> fighters, TargetingStrategy strategy) {
        int best = Integer.MAX_VALUE;
        List<Fighter> nearest = new ArrayList<>();
        for (Fighter e : fighters) {
            if (e.team == f.team || e.hp <= 0) continue;
            int d = distance(f, e);
            if (d < best) {
                best = d;
                nearest.clear();
            }
            if (d == best) nearest.add(e);
        }
        if (nearest.isEmpty()) return null;
        // Strategy-Pattern: die TargetingStrategy waehlt unter den Gleichstehenden (wenigste HP)
        Map<Unit, Fighter> byUnit = new HashMap<>();
        for (Fighter e : nearest) byUnit.put(new Unit(0, e.name, 0, e.hp, 0, 1), e);
        Unit lowHp = strategy.pick(new ArrayList<>(byUnit.keySet()));
        return byUnit.get(lowHp);
    }

    /** Ein Feld in Richtung Ziel (diagonal geht, wie ein Koenig); nie auf belegte Zellen. */
    private static int[] stepToward(Fighter f, Fighter target, List<Fighter> fighters) {
        int dr = Integer.signum(target.row - f.row);
        int dc = Integer.signum(target.col - f.col);
        int[][] candidates = {
                {f.row + dr, f.col + dc},   // diagonal ist am schnellsten
                {f.row + dr, f.col},
                {f.row, f.col + dc}};
        for (int[] c : candidates) {
            if (c[0] < 0 || c[0] >= GameEngine.BOARD_SIZE || c[1] < 0 || c[1] >= GameEngine.BOARD_SIZE) continue;
            if (c[0] == f.row && c[1] == f.col) continue;
            if (occupied(f, c[0], c[1], fighters)) continue;
            if (distance(c[0], c[1], target) >= distance(f, target)) continue;
            return c;
        }
        return null;
    }

    private static boolean occupied(Fighter self, int row, int col, List<Fighter> fighters) {
        for (Fighter x : fighters) {
            if (x != self && x.hp > 0 && x.row == row && x.col == col) return true;
        }
        return false;
    }

    private static List<UnitStateDto> snapshot(List<Fighter> fighters) {
        List<UnitStateDto> units = new ArrayList<>();
        for (Fighter f : fighters) {
            units.add(new UnitStateDto(f.team, f.row, f.col, f.name, f.star,
                    Math.max(0, f.hp), f.maxHp, f.hp > 0));
        }
        return units;
    }

    private static int count(List<Fighter> fighters, int team) {
        int n = 0;
        for (Fighter f : fighters) if (f.team == team && f.hp > 0) n++;
        return n;
    }

    private static String name(int team, String n0, String n1) {
        return team == 0 ? n0 : n1;
    }

    /** Anzeigename inklusive Sternen (z.B. "★ Ritter" fuer einen 2-Sterne-Ritter). */
    static String label(Fighter f) {
        return UnitDto.starLabel(f.name, f.star);
    }

    private static int distance(Fighter a, Fighter b) {
        return distance(a.row, a.col, b);
    }

    /** Grid-Distanz (Koenigszug): ein Schritt diagonal zaehlt wie ein Schritt geradeaus. */
    private static int distance(int row, int col, Fighter b) {
        return Math.max(Math.abs(row - b.row), Math.abs(col - b.col));
    }
}
