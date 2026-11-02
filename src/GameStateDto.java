// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.io.Serializable;

/** DTO: Gesamtzustand aus Sicht eines Spielers. */
public record GameStateDto(int round, PlayerDto self, PlayerDto opponent,
                           boolean gameOver, String winnerName) implements Serializable {}
                           // AI-GENERATED-END
