import jakarta.jms.*;

import javax.swing.SwingUtilities;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMS-Client fuer Mini-TFT.
 *
 * Ersetzt RmiClient: statt RMI-Stub wird ein JMS-Producer verwendet, um
 * Befehle asynchron an den Server zu senden. Callbacks werden ueber eine
 * eigene Reply-Queue empfangen.
 *
 * Architektur:
 *   Client --(JmsMessage/COMMAND)--> Queue: mini-tft/commands
 *   Client <--(JmsMessage/CALLBACK)-- Queue: mini-tft/reply/<clientId>
 *
 * Verwendung:
 *   JmsClient client = new JmsClient();
 *   client.connect();
 *   client.buy(0, 1);
 *   client.registerListener(myListener);
 *   // ...
 *   client.close();
 */
public class JmsClient {

    private final String clientId;
    private Connection connection;
    private Session session;
    private MessageProducer commandProducer;
    private MessageConsumer callbackConsumer;
    private GameListener listener;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * ID dieser Client-Session. Jeder Programmstart bekommt eine neue Session,
     * damit der Server die Vektoruhr nach einem Reconnect zuruecksetzen kann
     * und keine alten Callbacks aus einer frueheren Session zugestellt werden.
     */
    private final String sessionId = UUID.randomUUID().toString();

    /** Name der session-eigenen Reply-Queue (verhindert das Replay alter Nachrichten). */
    private String sessionReplyQueue() {
        return JmsConfig.REPLY_QUEUE_PREFIX + clientId + "/" + sessionId;
    }

    /**
     * Lokale Vektoruhr des Clients: [Server_Tick, P1_Tick, P2_Tick].
     * Wird bei jedem gesendeten Befehl inkrementiert und bei empfangenen
     * Callbacks mit der Server-Uhr verschmolzen.
     */
    private final VectorClock localClock = new VectorClock();

    /**
     * Der Index dieses Clients in der Vektoruhr.
     * Wird vom Server beim ersten Befehl zugewiesen (1 oder 2).
     * -1 bedeutet: noch nicht bekannt (wird beim ersten Callback ermittelt).
     */
    private volatile int myVcIndex = -1;

