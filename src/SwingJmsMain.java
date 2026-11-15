import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Swing-GUI fuer Mini-TFT im JMS-Modus.
 *
 * Analog zu SwingMain, verwendet aber JmsClient statt RmiClient.
 * Jeder Client verbindet sich ueber ActiveMQ zum JmsServer und empfaengt
 * Callbacks asynchron via JMS.
 *
 * Startbefehl (Beispiel):
 *   java -cp out;... SwingJmsMain [SpielerID]
 *
 * SpielerID: 0 oder 1 (Standard: 0).
 * Broker-URL: -Dminitft.brokerUrl=tcp://localhost:61616 (Standard).
 */
public class SwingJmsMain extends JFrame implements GameListener {

    /** Name des Spielers nach ID. */
    private static final String[] NAMES = {"Spieler 0", "Spieler 1"};

    private final JmsClient client;
    private final JmsGameServiceStub service;
    /** Spieler-ID dieses Clients (0 oder 1). */
    private final int playerId;

    private static final Color[] COST_COLORS = {
            new Color(120, 124, 135),  // Kosten 1
            new Color(54, 150, 92),    // Kosten 2
            new Color(52, 104, 196),   // Kosten 3
            new Color(142, 70, 184)};  // Kosten 4+

    private interface Action {
        void run() throws GameRuleException;
    }

    private List<CombatFrameDto> lastFrames;
    private boolean combatPlayback = false;

    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel msgLabel = new JLabel(" ");
    private final BoardPanel boardPanel = new BoardPanel();
    private final JPanel shopPanel = new JPanel(new GridLayout(1, GameEngine.SHOP_SIZE, 10, 10));
    private final JTextArea logArea = new JTextArea();
    private final JButton rerollBtn = new JButton("Reroll (" + GameEngine.REROLL_COST + " Gold)");
    private final JButton delayedBuyBtn = new JButton("Kaufen Slot 1 (" + (JmsConfig.DEMO_DELAY_MS / 1000) + " s verzögert)");
    private final JButton readyBtn = new JButton("Fertig / Kampf starten");

    /**
     * Erstellt die JMS-GUI fuer einen Spieler.
     *
     * @param client    der verbundene JmsClient
     * @param service   das GameService-Proxy-Objekt (muss JMS-Befehle weiterleiten)
     * @param playerId  die Spieler-ID (0 oder 1)
     */
    public SwingJmsMain(JmsClient client, JmsGameServiceStub service, int playerId) {
        super("Mini-TFT (JMS) - Spieler " + playerId);
        this.client = client;
        this.service = service;
        this.playerId = playerId;
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

        // Spielfeld
        boardPanel.setBorder(BorderFactory.createTitledBorder(
                "Spielfeld + Bench (Klick = verschieben, Bench = aufs Feld, Rechtsklick = verkaufen)"));
        boardPanel.setActions(new BoardPanel.Actions() {
            @Override
            public void onMove(int index, int row, int col) {
                act(() -> service.move(playerId, index, row, col));
            }

            @Override
            public void onToField(int benchSlot, int row, int col) {
                act(() -> service.toField(playerId, benchSlot, row, col));
            }

            @Override
            public void onToBench(int index) {
                act(() -> service.toBench(playerId, index));
            }

            @Override
            public void onSellField(int index) {
                act(() -> service.sell(playerId, index));
            }

            @Override
            public void onSellBench(int benchSlot) {
                act(() -> service.sellBench(playerId, benchSlot));
            }
        });
        shopPanel.setBorder(BorderFactory.createTitledBorder("Shop (Klick = kaufen)"));
        JPanel areas = new JPanel(new BorderLayout(0, 10));
        areas.add(boardPanel, BorderLayout.CENTER);
        areas.add(shopPanel, BorderLayout.SOUTH);

        rerollBtn.addActionListener(e -> act(() -> service.reroll(playerId)));
        delayedBuyBtn.setToolTipText("Demo der Delayed Delivery: Befehl wird beim Broker "
                + (JmsConfig.DEMO_DELAY_MS / 1000) + " s zurueckgehalten. Danach sofort Reroll klicken.");
        delayedBuyBtn.addActionListener(e -> actDelayedBuy());
        JButton levelBtn = new JButton("Level up (" + GameEngine.LEVEL_COST + " Gold)");
        levelBtn.setToolTipText("Gibt +1 Platz auf dem Feld (max. Level " + GameEngine.MAX_LEVEL + ")");
        levelBtn.addActionListener(e -> act(() -> service.levelUp(playerId)));
        readyBtn.addActionListener(e -> onReady());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(delayedBuyBtn);
        buttons.add(rerollBtn);
        buttons.add(levelBtn);
        buttons.add(readyBtn);

        JPanel game = new JPanel(new BorderLayout(10, 10));
        game.add(areas, BorderLayout.CENTER);
        game.add(buttons, BorderLayout.SOUTH);
        add(game, BorderLayout.CENTER);

        // Kampflog
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Kampflog"));
        scroll.setPreferredSize(new Dimension(380, 0));
        add(scroll, BorderLayout.EAST);

        setSize(1250, 780);
        setLocationRelativeTo(null);
        setMyHalf();
    }

