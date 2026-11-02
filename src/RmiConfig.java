// AI-GENERATED-START: Neue Datei - zentrale RMI-Konfiguration (Host + feste Ports) für Server/Client-Betrieb über VPN - 2026-11-02 12:41 CET
/**
 * Zentrale RMI-Konfiguration - noetig, wenn Server und Client auf verschiedenen
 * Rechnern laufen (z.B. ueber ein VPN).
 *
 * Einstellungen als System-Property (-D...) oder Env-Variable:
 *
 *   minitft.host        Eigene IP/Hostname, die in den RMI-Stubs landet.
 *                       Entspricht java.rmi.server.hostname, wird aber VOR dem
 *                       Export des ersten Remote-Objekts gesetzt.
 *                       Env-Alternative: MINITFT_HOST
 *   minitft.server      Beim Client: Server-Host, falls keiner als Argument
 *                       uebergeben wurde.  Env-Alternative: MINITFT_SERVER
 *   minitft.enginePort  Fester Port des GameEngine (Server).  Standard 1100.
 *   minitft.listenerPort Fester Port des GameListener (Client).  Standard 1098.
 *                       "0" = anonymer Port (Firewall dann schwer zu regeln).
 *
 * Beispiel Server:   java -Dminitft.host=10.8.0.2 RmiServer
 * Beispiel Client:   java SwingMain 10.8.0.2
 */
public final class RmiConfig {
    /** RMI-Registry (Lookup). */
    static final int REGISTRY_PORT = 1099;
    /** GameEngine auf dem Server. */
    static final int ENGINE_PORT = 1100;
    /** GameListener-Callback-Objekt auf dem Client (Server -> Client). */
    static final int LISTENER_PORT = 1098;
    /** Bindings-Name in der Registry. */
    static final String BIND_NAME = "mini-tft";

    private RmiConfig() {}

    /**
     * Eigene Adresse fuer die RMI-Stubs: minitft.host, sonst MINITFT_HOST,
     * sonst ein bereits gesetztes java.rmi.server.hostname, sonst null
     * (dann entscheidet RMI selbst - nur fuer rein lokalen Betrieb geeignet).
     */
    static String localHost() {
        String host = System.getProperty("minitft.host");
        if (host == null || host.isBlank()) host = System.getenv("MINITFT_HOST");
        if (host == null || host.isBlank()) host = System.getProperty("java.rmi.server.hostname");
        return (host == null || host.isBlank()) ? null : host.trim();
    }

    /**
     * Schreibt die eigene Adresse nach java.rmi.server.hostname.
     * Muss vor dem Export des ersten Remote-Objekts aufgerufen werden,
     * sonst haengt ein nicht erreichbarer lokaler Name in den Stubs.
     */
    static void applyLocalHost() {
        String host = localHost();
        if (host != null && System.getProperty("java.rmi.server.hostname") == null) {
            System.setProperty("java.rmi.server.hostname", host);
        }
    }

    /**
     * Server-Host fuer Clients: erst Argument, dann minitft.server/MINITFT_SERVER,
     * sonst "localhost".
     */
    static String serverHost(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return args[0].trim();
        }
        String host = System.getProperty("minitft.server");
        if (host == null || host.isBlank()) host = System.getenv("MINITFT_SERVER");
        return (host == null || host.isBlank()) ? "localhost" : host.trim();
    }

    /** Liest einen Port aus einer System-Property, sonst den Standard. */
    static int port(String key, int defaultPort) {
        Integer value = Integer.getInteger(key);
        return (value == null || value < 0 || value > 65535) ? defaultPort : value;
    }
}
// AI-GENERATED-END