    /**
     * Erstellt einen Client mit einer zufaelligen ID.
     */
    public JmsClient() {
        this.clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Erstellt einen Client mit einer expliziten ID und einem Vektoruhr-Index.
     * Der Index bestimmt, welche Position in der Vektoruhr diesem Client zugeordnet ist.
     *
     * @param clientId  die eindeutige Client-ID
     * @param vcIndex   der Index in der Vektoruhr (1 fuer Spieler 0, 2 fuer Spieler 1)
     */
    public JmsClient(String clientId, int vcIndex) {
        this.clientId = clientId;
        this.myVcIndex = vcIndex;
    }

    /**
     * Erstellt einen Client mit einer expliziten ID.
     * Der Vektoruhr-Index wird automatisch beim ersten Callback ermittelt.
     */
    public JmsClient(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Verbindet sich zum JMS-Broker und richtet Producer/Consumer ein.
     */
    public void connect() throws JMSException {
        String brokerUrl = JmsConfig.brokerUrl();
        System.out.println("JMS-Client verbindet zu: " + brokerUrl);

        ConnectionFactory factory = new org.apache.activemq.ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Producer fuer Befehle (Client -> Server)
        Destination commandQueue = session.createQueue(JmsConfig.COMMAND_QUEUE);
        commandProducer = session.createProducer(commandQueue);

        // Consumer fuer Callbacks (Server -> Client, session-eigene Reply-Queue)
        Destination replyQueue = session.createQueue(sessionReplyQueue());
        callbackConsumer = session.createConsumer(replyQueue);

        // Callback-Handler: JmsMessage -> GameListener
        callbackConsumer.setMessageListener(msg -> {
            if (msg instanceof TextMessage txtMsg) {
                try {
                    String json = txtMsg.getText();
                    JmsMessage jmsMsg = JmsMessage.fromJson(json);

                    // VEKTORUHR: Bei empfangenen Callbacks die lokale Uhr mit der Server-Uhr merge-n
                    VectorClock serverClock = jmsMsg.vectorClock();
                    if (serverClock != null) {
                        synchronized (localClock) {
                            VectorClock before = localClock.clone();
                            localClock.getInternalTicks(); // ensure access
                            // Elementweises Maximum (merge)
                            for (int i = 0; i < VectorClock.SIZE; i++) {
                                int serverVal = serverClock.get(i);
                                int localVal = localClock.get(i);
                                if (serverVal > localVal) {
                                    // Lokale Uhr auf Server-Wert setzen
                                    localClock.getInternalTicks()[i] = serverVal;
                                }
                            }
                            System.out.println("  [VC-Client] Callback empfangen, Uhr merged: " + before
                                    + " -> " + localClock);
                        }
                    }

                    dispatchCallback(jmsMsg);
                } catch (Exception e) {
                    System.err.println("Fehler beim Verarbeiten der Callback-Nachricht: " + e.getMessage());
                }
            }
        });

        connection.start();
        System.out.println("JMS-Client verbunden (ID: " + clientId + ")");
        System.out.println("  Befehle -> " + JmsConfig.COMMAND_QUEUE);
        System.out.println("  Callbacks <- " + sessionReplyQueue());
    }

    /**
     * Registriert einen GameListener, der die Callbacks empfängt.
     */
    public void registerListener(GameListener gameListener) {
        this.listener = gameListener;
        System.out.println("GameListener fuer JMS-Client registriert.");
    }

    /**
     * Liefert die Client-ID (wird an den Server mitgesendet).
     */
    public String getClientId() {
        return clientId;
    }

    // ---------- Spielbefehle (Client -> Server) ----------

    private void sendCommand(String action, int playerId, int... args) throws JMSException {
        sendCommandInternal(action, playerId, 0L, args);
    }

    /**
     * Sendet einen Befehl mit Verzoegerung ueber den ActiveMQ-Scheduler.
     * Die Vektoruhr wird beim Senden eingefroren; genau dadurch kommt die
     * Nachricht spaeter mit einem "alten" Tick an und loest die
     * serverseitige Verzoegerungskompensation aus.
     */
    public void sendCommandDelayed(String action, long delayMs, int playerId, int... args) throws JMSException {
        if (delayMs <= 0) {
            sendCommandInternal(action, playerId, 0L, args);
        } else {
            sendCommandInternal(action, playerId, delayMs, args);
        }
    }

    /** Kauft eine Einheit verzoegert (Demo der Delayed Delivery). */
    public void buyDelayed(int playerId, int slot, long delayMs) throws JMSException {
        sendCommandDelayed("buy", delayMs, playerId, slot);
    }

    private void sendCommandInternal(String action, int playerId, long delayMs, int... args) throws JMSException {
        if (closed.get()) throw new java.lang.IllegalStateException("Client ist geschlossen");

        // VEKTORUHR: Eigene Uhr inkrementieren und in Nachricht einbetten
        VectorClock clockToSend;
        synchronized (localClock) {
            if (myVcIndex >= 0) {
                localClock.increment(myVcIndex);
            }
            clockToSend = localClock.clone();
        }

        System.out.println("  [VC-Client] Befehl senden: " + action
                + (delayMs > 0 ? " (verzoegert um " + delayMs + " ms)" : "")
                + " | Uhr: " + clockToSend);

        JmsMessage msg = new JmsMessage(clientId, playerId, action, clockToSend, args);
        TextMessage textMessage = session.createTextMessage(msg.toJson());
        attachCommandProperties(textMessage, action, playerId, clockToSend, delayMs);
        commandProducer.send(textMessage);
    }

    public void buy(int playerId, int slot) throws JMSException {
        sendCommand("buy", playerId, slot);
    }

    /**
     * Setzt aussagekraeftige JMS-Properties, damit die ActiveMQ-Web-Konsole
     * Aktion, Spieler, Vektoruhr und Correlation-ID direkt anzeigen kann.
     */
    private void attachCommandProperties(TextMessage textMessage, String action, int playerId,
                                         VectorClock clock, long delayMs) throws JMSException {
        String correlationId = UUID.randomUUID().toString();
        textMessage.setJMSCorrelationID(correlationId);
        textMessage.setStringProperty("action", action);
        textMessage.setIntProperty("playerId", playerId);
        textMessage.setStringProperty("clientId", clientId);
        textMessage.setStringProperty("sessionId", sessionId);
        textMessage.setStringProperty("correlationId", correlationId);
        if (clock != null) textMessage.setStringProperty("vectorClock", clock.toJson());
        if (delayMs > 0) {
            // ActiveMQ-Scheduler: Nachricht erst nach delayMs zustellen.
            textMessage.setLongProperty("AMQ_SCHEDULED_DELAY", delayMs);
        }
    }

    public void sell(int playerId, int index) throws JMSException {
        sendCommand("sell", playerId, index);
    }

    public void sellBench(int playerId, int slot) throws JMSException {
        sendCommand("sellbench", playerId, slot);
    }

    public void move(int playerId, int index, int row, int col) throws JMSException {
        sendCommand("move", playerId, index, row, col);
    }

    public void toField(int playerId, int benchSlot, int row, int col) throws JMSException {
        sendCommand("tofield", playerId, benchSlot, row, col);
    }

    public void toBench(int playerId, int index) throws JMSException {
        sendCommand("tobench", playerId, index);
    }

    public void levelUp(int playerId) throws JMSException {
        sendCommand("levelup", playerId);
    }

    public void reroll(int playerId) throws JMSException {
        sendCommand("reroll", playerId);
    }

    public void ready(int playerId) throws JMSException {
        sendCommand("ready", playerId);
    }

    /**
     * Fragt den aktuellen Spielstand vom Server ab (synchrones Request/Reply).
     *
     * Sendet einen "getState"-Befehl und wartet auf die Antwort via einer
     * temporären Reply-Queue. Blockiert bis die Antwort eintrifft oder ein
     * Timeout (5 Sekunden) erreicht wird.
     *
     * @return der aktuelle GameStateDto
     * @throws JMSException bei Verbindungsfehlern
     * @throws java.lang.IllegalStateException wenn kein Antwort innerhalb des Timeouts eintrifft
     */
    public GameStateDto getState(int playerId) throws JMSException, InterruptedException, java.lang.IllegalStateException {
        if (closed.get()) throw new java.lang.IllegalStateException("Client ist geschlossen");

        // Request/Reply: temporaere Queue + Correlation-ID (JMS-Standardmuster).
        TemporaryQueue tempQueue = session.createTemporaryQueue();
        String correlationId = UUID.randomUUID().toString();

        // Consumer filtert per Correlation-ID-Selektor auf genau diese Antwort.
        MessageConsumer tempConsumer = session.createConsumer(tempQueue,
                "JMSCorrelationID = '" + correlationId + "'");

        // Antwort-Container
        final Object[] result = new Object[1];
        final Exception[] error = new Exception[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        // Listener fuer die Antwort
        tempConsumer.setMessageListener(msg -> {
            try {
                if (msg instanceof TextMessage txtMsg) {
                    String json = txtMsg.getText();
                    JmsMessage jmsMsg = JmsMessage.fromJson(json);
                    if ("getStateReply".equals(jmsMsg.action()) && jmsMsg.payload() != null) {
                        result[0] = JsonMapper.toGameState(jmsMsg.payload());
                    } else if ("error".equals(jmsMsg.action())) {
                        error[0] = new RuntimeException(jmsMsg.payload());
                    }
                    latch.countDown();
                }
            } catch (Exception e) {
                error[0] = e;
                latch.countDown();
            }
        });

        // VEKTORUHR: getState sendet die aktuelle lokale Uhr (ohne Inkrementierung — nur Lesebefehl)
        VectorClock clockToSend;
        synchronized (localClock) {
            clockToSend = localClock.clone();
        }

        // Request mit Vektoruhr, JMSReplyTo und JMSCorrelationID senden
        JmsMessage msg = new JmsMessage(clientId, playerId, "getState", clockToSend, new int[]{playerId});
        TextMessage textMessage = session.createTextMessage(msg.toJson());
        textMessage.setJMSReplyTo(tempQueue);
        textMessage.setJMSCorrelationID(correlationId);
        textMessage.setStringProperty("action", "getState");
        textMessage.setIntProperty("playerId", playerId);
        textMessage.setStringProperty("clientId", clientId);
        textMessage.setStringProperty("sessionId", sessionId);
        textMessage.setStringProperty("correlationId", correlationId);
        if (clockToSend != null) textMessage.setStringProperty("vectorClock", clockToSend.toJson());
        commandProducer.send(textMessage);

        // Auf Antwort warten (Timeout: 5 Sekunden)
        boolean received = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        tempConsumer.close();
        tempQueue.delete();

        if (!received) {
            throw new java.lang.IllegalStateException("Keine Antwort vom Server erhalten (Timeout nach 5s)");
        }
        if (error[0] != null) {
            throw new RuntimeException("Fehler vom Server: " + error[0].getMessage(), error[0]);
        }
        if (result[0] == null) {
            throw new java.lang.IllegalStateException("Ungültige Antwort vom Server erhalten");
        }
        return (GameStateDto) result[0];
    }

    /**
     * Schließt die JMS-Verbindung und räumt alle Ressourcen auf.
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                if (connection != null) connection.close();
                System.out.println("JMS-Client getrennt.");
            } catch (JMSException e) {
                System.err.println("Fehler beim Trennen: " + e.getMessage());
            }
        }
    }

    // ---------- Callback-Dispatch (Server -> Client) ----------

    private void dispatchCallback(JmsMessage msg) {
        if (listener == null) return;

        try {
            switch (msg.action()) {
                case "onRoundStarted" -> {
                    int round = msg.arg(0);
                    SwingUtilities.invokeLater(() -> {
                        try { listener.onRoundStarted(round); }
                        catch (Exception e) { System.err.println("Callback onRoundStarted: " + e.getMessage()); }
                    });
                }
                case "onTurnChanged" -> {
                    int turn = msg.arg(0);
                    SwingUtilities.invokeLater(() -> {
                        try { listener.onTurnChanged(turn); }
                        catch (Exception e) { System.err.println("Callback onTurnChanged: " + e.getMessage()); }
                    });
                }
                case "onUnitCombined" -> {
                    int pid = msg.arg(0);
                    final String name;
                    final int star;
                    // payload format: "name,star"
                    if (msg.payload() != null) {
                        String[] parts = msg.payload().split(",");
                        name = parts[0];
                        star = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    } else {
                        name = "";
                        star = 0;
                    }
                    SwingUtilities.invokeLater(() -> {
                        try { listener.onUnitCombined(pid, name, star); }
                        catch (Exception e) { System.err.println("Callback onUnitCombined: " + e.getMessage()); }
                    });
                }
                case "onCombatFrames" -> {
                    // Payload ist JSON-Array von CombatFrameDto
                    if (msg.payload() != null) {
                        java.util.List<CombatFrameDto> frames = parseCombatFrames(msg.payload());
                        final java.util.List<CombatFrameDto> f = frames;
                        SwingUtilities.invokeLater(() -> {
                            try { listener.onCombatFrames(msg.arg(0), f); }
                            catch (Exception e) { System.err.println("Callback onCombatFrames: " + e.getMessage()); }
                        });
                    }
                }
                case "onCombatFinished" -> {
                    if (msg.payload() != null) {
                        CombatResultDto result = JsonMapper.toCombatResult(msg.payload());
                        SwingUtilities.invokeLater(() -> {
                            try { listener.onCombatFinished(result); }
                            catch (Exception e) { System.err.println("Callback onCombatFinished: " + e.getMessage()); }
                        });
                    }
                }
                case "onGameOver" -> {
                    String winner = msg.payload();
                    SwingUtilities.invokeLater(() -> {
                        try { listener.onGameOver(winner); }
                        catch (Exception e) { System.err.println("Callback onGameOver: " + e.getMessage()); }
                    });
                }
                case "onStateRefresh" -> {
                    SwingUtilities.invokeLater(() -> {
                        try { listener.onStateRefresh(); }
                        catch (Exception e) { System.err.println("Callback onStateRefresh: " + e.getMessage()); }
                    });
                }
                case "error" -> {
                    String errorMessage = msg.payload();
                    // Wurde der Befehl wegen der Vektoruhr abgelehnt, die eigene Uhr
                    // auf den Serverstand ziehen. Sonst bleibt der Client dauerhaft
                    // blockiert, weil jeder weitere Befehl erneut abgelehnt wird.
                    if (errorMessage != null && errorMessage.contains("Vektoruhr")
                            && msg.vectorClock() != null && myVcIndex >= 0) {
                        synchronized (localClock) {
                            int serverTick = msg.vectorClock().get(myVcIndex);
                            localClock.getInternalTicks()[myVcIndex] = serverTick;
                            System.out.println("  [VC-Client] Resync nach Ablehnung: eigener Tick -> " + serverTick);
                        }
                    }
                    SwingUtilities.invokeLater(() -> {
                        try {
                            javax.swing.JOptionPane.showMessageDialog(
                                null, errorMessage, "Fehler", javax.swing.JOptionPane.WARNING_MESSAGE);
                        } catch (Exception e) {
                            System.err.println("Fehler beim Anzeigen der Fehlermeldung: " + e.getMessage());
                        }
                    });
                }
                default -> System.err.println("Unbekannter Callback: " + msg.action());
            }
        } catch (Exception e) {
            System.err.println("Fehler im Callback-Dispatch: " + e.getMessage());
        }
    }

    /**
     * Parst ein JSON-Array von CombatFrameDto aus der Payload.
     */
    private java.util.List<CombatFrameDto> parseCombatFrames(String json) {
        java.util.List<CombatFrameDto> frames = new java.util.ArrayList<>();
        try (javax.json.JsonReader reader = javax.json.Json.createReader(new java.io.StringReader(json))) {
            javax.json.JsonArray array = reader.readArray();
            for (int i = 0; i < array.size(); i++) {
                javax.json.JsonObject obj = array.getJsonObject(i);
                int tick = obj.getInt("tick");
                java.util.List<UnitStateDto> units = new java.util.ArrayList<>();
                java.util.List<CombatEventDto> events = new java.util.ArrayList<>();
                for (javax.json.JsonValue uv : obj.getJsonArray("units")) {
                    javax.json.JsonObject u = (javax.json.JsonObject) uv;
                    units.add(new UnitStateDto(
                            u.getInt("team"), u.getInt("row"), u.getInt("col"), u.getString("name"),
                            u.getInt("star"), u.getInt("hp"), u.getInt("maxHp"), u.getBoolean("alive")));
                }
                for (javax.json.JsonValue ev : obj.getJsonArray("events")) {
                    javax.json.JsonObject e = (javax.json.JsonObject) ev;
                    events.add(new CombatEventDto(
                            e.getInt("team"), e.getInt("fromRow"), e.getInt("fromCol"),
                            e.getInt("toRow"), e.getInt("toCol"), e.getInt("damage"), e.getString("text")));
                }
                frames.add(new CombatFrameDto(tick, units, events));
            }
        }
        return frames;
    }
}