    // ---------- Ablauf ----------

    private void act(Action action) {
        if (combatPlayback) return;
        msgLabel.setText(" ");
        try {
            action.run();
        } catch (GameRuleException e) {
            msgLabel.setText(e.getMessage());
        } catch (RuntimeException e) {
            msgLabel.setText("Fehler: " + e.getMessage());
        }
        refresh();
    }

    /**
     * Demo: kauft Slot 1 mit Broker-Verzoegerung. Direkt danach sollte der
     * Benutzer "Reroll" klicken, damit die zweite Nachricht zuerst ankommt.
     */
    private void actDelayedBuy() {
        if (combatPlayback) return;
        msgLabel.setText(" ");
        try {
            service.buyDelayed(playerId, 1);
            logArea.append(">>> Delayed Delivery: buy(Slot 1) mit "
                    + (JmsConfig.DEMO_DELAY_MS / 1000) + " s Verzoegerung gesendet.\n");
            logArea.append(">>> Jetzt sofort 'Reroll' klicken, damit die Vektoruhr eine Luecke zeigt.\n");
        } catch (GameRuleException e) {
            msgLabel.setText(e.getMessage());
        } catch (RuntimeException e) {
            msgLabel.setText("Fehler: " + e.getMessage());
        }
        refresh();
    }

