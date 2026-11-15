import java.util.Arrays;

// AI-GENERATED-START: Neue Datei - Aufgabe 6: Dauer lokaler Funktionsaufrufe vs. entfernter RPC-Aufrufe - 2026-11-02 14:40 CET
/**
 * Aufgabe 6: Vergleich der Dauer eines lokalen Funktionsaufrufs mit der Dauer
 * eines entfernten RPC-Aufrufs (RMI/JRMP).
 */
public final class RpcBenchmark {

    public static final int DEFAULT_ITERATIONS = 500;
    public static final int WARMUP = 100;

    private RpcBenchmark() {}

    @FunctionalInterface
    public interface Call {
        long run() throws Exception;
    }

    public record Stats(long min, long avg, long median, long max) {}

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

    public static Stats stats(long[] samplesNs) {
        long[] sorted = samplesNs.clone();
        Arrays.sort(sorted);
        long sum = 0;
        for (long s : sorted) sum += s;
        return new Stats(sorted[0], sum / sorted.length,
                sorted[sorted.length / 2], sorted[sorted.length - 1]);
    }

    public static String compare(GameService remote, String host, int iterations) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("=== Aufgabe 6: lokaler Funktionsaufruf vs. entfernter RPC-Aufruf ===\n");
        out.append("Ziel     : rmi://").append(host).append(':').append(RmiConfig.REGISTRY_PORT)
           .append('/').append(RmiConfig.BIND_NAME).append('\n');
        out.append("Messung  : ").append(iterations).append(" Aufrufe je Methode, Warmup ")
           .append(WARMUP).append(" (nicht mitgezaehlt)\n");
        out.append("lokal    : direkter Methodenaufruf im selben JVM (kein Stub, kein Netzwerk)\n");
        out.append("remote   : RMI-Stub -> Marshalling -> TCP -> Server -> Unmarshalling\n\n");

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
            // GameEngine benoetigt keine RMI-Deexportierung mehr
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

    private static String us(long ns) {
        return String.format("%,.2f", ns / 1000.0);
    }

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
        System.exit(0);
    }
}
// AI-GENERATED-END
