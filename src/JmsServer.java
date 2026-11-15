import jakarta.jms.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMS-Server fuer Mini-TFT.
 *
 * Ersetzt RmiServer: statt RMI-Registry und -Stub wird ein JMS-Consumer verwendet,
 * um Befehle von Clients zu empfangen. Callbacks werden als JMS-Nachrichten
 * an die Reply-Queue jedes Clients gesendet.
 *
 * Architektur:
 *   Client --(JmsMessage/COMMAND)--> Queue: mini-tft/commands
 *   Server <--(liest)---------------- Queue: mini-tft/commands
 *   Server --(JmsMessage/CALLBACK)--> Queue: mini-tft/reply/<clientId>/<sessionId>
 *
 * Der Server verwendet:
 *   - {@link JmsGameSkeleton} als Kommunikations-Skeleton zur GameEngine
 *   - {@link VectorClockManager} zur Erkennung von Luecken/Duplikaten
 *   - ein Befehls-Journal mit Snapshots fuer Rewind/Replay
 *   - JMS-Correlation-IDs fuer Request/Reply und Duplikaterkennung
 *
 * Verwendung:
 *   java JmsServer [Spieler0] [Spieler1]
 *
 * Der Server erwartet, dass ActiveMQ bereits laeuft (z.B. via
 *   apache-activemq-6.4.0/bin/activemq start
 * ). Die Web-Konsole ist unter http://localhost:8161/admin/ erreichbar.
 */
public class JmsServer {

    /** Client-ID -> Reply-Queue-Name. */
    private final Map<String, String> clientQueues = new ConcurrentHashMap<>();

    /** Client-ID -> Session-ID der aktuell verbundenen Instanz (erkennt Reconnects). */
    private final Map<String, String> clientSessions = new ConcurrentHashMap<>();

    /** Vektoruhr-Manager fuer kausale Abhaengigkeitsverfolgung. */
    private final VectorClockManager vcManager = new VectorClockManager();

    /** Zaehler fuer die Zuordnung von Clients zu Vektoruhr-Indizes (1 = Spieler 0, 2 = Spieler 1). */
    private final AtomicInteger nextVcIndex = new AtomicInteger(1);

    /**
     * Befehls-Journal in Verarbeitungsreihenfolge. Jeder Eintrag enthaelt den
     * Spielzustand VOR der Ausfuehrung (Snapshot) fuer Rewind/Replay.
     */
    private final List<CommandJournalEntry> journal = new ArrayList<>();

    /** Bereits gesehene Correlation-IDs (Duplikaterkennung bei JMS-Redelivery). */
    private final Set<String> seenCorrelations = ConcurrentHashMap.newKeySet();

    private GameEngine engine;
    private JmsGameSkeleton skeleton;
    private Connection connection;
    private Session session;
    private MessageConsumer commandConsumer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Startet den JMS-Server.
     */
    public static void main(String[] args) {
        String name0 = args.length > 0 ? args[0] : "Spieler 0";
        String name1 = args.length > 1 ? args[1] : "Spieler 1";

        JmsServer server = new JmsServer();
        try {
            server.start(name0, name1);
            System.out.println("JMS-Server wartet auf Nachrichten...");
            System.out.println("Web-Konsole: http://localhost:8161/admin/");
            System.out.println("  Queues: " + JmsConfig.COMMAND_QUEUE + ", " + JmsConfig.CALLBACK_QUEUE);
            System.out.println("  Delayed Delivery: AMQ_SCHEDULED_DELAY (Demo: "
                    + JmsConfig.DEMO_DELAY_MS + " ms)");
        } catch (Exception e) {
            System.err.println("Fehler beim Starten des JMS-Servers:");
            e.printStackTrace();
        }
    }

