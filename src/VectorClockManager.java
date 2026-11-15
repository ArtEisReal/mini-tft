import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet die Vektoruhren auf dem Server.
 *
 * Der Server fuehrt die "Master-Matrix" (Golden Copy) aller Client-Uhren.
 * Jeder Client hat einen eigenen Index in der Vektoruhr:
 *   Index 0 = Server, Index 1 = Player 1, Index 2 = Player 2
 *
 * Der VectorClockManager:
 *   - Speichert die vom Server erwartete Uhr jedes Clients
 *   - Validiert eingehende Client-Nachrichten auf Manipulation
 *   - Aktualisiert die Server-Uhr bei jedem empfangenen Befehl
 *   - Protokolliert alle Uhr-Zustaende zur Demonstration
 *
 * Sicherheitsregeln (Anti-Cheat):
 *   1. Monotonitaets-Pruefung: Client kann nicht zurueck in der eigenen Zeit springen
 *   2. Max-Step-Pruefung: Client kann nur um +1 pro Nachricht springen (Anti-Speedhack)
 *   3. Cross-Entity-Pruefung: Client darf nicht die Uhren anderer Spieler veraendern
 */
public class VectorClockManager {

    /** Server-weite Vektoruhr: [Server_Tick, P1_Tick, P2_Tick]. */
    private final VectorClock serverClock;

    /**
     * Vom Server erwartete Uhr pro Client-ID.
     * Der Server speichert, welche Uhr er vom Client erwartet.
     */
    private final Map<String, VectorClock> clientExpectedClocks = new ConcurrentHashMap<>();

    /**
     * Der Index des Clients in der Vektoruhr.
     * Z.B. "swing-player-0" -> 1 (Player 1 Index)
     */
    private final Map<String, Integer> clientIndices = new ConcurrentHashMap<>();

    /**
     * Erstellt einen neuen VectorClockManager mit einer initialen Server-Uhr [0,0,0].
     */
    public VectorClockManager() {
        this.serverClock = new VectorClock();
        logServerClock("INIT", serverClock);
    }

    /**
     * Registriert einen neuen Client mit dem gegebenen Spieler-Index.
     * Wird aufgerufen, wenn ein Client sich zum ersten Mal verbindet.
     *
     * @param clientId  die eindeutige Client-ID
     * @param playerIndex der Index in der Vektoruhr (1 fuer Spieler 0, 2 fuer Spieler 1)
     */
    public void registerClient(String clientId, int playerIndex) {
        VectorClock initialClock = new VectorClock();
        clientExpectedClocks.put(clientId, initialClock);
        clientIndices.put(clientId, playerIndex);
        System.out.println("  [VC] Client registriert: " + clientId + " -> Index " + playerIndex);
    }

    /**
     * Liefert den gespeicherten Index eines Clients.
     */
    public Integer getClientIndex(String clientId) {
        return clientIndices.get(clientId);
    }

    /**
     * Validiert eine eingehende Nachricht eines Clients.
     *
     * Prueft:
     *   1. Monotonitaet: Client-Uhr muss sich vorwaerts bewegen
     *   2. Max-Step: Client-Uhr darf sich nur um +1 bewegen
     *   3. Cross-Entity: Client darf fremde Uhren-Indizes nicht veraendern
     *
     * @param msg die eingehende JmsMessage
     * @return ValidationResult mit Erfolg/Fehler und optionaler aktualisierter Server-Uhr
     */
    public ValidationResult validate(JmsMessage msg) {
        String clientId = msg.clientId();
        VectorClock incomingClock = msg.vectorClock();

        // Wenn keine Vektoruhr in der Nachricht, ist das ein alter Client -> ablehnen
        if (incomingClock == null) {
            return ValidationResult.reject(clientId, "Keine Vektoruhr in der Nachricht");
        }

        // Client muss registriert sein
        Integer clientIndex = clientIndices.get(clientId);
        if (clientIndex == null) {
            return ValidationResult.reject(clientId, "Client nicht registriert: " + clientId);
        }

        VectorClock expectedClock = clientExpectedClocks.get(clientId);
        if (expectedClock == null) {
            return ValidationResult.reject(clientId, "Keine erwartete Uhr fuer Client: " + clientId);
        }

        // REGEL 1: Monotonitaets-Pruefung
        // Der Client muss seine eigene Uhr mindestens um 1 erhoeht haben
        int incomingOwnTick = incomingClock.get(clientIndex);
        int expectedOwnTick = expectedClock.get(clientIndex);

        if (incomingOwnTick <= expectedOwnTick) {
            return ValidationResult.reject(clientId,
                    "Monotonitaets-Verletzung: Client " + clientId + " sendet Tick " + incomingOwnTick
                    + ", erwartet war > " + expectedOwnTick
                    + " (moeegliche Manipulation: Time Freezing)");
        }

        // REGEL 2: Max-Step-Pruefung (Anti-Speedhack)
        // Der Client darf seine eigene Uhr nur um genau +1 erhoehen
        if (incomingOwnTick > expectedOwnTick + 1) {
            return ValidationResult.reject(clientId,
                    "Max-Step-Verletzung: Client " + clientId + " springt von Tick " + expectedOwnTick
                    + " auf " + incomingOwnTick + " (erlaubt: nur +1)");
        }

        // REGEL 3: Cross-Entity-Pruefung
        // Der Client darf die fremden Indizes aus der Server-Uhr uebernehmen (Merge aus Callbacks),
        // aber sie nicht darueber hinaus erhoehen.
        // Der Client sendet die gemergte Uhr, also sollten die fremden Indizes <= Server-Uhr sein.
        for (int i = 0; i < VectorClock.SIZE; i++) {
            if (i != clientIndex) {
                int incomingOtherTick = incomingClock.get(i);
                int serverOtherTick = serverClock.get(i);
                if (incomingOtherTick > serverOtherTick) {
                    return ValidationResult.reject(clientId,
                            "Cross-Entity-Verletzung: Client " + clientId + " veraendert Index " + i
                            + " auf " + incomingOtherTick + " (Server hat nur " + serverOtherTick
                            + " — fremde Uhren duerfen nicht ueber Server-Wert erhoht werden)");
                }
            }
        }

        // Validierung erfolgreich — Server-Uhr aktualisieren
        VectorClock merged = serverClock.merge(incomingClock);
        merged.increment(VectorClock.SERVER_INDEX); // Server-Tick erhoehen

        // Erwartete Uhr des Clients aktualisieren (nur den eigenen Index)
        VectorClock newExpected = expectedClock.clone();
        newExpected.getInternalTicks()[clientIndex] = incomingOwnTick;
        clientExpectedClocks.put(clientId, newExpected);

        return ValidationResult.accept(clientId, merged, incomingClock);
    }

