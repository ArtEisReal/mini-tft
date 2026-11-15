import java.util.List;

/**
 * Universeller Nachrichten-Wrapper fuer JMS-Kommunikation.
 *
 * Eine JmsMessage transportiert entweder einen Befehl (Client -> Server)
 * oder einen Callback (Server -> Client). Das Feld {@code type} bestimmt die Richtung.
 *
 * Typen:
 *   COMMAND   - Client sendet einen Spielbefehl an den Server
 *   CALLBACK  - Server sendet eine Rueckmeldung an den Client
 *
 * Die Nachricht wird als JSON-Text (JMS TextMessage) uebertragen.
 */
public class JmsMessage {

    /** Nachrichtentyp: Befehl oder Callback. */
    public enum Type {
        COMMAND,
        CALLBACK
    }

    /** Typ der Nachricht. */
    private final Type type;

    /** Client-ID, die die Nachricht gesendet hat / fuer die sie bestimmt ist. */
    private final String clientId;

    /**
     * Bei COMMAND: der Befehlsname (z.B. "buy", "sell", "ready").
     * Bei CALLBACK: der Callback-Typ (z.B. "onRoundStarted", "onCombatFinished").
     */
    private final String action;

    /** Spieler-ID (0 oder 1). */
    private final int playerId;

    /** Ganzzahlige Argumente (z.B. Slot, Zeile, Spalte). */
    private final int[] args;

    /**
     * Bei CALLBACK: der JSON-String fuer komplexe DTOs (GameStateDto, CombatResultDto, etc.).
     * Null bei einfachen Callbacks ohne Payload.
     * Bei getState-Request: der Name der temporären Reply-Queue.
     */
    private final String payload;

    /**
     * Konstruktor fuer einen Befehl (Client -> Server).
     */
    public JmsMessage(String clientId, int playerId, String action, int... args) {
        this(Type.COMMAND, clientId, playerId, action, args, null);
    }

    /**
     * Konstruktor fuer einen Callback (Server -> Client).
     */
    public JmsMessage(String clientId, String action, String payload) {
        this(Type.CALLBACK, clientId, -1, action, new int[0], payload);
    }

    /**
     * Konstruktor fuer einen Callback mit Spieler-ID und Argumenten (z.B. onUnitCombined, onRoundStarted).
     */
    public static JmsMessage callback(String clientId, int playerId, String action, int[] args, String payload) {
        return new JmsMessage(Type.CALLBACK, clientId, playerId, action, args, payload);
    }

    /**
     * Konstruktor fuer einen Befehl mit Reply-Queue (Request/Reply-Muster, z.B. getState).
     * Der replyQueue-Parameter enthält den Namen der temporären Reply-Queue.
     */
    public static JmsMessage commandWithReply(String clientId, int playerId, String action, int[] args, String replyQueue) {
        return new JmsMessage(Type.COMMAND, clientId, playerId, action, args, replyQueue);
    }

    private JmsMessage(Type type, String clientId, int playerId, String action, int[] args, String payload) {
        this.type = type;
        this.clientId = clientId;
        this.playerId = playerId;
        this.action = action;
        this.args = args != null ? args.clone() : new int[0];
        this.payload = payload;
    }

    public Type type() { return type; }
    public String clientId() { return clientId; }
    public String action() { return action; }
    public int playerId() { return playerId; }
    public int[] args() { return args.clone(); }
    public String payload() { return payload; }

    /** Liefert Argument an Position i. */
    public int arg(int i) {
        if (i < 0 || i >= args.length) throw new IndexOutOfBoundsException("Arg index: " + i);
        return args[i];
    }

    /** Ist dies ein Befehl? */
    public boolean isCommand() { return type == Type.COMMAND; }

    /** Ist dies ein Callback? */
    public boolean isCallback() { return type == Type.CALLBACK; }

    @Override
    public String toString() {
        return "JmsMessage{type=" + type + ", client='" + clientId + '\''
                + ", action='" + action + "', playerId=" + playerId + '}';
    }

    // ---------- JSON-Serialisierung (für JMS TextMessage) ----------

    /**
     * Serialisiert diese Nachricht als JSON-String.
     * Wird als TextMessage-Payload über JMS übertragen.
     */
    public String toJson() {
        javax.json.JsonObjectBuilder builder = javax.json.Json.createObjectBuilder();
        builder.add("type", type.name());
        builder.add("clientId", clientId);
        builder.add("action", action);
        builder.add("playerId", playerId);

        // args als JSON-Array
        javax.json.JsonArrayBuilder argsBuilder = javax.json.Json.createArrayBuilder();
        for (int a : args) {
            argsBuilder.add(a);
        }
        builder.add("args", argsBuilder.build());

        // payload (kann null sein)
        if (payload != null) {
            builder.add("payload", payload);
        }
        return builder.build().toString();
    }

    /**
     * Deserialisiert eine JmsMessage aus einem JSON-String.
     */
    public static JmsMessage fromJson(String json) {
        javax.json.JsonObject obj = javax.json.Json.createReader(new java.io.StringReader(json)).readObject();
        String type = obj.getString("type");
        String clientId = obj.getString("clientId");
        String action = obj.getString("action");
        int playerId = obj.getInt("playerId");

        int[] argsArr = new int[0];
        if (obj.containsKey("args")) {
            javax.json.JsonArray arr = obj.getJsonArray("args");
            argsArr = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                argsArr[i] = arr.getInt(i);
            }
        }

        String payload = obj.containsKey("payload") ? obj.getString("payload", null) : null;

        JmsMessage.Type t = "COMMAND".equals(type) ? JmsMessage.Type.COMMAND : JmsMessage.Type.CALLBACK;
        return new JmsMessage(t, clientId, playerId, action, argsArr, payload);
    }
}
