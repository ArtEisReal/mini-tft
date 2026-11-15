import java.util.Arrays;

/**
 * Vektoruhr fuer die Nachverfolgung kausaler Abhaengigkeiten zwischen
 * Server und Clients in der Mini-TFT-Spielarchitektur.
 *
 * Die Vektoruhr hat die Struktur: [Server_Tick, P1_Tick, P2_Tick]
 * - Index 0: Server-Tick (wird nur vom Server inkrementiert)
 * - Index 1: Player 1 Tick (wird von Player 1 und Server inkrementiert)
 * - Index 2: Player 2 Tick (wird von Player 2 und Server inkrementiert)
 *
 * Jede Nachricht wird mit dem aktuellen Vektoruhr-Zustand versehen.
 * Der Server verwendet die Vektoruhren, um:
 *   - Kausale Abhaengigkeiten zu erkennen (vorher/nachher/konkurrent)
 *   - Manipulierte Client-Uhren zu erkennen (Anti-Cheat)
 *   - Lag-Kompensation durch Server-Side Rewind zu ermöglichen
 *
 * Die Vektoruhr wird als JSON-Array serialisiert und in JmsMessage eingebettet.
 */
public class VectorClock {

    /** Anzahl der Teilnehmer: Server + 2 Spieler. */
    public static final int SIZE = 3;

    /** Index des Server-Ticks in der Vektoruhr. */
    public static final int SERVER_INDEX = 0;

    /** Index des Player-1-Ticks in der Vektoruhr. */
    public static final int PLAYER_1_INDEX = 1;

    /** Index des Player-2-Ticks in der Vektoruhr. */
    public static final int PLAYER_2_INDEX = 2;

    /** Die Uhr-Werte: [Server_Tick, P1_Tick, P2_Tick]. */
    private final int[] ticks;

    /**
     * Erstellt eine neue Vektoruhr mit allen Werten auf 0.
     */
    public VectorClock() {
        this.ticks = new int[SIZE];
    }

    /**
     * Erstellt eine Vektoruhr aus einem Array.
     * @param ticks Array der Laenge SIZE
     */
    public VectorClock(int[] ticks) {
        if (ticks == null || ticks.length != SIZE) {
            throw new IllegalArgumentException("VectorClock muss genau " + SIZE + " Elemente haben");
        }
        this.ticks = ticks.clone();
    }

    /**
     * Liefert den Tick-Wert an der gegebenen Position.
     */
    public int get(int index) {
        if (index < 0 || index >= SIZE) {
            throw new IndexOutOfBoundsException("Index " + index + " ist ungültig (0.." + (SIZE - 1) + ")");
        }
        return ticks[index];
    }

    /**
     * Inkrementiert den Tick an der gegebenen Position um 1.
     * Wird aufgerufen, wenn der entsprechende Teilnehmer eine Aktion ausführt.
     */
    public void increment(int index) {
        if (index < 0 || index >= SIZE) {
            throw new IndexOutOfBoundsException("Index " + index + " ist ungültig");
        }
        if (ticks[index] < Integer.MAX_VALUE) {
            ticks[index]++;
        }
    }

    /**
     * Führt das elementweise Maximum dieser und einer anderen Vektoruhr durch
     * und gibt eine neue Vektoruhr als Ergebnis zurück.
     * Wird verwendet, um zwei Uhren bei Nachrichtenempfang zu verschmelzen.
     */
    public VectorClock merge(VectorClock other) {
        if (other == null) return this.clone();
        int[] merged = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            merged[i] = Math.max(this.ticks[i], other.ticks[i]);
        }
        return new VectorClock(merged);
    }

    /**
     * Vergleicht diese Vektoruhr mit einer anderen und bestimmt die kausale Beziehung.
     *
     * @return CausalRelation: BEFORE, AFTER, CONCURRENT, oder EQUAL
     */
    public CausalRelation compare(VectorClock other) {
        if (other == null) return CausalRelation.AFTER;

        boolean atLeastOneLess = false;
        boolean atLeastOneGreater = false;

        for (int i = 0; i < SIZE; i++) {
            if (this.ticks[i] < other.ticks[i]) {
                atLeastOneLess = true;
            }
            if (this.ticks[i] > other.ticks[i]) {
                atLeastOneGreater = true;
            }
        }

        if (!atLeastOneLess && !atLeastOneGreater) {
            return CausalRelation.EQUAL;
        } else if (atLeastOneLess && !atLeastOneGreater) {
            return CausalRelation.BEFORE;
        } else if (!atLeastOneLess && atLeastOneGreater) {
            return CausalRelation.AFTER;
        } else {
            return CausalRelation.CONCURRENT;
        }
    }

    /**
     * Prüft, ob diese Vektoruhr streng vor der anderen liegt (kausale Vorher-Nachher-Beziehung).
     */
    public boolean happensBefore(VectorClock other) {
        return compare(other) == CausalRelation.BEFORE;
    }

    /**
     * Prüft, ob diese Vektoruhr streng nach der anderen liegt.
     */
    public boolean happensAfter(VectorClock other) {
        return compare(other) == CausalRelation.AFTER;
    }

    /**
     * Prüft, ob diese Vektoruhr konkurrent (unvergleichbar) zur anderen ist.
     */
    public boolean isConcurrentWith(VectorClock other) {
        return compare(other) == CausalRelation.CONCURRENT;
    }

    /**
     * Erstellt eine tiefe Kopie dieser Vektoruhr.
     */
    public VectorClock clone() {
        return new VectorClock(ticks);
    }

    /**
     * Liefert das interne ticks-Array (nur fuer internen Zugriff durch VectorClockManager).
     * Package-private, damit VectorClockManager die Server-Uhr direkt aktualisieren kann.
     */
    int[] getInternalTicks() {
        return ticks;
    }

    /**
     * Serialisiert die Vektoruhr als JSON-Array-String.
     * Beispiel: "[1,0,1]"
     */
    public String toJson() {
        return Arrays.toString(ticks).replace(" ", "");
    }

    /**
     * Deserialisiert eine Vektoruhr aus einem JSON-Array-String.
     * Beispiel: "[1,0,1]"
     */
    public static VectorClock fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new VectorClock();
        }
        // "[1,0,1]" -> "1,0,1"
        String inner = json.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);

        String[] parts = inner.split(",");
        if (parts.length != SIZE) {
            throw new IllegalArgumentException("VectorClock JSON muss " + SIZE + " Elemente haben: " + json);
        }
        int[] ticks = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ticks[i] = Integer.parseInt(parts[i].trim());
        }
        return new VectorClock(ticks);
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VectorClock other = (VectorClock) obj;
        return Arrays.equals(this.ticks, other.ticks);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ticks);
    }

    /**
     * Kausale Beziehung zwischen zwei Vektoruhren.
     */
    public enum CausalRelation {
        /** Diese Uhr liegt streng vor der anderen. */
        BEFORE,
        /** Diese Uhr liegt streng nach der anderen. */
        AFTER,
        /** Die Uhren sind konkurrent (unvergleichbar). */
        CONCURRENT,
        /** Die Uhren sind identisch. */
        EQUAL
    }
}
