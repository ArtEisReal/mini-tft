// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
// AI-GENERATED-END
import java.util.List;

/**
 * Swing-GUI (Hotseat). Spricht nur ueber das Interface GameService mit der Spiellogik
 * und bekommt Updates ueber GameListener -> spaeter einfach den Stub statt GameEngine einsetzen.
 *
 * Bedienung: Shop-Klick = kaufen (Einheit landet auf dem Brett), Brett-Klick = verschieben,
 * Rechtsklick auf eigene Einheit = verkaufen. Nach "Fertig" wird der Kampf animiert.
 */
public class SwingMain extends JFrame implements GameListener {
    /** Eigene IP-Adresse des Client-Rechners. */
    private static final String DEFAULT_CLIENT_HOST = "10.8.3.2";
    /** IP-Adresse des Rechners, auf dem RmiServer läuft. */
    private static final String DEFAULT_SERVER_HOST = "10.8.3.2";

    /** Name des Spielers nach ID (Hotseat-Modus). */
    private static final String[] NAMES = {"Spieler 1", "Spieler 2"};

    private final GameService service;
    private final String serverHost;
    /** Spieler-ID dieses Clients (RMI-Modus, -1 = Hotseat). */
    private final int playerId;
    /** Aktueller Spieler im Hotseat-Modus (nur wenn playerId == -1). */
    private int current = 0;
    private static final Color[] COST_COLORS = {
            new Color(120, 124, 135),  // Kosten 1
            new Color(54, 150, 92),    // Kosten 2
            new Color(52, 104, 196),   // Kosten 3
            new Color(142, 70, 184)};  // Kosten 4+

    private interface Action {
    // AI-GENERATED-END
        void run() throws GameRuleException;
    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    }

    // AI-GENERATED-START: letzte Kampf-Frames fuer die JSON-Ansicht merken - 2026-11-02 16:05 CET
    private List<CombatFrameDto> lastFrames;
    // AI-GENERATED-END
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

    /** Hotseat-Konstruktor (lokaler Start über Main, kein RMI). */
    public SwingMain(GameService service, String serverHost) {
        this(service, serverHost, -1);
    }

    /** RMI-Konstruktor (Netzwerk-Client mit fester Spieler-ID). */
    public SwingMain(GameService service, String serverHost, int playerId) {
        super("Mini-TFT");
        if (playerId >= 0) {
            setTitle("Mini-TFT - Spieler " + playerId);
        }
        this.service = service;
        this.serverHost = serverHost;
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

        // Spielfeld: 9x9-Brett + Bench + Shop
        boardPanel.setBorder(BorderFactory.createTitledBorder(
                "Spielfeld + Bench (Feld-Einheit anklicken = verschieben/auswechseln, "
                        + "Bench anklicken = aufs Feld, Rechtsklick = verkaufen)"));
        boardPanel.setActions(new BoardPanel.Actions() {
            @Override
            public void onMove(int index, int row, int col) {
                act(() -> service.move(activeId(), index, row, col));
            }

            @Override
            public void onToField(int benchSlot, int row, int col) {
                act(() -> service.toField(activeId(), benchSlot, row, col));
            }

            @Override
            public void onToBench(int index) {
                act(() -> service.toBench(activeId(), index));
            }

            @Override
            public void onSellField(int index) {
                act(() -> service.sell(activeId(), index));
            }

            @Override
            public void onSellBench(int benchSlot) {
                act(() -> service.sellBench(activeId(), benchSlot));
            }
        });
        shopPanel.setBorder(BorderFactory.createTitledBorder("Shop (Klick = kaufen, landet auf der Bench)"));
        JPanel areas = new JPanel(new BorderLayout(0, 10));
        areas.add(boardPanel, BorderLayout.CENTER);
        areas.add(shopPanel, BorderLayout.SOUTH);

        rerollBtn.addActionListener(e -> act(() -> service.reroll(activeId())));
        JButton levelBtn = new JButton("Level up (" + GameEngine.LEVEL_COST + " Gold)");
        levelBtn.setToolTipText("Gibt +1 Platz auf dem Feld (max. Level " + GameEngine.MAX_LEVEL + ")");
        levelBtn.addActionListener(e -> act(() -> service.levelUp(activeId())));
        readyBtn.addActionListener(e -> onReady());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(rerollBtn);
        buttons.add(levelBtn);
        buttons.add(readyBtn);

        JPanel game = new JPanel(new BorderLayout(10, 10));
        game.add(areas, BorderLayout.CENTER);
        game.add(buttons, BorderLayout.SOUTH);

        center.add(game, "game");

        // Sichtschutz zwischen den Spielern (nur im Hotseat-Modus)
        if (playerId < 0) {
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
            center.add(cover, "cover");
        }

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
        setJMenuBar(buildMenuBar());
        if (playerId >= 0) {
            setMyHalf();
        } else {
            showCover();
        }
    }

