package akyrion.thepredict;

import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemoryPolicy;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.engine.MemoryModeLoader;
import akyrion.thepredict.engine.PatternModeGate;
import akyrion.thepredict.engine.StorageReply;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The latest verdict of every stage of every candidate (M22.5), read from {@code VerdictsLoaded}, and the two modes that
 * wait for them (W8): the memory mode for the fingerprint of {@code MemoryPolicy} (M27.7) and the pattern mode for
 * {@link PatternFingerprint} (M26.6). {@code seed} and {@code bias} are {@code CODE_PRESET}s: they need a passing
 * {@code G1}, whose verdict is the global one, {@code PASS} or {@code PASS_SIM_ONLY}, which decision 7 of M22 allows
 * for a preset that does not move the mob (M26 and M27, decision 1: "+ E6 o {@code PASS_SIM_ONLY}"). {@code full} also
 * changes the body sent to Jev and needs a passing {@code G2} as well; with {@code G1} alone it falls back to
 * {@code seed} or {@code bias}, with a warning. Per candidate and stage, the later {@code decided_epoch_ms} wins.
 * Server thread only.
 */
final class VerdictGates {
    /** Stage of the simulator, which carries the global verdict of a preset. */
    static final String SIMULATOR = "G1";
    /** Stage of the bench, which {@code full} adds. */
    static final String BENCH = "G2";

    private final Map<String, Stored> latest = new HashMap<>();
    private boolean loaded;

    /**
     * Adds a load of verdict rows.
     *
     * @param rows rows from the verdict files and from SQLite
     */
    void load(List<StorageReply.GateVerdictRow> rows) {
        loaded = true;
        for (var row : rows) {
            var key = row.sha256() + "|" + row.gate();
            var stored = latest.get(key);
            if (stored == null || row.decidedEpochMs() > stored.epochMs()) {
                latest.put(key, new Stored(row.verdict(), row.decidedEpochMs()));
            }
        }
    }

    /**
     * Whether a stage of a candidate passed.
     *
     * @param sha256 candidate fingerprint
     * @param gate stage name, {@code G0} to {@code G4}
     * @return {@code true} for {@code PASS} or {@code PASS_SIM_ONLY}
     */
    boolean passes(String sha256, String gate) {
        var stored = latest.get(sha256 + "|" + gate);
        return stored != null && ("PASS".equals(stored.verdict()) || "PASS_SIM_ONLY".equals(stored.verdict()));
    }

    /**
     * Offers the configured pattern mode to its gate: {@code bias} with a passing {@code G1}, {@code full} with a
     * passing {@code G2} as well. {@code off} and {@code observe} need no verdict.
     *
     * @param gate the gate the pattern director reads
     * @param settings the global pattern limits, whose mode is the request
     * @return the warning when {@code full} fell back to {@code bias}
     */
    Optional<String> offerPatterns(PatternModeGate gate, PatternSettings settings) {
        var requested = settings.mode();
        var sha = PatternFingerprint.of(settings);
        var granted = requested == PatternMode.OFF || requested == PatternMode.OBSERVE ? 0 : granted(sha);
        if (granted == 0) {
            gate.offer(requested, false);
            return Optional.empty();
        }
        var mode = requested == PatternMode.FULL && granted == 2 ? PatternMode.FULL : PatternMode.BIAS;
        gate.offer(mode, true);
        return mode == requested ? Optional.empty() : Optional.of(lowered("patterns.mode", sha, "bias"));
    }

    /**
     * Offers the configured memory mode to its loader: {@code seed} with a passing {@code G1}, {@code full} with a
     * passing {@code G2} as well. {@code off}, {@code record} and {@code learning.allowUnverified} need no verdict.
     *
     * @param loader the loader of the memory host
     * @param settings the memory limits, whose mode is the request
     * @return the warning when {@code full} fell back to {@code seed}
     */
    Optional<String> offerMemory(MemoryModeLoader loader, MemorySettings settings) {
        var requested = settings.mode();
        var free = requested == MemoryMode.OFF || requested == MemoryMode.RECORD || settings.allowUnverified();
        var sha = MemoryPolicy.fingerprint(settings);
        var granted = free ? 0 : granted(sha);
        if (granted == 0) {
            loader.offer(requested, false, settings);
            return Optional.empty();
        }
        var mode = requested == MemoryMode.FULL && granted == 2 ? MemoryMode.FULL : MemoryMode.SEED;
        loader.offer(mode, true, settings);
        return mode == requested ? Optional.empty() : Optional.of(lowered("memory.mode", sha, "seed"));
    }

    /**
     * The loaded verdicts for {@code eval verdict list}: {@code <first 12 hex> <stage> <verdict>} per candidate and
     * stage, in fingerprint order. With more than {@code max}, the last line counts the rest. A verdict imported is
     * delivered again by the writer and appears at once (CAR-08).
     *
     * @param max most lines, at least 2
     * @return the lines; one pending line before the first load; empty when nothing loaded
     */
    List<String> lines(int max) {
        if (!loaded) {
            return List.of("pending: the verdicts have not loaded yet");
        }
        var keys = new ArrayList<>(latest.keySet());
        Collections.sort(keys);
        var lines = new ArrayList<String>();
        for (var key : keys) {
            if (keys.size() > max && lines.size() == max - 1) {
                lines.add((keys.size() - lines.size()) + " more verdicts are not shown");
                break;
            }
            var cut = key.indexOf('|');
            lines.add(key.substring(0, Math.min(12, cut)) + " " + key.substring(cut + 1) + " "
                    + latest.get(key).verdict());
        }
        return List.copyOf(lines);
    }

    private int granted(String sha256) {
        if (!passes(sha256, SIMULATOR)) {
            return 0;
        }
        return passes(sha256, BENCH) ? 2 : 1;
    }

    private static String lowered(String key, String sha256, String used) {
        return key + " full has no passing " + BENCH + " verdict for fingerprint " + sha256 + "; " + used
                + " is used until one is imported";
    }

    private record Stored(String verdict, long epochMs) {
    }
}
