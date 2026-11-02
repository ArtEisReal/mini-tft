// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.io.Serializable;
import java.util.List;

/** DTO: Spielerzustand inklusive Feld, Bench und Shop. */
public record PlayerDto(int id, String name, int hp, int gold, int level, boolean ready,
                        List<UnitDto> board, List<UnitDto> bench,
                        List<UnitDto> shop) implements Serializable {}
                        // AI-GENERATED-END