    /** Gibt die aktive Spieler-ID zurück (playerId im RMI-Modus, current im Hotseat-Modus). */
    private int activeId() {
        return playerId >= 0 ? playerId : current;
    }

    // ---------- Ablauf ----------

    private void act(Action action) {
        if (combatPlayback) return; // waehrend der Animation ist keine Eingabe moeglich
        msgLabel.setText(" ");
        try {
            action.run();
        } catch (GameRuleException e) {
            msgLabel.setText(e.getMessage());
            // AI-GENERATED-END
        } catch (RuntimeException e) {
            msgLabel.setText("Fehler: " + e.getMessage());
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        }
        refresh();
    }

    private void onReady() {
        msgLabel.setText(" ");
        try {
            service.ready(activeId());
        } catch (GameRuleException e) {
            msgLabel.setText(e.getMessage());
            return;
            // AI-GENERATED-END
        } catch (RuntimeException e) {
            msgLabel.setText("Fehler: " + e.getMessage());
            return;
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
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

    /** Eingabefelder aktivieren/deaktivieren je nachdem, ob ich dran bin (nur RMI-Modus). */
    private void setMyHalf() {
        GameStateDto s = state();
        boolean myTurn = s.turn() == playerId;
        rerollBtn.setEnabled(myTurn && !s.self().ready());
        readyBtn.setEnabled(!s.self().ready());
        boardPanel.setEnabled(myTurn && !s.self().ready());
        shopPanel.setEnabled(myTurn && !s.self().ready());
    }

    private GameStateDto state() {
        try {
            return service.getState(activeId());
        } catch (GameRuleException e) {
            throw new IllegalStateException(e);
            // AI-GENERATED-END
        } catch (RuntimeException e) {
            throw new IllegalStateException("Fehler: " + e.getMessage(), e);
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        }
    }

    private void refresh() {
        if (combatPlayback) return;
        GameStateDto s = state();
        PlayerDto me = s.self(), op = s.opponent();
        int active = activeId();
        if (playerId >= 0) {
            // RMI-Modus: Turn-Meldung anzeigen
            boolean myTurn = s.turn() == playerId;
            String turnMsg = myTurn ? "(du bist dran)" : ("(warte auf " + op.name() + ")");
            statusLabel.setText(String.format("Runde %d   |   %s: %d HP, %d Gold, Level %d   |   Feld %d/%d, Bench %d/%d   |   Gegner %s: %d HP, %d aufm Feld   |   %s",
                    s.round(), me.name(), me.hp(), me.gold(), me.level(),
                    me.board().size(), me.level(), me.bench().size(), GameEngine.BENCH_SIZE,
                    op.name(), op.hp(), op.board().size(),
                    turnMsg));
        } else {
            // Hotseat-Modus: alte Statuszeile ohne Turn-Meldung
            statusLabel.setText(String.format("Runde %d   |   %s: %d HP, %d Gold, Level %d   |   Feld %d/%d, Bench %d/%d   |   Gegner %s: %d HP, %d aufm Feld   |   Haelfte: %s",
                    s.round(), me.name(), me.hp(), me.gold(), me.level(),
                    me.board().size(), me.level(), me.bench().size(), GameEngine.BENCH_SIZE,
                    op.name(), op.hp(), op.board().size(),
                    current == 0 ? "oben" : "unten"));
        }

        boardPanel.setSetup(active, me.board(), me.bench(), op.board());
        shopPanel.removeAll();
        for (int i = 0; i < GameEngine.SHOP_SIZE; i++) {
            UnitDto u = i < me.shop().size() ? me.shop().get(i) : null;
            final int slot = i + 1;
            boolean tooExpensive = u != null && me.gold() < u.cost();
            shopPanel.add(new Slot(u, tooExpensive, "Klick: kaufen",
                    () -> act(() -> service.buy(active, slot))));
        }
        shopPanel.revalidate();
        shopPanel.repaint();
        if (playerId >= 0) setMyHalf();
    }

    // ---------- JSON-Nachrichtenformat + Benchmark (Menue) ----------
    // AI-GENERATED-START: JSON-Ansicht, JSON-Befehl und Benchmark in der GUI - 2026-11-02 16:05 CET

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu view = new JMenu("Ansicht");
        JMenuItem jsonState = new JMenuItem("Spielstand als JSON ...");
        jsonState.setToolTipText("getStateJson(activeId) - Server marshallt, Client zeigt den Rohtext");
        jsonState.addActionListener(e -> showJsonState());
        view.add(jsonState);

        JMenuItem jsonCommand = new JMenuItem("Befehl als JSON senden ...");
        jsonCommand.setToolTipText("executeJson(...) - Befehl als JSON zum Server, Antwort als JSON zurueck");
        jsonCommand.addActionListener(e -> sendJsonCommand());
        view.add(jsonCommand);

        JMenuItem jsonFrames = new JMenuItem("Kampf-Frames als JSON ...");
        jsonFrames.setToolTipText("CombatFrameDto/UnitStateDto/CombatEventDto als JSON");
        jsonFrames.addActionListener(e -> showJsonFrames());
        view.add(jsonFrames);

        JMenu extras = new JMenu("Extras");
        JMenuItem bench = new JMenuItem("Benchmark (lokal vs. RPC) ...");
        bench.setToolTipText("Aufgabe 6: Dauer lokaler Aufruf vs. entfernter RMI-Aufruf");
        bench.addActionListener(e -> runBenchmark());
        extras.add(bench);

        bar.add(view);
        bar.add(extras);
        return bar;
    }

    /** Zeigt den Spielstand so, wie er als JSON-String vom Server kommt. */
    private void showJsonState() {
        msgLabel.setText(" ");
        try {
            showTextDialog("Spielstand als JSON (Server -> Client)",
                    service.getStateJson(activeId()));
        } catch (GameRuleException | RuntimeException e) {
            msgLabel.setText("JSON nicht verfuegbar: " + e.getMessage());
        }
    }

    /** Zeigt die zuletzt empfangenen Kampf-Frames (DTOs der Animation) als JSON. */
    private void showJsonFrames() {
        if (lastFrames == null || lastFrames.isEmpty()) {
            showTextDialog("Kampf-Frames als JSON",
                    "Noch kein Kampf gespielt - es liegen keine Frames vor.");
            return;
        }
        CombatFrameDto last = lastFrames.get(lastFrames.size() - 1);
        showTextDialog("Kampf-Frames als JSON (Server -> Client)",
                "Frames insgesamt: " + lastFrames.size() + "  (hier der letzte Tick)\n\n"
                        + JsonMapper.write(JsonMapper.toJson(last), true));
    }

    /** Schickt eine Konsoleneingabe (z.B. "buy 1") als JSON zum Server und zeigt Request + Response. */
    private void sendJsonCommand() {
        if (combatPlayback) return;
        String input = JOptionPane.showInputDialog(this,
                "Befehl wie in der Konsole, z.B.  buy 1  |  reroll  |  levelup  |  tofield 1 0 0",
                "Befehl als JSON senden", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.isBlank()) return;
        String line = input.trim();
        try {
            // Command-Pattern: parse() validiert Name und Argumente der Eingabe
            Command.parse(line);
            String request = JsonMapper.commandJson(line, activeId());
            String response = service.executeJson(request);
            PlayerDto p = JsonMapper.toPlayer(response);
            showTextDialog("JSON-Roundtrip (Client -> Server -> Client)",
                    "--> Request  (Marshalling auf dem Client)\n" + request
                            + "\n<-- Response (Marshalling auf dem Server)\n" + response
                            + "\nUnmarshalling auf dem Client: " + p.name() + " | HP " + p.hp()
                            + " | Gold " + p.gold() + " | Level " + p.level());
            refresh();
        } catch (GameRuleException e) {
            msgLabel.setText(e.getMessage()); // als Exception vom Server uebertragen
        } catch (IllegalArgumentException e) {
            msgLabel.setText("Ungueltiger Befehl: " + e.getMessage());
            Toolkit.getDefaultToolkit().beep();
        } catch (RuntimeException e) {
            msgLabel.setText("Fehler: " + e.getMessage());
        }
    }

    /** Aufgabe 6: lokale Aufrufe vs. entfernte RPC-Aufrufe messen (im Hintergrund). */
    private void runBenchmark() {
        String count = JOptionPane.showInputDialog(this,
                "Anzahl Aufrufe je Methode:", RpcBenchmark.DEFAULT_ITERATIONS);
        if (count == null || count.isBlank()) return;
        int iterations;
        try {
            iterations = Integer.parseInt(count.trim());
        } catch (NumberFormatException e) {
            msgLabel.setText("Bitte eine Zahl eingeben, z.B. 300");
            return;
        }
        msgLabel.setText("Benchmark laeuft (" + iterations + " Aufrufe je Methode) ...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return RpcBenchmark.compare(service, serverHost, iterations);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                msgLabel.setText(" ");
                try {
                    showTextDialog("Aufgabe 6: lokaler Aufruf vs. entfernter RPC", get());
                } catch (Exception e) {
                    msgLabel.setText("Benchmark fehlgeschlagen: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void showTextDialog(String title, String text) {
        JTextArea area = new JTextArea(text, 26, 90);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), title, JOptionPane.INFORMATION_MESSAGE);
    }
    // AI-GENERATED-END

    // ---------- GameListener (Callbacks von der Spiellogik) ----------

    @Override
    public void onRoundStarted(int round) {
        logArea.append("--- Runde " + round + " beginnt ---\n");
    }

    @Override
    // AI-GENERATED-END
    public void onCombatFrames(int round, List<CombatFrameDto> frames) {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        lastFrames = frames;
        combatPlayback = true;
        statusLabel.setText("Kampf wird ausgetragen ...");
        boardPanel.playFrames(frames, this::afterCombat);
    }

    @Override
    public void onCombatFinished(CombatResultDto r) {
        logArea.append("\n##### KAMPF Runde " + r.round() + " #####\n");
        r.log().forEach(line -> logArea.append(line + "\n"));
        String winnerName;
        if (r.winnerId() < 0) {
            winnerName = "Unentschieden!";
        } else if (playerId >= 0) {
            // RMI-Modus: Namen aus dem Server-Zustand holen
            GameStateDto st = state();
            winnerName = r.winnerId() == playerId ? st.self().name() : st.opponent().name();
        } else {
            // Hotseat-Modus: Namen aus dem Array holen
            winnerName = NAMES[r.winnerId()];
        }
        logArea.append(winnerName + (r.winnerId() < 0 ? "" : " (Gegner verliert " + r.damage() + " HP)") + "\n\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    @Override
    // AI-GENERATED-END
    public void onUnitCombined(int combinedPlayerId, String unitName, int star) {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        String playerName;
        if (this.playerId >= 0) {
            // RMI-Modus: Namen aus dem Server-Zustand holen
            playerName = combinedPlayerId == this.playerId ? state().self().name() : state().opponent().name();
        } else {
            // Hotseat-Modus: Namen aus dem Array holen
            playerName = NAMES[combinedPlayerId];
        }
        logArea.append("*** " + playerName + ": 3x " + unitName + " vereinigt -> "
                + UnitDto.starLabel(unitName, star) + " ***\n");
    }

    @Override
    public void onTurnChanged(int turn) {
        refresh();
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
    // AI-GENERATED-END
        // AI-GENERATED-START: Server-Host-Resolution (Argument / minitft.server / localhost) - 2026-11-02 12:41 CET
        // Server-Host: erst Argument, sonst minitft.server/MINITFT_SERVER, sonst "localhost"
        // Die Konstanten ermöglichen den Start direkt aus IntelliJ ohne Argumente.
        if (System.getProperty("minitft.host") == null) {
            System.setProperty("minitft.host", DEFAULT_CLIENT_HOST);
        }
        String host = args.length > 0 ? args[0] : DEFAULT_SERVER_HOST;
        // AI-GENERATED-END

        // Spieler-ID: zweites Argument (0 oder 1), Standard 0
        int parsedPlayerId = 0;
        if (args.length > 1) {
            try {
                parsedPlayerId = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Ungueltige Spieler-ID: " + args[1] + " - verwende Standard 0");
            }
        }
        final int playerId = parsedPlayerId;

        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        SwingUtilities.invokeLater(() -> {
        // AI-GENERATED-END
            try {
                // RMI-Client verbinden
                RmiClient client = new RmiClient(host);
                client.connect();
                GameService service = client.getService();

                // GUI erstellen (mit Spieler-ID)
                SwingMain ui = new SwingMain(service, host, playerId);

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
