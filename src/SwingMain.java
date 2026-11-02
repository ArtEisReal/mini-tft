import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Swing-GUI (Hotseat). Spricht nur ueber das Interface GameService mit der Spiellogik
 * und bekommt Updates ueber GameListener -> spaeter einfach den Stub statt GameEngine einsetzen.
 *
 * Bedienung: Shop-Klick = kaufen (Einheit landet auf dem Brett), Brett-Klick = verschieben,
 * Rechtsklick auf eigene Einheit = verkaufen. Nach "Fertig" wird der Kampf animiert.
 */
public class SwingMain extends JFrame implements GameListener {
    private static final String[] NAMES = {"Spieler1", "Spieler2"};
    private static final Color[] COST_COLORS = {
            new Color(120, 124, 135),  // Kosten 1
            new Color(54, 150, 92),    // Kosten 2
            new Color(52, 104, 196),   // Kosten 3
            new Color(142, 70, 184)};  // Kosten 4+

    private interface Action {
        void run() throws GameRuleException, RemoteException;
    }

    private final GameService service;
    private int current = 0;
    private boolean combatPlayback = false;

    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel msgLabel = new JLabel(" ");
    private final BoardPanel boardPanel = new BoardPanel();
    private final JPanel shopPanel = new JPanel(new GridLayout(1, GameEngine.SHOP_SIZE, 10, 10));
    private final JTextArea logArea = new JTextArea();
    private final JButton rerollBtn = new JButton("Reroll (" + GameEngine.REROLL_COST + " Gold)");
    private final JButton readyBtn = new JButton("Fertig / Kampf starten");
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel coverLabel = new JLabel("", SwingConstants.CENTER);