    /**
     * Startet den Server: verbindet zum Broker, erstellt GameEngine,
     * registriert sich als GameListener und beginnt, Befehle zu empfangen.
     */
    public void start(String name0, String name1) throws Exception {
        // GameEngine erstellen (lokal, nicht RMI) und hinter das Skeleton legen.
        engine = new GameEngine(name0, name1);
        skeleton = new JmsGameSkeleton(engine);
        System.out.println("GameEngine fuer " + name0 + " und " + name1 + " erstellt.");

        // JMS-Verbindung aufbauen
        String brokerUrl = JmsConfig.brokerUrl();
        System.out.println("JMS-Server verbindet zu: " + brokerUrl);

        ConnectionFactory factory = new org.apache.activemq.ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Consumer fuer Befehle (Client -> Server)
        Destination commandQueue = session.createQueue(JmsConfig.COMMAND_QUEUE);
        commandConsumer = session.createConsumer(commandQueue);

        // GameListener registrieren — Callbacks gehen an alle verbundenen Clients
        engine.addListener(new JmsGameListener(this));

        // Spiel starten
        engine.start();

        // Befehle empfangen
        commandConsumer.setMessageListener(msg -> {
            if (msg instanceof TextMessage txtMsg) {
                try {
                    String json = txtMsg.getText();
                    JmsMessage jmsMsg = JmsMessage.fromJson(json);
                    handleCommand(txtMsg, jmsMsg);
                } catch (Exception e) {
                    System.err.println("Fehler beim Verarbeiten des Befehls: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        connection.start();
        System.out.println("JMS-Server laeuft. Wartet auf Client-Befehle...");
    }

    /**
     * Registriert einen neuen Client mit seiner session-eigenen Reply-Queue.
     * Wird automatisch beim ersten Befehl eines Clients aufgerufen.
     */
    public void registerClient(String clientId, String sessionId) {
        String replyQueue = (sessionId != null && !sessionId.isBlank())
                ? JmsConfig.REPLY_QUEUE_PREFIX + clientId + "/" + sessionId
                : JmsConfig.replyQueue(clientId);
        clientQueues.put(clientId, replyQueue);
        if (sessionId != null) clientSessions.put(clientId, sessionId);
        System.out.println("Client registriert: " + clientId + " -> " + replyQueue);
    }

    /**
     * Entfernt einen Client.
     */
    public void unregisterClient(String clientId) {
        clientQueues.remove(clientId);
        System.out.println("Client entfernt: " + clientId);
    }

    /**
     * Sendet einen Callback an einen spezifischen Client.
     * Fuegt die aktuelle Server-Vektoruhr und sprechende JMS-Properties ein.
     */
    public void sendCallback(String clientId, JmsMessage callback) throws JMSException {
        String replyQueue = clientQueues.get(clientId);
        if (replyQueue == null) {
            System.err.println("Client nicht gefunden: " + clientId);
            return;
        }
        Destination dest = session.createQueue(replyQueue);
        MessageProducer producer = session.createProducer(dest);

        // Jede Server->Client-Nachricht traegt die aktuelle Server-Uhr.
        JmsMessage msgToSend = JmsMessage.callback(clientId, callback.playerId(), callback.action(),
                callback.args(), callback.payload(), vcManager.getServerClock());

        TextMessage textMessage = session.createTextMessage(msgToSend.toJson());

        String correlationId = UUID.randomUUID().toString();
        textMessage.setJMSCorrelationID(correlationId);
        textMessage.setStringProperty("action", msgToSend.action());
        textMessage.setIntProperty("playerId", msgToSend.playerId());
        textMessage.setStringProperty("clientId", clientId);
        textMessage.setStringProperty("correlationId", correlationId);
        if (msgToSend.vectorClock() != null) {
            textMessage.setStringProperty("vectorClock", msgToSend.vectorClock().toJson());
        }

        producer.send(textMessage);
        producer.close();
    }

    /**
     * Sendet einen Callback an alle verbundenen Clients.
     */
    public void broadcastCallback(JmsMessage callback) throws JMSException {
        for (String clientId : clientQueues.keySet()) {
            sendCallback(clientId, callback);
        }
    }

    /**
     * Erhoeht den Server-Tick fuer ein neues Callback-Ereignis.
     * So traegt jedes Event eine eigene, nachvollziehbare Server-Uhr.
     */
    public void advanceServerClock() {
        vcManager.updateServerClock();
    }

    /**
     * Informiert alle Clients, dass der Server den Zustand nach einer
     * Verzoegerungskompensation korrigiert hat.
     */
    public void broadcastStateRefresh() {
        advanceServerClock();
        for (String clientId : clientQueues.keySet()) {
            try {
                sendCallback(clientId, JmsMessage.callback(clientId, -1, "onStateRefresh", new int[0], null));
            } catch (JMSException e) {
                System.err.println("Fehler beim Senden von onStateRefresh: " + e.getMessage());
            }
        }
        System.out.println("  [COMPENSATION] onStateRefresh an " + clientQueues.size() + " Client(s) gesendet.");
    }

    /**
     * Verarbeitet eine eingehende JMS-Nachricht.
     */
    private void handleCommand(TextMessage txtMsg, JmsMessage msg) throws Exception {
        String clientId = msg.clientId();
        int playerId = msg.playerId();

        String replyQueueName = null;
        Destination replyDestination = null;
        String sessionId = null;
        String correlationId = null;
        try { replyQueueName = txtMsg.getStringProperty("replyQueue"); } catch (JMSException ignored) {}
        try { replyDestination = txtMsg.getJMSReplyTo(); } catch (JMSException ignored) {}
        try { sessionId = txtMsg.getStringProperty("sessionId"); } catch (JMSException ignored) {}
        try { correlationId = txtMsg.getJMSCorrelationID(); } catch (JMSException ignored) {}

        // Client beim ersten Befehl oder bei einer neuen Session registrieren.
        // Eine neue Session (z.B. GUI-Neustart) setzt die erwartete Vektoruhr
        // zurueck, damit der frische Client nicht als "Time Freezing" abgelehnt wird.
        boolean isNewClient = !clientQueues.containsKey(clientId);
        boolean isNewSession = sessionId != null
                && !sessionId.equals(clientSessions.get(clientId));

        if (isNewClient || isNewSession) {
            registerClient(clientId, sessionId);
            // Der Index muss zum Spieler passen (Spieler 0 -> 1, Spieler 1 -> 2),
            // nicht zur Verbindungsreihenfolge.
            int vcIndex = (playerId >= 0 && playerId <= 1)
                    ? playerId + 1
                    : nextVcIndex.getAndIncrement();
            if (isNewClient) {
                vcManager.registerClient(clientId, vcIndex);
            } else {
                vcManager.resetClient(clientId, vcIndex);
            }
        }

        String action = msg.action();

        // getState ist ein Sonderfall: reiner Lesebefehl mit Request/Reply.
        if ("getState".equals(action)) {
            System.out.println();
            System.out.println("  [VC] === GET STATE (Lesebefehl, keine Validierung) ===");
            System.out.println("  [VC] Von: " + clientId + " | Spieler: " + playerId);
            handleGetState(clientId, playerId, replyDestination, replyQueueName, correlationId);
            System.out.println("  [VC] ===========================");
            return;
        }

        // === VEKTORUHR: Vor der Verarbeitung ===
        VectorClock incomingClock = msg.vectorClock();
        System.out.println();
        System.out.println("  [VC] === EINGEHENDE NACHRICHT ===");
        System.out.println("  [VC] Von: " + clientId + " | Aktion: " + action + " | Spieler: " + playerId);
        System.out.println("  [VC] Correlation-ID: " + correlationId);
        if (incomingClock != null) {
            System.out.println("  [VC] Client-Uhr (incoming): " + incomingClock
                    + " [S=" + incomingClock.get(VectorClock.SERVER_INDEX)
                    + ", P1=" + incomingClock.get(VectorClock.PLAYER_1_INDEX)
                    + ", P2=" + incomingClock.get(VectorClock.PLAYER_2_INDEX) + "]");
        } else {
            System.out.println("  [VC] Client-Uhr: keine (alter Client)");
        }
        VectorClock serverBefore = vcManager.getServerClock();
        System.out.println("  [VC] Server-Uhr (vor):       " + serverBefore
                + " [S=" + serverBefore.get(VectorClock.SERVER_INDEX)
                + ", P1=" + serverBefore.get(VectorClock.PLAYER_1_INDEX)
                + ", P2=" + serverBefore.get(VectorClock.PLAYER_2_INDEX) + "]");

        // Correlation-ID-Duplikaterkennung (JMS-Redelivery).
        if (correlationId != null && seenCorrelations.contains(correlationId)) {
            System.out.println("  [DEDUP] Correlation-ID bereits verarbeitet: " + correlationId
                    + " -> Nachricht wird ignoriert.");
            System.out.println("  [VC] ===========================");
            return;
        }

        // === VEKTORUHR: Validierung ===
        VectorClockManager.ValidationResult validation = vcManager.validate(msg);
        if (!validation.isAccepted()) {
            System.out.println("  [VC] ABGELEHNT: " + validation.reason());
            System.out.println("  [VC] ===========================");
            sendError(clientId, "Vektoruhr-Validierung fehlgeschlagen: " + validation.reason());
            return;
        }

        if (validation.isDuplicate()) {
            System.out.println("  [VC] DUPLIKAT: Tick wurde bereits verarbeitet -> ignoriert.");
            System.out.println("  [VC] Luecken: " + vcManager.pendingTicks(clientId)
                    + " | verarbeitet: " + vcManager.processedTicks(clientId));
            System.out.println("  [VC] ===========================");
            return;
        }

        if (correlationId != null) seenCorrelations.add(correlationId);

        // Kausale Beziehung zwischen incoming und Server-Uhr
        VectorClock.CausalRelation relation = incomingClock.compare(serverBefore);
        System.out.println("  [VC] Kausale Beziehung: " + relation);
        if (validation.hasGap()) {
            System.out.println("  [VC] LUECKE erkannt: Tick " + incomingClock.get(vcManager.getClientIndex(clientId))
                    + " kam vor frueheren Nachrichten an -> Luecke " + vcManager.pendingTicks(clientId));
        }
        if (validation.isLate()) {
            System.out.println("  [VC] LATE MESSAGE erkannt: Tick " + incomingClock.get(vcManager.getClientIndex(clientId))
                    + " fuellt eine bekannte Luecke -> REWIND/REPLAY noetig.");
        }

        // Server-Uhr nach Validierung aktualisieren
        VectorClock newServerClock = validation.newServerClock();
        vcManager.setServerClock(newServerClock);
        System.out.println("  [VC] Server-Uhr (nach):       " + newServerClock
                + " [S=" + newServerClock.get(VectorClock.SERVER_INDEX)
                + ", P1=" + newServerClock.get(VectorClock.PLAYER_1_INDEX)
                + ", P2=" + newServerClock.get(VectorClock.PLAYER_2_INDEX) + "]");

        System.out.println("Befehl von " + clientId + ": " + action + " (Spieler " + playerId + ")");

        if (validation.isLate()) {
            boolean compensated = applyLateCommand(msg, correlationId);
            if (compensated) {
                broadcastStateRefresh();
            }
        } else {
            applyNormalCommand(msg, correlationId);
        }

        System.out.println("  [VC] Luecken: " + vcManager.pendingTicks(clientId)
                + " | verarbeitet: " + vcManager.processedTicks(clientId));
        System.out.println("  [VC] ===========================");
    }

    /** Fuehrt einen normalen (nicht verspaeteten) Befehl aus und journalisiert ihn. */
    private boolean applyNormalCommand(JmsMessage msg, String correlationId) {
        synchronized (journal) {
            GameEngine.Snapshot before = engine.snapshot();
            try {
                skeleton.dispatch(msg.action(), msg.playerId(), msg.args());
            } catch (GameRuleException e) {
                sendError(msg.clientId(), e.getMessage());
                return false;
            } catch (Exception e) {
                sendError(msg.clientId(), "Fehler bei der Befehlsausfuehrung: " + e.getMessage());
                return false;
            }
            journal.add(new CommandJournalEntry(msg.clientId(), msg.playerId(), msg.action(),
                    msg.args(), msg.vectorClock(), correlationId, before));
            System.out.println("  [JOURNAL] Eintrag #" + journal.size() + ": " + journal.get(journal.size() - 1));
            return true;
        }
    }

    /**
     * Behandelt eine verspaetete Nachricht:
     *   1. Einfuegeposition im Journal anhand der Vektoruhr bestimmen
     *   2. Snapshot vor dem ersten spaeteren Befehl wiederherstellen
     *   3. Verspaeteten Befehl ausfuehren
     *   4. Alle nachfolgenden Befehle erneut ausfuehren
     *
     * @return true, wenn tatsaechlich zurueckgespult wurde
     */
    private boolean applyLateCommand(JmsMessage msg, String correlationId) {
        synchronized (journal) {
            if (engine.isCombatRunning()) {
                sendError(msg.clientId(),
                        "Verzoegerungskompensation waehrend eines laufenden Kampfes nicht moeglich");
                return false;
            }

            int idx = findRewindIndex(msg);
            if (idx >= journal.size()) {
                System.out.println("  [REWIND] Kein spaeterer Journal-Eintrag -> Befehl wird direkt angewendet.");
                return applyNormalCommand(msg, correlationId);
            }

            GameEngine.Snapshot stateAfterReplay = engine.snapshot();
            List<CommandJournalEntry> prefix = new ArrayList<>(journal.subList(0, idx));
            List<CommandJournalEntry> later = new ArrayList<>(journal.subList(idx, journal.size()));
            GameEngine.Snapshot base = journal.get(idx).snapshotBefore;

            System.out.println("  [REWIND] Spule zurueck auf Zustand vor Journal-Eintrag #" + (idx + 1)
                    + " (" + journal.get(idx) + ").");
            System.out.println("  [REWIND] Snapshot wiederhergestellt; "
                    + later.size() + " spaeterer Befehl(e) werden repliziert.");

            List<CommandJournalEntry> rebuilt = new ArrayList<>(prefix);
            engine.beginReplay();
            try {
                engine.restore(base);

                GameEngine.Snapshot beforeLate = engine.snapshot();
                skeleton.dispatch(msg.action(), msg.playerId(), msg.args());
                rebuilt.add(new CommandJournalEntry(msg.clientId(), msg.playerId(), msg.action(),
                        msg.args(), msg.vectorClock(), correlationId, beforeLate));
                System.out.println("  [REPLAY] Verspaeteter Befehl angewendet: " + msg.action()
                        + " von " + msg.clientId());

                int replayed = 0;
                for (CommandJournalEntry entry : later) {
                    GameEngine.Snapshot before = engine.snapshot();
                    skeleton.dispatch(entry.action, entry.playerId, entry.args);
                    rebuilt.add(entry.withSnapshotBefore(before));
                    replayed++;
                }
                System.out.println("  [REPLAY] " + replayed + " Befehl(e) erneut ausgefuehrt.");
            } catch (Exception e) {
                engine.restore(stateAfterReplay);
                System.out.println("  [REWIND] Fehler beim Replay: " + e.getMessage()
                        + " -> Zustand vor der Kompensation wiederhergestellt.");
                sendError(msg.clientId(), "Verzoegerungskompensation fehlgeschlagen: " + e.getMessage());
                return false;
            } finally {
                engine.endReplay();
            }

            journal.clear();
            journal.addAll(rebuilt);
            System.out.println("  [JOURNAL] Neu aufgebaut: " + journal.size() + " Eintraege.");
            return true;
        }
    }

    /**
     * Sucht den ersten Journal-Eintrag, der kausal NACH der verspaeteten
     * Nachricht liegt. Ab diesem Eintrag muss zurueckgespult werden.
     */
    private int findRewindIndex(JmsMessage lateMsg) {
        VectorClock lateClock = lateMsg.vectorClock();
        Integer lateIndex = vcManager.getClientIndex(lateMsg.clientId());
        int lateOwnTick = (lateClock != null && lateIndex != null) ? lateClock.get(lateIndex) : -1;

        for (int i = 0; i < journal.size(); i++) {
            CommandJournalEntry entry = journal.get(i);
            if (entry.clock == null) continue;

            // Gleicher Client: jede spaetere eigene Tick-Nummer liegt nach der Nachricht.
            if (entry.clientId.equals(lateMsg.clientId()) && lateIndex != null
                    && entry.clock.get(lateIndex) > lateOwnTick) {
                return i;
            }
            // Allgemein: Eintrag geschieht kausal nach der verspaeteten Nachricht.
            if (lateClock != null
                    && entry.clock.compare(lateClock) == VectorClock.CausalRelation.AFTER) {
                return i;
            }
        }
        return journal.size();
    }

    /** Sendet einen Fehler-Callback mit aktueller Server-Uhr. */
    private void sendError(String clientId, String message) {
        try {
            advanceServerClock();
            sendCallback(clientId, JmsMessage.callback(clientId, -1, "error", new int[0], message));
        } catch (JMSException e) {
            System.err.println("Fehler beim Senden des Fehler-Callbacks: " + e.getMessage());
        }
    }

    /**
     * Beantwortet einen getState-Request ueber JMSReplyTo + JMSCorrelationID.
     */
    private void handleGetState(String clientId, int playerId, Destination replyDestination,
                                String replyQueueName, String correlationId) throws Exception {
        Destination dest = replyDestination;
        if (dest == null && replyQueueName != null && !replyQueueName.isBlank()) {
            dest = session.createQueue(replyQueueName);
        }
        if (dest == null) {
            System.err.println("getState ohne JMSReplyTo/Reply-Queue von " + clientId);
            return;
        }

        MessageProducer producer = session.createProducer(dest);
        try {
            GameStateDto state = engine.getState(playerId);
            String json = JsonMapper.write(JsonMapper.toJson(state), false);
            JmsMessage reply = new JmsMessage(clientId, "getStateReply", json, vcManager.getServerClock());

            TextMessage textMessage = session.createTextMessage(reply.toJson());
            String replyCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
            textMessage.setJMSCorrelationID(replyCorrelationId);
            textMessage.setStringProperty("action", "getStateReply");
            textMessage.setIntProperty("playerId", playerId);
            textMessage.setStringProperty("clientId", clientId);
            textMessage.setStringProperty("correlationId", replyCorrelationId);
            textMessage.setStringProperty("vectorClock", vcManager.getServerClock().toJson());
            producer.send(textMessage);
        } catch (Exception e) {
            JmsMessage errorReply = new JmsMessage(clientId, "error", e.getMessage(), vcManager.getServerClock());
            TextMessage textMessage = session.createTextMessage(errorReply.toJson());
            if (correlationId != null) textMessage.setJMSCorrelationID(correlationId);
            textMessage.setStringProperty("action", "error");
            textMessage.setStringProperty("clientId", clientId);
            producer.send(textMessage);
        } finally {
            producer.close();
        }
    }

    /**
     * Schließt den Server.
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                if (connection != null) connection.close();
                System.out.println("JMS-Server gestoppt.");
            } catch (JMSException e) {
                System.err.println("Fehler beim Stoppen: " + e.getMessage());
            }
        }
    }

    // ---------- Innerer GameListener: leitet Callbacks an Clients weiter ----------

    /**
     * Implementiert GameListener und sendet alle Callbacks als JMS-Nachrichten
     * an die Reply-Queues der verbundenen Clients.
     */
    private static class JmsGameListener implements GameListener {

        private final JmsServer server;

        JmsGameListener(JmsServer server) {
            this.server = server;
        }

        @Override
        public void onRoundStarted(int round) {
            server.advanceServerClock();
            try {
                for (String clientId : server.clientQueues.keySet()) {
                    server.sendCallback(clientId,
                            JmsMessage.callback(clientId, 0, "onRoundStarted", new int[]{round}, null));
                }
            } catch (JMSException e) {
                System.err.println("Fehler beim Senden von onRoundStarted: " + e.getMessage());
            }
        }

        @Override
        public void onCombatFrames(int round, List<CombatFrameDto> frames) {
            server.advanceServerClock();
            try {
                // Frames als JSON serialisieren (jakarta.json)
                String json = combatFramesToJson(frames);
                for (String clientId : server.clientQueues.keySet()) {
                    server.sendCallback(clientId,
                            JmsMessage.callback(clientId, round, "onCombatFrames", new int[]{round}, json));
                }
            } catch (JMSException e) {
                System.err.println("Fehler beim Senden von onCombatFrames: " + e.getMessage());
            }
        }

        @Override
        public void onUnitCombined(int playerId, String unitName, int star) {
            server.advanceServerClock();
            try {
                String payload = unitName + "," + star;
                for (String clientId : server.clientQueues.keySet()) {
                    server.sendCallback(clientId,
                            JmsMessage.callback(clientId, playerId, "onUnitCombined",
                                    new int[]{playerId}, payload));
                }
            } catch (JMSException e) {
                System.err.println("Fehler beim Senden von onUnitCombined: " + e.getMessage());
            }
        }

        @Override
        public void onCombatFinished(CombatResultDto result) {
            server.advanceServerClock();
            try {
                // CombatResultDto als JSON serialisieren (javax.json -> String)
                String json = JsonMapper.write(JsonMapper.toJson(result), false);
                for (String clientId : server.clientQueues.keySet()) {
                    server.sendCallback(clientId,
                            new JmsMessage(clientId, "onCombatFinished", json));
                }
            } catch (JMSException e) {
                System.err.println("Fehler beim Senden von onCombatFinished: " + e.getMessage());
            }
        }

        @Override
        public void onTurnChanged(int turn) {
            server.advanceServerClock();
            try {
                for (String clientId : server.clientQueues.keySet()) {
                    server.sendCallback(clientId,
                            JmsMessage.callback(clientId, 0, "onTurnChanged", new int[]{turn}, null));
                }
            } catch (JMSException e) {
                System.err.println("Fehler beim Senden von onTurnChanged: " + e.getMessage());
            }
        }

        @Override
        public void onGameOver(String winnerName) {
            server.advanceServerClock();
            try {
                for (String clientId : server.clientQueues.keySet()) {
                    server.sendCallback(clientId,
                            new JmsMessage(clientId, "onGameOver", winnerName));
                }
            } catch (JMSException e) {
                System.err.println("Fehler beim Senden von onGameOver: " + e.getMessage());
            }
        }

        /**
         * Serialisiert CombatFrameDto-Liste als JSON-Array (jakarta.json).
         */
        private String combatFramesToJson(List<CombatFrameDto> frames) {
            javax.json.JsonArrayBuilder arrayBuilder = javax.json.Json.createArrayBuilder();
            for (CombatFrameDto frame : frames) {
                javax.json.JsonObjectBuilder frameBuilder = javax.json.Json.createObjectBuilder();
                frameBuilder.add("tick", frame.tick());

                javax.json.JsonArrayBuilder unitsBuilder = javax.json.Json.createArrayBuilder();
                for (UnitStateDto u : frame.units()) {
                    unitsBuilder.add(javax.json.Json.createObjectBuilder()
                            .add("team", u.team())
                            .add("row", u.row())
                            .add("col", u.col())
                            .add("name", u.name())
                            .add("star", u.star())
                            .add("hp", u.hp())
                            .add("maxHp", u.maxHp())
                            .add("alive", u.alive()));
                }
                frameBuilder.add("units", unitsBuilder);

                javax.json.JsonArrayBuilder eventsBuilder = javax.json.Json.createArrayBuilder();
                for (CombatEventDto e : frame.events()) {
                    eventsBuilder.add(javax.json.Json.createObjectBuilder()
                            .add("team", e.team())
                            .add("fromRow", e.fromRow())
                            .add("fromCol", e.fromCol())
                            .add("toRow", e.toRow())
                            .add("toCol", e.toCol())
                            .add("damage", e.damage())
                            .add("text", e.text()));
                }
                frameBuilder.add("events", eventsBuilder);

                arrayBuilder.add(frameBuilder);
            }
            return arrayBuilder.build().toString();
        }
    }
}
