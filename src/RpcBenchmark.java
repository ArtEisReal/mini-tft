import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;

// AI-GENERATED-START: Neue Datei - Aufgabe 6: Dauer lokaler Funktionsaufrufe vs. entfernter RPC-Aufrufe - 2026-11-02 14:40 CET
/**
 * Aufgabe 6: Vergleich der Dauer eines lokalen Funktionsaufrufs mit der Dauer
 * eines entfernten RPC-Aufrufs (RMI/JRMP).
 *
 * Es wird IMMER dieselbe Methode auf derselben Implementierung gemessen:
 *
 *   lokal   - direkter Methodenaufruf auf dem Objekt-Referenz im selben JVM
 *             (kein Stub, keine Serialisierung, kein Netzwerk)
 *   remote  - Aufruf ueber den RMI-Stub
 *             Stub -> Marshalling (Java-Serialisierung) -> TCP -> Server-Logik
 *             -> Unmarshalling -> Rueckweg
 *
 * Drei Messreihen mit steigender Nutzlast:
 *   ping()          Minimalaufruf, Rueckgabe long  -> reine RPC-Grundkosten
 *   getState(0)     DTO mit Listen                 -> + Serialisierungskosten
 *   getStateJson(0) dasselbe DTO als JSON-String   -> + JSON-Marshalling-Kosten
 *
 * Aufruf:
 *   java RpcBenchmark [host] [messungen]     Standard: localhost, 500
 * oder aus dem Konsolen-Client heraus mit dem Befehl "bench".
 *
 * Hinweis fuer die Auswertung: gegen "localhost" misst man nur die
 * Protokoll-/Serialisierungskosten; gegen die IP des anderen Rechners
 * kommt die echte Netzwerklataenz hinzu - beides sollte man vorfuehren.
 */
public final class RpcBenchmark {

    /** Standardzahl der Messungen je Methode. */
    public static final int DEFAULT_ITERATIONS = 500;
    /** Warmup-Aufrufe (JIT-Optimierung) - nicht Teil der Messung. */
    public static final int WARMUP = 100;

    private RpcBenchmark() {}

    /** Ein Aufruf, der auch checked Exceptions (GameRuleException/RemoteException) werfen darf. */
    @FunctionalInterface
    public interface Call {
        long run() throws Exception;
    }

    /** Kennzahlen einer Messreihe in Nanosekunden. */
    public record Stats(long min, long avg, long median, long max) {}

    // ---------- Messung ----------

    /** Misst n Aufrufe nach WARMUP-Aufrufen und liefert die Einzelzeiten in ns. */
    public static long[] measure(Call call, int iterations) throws Exception {
        for (int i = 0; i < WARMUP; i++) call.run();
        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            call.run();
            samples[i] = System.nanoTime() - start;
        }
        return samples;
    }

    /** Auswertung einer Messreihe (ns -> Kennzahlen). */
    public static Stats stats(long[] samplesNs) {
        long[] sorted = samplesNs.clone();
        Arrays.sort(sorted);
        long sum = 0;
        for (long s : sorted) sum += s;
        return new Stats(sorted[0], sum / sorted.length,
                sorted[sorted.length / 2], sorted[sorted.length - 1]);
    }

    // ---------- Bericht ----------

    /**
     * Fuehrt den kompletten Vergleich aus und liefert den formatierten Bericht.
     *
     * @param remote der RMI-Stub zum Server (Messreihe "remote")
     * @param host   nur fuer die Anzeige des Ziels
     * @param iterations Messungen je Methode
     */
    public static String compare(GameService remote, String host, int iterations) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("=== Aufgabe 6: lokaler Funktionsaufruf vs. entfernter RPC-Aufruf ===\n");
        out.append("Ziel     : rmi://").append(host).append(':').append(RmiConfig.REGISTRY_PORT)
           .append('/').append(RmiConfig.BIND_NAME).append('\n');
        out.append("Messung  : ").append(iterations).append(" Aufrufe je Methode, Warmup ")
           .append(WARMUP).append(" (nicht mitgezaehlt)\n");
        out.append("lokal    : direkter Methodenaufruf im selben JVM (kein Stub, kein Netzwerk)\n");
        out.append("remote   : RMI-Stub -> Marshalling -> TCP -> Server -> Unmarshalling\n\n");

        // Lokale Referenz auf dieselbe Implementierung (kein Stub!)
        GameService local = new GameEngine("Benchmark-lokal", "Benchmark-gespiegelt");
        try {
            row(out, "ping()", iterations,
                    () -> local.ping(),
                    () -> remote.ping());
            row(out, "getState(0)", iterations,
                    () -> { local.getState(0); return 0; },
                    () -> { remote.getState(0); return 0; });
            row(out, "getStateJson(0)", iterations,
                    () -> JsonMapper.toGameState(local.getStateJson(0)).round(),
                    () -> JsonMapper.toGameState(remote.getStateJson(0)).round());
        } finally {
            try {
                UnicastRemoteObject.unexportObject(local, true);
            } catch (Exception ignored) {
                // schon deexportiert - egal fuer die Messung
            }
        }

        out.append("\nAuswertung:\n");
        out.append(" - Die Differenz 'remote - lokal' ist der RPC-UEberkopf: Stub-Aufruf,\n");
        out.append("   Marshalling/Unmarshalling der Argumente und Rueckgabe, Netzwerk-Turnaround.\n");
        out.append(" - ping() hat keinen Nutzlast-Overhead -> minimale RPC-Grundkosten pro Aufruf.\n");
        out.append(" - getState(0) uebertraegt DTOs (Listen) -> Differenz zu ping() ist die Serialisierung.\n");
        out.append(" - getStateJson(0) marshallt UND unmarshalls dasselbe DTO als JSON (JSON-P) ->\n");
        out.append("   die Differenz zu getState(0) ist der Aufwand des menschenlesbaren Formats.\n");
        out.append(" - Gemessen gegen localhost enthaelt 'remote' KEINE Netzwerklataenz -\n");
        out.append("   erst der Vergleich mit der IP des zweiten Rechners zeigt den echten Netzwerkanteil.\n");
        return out.toString();
    }

    private static void row(StringBuilder out, String label, int iterations,
                            Call localCall, Call remoteCall) throws Exception {
        Stats local = stats(measure(localCall, iterations));
        Stats remote = stats(measure(remoteCall, iterations));
        double factor = local.avg() > 0 ? (double) remote.avg() / local.avg() : Double.POSITIVE_INFINITY;
        out.append(String.format(
                "%-16s lokal Ø %11s µs  median %11s µs  |  remote Ø %11s µs  median %11s µs  min %9s  max %9s  |  Faktor %s%n",
                label, us(local.avg()), us(local.median()), us(remote.avg()), us(remote.median()),
                us(remote.min()), us(remote.max()),
                factor == Double.POSITIVE_INFINITY ? "∞"
                        : factor < 100 ? String.format("%,.1f", factor) : String.format("%,.0f", factor)));
    }

    /** ns -> Mikrosekunden als String. */
    private static String us(long ns) {
        return String.format("%,.2f", ns / 1000.0);
    }

    // ---------- Einstieg ----------

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 && !args[0].isBlank() ? args[0] : "localhost";
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_ITERATIONS;

        RmiClient client = new RmiClient(host);
        client.connect();
        try {
            System.out.println(compare(client.getService(), host, iterations));
        } finally {
            client.close();
        }
        // Exportierte Objekte (Registry-Lookup/lokale Messobjekte) duerfen den Prozess nicht halten
        System.exit(0);
    }
}
// AI-GENERATED-END