    private void onReady() {        msgLabel.setText(" ");
        try {
            service.ready(playerId);
        } catch (GameRuleException e) {
            msgLabel.setText(e.getMessage());
            return;
        } catch (RuntimeException e) {
            msgLabel.setText("Fehler: " + e.getMessage());
            return;
        }
        if (combatPlayback) return;
        GameStateDto st = state();
        if (st.gameOver()) {
            refresh();
            rerollBtn.setEnabled(false);
            readyBtn.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Gewinner: " + st.winnerName(), "Spiel vorbei",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void afterCombat() {
        combatPlayback = false;
        GameStateDto st = state();
        refresh();
        if (st.gameOver()) {
            rerollBtn.setEnabled(false);
            readyBtn.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Gewinner: " + st.winnerName(), "Spiel vorbei",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void setMyHalf() {
        GameStateDto s = state();
        boolean myTurn = s.turn() == playerId;
        String turnMsg = myTurn ? "(du bist dran)" : ("(warte auf " + s.opponent().name() + ")");
        statusLabel.setText(String.format("Runde %d   |   %s: %d HP, %d Gold   |   %s",
                s.round(), s.self().name(), s.self().hp(), s.self().gold(), turnMsg));
        rerollBtn.setEnabled(myTurn && !s.self().ready());
        delayedBuyBtn.setEnabled(myTurn && !s.self().ready());
        readyBtn.setEnabled(!s.self().ready());
        boardPanel.setEnabled(myTurn && !s.self().ready());
        shopPanel.setEnabled(myTurn && !s.self().ready());
    }

    private GameStateDto state() {
        try {
            return service.getState(playerId);
        } catch (GameRuleException e) {
            throw new IllegalStateException(e);
        }
    }

    private void refresh() {
        if (combatPlayback) return;
        GameStateDto s = state();
        PlayerDto me = s.self(), op = s.opponent();
        boolean myTurn = s.turn() == playerId;
        String turnMsg = myTurn ? "(du bist dran)" : ("(warte auf " + op.name() + ")");
        statusLabel.setText(String.format("Runde %d   |   %s: %d HP, %d Gold, Level %d   |   Feld %d/%d, Bench %d/%d   |   Gegner %s: %d HP, %d aufm Feld   |   %s",
                s.round(), me.name(), me.hp(), me.gold(), me.level(),
                me.board().size(), me.level(), me.bench().size(), GameEngine.BENCH_SIZE,
                op.name(), op.hp(), op.board().size(),
                turnMsg));

        boardPanel.setSetup(playerId, me.board(), me.bench(), op.board());
        shopPanel.removeAll();
        for (int i = 0; i < GameEngine.SHOP_SIZE; i++) {
            UnitDto u = i < me.shop().size() ? me.shop().get(i) : null;
            final int slot = i + 1;
            boolean tooExpensive = u != null && me.gold() < u.cost();
            shopPanel.add(new Slot(u, tooExpensive, "Klick: kaufen",
                    () -> act(() -> service.buy(playerId, slot))));
        }
        shopPanel.revalidate();
        shopPanel.repaint();
        setMyHalf();
    }

    // ---------- GameListener (Callbacks vom Server via JMS) ----------

    @Override
    public void onRoundStarted(int round) {
        SwingUtilities.invokeLater(() -> logArea.append("--- Runde " + round + " beginnt ---\n"));
    }

    @Override
    public void onCombatFrames(int round, List<CombatFrameDto> frames) {
        SwingUtilities.invokeLater(() -> {
            lastFrames = frames;
            combatPlayback = true;
            statusLabel.setText("Kampf wird ausgetragen ...");
            boardPanel.playFrames(frames, this::afterCombat);
        });
    }

    @Override
    public void onCombatFinished(CombatResultDto r) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("\n##### KAMPF Runde " + r.round() + " #####\n");
            r.log().forEach(line -> logArea.append(line + "\n"));
            GameStateDto st = state();
            String winnerName = r.winnerId() < 0 ? "Unentschieden" :
                    (r.winnerId() == playerId ? st.self().name() : st.opponent().name());
            logArea.append(winnerName + "\n\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void onUnitCombined(int combinedPlayerId, String unitName, int star) {
        SwingUtilities.invokeLater(() -> {
            String playerName = combinedPlayerId == playerId ? state().self().name() : state().opponent().name();
            logArea.append("*** " + playerName + ": 3x " + unitName + " vereinigt -> "
                    + UnitDto.starLabel(unitName, star) + " ***\n");
        });
    }

    @Override
    public void onTurnChanged(int turn) {
        SwingUtilities.invokeLater(() -> refresh());
    }

    @Override
    public void onGameOver(String winnerName) {
        SwingUtilities.invokeLater(() -> logArea.append("*** SPIEL VORBEI - Gewinner: " + winnerName + " ***\n"));
    }

    @Override
    public void onStateRefresh() {
        SwingUtilities.invokeLater(() -> {
            logArea.append("*** [Server] Zustand nach Verzoegerungskompensation aktualisiert. ***\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            refresh();
        });
    }

    // ---------- Einheiten-Karte (aus SwingMain uebernommen) ----------

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

            g.setColor(new Color(255, 215, 80));
            g.fillOval(10, 10, 28, 28);
            g.setColor(Color.BLACK);
            g.setFont(getFont().deriveFont(Font.BOLD, 15f));
            centered(g, String.valueOf(unit.cost()), 24, 24);

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

    // ---------- Main ----------

    public static void main(String[] args) {
        // Spieler-ID: erstes Argument (0 oder 1), Standard 0
        int parsedPlayerId = 0;
        if (args.length > 0) {
            try {
                parsedPlayerId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Ungueltige Spieler-ID: " + args[0] + " - verwende Standard 0");
            }
        }
        final int playerId = parsedPlayerId;

        SwingUtilities.invokeLater(() -> {
            try {
                // JMS-Client verbinden
                // Vektoruhr-Index: Spieler 0 -> Index 1, Spieler 1 -> Index 2
                int vcIndex = playerId + 1;
                JmsClient client = new JmsClient("swing-player-" + playerId, vcIndex);
                client.connect();
                System.out.println("  [VC-Client] Vektoruhr-Index: " + vcIndex + " (Spieler " + playerId + ")");

                // GUI erstellen (Stub kapselt die JMS-Kommunikationsschicht)
                JmsGameServiceStub service = new JmsGameServiceStub(client);
                SwingJmsMain ui = new SwingJmsMain(client, service, playerId);

                // Listener fuer Callbacks registrieren
                client.registerListener(ui);

                // Spielstand vom Server holen und GUI aktualisieren
                ui.refresh();
                ui.setVisible(true);

                // Bei Fenster-Schliessung: Client trennen
                ui.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        client.close();
                    }
                });

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Fehler beim Verbinden mit dem JMS-Broker:\n" + e.getMessage() + "\n\n"
                        + "Stelle sicher, dass ActiveMQ laeuft:\n"
                        + "apache-activemq-6.4.0\\bin\\activemq.bat start\n"
                        + "Und dass JmsServer laeuft:\n"
                        + "java -cp ... JmsServer",
                        "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        });
    }
}
