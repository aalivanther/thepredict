package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Effective distilled state of one profile, per regime (M29.6). Loading does not wait. Disable acts on the
 * next capture.
 */
public final class DistilledArtifacts {
    /** Lifecycle shared with the learned artifacts. */
    public enum State {
        /** Nothing loaded. */
        NONE,
        /** Verdicts are still loading. */
        PENDING_VERDICT,
        /** A passing verdict is in force. */
        ACTIVE,
        /** The file has no passing verdict. */
        UNVERIFIED_ARTIFACT,
        /** Drift. The artifact stays, with a warning. */
        DEGRADED
    }

    private final Map<String, DistillMode> modes = new LinkedHashMap<>();
    private State state = State.NONE;
    private boolean killed;

    /**
     * Starts with nothing loaded.
     */
    public DistilledArtifacts() {
    }

    /**
     * The state.
     *
     * @return the state
     */
    public State state() {
        return state;
    }

    /**
     * Whether {@code distill disable} has been asked. The next capture sees it.
     *
     * @return the flag
     */
    public boolean killed() {
        return killed;
    }

    /**
     * Publishes a mode for one regime. A higher request is stored as the minimum of the current floor.
     *
     * @param regime regime key
     * @param mode requested mode
     * @param floor governor floor
     */
    public void publish(String regime, DistillMode mode, DistillMode floor) {
        modes.put(regime, mode.lower(floor));
        state = State.ACTIVE;
    }

    /**
     * The mode in force, or off when the artifact was killed.
     *
     * @param regime regime key
     * @return the mode
     */
    public DistillMode mode(String regime) {
        if (killed) {
            return DistillMode.OFF;
        }
        return modes.getOrDefault(regime, DistillMode.OFF);
    }

    /**
     * Marks disable. The mode drops on the next read.
     */
    public void kill() {
        killed = true;
        state = State.DEGRADED;
    }

    /**
     * Clears disable. This is {@code distill mode}, the only rise.
     *
     * @param regime regime key
     * @param mode the mode the command sets
     */
    public void restore(String regime, DistillMode mode) {
        killed = false;
        modes.put(regime, mode);
        state = State.ACTIVE;
    }

    /**
     * The active fingerprint, empty when nothing is loaded.
     *
     * @return the fingerprint
     */
    public Optional<String> active() {
        return state == State.NONE ? Optional.empty() : Optional.of(state.name());
    }
}