    /**
     * Liefert die aktuelle Server-Vektoruhr.
     */
    public VectorClock getServerClock() {
        return serverClock.clone();
    }

    /**
     * Setzt die Server-Uhr auf einen neuen Wert (nach Validierung/Merge).
     * Wird vom JmsServer aufgerufen, nachdem eine Nachricht validiert wurde.
     */
    public void setServerClock(VectorClock newClock) {
        System.arraycopy(newClock.getInternalTicks(), 0, serverClock.getInternalTicks(), 0, VectorClock.SIZE);
        logServerClock("AFTER_MERGE", serverClock);
    }

    /**
     * Aktualisiert die Server-Uhr nach der Verarbeitung eines Callbacks.
     * Wird aufgerufen, wenn der Server einen Callback an Clients sendet.
     */
    public void updateServerClock() {
        serverClock.increment(VectorClock.SERVER_INDEX);
        logServerClock("AFTER_CALLBACK", serverClock);
    }

    /**
     * Loggt den aktuellen Zustand der Server-Vektoruhr.
     */
    private void logServerClock(String event, VectorClock clock) {
        System.out.println("  [VC] Server " + event + ": " + clock
                + " [S=" + clock.get(VectorClock.SERVER_INDEX)
                + ", P1=" + clock.get(VectorClock.PLAYER_1_INDEX)
                + ", P2=" + clock.get(VectorClock.PLAYER_2_INDEX) + "]");
    }

    /**
     * Loggt den Zustand aller Client-Uhren.
     */
    public void logAllClocks(String label) {
        System.out.println("  [VC] === " + label + " ===");
        System.out.println("  [VC] Server: " + serverClock
                + " [S=" + serverClock.get(VectorClock.SERVER_INDEX)
                + ", P1=" + serverClock.get(VectorClock.PLAYER_1_INDEX)
                + ", P2=" + serverClock.get(VectorClock.PLAYER_2_INDEX) + "]");
        for (Map.Entry<String, VectorClock> entry : clientExpectedClocks.entrySet()) {
            String cid = entry.getKey();
            VectorClock clock = entry.getValue();
            Integer idx = clientIndices.get(cid);
            System.out.println("  [VC] Client " + cid + " (Index " + idx + "): " + clock
                    + " [S=" + clock.get(VectorClock.SERVER_INDEX)
                    + ", P1=" + clock.get(VectorClock.PLAYER_1_INDEX)
                    + ", P2=" + clock.get(VectorClock.PLAYER_2_INDEX) + "]");
        }
        System.out.println("  [VC] ===================");
    }

    /**
     * Ergebnis der Validierung einer eingehenden Nachricht.
     */
    public static class ValidationResult {
        private final boolean accepted;
        private final String clientId;
        private final String reason;
        private final VectorClock newServerClock;
        private final VectorClock incomingClock;

        private ValidationResult(boolean accepted, String clientId, String reason,
                                 VectorClock newServerClock, VectorClock incomingClock) {
            this.accepted = accepted;
            this.clientId = clientId;
            this.reason = reason;
            this.newServerClock = newServerClock;
            this.incomingClock = incomingClock;
        }

        public static ValidationResult accept(String clientId, VectorClock newServerClock, VectorClock incomingClock) {
            return new ValidationResult(true, clientId, null, newServerClock, incomingClock);
        }

        public static ValidationResult reject(String clientId, String reason) {
            return new ValidationResult(false, clientId, reason, null, null);
        }

        public boolean isAccepted() { return accepted; }
        public String clientId() { return clientId; }
        public String reason() { return reason; }
        public VectorClock newServerClock() { return newServerClock; }
        public VectorClock incomingClock() { return incomingClock; }
    }
}
