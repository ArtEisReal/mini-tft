import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
// AI-GENERATED-END

/**
 * Spiellogik. Implementiert GameService (Design Pattern: Facade).
 * Läuft auf dem Server; der Client sieht nur GameService + DTOs.
 * 
 * RMI-Anpassung: Erbt von UnicastRemoteObject für Remote-Zugriff.
 * 
 * Einheiten: Kauf -> Bench -> per toField aufs Feld (begrenzt durch das Level).
 * 3 gleiche Einheiten (Bench + Feld zusammen) verschmelzen zur naechsten Sternstufe.
 */
public class GameEngine extends UnicastRemoteObject implements GameService {
    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    /** Brettgroesse und Bretthalfte: Spieler 0 besetzt die oberen Zeilen, Spieler 1 die unteren. */
    static final int BOARD_SIZE = 9;
    static final int PLAYER_ROWS = 4;
    /** Auf dem Feld duerfen hoechstens Level Einheiten stehen; Leveln kostet Gold. */
    static final int START_LEVEL = 2;
    static final int MAX_LEVEL = 9;
    static final int LEVEL_COST = 4;
    static final int BENCH_SIZE = 9;
    static final int SHOP_SIZE = 3;
    static final int REROLL_COST = 1;
    static final int INCOME = 5;

    private final Player[] players;
    private final List<GameListener> listeners = new ArrayList<>();
    private final UnitFactory factory;
    private int round = 0;
    private boolean gameOver = false;
    private String winnerName = null;
    // AI-GENERATED-END

    // AI-GENERATED-START: Konstruktoren - fester Export-Port (0 = anonym) als neue Variante, Bestandskonstruktoren delegieren - 2026-11-02 12:41 CET
    /** Erstellt den RMI-Stub auf einem anonymen Port. */
    protected GameEngine(String name0, String name1) throws RemoteException {
        this(name0, name1, new UnitFactory(new Random()), 0);
    }

    /**
     * Erstellt den RMI-Stub auf einem festen Port (0 = anonymer Port).
     * Fester Port = nur eine Firewall-Regel noetig, wenn der Server
     * auf einem anderen Rechner laeuft.
     */
    protected GameEngine(String name0, String name1, int port) throws RemoteException {
        this(name0, name1, new UnitFactory(new Random()), port);
    }

    /** Mit eigener Factory (z.B. fuer deterministische Tests). */
    protected GameEngine(String name0, String name1, UnitFactory factory) throws RemoteException {
        this(name0, name1, factory, 0);
    }

    /** Mit eigener Factory und festem Port. */
    protected GameEngine(String name0, String name1, UnitFactory factory, int port) throws RemoteException {
        super(port);
        this.factory = factory;
        players = new Player[] {new Player(0, name0), new Player(1, name1)};
    }
    // AI-GENERATED-END

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    public synchronized void start() {
        startRound();
    }

