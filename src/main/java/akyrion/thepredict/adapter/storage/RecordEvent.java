package akyrion.thepredict.adapter.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One immutable JSONL event (M21.1). The writer turns it into text. The type name is the {@code type} field.
 * Payloads are JSON tokens already, so the server thread does not format strings beyond the tokens it already has.
 * The fields keep the order they were put in, so two runs of the same script write the same bytes.
 *
 * @param type event type
 * @param run run id
 * @param tMs wall time in epoch milliseconds, a timestamp only
 * @param tNanos monotonic time
 * @param tick server tick
 * @param tag operator tag, empty when none
 * @param fields payload tokens, excluding the envelope
 */
public record RecordEvent(String type, String run, long tMs, long tNanos, long tick, String tag,
                          Map<String, String> fields) {
    /**
     * Copies the fields in their order.
     *
     * @throws NullPointerException if a component, a field name or a field value is {@code null}
     */
    public RecordEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(tag, "tag");
        var copy = new LinkedHashMap<String, String>(Math.max(4, fields.size() * 2));
        for (var entry : fields.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "field name"),
                    Objects.requireNonNull(entry.getValue(), "field value"));
        }
        fields = Collections.unmodifiableMap(copy);
    }
}
