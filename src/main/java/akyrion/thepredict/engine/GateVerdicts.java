package akyrion.thepredict.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory gate status (M22.5). A lookup is O(1). While no verdict file has been loaded the status is
 * {@link Status#PENDING} and a caller may ask again every 60 seconds. Nothing here waits on the server thread.
 * A pass becomes visible on the next tick, never in the middle of a cast.
 */
public final class GateVerdicts {
    /**
     * An empty gate. Every candidate stays pending until a verdict file is loaded.
     */
    public GateVerdicts() {
    }

    /** Ticks between requests while nothing has loaded (60 s). */
    public static final int REQUEST_EVERY_TICKS = 1200;

    private final Map<String, Stored> rows = new HashMap<>();
    private boolean loaded;
    private long lastRequestTick = Long.MIN_VALUE;
    private String armedSha = "";
    private boolean armNextTick;

    /**
     * Status of one candidate and one gate.
     */
    public enum Status {
        /** The gate passed, including a live source. */
        PASS,
        /** The gate passed on simulation only. */
        PASS_SIM_ONLY,
        /** The gate failed. */
        FAIL,
        /** Verdicts were loaded and this candidate has none. */
        NONE,
        /** Nothing has been loaded yet. The previous policy continues. */
        PENDING
    }

    /**
     * Whether a fresh request should be sent. Calling this records the tick when it returns true.
     *
     * @param tick server tick
     * @return whether 60 s have passed without a load
     */
    public boolean due(long tick) {
        if (loaded) {
            return false;
        }
        if (lastRequestTick != Long.MIN_VALUE && tick - lastRequestTick < REQUEST_EVERY_TICKS) {
            return false;
        }
        lastRequestTick = tick;
        return true;
    }

    /**
     * Replaces the memory with a load. The higher {@code decided_epoch_ms} wins per candidate and gate.
     *
     * @param incoming the rows
     */
    public void load(List<StorageReply.GateVerdictRow> incoming) {
        loaded = true;
        for (var row : incoming) {
            var key = row.sha256() + "|" + row.gate();
            var stored = rows.get(key);
            if (stored == null || row.decidedEpochMs() > stored.epochMs) {
                rows.put(key, new Stored(statusOf(row.verdict()), row.decidedEpochMs()));
            }
        }
    }

    /**
     * Status of a candidate for gate {@code G1}. Pending until the first load.
     *
     * @param sha256 candidate fingerprint
     * @return the status
     */
    public Status status(String sha256) {
        if (!loaded) {
            return Status.PENDING;
        }
        var stored = rows.get(sha256 + "|G1");
        return stored == null ? Status.NONE : stored.status;
    }

    /**
     * Asks the next tick to adopt a pass. A cast already in progress is not interrupted.
     *
     * @param sha256 candidate fingerprint
     */
    public void armNext(String sha256) {
        if (status(sha256) == Status.PASS || status(sha256) == Status.PASS_SIM_ONLY) {
            armedSha = sha256;
            armNextTick = true;
        }
    }

    /**
     * Consumes a pass that was armed on the previous tick.
     *
     * @return the fingerprint, or empty when nothing was armed
     */
    public String takeArmed() {
        if (!armNextTick) {
            return "";
        }
        armNextTick = false;
        return armedSha;
    }

    private static Status statusOf(String verdict) {
        return switch (verdict) {
            case "PASS" -> Status.PASS;
            case "PASS_SIM_ONLY" -> Status.PASS_SIM_ONLY;
            case "FAIL" -> Status.FAIL;
            default -> Status.NONE;
        };
    }

    private record Stored(Status status, long epochMs) {
    }
}
