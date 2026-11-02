// AI-GENERATED-START: Bestandscode aus Basis-Commit d7ee115 (vor Branch client-server) - 2026-10-29
import java.io.Serializable;
import java.util.List;

/** DTO: Zustand des Brettes nach einem Kampf-Tick inkl. der Ereignisse dieses Ticks. */
public record CombatFrameDto(int tick, List<UnitStateDto> units,
                             List<CombatEventDto> events) implements Serializable {}
                             // AI-GENERATED-END
