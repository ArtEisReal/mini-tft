/** Wird bei Regelverstößen geworfen (z.B. zu wenig Gold). Eignet sich für Aufgabe 7 (Exception-Übertragung). */
public class GameRuleException extends Exception {
    private static final long serialVersionUID = 1L;

    public GameRuleException(String message) {
        super(message);
    }
}
