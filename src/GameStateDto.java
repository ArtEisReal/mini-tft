import java.io.Serializable;

/** DTO: Gesamtzustand aus Sicht eines Spielers. */
public record GameStateDto(int round, PlayerDto self, PlayerDto opponent,
                           boolean gameOver, String winnerName) implements Serializable {}
