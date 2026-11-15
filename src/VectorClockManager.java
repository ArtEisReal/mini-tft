import java.util.Map;
import java.util.Set;
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
 *   - Erkennt normales, verspaetetes, lueckenhaftes und doppeltes Zustellen
 *   - Aktualisiert die Server-Uhr bei jedem empfangenen Befehl
 *   - Protokolliert alle Uhr-Zustaende zur Demonstration
 *
 * Sicherheitsregeln (Anti-Cheat):
 *   1. Luecken-Pruefung: Ein Sprung ist nur bis MAX_GAP erlaubt. Eine Luecke
 *      bedeutet "Nachricht ist noch unterwegs" (Delayed Delivery) und triggert
 *      spaeter beim Eintreffen die serverseitige Kompensation.
 *   2. Duplikat-Pruefung: bereits verarbeitete Ticks werden ignoriert.
 *   3. Cross-Entity-Pruefung: Client darf fremde Uhren-Indizes nicht veraendern.
 *
 * Anders als frueher wird eine verspaetete Nachricht NICHT abgelehnt: sie
 * fuellt eine bekannte Luecke und wird als {@link Delivery#LATE} markiert.
 */
public class VectorClockManager {

    /** Server-weite Vektoruhr: [Server_Tick, P1_Tick, P2_Tick]. */
    private final VectorClock serverClock;

    /**
     * Vom Server erwartete Uhr pro Client-ID (hoechster bekannter Tick).
     * Der Server speichert, welche Uhr er vom Client erwartet.
     */
    private final Map<String, VectorClock> clientExpectedClocks = new ConcurrentHashMap<>();

    /**
     * Der Index des Clients in der Vektoruhr.
     * Z.B. "swing-player-0" -> 1 (Player 1 Index)
     */
    private final Map<String, Integer> clientIndices = new ConcurrentHashMap<>();

    /** Hoechster bereits akzeptierter eigener Tick pro Client. */
    private final Map<String, Integer> highestOwnTicks = new ConcurrentHashMap<>();

    /** Bereits verarbeitete eigene Ticks pro Client (Duplikat-Erkennung). */
    private final Map<String, Set<Integer>> processedTicks = new ConcurrentHashMap<>();

    /** Bekannte, noch fehlende eigene Ticks pro Client (Luecken). */
    private final Map<String, Set<Integer>> pendingTicks = new ConcurrentHashMap<>();

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
        clientExpectedClocks.put(clientId, new VectorClock());
        clientIndices.put(clientId, playerIndex);
        highestOwnTicks.put(clientId, 0);
        processedTicks.put(clientId, ConcurrentHashMap.newKeySet());
        pendingTicks.put(clientId, ConcurrentHashMap.newKeySet());
        System.out.println("  [VC] Client registriert: " + clientId + " -> Index " + playerIndex);
    }

    /**
     * Setzt die erwartete Uhr eines Clients zurueck (z.B. nach einem Reconnect
     * oder Neustart des GUI-Clients). Der Index bleibt dabei erhalten.
     */
    public void resetClient(String clientId, int playerIndex) {
        clientExpectedClocks.put(clientId, new VectorClock());
        clientIndices.put(clientId, playerIndex);
        highestOwnTicks.put(clientId, 0);
        processedTicks.put(clientId, ConcurrentHashMap.newKeySet());
        pendingTicks.put(clientId, ConcurrentHashMap.newKeySet());
        System.out.println("  [VC] Neue Client-Session: " + clientId + " -> Index " + playerIndex
                + ", erwartete Uhr zurueckgesetzt auf [0,0,0]");
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
     * @param msg die eingehende JmsMessage
     * @return ValidationResult mit Erfolg/Fehler, Zustellart und aktualisierter Server-Uhr
     */
    public synchronized ValidationResult validate(JmsMessage msg) {
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

        // REGEL 3 (zuerst): Cross-Entity-Pruefung
        // Der Client darf die fremden Indizes aus der Server-Uhr uebernehmen (Merge aus Callbacks),
        // aber sie nicht darueber hinaus erhoehen.
        for (int i = 0; i < VectorClock.SIZE; i++) {
            if (i != clientIndex) {
                int incomingOtherTick = incomingClock.get(i);
                int serverOtherTick = serverClock.get(i);
                if (incomingOtherTick > serverOtherTick) {
                    return ValidationResult.reject(clientId,
                            "Cross-Entity-Verletzung: Client " + clientId + " veraendert Index " + i
                            + " auf " + incomingOtherTick + " (Server hat nur " + serverOtherTick
                            + " — fremde Uhren duerfen nicht ueber Server-Wert erhoeht werden)");
                }
            }
        }

        int incomingOwnTick = incomingClock.get(clientIndex);
        int highestOwnTick = highestOwnTicks.getOrDefault(clientId, 0);
        Set<Integer> processed = processedTicks.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet());
        Set<Integer> pending = pendingTicks.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet());

        if (incomingOwnTick <= 0) {
            return ValidationResult.reject(clientId,
                    "Ungueltiger Tick " + incomingOwnTick + " (Client muss seine Uhr vor dem Senden inkrementieren)");
        }

        // REGEL 2: Duplikat-Pruefung (z.B. JMS-Redelivery)
        if (processed.contains(incomingOwnTick)) {
            return ValidationResult.accept(clientId, serverClock.clone(), incomingClock, Delivery.DUPLICATE);
        }

        Delivery delivery;
        if (pending.contains(incomingOwnTick)) {
            // REGEL 1a: Verspaetete Nachricht fuellt eine bereits bekannte Luecke.
            pending.remove(incomingOwnTick);
            processed.add(incomingOwnTick);
            delivery = Delivery.LATE;
        } else if (incomingOwnTick == highestOwnTick + 1) {
            // Normalfall: naechster Tick in der Reihenfolge.
            processed.add(incomingOwnTick);
            highestOwnTick = incomingOwnTick;
            delivery = Delivery.NORMAL;
        } else if (incomingOwnTick > highestOwnTick + 1) {
            // REGEL 1b: Luecke -> Nachricht(en) sind noch unterwegs (Delayed Delivery).
            int gap = incomingOwnTick - highestOwnTick - 1;
            if (gap > JmsConfig.MAX_GAP) {
                return ValidationResult.reject(clientId,
                        "Max-Gap-Verletzung: Tick-Sprung von " + highestOwnTick + " auf " + incomingOwnTick
                        + " (" + gap + " fehlende Nachrichten, erlaubt: " + JmsConfig.MAX_GAP + ")");
            }
            for (int t = highestOwnTick + 1; t < incomingOwnTick; t++) {
                pending.add(t);
            }
            processed.add(incomingOwnTick);
            highestOwnTick = incomingOwnTick;
            delivery = Delivery.GAP;
        } else {
            // Tick liegt hinter dem hoechsten bekannten Stand, ist aber weder
            // verarbeitet noch als Luecke vorgemerkt -> Replay/Manipulation.
            return ValidationResult.reject(clientId,
                    "Veralteter Tick " + incomingOwnTick + " (hoechster bekannter Tick: "
                    + highestOwnTick + ", Luecken: " + pending + ")");
        }

        highestOwnTicks.put(clientId, highestOwnTick);

        // Validierung erfolgreich — Server-Uhr aktualisieren
        VectorClock merged = serverClock.merge(incomingClock);
        merged.increment(VectorClock.SERVER_INDEX); // Server-Tick erhoehen

        // Erwartete Uhr des Clients aktualisieren (nur den eigenen Index, nie zurueck)
        VectorClock newExpected = expectedClock.clone();
        newExpected.getInternalTicks()[clientIndex] = Math.max(expectedClock.get(clientIndex), incomingOwnTick);
        clientExpectedClocks.put(clientId, newExpected);

        return ValidationResult.accept(clientId, merged, incomingClock, delivery);
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

    /** Liefert die bekannten Luecken eines Clients (fuer Demo-Logging). */
    public Set<Integer> pendingTicks(String clientId) {
        Set<Integer> pending = pendingTicks.get(clientId);
        return pending == null ? Set.of() : Set.copyOf(pending);
    }

    /** Liefert die bereits verarbeiteten Ticks eines Clients (fuer Demo-Logging). */
    public Set<Integer> processedTicks(String clientId) {
        Set<Integer> processed = processedTicks.get(clientId);
        return processed == null ? Set.of() : Set.copyOf(processed);
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
                    + ", P2=" + clock.get(VectorClock.PLAYER_2_INDEX) + "]"
                    + " processed=" + processedTicks(cid)
                    + " pending=" + pendingTicks(cid));
        }
        System.out.println("  [VC] ===================");
    }

    /** Art der Zustellung, die aus der Vektoruhr abgeleitet wurde. */
    public enum Delivery {
        /** Erwarteter naechster Tick. */
        NORMAL,
        /** Nachricht kam vor einer frueheren an; Luecke wurde vorgemerkt. */
        GAP,
        /** Nachricht fuellt eine bekannte Luecke -> Rewind/Replay noetig. */
        LATE,
        /** Tick wurde bereits verarbeitet -> ignorieren. */
        DUPLICATE
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
        private final Delivery delivery;

        private ValidationResult(boolean accepted, String clientId, String reason,
                                 VectorClock newServerClock, VectorClock incomingClock, Delivery delivery) {
            this.accepted = accepted;
            this.clientId = clientId;
            this.reason = reason;
            this.newServerClock = newServerClock;
            this.incomingClock = incomingClock;
            this.delivery = delivery;
        }

        public static ValidationResult accept(String clientId, VectorClock newServerClock,
                                              VectorClock incomingClock, Delivery delivery) {
            return new ValidationResult(true, clientId, null, newServerClock, incomingClock, delivery);
        }

        public static ValidationResult reject(String clientId, String reason) {
            return new ValidationResult(false, clientId, reason, null, null, null);
        }

        public boolean isAccepted() { return accepted; }
        public String clientId() { return clientId; }
        public String reason() { return reason; }
        public VectorClock newServerClock() { return newServerClock; }
        public VectorClock incomingClock() { return incomingClock; }
        public Delivery delivery() { return delivery; }
        public boolean isLate() { return delivery == Delivery.LATE; }
        public boolean isDuplicate() { return delivery == Delivery.DUPLICATE; }
        public boolean hasGap() { return delivery == Delivery.GAP; }
    }
}
