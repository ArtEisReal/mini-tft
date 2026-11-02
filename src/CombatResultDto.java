// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.io.Serializable;
import java.util.List;

/** DTO: Ergebnis eines Kampfes (Server -> Client, über den Callback). winnerId = -1 bei Unentschieden. */
public record CombatResultDto(int round, List<String> log, int winnerId, int damage) implements Serializable {}
// AI-GENERATED-END
