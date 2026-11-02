// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
/** Wird bei Regelverstößen geworfen (z.B. zu wenig Gold). Eignet sich für Aufgabe 7 (Exception-Übertragung). */
public class GameRuleException extends Exception {
    private static final long serialVersionUID = 1L;

    public GameRuleException(String message) {
        super(message);
    }
}
// AI-GENERATED-END