    @Override
    // AI-GENERATED-END
    public synchronized void addListener(GameListener listener) throws RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        listeners.add(listener);
        // AI-GENERATED-END
        // AI-GENERATED-START: Feedback, wenn sich ein Client registriert - 2026-11-02 13:02 CET
        System.out.println("Client verbunden (Listener registriert) - insgesamt "
                + listeners.size() + " Listener.");
        // AI-GENERATED-END
    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    }

    // ---------- Befehle (vom Client aufgerufen) ----------

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto buy(int playerId, int slot) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        int idx = slot - 1;
        if (idx < 0 || idx >= p.shop.size()) throw new GameRuleException("Ungueltiger Shop-Slot: " + slot);
        Unit u = p.shop.get(idx);
        if (p.bench.size() >= BENCH_SIZE) throw new GameRuleException("Bench ist voll (" + BENCH_SIZE + ") - verkaufe etwas");
        if (p.gold < u.cost) throw new GameRuleException("Zu wenig Gold: " + u.name + " kostet " + u.cost + ", du hast " + p.gold);
        p.gold -= u.cost;
        p.bench.add(p.shop.remove(idx));
        combine(p);
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto sell(int playerId, int index) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        Unit u = fieldUnit(p, index);
        p.gold += u.sellValue();
        p.board.remove(u);
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto sellBench(int playerId, int slot) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        Unit u = benchUnit(p, slot);
        p.gold += u.sellValue();
        p.bench.remove(u);
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto move(int playerId, int index, int row, int col) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        Unit u = fieldUnit(p, index);
        checkOwnHalf(p, row, col);
        Unit occupant = occupant(p, row, col);
        if (occupant != null && occupant != u) { // Tausch innerhalb des Feldes
            occupant.row = u.row;
            occupant.col = u.col;
        }
        u.row = row;
        u.col = col;
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto toField(int playerId, int benchSlot, int row, int col) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        Unit u = benchUnit(p, benchSlot);
        checkOwnHalf(p, row, col);
        Unit occupant = occupant(p, row, col);
        if (occupant == null && p.board.size() >= p.level)
            throw new GameRuleException("Feld voll (" + p.board.size() + "/" + p.level
                    + ") - tausche eine Einheit aus oder steige ein Level auf");
        p.bench.remove(u);
        if (occupant != null) { // Tausch: die Feld-Einheit geht auf die Bench-Platz
            p.board.remove(occupant);
            occupant.row = -1;
            occupant.col = -1;
            p.bench.add(occupant);
        }
        u.row = row;
        u.col = col;
        p.board.add(u);
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto toBench(int playerId, int index) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        Unit u = fieldUnit(p, index);
        if (p.bench.size() >= BENCH_SIZE) throw new GameRuleException("Bench ist voll (" + BENCH_SIZE + ")");
        p.board.remove(u);
        u.row = -1;
        u.col = -1;
        p.bench.add(u);
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto levelUp(int playerId) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        if (p.level >= MAX_LEVEL) throw new GameRuleException("Maximales Level erreicht (" + MAX_LEVEL + ")");
        if (p.gold < LEVEL_COST) throw new GameRuleException("Zu wenig Gold zum Leveln (" + LEVEL_COST + ")");
        p.gold -= LEVEL_COST;
        p.level++;
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto reroll(int playerId) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        if (p.gold < REROLL_COST) throw new GameRuleException("Zu wenig Gold fuer Reroll (" + REROLL_COST + ")");
        p.gold -= REROLL_COST;
        refillShop(p);
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized PlayerDto ready(int playerId) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = activePlayer(playerId);
        p.ready = true;
        if (players[0].ready && players[1].ready) resolveCombat();
        return p.toDto(false);
    }

    @Override
    // AI-GENERATED-END
    public synchronized GameStateDto getState(int playerId) throws GameRuleException, RemoteException {
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        Player p = player(playerId);
        Player opp = players[1 - playerId];
        return new GameStateDto(round, p.toDto(false), opp.toDto(true), gameOver, winnerName);
    }
    // AI-GENERATED-END

    // AI-GENERATED-START: Aufgabe 6 - Benchmark-Aufruf (identischer Code lokal und remote) - 2026-11-02 14:40 CET
    @Override
    public long ping() throws RemoteException {
        return System.nanoTime();
    }
    // AI-GENERATED-END

    // AI-GENERATED-START: JSON-RPC-Varianten - Marshalling/Unmarshalling mit JSON-P - 2026-11-02 14:10 CET
    @Override
    public synchronized String getStateJson(int playerId) throws GameRuleException, RemoteException {
        return JsonMapper.write(JsonMapper.toJson(getState(playerId)), true);
    }

    @Override
    public synchronized String executeJson(String commandJson) throws GameRuleException, RemoteException {
        JsonMapper.CommandRequest req = JsonMapper.parseCommand(commandJson);
        PlayerDto result;
        switch (req.cmd()) {
            case "buy" -> result = buy(req.playerId(), req.arg(0));
            case "sell" -> result = sell(req.playerId(), req.arg(0));
            case "sellbench" -> result = sellBench(req.playerId(), req.arg(0));
            case "tofield" -> result = toField(req.playerId(), req.arg(0), req.arg(1), req.arg(2));
            case "tobench" -> result = toBench(req.playerId(), req.arg(0));
            case "move" -> result = move(req.playerId(), req.arg(0), req.arg(1), req.arg(2));
            case "levelup" -> result = levelUp(req.playerId());
            case "reroll" -> result = reroll(req.playerId());
            case "ready" -> result = ready(req.playerId());
            default -> throw new GameRuleException("Unbekannter JSON-Befehl: \"" + req.cmd() + "\"");
        }
        return JsonMapper.write(JsonMapper.toJson(result), true);
    }
    // AI-GENERATED-END

    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    // ---------- interne Logik ----------

    private Player player(int id) throws GameRuleException {
        if (id < 0 || id > 1) throw new GameRuleException("Unbekannter Spieler: " + id);
        return players[id];
    }

    private Player activePlayer(int id) throws GameRuleException {
        Player p = player(id);
        if (gameOver) throw new GameRuleException("Das Spiel ist vorbei");
        if (p.ready) throw new GameRuleException("Du bist bereits bereit - warte auf den Gegner");
        return p;
    }

    /** 1-basierter Index -> Feld-Einheit. */
    private static Unit fieldUnit(Player p, int index) throws GameRuleException {
        int idx = index - 1;
        if (idx < 0 || idx >= p.board.size())
            throw new GameRuleException("Ungueltige Feld-Position: " + index);
        return p.board.get(idx);
    }

    /** 1-basierter Slot -> Bench-Einheit. */
    private static Unit benchUnit(Player p, int slot) throws GameRuleException {
        int idx = slot - 1;
        if (idx < 0 || idx >= p.bench.size())
            throw new GameRuleException("Ungueltige Bench-Position: " + slot);
        return p.bench.get(idx);
    }

    /** Darf auf row/col platziert werden? Gehoert die Zelle der eigenen Halbseite? */
    private static void checkOwnHalf(Player p, int row, int col) throws GameRuleException {
        int firstRow = firstRow(p);
        if (col < 0 || col >= BOARD_SIZE || row < firstRow || row >= firstRow + PLAYER_ROWS)
            throw new GameRuleException("Nur die eigene Bretthalfte ist belegbar (Zeilen " + firstRow
                    + "-" + (firstRow + PLAYER_ROWS - 1) + ")");
    }

    private static Unit occupant(Player p, int row, int col) {
        for (Unit u : p.board) {
            if (u.row == row && u.col == col) return u;
        }
        return null;
    }

    /** Erste Zeile der Bretthalfte eines Spielers. */
    private static int firstRow(Player p) {
        return p.id == 0 ? 0 : BOARD_SIZE - PLAYER_ROWS;
    }

    private void refillShop(Player p) {
        p.shop.clear();
        for (int i = 0; i < SHOP_SIZE; i++) p.shop.add(factory.randomUnit());
    }

    /**
     * Vereinigung (Sterne-System): 3 gleiche Einheiten (gleicher Name + gleiche Sternzahl,
     * Bench und Feld zusammengerechnet) verschmelzen zur naechsten Sternstufe –
     * Werte via Unit-Stern-Skala (2-Sterne 1.5x, 3-Sterne ueberproportional 4x).
     * Ueberlebende Einheit behaelt ihren Platz (Feld oder Bench).
     */
    private void combine(Player p) {
        boolean merged = true;
        while (merged) {
            merged = false;
            List<Unit> all = new ArrayList<>(p.board);
            all.addAll(p.bench);
            outer:
            for (int i = 0; i < all.size(); i++) {
                for (int j = i + 1; j < all.size(); j++) {
                    for (int k = j + 1; k < all.size(); k++) {
                        Unit a = all.get(i), b = all.get(j), c = all.get(k);
                        if (a.star >= 3 || a.star != b.star || b.star != c.star || !a.name.equals(b.name)
                                || !b.name.equals(c.name)) continue;
                        a.star++;
                        a.applyStar();
                        removeAnywhere(p, b);
                        removeAnywhere(p, c);
                        merged = true;
                        // AI-GENERATED-END
                        for (GameListener l : listeners) {
                            try { l.onUnitCombined(p.id, a.name, a.star); }
                            catch (RemoteException e) {
                                System.err.println("Listener-Callback fehlgeschlagen: " + e.getMessage());
                            }
                        }
                        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
                        break outer;
                    }
                }
            }
        }
    }

    private static void removeAnywhere(Player p, Unit u) {
        if (!p.board.remove(u)) p.bench.remove(u);
    }

    private void startRound() {
        round++;
        for (Player p : players) {
            p.gold += INCOME;
            p.ready = false;
            refillShop(p);
        }
        // AI-GENERATED-END
        for (GameListener l : listeners) {
            try { l.onRoundStarted(round); }
            catch (RemoteException e) {
                System.err.println("Listener-Callback fehlgeschlagen: " + e.getMessage());
            }
        }
    // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
    }

    private void resolveCombat() {
        List<CombatFrameDto> frames = BoardCombat.run(players[0].board, players[1].board,
                players[0].name, players[1].name);
        List<String> log = new ArrayList<>();
        for (CombatFrameDto f : frames) {
            log.add("--- Tick " + f.tick() + " ---");
            f.events().forEach(e -> log.add(e.text()));
        }

        int winner = -1;
        int alive0 = 0, alive1 = 0;
        if (!frames.isEmpty()) {
            for (UnitStateDto u : frames.get(frames.size() - 1).units()) {
                if (!u.alive()) continue;
                if (u.team() == 0) alive0++;
                else alive1++;
            }
        }
        if (alive0 > 0 && alive1 == 0) winner = 0;
        else if (alive1 > 0 && alive0 == 0) winner = 1;

        int damage = 0;
        if (winner >= 0) {
            int survivors = winner == 0 ? alive0 : alive1;
            damage = 2 + survivors;
            players[1 - winner].hp -= damage;
        }

        // Animation zuerst (Richtung 2: Callback mit den Frames), dann das Ergebnis
        // AI-GENERATED-END
        for (GameListener l : listeners) {
            try { l.onCombatFrames(round, frames); }
            catch (RemoteException e) {
                System.err.println("Listener-Callback fehlgeschlagen: " + e.getMessage());
            }
        }
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        CombatResultDto result = new CombatResultDto(round, log, winner, damage);
        // AI-GENERATED-END
        for (GameListener l : listeners) {
            try { l.onCombatFinished(result); }
            catch (RemoteException e) {
                System.err.println("Listener-Callback fehlgeschlagen: " + e.getMessage());
            }
        }

        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        for (Player p : players) {
            if (p.hp <= 0) {
                gameOver = true;
                winnerName = players[1 - p.id].name;
            }
        }
        if (gameOver) {
        // AI-GENERATED-END
            for (GameListener l : listeners) {
                try { l.onGameOver(winnerName); }
                catch (RemoteException e) {
                    System.err.println("Listener-Callback fehlgeschlagen: " + e.getMessage());
                }
            }
        // AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
        } else {
            startRound();
        }
    }
}
// AI-GENERATED-END
