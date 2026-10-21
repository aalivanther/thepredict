package akyrion.thepredict.adapter.storage;

import java.util.LinkedHashMap;

/**
 * Writes one {@link RecordEvent} as a single JSON line without spaces (M21.1).
 */
public final class RecordLine {
    private RecordLine() {
    }

    /**
     * Formats the event.
     *
     * @param event the event
     * @return one JSON object, without a trailing newline
     */
    public static String format(RecordEvent event) {
        var line = new LinkedHashMap<String, String>();
        line.put("v", "1");
        line.put("type", JsonText.quote(event.type()));
        line.put("run", JsonText.quote(event.run()));
        line.put("t_ms", Long.toString(event.tMs()));
        line.put("t_nanos", Long.toString(event.tNanos()));
        line.put("tick", Long.toString(event.tick()));
        line.put("tag", JsonText.quote(event.tag()));
        line.putAll(event.fields());
        return JsonText.object(line);
    }
}
