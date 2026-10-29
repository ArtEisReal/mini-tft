import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 9x9-Brettansicht mit Bench-Reihe darunter.
 * zeigt die Aufstellung beider Spieler (obere Haelfte = Spieler 0, untere = Spieler 1)
 * und spielt den Kampf Frame fuer Frame animiert ab.
 *
 * Planungsphase:
 *   Feld-Einheit anklicken -> Zelle anklicken = verschieben (auf eigene Zelle = tauschen)
 *   Bench-Einheit anklicken -> Feldzelle anklicken = aufs Feld (tauscht ggf. mit Feld-Einheit)
 *   Feld-Einheit anklicken -> leere Bench-Platz = auf die Bench (auswechseln)
 *   Rechtsklick auf Einheit = verkaufen (Feld oder Bench)
 */
public class BoardPanel extends JPanel {
    public static final int SIZE = GameEngine.BOARD_SIZE;
    public static final int PLAYER_ROWS = GameEngine.PLAYER_ROWS;
    public static final int BENCH_SIZE = GameEngine.BENCH_SIZE;
    private static final int TICK_MS = 350;
    private static final int GAP = 14;

    public interface Actions {
        /** Gewaehlte Feld-Einheit (1-basierter Feld-Index) auf Zelle row/col setzen. */
        void onMove(int index, int row, int col);

        /** Bench-Einheit (1-basierter Slot) auf Zelle row/col ziehen (tauscht ggf.). */
        void onToField(int benchSlot, int row, int col);

        /** Gewaehlte Feld-Einheit auf die Bench zurueckziehen. */
        void onToBench(int index);

        /** Rechtsklick: Feld-Einheit verkaufen. */
        void onSellField(int index);

        /** Rechtsklick: Bench-Einheit verkaufen. */
        void onSellBench(int benchSlot);
    }

    private static final Color FIELD_MINE = new Color(70, 120, 200, 35);
    private static final Color FIELD_FOE = new Color(200, 70, 60, 35);
    private static final Color BENCH_BG = new Color(120, 120, 120, 40);
    private static final Color GRID = new Color(0, 0, 0, 60);
    private static final Color[] TEAM = {new Color(48, 96, 190), new Color(196, 62, 52)};
    private static final Color GOLD = new Color(255, 205, 70);

    private final Map<Integer, UnitDto> mine = new HashMap<>();
    /** Zelle -> 1-basierter Index in der Feldliste (so wie GameService.move/sell es erwarten). */
    private final Map<Integer, Integer> myIndex = new HashMap<>();
    private final Map<Integer, UnitDto> foe = new HashMap<>();
    private final Map<Integer, UnitDto> bench = new HashMap<>();
    private int myTeam = 0;
    private int selField = -1;   // Zellen-Key auf dem Feld
    private int selBench = -1;   // Bench-Slot (0-basiert)
    private Actions actions;

    private List<CombatFrameDto> frames;
    private int frameIdx = -1;
    private Timer timer;

