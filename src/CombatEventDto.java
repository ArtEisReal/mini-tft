// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.io.Serializable;

/** DTO: ein Kampfereignis innerhalb eines Ticks (Angriff, Treffer, Tod, Bewegung). */
public record CombatEventDto(int team, int fromRow, int fromCol, int toRow, int toCol,
                             int damage, String text) implements Serializable {}
                             // AI-GENERATED-END
