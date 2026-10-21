package akyrion.thepredict.adapter.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

/**
 * Strict JSON reading shared by the response and catalog parsers. Unlike {@code JsonParser.parseString}, which
 * is lenient and keeps the last of two equal keys, it rejects {@code NaN}, comments, unquoted names, trailing
 * content, repeated keys and nesting deeper than 64 levels. Numbers keep their exact text. Errors never
 * quote the input (rule C8).
 */
final class StrictJson {
    private static final int MAX_DEPTH = 64;

    private StrictJson() {
    }

    static JsonObject parseObject(String body, String what) {
        try (var reader = new JsonReader(new StringReader(body))) {
            reader.setStrictness(Strictness.STRICT);
            var element = read(reader, what, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new SystemOneProtocolException(what + " is not valid JSON");
            }
            return object(element, what);
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            throw new SystemOneProtocolException(what + " is not valid JSON");
        }
    }

    private static JsonElement read(JsonReader reader, String what, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new SystemOneProtocolException(what + " is nested too deeply");
        }
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> readObject(reader, what, depth);
            case BEGIN_ARRAY -> readArray(reader, what, depth);
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new SystemOneProtocolException(what + " is not valid JSON");
        };
    }

    private static JsonObject readObject(JsonReader reader, String what, int depth) throws IOException {
        var object = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
            var name = reader.nextName();
            if (object.has(name)) {
                throw new SystemOneProtocolException(what + " repeats a key");
            }
            object.add(name, read(reader, what, depth + 1));
        }
        reader.endObject();
        return object;
    }

    private static JsonArray readArray(JsonReader reader, String what, int depth) throws IOException {
        var array = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) {
            array.add(read(reader, what, depth + 1));
        }
        reader.endArray();
        return array;
    }

    static JsonObject object(JsonElement element, String what) {
        if (element == null || !element.isJsonObject()) {
            throw new SystemOneProtocolException(what + " must be an object");
        }
        return element.getAsJsonObject();
    }

    static String text(JsonObject parent, String name, String what) {
        var element = parent.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new SystemOneProtocolException(what + " must be a string");
        }
        return element.getAsString();
    }

    static double number(JsonElement element, String what) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new SystemOneProtocolException(what + " must be a number");
        }
        return element.getAsDouble();
    }

    static int nonNegativeInt(JsonElement element, String what) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new SystemOneProtocolException(what + " must be an integer");
        }
        try {
            var value = new BigDecimal(((JsonPrimitive) element).getAsString());
            var exact = value.intValueExact();
            if (exact < 0) {
                throw new SystemOneProtocolException(what + " must not be negative");
            }
            return exact;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new SystemOneProtocolException(what + " must be an integer");
        }
    }
}
