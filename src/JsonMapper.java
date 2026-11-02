import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// AI-GENERATED-START: Neue Datei - JSON-P (javax.json) Marshalling/Unmarshalling der DTOs - 2026-11-02 14:10 CET
/**
 * Menschenlesbares Nachrichtenformat (JSON) fuer den C-S-Contract.
 *
 * Bibliothek: JSON-P (JSR 374, javax.json) - Marshalling ueber
 * {@code Json.createObjectBuilder()}, Unmarshalling ueber den
 * {@code JsonParser} (Streaming-Parser) bzw. {@code JsonReader}.
 *
 * Es wird jeder DTO-Zustand in beide Richtungen kodiert:
 *   Client -> Server : Befehle als JSON ({@link #commandJson(String, int)})
 *                      -> unmarshalled in {@link #parseCommand(String)}
 *   Server -> Client : GameStateDto/PlayerDto/UnitDto/CombatResultDto als JSON
 *
 * Beispiel (kompakt, auf dem Draht):
 *   {"cmd":"buy","playerId":0,"args":[1]}
 *   {"round":3,"gameOver":false,"winnerName":null,"self":{...},"opponent":{...}}
 */
public final class JsonMapper {

    private JsonMapper() {}

    // ---------- Marshalling (Java-Objekt -> JSON-String) ----------

