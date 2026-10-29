import java.io.Serializable;

/** DTO: ein Kampfereignis innerhalb eines Ticks (Angriff, Treffer, Tod, Bewegung). */
public record CombatEventDto(int team, int fromRow, int fromCol, int toRow, int toCol,
                             int damage, String text) implements Serializable {}
