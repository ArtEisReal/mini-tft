/**
 * Zentrale JMS-Konfiguration fuer die ActiveMQ-basierte Kommunikation.
 *
 * Einstellungen als System-Property (-D...) oder Env-Variable:
 *
 *   minitft.brokerUrl   JMS-Broker-URL.  Standard: tcp://localhost:61616
 *                        Env-Alternative: MINITFT_BROKER_URL
 *   minitft.clientId    Client-ID (zur Identifizierung). Standard: auto
 *
 * Queue-Namen (fest, nicht konfigurierbar):
 *   mini-tft/commands   Client -> Server (Befehle)
 *   mini-tft/callbacks  Server -> Client (Rueckmeldungen)
 *
 * ActiveMQ Web-Console: http://localhost:8161/admin/
 *   Standard-Login: admin / admin
 */
public final class JmsConfig {

    /** Broker-URL (Default: lokaler ActiveMQ auf Port 61616). */
    public static final String DEFAULT_BROKER_URL = "tcp://localhost:61616";

    /** Queue fuer Client -> Server Befehle. */
    public static final String COMMAND_QUEUE = "mini-tft/commands";

    /** Queue fuer Server -> Client Callbacks. */
    public static final String CALLBACK_QUEUE = "mini-tft/callbacks";

    /** Reply-Queue-Prefix fuer einzelne Clients (jeder Client bekommt eine eigene Reply-Queue). */
    public static final String REPLY_QUEUE_PREFIX = "mini-tft/reply/";

    /**
     * Maximale Luecke in der Client-Vektoruhr, die noch als "verspaetete
     * Nachricht" akzeptiert wird. Groessere Spruenge gelten als Manipulation.
     */
    public static final int MAX_GAP = 10;

    /** Verzoegerung fuer die Demo "Kaufen (3 s verzoegert)" in Millisekunden. */
    public static final long DEMO_DELAY_MS = 3000L;

    private JmsConfig() {}

    /**
     * Liest die Broker-URL aus System-Property oder Env-Variable.
     */
    public static String brokerUrl() {
        String url = System.getProperty("minitft.brokerUrl");
        if (url == null || url.isBlank()) url = System.getenv("MINITFT_BROKER_URL");
        return (url == null || url.isBlank()) ? DEFAULT_BROKER_URL : url.trim();
    }

    /**
     * Bildet den Namen der Reply-Queue fuer einen Client.
     * Jeder Client erhaelt eine eigene Queue, damit der Server gezielt Callbacks senden kann.
     */
    public static String replyQueue(String clientId) {
        return REPLY_QUEUE_PREFIX + clientId;
    }
}