    public SwingMain(GameService service) {
        super("Mini-TFT");
        this.service = service;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Statusleiste
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 15f));
        msgLabel.setForeground(new Color(190, 40, 40));
        JPanel north = new JPanel(new GridLayout(2, 1));
        north.add(statusLabel);
        north.add(msgLabel);
        add(north, BorderLayout.NORTH);

        // Spielfeld: 9x9-Brett + Bench + Shop
        boardPanel.setBorder(BorderFactory.createTitledBorder(
                "Spielfeld + Bench (Feld-Einheit anklicken = verschieben/auswechseln, "
                        + "Bench anklicken = aufs Feld, Rechtsklick = verkaufen)"));
        boardPanel.setActions(new BoardPanel.Actions() {
            @Override
            public void onMove(int index, int row, int col) {
                act(() -> service.move(current, index, row, col));
            }

            @Override
            public void onToField(int benchSlot, int row, int col) {
                act(() -> service.toField(current, benchSlot, row, col));
            }

            @Override
            public void onToBench(int index) {
                act(() -> service.toBench(current, index));
            }

            @Override
            public void onSellField(int index) {
                act(() -> service.sell(current, index));
            }

            @Override
            public void onSellBench(int benchSlot) {
                act(() -> service.sellBench(current, benchSlot));
            }
        });
        shopPanel.setBorder(BorderFactory.createTitledBorder("Shop (Klick = kaufen, landet auf der Bench)"));
        JPanel areas = new JPanel(new BorderLayout(0, 10));
        areas.add(boardPanel, BorderLayout.CENTER);
        areas.add(shopPanel, BorderLayout.SOUTH);

        rerollBtn.addActionListener(e -> act(() -> service.reroll(current)));
        JButton levelBtn = new JButton("Level up (" + GameEngine.LEVEL_COST + " Gold)");
        levelBtn.setToolTipText("Gibt +1 Platz auf dem Feld (max. Level " + GameEngine.MAX_LEVEL + ")");
        levelBtn.addActionListener(e -> act(() -> service.levelUp(current)));
        readyBtn.addActionListener(e -> onReady());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(rerollBtn);
        buttons.add(levelBtn);
        buttons.add(readyBtn);

        JPanel game = new JPanel(new BorderLayout(10, 10));
        game.add(areas, BorderLayout.CENTER);
        game.add(buttons, BorderLayout.SOUTH);

        // Sichtschutz zwischen den Spielern (Hotseat)
        JButton continueBtn = new JButton("Weiter");
        continueBtn.addActionListener(e -> {
            refresh();
            cards.show(center, "game");
        });
        coverLabel.setFont(coverLabel.getFont().deriveFont(Font.BOLD, 24f));
        JPanel cover = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.insets = new Insets(10, 10, 10, 10);
        cover.add(coverLabel, gc);
        cover.add(continueBtn, gc);

        center.add(game, "game");
        center.add(cover, "cover");
        add(center, BorderLayout.CENTER);

        // Kampflog
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Kampflog"));
        scroll.setPreferredSize(new Dimension(380, 0));
        add(scroll, BorderLayout.EAST);

        setSize(1250, 780);
        setLocationRelativeTo(null);
        showCover();
    }

    // ---------- Ablauf ----------

    private void act(Action action) {
        if (combatPlayback) return; // waehrend der Animation ist keine Eingabe moeglich
        msgLabel.setText(" ");
        try {
            action.run();
        } catch (GameRuleException e) {
            msgLabel.setText(e.getMessage());
        } catch (RemoteException e) {
            msgLabel.setText("Verbindungsfehler: " + e.getMessage());
        }
        refresh();
    }

    private void onReady() {
        msgLabel.setText(" ");
        try {
            service.ready(current);
        } catch (GameRuleException e) {
            msgLabel.setText(e.getMessage());
            return;
        } catch (RemoteException e) {
            msgLabel.setText("Verbindungsfehler: " + e.getMessage());
            return;
        }
        if (combatPlayback) return; // die Animation steuert den weiteren Ablauf
        GameStateDto st = state();
        if (st.gameOver()) {
            refresh();
            rerollBtn.setEnabled(false);
            readyBtn.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Gewinner: " + st.winnerName(), "Spiel vorbei",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        current = 1 - current;
        showCover();
    }

    /** Wenn die Kampf-Animation durchgelaufen ist: Runde weiter (naechster Spieler ist wieder Nr. 0). */
    private void afterCombat() {
        combatPlayback = false;
        GameStateDto st = state();
        refresh();
        if (st.gameOver()) {
            rerollBtn.setEnabled(false);
            readyBtn.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Gewinner: " + st.winnerName(), "Spiel vorbei",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            current = 0;
            showCover();
        }
    }

    private void showCover() {
        coverLabel.setText(NAMES[current] + " ist dran");
        statusLabel.setText(" ");
        msgLabel.setText(" ");
        cards.show(center, "cover");
    }

    private GameStateDto state() {
        try {
            return service.getState(current);
        } catch (GameRuleException e) {
            throw new IllegalStateException(e);
        } catch (RemoteException e) {
            throw new IllegalStateException("Verbindungsfehler: " + e.getMessage(), e);
        }
    }

    private void refresh() {
        if (combatPlayback) return;
        GameStateDto s = state();
        PlayerDto me = s.self(), op = s.opponent();
        statusLabel.setText(String.format("Runde %d   |   %s: %d HP, %d Gold, Level %d   |   Feld %d/%d, Bench %d/%d   |   Gegner %s: %d HP, %d aufm Feld   |   Haelfte: %s",
                s.round(), me.name(), me.hp(), me.gold(), me.level(),
                me.board().size(), me.level(), me.bench().size(), GameEngine.BENCH_SIZE,
                op.name(), op.hp(), op.board().size(),
                current == 0 ? "oben" : "unten"));

        boardPanel.setSetup(current, me.board(), me.bench(), op.board());
        shopPanel.removeAll();
        for (int i = 0; i < GameEngine.SHOP_SIZE; i++) {
            UnitDto u = i < me.shop().size() ? me.shop().get(i) : null;
            final int slot = i + 1;
            boolean tooExpensive = u != null && me.gold() < u.cost();
            shopPanel.add(new Slot(u, tooExpensive, "Klick: kaufen",
                    () -> act(() -> service.buy(current, slot))));
        }
        shopPanel.revalidate();
        shopPanel.repaint();
    }

    // ---------- GameListener (Callbacks von der Spiellogik) ----------

    @Override
    public void onRoundStarted(int round) {
        logArea.append("--- Runde " + round + " beginnt ---\n");
    }

    @Override
    public void onCombatFrames(int round, List<CombatFrameDto> frames) throws RemoteException {
        combatPlayback = true;
        statusLabel.setText("Kampf wird ausgetragen ...");
        boardPanel.playFrames(frames, this::afterCombat);
    }

    @Override
    public void onCombatFinished(CombatResultDto r) {
        logArea.append("\n##### KAMPF Runde " + r.round() + " #####\n");
        r.log().forEach(line -> logArea.append(line + "\n"));
        logArea.append(r.winnerId() < 0 ? "Unentschieden!\n\n"
                : "Sieger: " + NAMES[r.winnerId()] + " (Gegner verliert " + r.damage() + " HP)\n\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    @Override
    public void onUnitCombined(int playerId, String unitName, int star) throws RemoteException {
        logArea.append("*** " + NAMES[playerId] + ": 3x " + unitName + " vereinigt -> "
                + UnitDto.starLabel(unitName, star) + " ***\n");
    }

    @Override
    public void onGameOver(String winnerName) {
        logArea.append("*** SPIEL VORBEI - Gewinner: " + winnerName + " ***\n");
    }

    // ---------- Einheiten-Karte ----------

    private static class Slot extends JPanel {
        private final UnitDto unit;
        private final boolean dim;

        Slot(UnitDto unit, boolean dim, String tip, Runnable onClick) {
            this.unit = unit;
            this.dim = dim;
            setOpaque(false);
            setPreferredSize(new Dimension(150, 185));
            if (unit != null) {
                setToolTipText(tip);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        onClick.run();
                    }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() - 4, h = getHeight() - 4;

            if (unit == null) {
                g.setColor(new Color(128, 128, 128, 40));
                g.fillRoundRect(2, 2, w, h, 20, 20);
                g.setColor(new Color(128, 128, 128, 160));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[] {6f, 6f}, 0f));
                g.drawRoundRect(2, 2, w, h, 20, 20);
                g.setFont(getFont().deriveFont(Font.PLAIN, 14f));
                centered(g, "leer", getWidth() / 2, getHeight() / 2);
                g.dispose();
                return;
            }

            if (dim) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            Color base = COST_COLORS[Math.min(unit.cost(), COST_COLORS.length) - 1];
            g.setPaint(new GradientPaint(0, 0, base.brighter(), 0, h, base.darker()));
            g.fillRoundRect(2, 2, w, h, 20, 20);
            g.setColor(base.darker().darker());
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(2, 2, w, h, 20, 20);

            // Kosten-Badge
            g.setColor(new Color(255, 215, 80));
            g.fillOval(10, 10, 28, 28);
            g.setColor(Color.BLACK);
            g.setFont(getFont().deriveFont(Font.BOLD, 15f));
            centered(g, String.valueOf(unit.cost()), 24, 24);

            // Name + Werte
            g.setColor(Color.WHITE);
            g.setFont(getFont().deriveFont(Font.BOLD, 16f));
            centered(g, unit.name(), getWidth() / 2, 70);
            g.setFont(getFont().deriveFont(Font.BOLD, 18f));
            centered(g, "HP  " + unit.hp(), getWidth() / 2, 108);
            centered(g, "ATK " + unit.attack(), getWidth() / 2, 132);
            centered(g, "RNG " + unit.range(), getWidth() / 2, 156);
            g.dispose();
        }

        private static void centered(Graphics2D g, String text, int cx, int cy) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    public static void main(String[] args) {
        // AI-GENERATED-START: Server-Host-Resolution (Argument / minitft.server / localhost) - 2026-11-02 12:41 CET
        // Server-Host: erst Argument, sonst minitft.server/MINITFT_SERVER, sonst "localhost"
        String host = RmiConfig.serverHost(args);
        // AI-GENERATED-END

        SwingUtilities.invokeLater(() -> {
            try {
                // RMI-Client verbinden
                RmiClient client = new RmiClient(host);
                client.connect();
                GameService service = client.getService();

                // GUI erstellen
                SwingMain ui = new SwingMain(service);

                // Listener beim Server registrieren (bidirektionale Kommunikation)
                client.registerListener(ui);

                // Spiel starten (Server hat bereits start() aufgerufen, UI nur aktualisieren)
                ui.refresh();
                ui.setVisible(true);

                // Bei Fenster-Schließung: Client trennen
                ui.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        client.close();
                    }
                });

            } catch (Exception e) {
                // AI-GENERATED-START: Fehlerdialog um Server-Host und Startbefehl ergänzt - 2026-11-02 12:41 CET
                JOptionPane.showMessageDialog(null,
                        "Fehler beim Verbinden mit dem Server:\n" + e.getMessage() + "\n\n"
                        + "Server: " + host + ":" + RmiConfig.REGISTRY_PORT + "\n"
                        + "Stelle sicher, dass RmiServer läuft:\n"
                        + "java -Dminitft.host=<eigene-IP> RmiServer",
                        "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                // AI-GENERATED-END
                e.printStackTrace();
            }
        });
    }
}