    public BoardPanel() {
        setPreferredSize(new Dimension(SIZE * 58 + 12, (SIZE + 1) * 58 + 56));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isPlaying() || actions == null) return;
                int slot = benchAt(e.getPoint());
                if (slot >= 0) {
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        if (bench.containsKey(slot)) actions.onSellBench(slot + 1);
                    } else if (bench.containsKey(slot)) {
                        selBench = selBench == slot ? -1 : slot;
                        selField = -1;
                    } else if (selField >= 0) {
                        actions.onToBench(fieldIndexOf(selField));
                        clearSelection();
                    }
                    repaint();
                    return;
                }
                int[] cell = cellAt(e.getPoint());
                if (cell == null) return;
                int key = cell[0] * SIZE + cell[1];
                if (e.getButton() == MouseEvent.BUTTON3) {
                    Integer idx = myIndex.get(key);
                    if (idx != null) {
                        clearSelection();
                        actions.onSellField(idx);
                    }
                    repaint();
                    return;
                }
                if (!inMyHalf(cell[0])) return;

                if (selBench >= 0) {
                    // Bench -> Feld (Feldzelle belegt? -> Tausch, das handelt die Engine)
                    actions.onToField(selBench + 1, cell[0], cell[1]);
                    clearSelection();
                } else if (selField >= 0) {
                    if (selField == key) clearSelection();
                    else {
                        actions.onMove(fieldIndexOf(selField), cell[0], cell[1]);
                        clearSelection();
                    }
                } else if (myIndex.containsKey(key)) {
                    selField = key;
                }
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setToolTipText(toolTipAt(e.getPoint()));
            }
        });
    }

    private void clearSelection() {
        selField = -1;
        selBench = -1;
    }

    private int fieldIndexOf(int key) {
        return myIndex.get(key);
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    /** Aufstellung setzen (Planungsphase); loescht die laufende Animation. */
    public void setSetup(int myTeam, List<UnitDto> myUnits, List<UnitDto> benchUnits, List<UnitDto> foeUnits) {
        stopPlayback();
        this.myTeam = myTeam;
        clearSelection();
        mine.clear();
        myIndex.clear();
        bench.clear();
        foe.clear();
        for (int i = 0; i < myUnits.size(); i++) {
            UnitDto u = myUnits.get(i);
            if (u.row() < 0) continue;
            int key = u.row() * SIZE + u.col();
            mine.put(key, u);
            myIndex.put(key, i + 1); // 1-basiert, wie GameService.move/sell es erwarten
        }
        for (int i = 0; i < benchUnits.size(); i++) {
            bench.put(i, benchUnits.get(i));
        }
        for (UnitDto u : foeUnits) if (u.row() >= 0) foe.put(u.row() * SIZE + u.col(), u);
        repaint();
    }

    public boolean isPlaying() {
        return frameIdx >= 0;
    }

    /** Startet die Kampf-Animation; ruft onDone auf, wenn sie durchgelaufen ist. */
    public void playFrames(List<CombatFrameDto> frames, Runnable onDone) {
        stopPlayback();
        this.frames = new ArrayList<>(frames);
        this.frameIdx = this.frames.isEmpty() ? 0 : -1;
        repaint();
        if (this.frames.isEmpty()) {
            onDone.run();
            return;
        }
        timer = new Timer(TICK_MS, e -> {
            frameIdx++;
            repaint();
            if (frameIdx >= frames.size() - 1) {
                stopPlayback();
                onDone.run();
            }
        });
        timer.start();
    }

    private void stopPlayback() {
        if (timer != null) timer.stop();
        timer = null;
        frameIdx = -1;
        frames = null;
    }

    private boolean inMyHalf(int row) {
        return myTeam == 0 ? row < PLAYER_ROWS : row >= SIZE - PLAYER_ROWS;
    }

    // ---------- Geometrie ----------

    private int cellSize() {
        int byWidth = (getWidth() - 12) / SIZE;
        int byHeight = (getHeight() - 44) / (SIZE + 1);
        return Math.max(20, Math.min(byWidth, byHeight));
    }

    private int benchTop() {
        // exakt eine GAP-Unterluecke unter dem Feld, damit nichts abgeschnitten wird
        return cellOrigin(SIZE - 1, 0).y + cellSize() + GAP;
    }

    /** Zelle unter dem Punkt oder null. */
    private int[] cellAt(Point p) {
        int cell = cellSize();
        int ox = (getWidth() - cell * SIZE) / 2, oy = (getHeight() - cell * SIZE - GAP - cell - 20) / 2;
        int c = (p.x - ox) / cell, r = (p.y - oy) / cell;
        if (r < 0 || r >= SIZE || c < 0 || c >= SIZE || p.y > oy + SIZE * cell) return null;
        return new int[] {r, c};
    }

    /** Bench-Slot unter dem Punkt (0..8) oder -1. */
    private int benchAt(Point p) {
        int cell = cellSize();
        int ox = (getWidth() - cell * SIZE) / 2, top = benchTop();
        if (p.y < top || p.y > top + cell) return -1;
        int slot = (p.x - ox) / cell;
        if (p.x < ox || slot < 0 || slot >= BENCH_SIZE) return -1;
        return slot;
    }

    private Point cellOrigin(int row, int col) {
        int cell = cellSize();
        int ox = (getWidth() - cell * SIZE) / 2, oy = (getHeight() - cell * SIZE - GAP - cell - 20) / 2;
        return new Point(ox + col * cell, oy + row * cell);
    }

    private Point benchOrigin(int slot) {
        int cell = cellSize();
        int ox = (getWidth() - cell * SIZE) / 2;
        return new Point(ox + slot * cell, benchTop());
    }

    private String toolTipAt(Point p) {
        int slot = benchAt(p);
        if (slot >= 0) {
            UnitDto u = bench.get(slot);
            return u == null ? "Bench-Platz " + (slot + 1) + " (leer)" : tip(u, "Bench-Platz " + (slot + 1));
        }
        int[] cell = cellAt(p);
        if (cell == null) return null;
        UnitDto u = mine.get(cell[0] * SIZE + cell[1]);
        if (u == null) u = foe.get(cell[0] * SIZE + cell[1]);
        return u == null ? null : tip(u, "Feld");
    }

    private String tip(UnitDto u, String where) {
        return String.format("<html><b>%s</b> (%s)<br>HP %d | ATK %d | Reichweite %d<br>Kosten %d | Verkauf +%d Gold</html>",
                UnitDto.starLabel(u.name(), u.star()), where, u.hp(), u.attack(), u.range(),
                u.cost(), u.sellValue());
    }

    // ---------- Rendering ----------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                Point o = cellOrigin(r, c);
                int cell = cellSize();
                Color fill = r == PLAYER_ROWS ? new Color(140, 140, 140, 45)
                        : (r < PLAYER_ROWS ? FIELD_MINE : FIELD_FOE);
                g.setColor(fill);
                g.fillRect(o.x, o.y, cell, cell);
                g.setColor(GRID);
                g.drawRect(o.x, o.y, cell, cell);
            }
        }

        if (isPlaying()) paintCombat(g);
        else paintSetup(g);

        // Trennlinie in der Mitte beschriften
        g.setColor(new Color(0, 0, 0, 140));
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g.drawString("Kampflinie", 8, cellOrigin(PLAYER_ROWS, 0).y + cellSize() / 2 + 4);

        paintBench(g);
        g.dispose();
    }

    /** Planungsphase: beide Aufstellungen, Auswahl markiert. */
    private void paintSetup(Graphics2D g) {
        for (Map.Entry<Integer, UnitDto> e : foe.entrySet()) {
            int key = e.getKey();
            paintChip(g, e.getValue(), cellOrigin(key / SIZE, key % SIZE),
                    TEAM[myTeam == 0 ? 1 : 0], false, false);
        }
        for (Map.Entry<Integer, UnitDto> e : mine.entrySet()) {
            int key = e.getKey();
            paintChip(g, e.getValue(), cellOrigin(key / SIZE, key % SIZE), TEAM[myTeam],
                    false, key == selField);
        }
    }

    /** Bench-Reihe: 9 Plaetze, meine Einheiten in Teamfarbe. */
    private void paintBench(Graphics2D g) {
        int cell = cellSize();
        for (int slot = 0; slot < BENCH_SIZE; slot++) {
            Point o = benchOrigin(slot);
            UnitDto u = bench.get(slot);
            if (u == null) {
                g.setColor(BENCH_BG);
                g.fillRect(o.x, o.y, cell, cell);
                g.setColor(GRID);
                g.drawRect(o.x, o.y, cell, cell);
                g.setColor(new Color(0, 0, 0, 120));
                g.setFont(getFont().deriveFont(Font.PLAIN, Math.max(8f, cell / 9f)));
                centered(g, String.valueOf(slot + 1), o.x + cell / 2, o.y + cell / 2);
            } else {
                paintChip(g, u, o, TEAM[myTeam], false, slot == selBench);
            }
        }
        g.setColor(new Color(0, 0, 0, 150));
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g.drawString("Bench (Klick = auswaehlen, leere Platz = aufs Feld/Feld-Einheit = zurueck)",
                8, benchTop() - 4);
    }

    /** Kampf: Einheiten des aktuellen Frames, Treffer als Pfeile, gefallene Einheiten durchgestrichen. */
    private void paintCombat(Graphics2D g) {
        if (frames == null || frameIdx < 0 || frameIdx >= frames.size()) return;
        CombatFrameDto frame = frames.get(frameIdx);
        int cell = cellSize();

        for (UnitStateDto u : frame.units()) {
            paintChip(g, UnitDto.starLabel(u.name(), u.star()), u.hp(), u.maxHp(),
                    cellOrigin(u.row(), u.col()), TEAM[u.team()], !u.alive(), false);
        }

        for (CombatEventDto e : frame.events()) {
            if (e.damage() <= 0) continue;
            Point from = center(e.fromRow(), e.fromCol());
            Point to = center(e.toRow(), e.toCol());
            g.setColor(e.team() == 0 ? TEAM[0] : TEAM[1]);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawArrow(g, from, to, cell / 3);
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g.drawString("-" + e.damage(), to.x + 8, to.y - 8);
        }

        // Tick-Zaehler
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(8, 8, 110, 24, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        g.drawString("Tick " + frame.tick() + "/" + frames.size(), 18, 25);
    }

    private Point center(int row, int col) {
        Point o = cellOrigin(row, col);
        int cell = cellSize();
        return new Point(o.x + cell / 2, o.y + cell / 2);
    }

    private void drawArrow(Graphics2D g, Point from, Point to, int inset) {
        double ang = Math.atan2(to.y - from.y, to.x - from.x);
        Point a = new Point((int) (from.x + Math.cos(ang) * (cellSize() / 2.0 + 4)),
                (int) (from.y + Math.sin(ang) * (cellSize() / 2.0 + 4)));
        Point b = new Point((int) (to.x - Math.cos(ang) * (cellSize() / 2.0 + 4)),
                (int) (to.y - Math.sin(ang) * (cellSize() / 2.0 + 4)));
        g.drawLine(a.x, a.y, b.x, b.y);
        int head = Math.max(6, inset);
        int x1 = (int) (b.x - Math.cos(ang - 0.4) * head), y1 = (int) (b.y - Math.sin(ang - 0.4) * head);
        int x2 = (int) (b.x - Math.cos(ang + 0.4) * head), y2 = (int) (b.y - Math.sin(ang + 0.4) * head);
        Polygon arrow = new Polygon(new int[] {b.x, x1, x2}, new int[] {b.y, y1, y2}, 3);
        g.fillPolygon(arrow);
    }

    /** Zeichnet eine Einheit als Chip mit Name, HP-Balken und Werten. */
    private void paintChip(Graphics2D g, UnitDto u, Point o, Color team, boolean dead, boolean selectedFlag) {
        paintChip(g, UnitDto.starLabel(u.name(), u.star()), u.hp(), u.hp(), o, team, dead, selectedFlag);
    }

    private void paintChip(Graphics2D g, String name, int hp, int maxHp, Point o,
                           Color team, boolean dead, boolean selectedFlag) {
        int cell = cellSize();
        int pad = Math.max(2, cell / 14);
        int x = o.x + pad, y = o.y + pad, w = cell - pad * 2, h = cell - pad * 2;

        g.setComposite(dead ? AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f)
                : AlphaComposite.SrcOver);
        g.setColor(team);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(team.darker());
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, w, h, 10, 10);

        // HP-Balken oben
        int barY = y + 3, barH = Math.max(4, cell / 12);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(x + 4, barY, w - 8, barH);
        float ratio = maxHp <= 0 ? 0 : Math.max(0, Math.min(1, hp / (float) maxHp));
        g.setColor(ratio > 0.5f ? new Color(80, 200, 80) : ratio > 0.25f ? GOLD : new Color(220, 60, 50));
        g.fillRect(x + 4, barY, (int) ((w - 8) * ratio), barH);

        // Name + Werte
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, Math.max(9f, cell / 8.5f)));
        centered(g, name.length() > 9 ? name.substring(0, 9) : name, x + w / 2, y + h * 38 / 100);
        g.setFont(getFont().deriveFont(Font.PLAIN, Math.max(8f, cell / 9.5f)));
        centered(g, hp + "/" + maxHp, x + w / 2, y + h * 62 / 100);

        if (dead) {
            g.setColor(new Color(120, 0, 0));
            g.setStroke(new BasicStroke(3f));
            g.drawLine(x + 4, y + 4, x + w - 4, y + h - 4);
            g.drawLine(x + w - 4, y + 4, x + 4, y + h - 4);
        }
        g.setComposite(AlphaComposite.SrcOver);
        if (selectedFlag) {
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(x, y, w, h, 10, 10);
        }
    }

    private static void centered(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }
}
