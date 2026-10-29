import java.io.Serializable;

/** DTO: Schnappschuss einer Einheit waehrend des Kampfes (fuer die Animation). */
public record UnitStateDto(int team, int row, int col, String name, int star,
                           int hp, int maxHp, boolean alive) implements Serializable {}