    /** Schreibt ein JSON-Value, pretty=true fuer die Anzeige (menschlich lesbar). */
    public static String write(JsonValue value, boolean pretty) {
        StringWriter out = new StringWriter();
        // JSON-P 1.1: Pretty-Printing ueber die Writer-Factory (JSON-P 2.0 haette createWriterBuilder)
        JsonWriterFactory factory = Json.createWriterFactory(pretty
                ? Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true)
                : Collections.emptyMap());
        try (JsonWriter writer = factory.createWriter(out)) {
            writer.write(value);
        }
        return out.toString();
    }

    public static JsonObject toJson(GameStateDto s) {
        return Json.createObjectBuilder()
                .add("round", s.round())
                .add("gameOver", s.gameOver())
                .add("winnerName", s.winnerName() == null ? JsonValue.NULL : Json.createValue(s.winnerName()))
                .add("self", toJson(s.self()))
                .add("opponent", toJson(s.opponent()))
                .build();
    }

    public static JsonObject toJson(PlayerDto p) {
        return Json.createObjectBuilder()
                .add("id", p.id())
                .add("name", p.name())
                .add("hp", p.hp())
                .add("gold", p.gold())
                .add("level", p.level())
                .add("ready", p.ready())
                .add("board", toJson(p.board()))
                .add("bench", toJson(p.bench()))
                .add("shop", toJson(p.shop()))
                .build();
    }

    public static JsonObject toJson(UnitDto u) {
        return Json.createObjectBuilder()
                .add("id", u.id())
                .add("name", u.name())
                .add("cost", u.cost())
                .add("hp", u.hp())
                .add("attack", u.attack())
                .add("range", u.range())
                .add("star", u.star())
                .add("row", u.row())
                .add("col", u.col())
                .build();
    }

    public static JsonObject toJson(CombatResultDto r) {
        JsonArrayBuilder log = Json.createArrayBuilder();
        r.log().forEach(log::add);
        return Json.createObjectBuilder()
                .add("round", r.round())
                .add("winnerId", r.winnerId())
                .add("damage", r.damage())
                .add("log", log)
                .build();
    }

    private static JsonArray toJson(List<UnitDto> units) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        units.forEach(u -> arr.add(toJson(u)));
        return arr.build();
    }

    /**
     * Baut die JSON-Anfrage fuer einen Konsolenbefehl, z.B.
     * {@code buy 1} + playerId 0 -> {@code {"cmd":"buy","playerId":0,"args":[1]}}.
     */
    public static String commandJson(String commandLine, int playerId) {
        String[] parts = commandLine.trim().toLowerCase().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) throw new IllegalArgumentException("Leerer Befehl");
        JsonArrayBuilder args = Json.createArrayBuilder();
        for (int i = 1; i < parts.length; i++) {
            try {
                args.add(Integer.parseInt(parts[i]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Argument ist keine Zahl: " + parts[i]);
            }
        }
        return write(Json.createObjectBuilder()
                .add("cmd", parts[0])
                .add("playerId", playerId)
                .add("args", args)
                .build(), true);
    }

    // ---------- Unmarshalling (JSON-String -> Java-Objekt) ----------

    /** Liest ein JSON-Objekt; syntaktisch ungueltiges JSON -> IllegalArgumentException. */
    public static JsonObject read(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ungueltiges JSON: " + e.getMessage());
        }
    }

    public static GameStateDto toGameState(String json) {
        JsonObject o = read(json);
        return new GameStateDto(
                o.getInt("round"),
                toPlayer(o.getJsonObject("self")),
                toPlayer(o.getJsonObject("opponent")),
                o.getBoolean("gameOver"),
                o.isNull("winnerName") ? null : o.getString("winnerName"));
    }

    public static PlayerDto toPlayer(String json) {
        return toPlayer(read(json));
    }

    public static PlayerDto toPlayer(JsonObject o) {
        return new PlayerDto(
                o.getInt("id"),
                o.getString("name"),
                o.getInt("hp"),
                o.getInt("gold"),
                o.getInt("level"),
                o.getBoolean("ready"),
                toUnits(o.getJsonArray("board")),
                toUnits(o.getJsonArray("bench")),
                toUnits(o.getJsonArray("shop")));
    }

    public static UnitDto toUnit(JsonObject o) {
        return new UnitDto(
                o.getInt("id"), o.getString("name"), o.getInt("cost"), o.getInt("hp"),
                o.getInt("attack"), o.getInt("range"), o.getInt("star"),
                o.getInt("row"), o.getInt("col"));
    }

    public static CombatResultDto toCombatResult(String json) {
        JsonObject o = read(json);
        List<String> log = new ArrayList<>();
        o.getJsonArray("log").forEach(v -> log.add(((javax.json.JsonString) v).getString()));
        return new CombatResultDto(o.getInt("round"), log, o.getInt("winnerId"), o.getInt("damage"));
    }

    private static List<UnitDto> toUnits(JsonArray arr) {
        List<UnitDto> units = new ArrayList<>();
        arr.forEach(v -> units.add(toUnit((JsonObject) v)));
        return units;
    }

    // ---------- Befehls-Protokoll (JsonParser, Streaming) ----------

    /** Ein aus JSON unmarshalling Befehl: cmd + playerId + Argumente. */
    public record CommandRequest(String cmd, int playerId, int[] args) {

        /** Argument an Position i (0-basiert) - fehlt es, gibt es eine Uebertragbare GameRuleException. */
        public int arg(int i) throws GameRuleException {
            if (i < 0 || i >= args.length)
                throw new GameRuleException("JSON-Befehl \"" + cmd + "\" braucht Argument " + (i + 1)
                        + " (habe " + args.length + ")");
            return args[i];
        }

        public int argCount() {
            return args.length;
        }
    }

    /**
     * Unmarshalling eines Befehls mit dem JSON-P-Parser (JsonParser, ereignisbasiert).
     * Unbekannte Felder werden uebersprungen, fehlt "cmd"/"playerId" oder ist das
     * JSON kaputt, entsteht eine GameRuleException (wird ueber RMI an den Client
     * uebertragen - Aufgabe 7).
     */
    public static CommandRequest parseCommand(String json) throws GameRuleException {
        if (json == null || json.isBlank()) throw new GameRuleException("Leere JSON-Nachricht");
        String cmd = null;
        Integer playerId = null;
        List<Integer> args = new ArrayList<>();
        try (JsonParser parser = Json.createParser(new StringReader(json))) {
            if (parser.next() != JsonParser.Event.START_OBJECT)
                throw new GameRuleException("JSON-Nachricht muss ein Objekt sein");

            while (parser.hasNext()) {
                JsonParser.Event event = parser.next();
                if (event == JsonParser.Event.END_OBJECT) break;
                if (event != JsonParser.Event.KEY_NAME)
                    throw new GameRuleException("Unerwartetes JSON-Element: " + event);

                String key = parser.getString();
                JsonParser.Event value = parser.next();
                switch (key) {
                    case "cmd" -> cmd = value == JsonParser.Event.VALUE_STRING
                            ? parser.getString() : null;
                    case "playerId" -> playerId = value == JsonParser.Event.VALUE_NUMBER
                            ? parser.getInt() : null;
                    case "args" -> {
                        if (value == JsonParser.Event.START_ARRAY) {
                            while (parser.hasNext() && parser.next() != JsonParser.Event.END_ARRAY) {
                                args.add(parser.getInt());
                            }
                        }
                    }
                    default -> skip(parser, value); // unbekannte Felder ignorieren
                }
            }
        } catch (GameRuleException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GameRuleException("JSON konnte nicht gelesen werden: " + e.getMessage());
        }

        if (cmd == null || cmd.isBlank()) throw new GameRuleException("JSON-Befehl ohne Feld \"cmd\"");
        if (playerId == null) throw new GameRuleException("JSON-Befehl ohne Feld \"playerId\"");
        return new CommandRequest(cmd, playerId, args.stream().mapToInt(Integer::intValue).toArray());
    }

    /** Ueberspringt ein verschachteltes Objekt/Array im Parser. */
    private static void skip(JsonParser parser, JsonParser.Event start) {
        if (start != JsonParser.Event.START_OBJECT && start != JsonParser.Event.START_ARRAY) return;
        int depth = 1;
        while (depth > 0 && parser.hasNext()) {
            JsonParser.Event e = parser.next();
            if (e == JsonParser.Event.START_OBJECT || e == JsonParser.Event.START_ARRAY) depth++;
            else if (e == JsonParser.Event.END_OBJECT || e == JsonParser.Event.END_ARRAY) depth--;
        }
    }
}
// AI-GENERATED-END
