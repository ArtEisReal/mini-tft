import jakarta.jms.*;

import java.util.List;
import java.util.Map;
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
 *   Server --(JmsMessage/CALLBACK)--> Queue: mini-tft/reply/<clientId>
 *
 * Der Server verwaltet eine GameEngine und mehrere verbundene Clients.
 * Jeder Client hat eine eigene Reply-Queue fuer Callbacks.
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

    private GameEngine engine;
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
            System.out.println("JMS-Server waartet auf Nachrichten...");
            System.out.println("Web-Konsole: http://localhost:8161/admin/");
            System.out.println("  Queues: " + JmsConfig.COMMAND_QUEUE + ", " + JmsConfig.CALLBACK_QUEUE);
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
        // GameEngine erstellen (lokal, nicht RMI)
        engine = new GameEngine(name0, name1);
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
                    // Reply-Queue aus JMS-Property extrahieren (fuer getState-Requests)
                    String replyQueue = null;
                    String sessionId = null;
                    try {
                        replyQueue = txtMsg.getStringProperty("replyQueue");
                    } catch (JMSException e) {
                        // keine replyQueue Property
                    }
                    try {
                        sessionId = txtMsg.getStringProperty("sessionId");
                    } catch (JMSException e) {
                        // keine sessionId Property (alter Client)
                    }
                    handleCommand(jmsMsg, replyQueue, sessionId);
                } catch (Exception e) {
                    System.err.println("Fehler beim Verarbeiten des Befehls: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        connection.start();
        System.out.println("JMS-Server laeuft. Waartet auf Client-Befehle...");
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
     * Fuegt die aktuelle Server-Vektoruhr in die Nachricht ein.
     */
    public void sendCallback(String clientId, JmsMessage callback) throws JMSException {
        String replyQueue = clientQueues.get(clientId);
        if (replyQueue == null) {
            System.err.println("Client nicht gefunden: " + clientId);
            return;
        }
        Destination dest = session.createQueue(replyQueue);
        MessageProducer producer = session.createProducer(dest);

        // Wenn der Callback noch keine Vektoruhr hat, die Server-Uhr eintragen.
        // Dabei muessen playerId und args erhalten bleiben (sonst schlagen
        // Callbacks mit Argumenten wie onRoundStarted/onTurnChanged fehl).
        JmsMessage msgToSend;
        if (callback.vectorClock() == null) {
            VectorClock serverClock = vcManager.getServerClock();
            msgToSend = JmsMessage.callback(clientId, callback.playerId(), callback.action(),
                    callback.args(), callback.payload(), serverClock);
        } else {
            msgToSend = callback;
        }

        TextMessage textMessage = session.createTextMessage(msgToSend.toJson());
        producer.send(textMessage);
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
     * Verarbeitet einen eingehenden Befehl.
     * Integriert Vektoruhr-Validierung und Logging.
     *
     * @param msg die eingehende JmsMessage
     * @param replyQueueName die Reply-Queue aus JMS-Property (fuer getState-Requests), sonst null
     */
    private void handleCommand(JmsMessage msg, String replyQueueName, String sessionId) throws Exception {
        String clientId = msg.clientId();
        int playerId = msg.playerId();

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

        // getState ist ein Sonderfall: nur Lesebefehl, keine Vektoruhr-Validierung
        if ("getState".equals(action)) {
            System.out.println();
            System.out.println("  [VC] === GET STATE (Lesebefehl, keine Validierung) ===");
            System.out.println("  [VC] Von: " + clientId + " | Spieler: " + playerId);
            handleGetState(clientId, playerId, replyQueueName);
            System.out.println("  [VC] ===========================");
            return;
        }

        // === VEKTORUHR: Vor der Verarbeitung ===
        VectorClock incomingClock = msg.vectorClock();
        System.out.println();
        System.out.println("  [VC] === EINGEHENDE NACHRICHT ===");
        System.out.println("  [VC] Von: " + clientId + " | Aktion: " + action + " | Spieler: " + playerId);
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

        // === VEKTORUHR: Validierung ===
        VectorClockManager.ValidationResult validation = vcManager.validate(msg);
        if (!validation.isAccepted()) {
            System.out.println("  [VC] ABGELEHNT: " + validation.reason());
            System.out.println("  [VC] ===========================");
            // Abgelehnte Nachricht an Client zurueckmelden
            JmsMessage errorCb = new JmsMessage(clientId, "error",
                    "Vektoruhr-Validierung fehlgeschlagen: " + validation.reason(),
                    vcManager.getServerClock());
            sendCallback(clientId, errorCb);
            return;
        }

        // Kausale Beziehung zwischen incoming und Server-Uhr
        VectorClock.CausalRelation relation = incomingClock.compare(serverBefore);
        System.out.println("  [VC] Kausale Beziehung: " + relation);
        if (relation == VectorClock.CausalRelation.CONCURRENT) {
            System.out.println("  [VC] -> Konkurrente Ereignisse (moeegliche Verzögerung erkannt)");
        }

        // Server-Uhr nach Validierung aktualisieren
        VectorClock newServerClock = validation.newServerClock();
        vcManager.setServerClock(newServerClock);
        System.out.println("  [VC] Server-Uhr (nach):       " + newServerClock
                + " [S=" + newServerClock.get(VectorClock.SERVER_INDEX)
                + ", P1=" + newServerClock.get(VectorClock.PLAYER_1_INDEX)
                + ", P2=" + newServerClock.get(VectorClock.PLAYER_2_INDEX) + "]");

        System.out.println("Befehl von " + clientId + ": " + action + " (Spieler " + playerId + ")");

        try {
            switch (action) {
                case "buy" -> engine.buy(playerId, msg.arg(0));
                case "sell" -> engine.sell(playerId, msg.arg(0));
                case "sellbench" -> engine.sellBench(playerId, msg.arg(0));
                case "tofield" -> engine.toField(playerId, msg.arg(0), msg.arg(1), msg.arg(2));
                case "tobench" -> engine.toBench(playerId, msg.arg(0));
                case "move" -> engine.move(playerId, msg.arg(0), msg.arg(1), msg.arg(2));
                case "levelup" -> engine.levelUp(playerId);
                case "reroll" -> engine.reroll(playerId);
                case "ready" -> engine.ready(playerId);
                default -> System.err.println("Unbekannter Befehl: " + action);
            }
            System.out.println("  [VC] Befehl erfolgreich verarbeitet.");
        } catch (GameRuleException e) {
            // Regelverletzung (z. B. "nicht dran") als Callback an den Client zuruecksenden
            JmsMessage errorCallback = new JmsMessage(clientId, "error", e.getMessage(),
                    vcManager.getServerClock());
            sendCallback(clientId, errorCallback);
        }

        System.out.println("  [VC] ===========================");
    }

    /**
     * Beantwortet einen getState-Request: sendet den aktuellen Spielstand
     * an die temporäre Reply-Queue des anfragenden Clients.
     */
    private void handleGetState(String clientId, int playerId, String replyQueueName) throws Exception {
        if (replyQueueName == null || replyQueueName.isBlank()) {
            System.err.println("getState ohne Reply-Queue von " + clientId);
            return;
        }
        try {
            GameStateDto state = engine.getState(playerId);
            String json = JsonMapper.write(JsonMapper.toJson(state), false);
            Destination replyQueue = session.createQueue(replyQueueName);
            MessageProducer producer = session.createProducer(replyQueue);
            JmsMessage reply = new JmsMessage(clientId, "getStateReply", json);
            TextMessage textMessage = session.createTextMessage(reply.toJson());
            producer.send(textMessage);
        } catch (Exception e) {
            // Fehler als Antwort senden
            Destination replyQueue = session.createQueue(replyQueueName);
            MessageProducer producer = session.createProducer(replyQueue);
            JmsMessage errorReply = new JmsMessage(clientId, "error", e.getMessage());
            TextMessage textMessage = session.createTextMessage(errorReply.toJson());
            producer.send(textMessage);
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
