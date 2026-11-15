import jakarta.jms.*;

import javax.swing.SwingUtilities;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * Erstellt einen Client mit einer zufaelligen ID.
     */
    public JmsClient() {
        this.clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Erstellt einen Client mit einer expliziten ID.
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

        // Consumer fuer Callbacks (Server -> Client, eigene Reply-Queue)
        Destination replyQueue = session.createQueue(JmsConfig.replyQueue(clientId));
        callbackConsumer = session.createConsumer(replyQueue);

        // Callback-Handler: JmsMessage -> GameListener
        callbackConsumer.setMessageListener(msg -> {
            if (msg instanceof TextMessage txtMsg) {
                try {
                    String json = txtMsg.getText();
                    JmsMessage jmsMsg = JmsMessage.fromJson(json);
                    dispatchCallback(jmsMsg);
                } catch (Exception e) {
                    System.err.println("Fehler beim Verarbeiten der Callback-Nachricht: " + e.getMessage());
                }
            }
        });

        connection.start();
        System.out.println("JMS-Client verbunden (ID: " + clientId + ")");
        System.out.println("  Befehle -> " + JmsConfig.COMMAND_QUEUE);
        System.out.println("  Callbacks <- " + JmsConfig.replyQueue(clientId));
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
        if (closed.get()) throw new java.lang.IllegalStateException("Client ist geschlossen");
        JmsMessage msg = new JmsMessage(clientId, playerId, action, args);
        TextMessage textMessage = session.createTextMessage(msg.toJson());
        commandProducer.send(textMessage);
    }

    public void buy(int playerId, int slot) throws JMSException {
        sendCommand("buy", playerId, slot);
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

        // Temporäre Queue fuer die Antwort erstellen
        TemporaryQueue tempQueue = session.createTemporaryQueue();
        MessageConsumer tempConsumer = session.createConsumer(tempQueue);

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

        // Request senden
        JmsMessage msg = JmsMessage.commandWithReply(clientId, playerId, "getState", new int[]{playerId}, tempQueue.getQueueName());
        TextMessage textMessage = session.createTextMessage(msg.toJson());
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
                case "error" -> {
                    String errorMessage = msg.payload();
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
